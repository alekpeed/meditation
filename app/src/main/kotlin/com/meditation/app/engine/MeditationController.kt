package com.meditation.app.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import com.meditation.app.audio.AudioController
import com.meditation.app.alarm.AlarmScheduler
import com.meditation.app.data.ActiveSessionRepository
import com.meditation.app.data.HistoryRepository
import com.meditation.app.data.PreferencesRepository
import com.meditation.app.service.MeditationService
import com.meditation.app.service.NotificationController
import com.meditation.core.ActiveSessionState
import com.meditation.core.AdvanceResult
import com.meditation.core.SessionEngine
import com.meditation.core.SessionPreset
import com.meditation.core.SessionSnapshot
import com.meditation.core.SoundEvent
import com.meditation.core.TerminalKind
import com.meditation.core.buildCompletedSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The authoritative session orchestrator — the Kotlin owner of session truth (brief rule #1).
 *
 * All timing decisions come from the pure [SessionEngine]; this class binds those decisions to
 * Android: it persists on every transition, drives audio/notification/alarm side effects, runs the
 * event loop that fires interval/stage/final sounds while the screen is off, and rebuilds the
 * session after process death. The UI only sends commands and renders [snapshot].
 */
class MeditationController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val engine: SessionEngine,
    private val activeRepo: ActiveSessionRepository,
    private val historyRepo: HistoryRepository,
    private val prefs: PreferencesRepository,
    private val audio: AudioController,
    private val alarms: AlarmScheduler,
    private val notifications: NotificationController,
    private val dnd: com.meditation.app.service.DndController,
    private val tts: com.meditation.app.audio.TtsController,
    private val clock: com.meditation.core.Clock = AndroidClock,
) {
    private val mutex = Mutex()
    private var state: ActiveSessionState? = null
    private var loopJob: Job? = null

    private val _snapshot = MutableStateFlow<SessionSnapshot?>(null)
    val snapshot: StateFlow<SessionSnapshot?> = _snapshot.asStateFlow()

    val hasActiveSession: Boolean get() = state?.isActive == true

    // Speech is optional presentation. Keep its preference hot in memory so an announcement
    // never performs DataStore I/O while holding the authoritative timing mutex.
    private val spokenCuesEnabled = MutableStateFlow(false)

    init {
        scope.launch {
            prefs.preferences.collect { spokenCuesEnabled.value = it.spokenCuesEnabled }
        }
    }

    // ---- Commands -------------------------------------------------------------------------

    fun startSession(preset: SessionPreset) = scope.launch {
        mutex.withLock {
            if (state?.isActive == true) return@withLock // an active session already exists
            val res = engine.start(UUID.randomUUID().toString(), preset)
            applyResult(res, isStart = true)
            if (prefs.currentPrefs().dndDuringSession) dnd.enable()
            startService()
            startLoop()
        }
    }

    fun pause() = transition { engine.pause(it).let { s -> AdvanceResult(s, emptyList(), false) } }

    fun resume() = scope.launch {
        mutex.withLock {
            val s = state ?: return@withLock
            applyResult(engine.resume(s), isStart = false)
            startLoop()
        }
    }

    fun extendByMinutes(minutes: Int) = transition {
        AdvanceResult(engine.extend(it, minutes * 60_000L), emptyList(), false)
    }

    fun skipStage() = scope.launch {
        mutex.withLock {
            val s = state ?: return@withLock
            applyResult(engine.skipStage(s), isStart = false)
        }
    }

    fun finish(
        cancelled: Boolean = false,
        note: String? = null,
        tags: List<String> = emptyList(),
        moodAfter: Int? = null,
    ) = scope.launch {
        mutex.withLock {
            val s = state ?: return@withLock
            // Stash the user's completion fields so onTerminal records them on the history entry.
            pendingNote = note
            pendingTags = tags
            pendingMood = moodAfter
            applyResult(engine.finish(s, cancelled), isStart = false)
        }
    }

    private var pendingNote: String? = null
    private var pendingTags: List<String> = emptyList()
    private var pendingMood: Int? = null

    // ---- Preview (isolated; safe to call during an active session) ------------------------

    // Preview starts the foreground service too: without it the process is frozen once the user
    // leaves the app and a previewed ambience or noise simply stops. The service takes itself down
    // again when the preview ends (and no session is running). Starting it is safe here because a
    // preview is always begun from a foreground tap.
    fun previewSound(soundId: String, volume: Double) = scope.launch {
        audio.previewSound(soundId, volume)
        if (audio.previewActive.value) runCatching { startService() }
    }

    fun previewMix(layers: List<Pair<String, Double>>) = scope.launch {
        audio.previewMix(layers)
        if (audio.previewActive.value) runCatching { startService() }
    }

    fun stopPreview() = audio.stopPreview()

    /** Called by the alarm receiver as the final-bell fallback. Idempotent via engine dedup. */
    fun onFinalAlarm(sessionId: String) = scope.launch {
        mutex.withLock {
            val s = state ?: activeRepo.load() ?: return@withLock
            if (s.sessionId != sessionId || !s.isActive) return@withLock
            applyResult(engine.advance(s), isStart = false)
        }
    }

    /** Rebuild after process/service recreation. Never replays already-fired sounds. */
    fun restore() = scope.launch {
        mutex.withLock {
            if (state != null) return@withLock
            val persisted = activeRepo.load() ?: return@withLock
            if (!persisted.isActive) { activeRepo.clear(); return@withLock }
            // Recompute: if the end already passed while we were dead, advance() completes it once.
            applyResult(engine.advance(persisted), isStart = false, restoring = true)
            if (state?.isActive == true && state?.running == true) {
                startService()
                startLoop()
            }
        }
    }

    /** Lightweight display refresh for the foreground UI; fires any now-due events too. */
    fun refresh() = scope.launch {
        mutex.withLock {
            val s = state ?: return@withLock
            if (s.isActive && s.running) applyResult(engine.advance(s), isStart = false)
            else _snapshot.value = engine.project(s)
        }
    }

    // ---- Internal -------------------------------------------------------------------------

    private fun transition(op: (ActiveSessionState) -> AdvanceResult) = scope.launch {
        mutex.withLock {
            val s = state ?: return@withLock
            applyResult(op(s), isStart = false)
        }
    }

    private suspend fun applyResult(res: AdvanceResult, isStart: Boolean, restoring: Boolean = false) {
        val previous = state
        state = res.state
        val snap = engine.project(res.state)
        _snapshot.value = snap

        // Play sounds the engine decided on (deduped upstream). Skip on restore to avoid replay.
        if (!restoring && res.events.isNotEmpty()) {
            audio.play(res.events, prefs.currentVolumes())
        }
        if (!restoring) announceStageIfNew(previous?.currentStageIndex, res.state)
        // Keep ambience aligned with the current stage's layers / running state.
        audio.syncForState(res.state, snap, prefs.currentVolumes())

        // Persist BEFORE returning so the session is recoverable at any instant (brief rule #3).
        val expectedEndWall = expectedEndWallMs(res.state, snap)
        activeRepo.save(res.state, expectedEndWall)

        // Final-bell fallback alarm: schedule while running with a known end, cancel otherwise.
        if (res.state.isActive && res.state.running && expectedEndWall != null) {
            alarms.scheduleFinal(res.state.sessionId, expectedEndWall)
        } else {
            alarms.cancel(res.state.sessionId)
        }

        if (!res.state.isActive) onTerminal(res.state)
    }

    private suspend fun onTerminal(terminal: ActiveSessionState) {
        loopJob?.cancel(); loopJob = null
        audio.stopAll()
        dnd.restore()
        alarms.cancel(terminal.sessionId)
        // Exactly one history record (dedup by session id at the DAO layer).
        if (terminal.terminal == TerminalKind.COMPLETED && !terminal.completionRecordCreated) {
            historyRepo.record(
                buildCompletedSession(
                    terminal, clock.wallClockMs(),
                    note = pendingNote, tags = pendingTags, moodAfter = pendingMood,
                ),
            )
            notifications.showCompletion(terminal.preset.name)
            tts.announceComplete(spokenCuesEnabled.value)
        }
        pendingNote = null; pendingTags = emptyList(); pendingMood = null
        activeRepo.clear()
        stopService()
        // Clear the active-session snapshot so the full-screen timer overlay dismisses and the UI
        // returns to normal navigation. Without this the finished session stays on screen forever.
        state = null
        _snapshot.value = null
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (true) {
                val s = mutex.withLock { state } ?: break
                if (!s.isActive || !s.running) break
                val nextDelay = mutex.withLock {
                    val cur = state ?: return@withLock null
                    if (!cur.isActive || !cur.running) return@withLock null
                    val res = engine.advance(cur)
                    applyResultNoLock(res)
                    if (!res.state.isActive || !res.state.running) null
                    else nextWakeDelayMs(engine.project(res.state))
                } ?: break
                delay(nextDelay.coerceIn(50L, 30_000L))
            }
        }
    }

    /** applyResult body without acquiring the mutex (caller already holds it). */
    private suspend fun applyResultNoLock(res: AdvanceResult) {
        val previousIdx = state?.currentStageIndex
        state = res.state
        val snap = engine.project(res.state)
        _snapshot.value = snap
        if (res.events.isNotEmpty()) audio.play(res.events, prefs.currentVolumes())
        announceStageIfNew(previousIdx, res.state)
        audio.syncForState(res.state, snap, prefs.currentVolumes())
        val expectedEndWall = expectedEndWallMs(res.state, snap)
        activeRepo.save(res.state, expectedEndWall)
        if (res.state.isActive && res.state.running && expectedEndWall != null) {
            alarms.scheduleFinal(res.state.sessionId, expectedEndWall)
        }
        if (!res.state.isActive) onTerminal(res.state)
    }

    /** Speak the entered stage's name + cues once, the first time [newState]'s stage index changes. */
    private suspend fun announceStageIfNew(previousIdx: Int?, newState: ActiveSessionState) {
        val newIdx = newState.currentStageIndex
        if (newIdx < 0 || newIdx == previousIdx) return
        val stage = newState.preset.stages.getOrNull(newIdx) ?: return
        tts.announceStage(stage, enabled = spokenCuesEnabled.value)
    }

    /** Next moment the engine needs to act: the sooner of stage end and next interval. */
    private fun nextWakeDelayMs(snap: SessionSnapshot): Long? {
        val candidates = listOfNotNull(snap.stageRemainingMs?.takeIf { it >= 0 }, snap.nextIntervalInMs)
        return candidates.minOrNull()?.plus(15) // small guard past the boundary
    }

    /** Wall-clock instant the session should end, for the fallback alarm. Null for open-ended/overtime. */
    private fun expectedEndWallMs(s: ActiveSessionState, snap: SessionSnapshot): Long? {
        if (!s.isActive || !s.running) return null
        if (snap.remainingMs <= 0) return null
        return clock.wallClockMs() + snap.remainingMs
    }

    private fun startService() {
        val intent = Intent(appContext, MeditationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, MeditationService::class.java))
    }
}
