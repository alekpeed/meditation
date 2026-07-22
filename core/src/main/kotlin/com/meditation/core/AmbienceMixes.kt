package com.meditation.core

import kotlinx.serialization.Serializable

/**
 * A named, reusable combination of ambience layers (brief §15 soundscape/mixer). Saved mixes are
 * just data — the mixer screen builds them from live [AmbienceLayer] previews, and a stage can
 * adopt one as its starting `ambienceLayers` (still capped at three, still editable afterward).
 */
@Serializable
data class SavedAmbienceMix(
    val id: String,
    val name: String,
    val layers: List<AmbienceLayer>,
)
