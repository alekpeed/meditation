package com.meditation.core

import kotlinx.serialization.Serializable

/** Terminal outcome of a session, or null while it is still active. */
enum class TerminalKind { COMPLETED, CANCELLED }

/**
 * The authoritative, persistable runtime state of an active session.
 *
 * Timing is reconstructed entirely from monotonic anchors — never from a decrementing counter — so
 * the session survives process death, activity recreation, and wall-clock changes. Recompute the
 * view with [SessionEngine.project]; drive side effects with [SessionEngine.advance].
 *
 * Active-time model:
 *  - While [running], active elapsed = [activeBaseMs] + (now - [runAnchorElapsedMs]).
 *  - While paused, active elapsed = [activeBaseMs] (frozen).
 *  Pausing folds the current run segment into [activeBaseMs]; resuming re-anchors [runAnchorElapsedMs].
 *
 * Stage model:
 *  - [currentStageIndex] == -1 means the preparation countdown is running.
 *  - [stageStartActiveMs] is the active-elapsed value at which the current phase (prep or a stage)
 *    began. Skipping and per-stage extension move these anchors rather than mutating a schedule.
 */
@Serializable
data class ActiveSessionState(
    val sessionId: String,
    val preset: SessionPreset,
    val running: Boolean,
    val terminal: TerminalKind?,
    val activeBaseMs: Long,
    val runAnchorElapsedMs: Long,
    val currentStageIndex: Int,
    val stageStartActiveMs: Long,
    val currentStageAddedMs: Long,
    val inOvertime: Boolean,
    val startedWallMs: Long,
    val pausedAccumulatedMs: Long,
    val lastPauseElapsedMs: Long?,
    val firedSoundKeys: Set<String>,
    val completionRecordCreated: Boolean,
    val lastUpdateWallMs: Long,
) {
    val isActive: Boolean get() = terminal == null

    fun activeElapsedMs(nowElapsed: Long): Long =
        if (running) activeBaseMs + (nowElapsed - runAnchorElapsedMs) else activeBaseMs
}

enum class SoundEventKind { OPENING, INTERVAL, TRANSITION, CLOSING, FINAL }

/** A sound the engine has determined must play now. The audio layer renders it; the engine dedupes it. */
data class SoundEvent(
    val key: String,
    val soundId: String?,
    val kind: SoundEventKind,
    val strikeCount: Int = 1,
    val strikeSpacingMs: Long = 0,
    val role: SoundRole,
)

/** Display projection consumed by the UI. Purely derived; the UI never mutates timing. */
data class SessionSnapshot(
    val sessionId: String,
    val presetName: String,
    val status: SessionStatus,
    val stageIndex: Int,
    val stageCount: Int,
    val stageName: String?,
    /** Countdown remaining for the whole session, ms. 0 for open-ended/overtime. */
    val remainingMs: Long,
    /** Count-up value for open-ended stages and overtime, ms. */
    val countUpMs: Long,
    val totalElapsedMs: Long,
    val stageElapsedMs: Long,
    val stageRemainingMs: Long?,
    val nextIntervalInMs: Long?,
    val running: Boolean,
    val overtime: Boolean,
)

/** Result of a single [SessionEngine.advance] step. */
data class AdvanceResult(
    val state: ActiveSessionState,
    val events: List<SoundEvent>,
    val justCompleted: Boolean,
)

/**
 * Stateless engine operating on [ActiveSessionState]. All methods are pure functions of their
 * inputs plus the injected monotonic [Clock], which makes every timing rule unit-testable.
 */
class SessionEngine(private val clock: Clock) {

    // ---- Lifecycle transitions -------------------------------------------------------------

    fun start(sessionId: String, preset: SessionPreset): AdvanceResult {
        require(preset.stages.isNotEmpty()) { "A session needs at least one stage" }
        require(preset.stages.dropLast(1).none { it.durationMs == null }) {
            "Only the final stage may be open-ended"
        }
        val now = clock.elapsedRealtimeMs()
        val hasPrep = preset.preparationMs > 0
        val state = ActiveSessionState(
            sessionId = sessionId,
            preset = preset,
            running = true,
            terminal = null,
            activeBaseMs = 0,
            runAnchorElapsedMs = now,
            currentStageIndex = if (hasPrep) -1 else 0,
            stageStartActiveMs = 0,
            currentStageAddedMs = 0,
            inOvertime = false,
            startedWallMs = clock.wallClockMs(),
            pausedAccumulatedMs = 0,
            lastPauseElapsedMs = null,
            firedSoundKeys = emptySet(),
            completionRecordCreated = false,
            lastUpdateWallMs = clock.wallClockMs(),
        )
        return advance(state)
    }

