package com.meditation.core

import kotlinx.serialization.Serializable

/**
 * Time-of-day auto-presets (brief §16): map ranges of the local day to a preset the app can offer
 * (or auto-select) when the user opens it. Pure logic so the matching — including ranges that wrap
 * past midnight — is unit-tested. The Android layer persists the rule list (DataStore JSON) and
 * passes the current local minute-of-day.
 */
@Serializable
data class AutoPresetRule(
    /** Inclusive start, minutes since local midnight (0..1439). */
    val startMinute: Int,
    /** Exclusive end, minutes since local midnight (0..1440). A value ≤ start means the range wraps past midnight. */
    val endMinute: Int,
    val presetId: String,
    val label: String = "",
    val enabled: Boolean = true,
) {
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return if (startMinute < endMinute) {
            m >= startMinute && m < endMinute
        } else {
            // wraps midnight, e.g. 22:00 -> 06:00
            m >= startMinute || m < endMinute
        }
    }
}

object AutoPreset {
    const val MINUTES_PER_DAY = 1440

    /** First enabled rule whose window contains [minuteOfDay], or null. Order = priority. */
    fun select(rules: List<AutoPresetRule>, minuteOfDay: Int): String? =
        rules.firstOrNull { it.contains(minuteOfDay) }?.presetId

    /** Convenience: derive local minute-of-day from a wall-clock ms and a tz offset ms. */
    fun minuteOfDay(nowWallMs: Long, tzOffsetMs: Long): Int {
        val local = nowWallMs + tzOffsetMs
        val dayMs = ((local % 86_400_000L) + 86_400_000L) % 86_400_000L
        return (dayMs / 60_000L).toInt()
    }
}
