package com.meditation.core

data class Statistics(
    val totalActiveMs: Long,
    val sessionCount: Int,
    val averageMs: Long,
    val longestMs: Long,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val weeklyTotalMs: Long,
    val monthlyTotalMs: Long,
)

/**
 * Pure statistics over completed sessions (backlog J3). Kept dependency-free and testable; the
 * Android layer passes the current wall time and the device's UTC offset so "days" align with the
 * user's local calendar. Cancelled sessions are excluded. Streaks avoid competitive framing — they
 * are simply consecutive local days containing at least one completed session.
 */
object Stats {

    private const val DAY_MS = 86_400_000L

    fun compute(sessions: List<CompletedSession>, nowWallMs: Long, tzOffsetMs: Long): Statistics {
        val done = sessions.filter { it.completionStatus != CompletionStatus.CANCELLED }
        if (done.isEmpty()) return Statistics(0, 0, 0, 0, 0, 0, 0, 0)

        val total = done.sumOf { it.actualActiveDurationMs }
        val count = done.size
        val longest = done.maxOf { it.actualActiveDurationMs }
        val average = total / count

        val weekAgo = nowWallMs - 7 * DAY_MS
        val monthAgo = nowWallMs - 30 * DAY_MS
        val weekly = done.filter { it.startedWallMs >= weekAgo }.sumOf { it.actualActiveDurationMs }
        val monthly = done.filter { it.startedWallMs >= monthAgo }.sumOf { it.actualActiveDurationMs }

        val days = done.map { dayIndex(it.startedWallMs, tzOffsetMs) }.toSortedSet()
        val longestStreak = longestRun(days)
        val currentStreak = currentRun(days, dayIndex(nowWallMs, tzOffsetMs))

        return Statistics(total, count, average, longest, currentStreak, longestStreak, weekly, monthly)
    }

    private fun dayIndex(wallMs: Long, tzOffsetMs: Long): Long = Math.floorDiv(wallMs + tzOffsetMs, DAY_MS)

    private fun longestRun(days: Set<Long>): Int {
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        var prev: Long? = null
        for (d in days.sorted()) {
            prev?.let { run = if (d == it + 1) run + 1 else 1 }
            if (run > best) best = run
            prev = d
        }
        return best
    }

    /** Streak ending today, or ending yesterday if nothing yet today (so it doesn't read as broken). */
    private fun currentRun(days: Set<Long>, today: Long): Int {
        val anchor = when {
            today in days -> today
            (today - 1) in days -> today - 1
            else -> return 0
        }
        var streak = 0
        var d = anchor
        while (d in days) { streak++; d-- }
        return streak
    }
}
