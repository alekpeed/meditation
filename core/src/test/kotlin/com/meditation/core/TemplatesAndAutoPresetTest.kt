package com.meditation.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SessionTemplatesTest {

    @Test fun `every catalog template builds a valid preset`() {
        for (t in SessionTemplates.catalog) {
            val preset = SessionTemplates.build(t.key, id = "tmpl-${t.key}", nowMs = 1_000)
            assertNotNull(preset, "template ${t.key} built null")
            val errors = PresetValidation.validate(preset)
            assertTrue(errors.isEmpty(), "template ${t.key} invalid: $errors")
        }
    }

    @Test fun `unknown template key returns null`() {
        assertNull(SessionTemplates.build("nope", "x", 0))
    }

    @Test fun `zazen has three stages with a walking interval in the middle`() {
        val p = SessionTemplates.zazen("z", 0)
        assertEquals(3, p.stages.size)
        assertTrue(p.stages[1].intervalPlan is IntervalPlan.EveryXMinutes)
        // opening/closing sits are fixed-length; nothing open-ended (all can be validated)
        assertFalse(p.hasOpenEndedStage)
    }

    @Test fun `walking is open-ended and counts up`() {
        val p = SessionTemplates.walking("w", 0)
        assertTrue(p.hasOpenEndedStage)
        assertEquals(OvertimeMode.COUNT_UP, p.overtimeMode)
        // open-ended stage must be the last one, or validation would reject it
        assertTrue(PresetValidation.isValid(p))
    }

    @Test fun `retreat produces alternating sit and rest stages`() {
        val p = SessionTemplates.retreat("r", 0, sittings = 3, sittingMin = 20, restMin = 4)
        // 3 sits + 2 rests = 5 stages
        assertEquals(5, p.stages.size)
        assertEquals("Sitting 1", p.stages.first().name)
        assertEquals("Rest 1", p.stages[1].name)
        assertTrue(PresetValidation.isValid(p))
    }

    @Test fun `stage ids are unique within a preset`() {
        for (t in SessionTemplates.catalog) {
            val p = SessionTemplates.build(t.key, "u-${t.key}", 0)!!
            val ids = p.stages.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "duplicate stage ids in ${t.key}")
        }
    }
}

class AutoPresetTest {
    @Test fun `daytime range matches inside and not outside`() {
        val rule = AutoPresetRule(startMinute = 6 * 60, endMinute = 9 * 60, presetId = "morning")
        assertTrue(rule.contains(7 * 60))
        assertFalse(rule.contains(9 * 60)) // end exclusive
        assertFalse(rule.contains(5 * 60 + 59))
    }

    @Test fun `range wrapping past midnight matches both sides`() {
        val night = AutoPresetRule(startMinute = 22 * 60, endMinute = 6 * 60, presetId = "sleep")
        assertTrue(night.contains(23 * 60))
        assertTrue(night.contains(2 * 60))
        assertFalse(night.contains(12 * 60))
    }

    @Test fun `select returns first matching enabled rule by priority`() {
        val rules = listOf(
            AutoPresetRule(6 * 60, 12 * 60, "morning"),
            AutoPresetRule(6 * 60, 20 * 60, "day", enabled = true),
            AutoPresetRule(0, 1440, "always", enabled = false),
        )
        assertEquals("morning", AutoPreset.select(rules, 8 * 60))
        assertEquals("day", AutoPreset.select(rules, 15 * 60))
        assertNull(AutoPreset.select(rules, 23 * 60)) // only disabled rule would match
    }

    @Test fun `minuteOfDay derives local time and rules round-trip through json`() {
        // 100 days + 8h30m past UTC midnight, tz +0 → 510 minutes
        val mod = AutoPreset.minuteOfDay(100L * 86_400_000L + (8 * 60 + 30) * 60_000L, tzOffsetMs = 0)
        assertEquals(8 * 60 + 30, mod)
        val json = Json { encodeDefaults = true }
        val rule = AutoPresetRule(60, 120, "p", label = "test")
        assertEquals(rule, json.decodeFromString<AutoPresetRule>(json.encodeToString(rule)))
    }
}
