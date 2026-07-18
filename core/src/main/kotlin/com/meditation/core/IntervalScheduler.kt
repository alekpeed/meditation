package com.meditation.core

/**
 * Pure computation of interval-bell offsets within a stage.
 *
 * All offsets are measured from the start of the stage (in active-time ms). The scheduler only
 * enumerates offsets; deciding which have already fired (deduplication) is the engine's job via
 * [ActiveSessionState.firedSoundKeys].
 *
 * Random intervals are intentionally not supported here (reserved for Phase 2).
 */
object IntervalScheduler {

    /**
     * All interval offsets that fall at or before [elapsedMs] within a stage, strictly inside the
     * stage window (an interval that would land exactly on the stage boundary is dropped so it does
     * not collide with the stage's closing/transition sound). For open-ended stages pass
     * [stageDurationMs] = null and offsets are generated up to [elapsedMs].
     */
    fun firedOffsets(plan: IntervalPlan, elapsedMs: Long, stageDurationMs: Long?): List<Long> =
        allOffsets(plan, ceilingMs = stageDurationMs ?: (elapsedMs + 1)).filter { it in 1..elapsedMs }

    /** The next interval offset strictly after [elapsedMs], or null if none remains in the stage. */
    fun nextOffsetAfter(plan: IntervalPlan, elapsedMs: Long, stageDurationMs: Long?): Long? {
        val ceiling = stageDurationMs ?: Long.MAX_VALUE
        return allOffsets(plan, ceiling).firstOrNull { it > elapsedMs && it < ceiling }
    }

    /**
     * Enumerate interval offsets strictly below [ceilingMs]. A hard cap prevents pathological plans
     * (e.g. a 1ms interval over an open-ended stage) from producing an unbounded list.
     */
    private fun allOffsets(plan: IntervalPlan, ceilingMs: Long): List<Long> {
        val cap = 100_000 // safety bound on number of intervals enumerated
        return when (plan) {
            is IntervalPlan.None -> emptyList()

            is IntervalPlan.EveryXMinutes -> buildList {
                if (plan.intervalMs <= 0) return@buildList
                var t = plan.intervalMs
                var n = 0
                while (t < ceilingMs && n < cap) {
                    add(t); t += plan.intervalMs; n++
                }
            }

            is IntervalPlan.CustomTimestamps ->
                plan.offsetsMs.filter { it in 1 until ceilingMs }.sorted()

            is IntervalPlan.Progressive -> buildList {
                if (plan.startMs <= 0 || plan.incrementMs < 0) return@buildList
                var offset = plan.startMs
                var gap = plan.startMs
                var n = 0
                while (offset < ceilingMs && n < cap) {
                    add(offset)
                    gap += plan.incrementMs
                    offset += gap
                    n++
                }
            }
        }
    }

    fun soundIdOf(plan: IntervalPlan): String? = when (plan) {
        is IntervalPlan.None -> null
        is IntervalPlan.EveryXMinutes -> plan.soundId
        is IntervalPlan.CustomTimestamps -> plan.soundId
        is IntervalPlan.Progressive -> plan.soundId
    }

    fun strikeCountOf(plan: IntervalPlan): Int = when (plan) {
        is IntervalPlan.None -> 1
        is IntervalPlan.EveryXMinutes -> plan.strikeCount
        is IntervalPlan.CustomTimestamps -> plan.strikeCount
        is IntervalPlan.Progressive -> plan.strikeCount
    }
}
