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
    fun finish(cancelled: Boolean) = container.controller.finish(cancelled)

    fun savePreset(preset: SessionPreset) = viewModelScope.launch {
        container.presetRepository.save(preset)
    }
    fun deletePreset(id: String) = viewModelScope.launch { container.presetRepository.delete(id) }
    fun deleteHistory(id: String) = viewModelScope.launch { container.historyRepository.delete(id) }
    fun toggleSoundFavorite(id: String, favorite: Boolean) = viewModelScope.launch {
        container.soundRepository.setFavorite(id, favorite)
    }
    fun setBellVolume(v: Double) = viewModelScope.launch { container.preferencesRepository.setBellVolume(v) }
    fun setAmbienceVolume(v: Double) = viewModelScope.launch { container.preferencesRepository.setAmbienceVolume(v) }

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

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeditationViewModel(container) as T
    }
}
