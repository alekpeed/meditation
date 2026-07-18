package com.meditation.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.Test

private fun min(m: Long) = m * 60_000L
private fun sec(s: Long) = s * 1_000L

private fun stage(
    id: String = "s",
    name: String = id,
    durationMs: Long? = min(10),
    opening: String? = null,
    closing: String? = null,
    interval: IntervalPlan = IntervalPlan.None,
) = SessionStage(id, name, durationMs, opening, closing, emptyList(), interval)

private fun preset(
    stages: List<SessionStage>,
    prep: Long = 0,
    finalSound: String? = "final-bell",
    overtime: OvertimeMode = OvertimeMode.STOP,
    strikeCount: Int = 1,
) = SessionPreset(
    id = "p1", name = "Test", preparationMs = prep, stages = stages,
    finalSoundId = finalSound, completionStrikeCount = strikeCount, overtimeMode = overtime,
)

class SessionEngineTest {

    @Test fun `single stage countdown reports correct remaining`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        var s = engine.start("id", preset(listOf(stage(durationMs = min(60))))).state

        clock.advance(min(10))
        val snap = engine.project(s)
        assertEquals(SessionStatus.RUNNING, snap.status)
        assertEquals(min(50), snap.remainingMs)
        assertEquals(min(10), snap.totalElapsedMs)
    }

    @Test fun `wall-clock jump does not change elapsed session duration`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val s = engine.start("id", preset(listOf(stage(durationMs = min(60))))).state

        clock.elapsed += min(10)          // 10 real minutes pass (monotonic)
        clock.wall += min(180)            // wall clock jumps forward 3h (DST / manual set / NTP)
        val a = engine.project(s)
        clock.wall -= min(400)            // wall clock jumps backward
        val b = engine.project(s)

        assertEquals(min(50), a.remainingMs)
        assertEquals(min(50), b.remainingMs) // unaffected by wall-clock chaos (rule #2)
    }

    @Test fun `pause freezes and resume excludes paused time`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        var s = engine.start("id", preset(listOf(stage(durationMs = min(10))))).state

        clock.advance(min(3))
        s = engine.pause(s)
        val paused = engine.project(s)
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertEquals(min(7), paused.remainingMs)

        clock.advance(min(5))             // 5 minutes pass while paused
        assertEquals(min(7), engine.project(s).remainingMs) // still frozen

        s = engine.resume(s).state
        clock.advance(min(3))             // 3 more active minutes
        val after = engine.project(s)
        assertEquals(min(4), after.remainingMs)          // 10 - (3+3) active
        assertEquals(min(5), s.pausedAccumulatedMs)      // paused time tracked for history
    }

    @Test fun `opening and closing sounds each fire exactly once`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val start = engine.start("id", preset(listOf(stage(durationMs = min(5), opening = "open", closing = "close"))))
        assertEquals(listOf("open"), start.events.map { it.soundId })

        clock.advance(min(5))
        val done = engine.advance(start.state)
        val ids = done.events.map { it.soundId }
        assertTrue("close" in ids)
        assertTrue("final-bell" in ids)
        assertTrue(done.justCompleted)

        // Re-advancing a completed session fires nothing further.
        val again = engine.advance(done.state)
        assertTrue(again.events.isEmpty())
    }

    @Test fun `final bell fires once even with duplicate completion calls`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val start = engine.start("id", preset(listOf(stage(durationMs = min(1)))))
        clock.advance(min(1))

        val first = engine.advance(start.state)                       // service tick completes it
        val viaAlarm = engine.advance(first.state)                    // alarm fires on same state
        val viaFinish = engine.finish(viaAlarm.state)                 // user hits finish too

        val finalCount = first.events.count { it.kind == SoundEventKind.FINAL } +
            viaAlarm.events.count { it.kind == SoundEventKind.FINAL } +
            viaFinish.events.count { it.kind == SoundEventKind.FINAL }
        assertEquals(1, finalCount) // acceptance #8
    }

    @Test fun `overtime counts up and does not auto-complete`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val start = engine.start("id", preset(listOf(stage(durationMs = min(10))), overtime = OvertimeMode.COUNT_UP))

        clock.advance(min(12))
        val res = engine.advance(start.state)
        assertFalse(res.justCompleted)
        assertNull(res.events.firstOrNull { it.kind == SoundEventKind.FINAL })

        val snap = engine.project(res.state)
        assertEquals(SessionStatus.OVERTIME, snap.status)
        assertEquals(min(2), snap.countUpMs)

        val finished = engine.finish(res.state)
        assertEquals(1, finished.events.count { it.kind == SoundEventKind.FINAL })
    }

    @Test fun `extend adds time to the active stage`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        var s = engine.start("id", preset(listOf(stage(durationMs = min(10))))).state
        clock.advance(min(5))
        assertEquals(min(5), engine.project(s).remainingMs)

        s = engine.extend(s, min(5))
        assertEquals(min(10), engine.project(s).remainingMs) // end pushed out

        clock.advance(min(10))
        assertTrue(engine.advance(s).justCompleted)
    }

    @Test fun `multi-stage advances and fires transition then closing`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val p = preset(listOf(
            stage("a", durationMs = min(2), opening = "openA", closing = "toB"),
            stage("b", durationMs = min(3), opening = "openB", closing = "endB"),
        ))
        val start = engine.start("id", p)
        assertEquals(listOf("openA"), start.events.map { it.soundId })

        clock.advance(min(2))
        val t = engine.advance(start.state)
        assertEquals(SoundEventKind.TRANSITION, t.events.first { it.soundId == "toB" }.kind)
        assertTrue("openB" in t.events.map { it.soundId })
        assertEquals(1, engine.project(t.state).stageIndex)
        assertEquals(min(3), engine.project(t.state).remainingMs)

        clock.advance(min(3))
        val end = engine.advance(t.state)
        assertEquals(SoundEventKind.CLOSING, end.events.first { it.soundId == "endB" }.kind)
        assertTrue(end.justCompleted)
    }

    @Test fun `interval bells fire at offsets without duplication`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val plan = IntervalPlan.EveryXMinutes(min(2), "ping")
        val start = engine.start("id", preset(listOf(stage(durationMs = min(10), interval = plan))))

        clock.advance(min(5))
        val a = engine.advance(start.state)
        assertEquals(2, a.events.count { it.kind == SoundEventKind.INTERVAL }) // at 2m and 4m
        assertEquals(min(1), engine.project(a.state).nextIntervalInMs)          // next at 6m

        clock.advance(min(4)) // now at 9m
        val b = engine.advance(a.state)
        assertEquals(2, b.events.count { it.kind == SoundEventKind.INTERVAL }) // 6m and 8m, not re-firing 2/4
    }

    @Test fun `skip stage jumps to the next stage immediately`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val p = preset(listOf(
            stage("a", durationMs = min(5), closing = "toB"),
            stage("b", durationMs = min(5), opening = "openB"),
        ))
        var s = engine.start("id", p).state
        clock.advance(min(1))
        val skipped = engine.skipStage(s)
        assertTrue("toB" in skipped.events.map { it.soundId })
        assertTrue("openB" in skipped.events.map { it.soundId })
        assertEquals(1, engine.project(skipped.state).stageIndex)
        assertEquals(min(5), engine.project(skipped.state).remainingMs)
    }

    @Test fun `preparation runs before first stage opening`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val start = engine.start("id", preset(listOf(stage(durationMs = min(5), opening = "open")), prep = sec(30)))
        assertTrue(start.events.isEmpty()) // nothing fires during prep
        assertEquals(SessionStatus.PREPARING, engine.project(start.state).status)

        clock.advance(sec(30))
        val afterPrep = engine.advance(start.state)
        assertEquals(listOf("open"), afterPrep.events.map { it.soundId })
        assertEquals(0, engine.project(afterPrep.state).stageIndex)
    }

    @Test fun `open-ended stage counts up and never auto-completes`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val start = engine.start("id", preset(listOf(stage(durationMs = null, opening = "open"))))
        clock.advance(min(45))
        val res = engine.advance(start.state)
        assertFalse(res.justCompleted)
        val snap = engine.project(res.state)
        assertEquals(SessionStatus.RUNNING, snap.status)
        assertEquals(min(45), snap.countUpMs)

        val finished = engine.finish(res.state)
        assertTrue(finished.justCompleted)
    }

    @Test fun `finish is idempotent and produces one terminal state`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val start = engine.start("id", preset(listOf(stage(durationMs = min(10)))))
        clock.advance(min(4))
        val f1 = engine.finish(start.state)
        val f2 = engine.finish(f1.state)
        assertEquals(TerminalKind.COMPLETED, f1.state.terminal)
        assertEquals(TerminalKind.COMPLETED, f2.state.terminal)
        assertTrue(f2.events.isEmpty())
    }

    @Test fun `completed session builds a single accurate history record`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        var s = engine.start("id", preset(listOf(stage(durationMs = min(10))))).state
        clock.advance(min(2)); s = engine.pause(s)
        clock.advance(min(3)); s = engine.resume(s).state // 3 min paused
        clock.advance(min(8))
        val done = engine.advance(s)
        val record = buildCompletedSession(done.state, clock.wallClockMs())

        assertEquals(min(10), record.intendedDurationMs)
        assertEquals(min(10), record.actualActiveDurationMs)
        assertEquals(min(3), record.pausedDurationMs)
        assertEquals(CompletionStatus.COMPLETED, record.completionStatus)
    }
}
