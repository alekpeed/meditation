package com.meditation.core

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertTrue
import org.junit.Test

class NoiseColorCalibrationTest {

    private fun channel(colour: String, seed: Int = 7) =
        NoiseColorCalibration.channel(colour, Random(seed))

    private fun db(ratio: Double) = 20 * log10(ratio)

    private val colours = listOf(
        NoiseColorCalibration.WHITE,
        NoiseColorCalibration.PINK,
        NoiseColorCalibration.BROWN,
    )

    @Test fun `all three colours match in K-weighted loudness`() {
        val target = NoiseColorCalibration.targetLoudness
        colours.forEach { colour ->
            val c = channel(colour, seed = 11)
            val level = Loudness.kWeightedRms(48_000) { c.next() }
            // Independent seeds, so allow a small statistical spread around the shared target.
            assertTrue(abs(db(level / target)) < 1.0, "$colour is ${db(level / target)} dB off target")
        }
    }

    @Test fun `colours keep headroom below full scale`() {
        colours.forEach { colour ->
            val c = channel(colour, seed = 23)
            val peak = Loudness.peak(48_000) { c.next() }
            assertTrue(peak < 0.98, "$colour peaked at $peak")
        }
    }

    @Test fun `pink falls about 3 dB per octave and brown about 6`() {
        fun slopePerOctave(colour: String): Double {
            val low = 500.0
            val high = 2_000.0
            val a = channel(colour, seed = 31)
            val lowEnergy = Loudness.bandEnergy(low, 96_000) { a.next() }
            val b = channel(colour, seed = 31)
            val highEnergy = Loudness.bandEnergy(high, 96_000) { b.next() }
            // Constant-Q bands widen with centre frequency (+3 dB per octave of bandwidth), so
            // remove that before reading the spectral slope.
            val octaves = log2(high / low)
            return (db(highEnergy / lowEnergy) - 3.0 * octaves) / octaves
        }

        val white = slopePerOctave(NoiseColorCalibration.WHITE)
        val pink = slopePerOctave(NoiseColorCalibration.PINK)
        val brown = slopePerOctave(NoiseColorCalibration.BROWN)

        assertTrue(abs(white) < 1.0, "white slope was $white dB/oct")
        assertTrue(abs(pink + 3.0) < 1.2, "pink slope was $pink dB/oct")
        assertTrue(abs(brown + 6.0) < 1.5, "brown slope was $brown dB/oct")
    }

    @Test fun `high pass removes subsonic energy but keeps the audible low end`() {
        val a = channel(NoiseColorCalibration.BROWN, seed = 41)
        val subsonic = Loudness.bandEnergy(8.0, 96_000) { a.next() }
        val b = channel(NoiseColorCalibration.BROWN, seed = 41)
        val audible = Loudness.bandEnergy(60.0, 96_000) { b.next() }
        assertTrue(subsonic < audible, "subsonic $subsonic should sit below 60 Hz energy $audible")
    }

    @Test fun `brown corner stays below the audible band`() {
        assertTrue(
            NoiseColorCalibration.brownCornerHz in 10.0..25.0,
            "corner ${NoiseColorCalibration.brownCornerHz}",
        )
    }

    @Test fun `limiter holds the output below its threshold`() {
        val limiter = PeakLimiter(threshold = 0.9)
        var worst = 0.0
        repeat(10_000) { i ->
            val x = if (i % 100 < 50) 0.2 else 2.5 // alternate quiet and over-full-scale
            worst = maxOf(worst, abs(x * limiter.gainFor(abs(x))))
        }
        assertTrue(worst <= 0.9001, "limiter let $worst through")
    }

    @Test fun `stereo channels are decorrelated`() {
        val left = channel(NoiseColorCalibration.PINK, seed = 1)
        val right = channel(NoiseColorCalibration.PINK, seed = 2)
        var dot = 0.0
        var lSq = 0.0
        var rSq = 0.0
        repeat(48_000) {
            val l = left.next()
            val r = right.next()
            dot += l * r; lSq += l * l; rSq += r * r
        }
        val correlation = dot / sqrt(lSq * rSq)
        assertTrue(abs(correlation) < 0.1, "channels correlated at $correlation")
    }
}
