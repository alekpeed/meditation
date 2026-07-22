package com.meditation.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class NoiseColorCalibrationTest {
    @Test fun `white and brown have the same uncompressed stationary RMS target`() {
        assertEquals(NoiseColorCalibration.TARGET_RMS, NoiseColorCalibration.whiteGain * NoiseColorCalibration.UNIFORM_WHITE_RMS, 1e-12)
        assertEquals(NoiseColorCalibration.TARGET_RMS, NoiseColorCalibration.brownStationaryRms(), 1e-12)
    }

    @Test fun `brown shelf is above sub bass and below low midrange`() {
        assertTrue(NoiseColorCalibration.brownCornerHz in 60.0..80.0)
    }
}
