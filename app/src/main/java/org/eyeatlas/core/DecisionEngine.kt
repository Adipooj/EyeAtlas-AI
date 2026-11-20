package org.eyeatlas.core

import kotlin.math.round

data class AdaptiveTestPlan(
    val patientId: String,
    val hasDiabetes: Boolean,
    val symptoms: List<String>,
    val mandatoryDomains: List<TestDomain>,
    val optionalRecommendedDomains: List<TestDomain>,
    val estimatedDurationSeconds: Int,
    val maxRetakesPerDomain: Int = 2,
    val isEmergencyBypassActive: Boolean = false
)

enum class CausalFaultType {
    BLUR_FOCUS_DEFICIT,
    GLARE_EXPOSURE_SATURATION,
    CLOCK_SYNC_TRANSPORT_LOSS,
    PATIENT_BLINK_OR_INATTENTION,
    MEDIA_OPACITY_OBSCURATION
}

data class GuidedRecoveryAction(
    val faultType: CausalFaultType,
    val guidanceInstructions: String,
    val canRetake: Boolean,
    val currentRetakeCount: Int,
    val status: String // "RETAKE_RECOMMENDED", "MAX_RETAKES_EXCEEDED_PROCEED", "CLINICAL_REFERRAL_REQUIRED"
)

class DecisionEngine {

    companion object {
        val EMERGENCY_RED_FLAGS = listOf(
            "SUDDEN_VISION_LOSS",
            "CORNEAL_TRAUMA",
            "CHEMICAL_EXPOSURE",
            "PAINFUL_PHOTOPHOBIC_RED_EYE",
            "RETINAL_DETACHMENT_SIGNS"
        )
    }

    fun evaluate(laterality: EyeLaterality): List<String> = emptyList()

    /**
     * Checks intake symptoms against critical emergency red flags.
     * HARD INVARIANT: Critical red flags immediately bypass routine screening.
     */
    fun screenIntakeForEmergency(symptoms: List<String>): Referral? {
        val detected = symptoms.filter { sym ->
            EMERGENCY_RED_FLAGS.any { flag -> sym.equals(flag, ignoreCase = true) }
        }
        if (detected.isNotEmpty()) {
            return Referral(
                urgency = EmergencyUrgency.EMERGENCY_IMMEDIATE,
                reason = "Emergency clinical red-flag detected: ${detected.joinToString()}",
                recommendedTests = listOf("IMMEDIATE_OPHTHALMIC_EXAMINATION", "SLIT_LAMP_BIOMICROSCOPY", "DILATED_FUNDUSCOPY"),
                completionStatus = "BYPASSED_ROUTINE"
            )
        }
        return null
    }

    /**
     * Plans adaptive test battery based on patient symptoms, prior diabetes status,
     * and information gain.
     * HARD CLINICAL INVARIANTS:
     * 1. If hasDiabetes is true, RETINAL testing is MANDATORY even with 6/6 acuity.
     * 2. Emergency red flags trigger immediate bypass with 0 routine testing.
     * 3. Full protocol targets under 15 minutes (900 seconds).
     */
    fun planAdaptiveTestBattery(
        patientId: String,
        hasDiabetes: Boolean,
        symptoms: List<String>,
        measuredDistanceLogMar: Double = 0.0 // 0.0 logMAR = 6/6
    ): AdaptiveTestPlan {
        // 1. Symptom safety precedence check
        val emergency = screenIntakeForEmergency(symptoms)
        if (emergency != null) {
            return AdaptiveTestPlan(
                patientId = patientId,
                hasDiabetes = hasDiabetes,
                symptoms = symptoms,
                mandatoryDomains = emptyList(),
                optionalRecommendedDomains = emptyList(),
                estimatedDurationSeconds = 0,
                isEmergencyBypassActive = true
            )
        }

        val mandatory = mutableListOf<TestDomain>()
        val optional = mutableListOf<TestDomain>()

        // Acuity is baseline for all non-emergency screenings
        mandatory.add(TestDomain.ACUITY)

        // Diabetes Mandate: Retinal fundus imaging is strictly mandatory regardless of 6/6 vision
        if (hasDiabetes) {
            mandatory.add(TestDomain.RETINAL)
        } else {
            optional.add(TestDomain.RETINAL)
        }

        // Red eye / dry eye symptoms mandate anterior inspection
        val hasAnteriorSymptoms = symptoms.any {
            it.contains("RED", ignoreCase = true) || it.contains("DRY", ignoreCase = true) ||
            it.contains("PAIN", ignoreCase = true) || it.contains("DISCHARGE", ignoreCase = true)
        }
        if (hasAnteriorSymptoms) {
            mandatory.add(TestDomain.ANTERIOR)
        } else {
            optional.add(TestDomain.ANTERIOR)
        }

        // Visual field symptoms mandate perimetry
        val hasFieldSymptoms = symptoms.any {
            it.contains("FIELD", ignoreCase = true) || it.contains("TUNNEL", ignoreCase = true) ||
            it.contains("GLAUCOMA", ignoreCase = true) || it.contains("SCOTOMA", ignoreCase = true)
        }
        if (hasFieldSymptoms) {
            mandatory.add(TestDomain.VISUAL_FIELD)
        } else {
            optional.add(TestDomain.VISUAL_FIELD)
        }

        // Colour / binocular optional
        optional.add(TestDomain.COLOUR_CONTRAST)
        optional.add(TestDomain.BINOCULAR)

        // Calculate expected duration based on selected tests (Acuity: 120s, Anterior: 150s, Retinal: 200s, Field: 240s, Stereo: 90s)
        var totalSec = 0
        val allSelected = (mandatory + optional).distinct()
        for (dom in allSelected) {
            totalSec += when (dom) {
                TestDomain.ACUITY -> 120
                TestDomain.ANTERIOR -> 150
                TestDomain.RETINAL -> 200
                TestDomain.VISUAL_FIELD -> 240
                TestDomain.COLOUR_CONTRAST -> 90
                TestDomain.BINOCULAR -> 90
            }
        }

        return AdaptiveTestPlan(
            patientId = patientId,
            hasDiabetes = hasDiabetes,
            symptoms = symptoms,
            mandatoryDomains = mandatory.distinct(),
            optionalRecommendedDomains = optional.distinct(),
            estimatedDurationSeconds = totalSec,
            maxRetakesPerDomain = 2,
            isEmergencyBypassActive = false
        )
    }

