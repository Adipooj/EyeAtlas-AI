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
}
