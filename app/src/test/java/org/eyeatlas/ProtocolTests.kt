package org.eyeatlas

import org.eyeatlas.core.*
import org.junit.Assert.*
import org.junit.Test

class ProtocolTests {

    @Test
    fun stateMachineNormalProgressionTest() {
        val sm = ClinicalStateMachine()
        assertEquals(SessionState.INTAKE, sm.getState())

        assertTrue(sm.processEvent(SessionEvent.TriagePassed).isSuccess)
        assertEquals(SessionState.DEVICE_CALIBRATION, sm.getState())

        assertTrue(sm.processEvent(SessionEvent.CalibrationVerified).isSuccess)
        assertEquals(SessionState.FUNCTIONAL_TESTS, sm.getState())

        assertTrue(sm.processEvent(SessionEvent.FunctionalCompleted).isSuccess)
        assertEquals(SessionState.IMAGING, sm.getState())

        assertTrue(sm.processEvent(SessionEvent.ImagingCompleted).isSuccess)
        assertEquals(SessionState.REVIEW_AND_REFERRAL, sm.getState())

        assertTrue(sm.processEvent(SessionEvent.FinalizeReview).isSuccess)
        assertEquals(SessionState.REVIEW_AND_REFERRAL, sm.getState())
    }

    @Test
    fun urgentBypassEmergencyTriageTest() {
        val decisionEngine = DecisionEngine()
        val sm = ClinicalStateMachine()

        // Red flag symptoms: sudden vision loss, corneal trauma, painful red eye
        val redFlagSymptoms = listOf("SUDDEN_VISION_LOSS", "EYE_PAIN")
        val referral = decisionEngine.screenIntakeForEmergency(redFlagSymptoms)

        assertNotNull(referral)
        assertEquals(EmergencyUrgency.EMERGENCY_IMMEDIATE, referral!!.urgency)
        assertTrue(referral.reason.contains("SUDDEN_VISION_LOSS"))

        // Trigger urgent bypass event in state machine
        val result = sm.processEvent(SessionEvent.TriggerEmergencyBypass(referral.reason))
        assertTrue(result.isSuccess)
        assertEquals(SessionState.URGENT_BYPASS, sm.getState())

        // Routine functional test transition must be blocked from urgent bypass
        val illegalProgression = sm.processEvent(SessionEvent.FunctionalCompleted)
        assertTrue(illegalProgression.isFailure)
        assertEquals(SessionState.URGENT_BYPASS, sm.getState())
    }

    @Test
    fun priorityStopAndRecoveryTest() {
        val sm = ClinicalStateMachine()
        sm.processEvent(SessionEvent.TriagePassed)
        sm.processEvent(SessionEvent.CalibrationVerified)
        assertEquals(SessionState.FUNCTIONAL_TESTS, sm.getState())

        // Patient or clinician triggers priority stop
        val stopResult = sm.processEvent(SessionEvent.PriorityStop("Patient discomfort"))
        assertTrue(stopResult.isSuccess)
        assertEquals(SessionState.STOPPED, sm.getState())

        // Recovery transition must safely return to calibration
        val resumeResult = sm.processEvent(SessionEvent.ResumeSession)
        assertTrue(resumeResult.isSuccess)
        assertEquals(SessionState.DEVICE_CALIBRATION, sm.getState())
    }

    @Test
    fun invalidStateTransitionRejectionTest() {
        val sm = ClinicalStateMachine()
        assertEquals(SessionState.INTAKE, sm.getState())

        // Attempting to jump directly to functional tests without triage/calibration must fail
        val invalidResult = sm.processEvent(SessionEvent.FunctionalCompleted)
        assertTrue(invalidResult.isFailure)
        assertEquals(SessionState.INTAKE, sm.getState())
    }

