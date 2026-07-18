package com.meditation.core

data class CompletedSession(
    val sessionId: String,
    val presetId: String,
    val presetName: String,
    val presetSnapshotJson: String? = null,
    val startedWallMs: Long,
    val completedWallMs: Long,
    val intendedDurationMs: Long,
    val actualActiveDurationMs: Long,
    val pausedDurationMs: Long,
    val overtimeDurationMs: Long,
    val completionStatus: CompletionStatus,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val moodBefore: Int? = null,
    val moodAfter: Int? = null,
)

/**
 * Build the single history record for a terminal session. Callers must guard on
 * [ActiveSessionState.completionRecordCreated] so exactly one record is ever written, satisfying
 * acceptance criterion #9 (a completed session creates one history entry).
 */
fun buildCompletedSession(
    state: ActiveSessionState,
    completedWallMs: Long,
    note: String? = null,
    tags: List<String> = emptyList(),
    moodBefore: Int? = null,
    moodAfter: Int? = null,
): CompletedSession {
    require(!state.isActive) { "Session is not terminal" }
    val intended = state.preset.plannedDurationMs
    val active = state.activeBaseMs
    val overtime = (active - intended).coerceAtLeast(0)
    val status = when (state.terminal) {
        TerminalKind.CANCELLED -> CompletionStatus.CANCELLED
        else -> if (active + 500 < intended) CompletionStatus.FINISHED_EARLY else CompletionStatus.COMPLETED
    }
    return CompletedSession(
        sessionId = state.sessionId,
        presetId = state.preset.id,
        presetName = state.preset.name,
        startedWallMs = state.startedWallMs,
        completedWallMs = completedWallMs,
        intendedDurationMs = intended,
        actualActiveDurationMs = active,
        pausedDurationMs = state.pausedAccumulatedMs,
        overtimeDurationMs = overtime,
        completionStatus = status,
        note = note,
        tags = tags,
        moodBefore = moodBefore,
        moodAfter = moodAfter,
    )
}
