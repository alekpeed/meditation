package com.meditation.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L

class IntervalRandomTest {
    @Test fun `random intervals are deterministic for a seed and within bounds`() {
        val plan = IntervalPlan.Random(minIntervalMs = 60_000, maxIntervalMs = 120_000, soundId = "x", seed = 42)
        val a = IntervalScheduler.firedOffsets(plan, elapsedMs = 600_000, stageDurationMs = 600_000)
        val b = IntervalScheduler.firedOffsets(plan, elapsedMs = 600_000, stageDurationMs = 600_000)
        assertEquals(a, b) // deterministic → dedup-safe across restarts
        assertTrue(a.isNotEmpty())
        // gaps within [min,max]
        var prev = 0L
        for (o in a) { val gap = o - prev; assertTrue(gap in 60_000..120_000, "gap $gap"); prev = o }
    }

    @Test fun `different seeds give different sequences`() {
        val p1 = IntervalPlan.Random(60_000, 120_000, "x", seed = 1)
        val p2 = IntervalPlan.Random(60_000, 120_000, "x", seed = 2)
        assertTrue(
            IntervalScheduler.firedOffsets(p1, 600_000, 600_000) !=
                IntervalScheduler.firedOffsets(p2, 600_000, 600_000),
        )
    }

    @Test fun `random plan round-trips through serialization`() {
        val plan: IntervalPlan = IntervalPlan.Random(1000, 2000, "y", 7, 2)
        val json = Json { encodeDefaults = true }
        assertEquals(plan, json.decodeFromString<IntervalPlan>(json.encodeToString(plan)))
    }
}

class StatisticsTest {
    private fun session(id: String, dayOffset: Long, activeMs: Long, status: CompletionStatus = CompletionStatus.COMPLETED) =
        CompletedSession(
            sessionId = id, presetId = "p", presetName = "P",
            startedWallMs = dayOffset * DAY + 10 * 3_600_000, // ~10am that day (UTC)
            completedWallMs = dayOffset * DAY + 10 * 3_600_000 + activeMs,
            intendedDurationMs = activeMs, actualActiveDurationMs = activeMs,
            pausedDurationMs = 0, overtimeDurationMs = 0, completionStatus = status,
        )

    @Test fun `totals average and longest exclude cancelled`() {
        val now = 100 * DAY
        val s = Stats.compute(
            listOf(
                session("a", 100, 600_000),
                session("b", 99, 1_200_000),
                session("c", 98, 300_000, CompletionStatus.CANCELLED), // excluded
            ),
            nowWallMs = now, tzOffsetMs = 0,
        )
        assertEquals(2, s.sessionCount)
        assertEquals(1_800_000, s.totalActiveMs)
        assertEquals(900_000, s.averageMs)
        assertEquals(1_200_000, s.longestMs)
    }

    @Test fun `current streak counts consecutive days ending today`() {
        val now = 100 * DAY + 12 * 3_600_000
        val s = Stats.compute(
            listOf(session("a", 100, 60_000), session("b", 99, 60_000), session("c", 98, 60_000), session("d", 96, 60_000)),
            nowWallMs = now, tzOffsetMs = 0,
        )
        assertEquals(3, s.currentStreakDays) // days 98,99,100 (96 breaks it)
        assertEquals(3, s.longestStreakDays)
    }

    @Test fun `streak holds if today empty but yesterday present`() {
        val now = 101 * DAY + 8 * 3_600_000 // nothing logged today (day 101)
        val s = Stats.compute(
            listOf(session("a", 100, 60_000), session("b", 99, 60_000)),
            nowWallMs = now, tzOffsetMs = 0,
        )
        assertEquals(2, s.currentStreakDays)
    }

    @Test fun `weekly and monthly windows`() {
        val now = 100 * DAY
        val s = Stats.compute(
            listOf(session("a", 100, 100), session("b", 96, 100), session("c", 80, 100)),
            nowWallMs = now, tzOffsetMs = 0,
        )
        assertEquals(200, s.weeklyTotalMs)  // days within 7
        assertEquals(300, s.monthlyTotalMs) // within 30
    }

    @Test fun `empty history is all zeros`() {
        val s = Stats.compute(emptyList(), 0, 0)
        assertEquals(0, s.sessionCount)
        assertEquals(0, s.currentStreakDays)
    }
}

class BackupTest {
    @Test fun `backup round-trips`() {
        val backup = Backup(
            exportedWallMs = 123,
            presets = listOf(SessionPreset(id = "p", name = "Sit", stages = listOf(SessionStage("s", "S", 600_000)))),
            history = listOf(
                CompletedSession("h", "p", "Sit", null, 1, 2, 600_000, 600_000, 0, 0, CompletionStatus.COMPLETED),
            ),
            favoriteSoundIds = listOf("bell-clear-small-01"),
            preferencesJson = "{\"bell\":0.8}",
        )
        val restored = Backup.decode(Backup.encode(backup))
        assertNotNull(restored)
        assertEquals(backup, restored)
    }

    @Test fun `garbage decodes to null`() {
        assertNull(Backup.decode("not a backup"))
        assertNull(Backup.decode("{\"random\":true}")) // missing required exportedWallMs
    }
}
