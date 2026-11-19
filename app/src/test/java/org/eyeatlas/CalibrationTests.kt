package org.eyeatlas

import org.eyeatlas.sensors.Calibration
import org.junit.Assert.assertNotNull
import org.junit.Test

class CalibrationTests {
    @Test
    fun baselineCalibrationVerification() {
        val cal = Calibration()
        assertNotNull(cal.calibrationHash)
    }
}
