package com.meditation.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L

class RecommendationsTest {

    private fun session(
        id: String,
        presetId: String,
        dayOffset: Long,
        activeMs: Long = 600_000,
        status: CompletionStatus = CompletionStatus.COMPLETED,
    ) = CompletedSession(
        sessionId = id, presetId = presetId, presetName = presetId,
        startedWallMs = dayOffset * DAY + 9 * 3_600_000,
        completedWallMs = dayOffset * DAY + 9 * 3_600_000 + activeMs,
        intendedDurationMs = activeMs, actualActiveDurationMs = activeMs,
        pausedDurationMs = 0, overtimeDurationMs = 0, completionStatus = status,
    )

    private fun preset(id: String) = SessionPreset(id = id, name = "Preset $id", stages = listOf(SessionStage("s", "S", 600_000)))

    private val now = 100 * DAY

    @Test fun `empty history yields no recommendation`() {
        assertNull(Recommendations.suggest(emptyList(), emptyList(), now))
    }

    @Test fun `three recent sessions with the same preset suggest repeating it`() {
        val history = listOf(
            session("a", "p1", 100), session("b", "p1", 99), session("c", "p1", 98),
        )
        val rec = Recommendations.suggest(history, listOf(preset("p1")), now)
        assertEquals(RecommendationKind.REPEAT_FAVORITE, rec?.kind)
        assertEquals("p1", rec?.presetId)
        assertTrue(rec!!.message.contains("3 times"))
    }

    @Test fun `a preset used twice but not in the last 10 days is a welcome-back`() {
        val history = listOf(session("a", "p2", 50), session("b", "p2", 40))
        val rec = Recommendations.suggest(history, listOf(preset("p2")), now)
        assertEquals(RecommendationKind.WELCOME_BACK, rec?.kind)
        assertEquals("p2", rec?.presetId)
    }

    @Test fun `recent activity takes priority over a lapsed preset`() {
        val history = listOf(
            session("old1", "p2", 50), session("old2", "p2", 40), // lapsed candidate
            session("a", "p1", 100), session("b", "p1", 99), session("c", "p1", 98), // recent repeat
        )
        val rec = Recommendations.suggest(history, listOf(preset("p1"), preset("p2")), now)
        assertEquals(RecommendationKind.REPEAT_FAVORITE, rec?.kind)
        assertEquals("p1", rec?.presetId)
    }

    @Test fun `no repeat pattern falls back to typical duration`() {
        val history = listOf(
            session("a", "p1", 100, activeMs = 600_000),
            session("b", "p2", 95, activeMs = 900_000),
            session("c", "p3", 90, activeMs = 1_200_000),
        )
        val rec = Recommendations.suggest(history, listOf(preset("p1"), preset("p2"), preset("p3")), now)
        assertEquals(RecommendationKind.TYPICAL_DURATION, rec?.kind)
        assertEquals(900_000L, rec?.suggestedDurationMs) // median of 600k/900k/1200k
    }

    @Test fun `a single session is not enough for any recommendation`() {
        assertNull(Recommendations.suggest(listOf(session("a", "p1", 100)), listOf(preset("p1")), now))
    }

    @Test fun `cancelled sessions do not count toward a repeat pattern`() {
        val history = listOf(
            session("a", "p1", 100, status = CompletionStatus.CANCELLED),
            session("b", "p1", 99, status = CompletionStatus.CANCELLED),
            session("c", "p1", 98, status = CompletionStatus.CANCELLED),
        )
        // All cancelled -> no finished sessions -> null, not a false REPEAT_FAVORITE.
        assertNull(Recommendations.suggest(history, listOf(preset("p1")), now))
    }

    @Test fun `a preset repeated but since deleted is ignored, not crashed on`() {
        val history = listOf(
            session("a", "ghost", 100), session("b", "ghost", 99), session("c", "ghost", 98),
        )
        val rec = Recommendations.suggest(history, emptyList(), now)
        // "ghost" isn't in the presets list, so tier 1/2 skip it; falls through to duration fallback
        // only once there are >= 2 finished sessions overall, which there are here.
        assertEquals(RecommendationKind.TYPICAL_DURATION, rec?.kind)
    }
}