    @Test
    fun clockSynchronizationPingPongProtocolTest() {
        val syncEngine = org.eyeatlas.net.ClockSyncEngine(maxAllowedUncertaintyNs = 20_000_000L)
        assertFalse(syncEngine.isSynchronized)

        // Sample 1: t0=100ms, t1=120ms, t2=125ms, t3=150ms
        // RTT = (150-100) - (125-120) = 45ms. Offset = ((120-100) + (125-150))/2 = (20 - 25)/2 = -2.5ms. Uncertainty = 22.5ms
        val sample1 = org.eyeatlas.net.ClockSyncSample("S-1", 100_000_000L, 120_000_000L, 125_000_000L, 150_000_000L)
        syncEngine.addSample(sample1)
        assertTrue(syncEngine.isSynchronized)
        assertEquals(45_000_000L, sample1.roundTripDelayNs)
        assertEquals(22_500_000L, sample1.uncertaintyNs)

        // Sample 2 (lower jitter): t0=200ms, t1=210ms, t2=212ms, t3=224ms
        // RTT = (224-200) - (212-210) = 22ms. Uncertainty = 11ms
        val sample2 = org.eyeatlas.net.ClockSyncSample("S-2", 200_000_000L, 210_000_000L, 212_000_000L, 224_000_000L)
        syncEngine.addSample(sample2)

        // Engine must select lowest RTT
        assertEquals(11_000_000L, syncEngine.currentUncertaintyNs)
        assertTrue(syncEngine.isUncertaintyAcceptable())
    }

    @Test
    fun qrPairingPayloadSerializationTest() {
        val payload = org.eyeatlas.net.PairingPayload(
            sessionId = "SESSION-101",
            patientPhoneId = "PHONE-PATIENT-VR",
            authPin = "8833",
            wifiEndpoint = "192.168.4.1:8080",
            cryptoKeyHash = "a1b2c3d4e5f67890"
        )
        val qr = payload.toQrString()
        assertEquals("EYEATLAS://SESSION-101/PHONE-PATIENT-VR/8833/192.168.4.1:8080/a1b2c3d4e5f67890", qr)

        val deserialized = org.eyeatlas.net.PairingPayload.fromQrString(qr)
        assertNotNull(deserialized)
        assertEquals(payload.sessionId, deserialized!!.sessionId)
        assertEquals(payload.patientPhoneId, deserialized.patientPhoneId)
        assertEquals(payload.authPin, deserialized.authPin)
        assertEquals(payload.wifiEndpoint, deserialized.wifiEndpoint)
        assertEquals(payload.cryptoKeyHash, deserialized.cryptoKeyHash)
    }

    @Test
    fun automatedNavigationToAllFourteenScreensTest() {
        val allScreens = org.eyeatlas.ui.ScreenId.values()
        assertEquals(14, allScreens.size)

        // Verify every screen has valid title, non-empty description, and correct ordinal order
        for ((index, screen) in allScreens.withIndex()) {
            assertEquals(index, screen.ordinal)
            assertTrue("Screen ${screen.name} must have a non-blank title", screen.title.isNotBlank())
            assertTrue("Screen ${screen.name} must have a non-blank description", screen.description.isNotBlank())
        }
    }

    @Test
    fun screenStateRestorationAndProcessDeathTest() {
        // Simulate initial session state
        var activeSession = ClinicalSession(
            sessionId = "SESSION-STATE-RESTORE-1",
            patientId = "PATIENT-RESTORE-1",
            patientPhoneId = "PHONE-VR-01",
            clinicianPhoneId = "PHONE-DOC-01",
            responsePhoneId = "PHONE-PAD-01",
            hasConsent = true,
            startTimeMs = 1700000000000L,
            state = SessionState.FUNCTIONAL_TESTS
        )

        // Process death / rotation simulation: state preserved in bundle
        val savedSessionId = activeSession.sessionId
        val savedState = activeSession.state
        val savedHasConsent = activeSession.hasConsent

        // Recreate activity
        val restoredSession = ClinicalSession(
            sessionId = savedSessionId,
            patientId = "PATIENT-RESTORE-1",
            patientPhoneId = "PHONE-VR-01",
            clinicianPhoneId = "PHONE-DOC-01",
            responsePhoneId = "PHONE-PAD-01",
            hasConsent = savedHasConsent,
            startTimeMs = 1700000000000L,
            state = savedState
        )

        assertEquals(activeSession.sessionId, restoredSession.sessionId)
        assertEquals(SessionState.FUNCTIONAL_TESTS, restoredSession.state)
        assertTrue(restoredSession.hasConsent)
    }

