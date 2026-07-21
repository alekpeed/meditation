package com.meditation.core

/**
 * Pure serialization of history to JSON and CSV (backlog J4). Kept dependency-free and testable; the
 * Android layer only handles writing the returned string to a user-chosen file.
 */
object HistoryExport {

    fun toCsv(sessions: List<CompletedSession>): String {
        val header = listOf(
            "sessionId", "presetName", "startedWallMs", "completedWallMs",
            "intendedDurationMs", "actualActiveDurationMs", "pausedDurationMs",
            "overtimeDurationMs", "completionStatus", "moodBefore", "moodAfter", "tags", "note",
        )
        val rows = sessions.map { s ->
            listOf(
                s.sessionId,
                s.presetName,
                s.startedWallMs.toString(),
                s.completedWallMs.toString(),
                s.intendedDurationMs.toString(),
                s.actualActiveDurationMs.toString(),
                s.pausedDurationMs.toString(),
                s.overtimeDurationMs.toString(),
                s.completionStatus.name,
                s.moodBefore?.toString() ?: "",
                s.moodAfter?.toString() ?: "",
                s.tags.joinToString(";"),
                s.note ?: "",
            )
        }
        return (listOf(header) + rows).joinToString("\n") { line ->
            line.joinToString(",") { escapeCsv(it) }
        }
    }

    /** Minimal, dependency-free JSON array. Fields are escaped; numbers/nulls are emitted raw. */
    fun toJson(sessions: List<CompletedSession>): String =
        sessions.joinToString(prefix = "[", postfix = "]", separator = ",") { s ->
            buildString {
                append('{')
                append(kv("sessionId", s.sessionId)); append(',')
                append(kv("presetName", s.presetName)); append(',')
                append(numKv("startedWallMs", s.startedWallMs)); append(',')
                append(numKv("completedWallMs", s.completedWallMs)); append(',')
                append(numKv("intendedDurationMs", s.intendedDurationMs)); append(',')
                append(numKv("actualActiveDurationMs", s.actualActiveDurationMs)); append(',')
                append(numKv("pausedDurationMs", s.pausedDurationMs)); append(',')
                append(numKv("overtimeDurationMs", s.overtimeDurationMs)); append(',')
                append(kv("completionStatus", s.completionStatus.name)); append(',')
                append(rawKv("moodBefore", s.moodBefore?.toString() ?: "null")); append(',')
                append(rawKv("moodAfter", s.moodAfter?.toString() ?: "null")); append(',')
                append("\"tags\":[")
                append(s.tags.joinToString(",") { "\"${escapeJson(it)}\"" })
                append("],")
                append(rawKv("note", s.note?.let { "\"${escapeJson(it)}\"" } ?: "null"))
                append('}')
            }
        }

    private fun kv(key: String, value: String) = "\"$key\":\"${escapeJson(value)}\""
    private fun numKv(key: String, value: Long) = "\"$key\":$value"
    private fun rawKv(key: String, rawValue: String) = "\"$key\":$rawValue"

    private fun escapeJson(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    private fun escapeCsv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
}
