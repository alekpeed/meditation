package com.meditation.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PresetValidationTest {

    private fun preset(vararg stages: SessionStage, name: String = "Ok", prep: Long = 0) =
        SessionPreset(id = "p", name = name, preparationMs = prep, stages = stages.toList(), finalSoundId = "f")

    @Test fun `a well-formed single-stage preset is valid`() {
        val p = preset(SessionStage("s", "Sit", 600_000))
        assertTrue(PresetValidation.isValid(p))
    }

    @Test fun `blank name and no stages are rejected`() {
        val p = SessionPreset(id = "p", name = "  ", stages = emptyList())
        val errors = PresetValidation.validate(p)
        assertTrue(errors.any { it.contains("Name") })
        assertTrue(errors.any { it.contains("at least one stage") })
    }

    @Test fun `non-final open-ended stage is rejected`() {
        val p = preset(
            SessionStage("a", "A", null),          // open-ended but not last
            SessionStage("b", "B", 300_000),
        )
        assertFalse(PresetValidation.isValid(p))
        assertTrue(PresetValidation.validate(p).any { it.contains("final stage can be open-ended") })
    }

    @Test fun `final open-ended stage is allowed`() {
        val p = preset(
            SessionStage("a", "A", 300_000),
            SessionStage("b", "B", null),          // open-ended AND last: fine
        )
        assertTrue(PresetValidation.isValid(p))
    }

    @Test fun `zero-duration stage and oversized interval are rejected`() {
        val p = preset(
            SessionStage("a", "A", 0),
            SessionStage("b", "B", 300_000, intervalPlan = IntervalPlan.EveryXMinutes(600_000, "x")),
        )
        val errors = PresetValidation.validate(p)
        assertTrue(errors.any { it.contains("longer than zero") })
        assertTrue(errors.any { it.contains("interval is longer than the stage") })
    }
}
