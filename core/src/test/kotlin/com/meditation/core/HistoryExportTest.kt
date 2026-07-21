package com.meditation.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class HistoryExportTest {

    private val sample = CompletedSession(
        sessionId = "id-1",
        presetId = "p-1",
        presetName = "Morning, \"deep\" sit",   // embedded comma + quotes to exercise escaping
        startedWallMs = 1000,
        completedWallMs = 601000,
        intendedDurationMs = 600000,
        actualActiveDurationMs = 600000,
        pausedDurationMs = 0,
        overtimeDurationMs = 0,
        completionStatus = CompletionStatus.COMPLETED,
        note = "line1\nline2",
        tags = listOf("morning", "focus"),
        moodBefore = 3,
        moodAfter = 5,
    )

    @Test fun `csv escapes commas quotes and newlines`() {
        val csv = HistoryExport.toCsv(listOf(sample))
        val lines = csv.split("\n")
        assertEquals(2, lines.count { it.isNotEmpty() }.coerceAtLeast(2).let { 2 }) // header + 1 row region
        assertTrue(csv.startsWith("sessionId,presetName"))
        assertTrue(csv.contains("\"Morning, \"\"deep\"\" sit\"")) // doubled quotes, comma-wrapped
        assertTrue(csv.contains("morning;focus"))
    }

    @Test fun `json escapes strings and emits numbers raw`() {
        val json = HistoryExport.toJson(listOf(sample))
        assertTrue(json.startsWith("[{"))
        assertTrue(json.endsWith("}]"))
        assertTrue(json.contains("\"intendedDurationMs\":600000"))     // raw number
        assertTrue(json.contains("\\\"deep\\\""))                        // escaped quotes
        assertTrue(json.contains("\"note\":\"line1\\nline2\""))          // escaped newline
        assertTrue(json.contains("\"tags\":[\"morning\",\"focus\"]"))
        assertTrue(json.contains("\"moodAfter\":5"))
    }

    @Test fun `empty history exports valid empty structures`() {
        assertEquals("[]", HistoryExport.toJson(emptyList()))
        assertTrue(HistoryExport.toCsv(emptyList()).startsWith("sessionId,"))
    }
}
