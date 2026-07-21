package com.meditation.core

/**
 * Pure validation for a [SessionPreset]. The Session Builder blocks Save until this returns no
 * errors (screen-flow §7 "validation blocks invalid combinations"). Kept in the core so the rules
 * are unit-tested and identical wherever a preset is constructed.
 */
object PresetValidation {

    fun validate(preset: SessionPreset): List<String> {
        val errors = mutableListOf<String>()

        if (preset.name.isBlank()) errors += "Name is required."
        if (preset.stages.isEmpty()) errors += "Add at least one stage."
        if (preset.preparationMs < 0) errors += "Preparation time cannot be negative."

        preset.stages.forEachIndexed { index, stage ->
            val label = stage.name.ifBlank { "Stage ${index + 1}" }
            val duration = stage.durationMs
            if (duration != null && duration <= 0) {
                errors += "$label must be longer than zero."
            }
            // Only the final stage may be open-ended; an earlier open-ended stage would never end.
            if (duration == null && index != preset.stages.lastIndex) {
                errors += "Only the final stage can be open-ended ($label is not last)."
            }
            validateInterval(stage.intervalPlan, duration, label, errors)
        }

        if (preset.completionStrikeCount < 1) errors += "Completion strike count must be at least 1."
        if (preset.completionStrikeSpacingMs < 0) errors += "Strike spacing cannot be negative."

        return errors
    }

    fun isValid(preset: SessionPreset): Boolean = validate(preset).isEmpty()

    private fun validateInterval(
        plan: IntervalPlan,
        stageDurationMs: Long?,
        label: String,
        errors: MutableList<String>,
    ) {
        when (plan) {
            is IntervalPlan.None -> Unit
            is IntervalPlan.EveryXMinutes -> {
                if (plan.intervalMs <= 0) errors += "$label interval must be positive."
                if (stageDurationMs != null && plan.intervalMs >= stageDurationMs) {
                    errors += "$label interval is longer than the stage."
                }
            }
            is IntervalPlan.CustomTimestamps -> {
                if (plan.offsetsMs.any { it <= 0 }) errors += "$label has an invalid interval time."
                if (stageDurationMs != null && plan.offsetsMs.any { it >= stageDurationMs }) {
                    errors += "$label has an interval past the end of the stage."
                }
            }
            is IntervalPlan.Progressive -> {
                if (plan.startMs <= 0) errors += "$label progressive start must be positive."
                if (plan.incrementMs < 0) errors += "$label progressive increment cannot be negative."
            }
            is IntervalPlan.Random -> {
                if (plan.minIntervalMs <= 0) errors += "$label random minimum must be positive."
                if (plan.maxIntervalMs < plan.minIntervalMs) errors += "$label random maximum must be ≥ minimum."
            }
        }
    }
}
