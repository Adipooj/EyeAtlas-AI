package org.eyeatlas

import org.eyeatlas.core.*
import org.junit.Assert.*
import org.junit.Test

class MultiDiseaseTests {
    @Test
    fun baselineMultiDiseaseVerification() {
        val engine = DecisionEngine()
        val results = engine.evaluate(EyeLaterality.OD)
        assertNotNull(results)
    }

    @Test
    fun independentPerEyeMultiDiseaseEvaluation() {
        val engine = DecisionEngine()
        val available = setOf(TestDomain.VISUAL_FIELD, TestDomain.ANTERIOR)
        val odMeasurements = listOf(
            Measurement("M-1", listOf("P-1"), EyeLaterality.OD, "PERIMETRY_54PT", 15.0, "pts", null, null, true),
            Measurement("M-2", listOf("P-2"), EyeLaterality.OD, "MEDIA_OPACITY", 0.8, "ratio", null, null, true)
        )
        val inferences = engine.evaluateEyeFindings(EyeLaterality.OD, odMeasurements, available)
        assertEquals(2, inferences.size)
        assertTrue(inferences.any { it.diseaseCode == "GLAUCOMA_SUSPICION" })
        assertTrue(inferences.any { it.diseaseCode == "CATARACT_SUSPICION" })
    }
}