    /**
     * Causal fault recovery engine: provides actionable remediation instructions
     * and enforces a finite retake budget (max 2 retakes) to prevent infinite loops and fatigue.
     */
    fun guideFaultRecovery(
        fault: CausalFaultType,
        currentRetakeCount: Int,
        maxAllowedRetakes: Int = 2
    ): GuidedRecoveryAction {
        val guidance = when (fault) {
            CausalFaultType.BLUR_FOCUS_DEFICIT ->
                "Focus failure: Clean optics with microfiber wipe. Verify patient IPD alignment (48-74mm) and adjust phone tray focal distance stop."
            CausalFaultType.GLARE_EXPOSURE_SATURATION ->
                "Glare saturation: Shield ambient overhead light. Verify opaque facial gasket seal against subject's brow."
            CausalFaultType.CLOCK_SYNC_TRANSPORT_LOSS ->
                "Clock sync jitter: Ensure response handset is within 2 meters. Re-authenticate local Wi-Fi ping-pong exchange."
            CausalFaultType.PATIENT_BLINK_OR_INATTENTION ->
                "Blink interference: Instruct patient to blink fully, pause, and keep eyes wide open during 10-second Placido video capture."
            CausalFaultType.MEDIA_OPACITY_OBSCURATION ->
                "Media opacity (dense cataract / vitreous haze): Retinal view obscured by dense anterior opacity. Repeating capture will not succeed. Refer for slit-lamp cataract assessment."
        }

        // Media opacity obscuration cannot be fixed by repeating photos
        if (fault == CausalFaultType.MEDIA_OPACITY_OBSCURATION) {
            return GuidedRecoveryAction(
                faultType = fault,
                guidanceInstructions = guidance,
                canRetake = false,
                currentRetakeCount = currentRetakeCount,
                status = "CLINICAL_REFERRAL_REQUIRED"
            )
        }

        return if (currentRetakeCount < maxAllowedRetakes) {
            GuidedRecoveryAction(
                faultType = fault,
                guidanceInstructions = guidance,
                canRetake = true,
                currentRetakeCount = currentRetakeCount + 1,
                status = "RETAKE_RECOMMENDED"
            )
        } else {
            GuidedRecoveryAction(
                faultType = fault,
                guidanceInstructions = "Maximum retake budget reached ($maxAllowedRetakes retakes). Mark task UNGRADABLE to prevent patient fatigue and retake bias. Proceeding to next modality.",
                canRetake = false,
                currentRetakeCount = currentRetakeCount,
                status = "MAX_RETAKES_EXCEEDED_PROCEED"
            )
        }
    }

    /**
     * Evaluates independent per-eye findings.
     * Enforces that multi-disease conditions can coexist independently,
     * and missing tests are classified as unknown, not negative.
     */
    fun evaluateEyeFindings(
        eye: EyeLaterality,
        measurements: List<Measurement>,
        availableTests: Set<TestDomain>
    ): List<ConditionInference> {
        val inferences = mutableListOf<ConditionInference>()

        // Check for Glaucoma evidence
        val visualFieldAvailable = availableTests.contains(TestDomain.VISUAL_FIELD)
        val fieldAbnormal = measurements.any { it.method == "PERIMETRY_54PT" && it.value > 0.0 && it.validity }
        
        inferences.add(
            ConditionInference(
                eye = eye,
                diseaseCode = "GLAUCOMA_SUSPICION",
                probabilityOrScore = if (visualFieldAvailable && fieldAbnormal) 0.75 else null,
                calibrationId = "CAL-P01-BASELINE",
                evidenceFor = if (fieldAbnormal) listOf("REPRODUCIBLE_FIELD_DEFECT") else emptyList(),
                evidenceAgainst = emptyList(),
                unknownEvidence = if (!visualFieldAvailable) listOf("PERIMETRY_54PT_UNASSESSED") else emptyList(),
                recommendedTest = if (!visualFieldAvailable) "PERIMETRY_54PT" else null
            )
        )

        // Check for Cataract evidence independently
        val anteriorAvailable = availableTests.contains(TestDomain.ANTERIOR)
        val mediaOpacity = measurements.any { it.method == "MEDIA_OPACITY" && it.value > 0.5 && it.validity }

        inferences.add(
            ConditionInference(
                eye = eye,
                diseaseCode = "CATARACT_SUSPICION",
                probabilityOrScore = if (anteriorAvailable && mediaOpacity) 0.80 else null,
                calibrationId = "CAL-P01-BASELINE",
                evidenceFor = if (mediaOpacity) listOf("MEDIA_OPACITY_DETECTED") else emptyList(),
                evidenceAgainst = emptyList(),
                unknownEvidence = if (!anteriorAvailable) listOf("ANTERIOR_MEDIA_UNASSESSED") else emptyList(),
                recommendedTest = if (!anteriorAvailable) "SLIT_LAMP_IMAGING" else null
            )
        )

        return inferences
    }
}
