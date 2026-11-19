package org.eyeatlas.core

sealed class SessionEvent {
    object BeginIntake : SessionEvent()
    data class TriggerEmergencyBypass(val redFlag: String) : SessionEvent()
    object TriagePassed : SessionEvent()
    object CalibrationVerified : SessionEvent()
    object FunctionalCompleted : SessionEvent()
    object ImagingCompleted : SessionEvent()
    data class PriorityStop(val reason: String) : SessionEvent()
    object ResumeSession : SessionEvent()
    object FinalizeReview : SessionEvent()
}

class ClinicalStateMachine(private var currentState: SessionState = SessionState.INTAKE) {

    fun getState(): SessionState = currentState

    fun processEvent(event: SessionEvent): Result<SessionState> {
        val nextState = when (event) {
            is SessionEvent.TriggerEmergencyBypass -> {
                // Urgent signs immediately bypass routine testing
                SessionState.URGENT_BYPASS
            }
            is SessionEvent.PriorityStop -> {
                SessionState.STOPPED
            }
            is SessionEvent.BeginIntake -> {
                if (currentState == SessionState.STOPPED || currentState == SessionState.INTAKE) {
                    SessionState.INTAKE
                } else {
                    return Result.failure(IllegalStateException("Cannot reset to INTAKE from $currentState"))
                }
            }
            is SessionEvent.TriagePassed -> {
                if (currentState == SessionState.INTAKE) {
                    SessionState.DEVICE_CALIBRATION
                } else {
                    return Result.failure(IllegalStateException("Triage only valid from INTAKE, got $currentState"))
                }
            }
            is SessionEvent.CalibrationVerified -> {
                if (currentState == SessionState.DEVICE_CALIBRATION) {
                    SessionState.FUNCTIONAL_TESTS
                } else {
                    return Result.failure(IllegalStateException("Calibration must precede functional tests"))
                }
            }
            is SessionEvent.FunctionalCompleted -> {
                if (currentState == SessionState.FUNCTIONAL_TESTS) {
                    SessionState.IMAGING
                } else {
                    return Result.failure(IllegalStateException("Functional tests must complete before imaging"))
                }
            }
            is SessionEvent.ImagingCompleted -> {
                if (currentState == SessionState.IMAGING) {
                    SessionState.REVIEW_AND_REFERRAL
                } else {
                    return Result.failure(IllegalStateException("Imaging must complete before review"))
                }
            }
            is SessionEvent.ResumeSession -> {
                if (currentState == SessionState.STOPPED) {
                    SessionState.DEVICE_CALIBRATION // Safe re-calibration required after stop
                } else {
                    return Result.failure(IllegalStateException("Resume only valid when stopped"))
                }
            }
            is SessionEvent.FinalizeReview -> {
                if (currentState == SessionState.REVIEW_AND_REFERRAL || currentState == SessionState.URGENT_BYPASS) {
                    currentState // stays completed
                } else {
                    return Result.failure(IllegalStateException("Finalize only valid from review or bypass"))
                }
            }
        }
        currentState = nextState
        return Result.success(currentState)
    }
}
