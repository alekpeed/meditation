package com.meditation.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meditation.app.AppContainer
import com.meditation.app.data.UserPreferences
import com.meditation.core.CompletedSession
import com.meditation.core.OvertimeMode
import com.meditation.core.SessionPreset
import com.meditation.core.SessionSnapshot
import com.meditation.core.SessionStage
import com.meditation.core.SoundAsset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Bridges the [com.meditation.app.engine.MeditationController] and repositories to Compose. It holds
 * no timing logic of its own — the countdown the UI shows is the controller's projection, refreshed
 * once a second while a session is on screen (display only; correctness lives in the engine).
 */
class MeditationViewModel(private val container: AppContainer) : ViewModel() {

    val snapshot: StateFlow<SessionSnapshot?> = container.controller.snapshot

    val presets = container.presetRepository.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = container.presetRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<CompletedSession>> = container.historyRepository.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sounds: StateFlow<List<SoundAsset>> = container.soundRepository.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attributions = container.soundRepository.attributions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessionCount = container.historyRepository.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalActiveMs = container.historyRepository.totalActiveMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val preferences: StateFlow<UserPreferences> = container.preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val statistics: StateFlow<com.meditation.core.Statistics> = container.historyRepository.all
        .map { list ->
            val now = System.currentTimeMillis()
            val tz = java.util.TimeZone.getDefault().getOffset(now).toLong()
            com.meditation.core.Stats.compute(list, now, tz)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.meditation.core.Statistics(0, 0, 0, 0, 0, 0, 0, 0))

    init {
        // Drive a lightweight display refresh while a session is active and the app is visible.
        viewModelScope.launch {
            while (isActive) {
                if (container.controller.hasActiveSession) container.controller.refresh()
                delay(1000)
            }
        }
    }

    // ---- Quick start ----------------------------------------------------------------------

