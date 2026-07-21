package com.meditation.core

/**
 * Binaural-beat maths (brief §16: binaural generator). A binaural beat is produced by playing a
 * slightly different pure tone in each ear; the brain perceives a "beat" at the difference
 * frequency. Kept in the core so the ear-frequency split is unit-tested and identical to whatever
 * the Android [com.meditation.app.audio.NoiseGenerator] streams.
 */
object Binaural {

    /** Typical usable beat range in Hz (delta .5–4, theta 4–8, alpha 8–13, beta 13–30). */
    val BEAT_RANGE = 0.5..40.0

    /** Comfortable carrier range in Hz for the shared tone. */
    val CARRIER_RANGE = 80.0..500.0

    /** Left/right ear frequencies for a [carrierHz] tone with a [beatHz] difference. */
    fun earFrequencies(carrierHz: Double, beatHz: Double): Pair<Double, Double> {
        val half = beatHz / 2.0
        return (carrierHz - half) to (carrierHz + half)
    }

    /** The perceived beat frequency for a given ear pair (= |right − left|). */
    fun beatOf(leftHz: Double, rightHz: Double): Double = kotlin.math.abs(rightHz - leftHz)
}
