package com.meditation.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure-Kotlin DSP for the procedural noise colours, kept out of the Android layer so the whole
 * signal chain is unit-testable on a plain JVM (slope, loudness match, filtering, limiting).
 *
 * Chain per channel: colour generator -> ~30 Hz high-pass -> optional ~18 kHz low-pass -> gain.
 * Stereo is produced by running two channels from *independent* random streams: decorrelated noise
 * is what makes the result sound wide and enveloping instead of collapsing to a point in the head.
 * A peak limiter linked across both channels catches transient peaks without distorting the way a
 * per-sample soft clip does.
 */

/** Direct-form-1 biquad. */
class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** RBJ cookbook coefficients. */
object BiquadDesign {
    const val BUTTERWORTH_Q = 0.7071067811865476

    fun highPass(sampleRate: Double, cutoffHz: Double, q: Double = BUTTERWORTH_Q): Biquad {
        val w0 = 2 * PI * cutoffHz / sampleRate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        return Biquad(
            b0 = ((1 + cw) / 2) / a0,
            b1 = (-(1 + cw)) / a0,
            b2 = ((1 + cw) / 2) / a0,
            a1 = (-2 * cw) / a0,
            a2 = (1 - alpha) / a0,
        )
    }

    fun lowPass(sampleRate: Double, cutoffHz: Double, q: Double = BUTTERWORTH_Q): Biquad {
        val w0 = 2 * PI * cutoffHz / sampleRate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        return Biquad(
            b0 = ((1 - cw) / 2) / a0,
            b1 = (1 - cw) / a0,
            b2 = ((1 - cw) / 2) / a0,
            a1 = (-2 * cw) / a0,
            a2 = (1 - alpha) / a0,
        )
    }

    fun bandPass(sampleRate: Double, centreHz: Double, q: Double): Biquad {
        val w0 = 2 * PI * centreHz / sampleRate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        return Biquad(
            b0 = alpha / a0,
            b1 = 0.0,
            b2 = -alpha / a0,
            a1 = (-2 * cw) / a0,
            a2 = (1 - alpha) / a0,
        )
    }
}

/** Paul Kellet's pink approximation: about -3 dB per octave. */
class PinkFilter {
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0

    fun process(white: Double): Double {
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return pink * 0.11
    }
}

/**
 * Leaky integrator: about -6 dB per octave above its corner. The corner sits well below the audible
 * band so brown keeps its deep weight; the 30 Hz high-pass afterwards removes the inaudible
 * subsonic energy that would otherwise eat headroom and move speakers for nothing.
 */
class BrownFilter(private val pole: Double, private val drive: Double = 1.0 - pole) {
    private var state = 0.0
    fun process(white: Double): Double {
        state = pole * state + drive * white
        return state
    }
}

/**
 * Peak limiter with instant attack and smooth release, applied to the summed stereo peak so both
 * channels are reduced together and the stereo image never shifts.
 */
class PeakLimiter(
    private val threshold: Double = 0.97,
    sampleRate: Double = NoiseColorCalibration.SAMPLE_RATE_HZ,
    releaseSeconds: Double = 0.25,
) {
    private val releaseCoeff = exp(-1.0 / (releaseSeconds * sampleRate))
    private var gain = 1.0

    /** Returns the gain to apply to every channel of this frame. */
    fun gainFor(peak: Double): Double {
        val needed = if (peak > threshold) threshold / peak else 1.0
        gain = if (needed < gain) needed else needed + (gain - needed) * releaseCoeff
        return gain
    }
}

