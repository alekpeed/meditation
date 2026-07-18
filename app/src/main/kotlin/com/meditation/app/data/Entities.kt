package com.meditation.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities. Complex nested structures (stages, generator configs, snapshots) are stored as
 * JSON strings via [Converters], keeping the schema flat and migrations simple. The core-domain
 * classes remain the source of truth; [Mappers] translate at the repository boundary.
 */

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val preparationMs: Long,
    val stagesJson: String,
    val finalSoundId: String?,
    val completionStrikeCount: Int,
    val completionStrikeSpacingMs: Long,
    val overtimeMode: String,
    val screenBehavior: String,
    val doNotDisturbBehavior: String,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * The single active session (there is at most one). Row id is always [ACTIVE_ROW_ID]; the whole
 * [com.meditation.core.ActiveSessionState] is stored as JSON so the exact runtime state can be
 * reconstructed after process death.
 */
@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Int = ACTIVE_ROW_ID,
    val sessionId: String,
    val stateJson: String,
    val expectedEndWallMs: Long?,
    val updatedAt: Long,
) {
    companion object { const val ACTIVE_ROW_ID = 1 }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val sessionId: String,
    val presetId: String,
    val presetName: String,
    val presetSnapshotJson: String?,
    val startedWallMs: Long,
    val completedWallMs: Long,
    val intendedDurationMs: Long,
    val actualActiveDurationMs: Long,
    val pausedDurationMs: Long,
    val overtimeDurationMs: Long,
    val completionStatus: String,
    val note: String?,
    val tagsJson: String,
    val moodBefore: Int?,
    val moodAfter: Int?,
)

@Entity(tableName = "sounds")
data class SoundEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val sourceType: String,
    val fileUri: String?,
    val generatorConfigJson: String?,
    val imageAssetId: String?,
    val durationMs: Long?,
    val defaultVolume: Double,
    val tagsJson: String,
    val description: String?,
    val rolesJson: String,
    val attributionId: String?,
    val favorite: Boolean,
)

@Entity(tableName = "attributions")
data class AttributionEntity(
    @PrimaryKey val id: String,
    val creator: String,
    val title: String,
    val sourceName: String,
    val sourcePage: String?,
    val licenseName: String,
    val licensePage: String?,
    val modifications: String?,
)
