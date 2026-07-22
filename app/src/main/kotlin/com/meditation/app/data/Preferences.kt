package com.meditation.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meditation.core.OvertimeMode
import com.meditation.core.VolumeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@kotlinx.serialization.Serializable
data class UserPreferences(
    val defaultDurationMs: Long = 10 * 60_000,
    val defaultPreparationMs: Long = 0,
    val overtimeMode: OvertimeMode = OvertimeMode.STOP,
    val bellVolume: Double = 0.85,
    val ambienceVolume: Double = 0.6,
    val interruptionResumeAuto: Boolean = true,
    val theme: String = "system",
    val palette: String = "twilight",
    val reducedMotion: Boolean = false,
    val keepScreenOn: Boolean = false,
    val dndDuringSession: Boolean = false,
    val spokenCuesEnabled: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    /** JSON-encoded List<AutoPresetRule> for time-of-day suggestions. */
    val autoPresetRulesJson: String = "[]",
    /** JSON-encoded List<SavedAmbienceMix> from the soundscape mixer. */
    val savedMixesJson: String = "[]",
) {
    val volumes get() = VolumeSettings(bellVolume, ambienceVolume)
}

class PreferencesRepository(private val context: Context) {

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            defaultDurationMs = p[Keys.defaultDuration] ?: 10 * 60_000,
            defaultPreparationMs = p[Keys.defaultPrep] ?: 0,
            overtimeMode = p[Keys.overtime]?.let { runCatching { OvertimeMode.valueOf(it) }.getOrNull() }
                ?: OvertimeMode.STOP,
            bellVolume = p[Keys.bellVolume] ?: 0.85,
            ambienceVolume = p[Keys.ambienceVolume] ?: 0.6,
            interruptionResumeAuto = (p[Keys.resumeAuto] ?: 1) == 1,
            theme = p[Keys.theme] ?: "system",
            palette = p[Keys.palette] ?: "twilight",
            reducedMotion = (p[Keys.reducedMotion] ?: 0) == 1,
            keepScreenOn = (p[Keys.keepScreenOn] ?: 0) == 1,
            dndDuringSession = (p[Keys.dndDuringSession] ?: 0) == 1,
            spokenCuesEnabled = (p[Keys.spokenCuesEnabled] ?: 0) == 1,
            reminderEnabled = (p[Keys.reminderEnabled] ?: 0) == 1,
            reminderHour = p[Keys.reminderHour] ?: 8,
            reminderMinute = p[Keys.reminderMinute] ?: 0,
            autoPresetRulesJson = p[Keys.autoPresetRules] ?: "[]",
            savedMixesJson = p[Keys.savedMixes] ?: "[]",
        )
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = edit {
        it[Keys.reminderEnabled] = if (enabled) 1 else 0
        it[Keys.reminderHour] = hour
        it[Keys.reminderMinute] = minute
    }

    suspend fun setBellVolume(v: Double) = edit { it[Keys.bellVolume] = v }
    suspend fun setAmbienceVolume(v: Double) = edit { it[Keys.ambienceVolume] = v }
    suspend fun setDefaultDuration(ms: Long) = edit { it[Keys.defaultDuration] = ms }
    suspend fun setDefaultPreparation(ms: Long) = edit { it[Keys.defaultPrep] = ms }
    suspend fun setOvertimeMode(mode: OvertimeMode) = edit { it[Keys.overtime] = mode.name }
    suspend fun setTheme(theme: String) = edit { it[Keys.theme] = theme }
    suspend fun setPalette(palette: String) = edit { it[Keys.palette] = palette }
    suspend fun setReducedMotion(on: Boolean) = edit { it[Keys.reducedMotion] = if (on) 1 else 0 }
    suspend fun setKeepScreenOn(on: Boolean) = edit { it[Keys.keepScreenOn] = if (on) 1 else 0 }
    suspend fun setDndDuringSession(on: Boolean) = edit { it[Keys.dndDuringSession] = if (on) 1 else 0 }
    suspend fun setSpokenCuesEnabled(on: Boolean) = edit { it[Keys.spokenCuesEnabled] = if (on) 1 else 0 }
    suspend fun setAutoPresetRules(json: String) = edit { it[Keys.autoPresetRules] = json }
    suspend fun setSavedMixes(json: String) = edit { it[Keys.savedMixes] = json }
    suspend fun currentPrefs(): UserPreferences = preferences.first()
    suspend fun setResumeAuto(on: Boolean) = edit { it[Keys.resumeAuto] = if (on) 1 else 0 }

    suspend fun currentVolumes(): VolumeSettings = preferences.first().volumes

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private object Keys {
        val defaultDuration = longPreferencesKey("default_duration_ms")
        val defaultPrep = longPreferencesKey("default_prep_ms")
        val overtime = stringPreferencesKey("overtime_mode")
        val bellVolume = doublePreferencesKey("bell_volume")
        val ambienceVolume = doublePreferencesKey("ambience_volume")
        val resumeAuto = intPreferencesKey("resume_auto")
        val theme = stringPreferencesKey("theme")
        val palette = stringPreferencesKey("palette")
        val reducedMotion = intPreferencesKey("reduced_motion")
        val keepScreenOn = intPreferencesKey("keep_screen_on")
        val dndDuringSession = intPreferencesKey("dnd_during_session")
        val spokenCuesEnabled = intPreferencesKey("spoken_cues_enabled")
        val reminderEnabled = intPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val autoPresetRules = stringPreferencesKey("auto_preset_rules")
        val savedMixes = stringPreferencesKey("saved_ambience_mixes")
    }
}
