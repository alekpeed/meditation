package com.meditation.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Proves the full runtime state survives a JSON round-trip. This is the persistence contract that
 * makes process-death recovery possible: the Android layer stores exactly this string and rebuilds
 * the session from it.
 */
class SerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test fun `active session state round-trips through JSON`() {
        val clock = FakeClock()
        val engine = SessionEngine(clock)
        val preset = SessionPreset(
            id = "p", name = "Round trip", preparationMs = 30_000,
            stages = listOf(
                SessionStage("a", "Settle", 120_000, openingSoundId = "bell", closingSoundId = "gong",
                    intervalPlan = IntervalPlan.EveryXMinutes(60_000, "ping", 2)),
                SessionStage("b", "Open", null, openingSoundId = "bowl"),
            ),
            finalSoundId = "final", completionStrikeCount = 3,
        )
        var s = engine.start("sid", preset).state
        clock.advance(45_000)
        s = engine.advance(s).state
        s = engine.pause(s)

        val text = json.encodeToString(s)
        val restored = json.decodeFromString<ActiveSessionState>(text)

        assertEquals(s, restored)
        // And the engine produces an identical projection from the restored state.
        assertEquals(engine.project(s), engine.project(restored))
        assertTrue(restored.firedSoundKeys.isNotEmpty())
    }

    @Test fun `interval plan polymorphism survives serialization`() {
        val plans: List<IntervalPlan> = listOf(
            IntervalPlan.None,
            IntervalPlan.EveryXMinutes(60_000, "x"),
            IntervalPlan.CustomTimestamps(listOf(1000, 2000), "y"),
            IntervalPlan.Progressive(1000, 500, "z"),
        )
        plans.forEach { plan ->
            val text = json.encodeToString(plan)
            assertEquals(plan, json.decodeFromString<IntervalPlan>(text))
        }
    }
}