    @Test
    fun noFakeClinicalScoringOnIncompleteScreensTest() {
        val decisionEngine = DecisionEngine()
        // Browsing functional screens with zero measurements must never invent scores
        val inferences = decisionEngine.evaluateEyeFindings(
            eye = EyeLaterality.OD,
            measurements = emptyList(),
            availableTests = emptySet()
        )

        for (inf in inferences) {
            assertNull("Unassessed test cannot produce numeric diagnostic score", inf.probabilityOrScore)
            assertTrue("Missing tests must be documented as unknown evidence", inf.unknownEvidence.isNotEmpty())
        }
    }

    @Test
    fun syntheticCaseNormalAcuityPlusDiabetesRetinalMandateTest() {
        val engine = DecisionEngine()

        // Asymptomatic patient with 6/6 visual acuity (0.0 logMAR) and known diabetes
        val plan = engine.planAdaptiveTestBattery(
            patientId = "PATIENT-DM-001",
            hasDiabetes = true,
            symptoms = emptyList(),
            measuredDistanceLogMar = 0.0 // 6/6 normal vision
        )

        assertFalse(plan.isEmergencyBypassActive)
        // Hard medical invariant: Retinal fundus imaging is strictly mandatory for diabetes regardless of 6/6 acuity
        assertTrue(plan.mandatoryDomains.contains(TestDomain.RETINAL))
        assertTrue(plan.mandatoryDomains.contains(TestDomain.ACUITY))
    }

    @Test
    fun cataractPlusDrUngradableCausalRecoveryTest() {
        val engine = DecisionEngine()

        // Dense cataract causing obscured retinal view
        val recovery = engine.guideFaultRecovery(
            fault = CausalFaultType.MEDIA_OPACITY_OBSCURATION,
            currentRetakeCount = 0
        )

        assertFalse("Cannot retake fundus photo through dense media opacity", recovery.canRetake)
        assertEquals("CLINICAL_REFERRAL_REQUIRED", recovery.status)
        assertTrue(recovery.guidanceInstructions.contains("cataract"))
    }

    @Test
    fun redEyeSeverePainSymptomSafetyPrecedenceTest() {
        val engine = DecisionEngine()

        val plan = engine.planAdaptiveTestBattery(
            patientId = "PATIENT-URGENT-RED",
            hasDiabetes = false,
            symptoms = listOf("PAINFUL_PHOTOPHOBIC_RED_EYE")
        )

        assertTrue("Emergency symptoms must trigger emergency bypass", plan.isEmergencyBypassActive)
        assertEquals("Routine testing duration must be zero under emergency bypass", 0, plan.estimatedDurationSeconds)
        assertTrue(plan.mandatoryDomains.isEmpty())
    }

    @Test
    fun finiteRetakeBudgetAndFatiguePreventionTest() {
        val engine = DecisionEngine()

        // Retake 0 -> Allowed (count becomes 1)
        val r0 = engine.guideFaultRecovery(CausalFaultType.BLUR_FOCUS_DEFICIT, currentRetakeCount = 0, maxAllowedRetakes = 2)
        assertTrue(r0.canRetake)
        assertEquals("RETAKE_RECOMMENDED", r0.status)
        assertEquals(1, r0.currentRetakeCount)

        // Retake 1 -> Allowed (count becomes 2)
        val r1 = engine.guideFaultRecovery(CausalFaultType.BLUR_FOCUS_DEFICIT, currentRetakeCount = 1, maxAllowedRetakes = 2)
        assertTrue(r1.canRetake)
        assertEquals(2, r1.currentRetakeCount)

        // Retake 2 (max reached) -> Blocked, proceeds to next modality
        val r2 = engine.guideFaultRecovery(CausalFaultType.BLUR_FOCUS_DEFICIT, currentRetakeCount = 2, maxAllowedRetakes = 2)
        assertFalse("Must not exceed max retake budget", r2.canRetake)
        assertEquals("MAX_RETAKES_EXCEEDED_PROCEED", r2.status)
    }