/** One channel of shaped noise: colour -> high-pass -> optional low-pass -> gain. */
class NoiseChannel(
    private val colour: String,
    private val random: Random,
    sampleRate: Double = NoiseColorCalibration.SAMPLE_RATE_HZ,
    private val gain: Double = 1.0,
    highPassHz: Double = NoiseColorCalibration.HIGH_PASS_HZ,
    lowPassHz: Double? = NoiseColorCalibration.LOW_PASS_HZ,
) {
    private val pink = PinkFilter()
    private val brown = BrownFilter(NoiseColorCalibration.BROWN_POLE)
    private val highPass = BiquadDesign.highPass(sampleRate, highPassHz)
    private val lowPass = lowPassHz?.let { BiquadDesign.lowPass(sampleRate, it) }

    /**
     * Voicing roll-off applied on top of each colour's own slope, so none of them sound harsher than
     * the colour is expected to (see the cutoffs in [NoiseColorCalibration]). Brown needs two stages
     * to reach its deep character; white and pink need one and a gentle one respectively.
     */
    private val topRolloff: List<Biquad> = when (colour) {
        NoiseColorCalibration.BROWN ->
            List(2) { BiquadDesign.lowPass(sampleRate, NoiseColorCalibration.BROWN_TOP_HZ) }
        NoiseColorCalibration.WHITE ->
            listOf(BiquadDesign.lowPass(sampleRate, NoiseColorCalibration.WHITE_TOP_HZ))
        NoiseColorCalibration.PINK ->
            listOf(BiquadDesign.lowPass(sampleRate, NoiseColorCalibration.PINK_TOP_HZ))
        else -> emptyList()
    }

    fun next(): Double {
        val white = random.nextDouble() * 2.0 - 1.0
        val coloured = when (colour) {
            NoiseColorCalibration.PINK -> pink.process(white)
            NoiseColorCalibration.BROWN -> brown.process(white)
            else -> white
        }
        var s = highPass.process(coloured)
        lowPass?.let { s = it.process(s) }
        topRolloff.forEach { s = it.process(s) }
        return s * gain
    }
}

/**
 * ITU-R BS.1770 K-weighted level measurement. The published coefficients are defined at 48 kHz,
 * which is also the rate the generators run at. Used to match the colours by *perceived* loudness
 * rather than raw RMS — brown's energy sits where the ear is least sensitive, so equal RMS would
 * leave it sounding noticeably quieter than white.
 */
object Loudness {
    private fun stage1() = Biquad(
        b0 = 1.53512485958697 / 1.0,
        b1 = -2.69169618940638,
        b2 = 1.19839281085285,
        a1 = -1.69065929318241,
        a2 = 0.73248077421585,
    )

    private fun stage2() = Biquad(
        b0 = 1.0,
        b1 = -2.0,
        b2 = 1.0,
        a1 = -1.99004745483398,
        a2 = 0.99007225036621,
    )

    /** K-weighted RMS of a signal produced by [next], skipping [warmUp] samples of filter settling. */
    fun kWeightedRms(sampleCount: Int, warmUp: Int = 4_800, next: () -> Double): Double {
        val s1 = stage1()
        val s2 = stage2()
        repeat(warmUp) { s2.process(s1.process(next())) }
        var sum = 0.0
        repeat(sampleCount) {
            val y = s2.process(s1.process(next()))
            sum += y * y
        }
        return sqrt(sum / sampleCount)
    }

    /** Plain RMS, for peak/energy checks that should not be ear-weighted. */
    fun rms(sampleCount: Int, warmUp: Int = 4_800, next: () -> Double): Double {
        repeat(warmUp) { next() }
        var sum = 0.0
        repeat(sampleCount) {
            val y = next()
            sum += y * y
        }
        return sqrt(sum / sampleCount)
    }

    /** Energy of [next] within a narrow band, used to verify spectral slope. */
    fun bandEnergy(
        centreHz: Double,
        sampleCount: Int,
        sampleRate: Double = NoiseColorCalibration.SAMPLE_RATE_HZ,
        next: () -> Double,
    ): Double {
        val bp = BiquadDesign.bandPass(sampleRate, centreHz, q = 4.0)
        repeat(4_800) { bp.process(next()) }
        var sum = 0.0
        repeat(sampleCount) {
            val y = bp.process(next())
            sum += y * y
        }
        return sqrt(sum / sampleCount)
    }

    fun peak(sampleCount: Int, next: () -> Double): Double {
        var p = 0.0
        repeat(sampleCount) { p = maxOf(p, abs(next())) }
        return p
    }
}