    fun pause(state: ActiveSessionState): ActiveSessionState {
        if (!state.running || !state.isActive) return state // idempotent
        val now = clock.elapsedRealtimeMs()
        val active = state.activeElapsedMs(now)
        return state.copy(
            running = false,
            activeBaseMs = active,
            lastPauseElapsedMs = now,
            lastUpdateWallMs = clock.wallClockMs(),
        )
    }

    fun resume(state: ActiveSessionState): AdvanceResult {
        if (state.running || !state.isActive) return AdvanceResult(state, emptyList(), false) // idempotent
        val now = clock.elapsedRealtimeMs()
        val pausedFor = state.lastPauseElapsedMs?.let { now - it } ?: 0
        val resumed = state.copy(
            running = true,
            runAnchorElapsedMs = now,
            pausedAccumulatedMs = state.pausedAccumulatedMs + pausedFor,
            lastPauseElapsedMs = null,
            lastUpdateWallMs = clock.wallClockMs(),
        )
        return advance(resumed)
    }

    /** Add time to the currently active stage (extends the session end). Reschedule alarm after this. */
    fun extend(state: ActiveSessionState, millis: Long): ActiveSessionState {
        if (!state.isActive || millis <= 0) return state
        // Extending during overtime re-opens the countdown by turning the overtime overflow into
        // additional stage time.
        val active = state.activeElapsedMs(clock.elapsedRealtimeMs())
        return if (state.inOvertime) {
            val overflow = active - state.stageStartActiveMs -
                ((state.preset.stages.last().durationMs ?: 0) + state.currentStageAddedMs)
            state.copy(
                inOvertime = false,
                currentStageAddedMs = state.currentStageAddedMs + overflow + millis,
                lastUpdateWallMs = clock.wallClockMs(),
            )
        } else {
            state.copy(
                currentStageAddedMs = state.currentStageAddedMs + millis,
                lastUpdateWallMs = clock.wallClockMs(),
            )
        }
    }

    /** Skip the remainder of the current stage. No-op during preparation or when already terminal. */
    fun skipStage(state: ActiveSessionState): AdvanceResult {
        if (!state.isActive || state.currentStageIndex < 0) {
            return AdvanceResult(state, emptyList(), false)
        }
        val idx = state.currentStageIndex
        val stage = state.preset.stages[idx]
        val active = state.activeElapsedMs(clock.elapsedRealtimeMs())
        val stageElapsed = active - state.stageStartActiveMs
        val effDur = (stage.durationMs ?: stageElapsed) + state.currentStageAddedMs
        // Fast-forward the stage-start anchor so this stage's window closes exactly at `active`,
        // then let advance() fire the transition/closing and enter the next stage or complete.
        val fastForwarded = state.copy(
            stageStartActiveMs = state.stageStartActiveMs - (effDur - stageElapsed).coerceAtLeast(0),
            inOvertime = false,
        )
        return advance(fastForwarded)
    }

    fun finish(state: ActiveSessionState, cancelled: Boolean = false): AdvanceResult {
        if (!state.isActive) return AdvanceResult(state, emptyList(), false) // idempotent finish
        val now = clock.elapsedRealtimeMs()
        val active = state.activeElapsedMs(now)
        val finished = state.copy(
            running = false,
            activeBaseMs = active,
            terminal = if (cancelled) TerminalKind.CANCELLED else TerminalKind.COMPLETED,
            inOvertime = false,
            lastUpdateWallMs = clock.wallClockMs(),
        )
        val events = if (cancelled || state.firedSoundKeys.contains(KEY_FINAL)) {
            emptyList()
        } else {
            listOf(finalEvent(state.preset))
        }
        val withFinal = finished.copy(firedSoundKeys = finished.firedSoundKeys + KEY_FINAL)
        return AdvanceResult(withFinal, events, justCompleted = !cancelled)
    }

    // ---- Advancement (side-effect resolution) ---------------------------------------------

