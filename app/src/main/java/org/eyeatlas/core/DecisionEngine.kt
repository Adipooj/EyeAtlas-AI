package org.eyeatlas.core

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