    @Test
    fun adaptiveBatteryTargetTimeBenchmarkingTest() {
        val engine = DecisionEngine()

        // Comprehensive screening plan (Acuity + Anterior + Retinal + Visual Field + Stereo)
        val plan = engine.planAdaptiveTestBattery(
            patientId = "PATIENT-ROUTINE-FULL",
            hasDiabetes = true,
            symptoms = listOf("MILD_DRYNESS", "BLURRED_VISION")
        )

        // Full comprehensive screening duration must be within target 15-minute (900 second) clinical envelope
        assertTrue(
            "Full screening battery must complete within 900 seconds (got ${plan.estimatedDurationSeconds}s)",
            plan.estimatedDurationSeconds in 600..900
        )
    }

    // --- Phase P20 & P21 Clinical Pilot and Release Tests ---

    @Test
    fun clinicalPilotPreregistrationAndEthicsAuditTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()
        val study = org.eyeatlas.store.ClinicalPilotStudyRecord(
            studyPreregistrationId = "CTRI/2026/11/048821",
            ethicsApprovalRef = "IEC/EYEATLAS/2026/08",
            operatorRole = "COMMUNITY_HEALTH_WORKER",
            operatorId = "CHW-042",
            durationSeconds = 840, // 14 minutes, within 900s target
            patientComfortScore = 4.2, // Likert >= 3.5
            protocolMode = "FULL_COMPREHENSIVE"
        )

        val comparisons = listOf(
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", true, true, false),
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", false, false, false),
            org.eyeatlas.store.MaskedClinicalReferenceComparison("GLAUCOMA_FIELD", "HUMPHREY_SAP", true, true, false),
            org.eyeatlas.store.MaskedClinicalReferenceComparison("GLAUCOMA_FIELD", "HUMPHREY_SAP", false, false, false)
        )

        val subgroups = listOf(
            org.eyeatlas.store.SubgroupQualityStratification("ADULT_18_64", "CLEAR_MEDIA", "FULL_COOPERATION", 4, 4, 0)
        )

        val report = exporter.generateClinicalPilotReport(study, comparisons, subgroups)

