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
    val reducedMotion: Boolean = false,
    val keepScreenOn: Boolean = false,
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
            reducedMotion = (p[Keys.reducedMotion] ?: 0) == 1,
            keepScreenOn = (p[Keys.keepScreenOn] ?: 0) == 1,
        )
    }

    suspend fun setBellVolume(v: Double) = edit { it[Keys.bellVolume] = v }
    suspend fun setAmbienceVolume(v: Double) = edit { it[Keys.ambienceVolume] = v }
    suspend fun setDefaultDuration(ms: Long) = edit { it[Keys.defaultDuration] = ms }
    suspend fun setDefaultPreparation(ms: Long) = edit { it[Keys.defaultPrep] = ms }
    suspend fun setOvertimeMode(mode: OvertimeMode) = edit { it[Keys.overtime] = mode.name }
    suspend fun setTheme(theme: String) = edit { it[Keys.theme] = theme }
    suspend fun setReducedMotion(on: Boolean) = edit { it[Keys.reducedMotion] = if (on) 1 else 0 }
    suspend fun setKeepScreenOn(on: Boolean) = edit { it[Keys.keepScreenOn] = if (on) 1 else 0 }
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
        val reducedMotion = intPreferencesKey("reduced_motion")
        val keepScreenOn = intPreferencesKey("keep_screen_on")
    }
}