    fun startQuickSession(
        durationMs: Long,
        preparationMs: Long,
        openingSoundId: String?,
        intervalSoundId: String?,
        intervalEveryMs: Long?,
        closingSoundId: String?,
        ambienceSoundId: String?,
        overtimeMode: OvertimeMode,
    ) {
        val stage = SessionStage(
            id = UUID.randomUUID().toString(),
            name = "Meditation",
            durationMs = durationMs,
            openingSoundId = openingSoundId,
            closingSoundId = closingSoundId,
            ambienceLayers = ambienceSoundId?.let {
                listOf(com.meditation.core.AmbienceLayer(it, 0.6))
            } ?: emptyList(),
            intervalPlan = if (intervalSoundId != null && intervalEveryMs != null)
                com.meditation.core.IntervalPlan.EveryXMinutes(intervalEveryMs, intervalSoundId)
            else com.meditation.core.IntervalPlan.None,
        )
        val preset = SessionPreset(
            id = "quick-${UUID.randomUUID()}",
            name = "Quick Session",
            preparationMs = preparationMs,
            stages = listOf(stage),
            finalSoundId = closingSoundId,
            overtimeMode = overtimeMode,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        container.controller.startSession(preset)
    }

    fun start(preset: SessionPreset) = container.controller.startSession(preset)
    fun pause() = container.controller.pause()
    fun resume() = container.controller.resume()
    fun extend(minutes: Int) = container.controller.extendByMinutes(minutes)
    fun skip() = container.controller.skipStage()
    fun finish(
        cancelled: Boolean,
        note: String? = null,
        tags: List<String> = emptyList(),
        moodAfter: Int? = null,
    ) = container.controller.finish(cancelled, note, tags, moodAfter)

    fun historyById(id: String): CompletedSession? = history.value.firstOrNull { it.sessionId == id }
    fun updateHistory(id: String, note: String?, tags: List<String>, moodAfter: Int?) = viewModelScope.launch {
        container.historyRepository.updateCompletionFields(id, note, tags, moodAfter)
    }

    fun savePreset(preset: SessionPreset) = viewModelScope.launch {
        container.presetRepository.save(preset)
    }

    /** Instantiate a [com.meditation.core.SessionTemplates] blueprint and save it as a new preset. */
    fun createFromTemplate(templateKey: String) = viewModelScope.launch {
        val preset = com.meditation.core.SessionTemplates.build(
            key = templateKey,
            id = "preset-${UUID.randomUUID()}",
            nowMs = System.currentTimeMillis(),
        ) ?: return@launch
        container.presetRepository.save(preset)
    }
    fun deletePreset(id: String) = viewModelScope.launch { container.presetRepository.delete(id) }
    fun deleteHistory(id: String) = viewModelScope.launch { container.historyRepository.delete(id) }
    fun toggleSoundFavorite(id: String, favorite: Boolean) = viewModelScope.launch {
        container.soundRepository.setFavorite(id, favorite)
    }
    fun setTheme(mode: String) = viewModelScope.launch { container.preferencesRepository.setTheme(mode) }
    fun setPalette(palette: String) = viewModelScope.launch { container.preferencesRepository.setPalette(palette) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { container.preferencesRepository.setKeepScreenOn(on) }
    fun setReducedMotion(on: Boolean) = viewModelScope.launch { container.preferencesRepository.setReducedMotion(on) }
    fun setBellVolume(v: Double) = viewModelScope.launch { container.preferencesRepository.setBellVolume(v) }
    fun setAmbienceVolume(v: Double) = viewModelScope.launch { container.preferencesRepository.setAmbienceVolume(v) }
    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        container.preferencesRepository.setReminder(enabled, hour, minute)
        if (enabled) container.reminderScheduler.schedule(hour, minute) else container.reminderScheduler.cancel()
    }

    // Preview (isolated from any active session).
    fun previewSound(soundId: String, volume: Double = 0.8) = container.controller.previewSound(soundId, volume)
    fun previewMix(layers: List<Pair<String, Double>>) = container.controller.previewMix(layers)
    fun stopPreview() = container.controller.stopPreview()

    // Export uses the pure core formatter; the caller writes the returned bytes to a SAF document.
    fun exportJson(): String = com.meditation.core.HistoryExport.toJson(history.value)
    fun exportCsv(): String = com.meditation.core.HistoryExport.toCsv(history.value)

    // Import an audio file the user selected via the Storage Access Framework.
    fun importAudio(context: android.content.Context, uri: android.net.Uri, displayName: String) =
        viewModelScope.launch { container.soundRepository.importFromUri(context, uri, displayName) }

    fun validationErrors(preset: SessionPreset): List<String> =
        com.meditation.core.PresetValidation.validate(preset)

    // ---- Full backup / restore ------------------------------------------------------------

    fun exportBackupJson(): String {
        val prefs = preferences.value
        return com.meditation.core.Backup.encode(
            com.meditation.core.Backup(
                exportedWallMs = System.currentTimeMillis(),
                presets = presets.value,
                history = history.value,
                favoriteSoundIds = sounds.value.filter { it.favorite }.map { it.id },
                preferencesJson = com.meditation.app.data.AppJson.encodeToString(
                    com.meditation.app.data.UserPreferences.serializer(), prefs,
                ),
            ),
        )
    }

    /** Returns true if the text was a valid backup and restore was started. */
    fun importBackupJson(text: String): Boolean {
        val backup = com.meditation.core.Backup.decode(text) ?: return false
        viewModelScope.launch {
            backup.presets.forEach { container.presetRepository.save(it) }
            backup.history.forEach { container.historyRepository.record(it) }
            backup.favoriteSoundIds.forEach { container.soundRepository.setFavorite(it, true) }
            backup.preferencesJson?.let { pj ->
                runCatching {
                    val p = com.meditation.app.data.AppJson.decodeFromString(
                        com.meditation.app.data.UserPreferences.serializer(), pj,
                    )
                    val repo = container.preferencesRepository
                    repo.setBellVolume(p.bellVolume); repo.setAmbienceVolume(p.ambienceVolume)
                    repo.setDefaultDuration(p.defaultDurationMs); repo.setDefaultPreparation(p.defaultPreparationMs)
                    repo.setOvertimeMode(p.overtimeMode); repo.setTheme(p.theme); repo.setPalette(p.palette)
                    repo.setReducedMotion(p.reducedMotion); repo.setKeepScreenOn(p.keepScreenOn)
                    repo.setResumeAuto(p.interruptionResumeAuto)
                }
            }
        }
        return true
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeditationViewModel(container) as T
    }
}
