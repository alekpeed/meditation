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

    private fun bandEnergy(colour: String, centreHz: Double, seed: Int = 31): Double {
        val c = channel(colour, seed)
        return Loudness.bandEnergy(centreHz, 96_000) { c.next() }
    }

    /** Band energy of the bare colour, with the voicing roll-off stripped off. */
    private fun rawBandEnergy(colour: String, centreHz: Double, seed: Int = 31): Double {
        val c = NoiseChannel(colour, Random(seed), gain = 1.0, voiced = false)
        return Loudness.bandEnergy(centreHz, 96_000) { c.next() }
    }

    /** Spectral slope between two octave bands, corrected for constant-Q bandwidth growth. */
    private fun rawSlopePerOctave(colour: String, low: Double, high: Double): Double {
        val octaves = log2(high / low)
        return (db(rawBandEnergy(colour, high) / rawBandEnergy(colour, low)) - 3.0 * octaves) / octaves
    }

    @Test fun `the colour maths is right - pink falls 3 dB per octave and brown 6`() {
        // Measured unvoiced, so this checks the generators themselves rather than the voicing.
        val white = rawSlopePerOctave(NoiseColorCalibration.WHITE, 500.0, 2_000.0)
        val pink = rawSlopePerOctave(NoiseColorCalibration.PINK, 500.0, 2_000.0)
        val brown = rawSlopePerOctave(NoiseColorCalibration.BROWN, 500.0, 2_000.0)

        assertTrue(abs(white) < 1.0, "white slope was $white dB/oct")
        assertTrue(abs(pink + 3.0) < 1.2, "pink slope was $pink dB/oct")
        assertTrue(abs(brown + 6.0) < 1.5, "brown slope was $brown dB/oct")
    }

    @Test fun `pink has no hissy layer sitting on top of its body`() {
        val reference = bandEnergy(NoiseColorCalibration.PINK, 500.0)
        // A textbook -3 dB/octave pink reads flat in constant-Q bands, which leaves the top audible
        // as a separate hiss. The voicing must tilt it well down.
        val presence = db(bandEnergy(NoiseColorCalibration.PINK, 4_000.0) / reference)
        assertTrue(presence < -8.0, "pink 4 kHz sat at $presence dB relative to 500 Hz")
        val top = db(bandEnergy(NoiseColorCalibration.PINK, 8_000.0) / reference)
        assertTrue(top < -12.0, "pink 8 kHz sat at $top dB relative to 500 Hz")

        // ...while keeping the low end that gives pink its body.
        val low = db(bandEnergy(NoiseColorCalibration.PINK, 125.0) / reference)
        assertTrue(low > -1.0, "pink 125 Hz sat at $low dB relative to 500 Hz")
    }

    @Test fun `white loses its top-end sizzle but stays the brightest colour`() {
        val reference = bandEnergy(NoiseColorCalibration.WHITE, 500.0)
        // Flat-per-Hz white climbs steeply in constant-Q bands, which is the hiss. The voicing has
        // to pull the top back down rather than let it keep rising.
        val hiss = db(bandEnergy(NoiseColorCalibration.WHITE, 8_000.0) / reference)
        assertTrue(hiss < 2.0, "white 8 kHz sat at $hiss dB relative to 500 Hz")
        val top = db(bandEnergy(NoiseColorCalibration.WHITE, 12_000.0) / reference)
        assertTrue(top < -4.0, "white 12 kHz sat at $top dB relative to 500 Hz")

        // ...without going dark: white keeps its presence range and stays the bright colour.
        val mid = db(bandEnergy(NoiseColorCalibration.WHITE, 2_000.0) / reference)
        assertTrue(mid > -2.0, "white 2 kHz sat at $mid dB relative to 500 Hz")
    }

    @Test fun `the colours stay distinct, white brightest and brown darkest`() {
        fun tilt(colour: String) =
            db(bandEnergy(colour, 4_000.0) / bandEnergy(colour, 500.0))

        val white = tilt(NoiseColorCalibration.WHITE)
        val pink = tilt(NoiseColorCalibration.PINK)
        val brown = tilt(NoiseColorCalibration.BROWN)
        assertTrue(white > pink + 3.0, "white ($white) should sit clearly above pink ($pink)")
        assertTrue(pink > brown + 3.0, "pink ($pink) should sit clearly above brown ($brown)")
    }

    @Test fun `brown is darker than its raw slope in the mids and highs`() {
        val reference = bandEnergy(NoiseColorCalibration.BROWN, 500.0)
        // A pure -6 dB/octave slope (less 3 dB/octave of band widening) would put 4 kHz at -9 dB
        // relative to 500 Hz. The extra roll-off must take it well below that.
        val high = db(bandEnergy(NoiseColorCalibration.BROWN, 4_000.0) / reference)
        assertTrue(high < -15.0, "brown 4 kHz sat at $high dB relative to 500 Hz")

        // ...without thinning the low end that gives brown its weight.
        val low = db(bandEnergy(NoiseColorCalibration.BROWN, 125.0) / reference)
        assertTrue(low > 4.0, "brown 125 Hz sat at $low dB relative to 500 Hz")
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
