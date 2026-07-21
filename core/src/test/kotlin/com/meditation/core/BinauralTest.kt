package com.meditation.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BinauralTest {
    @Test fun `ear frequencies split symmetrically around the carrier`() {
        val (l, r) = Binaural.earFrequencies(carrierHz = 200.0, beatHz = 6.0)
        assertEquals(197.0, l, 1e-9)
        assertEquals(203.0, r, 1e-9)
        assertEquals(6.0, Binaural.beatOf(l, r), 1e-9)
        // carrier is the midpoint
        assertEquals(200.0, (l + r) / 2.0, 1e-9)
    }

    @Test fun `common bands land in the beat range`() {
        for (beat in listOf(2.0, 6.0, 10.0, 20.0)) {
            assertTrue(beat in Binaural.BEAT_RANGE)
            val (l, r) = Binaural.earFrequencies(220.0, beat)
            assertEquals(beat, Binaural.beatOf(l, r), 1e-9)
        }
    }
}
