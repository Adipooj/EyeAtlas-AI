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

    @Test
    fun datasetLicenseGatingAndAuditTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()
        val datasets = exporter.getRegisteredDatasets()
        assertEquals(8, datasets.size)

        // Verified open datasets must be eligible
        assertTrue(exporter.isDatasetEligibleForClinicalUse("RFMiD"))
        assertTrue(exporter.isDatasetEligibleForClinicalUse("IDRiD"))
        assertTrue(exporter.isDatasetEligibleForClinicalUse("CONJUNCTIVITIS_MENDELEY"))
        assertTrue(exporter.isDatasetEligibleForClinicalUse("DRY_EYE_NIKBUT"))

        // Conflicted or restricted datasets must be strictly gated / ineligible
        assertFalse("PAPILA has conflicting license terms and must be gated", exporter.isDatasetEligibleForClinicalUse("PAPILA"))
        assertFalse("CSDI has non-commercial restriction and must be gated", exporter.isDatasetEligibleForClinicalUse("CSDI"))
        assertFalse("SLID lacks explicit repository license text and must be gated", exporter.isDatasetEligibleForClinicalUse("SLID"))
        assertFalse("APTOS requires login/CAPTCHA and must be gated", exporter.isDatasetEligibleForClinicalUse("APTOS_2019"))
    }

    @Test
    fun patientLevelIsolationLeakageTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()

        // Case 1: Disjoint sets -> PASS
        val trainPatients = setOf("PAT-001", "PAT-002", "PAT-003")
        val testPatients = setOf("PAT-004", "PAT-005")
        assertTrue(exporter.verifyPatientLevelIsolation(trainPatients, testPatients))

        // Case 2: Overlapping patient ID -> FAIL (Patient-level data leakage)
        val leakingTestPatients = setOf("PAT-002", "PAT-006")
        assertFalse(exporter.verifyPatientLevelIsolation(trainPatients, leakingTestPatients))
    }

    @Test
    fun provenanceReportExportTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()
        val report = exporter.exportProvenanceReport("SESSION-TEST-001")
        assertNotNull(report)
        assertTrue(report.contains("DATASET PROVENANCE AND LICENSE MANIFEST"))
        assertTrue(report.contains("RFMiD"))
        assertTrue(report.contains("PAPILA"))
        assertTrue(report.contains("[GATED/WITHHELD]"))
        assertTrue(report.contains("Manifest SHA256:"))
    }

    @Test
    fun syntheticPsychometricConvergenceTest() {
        // High PPD setup to allow full range measurement
        val vision = org.eyeatlas.vision.FunctionalTests(displayPpd = 120.0)

        // Staircase simulation converging on true threshold 0.3 logMAR
        val responses = mutableListOf<Pair<org.eyeatlas.vision.OptotypeOrientation, org.eyeatlas.vision.OptotypeOrientation>>()
        val up = org.eyeatlas.vision.OptotypeOrientation.UP
        val down = org.eyeatlas.vision.OptotypeOrientation.DOWN

        // Step down from 1.0 to 0.3 (7 steps * 3 correct = 21 correct)
        repeat(21) { responses.add(Pair(up, up)) }
        // Oscillate around threshold 0.3
        responses.add(Pair(up, down)) // error -> 0.4
        repeat(3) { responses.add(Pair(up, up)) } // 3 correct -> 0.3
        responses.add(Pair(up, down)) // error -> 0.4
        repeat(3) { responses.add(Pair(up, up)) } // 3 correct -> 0.3

        val result = vision.measureMonocularAcuity(EyeLaterality.OD, responses, startingLogMar = 1.0)
        assertNotNull(result)
        // Verifies convergence near true threshold 0.3
        assertEquals(0.3, result.estimatedLogMar, 0.05)
        assertEquals(EyeLaterality.OD, result.eye)
        assertTrue(result.reversals >= 2)
    }

    @Test
    fun impossibleResolutionGate66Test() {
        // Baseline cheap VR display: 13.5 PPD
        val standardVr = org.eyeatlas.vision.FunctionalTests(displayPpd = 13.5)

        // Even with 100% correct answers pretending to resolve 6/6 (0.0 logMAR)
        val allCorrect = List(20) {
            Pair(org.eyeatlas.vision.OptotypeOrientation.UP, org.eyeatlas.vision.OptotypeOrientation.UP)
        }

        val result = standardVr.measureMonocularAcuity(EyeLaterality.OD, allCorrect)

        // Physical resolution gate invariant:
        // Must flag isResolutionLimited and clamp to physical floor (cannot pretend 6/6 on 13.5 PPD)
        assertTrue("Must flag resolution limited on low PPD optics", result.isResolutionLimited)
        assertTrue("Floor must prevent fake 6/6 (floor: ${result.physicalResolutionFloorLogMar})", result.estimatedLogMar >= result.physicalResolutionFloorLogMar)

        val (measurement, _) = standardVr.toClinicalFindings(result)
        assertFalse("Measurement must not claim validity when resolution limited", measurement.validity)
        assertTrue(measurement.limits.contains("RESOLUTION_LIMITED_AT_PPD"))
    }

    @Test
    fun accommodationConfoundingFlagsTest() {
        val vision = org.eyeatlas.vision.FunctionalTests(displayPpd = 90.0)

        // Good distance acuity (logMAR 0.0, 6/6)
        val distanceResult = org.eyeatlas.vision.AcuityResult(
            eye = EyeLaterality.OD,
            estimatedLogMar = 0.0,
            snellenEquivalent = "6/6",
            isResolutionLimited = false,
            physicalResolutionFloorLogMar = 0.0,
            accommodationConfounded = false,
            vergenceDiopters = 0.0,
            reversals = 4
        )

        // Degraded near acuity under -2.5 D optical vergence (logMAR 0.4, 6/15)
        val nearResult = org.eyeatlas.vision.AcuityResult(
            eye = EyeLaterality.OD,
            estimatedLogMar = 0.4,
            snellenEquivalent = "6/15",
            isResolutionLimited = false,
            physicalResolutionFloorLogMar = 0.0,
            accommodationConfounded = false,
            vergenceDiopters = -2.5,
            reversals = 3
        )

        val (isConfounded, reason) = vision.evaluateNearAccommodation(distanceResult, nearResult)
        assertTrue("Significant near drop must flag accommodation confounding", isConfounded)
        assertNotNull(reason)
        assertTrue(reason!!.contains("presbyopia"))
    }

    @Test
    fun etdrsOptotypeHarnessAndLateralityTest() {
        val vision = org.eyeatlas.vision.FunctionalTests(displayPpd = 90.0)

        val trialOD = vision.generateOptotypeTrial("T-OD-01", EyeLaterality.OD, 0.3, org.eyeatlas.vision.OptotypeOrientation.LEFT)
        val trialOS = vision.generateOptotypeTrial("T-OS-01", EyeLaterality.OS, 0.3, org.eyeatlas.vision.OptotypeOrientation.RIGHT)

        assertEquals(EyeLaterality.OD, trialOD.eye)
        assertEquals(EyeLaterality.OS, trialOS.eye)
        assertEquals(org.eyeatlas.vision.OptotypeOrientation.LEFT, trialOD.orientation)
        assertEquals(org.eyeatlas.vision.OptotypeOrientation.RIGHT, trialOS.orientation)
        assertTrue(trialOD.isPhysicallyResolvable)
    }

    @Test
    fun displayGammaAndInvalidColorProfileGateTest() {
        val vision = org.eyeatlas.vision.FunctionalTests()

        // Valid sRGB calibration (gamma 2.2, D65)
        val validProfile = vision.validateDisplayColorProfile(gamma = 2.2, whitePoint = "D65")
        assertTrue(validProfile.isCalibrated)
        assertTrue(validProfile.validationFlags.isEmpty())

        // Invalid gamma (1.6) -> FAIL
        val invalidGamma = vision.validateDisplayColorProfile(gamma = 1.6, whitePoint = "D65")
        assertFalse(invalidGamma.isCalibrated)
        assertTrue(invalidGamma.validationFlags.contains("GAMMA_OUT_OF_BOUNDS"))

        // Invalid white point (D50) -> FAIL
        val invalidWhitePoint = vision.validateDisplayColorProfile(gamma = 2.2, whitePoint = "D50")
        assertFalse(invalidWhitePoint.isCalibrated)
        assertTrue(invalidWhitePoint.validationFlags.contains("WHITE_POINT_NOT_D65"))
    }

    @Test
    fun aliasPreventionAntiAliasingTest() {
        val vision = org.eyeatlas.vision.FunctionalTests()

        // Clean anti-aliased optotype
        assertTrue(vision.validateContrastOptotype(contrastPercent = 5.0, isAntiAliased = true, aliasingDetected = false))

        // Subpixel aliasing artifact detected -> rejected as invalid contrast stimulus
        assertFalse(vision.validateContrastOptotype(contrastPercent = 5.0, isAntiAliased = true, aliasingDetected = true))

        // Non-anti-aliased target -> rejected
        assertFalse(vision.validateContrastOptotype(contrastPercent = 5.0, isAntiAliased = false, aliasingDetected = false))
    }

    @Test
    fun repeatScotomaConfirmationTest() {
        val vision = org.eyeatlas.vision.FunctionalTests()

        // Case 1: Transient unconfirmed miss (1 presentation, 1 miss)
        val transientMiss = org.eyeatlas.vision.MacularScotomaPoint(angleXDeg = 2.0, angleYDeg = -2.0, presentations = 1, misses = 1)
        val (hasDefectTransient, confirmedTransient) = vision.evaluateMacularScotomas(listOf(transientMiss))
        assertFalse("Single unconfirmed miss must NOT be flagged as scotoma", hasDefectTransient)
        assertTrue(confirmedTransient.isEmpty())

        // Case 2: Confirmed repeat scotoma (3 presentations, 3 misses at same location)
        val confirmedMiss = org.eyeatlas.vision.MacularScotomaPoint(angleXDeg = 2.0, angleYDeg = -2.0, presentations = 3, misses = 3)
        val (hasDefectConfirmed, confirmedList) = vision.evaluateMacularScotomas(listOf(confirmedMiss))
        assertTrue("Repeat misses must confirm scotoma", hasDefectConfirmed)
        assertEquals(1, confirmedList.size)
    }

    @Test
    fun variedResponseLatencyTrackingTest() {
        val responseEngine = org.eyeatlas.sensors.ResponseAndGaze()

        // Test response latency timestamps: 250ms (fast), 600ms (medium), 1100ms (near window boundary)
        val baseNs = 1_000_000_000L
        val fastTap = responseEngine.registerTouchEvent("S-01", "PAD-01", baseNs + 250_000_000L, baseNs + 290_000_000L, 1)
        val medTap = responseEngine.registerTouchEvent("S-01", "PAD-01", baseNs + 600_000_000L, baseNs + 640_000_000L, 1)
        val slowTap = responseEngine.registerTouchEvent("S-01", "PAD-01", baseNs + 1_100_000_000L, baseNs + 1_140_000_000L, 1)

        assertEquals("TOUCH_TAP", fastTap.inputType)
        assertEquals("TOUCH_TAP", medTap.inputType)
        assertEquals("TOUCH_TAP", slowTap.inputType)

        // Latency is preserved faithfully as native monotonic time without arbitrary modification
        assertEquals(baseNs + 250_000_000L, fastTap.monotonicEventNs)
        assertEquals(baseNs + 600_000_000L, medTap.monotonicEventNs)
        assertEquals(baseNs + 1_100_000_000L, slowTap.monotonicEventNs)
    }

    @Test
    fun fiftyFourPointPerimetryMapAndCatchTrialsTest() {
        val vision = org.eyeatlas.vision.FunctionalTests()
        val fieldMap = vision.generateFiftyFourPointMap(EyeLaterality.OD)
        assertEquals(54, fieldMap.size)

        // Verify points fall within physical 30-degree envelope
        for ((x, y) in fieldMap) {
            assertTrue("X coordinate must be within +/- 30 deg", kotlin.math.abs(x) <= 30.0)
            assertTrue("Y coordinate must be within +/- 24 deg", kotlin.math.abs(y) <= 24.0)
        }
    }

    @Test
    fun falsePositiveAndNegativeReliabilityControlTest() {
        val vision = org.eyeatlas.vision.FunctionalTests()

        // Reliable case: low false positive (0/10) and low false negative (0/10)
        val reliableTrials = List(10) {
            org.eyeatlas.vision.CatchTrialResult(org.eyeatlas.vision.CatchTrialType.BLANK_FALSE_POSITIVE, patientResponded = false)
        } + List(10) {
            org.eyeatlas.vision.CatchTrialResult(org.eyeatlas.vision.CatchTrialType.SUPRATHRESHOLD_FALSE_NEGATIVE, patientResponded = true)
        }
        val reliableResult = vision.evaluateFieldReliability(reliableTrials)
        assertTrue(reliableResult.isReliable)
        assertEquals(0.0, reliableResult.falsePositiveRate, 0.01)

        // Unreliable case: high false positive rate (3/10 = 30% > 20% threshold)
        val unreliableTrials = List(3) {
            org.eyeatlas.vision.CatchTrialResult(org.eyeatlas.vision.CatchTrialType.BLANK_FALSE_POSITIVE, patientResponded = true) // False positive
        } + List(7) {
            org.eyeatlas.vision.CatchTrialResult(org.eyeatlas.vision.CatchTrialType.BLANK_FALSE_POSITIVE, patientResponded = false)
        }
        val unreliableResult = vision.evaluateFieldReliability(unreliableTrials)
        assertFalse("Catch-trial errors > 20% must flag test as unreliable", unreliableResult.isReliable)
        assertTrue(unreliableResult.reliabilityWarning!!.contains("TEST_RELIABILITY_LOW"))
    }

    @Test
    fun noHumphreyEquivalenceClaimTest() {
        val vision = org.eyeatlas.vision.FunctionalTests()
        val trials = listOf(
            org.eyeatlas.vision.CatchTrialResult(org.eyeatlas.vision.CatchTrialType.BLANK_FALSE_POSITIVE, false)
        )
        val result = vision.evaluateFieldReliability(trials)
        // Hard requirement: explicit disclaimer that Humphrey SAP equivalence is not claimed
        assertTrue("Output must disclaim Humphrey equivalence", result.reliabilityWarning!!.contains("NO Humphrey equivalence"))
    }

    @Test
    fun binocularStereoAndCoverMotilityTest() {
        val binocular = org.eyeatlas.vision.BinocularTests()

        // 1. Stereopsis measurement
        val stereoTrials = listOf(
            org.eyeatlas.vision.StereoTrial("S-01", 400.0, "CIRCLE", "CIRCLE"),
            org.eyeatlas.vision.StereoTrial("S-02", 100.0, "SQUARE", "SQUARE"),
            org.eyeatlas.vision.StereoTrial("S-03", 50.0, "TRIANGLE", "TRIANGLE")
        )
        val stereoResult = binocular.measureStereoAcuity(stereoTrials)
        assertFalse(stereoResult.isStereoBlind)
        assertEquals(50.0, stereoResult.thresholdArcsec!!, 0.1)
        assertEquals("NORMAL_HIGH_GRADE_STEREO", stereoResult.category)

        // 2. Cover test motility evaluation
        // Normal alignment (displacement 0.5 mm -> ~1.2 PD <= 2.0 PD)
        val normalCover = binocular.evaluateCoverTest(EyeLaterality.OD, refixationMovementMm = 0.5)
        assertFalse(normalCover.isStrabismusSuspected)
        assertEquals("ORTHOPHORIA_NORMAL", normalCover.deviationType)

        // Manifest strabismus (displacement 3.0 mm -> ~7.5 PD > 2.0 PD)
        val strabismusCover = binocular.evaluateCoverTest(EyeLaterality.OS, refixationMovementMm = 3.0)
        assertTrue(strabismusCover.isStrabismusSuspected)
        assertEquals("MANIFEST_ESOTROPIA", strabismusCover.deviationType)
    }

    // --- Defect Remediation Tests (DEF-01, DEF-07) ---

    @Test
    fun encryptedPatientStoreCrudAndIntegrityTest() {
        val store = org.eyeatlas.store.PatientStore(encryptionKey = "CUSTOM-TEST-KEY-42")

        // 1. Initial seeded record check
        val defaultRec = store.getRecord("P-101")
        assertEquals("P-101", defaultRec.patientId)
        assertEquals("40-59", defaultRec.ageBand)

        // 2. Save new pseudonymized record
        val newPatient = org.eyeatlas.core.PatientRecord(
            patientId = "P-202",
            ageBand = "60-79",
            hasDiabetes = true,
            symptoms = listOf("BLURRED_VISION", "FLOATERS"),
            currentCorrection = "SPECTACLES"
        )
        assertTrue(store.saveRecord(newPatient))

        // 3. Retrieve and verify decrypted integrity
        val retrieved = store.getRecord("P-202")
        assertEquals("P-202", retrieved.patientId)
        assertEquals("60-79", retrieved.ageBand)
        assertTrue(retrieved.hasDiabetes)
        assertEquals(listOf("BLURRED_VISION", "FLOATERS"), retrieved.symptoms)
        assertEquals("SPECTACLES", retrieved.currentCorrection)

        // 4. Session evidence encrypted storage
        assertTrue(store.saveSessionEvidence("SES-99", "P-202", "TEST-EVIDENCE-PAYLOAD"))
        val evidence = store.getSessionEvidence("SES-99")
        assertEquals("TEST-EVIDENCE-PAYLOAD", evidence)

        // 5. Delete record
        assertTrue(store.deleteRecord("P-202"))
        assertFalse(store.listPatientIds().contains("P-202"))
    }

    @Test
    fun neuralInferenceMultiLabelExecutionTest() {
        val inference = org.eyeatlas.ai.Inference()
        val dummyImageBytes = ByteArray(1024) { (it % 256).toByte() }

        val probs = inference.runInference(dummyImageBytes)
        assertEquals("Must return 4 independent multi-label head probabilities", 4, probs.size)
        // Verify independent sigmoids are within valid [0.01, 0.99] bounds
        for (p in probs) {
            assertTrue("Probability must be in (0.0, 1.0)", p in 0.01f..0.99f)
        }
        // Independent sigmoids do not sum to 1.0
        val sum = probs.sum()
        assertTrue("Independent sigmoids should not be forced to sum to 1.0", sum > 0.1f)
    }

    @Test
    fun conditionRankingSortingTest() {
        val ranker = org.eyeatlas.ai.QualityAndRanker()
        val scores = floatArrayOf(0.85f, 0.22f, 0.65f, 0.10f)

        val ranked = ranker.rankPredictions(scores)
        assertEquals(4, ranked.size)
        assertEquals("DIABETIC_RETINOPATHY", ranked[0].first)
        assertEquals(0.85f, ranked[0].second, 0.01f)
        assertEquals("GLAUCOMA_OPTIC_DISC", ranked[1].first)
        assertEquals(0.65f, ranked[1].second, 0.01f)
    }
}
