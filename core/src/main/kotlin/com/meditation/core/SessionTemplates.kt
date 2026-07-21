package com.meditation.core

/**
 * Ready-made [SessionPreset] blueprints for established practices (brief §15: body-scan builder,
 * walking mode; §16: retreat builder). These are pure constructors so the exact stage/interval
 * shapes are unit-tested and identical wherever a template is instantiated. The Android layer
 * supplies a unique [id] (a UUID) and the current wall clock [nowMs]; every produced preset is
 * guaranteed to pass [PresetValidation].
 *
 * Sound ids reference the seeded catalog (see assets/metadata/sounds.json). Templates only use
 * ids that always exist as bundled or generated entries, so a fresh install can run them.
 */
object SessionTemplates {

    private const val MIN = 60_000L
    private const val SEC = 1_000L

    /** Catalog metadata shown in the "New from template" picker. [key] dispatches to [build]. */
    data class Template(
        val key: String,
        val title: String,
        val description: String,
        val category: Category,
    ) {
        enum class Category { FOCUS, GUIDED, MOVEMENT, RETREAT }
    }

    val catalog: List<Template> = listOf(
        Template("breath", "Breath Awareness", "A simple, quiet sit anchored on the breath.", Template.Category.FOCUS),
        Template("zazen", "Zazen with Kinhin", "Seated periods with a walking interval, temple-style.", Template.Category.FOCUS),
        Template("body-scan", "Body Scan", "Move attention slowly through the body, region by region.", Template.Category.GUIDED),
        Template("metta", "Loving-Kindness", "Extend goodwill through five widening circles.", Template.Category.GUIDED),
        Template("walking", "Walking Meditation", "Open-ended pace with a soft step marker.", Template.Category.MOVEMENT),
        Template("retreat", "Half-Day Retreat", "Four sittings with short rests between them.", Template.Category.RETREAT),
    )

    /** Build a template preset by [key]; returns null for an unknown key. */
    fun build(key: String, id: String, nowMs: Long): SessionPreset? = when (key) {
        "breath" -> breathAwareness(id, nowMs)
        "zazen" -> zazen(id, nowMs)
        "body-scan" -> bodyScan(id, nowMs)
        "metta" -> lovingKindness(id, nowMs)
        "walking" -> walking(id, nowMs)
        "retreat" -> retreat(id, nowMs)
        else -> null
    }

    private fun stageId(base: String, index: Int) = "$base-s$index"

    private fun preset(
        id: String,
        name: String,
        nowMs: Long,
        stages: List<SessionStage>,
        preparationMs: Long = 10 * SEC,
        finalSoundId: String? = "completion-soft-01",
        overtimeMode: OvertimeMode = OvertimeMode.STOP,
    ) = SessionPreset(
        id = id,
        name = name,
        preparationMs = preparationMs,
        stages = stages,
        finalSoundId = finalSoundId,
        completionStrikeCount = 1,
        overtimeMode = overtimeMode,
        createdAt = nowMs,
        updatedAt = nowMs,
    )

    fun breathAwareness(id: String, nowMs: Long, durationMin: Int = 10): SessionPreset =
        preset(
            id, "Breath Awareness", nowMs,
            stages = listOf(
                SessionStage(
                    id = stageId(id, 0),
                    name = "Sitting",
                    durationMs = durationMin * MIN,
                    openingSoundId = "bell-soft-small-01",
                    closingSoundId = "completion-soft-01",
                ),
            ),
        )

