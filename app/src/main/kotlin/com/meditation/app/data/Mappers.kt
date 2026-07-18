package com.meditation.app.data

import com.meditation.core.ActiveSessionState
import com.meditation.core.Attribution
import com.meditation.core.CompletedSession
import com.meditation.core.CompletionStatus
import com.meditation.core.DoNotDisturbBehavior
import com.meditation.core.GeneratorConfig
import com.meditation.core.OvertimeMode
import com.meditation.core.ScreenBehavior
import com.meditation.core.SessionPreset
import com.meditation.core.SessionStage
import com.meditation.core.SoundAsset
import com.meditation.core.SoundCategory
import com.meditation.core.SoundRole
import com.meditation.core.SoundSourceType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

// ---- Preset ---------------------------------------------------------------------------------

fun SessionPreset.toEntity() = PresetEntity(
    id = id,
    name = name,
    preparationMs = preparationMs,
    stagesJson = AppJson.encodeToString(ListSerializer(SessionStage.serializer()), stages),
    finalSoundId = finalSoundId,
    completionStrikeCount = completionStrikeCount,
    completionStrikeSpacingMs = completionStrikeSpacingMs,
    overtimeMode = overtimeMode.name,
    screenBehavior = screenBehavior.name,
    doNotDisturbBehavior = doNotDisturbBehavior.name,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PresetEntity.toDomain() = SessionPreset(
    id = id,
    name = name,
    preparationMs = preparationMs,
    stages = AppJson.decodeFromString(ListSerializer(SessionStage.serializer()), stagesJson),
    finalSoundId = finalSoundId,
    completionStrikeCount = completionStrikeCount,
    completionStrikeSpacingMs = completionStrikeSpacingMs,
    overtimeMode = OvertimeMode.valueOf(overtimeMode),
    screenBehavior = ScreenBehavior.valueOf(screenBehavior),
    doNotDisturbBehavior = DoNotDisturbBehavior.valueOf(doNotDisturbBehavior),
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---- Active session -------------------------------------------------------------------------

fun ActiveSessionState.toEntity(expectedEndWallMs: Long?) = ActiveSessionEntity(
    sessionId = sessionId,
    stateJson = AppJson.encodeToString(ActiveSessionState.serializer(), this),
    expectedEndWallMs = expectedEndWallMs,
    updatedAt = lastUpdateWallMs,
)

fun ActiveSessionEntity.toDomain(): ActiveSessionState =
    AppJson.decodeFromString(ActiveSessionState.serializer(), stateJson)

// ---- History --------------------------------------------------------------------------------

fun CompletedSession.toEntity() = HistoryEntity(
    sessionId = sessionId,
    presetId = presetId,
    presetName = presetName,
    presetSnapshotJson = presetSnapshotJson,
    startedWallMs = startedWallMs,
    completedWallMs = completedWallMs,
    intendedDurationMs = intendedDurationMs,
    actualActiveDurationMs = actualActiveDurationMs,
    pausedDurationMs = pausedDurationMs,
    overtimeDurationMs = overtimeDurationMs,
    completionStatus = completionStatus.name,
    note = note,
    tagsJson = AppJson.encodeToString(tags),
    moodBefore = moodBefore,
    moodAfter = moodAfter,
)

fun HistoryEntity.toDomain() = CompletedSession(
    sessionId = sessionId,
    presetId = presetId,
    presetName = presetName,
    presetSnapshotJson = presetSnapshotJson,
    startedWallMs = startedWallMs,
    completedWallMs = completedWallMs,
    intendedDurationMs = intendedDurationMs,
    actualActiveDurationMs = actualActiveDurationMs,
    pausedDurationMs = pausedDurationMs,
    overtimeDurationMs = overtimeDurationMs,
    completionStatus = CompletionStatus.valueOf(completionStatus),
    note = note,
    tags = AppJson.decodeFromString(tagsJson),
    moodBefore = moodBefore,
    moodAfter = moodAfter,
)

// ---- Sound + attribution --------------------------------------------------------------------

fun SoundAsset.toEntity() = SoundEntity(
    id = id,
    name = name,
    category = category.name,
    sourceType = sourceType.name,
    fileUri = fileUri,
    generatorConfigJson = generatorConfig?.let { AppJson.encodeToString(GeneratorConfig.serializer(), it) },
    imageAssetId = imageAssetId,
    durationMs = durationMs,
    defaultVolume = defaultVolume,
    tagsJson = AppJson.encodeToString(tags),
    description = description,
    rolesJson = AppJson.encodeToString(roles.map { it.name }),
    attributionId = attributionId,
    favorite = favorite,
)

fun SoundEntity.toDomain() = SoundAsset(
    id = id,
    name = name,
    category = SoundCategory.fromId(category),
    sourceType = SoundSourceType.valueOf(sourceType),
    fileUri = fileUri,
    generatorConfig = generatorConfigJson?.let { AppJson.decodeFromString(GeneratorConfig.serializer(), it) },
    imageAssetId = imageAssetId,
    durationMs = durationMs,
    defaultVolume = defaultVolume,
    tags = AppJson.decodeFromString(tagsJson),
    description = description,
    roles = AppJson.decodeFromString<List<String>>(rolesJson).mapNotNull {
        runCatching { SoundRole.valueOf(it) }.getOrNull()
    },
    attributionId = attributionId,
    favorite = favorite,
)

fun Attribution.toEntity() = AttributionEntity(
    id = id, creator = creator, title = title, sourceName = sourceName,
    sourcePage = sourcePage, licenseName = licenseName, licensePage = licensePage,
    modifications = modifications,
)

fun AttributionEntity.toDomain() = Attribution(
    id = id, creator = creator, title = title, sourceName = sourceName,
    sourcePage = sourcePage, licenseName = licenseName, licensePage = licensePage,
    modifications = modifications,
)
