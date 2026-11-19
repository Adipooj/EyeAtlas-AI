package org.eyeatlas

import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.PatientRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LogicTests {
    @Test
    fun baselineLogicVerification() {
        val record = PatientRecord("P-001", 1000L, "Baseline test")
        assertEquals("P-001", record.id)
        assertNotNull(record)
    }

    @Test
    fun lateralityIndependenceCheck() {
        val od = EyeLaterality.OD
        val os = EyeLaterality.OS
        assert(od != os)
    }
}
