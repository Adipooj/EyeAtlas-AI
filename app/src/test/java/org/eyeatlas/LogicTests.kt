package org.eyeatlas

import org.eyeatlas.core.*
import org.junit.Assert.*
import org.junit.Test

class LogicTests {

    @Test
    fun modelSerializationAndHashingPropertyTest() {
        val record = PatientRecord(
            patientId = "PATIENT-SECRET-12345",
            ageBand = "40-59",
            hasDiabetes = true,
            symptoms = listOf("BLURRED_VISION"),
            currentCorrection = "GLASSES"
        )
        val hash = record.anonymizedHash()
        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 produces 64 hex chars
        assertFalse(hash.contains("PATIENT-SECRET-12345"))
    }

    @Test
    fun lateralityIndependenceTest() {
        val od = EyeLaterality.OD
        val os = EyeLaterality.OS
        assertNotEquals(od, os)

        val measurementEngine = MeasurementEngine()
        val odAcuity = Measurement(
            measurementId = "M-OD-01",
            provenanceIds = listOf("TRIAL-OD-01"),
            eye = EyeLaterality.OD,
            method = "ACUITY_LOGMAR",
            value = 0.1, // good acuity
            units = "logMAR",
            validity = true
        )
        val osAcuity = Measurement(
            measurementId = "M-OS-01",
            provenanceIds = listOf("TRIAL-OS-01"),
            eye = EyeLaterality.OS,
            method = "ACUITY_LOGMAR",
            value = 1.0, // impaired acuity
            units = "logMAR",
            validity = true
        )

        measurementEngine.recordMeasurement(odAcuity)
        measurementEngine.recordMeasurement(osAcuity)

        val odResults = measurementEngine.getMeasurementsForEye(EyeLaterality.OD)
        val osResults = measurementEngine.getMeasurementsForEye(EyeLaterality.OS)

        assertEquals(1, odResults.size)
        assertEquals(1, osResults.size)
        assertEquals(0.1, odResults[0].value, 0.001)
        assertEquals(1.0, osResults[0].value, 0.001)
    }

    @Test
    fun dualDiseaseCoexistencePropertyTest() {
        val decisionEngine = DecisionEngine()
        val availableTests = setOf(TestDomain.VISUAL_FIELD, TestDomain.ANTERIOR)

        val measurements = listOf(
            Measurement(
                measurementId = "M-01",
                provenanceIds = listOf("PROV-1"),
                eye = EyeLaterality.OD,
                method = "PERIMETRY_54PT",
                value = 12.0, // abnormal field
                units = "points",
                validity = true
            ),
            Measurement(
                measurementId = "M-02",
                provenanceIds = listOf("PROV-2"),
                eye = EyeLaterality.OD,
                method = "MEDIA_OPACITY",
                value = 0.75, // media opacity present
                units = "index",
                validity = true
            )
        )

        val inferences = decisionEngine.evaluateEyeFindings(
            eye = EyeLaterality.OD,
            measurements = measurements,
            availableTests = availableTests
        )

        // Verifies coexisting diagnoses for the SAME eye
        assertEquals(2, inferences.size)
        val glaucoma = inferences.first { it.diseaseCode == "GLAUCOMA_SUSPICION" }
        val cataract = inferences.first { it.diseaseCode == "CATARACT_SUSPICION" }

        assertNotNull(glaucoma.probabilityOrScore)
        assertNotNull(cataract.probabilityOrScore)

        // Non-exclusive invariant: Probabilities MUST NOT be constrained to sum to 1.0 (no softmax)
        val scoreSum = glaucoma.probabilityOrScore!! + cataract.probabilityOrScore!!
        assertTrue("Dual disease scores must be independent (sum: $scoreSum)", scoreSum > 1.0)
    }

    @Test
    fun missingEvidencePropertyTest() {
        val decisionEngine = DecisionEngine()
        // Simulate missing visual field test
        val availableTests = setOf(TestDomain.ANTERIOR)

        val inferences = decisionEngine.evaluateEyeFindings(
            eye = EyeLaterality.OS,
            measurements = emptyList(),
            availableTests = availableTests
        )

        val glaucoma = inferences.first { it.diseaseCode == "GLAUCOMA_SUSPICION" }
        // Invariant: Missing test must NOT be treated as negative evidence
        assertTrue("Missing test must be flagged in unknownEvidence", glaucoma.unknownEvidence.isNotEmpty())
        assertTrue("Missing test must NOT be claimed negative evidence", glaucoma.evidenceAgainst.isEmpty())
        assertNull("Unassessed test cannot produce numeric diagnostic score", glaucoma.probabilityOrScore)
        assertEquals("PERIMETRY_54PT", glaucoma.recommendedTest)
    }

    @Test
    fun noPretendDiagnosisInvariantTest() {
        try {
            ConditionInference(
                eye = EyeLaterality.OD,
                diseaseCode = "DIABETIC_RETINOPATHY",
                probabilityOrScore = 0.85,
                calibrationId = "" // Empty calibration must fail
            )
            fail("Expected IllegalArgumentException when score provided without calibration ID")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("calibration"))
        }
    }
}
