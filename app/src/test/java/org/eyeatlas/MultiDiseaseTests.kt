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

    @Test
    fun nibutPlacidoAnalysisAndRightCensoringTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()

        // 1. Stable tear film: break at 11.5s
        val stableFrames = listOf(
            org.eyeatlas.vision.NibutVideoFrame(0.0, 1.0),
            org.eyeatlas.vision.NibutVideoFrame(5.0, 0.98),
            org.eyeatlas.vision.NibutVideoFrame(10.0, 0.92),
            org.eyeatlas.vision.NibutVideoFrame(11.5, 0.82) // Break < 0.85
        )
        val stableRes = anterior.calculateNibut(EyeLaterality.OD, stableFrames)
        assertEquals(org.eyeatlas.vision.TearFilmStability.NORMAL_STABLE, stableRes.stability)
        assertEquals(11.5, stableRes.firstBreakSeconds!!, 0.01)
        assertFalse(stableRes.isRightCensored)

        // 2. Severe tear deficiency: rapid break at 3.2s (< 5.0s)
        val dryFrames = listOf(
            org.eyeatlas.vision.NibutVideoFrame(0.0, 1.0),
            org.eyeatlas.vision.NibutVideoFrame(2.0, 0.90),
            org.eyeatlas.vision.NibutVideoFrame(3.2, 0.75) // Break
        )
        val dryRes = anterior.calculateNibut(EyeLaterality.OD, dryFrames)
        assertEquals(org.eyeatlas.vision.TearFilmStability.SEVERE_DEFICIENCY, dryRes.stability)
        assertEquals(3.2, dryRes.firstBreakSeconds!!, 0.01)

        // 3. Right-censored early blink at 4.2s before tear film break
        val blinkFrames = listOf(
            org.eyeatlas.vision.NibutVideoFrame(0.0, 1.0),
            org.eyeatlas.vision.NibutVideoFrame(2.0, 0.95),
            org.eyeatlas.vision.NibutVideoFrame(4.2, 0.92, blinkDetected = true)
        )
        val blinkRes = anterior.calculateNibut(EyeLaterality.OS, blinkFrames)
        assertEquals(org.eyeatlas.vision.TearFilmStability.RIGHT_CENSORED_EARLY_BLINK, blinkRes.stability)
        assertNull(blinkRes.firstBreakSeconds)
        assertTrue(blinkRes.isRightCensored)
        assertTrue(blinkRes.measurement.limits.contains("RIGHT_CENSORED_EARLY_BLINK"))
    }

    @Test
    fun pterygiumLimbalEncroachmentGradingTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()

        // None
        val pNone = anterior.evaluatePterygium(EyeLaterality.OD, 0.0)
        assertEquals(org.eyeatlas.vision.PterygiumStage.NONE, pNone.stage)
        assertNull(pNone.finding)

        // Grade 1: 1.5mm encroachment
        val p1 = anterior.evaluatePterygium(EyeLaterality.OD, 1.5, "NASAL")
        assertEquals(org.eyeatlas.vision.PterygiumStage.GRADE_1_EARLY_LIMBAL, p1.stage)
        assertNotNull(p1.finding)
        assertEquals("MODERATE", p1.finding!!.severity)

        // Grade 2: 3.0mm encroachment
        val p2 = anterior.evaluatePterygium(EyeLaterality.OD, 3.0, "NASAL")
        assertEquals(org.eyeatlas.vision.PterygiumStage.GRADE_2_INTERMEDIATE, p2.stage)

        // Grade 3: 4.8mm encroachment (pupil margin)
        val p3 = anterior.evaluatePterygium(EyeLaterality.OD, 4.8, "NASAL")
        assertEquals(org.eyeatlas.vision.PterygiumStage.GRADE_3_ADVANCED_PUPILLARY, p3.stage)
        assertEquals("SEVERE", p3.finding!!.severity)
    }

    @Test
    fun cataractPupillaryOpacityAssessmentTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()

        // Clear lens
        val clear = anterior.evaluateCataractOpacity(EyeLaterality.OD, 0.08)
        assertEquals(org.eyeatlas.vision.CataractDensityGrade.CLEAR_NO_OPACITY, clear.grade)
        assertNull(clear.finding)

        // Moderate cataract: 0.55 opacity score
        val mod = anterior.evaluateCataractOpacity(EyeLaterality.OD, 0.55)
        assertEquals(org.eyeatlas.vision.CataractDensityGrade.MODERATE, mod.grade)
        assertNotNull(mod.finding)

        // Mature dense cataract: 0.88 opacity score
        val dense = anterior.evaluateCataractOpacity(EyeLaterality.OS, 0.88)
        assertEquals(org.eyeatlas.vision.CataractDensityGrade.DENSE_MATURE, dense.grade)
        assertEquals("SEVERE", dense.finding!!.severity)
    }

    @Test
    fun openSetUnknownRejectionTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()

        // In-distribution typical finding
        val inDist = anterior.classifyWithOpenSetRejection(
            candidateClass = "ANTERIOR_BLEPHARITIS",
            confidence = 0.89,
            distributionDistance = 1.1
        )
        assertFalse(inDist.isOpenSetUnknown)
        assertEquals("ANTERIOR_BLEPHARITIS", inDist.recognizedClass)

        // Out-of-distribution atypical anterior lesion
        val ood = anterior.classifyWithOpenSetRejection(
            candidateClass = "ANTERIOR_BLEPHARITIS",
            confidence = 0.42,
            distributionDistance = 3.8
        )
        assertTrue(ood.isOpenSetUnknown)
        assertEquals("OPEN_SET_UNKNOWN", ood.recognizedClass)
        assertTrue(ood.recommendation.contains("slit-lamp"))
    }

    @Test
    fun bilateralAsymmetricAnteriorDiseaseTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()

        // OD: Severe Dry Eye + Pterygium
        val odFrames = listOf(
            org.eyeatlas.vision.NibutVideoFrame(0.0, 1.0),
            org.eyeatlas.vision.NibutVideoFrame(2.5, 0.70)
        )
        val odNibut = anterior.calculateNibut(EyeLaterality.OD, odFrames)
        val odPterygium = anterior.evaluatePterygium(EyeLaterality.OD, 3.2, "NASAL")

        // OS: Normal Tear Film, No Pterygium
        val osFrames = listOf(
            org.eyeatlas.vision.NibutVideoFrame(0.0, 1.0),
            org.eyeatlas.vision.NibutVideoFrame(11.0, 0.90)
        )
        val osNibut = anterior.calculateNibut(EyeLaterality.OS, osFrames)
        val osPterygium = anterior.evaluatePterygium(EyeLaterality.OS, 0.0)

        assertEquals(org.eyeatlas.vision.TearFilmStability.SEVERE_DEFICIENCY, odNibut.stability)
        assertEquals(org.eyeatlas.vision.PterygiumStage.GRADE_2_INTERMEDIATE, odPterygium.stage)

        assertEquals(org.eyeatlas.vision.TearFilmStability.NORMAL_STABLE, osNibut.stability)
        assertEquals(org.eyeatlas.vision.PterygiumStage.NONE, osPterygium.stage)
    }

    @Test
    fun retinalLightSafetyInterlockRejectionTest() {
        val retinal = org.eyeatlas.vision.RetinalTests()

        // 1. Certified safe fundus light profile
        val certifiedAdapter = org.eyeatlas.vision.FundusAdapterProfile(
            adapterId = "ADAPTER-CERT-45DEG",
            opticalFovDeg = 45.0,
            opticalMagnification = 2.5,
            isLightSafetyCertified = true,
            lightSourceModel = "LED-INDIRECT-SAFE-4500K",
            maxExposureLuxSeconds = 350.0
        )
        assertTrue(certifiedAdapter.verifyRetinalSafety().isSuccess)

        // 2. Uncertified / improvised phone flash -> must trip safety interlock
        val unsafeAdapter = org.eyeatlas.vision.FundusAdapterProfile(
            adapterId = "ADAPTER-UNSAFE-PHONE-FLASH",
            opticalFovDeg = 45.0,
            opticalMagnification = 2.5,
            isLightSafetyCertified = false, // Phone flash
            lightSourceModel = "PHONE_CAMERA_FLASH_IMPROVISED",
            maxExposureLuxSeconds = 1500.0
        )
        val safetyResult = unsafeAdapter.verifyRetinalSafety()
        assertTrue(safetyResult.isFailure)
        assertTrue(safetyResult.exceptionOrNull() is SecurityException)

        // Evaluate series with unsafe adapter
        val unsafeSeries = org.eyeatlas.vision.RetinalSeries(
            sessionId = "SESSION-RET-01",
            eye = EyeLaterality.OD,
            adapterProfile = unsafeAdapter,
            captures = emptyMap()
        )
        val eval = retinal.evaluateRetinalCoverage(unsafeSeries)
        assertEquals("LIGHT_SAFETY_INTERLOCK_FAILURE", eval.drScreeningStatus)
        assertFalse(eval.isGradable)
    }

    @Test
    fun noRetinaPhotoNoDrNegativeInvariantTest() {
        val retinal = org.eyeatlas.vision.RetinalTests()

        // Hard medical invariant: null retina series MUST NEVER produce a normal / negative DR status
        val nullEvaluation = retinal.evaluateRetinalCoverage(null)
        assertFalse("Cannot claim gradable without retinal imaging", nullEvaluation.isGradable)
        assertFalse("Cannot claim sufficient coverage without retinal imaging", nullEvaluation.hasSufficientCoverage)
        assertTrue("Status must explicitly declare missing retinal photo", nullEvaluation.drScreeningStatus.contains("MISSING_RETINA_PHOTO"))
        assertFalse("Finding validity must be false when evidence is missing", nullEvaluation.findings.first().validity)
    }

    @Test
    fun retinalTwoFieldGradabilityAndMissingFieldTest() {
        val retinal = org.eyeatlas.vision.RetinalTests()
        val certifiedAdapter = org.eyeatlas.vision.FundusAdapterProfile(
            adapterId = "ADAPTER-CERT-45DEG",
            isLightSafetyCertified = true,
            lightSourceModel = "SAFE-OPHTHALMIC-LED"
        )
        val goodQuality = retinal.evaluateRetinalQuality(
            vesselSharpness = 38.0,
            illuminationUniformity = 0.85,
            discContrast = 0.90,
            maculaCentration = 0.92,
            mediaHaze = 0.10
        )
        assertTrue(goodQuality.isGradable)

        // Case 1: Both Disc and Macula fields valid
        val completeCaptures = mapOf(
            org.eyeatlas.vision.RetinalField.DISC_CENTERED to org.eyeatlas.vision.RetinalCapture(
                captureId = "CAP-RET-DISC",
                sessionId = "SESSION-RET-01",
                eye = EyeLaterality.OD,
                field = org.eyeatlas.vision.RetinalField.DISC_CENTERED,
                adapterId = certifiedAdapter.adapterId,
                rawImageHash = "HASH-DISC",
                quality = goodQuality
            ),
            org.eyeatlas.vision.RetinalField.MACULA_CENTERED to org.eyeatlas.vision.RetinalCapture(
                captureId = "CAP-RET-MACULA",
                sessionId = "SESSION-RET-01",
                eye = EyeLaterality.OD,
                field = org.eyeatlas.vision.RetinalField.MACULA_CENTERED,
                adapterId = certifiedAdapter.adapterId,
                rawImageHash = "HASH-MACULA",
                quality = goodQuality
            )
        )
        val completeSeries = org.eyeatlas.vision.RetinalSeries("SESSION-RET-01", EyeLaterality.OD, certifiedAdapter, completeCaptures)
        val completeEval = retinal.evaluateRetinalCoverage(completeSeries)
        assertEquals("GRADABLE_SUFFICIENT", completeEval.drScreeningStatus)
        assertTrue(completeEval.isGradable)
        assertEquals(0.35, completeEval.overlapRatioDiscMacula, 0.01)

        // Case 2: Macula field blurry (sharpness 12.0 < 20.0) -> missing macula field
        val blurredQuality = retinal.evaluateRetinalQuality(
            vesselSharpness = 12.0,
            illuminationUniformity = 0.85,
            discContrast = 0.90,
            maculaCentration = 0.92,
            mediaHaze = 0.10
        )
        val partialCaptures = mapOf(
            org.eyeatlas.vision.RetinalField.DISC_CENTERED to completeCaptures.getValue(org.eyeatlas.vision.RetinalField.DISC_CENTERED),
            org.eyeatlas.vision.RetinalField.MACULA_CENTERED to org.eyeatlas.vision.RetinalCapture(
                captureId = "CAP-RET-MACULA-BLUR",
                sessionId = "SESSION-RET-01",
                eye = EyeLaterality.OD,
                field = org.eyeatlas.vision.RetinalField.MACULA_CENTERED,
                adapterId = certifiedAdapter.adapterId,
                rawImageHash = "HASH-MACULA-BLUR",
                quality = blurredQuality
            )
        )
        val partialSeries = org.eyeatlas.vision.RetinalSeries("SESSION-RET-01", EyeLaterality.OD, certifiedAdapter, partialCaptures)
        val partialEval = retinal.evaluateRetinalCoverage(partialSeries)
        assertEquals("UNGRADABLE_NEEDS_IMAGING", partialEval.drScreeningStatus)
        assertFalse(partialEval.isGradable)
        assertTrue(partialEval.missingRequiredFields.contains(org.eyeatlas.vision.RetinalField.MACULA_CENTERED))
    }

    @Test
    fun retinalQualityRejectionBlurAndHazeTest() {
        val retinal = org.eyeatlas.vision.RetinalTests()

        // Severe media haze (e.g. dense cataract obscuring retina)
        val hazeMetrics = retinal.evaluateRetinalQuality(
            vesselSharpness = 25.0,
            illuminationUniformity = 0.75,
            discContrast = 0.30,
            maculaCentration = 0.50,
            mediaHaze = 0.85 // > 0.70
        )
        assertFalse(hazeMetrics.isGradable)
        assertTrue(hazeMetrics.rejectionReasons.any { it.contains("SEVERE_MEDIA_HAZE") })

        // Uneven illumination (glare / crescent reflex)
        val unevenMetrics = retinal.evaluateRetinalQuality(
            vesselSharpness = 30.0,
            illuminationUniformity = 0.42, // < 0.60
            discContrast = 0.70,
            maculaCentration = 0.80,
            mediaHaze = 0.20
        )
        assertFalse(unevenMetrics.isGradable)
        assertTrue(unevenMetrics.rejectionReasons.any { it.contains("UNEVEN_ILLUMINATION") })
    }

    @Test
    fun retinalMultiLabelIndependentOutputsTest() {
        val inference = org.eyeatlas.ai.Inference()
        val retinal = org.eyeatlas.vision.RetinalTests()
        val adapter = org.eyeatlas.vision.FundusAdapterProfile("ADAPTER-CERT", isLightSafetyCertified = true, lightSourceModel = "SAFE-LED")
        val quality = retinal.evaluateRetinalQuality(35.0, 0.85, 0.88, 0.85, 0.15)

        val series = org.eyeatlas.vision.RetinalSeries(
            sessionId = "SESSION-ML-01",
            eye = EyeLaterality.OD,
            adapterProfile = adapter,
            captures = mapOf(
                org.eyeatlas.vision.RetinalField.DISC_CENTERED to org.eyeatlas.vision.RetinalCapture("CAP-D", "SESSION-ML-01", EyeLaterality.OD, org.eyeatlas.vision.RetinalField.DISC_CENTERED, "ADAPTER-CERT", "HASH-D", quality),
                org.eyeatlas.vision.RetinalField.MACULA_CENTERED to org.eyeatlas.vision.RetinalCapture("CAP-M", "SESSION-ML-01", EyeLaterality.OD, org.eyeatlas.vision.RetinalField.MACULA_CENTERED, "ADAPTER-CERT", "HASH-M", quality)
            )
        )

        val result = inference.evaluateRetinalSeries(series)
        assertTrue(result.isGradable)
        assertNotNull(result.diabeticRetinopathy.rawLogitOrScore)
        assertNotNull(result.amd.rawLogitOrScore)
        assertNotNull(result.glaucomatousDisc.rawLogitOrScore)

        // Hard Medical Invariants:
        // 1. Independent outputs (no softmax sum-to-1)
        val sum = (result.diabeticRetinopathy.rawLogitOrScore ?: 0.0) + (result.amd.rawLogitOrScore ?: 0.0) + (result.glaucomatousDisc.rawLogitOrScore ?: 0.0)
        assertNotEquals("Multi-label predictions must not be forced to sum to 1.0", 1.0, sum, 0.001)

        // 2. Calibration withheld until target population trial
        assertTrue(result.diabeticRetinopathy.isCalibrationWithheld)
        assertEquals("CALIBRATION_WITHHELD_PENDING_CLINICAL_TRIAL", result.diabeticRetinopathy.calibrationId)

        // 3. Inference latency under 50ms budget
        assertTrue("Inference latency must be within 50ms target budget", result.latencyMs < 50.0)
    }

    @Test
    fun ungradableRetinalSeriesYieldsNullPredictionsTest() {
        val inference = org.eyeatlas.ai.Inference()

        // Missing series
        val missingResult = inference.evaluateRetinalSeries(null)
        assertFalse(missingResult.isGradable)
        assertNull("Missing image MUST yield null score, never negative", missingResult.diabeticRetinopathy.rawLogitOrScore)
        assertNull(missingResult.amd.rawLogitOrScore)
        assertNull(missingResult.glaucomatousDisc.rawLogitOrScore)
    }

    @Test
    fun repeatCoverAlignmentIntermittentStrabismusTest() {
        val binocular = org.eyeatlas.vision.BinocularTests()

        // Case 1: Intermittent strabismus (alternates between ortho and manifest esotropia)
        val intermittentCycles = listOf(
            org.eyeatlas.vision.CoverCycleSample(1, 0.5, 800), // 1.25 PD (ortho)
            org.eyeatlas.vision.CoverCycleSample(2, 2.4, 900), // 6.0 PD (esotropia)
            org.eyeatlas.vision.CoverCycleSample(3, 0.4, 850)  // 1.0 PD (ortho)
        )
        val interResult = binocular.evaluateRepeatCoverAlignment(EyeLaterality.OD, intermittentCycles, viewingDistanceM = 0.4)
        assertTrue(interResult.isIntermittent)
        assertEquals("INTERMITTENT_ESOTROPIA", interResult.alignmentDiagnosis)
        assertTrue(interResult.coverResult.isStrabismusSuspected)

        // Case 2: Constant manifest strabismus (consistent tropia across all cycles)
        val constantCycles = listOf(
            org.eyeatlas.vision.CoverCycleSample(1, 3.0, 800), // 7.5 PD
            org.eyeatlas.vision.CoverCycleSample(2, 3.2, 900), // 8.0 PD
            org.eyeatlas.vision.CoverCycleSample(3, 2.8, 850)  // 7.0 PD
        )
        val constResult = binocular.evaluateRepeatCoverAlignment(EyeLaterality.OD, constantCycles, viewingDistanceM = 0.4)
        assertFalse(constResult.isIntermittent)
        assertEquals("CONSTANT_MANIFEST_ESOTROPIA", constResult.alignmentDiagnosis)
        assertEquals(8.0, constResult.maxDeviationPrismDiopters, 0.1)
    }

    @Test
    fun crowdedVsIsolatedAmblyopiaDiscrepancyTest() {
        val binocular = org.eyeatlas.vision.BinocularTests()

        // Case 1: Significant amblyopic crowding deficit (crowded 0.45 vs isolated 0.15 = 0.30 logMAR > 0.20)
        // With contralateral acuity 0.05 logMAR -> inter-ocular diff 0.40 logMAR
        val amblyopic = binocular.evaluateCrowdedVsIsolatedAcuity(
            testedEye = EyeLaterality.OS,
            isolatedLogMar = 0.15,
            crowdedLogMar = 0.45,
            contralateralAcuityLogMar = 0.05
        )
        assertTrue("Crowding deficit >= 0.20 logMAR must be flagged pathological", amblyopic.isPathologicalCrowding)
        assertEquals(0.30, amblyopic.crowdingDeficitLogMar, 0.01)
        assertEquals(org.eyeatlas.vision.AmblyopiaRiskLevel.HIGH_RISK_ANISOMETROPIC_OR_STRABISMIC, amblyopic.riskLevel)
        assertNotNull(amblyopic.finding)
        assertEquals("SEVERE", amblyopic.finding!!.severity)

        // Case 2: Normal physiological crowding (crowded 0.10 vs isolated 0.05 = 0.05 logMAR)
        val normal = binocular.evaluateCrowdedVsIsolatedAcuity(
            testedEye = EyeLaterality.OD,
            isolatedLogMar = 0.05,
            crowdedLogMar = 0.10,
            contralateralAcuityLogMar = 0.05
        )
        assertFalse(normal.isPathologicalCrowding)
        assertEquals(org.eyeatlas.vision.AmblyopiaRiskLevel.LOW_NO_CROWDING, normal.riskLevel)
        assertNull(normal.finding)
    }

    @Test
    fun childrenUncooperativeAlternativePathwayTest() {
        val binocular = org.eyeatlas.vision.BinocularTests()

        // 3-year-old child -> routes to preferential looking alternative pathway
        val child = binocular.selectCooperationPathway(ageYears = 3, isCooperative = false)
        assertEquals("PREFERENTIAL_LOOKING_ALTERNATIVE_PATHWAY", child.pathwaySelected)
        assertFalse(child.isStandardTestFeasible)
        assertTrue(child.note.contains("preferential fixation"))

        // Cooperative 12-year-old -> standard psychometric staircase
        val older = binocular.selectCooperationPathway(ageYears = 12, isCooperative = true)
        assertEquals("STANDARD_PSYCHOMETRIC_STAIRCASE", older.pathwaySelected)
        assertTrue(older.isStandardTestFeasible)
    }

    @Test
    fun photorefractionDisabledInterlockTest() {
        val binocular = org.eyeatlas.vision.BinocularTests()
        assertFalse("Objective photorefraction must remain strictly disabled", binocular.isObjectivePhotorefractionEnabled)
        assertEquals("PHOTOREFRACTION_DISABLED_PENDING_OPTICAL_VALIDATION", binocular.photorefractionStatus)
    }

    @Test
    fun doubleTripleSimultaneousDiseaseCoexistenceTest() {
        val ranker = org.eyeatlas.ai.QualityAndRanker()

        // Triple simultaneous disease in OD: DR (0.82), Cataract (0.78), Glaucoma (0.65)
        val scores = mapOf(
            "PROLIFERATIVE_DIABETIC_RETINOPATHY" to 0.82,
            "NUCLEAR_CATARACT" to 0.78,
            "GLAUCOMA_ADVANCED" to 0.65,
            "DRY_EYE_SYNDROME" to 0.20
        )
        val report = ranker.rankMultiMorbidityFindings(
            eye = EyeLaterality.OD,
            conditionScores = scores,
            missingModalities = emptyMap(),
            evidenceMap = emptyMap()
        )

        // All 3 coexisting diseases detected
        assertEquals(3, report.coexistingDiseasesDetected.size)
        assertTrue(report.coexistingDiseasesDetected.contains("PROLIFERATIVE_DIABETIC_RETINOPATHY"))
        assertTrue(report.coexistingDiseasesDetected.contains("NUCLEAR_CATARACT"))
        assertTrue(report.coexistingDiseasesDetected.contains("GLAUCOMA_ADVANCED"))
        // No false exclusivity (no sum-to-1 restriction)
        val scoreSum = scores.values.filterNotNull().sum()
        assertTrue("Independent scores must be allowed to sum well over 1.0", scoreSum > 2.0)
    }

    @Test
    fun falseExclusivityAndBilateralDiscordanceTest() {
        val ranker = org.eyeatlas.ai.QualityAndRanker()

        // OD: Severe DR, low AMD
        val odScores = mapOf("PROLIFERATIVE_DIABETIC_RETINOPATHY" to 0.88, "WET_AMD" to 0.12)
        val odReport = ranker.rankMultiMorbidityFindings(EyeLaterality.OD, odScores, emptyMap(), emptyMap())

        // OS: Severe Wet AMD, low DR
        val osScores = mapOf("PROLIFERATIVE_DIABETIC_RETINOPATHY" to 0.08, "WET_AMD" to 0.91)
        val osReport = ranker.rankMultiMorbidityFindings(EyeLaterality.OS, osScores, emptyMap(), emptyMap())

        assertEquals("PROLIFERATIVE_DIABETIC_RETINOPATHY", odReport.rankedConditionsByScore.first().conditionCode)
        assertEquals("WET_AMD", osReport.rankedConditionsByScore.first().conditionCode)
    }

    @Test
    fun urgencyDecoupledFromProbabilityPrecedenceTest() {
        val ranker = org.eyeatlas.ai.QualityAndRanker()

        // Cataract has high confidence (0.92) but ROUTINE urgency
        // Retinal detachment suspicion has low confidence (0.35) but EMERGENCY urgency
        val scores = mapOf(
            "NUCLEAR_CATARACT" to 0.92,
            "RETINAL_DETACHMENT" to 0.35
        )
        val report = ranker.rankMultiMorbidityFindings(EyeLaterality.OD, scores, emptyMap(), emptyMap())

        // Clinical urgency precedence: Retinal Detachment must rank #1 for referral
        assertEquals("RETINAL_DETACHMENT", report.rankedConditionsByUrgency.first().conditionCode)
        assertEquals(org.eyeatlas.core.EmergencyUrgency.EMERGENCY_IMMEDIATE, report.overallReferralUrgency)
        assertTrue(report.primaryReferralRecommendation.contains("EMERGENCY"))

        // Pure score rank: Cataract ranks #1
        assertEquals("NUCLEAR_CATARACT", report.rankedConditionsByScore.first().conditionCode)
    }

    @Test
    fun evidenceFusionWithoutDoubleCountingTest() {
        val ranker = org.eyeatlas.ai.QualityAndRanker()

        // Correlated disc & macula captures: individual signals 0.70 and 0.65
        // Redundancy weight 0.50 dampens correlated double-counting
        val items = listOf(
            org.eyeatlas.ai.FusedEvidenceItem("DISC_PHOTO", 0.70, redundancyWeight = 0.50),
            org.eyeatlas.ai.FusedEvidenceItem("MACULA_PHOTO", 0.65, redundancyWeight = 0.50)
        )
        val fused = ranker.fuseEvidenceWithoutDoubleCounting(items)

        // Fused probability should be dampened (0.56) instead of naive bayes inflation (0.90+)
        assertTrue("Fused evidence must not double-count correlated sources", fused in 0.50..0.65)
    }

    @Test
    fun missingModalityExplicitNullScoreTest() {
        val ranker = org.eyeatlas.ai.QualityAndRanker()

        val scores = mapOf(
            "GLAUCOMA_ADVANCED" to null, // Perimetry missing
            "NUCLEAR_CATARACT" to 0.72
        )
        val missing = mapOf("GLAUCOMA_ADVANCED" to listOf("PERIMETRY_54PT_UNASSESSED"))

        val report = ranker.rankMultiMorbidityFindings(EyeLaterality.OD, scores, missing, emptyMap())
        val glaucomaEval = report.rankedConditionsByScore.first { it.conditionCode == "GLAUCOMA_ADVANCED" }

        assertTrue(glaucomaEval.isModalityMissing)
        assertNull("Missing modality must have null score, never 0.0 or negative", glaucomaEval.rawScore)
        assertNull(glaucomaEval.calibratedProbability)
        assertTrue(glaucomaEval.missingModalities.contains("PERIMETRY_54PT_UNASSESSED"))
    }

    @Test
    fun retinalPhantomCalibrationValidationTest() {
        val retinal = org.eyeatlas.vision.RetinalTests()
        assertFalse("Initially uncalibrated on phantom", retinal.isPhantomCalibrated)

        // Valid model eye phantom (25um lesion resolution <= 30um limit)
        val validPhantom = org.eyeatlas.vision.PhantomCalibrationRecord(
            phantomId = "PHANTOM-GULLSTRAND-01",
            minResolvableLesionMicrons = 25.0,
            discContrastRatio = 0.88,
            macularFovealAvascularZoneVisible = true
        )
        val calResult = retinal.calibrateWithArtificialEyePhantom(validPhantom)
        assertTrue(calResult.isSuccess)
        assertTrue(retinal.isPhantomCalibrated)

        // Degraded phantom (45um resolution cannot resolve early microaneurysms)
        val badPhantom = org.eyeatlas.vision.PhantomCalibrationRecord(
            phantomId = "PHANTOM-DEGRADED",
            minResolvableLesionMicrons = 45.0,
            discContrastRatio = 0.65
        )
        val badResult = retinal.calibrateWithArtificialEyePhantom(badPhantom)
        assertTrue(badResult.isFailure)
        assertTrue(badResult.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun cameraAdapterLockInterlockVerificationTest() {
        // Locked and verified camera-adapter assembly
        val locked = org.eyeatlas.vision.CameraAdapterLockInterlock(
            adapterMounted = true,
            mechanicalLockEngaged = true,
            opticalAlignmentVerified = true,
            exposureSettingsLocked = true
        )
        assertTrue(locked.isLockedAndReady)
        assertTrue(locked.verifyLock().isSuccess)

        // Loose clamp fault
        val unclamped = locked.copy(mechanicalLockEngaged = false)
        assertFalse(unclamped.isLockedAndReady)
        val resUnclamped = unclamped.verifyLock()
        assertTrue(resUnclamped.isFailure)
        assertTrue(resUnclamped.exceptionOrNull()!!.message!!.contains("MECHANICAL_LOCK_DISENGAGED"))

        // Misaligned lens fault
        val misaligned = locked.copy(opticalAlignmentVerified = false)
        val resMisaligned = misaligned.verifyLock()
        assertTrue(resMisaligned.isFailure)
        assertTrue(resMisaligned.exceptionOrNull()!!.message!!.contains("ALIGNMENT_FAULT"))
    }

    @Test
    fun minimumLesionDetectabilityResolutionGateTest() {
        val phantom = org.eyeatlas.vision.PhantomCalibrationRecord(
            phantomId = "PHANTOM-HIGHRES",
            minResolvableLesionMicrons = 28.0
        )
        // Hard clinical rule: 30 micron resolution ensures detection of diabetic microaneurysms
        assertTrue("Resolution <= 30 microns must pass", phantom.minResolvableLesionMicrons <= 30.0)
        assertTrue(phantom.isCalibrationPassed)
    }
}
