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
}