    /**
     * Resolve everything that should have happened up to now: fire opening/interval/transition/
     * closing/final sounds exactly once each, advance stage anchors, and detect completion.
     * Idempotent — calling repeatedly with no time elapsed yields no new events.
     */
    fun advance(input: ActiveSessionState): AdvanceResult {
        var state = input
        val events = mutableListOf<SoundEvent>()
        var justCompleted = false
        val now = clock.elapsedRealtimeMs()

        var guard = 0
        loop@ while (guard++ < state.preset.stages.size + 4) {
            if (!state.isActive) break
            val active = state.activeElapsedMs(now)

            if (state.currentStageIndex < 0) { // preparation
                if (active - state.stageStartActiveMs >= state.preset.preparationMs) {
                    state = state.copy(
                        currentStageIndex = 0,
                        stageStartActiveMs = state.stageStartActiveMs + state.preset.preparationMs,
                        currentStageAddedMs = 0,
                    )
                    continue@loop
                }
                break
            }

            val idx = state.currentStageIndex
            val stage = state.preset.stages[idx]
            val stageElapsed = active - state.stageStartActiveMs

            // Opening sound for this stage, once.
            val openKey = "open-$idx"
            if (openKey !in state.firedSoundKeys) {
                state = state.copy(firedSoundKeys = state.firedSoundKeys + openKey)
                stage.openingSoundId?.let {
                    events += SoundEvent(openKey, it, SoundEventKind.OPENING, role = SoundRole.OPENING)
                }
            }

            // Interval bells whose offsets have elapsed, once each.
            val intervalSound = IntervalScheduler.soundIdOf(stage.intervalPlan)
            if (intervalSound != null) {
                val strikes = IntervalScheduler.strikeCountOf(stage.intervalPlan)
                IntervalScheduler.firedOffsets(stage.intervalPlan, stageElapsed, effectiveDuration(stage, state)).forEach { off ->
                    val key = "interval-$idx-$off"
                    if (key !in state.firedSoundKeys) {
                        state = state.copy(firedSoundKeys = state.firedSoundKeys + key)
                        events += SoundEvent(key, intervalSound, SoundEventKind.INTERVAL, strikes, role = SoundRole.INTERVAL)
                    }
                }
            }

            if (state.inOvertime) break

            val effDur = effectiveDuration(stage, state)
            if (effDur == null) break // open-ended final stage: count up, never auto-complete
            if (stageElapsed < effDur) break // still within the stage

            // Stage window closed: fire this stage's closing sound once.
            val closeKey = "close-$idx"
            if (closeKey !in state.firedSoundKeys) {
                state = state.copy(firedSoundKeys = state.firedSoundKeys + closeKey)
                stage.closingSoundId?.let {
                    val kind = if (idx == state.preset.stages.lastIndex) SoundEventKind.CLOSING else SoundEventKind.TRANSITION
                    events += SoundEvent(closeKey, it, kind, role = if (kind == SoundEventKind.CLOSING) SoundRole.CLOSING else SoundRole.TRANSITION)
                }
            }

            if (idx == state.preset.stages.lastIndex) {
                val mode = state.preset.overtimeMode
                if (mode == OvertimeMode.STOP) {
                    val res = completeNow(state, now)
                    state = res; justCompleted = true
                    if (KEY_FINAL !in input.firedSoundKeys) events += finalEvent(state.preset)
                    break
                } else {
                    state = state.copy(inOvertime = true)
                    break
                }
            } else {
                val nextIdx = idx + 1
                state = state.copy(
                    currentStageIndex = nextIdx,
                    stageStartActiveMs = state.stageStartActiveMs + effDur,
                    currentStageAddedMs = 0,
                )
                continue@loop
            }
        }

        state = state.copy(lastUpdateWallMs = clock.wallClockMs())
        return AdvanceResult(state, events, justCompleted)
    }

    private fun completeNow(state: ActiveSessionState, now: Long): ActiveSessionState = state.copy(
        running = false,
        activeBaseMs = state.activeElapsedMs(now),
        terminal = TerminalKind.COMPLETED,
        firedSoundKeys = state.firedSoundKeys + KEY_FINAL,
    )

    private fun effectiveDuration(stage: SessionStage, state: ActiveSessionState): Long? =
        stage.durationMs?.let { it + state.currentStageAddedMs }

