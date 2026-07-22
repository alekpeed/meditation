package com.meditation.core

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Shared, deterministic level calibration for the procedural noise colours.
 *
 * The Paul Kellet pink filter used by the app has an observed uncompressed RMS of about 0.195 for
 * a uniform [-1, 1] input when its established 0.11 output scale is used. White and brown use the
 * gains below so their stationary *electrical* RMS matches that reference before user volume is
 * applied. This is not a loudness standard; final headphone/device audition remains required.
 */
object NoiseColorCalibration {
    const val SAMPLE_RATE_HZ = 44_100.0
    const val TARGET_RMS = 0.195
    const val UNIFORM_WHITE_RMS = 0.5773502691896258 // 1 / sqrt(3)

    /** Raw white noise is deliberately left full-band; its RMS matches the pink reference. */
    val whiteGain: Double = TARGET_RMS / UNIFORM_WHITE_RMS

    /**
     * A stable red/brown one-pole filter. The ~70 Hz shelf limits sub-bass dominance while
     * retaining the -6 dB/octave amplitude slope above it.
     */
    const val BROWN_POLE = 0.99
    val brownDrive: Double = TARGET_RMS * sqrt(1.0 - BROWN_POLE * BROWN_POLE) / UNIFORM_WHITE_RMS
    val brownCornerHz: Double = -SAMPLE_RATE_HZ * ln(BROWN_POLE) / (2.0 * Math.PI)

    /** Stationary RMS of `state = pole * state + drive * uniformWhite`. */
    fun brownStationaryRms(pole: Double = BROWN_POLE, drive: Double = brownDrive): Double =
        drive * UNIFORM_WHITE_RMS / sqrt(1.0 - pole * pole)
}
