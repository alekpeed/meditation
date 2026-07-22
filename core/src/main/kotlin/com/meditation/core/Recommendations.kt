package com.meditation.core

/** What kind of nudge a [Recommendation] is, so the UI can style/word it appropriately. */
enum class RecommendationKind { REPEAT_FAVORITE, WELCOME_BACK, TYPICAL_DURATION }

data class Recommendation(
    val kind: RecommendationKind,
    /** Set for REPEAT_FAVORITE / WELCOME_BACK. */
    val presetId: String? = null,
    /** Set for TYPICAL_DURATION. */
    val suggestedDurationMs: Long? = null,
    val message: String,
)

/**
 * On-device recommendations (brief §17 moonshot, scoped down to a deterministic heuristic that
 * needs no network or ML runtime — everything stays local, matching the app's design rule).
 * Pure function of [history] so it is fully unit-testable; the UI decides how/whether to surface
 * the result (e.g. a dismissible card on Home).
 */
object Recommendations {

    private const val DAY = 86_400_000L
    private const val RECENT_WINDOW_DAYS = 14
    private const val LAPSED_AFTER_DAYS = 10
    private const val MIN_REPEAT_COUNT = 3
    private const val MIN_LAPSED_COUNT = 2

    /** Only counts sessions the user actually finished — cancelled sits don't build a pattern. */
    private fun CompletedSession.counts() = completionStatus != CompletionStatus.CANCELLED

    fun suggest(history: List<CompletedSession>, presets: List<SessionPreset>, nowWallMs: Long): Recommendation? {
        val finished = history.filter { it.counts() }
        if (finished.isEmpty()) return null

        val recentCutoff = nowWallMs - RECENT_WINDOW_DAYS * DAY
        val lapsedCutoff = nowWallMs - LAPSED_AFTER_DAYS * DAY
        val presetById = presets.associateBy { it.id }

        // 1) A preset sat with often in the recent window → offer to repeat it.
        val recentCounts = finished.filter { it.startedWallMs >= recentCutoff }
            .groupingBy { it.presetId }.eachCount()
        recentCounts.entries
            .filter { it.value >= MIN_REPEAT_COUNT && presetById.containsKey(it.key) }
            .maxByOrNull { it.value }
            ?.let { (presetId, count) ->
                val name = presetById.getValue(presetId).name
                return Recommendation(
                    kind = RecommendationKind.REPEAT_FAVORITE,
                    presetId = presetId,
                    message = "You've sat with “$name” $count times this week — continue?",
                )
            }

        // 2) A preset used a couple of times but not lately → gently surface it again.
        val lastUsed = finished.groupingBy { it.presetId }.fold(Long.MIN_VALUE) { acc, s -> maxOf(acc, s.startedWallMs) }
        val totalCounts = finished.groupingBy { it.presetId }.eachCount()
        totalCounts.entries
            .filter { (id, count) ->
                count >= MIN_LAPSED_COUNT && presetById.containsKey(id) && (lastUsed[id] ?: 0L) < lapsedCutoff
            }
            .maxByOrNull { lastUsed[it.key] ?: 0L } // most-recently-lapsed first
            ?.let { (presetId, _) ->
                val name = presetById.getValue(presetId).name
                return Recommendation(
                    kind = RecommendationKind.WELCOME_BACK,
                    presetId = presetId,
                    message = "It's been a while since “$name” — welcome back?",
                )
            }

        // 3) Fallback: your typical recent session length, as a quick-start nudge.
        if (finished.size < 2) return null
        val recentDurations = finished.sortedByDescending { it.startedWallMs }
            .take(5).map { it.actualActiveDurationMs }.sorted()
        val median = recentDurations[recentDurations.size / 2]
        val roundedMinutes = (median / 60_000L).coerceAtLeast(1)
        return Recommendation(
            kind = RecommendationKind.TYPICAL_DURATION,
            suggestedDurationMs = roundedMinutes * 60_000L,
            message = "Your usual sit lately is about $roundedMinutes min — start one now?",
        )
    }
}
