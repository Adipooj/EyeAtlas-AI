package org.eyeatlas.core

import java.security.MessageDigest

enum class EyeLaterality {
    OD, // Right eye (Oculus Dexter)
    OS, // Left eye (Oculus Sinister)
    OU  // Both eyes (Oculus Uterque / Binocular)
}

enum class TestDomain {
    ACUITY,
    COLOUR_CONTRAST,
    VISUAL_FIELD,
    ANTERIOR,
    RETINAL,
    BINOCULAR
}

enum class EmergencyUrgency {
    ROUTINE,
    PRIORITY,
    EMERGENCY_IMMEDIATE
}

enum class SessionState {
    INTAKE,
    URGENT_BYPASS,
    DEVICE_CALIBRATION,
    FUNCTIONAL_TESTS,
    IMAGING,
    REVIEW_AND_REFERRAL,
    STOPPED
}

data class PatientRecord(
    val patientId: String,
    val ageBand: String,
    val hasDiabetes: Boolean,
    val symptoms: List<String>,
    val currentCorrection: String,
    val comorbidityFlags: List<String> = emptyList(),
    val isEmergencyFlagged: Boolean = false,
    val emergencyReason: String? = null
) {
    fun anonymizedHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(patientId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

data class ClinicalSession(
    val sessionId: String,
    val patientId: String,
    val patientPhoneId: String,
    val clinicianPhoneId: String,
    val responsePhoneId: String,
    val responseRole: String = "RESPONSE_PAD",
    val mode: String = "RESEARCH_BASELINE",
    val hasConsent: Boolean,
    val startTimeMs: Long,
    val state: SessionState
)

data class EyeRecord(
    val laterality: EyeLaterality,
    val correctionStatus: String,
    val opticalProfile: String,
    val fixationStatus: String
)

data class StimulusTrial(
    val trialId: String,
    val sessionId: String,
    val eye: EyeLaterality,
    val testCode: String,
    val targetAngleDeg: Double,
    val luminanceCdM2: Double,
    val opticalIndex: Double,
    val monotonicPresentedNs: Long,
    val responsePhoneMonotonicNs: Long? = null,
    val correctedResponseNs: Long? = null,
    val clockOffsetNs: Long = 0L,
    val clockUncertaintyNs: Long = 0L,
    val expectedAnswer: String,
    val observedAnswer: String? = null,
    val framesRendered: Int,
    val calibrationHash: String,
    val responseEventId: String? = null,
    val validityFlags: List<String> = emptyList()
) {
    val isUngradableOrInvalid: Boolean
        get() = validityFlags.any { it.startsWith("INVALID") || it.startsWith("DISCONNECT") }
}

data class ResponseEvent(
    val eventId: String,
    val sessionId: String,
    val trialId: String? = null,
    val responsePhoneId: String,
    val monotonicEventNs: Long,
    val inputType: String, // "TOUCH_TAP", "LONG_PRESS_STOP", "NONE"
    val pointerCount: Int = 1,
    val inputQuality: String = "VALID",
    val clockSyncId: String = "DEFAULT_SYNC",
    val receivedByPatientNs: Long = 0L,
    val durableStatus: String = "COMMITTED"
)

data class ImageCapture(
    val imageId: String,
    val sessionId: String,
    val eye: EyeLaterality,
    val mode: String, // "ANTERIOR", "TEAR_FILM", "RETINAL_FUNDUS"
    val cameraId: String,
    val rawUri: String,
    val processingHash: String,
    val focusMetric: Double,
    val exposureTimeMs: Double,
    val whiteBalance: String,
    val visibleStructures: List<String>,
    val fieldCoverage: String,
    val qualityScore: Double
)

data class Measurement(
    val measurementId: String,
    val provenanceIds: List<String>,
    val eye: EyeLaterality,
    val method: String,
    val value: Double,
    val units: String,
    val confidenceLower: Double? = null,
    val confidenceUpper: Double? = null,
    val validity: Boolean,
    val limits: List<String> = emptyList()
)

data class Finding(
    val eye: EyeLaterality,
    val broadDomain: TestDomain,
    val finding: String,
    val direction: String = "DEFAULT",
    val severity: String = "MILD",
    val provenanceIds: List<String> = emptyList(),
    val validity: Boolean = true
)

data class ConditionInference(
    val eye: EyeLaterality,
    val diseaseCode: String,
    val probabilityOrScore: Double?,
    val calibrationId: String,
    val evidenceFor: List<String> = emptyList(),
    val evidenceAgainst: List<String> = emptyList(),
    val unknownEvidence: List<String> = emptyList(),
    val recommendedTest: String? = null
) {
    init {
        // Enforce no-pretend-diagnosis invariant:
        // Numeric probabilities require explicit calibration ID
        if (probabilityOrScore != null) {
            require(calibrationId.isNotBlank() && calibrationId != "NONE") {
                "Numerical scores require validated calibration ID"
            }
        }
    }
}

data class Referral(
    val urgency: EmergencyUrgency,
    val reason: String,
    val recommendedTests: List<String>,
    val completionStatus: String = "PENDING"
)

data class AuditEvent(
    val eventId: String,
    val actor: String,
    val timestampMs: Long,
    val deviceId: String,
    val action: String,
    val beforeHash: String,
    val afterHash: String,
    val reason: String
)
