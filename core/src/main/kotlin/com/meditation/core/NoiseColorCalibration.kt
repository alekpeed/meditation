package com.meditation.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Level calibration for the procedural noise colours.
 *
 * The colours are matched by **K-weighted loudness** (ITU-R BS.1770), not raw electrical RMS:
 * brown's energy sits where the ear is least sensitive, so equal RMS leaves it sounding much
 * quieter than white. Matching perceptually means brown must run electrically hotter, so the shared
 * target is chosen as the loudest level at which every colour still peaks below [PEAK_CEILING] —
 * leaving the limiter with only occasional transients to catch.
 *
 * Every number is derived by running the real signal chain ([NoiseChannel]) with fixed seeds, so the
 * calibration can never drift out of step with the filters, and it is unit-testable on a plain JVM.
 */
object NoiseColorCalibration {

    const val WHITE = "white"
    const val PINK = "pink"
    const val BROWN = "brown"

    /** Device-native on Android, and the rate the BS.1770 coefficients are defined at. */
    const val SAMPLE_RATE_HZ = 48_000.0

    /** Removes inaudible subsonic energy that would waste headroom and move speakers for nothing. */
    const val HIGH_PASS_HZ = 30.0

    /** Gentle top-end limit. */
    const val LOW_PASS_HZ = 18_000.0

    /** Brown's integrator corner, below the audible band so it keeps its deep weight. */
    const val BROWN_CORNER_HZ = 18.0

    /**
     * Per-colour top-end roll-off, on top of each colour's own slope. Raise for brighter, lower for
     * darker. These are voicing choices rather than definitions of the colours, which is why they
     * live here as single tunable constants.
     *
     * - Brown: two cascaded stages. A textbook -6 dB/octave slope still leaves audible hiss, and the
     *   deep rumble the colour is expected to have is darker than the raw slope.
     * - White: one stage. Flat-per-Hz means over half of white's energy sits above 10 kHz, which is
     *   what makes raw white noise feel harsh; this takes the sizzle off while leaving everything
     *   below ~2 kHz untouched, so it stays clearly the brightest colour.
     * - Pink: one gentle stage. Pink is already the most balanced of the three, so this only trims
     *   the last of the top-end air.
     */
    const val BROWN_TOP_HZ = 1_600.0
    const val WHITE_TOP_HZ = 7_000.0
    const val PINK_TOP_HZ = 12_000.0

    /** Peak ceiling used when choosing the shared target. */
    const val PEAK_CEILING = 0.80

    val BROWN_POLE: Double = exp(-2.0 * PI * BROWN_CORNER_HZ / SAMPLE_RATE_HZ)
    val brownCornerHz: Double = -SAMPLE_RATE_HZ * ln(BROWN_POLE) / (2.0 * PI)

    private const val MEASURE_SAMPLES = 24_000

    /** K-weighted loudness and peak of each colour at unity gain, measured through the real chain. */
    private val unity: Map<String, Pair<Double, Double>> =
        listOf(WHITE, PINK, BROWN).associateWith { colour ->
            val loudnessChannel = NoiseChannel(colour, Random(colour.hashCode() * 31 + 1), gain = 1.0)
            val loudness = Loudness.kWeightedRms(MEASURE_SAMPLES) { loudnessChannel.next() }
            val peakChannel = NoiseChannel(colour, Random(colour.hashCode() * 31 + 2), gain = 1.0)
            val peak = Loudness.peak(MEASURE_SAMPLES) { peakChannel.next() }
            loudness to peak
        }

    /** The loudest shared K-weighted level at which every colour still peaks below the ceiling. */
    val targetLoudness: Double = unity.values.minOf { (loudness, peak) -> PEAK_CEILING * loudness / peak }

    /** Gain that brings [colour] to [targetLoudness]. */
    fun gainFor(colour: String): Double {
        val loudness = unity[colour]?.first ?: return 1.0
        return targetLoudness / loudness
    }

    val whiteGain: Double get() = gainFor(WHITE)
    val pinkGain: Double get() = gainFor(PINK)
    val brownGain: Double get() = gainFor(BROWN)

    fun isNoiseColour(type: String): Boolean = type == WHITE || type == PINK || type == BROWN

    /** Builds a calibrated, playback-ready channel. */
    fun channel(colour: String, random: Random): NoiseChannel =
        NoiseChannel(colour = colour, random = random, gain = gainFor(colour))
}