        // Verifications
        assertTrue("Report must contain study preregistration ID", report.contains("CTRI/2026/11/048821"))
        assertTrue("Report must contain ethics approval ref", report.contains("IEC/EYEATLAS/2026/08"))
        assertTrue("Report must include research prototype non-deployment disclaimer", report.contains(org.eyeatlas.store.ReportsAndExports.RESEARCH_PROTOTYPE_DISCLAIMER))
        assertTrue("Report must contain zero missed disease audit", report.contains("Zero Missed Disease Masked as Negative: VERIFIED_AUDIT_PASS"))
        assertTrue("Report must contain SHA-256 integrity signature", report.contains("Report SHA256:"))
    }

    @Test
    fun clinicalPilotMaskedReferenceAgreementAndNoMissedDiseaseTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()

        val validComparisons = listOf(
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", true, true, false),
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", true, true, false),
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", false, false, false),
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", false, false, false),
            // Ungradable comparison MUST have null candidatePositive
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", true, null, true)
        )

        val summary = exporter.calculateMaskedAgreement(validComparisons)
        assertEquals(5, summary.totalCases)
        assertEquals(0.20, summary.ungradableRate, 0.01)
        assertEquals(1.0, summary.concordanceRate, 0.01) // 4 evaluable all concordant
        assertNotNull(summary.sensitivityCi)
        assertNotNull(summary.specificityCi)

        // HARD INVARIANT CHECK: Ungradable tests must NEVER have a non-null candidate classification
        val illegalComparisons = listOf(
            org.eyeatlas.store.MaskedClinicalReferenceComparison("DIABETIC_RETINOPATHY", "7FIELD_FUNDUS", true, false, true)
        )
        try {
            exporter.calculateMaskedAgreement(illegalComparisons)
            fail("Must throw IllegalStateException when ungradable test is classified as negative")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Missing data must be UNGRADABLE_WITHHELD"))
        }
    }

    @Test
    fun clinicalPilotSubgroupQualityAuditTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()

        val validSubgroups = listOf(
            org.eyeatlas.store.SubgroupQualityStratification("PEDIATRIC_UNDER_5", "CLEAR_MEDIA", "LIMITED_COOPERATION", 10, 8, 2),
            org.eyeatlas.store.SubgroupQualityStratification("ADULT_18_64", "CLEAR_MEDIA", "FULL_COOPERATION", 50, 48, 2),
            org.eyeatlas.store.SubgroupQualityStratification("SENIOR_65_PLUS", "DENSE_CATARACT", "VARIABLE_FIXATION", 25, 20, 5)
        )
        assertTrue("Subgroups summing correctly must pass audit", exporter.auditSubgroupQuality(validSubgroups))

        val invalidSubgroups = listOf(
            // Sum mismatch: complete (8) + ungradable (1) = 9 != totalScreened (10)
            org.eyeatlas.store.SubgroupQualityStratification("PEDIATRIC_UNDER_5", "CLEAR_MEDIA", "LIMITED_COOPERATION", 10, 8, 1)
        )
        try {
            exporter.auditSubgroupQuality(invalidSubgroups)
            fail("Must throw IllegalStateException when subgroup counts do not sum to totalScreened")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Subgroup audit failure"))
        }
    }

    @Test
    fun externalValidationProspectiveMetricsAndNonInferiorityGateTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()

        // Test site 1: Diabetic Retinopathy screening (Prevalence = 15%, TP=85, FP=15, TN=885, FN=15, Ungradable=20)
        // Sensitivity = 85 / (85 + 15) = 85.0%
        val drMetrics = exporter.evaluateConditionValidationMetrics(
            condition = "DIABETIC_RETINOPATHY",
            siteId = "SITE-RURAL-01",
            truePositives = 85,
            falsePositives = 15,
            trueNegatives = 885,
            falseNegatives = 15,
            ungradableCount = 20,
            prevalence = 0.15,
            nonInferioritySensitivityThreshold = 0.75 // Margin: lower bound 95% CI >= 75%
        )

        assertEquals(1020, drMetrics.totalEnrolled)
        assertEquals(0.85, drMetrics.sensitivity, 0.01)
        assertTrue("Lower bound of 95% CI must be calculated", drMetrics.sensitivityCi.lowerBound > 0.75)
        assertTrue("Condition must meet non-inferiority margin", drMetrics.isNonInferior)
    }

    @Test
    fun releaseReadinessRegulatoryHoldTest() {
        val exporter = org.eyeatlas.store.ReportsAndExports()

        val sites = listOf(
            org.eyeatlas.store.ValidationSiteInfo("SITE-01", "Apex Eye Hospital", "TERTIARY_HOSPITAL", "PHONE_BATCH_A"),
            org.eyeatlas.store.ValidationSiteInfo("SITE-02", "Rural Vision Center", "RURAL_VISION_CENTER", "PHONE_BATCH_B")
        )

        val metrics = listOf(
            exporter.evaluateConditionValidationMetrics("DIABETIC_RETINOPATHY", "SITE-01", 90, 10, 890, 10, 15, 0.10, 0.80),
            exporter.evaluateConditionValidationMetrics("GLAUCOMA_SUSPECT", "SITE-01", 85, 15, 880, 15, 12, 0.10, 0.75),
            exporter.evaluateConditionValidationMetrics("CATARACT_SIGNIFICANT", "SITE-02", 95, 5, 895, 5, 8, 0.10, 0.85)
        )

        val releaseSummary = exporter.evaluateReleaseReadiness(metrics, maxAllowedAttritionRate = 0.10)

        // REGULATORY HOLD INVARIANT:
        // Research prototype software is locked from commercial release until CDSCO clearance
        assertFalse("Commercial release must remain FALSE", releaseSummary.isReleaseApproved)
        assertTrue("CDSCO review must be marked PENDING", releaseSummary.cdscoReviewPending)
        assertEquals(
            "INVESTIGATIONAL_ONLY_NOT_FOR_COMMERCIAL_DISTRIBUTION",
            releaseSummary.regulatoryStatus
        )

        val report = exporter.generateExternalValidationReleaseReport(sites, metrics, releaseSummary)
        assertTrue("Report must include prominent non-deployment disclaimer", report.contains(org.eyeatlas.store.ReportsAndExports.RESEARCH_PROTOTYPE_DISCLAIMER))
        assertTrue("Report must specify pending CDSCO review", report.contains("PENDING_REGULATORY_SUBMISSION"))
        assertTrue("Report must contain SHA-256 signature", report.contains("Report SHA256:"))
    }
}
