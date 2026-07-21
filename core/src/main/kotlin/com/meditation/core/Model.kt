package com.meditation.core

import kotlinx.serialization.Serializable

/**
 * Core domain model for the Always-On Meditation Timer.
 *
 * These types are deliberately free of any Android dependency so the timing engine
 * and state machine can be unit-tested on a plain JVM. The Android layer maps these
 * to/from Room entities and the UI.
 */

enum class SoundCategory {
    BELL, BOWL, GONG, WOOD, CHIME, AMBIENCE, NOISE, DRONE, BINAURAL, IMPORTED;

    companion object {
        fun fromId(value: String): SoundCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: IMPORTED
    }
}

enum class SoundSourceType { BUNDLED, GENERATED, IMPORTED }

/** Roles a sound may be assigned to. Used to gate assignment controls in the UI. */
enum class SoundRole { OPENING, INTERVAL, TRANSITION, CLOSING, AMBIENCE }

enum class OvertimeMode { STOP, COUNT_UP, SILENT_COUNT_UP }

enum class ScreenBehavior { NORMAL, DIM, TURN_OFF_AFTER_START }

enum class DoNotDisturbBehavior { UNCHANGED, REQUEST_PRIORITY_ONLY, REQUEST_SILENT }

/**
 * Lifecycle status of an active session. Kotlin is authoritative for this value;
 * the UI only renders it.
 */
enum class SessionStatus { PREPARING, RUNNING, PAUSED, OVERTIME, COMPLETED, CANCELLED }

enum class CompletionStatus { COMPLETED, FINISHED_EARLY, CANCELLED }

@Serializable
data class GeneratorConfig(
    /** white | pink | brown | sine | dual */
    val type: String,
    val gain: Double = 0.6,
    val frequencyHz: Double? = null,
    val secondFrequencyHz: Double? = null,
    val mix: Double? = null,
    val modulationHz: Double? = null,
)

@Serializable
data class SoundAsset(
    val id: String,
    val name: String,
    val category: SoundCategory,
    val sourceType: SoundSourceType,
    val fileUri: String? = null,
    val generatorConfig: GeneratorConfig? = null,
    val imageAssetId: String? = null,
    val durationMs: Long? = null,
    val defaultVolume: Double = 0.7,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val roles: List<SoundRole> = emptyList(),
    val attributionId: String? = null,
    val favorite: Boolean = false,
)

@Serializable
data class Attribution(
    val id: String,
    val creator: String,
    val title: String,
    val sourceName: String,
    val sourcePage: String? = null,
    val licenseName: String,
    val licensePage: String? = null,
    val modifications: String? = null,
)

@Serializable
data class AmbienceLayer(
    val soundId: String,
    val volume: Double = 0.6,
)

/**
 * Interval bell plan for a stage. Sealed so exhaustive handling is enforced at compile time.
 * Random intervals are intentionally omitted (reserved for Phase 2).
 */
@Serializable
sealed interface IntervalPlan {
    @Serializable
    data object None : IntervalPlan

    @Serializable
    data class EveryXMinutes(
        val intervalMs: Long,
        val soundId: String,
        val strikeCount: Int = 1,
    ) : IntervalPlan

    @Serializable
    data class CustomTimestamps(
        /** Offsets from the start of the stage, in ms, ascending. */
        val offsetsMs: List<Long>,
        val soundId: String,
        val strikeCount: Int = 1,
    ) : IntervalPlan

    /** Intervals that lengthen over the stage: first at [startMs], each subsequent +[incrementMs]. */
    @Serializable
    data class Progressive(
        val startMs: Long,
        val incrementMs: Long,
        val soundId: String,
        val strikeCount: Int = 1,
    ) : IntervalPlan

    /**
     * Randomly spaced intervals (Phase 2). Each gap is drawn uniformly from [minIntervalMs]..
     * [maxIntervalMs]. [seed] makes the sequence deterministic so it survives process death and
     * deduplicates correctly; the builder assigns a fresh seed per stage.
     */
    @Serializable
    data class Random(
        val minIntervalMs: Long,
        val maxIntervalMs: Long,
        val soundId: String,
        val seed: Long = 1,
        val strikeCount: Int = 1,
    ) : IntervalPlan
}

@Serializable
data class SessionStage(
    val id: String,
    val name: String,
    /** null represents an open-ended (count-up) stage. Only valid as the final stage. */
    val durationMs: Long?,
    val openingSoundId: String? = null,
    val closingSoundId: String? = null,
    val ambienceLayers: List<AmbienceLayer> = emptyList(),
    val intervalPlan: IntervalPlan = IntervalPlan.None,
    val spokenCueIds: List<String> = emptyList(),
)

@Serializable
data class SessionPreset(
    val id: String,
    val name: String,
    val preparationMs: Long = 0,
    val stages: List<SessionStage> = emptyList(),
    val finalSoundId: String? = null,
    val completionStrikeCount: Int = 1,
    val completionStrikeSpacingMs: Long = 2500,
    val overtimeMode: OvertimeMode = OvertimeMode.STOP,
    val screenBehavior: ScreenBehavior = ScreenBehavior.NORMAL,
    val doNotDisturbBehavior: DoNotDisturbBehavior = DoNotDisturbBehavior.UNCHANGED,
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** Sum of fixed stage durations plus preparation. Open-ended stages contribute 0. */
    val plannedDurationMs: Long
        get() = preparationMs + stages.sumOf { it.durationMs ?: 0L }

    val hasOpenEndedStage: Boolean get() = stages.any { it.durationMs == null }
}

@Serializable
data class VolumeSettings(
    val bellVolume: Double = 0.85,
    val ambienceVolume: Double = 0.6,
)

data class DeviceCapabilities(
    val canScheduleExactAlarms: Boolean,
    val notificationsEnabled: Boolean,
    val ignoresBatteryOptimizations: Boolean,
)