    private fun finalEvent(preset: SessionPreset) = SoundEvent(
        key = KEY_FINAL,
        soundId = preset.finalSoundId,
        kind = SoundEventKind.FINAL,
        strikeCount = preset.completionStrikeCount,
        strikeSpacingMs = preset.completionStrikeSpacingMs,
        role = SoundRole.CLOSING,
    )

    // ---- Projection (read model) ----------------------------------------------------------

    fun project(state: ActiveSessionState): SessionSnapshot {
        val now = clock.elapsedRealtimeMs()
        val active = state.activeElapsedMs(now)
        val stages = state.preset.stages

        when (state.terminal) {
            TerminalKind.COMPLETED -> return terminalSnapshot(state, SessionStatus.COMPLETED, active)
            TerminalKind.CANCELLED -> return terminalSnapshot(state, SessionStatus.CANCELLED, active)
            null -> Unit
        }

        if (state.currentStageIndex < 0) {
            val prepRemaining = (state.preset.preparationMs - (active - state.stageStartActiveMs)).coerceAtLeast(0)
            return SessionSnapshot(
                sessionId = state.sessionId,
                presetName = state.preset.name,
                status = if (state.running) SessionStatus.PREPARING else SessionStatus.PAUSED,
                stageIndex = -1,
                stageCount = stages.size,
                stageName = "Preparation",
                remainingMs = prepRemaining + remainingAfterPrep(state),
                countUpMs = 0,
                totalElapsedMs = active,
                stageElapsedMs = active - state.stageStartActiveMs,
                stageRemainingMs = prepRemaining,
                nextIntervalInMs = null,
                running = state.running,
                overtime = false,
            )
        }

        // Walk from the stored anchors to find the display stage (pure; reflects skips/extension).
        var idx = state.currentStageIndex
        var stageStart = state.stageStartActiveMs
        var added = state.currentStageAddedMs
        while (idx < stages.lastIndex) {
            val dur = stages[idx].durationMs?.plus(added) ?: break
            if (active - stageStart < dur) break
            stageStart += dur; idx++; added = 0
        }
        val stage = stages[idx]
        val stageElapsed = (active - stageStart).coerceAtLeast(0)
        val effDur = stage.durationMs?.plus(if (idx == state.currentStageIndex) added else 0)

        val overtime = state.inOvertime || (effDur != null && stageElapsed >= effDur && idx == stages.lastIndex &&
            state.preset.overtimeMode != OvertimeMode.STOP)
        val openEnded = stage.durationMs == null

        val status = when {
            !state.running -> SessionStatus.PAUSED
            overtime -> SessionStatus.OVERTIME
            else -> SessionStatus.RUNNING
        }

        val stageRemaining = effDur?.let { (it - stageElapsed).coerceAtLeast(0) }
        val laterStages = stages.drop(idx + 1).sumOf { it.durationMs ?: 0L }
        val remaining = if (openEnded || overtime) 0 else (stageRemaining ?: 0) + laterStages

        val countUp = when {
            overtime && effDur != null -> stageElapsed - effDur
            openEnded -> stageElapsed
            else -> 0
        }
        val nextInterval = IntervalScheduler.nextOffsetAfter(stage.intervalPlan, stageElapsed, effDur)
            ?.let { it - stageElapsed }

        return SessionSnapshot(
            sessionId = state.sessionId,
            presetName = state.preset.name,
            status = status,
            stageIndex = idx,
            stageCount = stages.size,
            stageName = stage.name,
            remainingMs = remaining,
            countUpMs = countUp,
            totalElapsedMs = active,
            stageElapsedMs = stageElapsed,
            stageRemainingMs = stageRemaining,
            nextIntervalInMs = nextInterval,
            running = state.running,
            overtime = overtime,
        )
    }

    private fun remainingAfterPrep(state: ActiveSessionState): Long =
        state.preset.stages.sumOf { it.durationMs ?: 0L }

    private fun terminalSnapshot(state: ActiveSessionState, status: SessionStatus, active: Long) =
        SessionSnapshot(
            sessionId = state.sessionId,
            presetName = state.preset.name,
            status = status,
            stageIndex = state.currentStageIndex,
            stageCount = state.preset.stages.size,
            stageName = null,
            remainingMs = 0,
            countUpMs = 0,
            totalElapsedMs = active,
            stageElapsedMs = 0,
            stageRemainingMs = 0,
            nextIntervalInMs = null,
            running = false,
            overtime = false,
        )

    companion object {
        const val KEY_FINAL = "final"
    }
}