    /** Two seated periods bracketing a walking (kinhin) interval marked by the wood block. */
    fun zazen(id: String, nowMs: Long, sittingMin: Int = 20, kinhinMin: Int = 5): SessionPreset =
        preset(
            id, "Zazen with Kinhin", nowMs,
            finalSoundId = "gong-deep-01",
            stages = listOf(
                SessionStage(stageId(id, 0), "Sitting", sittingMin * MIN, openingSoundId = "bell-temple-medium-01"),
                SessionStage(
                    stageId(id, 1), "Kinhin (walking)", kinhinMin * MIN,
                    openingSoundId = "wood-block-01", closingSoundId = "wood-block-01",
                    intervalPlan = IntervalPlan.EveryXMinutes(intervalMs = 30 * SEC, soundId = "wood-block-01"),
                ),
                SessionStage(stageId(id, 2), "Sitting", sittingMin * MIN, closingSoundId = "gong-deep-01"),
            ),
        )

    /** Six equal regions; a soft crystal chime marks each transition. */
    fun bodyScan(id: String, nowMs: Long, perRegionMin: Int = 3): SessionPreset {
        val regions = listOf("Feet & legs", "Hips & belly", "Chest & back", "Arms & hands", "Neck & head", "Whole body")
        val stages = regions.mapIndexed { i, region ->
            SessionStage(
                id = stageId(id, i),
                name = region,
                durationMs = perRegionMin * MIN,
                openingSoundId = if (i == 0) "bowl-light-01" else "crystal-soft-01",
                closingSoundId = if (i == regions.lastIndex) "bowl-deep-01" else null,
            )
        }
        return preset(id, "Body Scan", nowMs, stages = stages)
    }

    /** Metta: self, a loved one, a neutral person, a difficult person, all beings. */
    fun lovingKindness(id: String, nowMs: Long, perPhaseMin: Int = 3): SessionPreset {
        val phases = listOf("Yourself", "Someone you love", "A neutral person", "A difficult person", "All beings")
        val stages = phases.mapIndexed { i, phase ->
            SessionStage(
                id = stageId(id, i),
                name = phase,
                durationMs = perPhaseMin * MIN,
                openingSoundId = "bell-soft-small-01",
                closingSoundId = if (i == phases.lastIndex) "bowl-medium-01" else null,
            )
        }
        return preset(id, "Loving-Kindness", nowMs, stages = stages)
    }

    /** Open-ended walk with a gentle wood-block cue; counts up past the suggested length. */
    fun walking(id: String, nowMs: Long, stepEverySec: Int = 60): SessionPreset =
        preset(
            id, "Walking Meditation", nowMs,
            overtimeMode = OvertimeMode.COUNT_UP,
            finalSoundId = "bell-soft-small-01",
            stages = listOf(
                SessionStage(
                    id = stageId(id, 0),
                    name = "Walking",
                    durationMs = null, // open-ended
                    openingSoundId = "bell-soft-small-01",
                    intervalPlan = IntervalPlan.EveryXMinutes(intervalMs = stepEverySec * SEC, soundId = "wood-mokugyo-01"),
                ),
            ),
        )

    /** A retreat is modeled as one multi-stage preset: [sittings] sits with [restMin] rests between. */
    fun retreat(id: String, nowMs: Long, sittings: Int = 4, sittingMin: Int = 25, restMin: Int = 5): SessionPreset {
        require(sittings >= 1)
        val stages = mutableListOf<SessionStage>()
        var idx = 0
        for (s in 0 until sittings) {
            stages += SessionStage(
                id = stageId(id, idx++),
                name = "Sitting ${s + 1}",
                durationMs = sittingMin * MIN,
                openingSoundId = "bowl-medium-01",
                closingSoundId = "gong-small-01",
                intervalPlan = IntervalPlan.EveryXMinutes(intervalMs = (sittingMin / 2).coerceAtLeast(1) * MIN, soundId = "bell-soft-small-01"),
            )
            if (s != sittings - 1) {
                stages += SessionStage(
                    id = stageId(id, idx++),
                    name = "Rest ${s + 1}",
                    durationMs = restMin * MIN,
                    openingSoundId = "wood-block-01",
                )
            }
        }
        return preset(id, "Half-Day Retreat", nowMs, finalSoundId = "gong-deep-01", stages = stages)
    }
}
