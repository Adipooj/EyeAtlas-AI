package org.eyeatlas.vision

import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.Finding
import org.eyeatlas.core.Measurement
import org.eyeatlas.core.TestDomain
import org.eyeatlas.sensors.CameraPipeline
import org.eyeatlas.sensors.ImageQualityAssessment
import org.eyeatlas.sensors.LockedCameraSettings

/**
 * Anterior segment inspection, multi-gaze image capture, tear film NIBUT,
 * pterygium, cataract opacity, and infection triage.
 */
enum class AnteriorGazeDirection {
    PRIMARY,
    UPGAZE,
    NASAL,
    TEMPORAL
}

data class AnteriorImageCapture(
    val captureId: String,
    val sessionId: String,
    val eye: EyeLaterality,
    val gazeDirection: AnteriorGazeDirection,
    val rawImageHash: String,
    val cameraSettings: LockedCameraSettings,
    val quality: ImageQualityAssessment,
    val scaleMarkerDetected: Boolean = true
) {
    val isGradable: Boolean
        get() = quality.isPassed && scaleMarkerDetected
}

data class MultiGazeSeries(
    val sessionId: String,
    val eye: EyeLaterality,
    val captures: Map<AnteriorGazeDirection, AnteriorImageCapture>
) {
    val isComplete: Boolean
        get() = AnteriorGazeDirection.values().all { captures.containsKey(it) }

    val allGradable: Boolean
        get() = isComplete && captures.values.all { it.isGradable }

    fun getUngradableReasons(): List<String> {
        val reasons = mutableListOf<String>()
        for (gaze in AnteriorGazeDirection.values()) {
            val cap = captures[gaze]
            if (cap == null) {
                reasons.add("MISSING_GAZE: ${gaze.name}")
            } else if (!cap.isGradable) {
                reasons.add("UNGRADABLE_${gaze.name}: ${cap.quality.failureReasons.joinToString("; ")}")
            }
        }
        return reasons
    }
}

enum class TearFilmStability {
    NORMAL_STABLE,      // NIBUT >= 10.0 s
    MARGINAL_UNSTABLE,  // 5.0 s <= NIBUT < 10.0 s
    SEVERE_DEFICIENCY,  // NIBUT < 5.0 s
    RIGHT_CENSORED_EARLY_BLINK // Blinking before break occurred, break > blink time
}

data class NibutVideoFrame(
    val timestampSeconds: Double,
    val ringContinuityRatio: Double, // 1.0 = perfect unbroken ring, 0.0 = completely broken
    val blinkDetected: Boolean = false
)

data class NibutMeasurement(
    val eye: EyeLaterality,
    val firstBreakSeconds: Double?,
    val isRightCensored: Boolean,
    val blinkIntervalSeconds: Double,
    val stability: TearFilmStability,
    val measurement: Measurement
)

enum class PterygiumStage {
    NONE,
    GRADE_1_EARLY_LIMBAL,      // < 2.0 mm onto cornea
    GRADE_2_INTERMEDIATE,      // 2.0 - 4.0 mm onto cornea
    GRADE_3_ADVANCED_PUPILLARY // > 4.0 mm or encroaching pupil margin
}

data class PterygiumAssessment(
    val eye: EyeLaterality,
    val stage: PterygiumStage,
    val encroachmentMm: Double,
    val location: String, // "NASAL", "TEMPORAL", "NONE"
    val finding: Finding?
)

enum class CataractDensityGrade {
    CLEAR_NO_OPACITY,
    MILD_SUSPECTED,
    MODERATE,
    DENSE_MATURE
}

data class CataractOpacityAssessment(
    val eye: EyeLaterality,
    val opacityScore: Double, // 0.0 clear to 1.0 opaque
    val grade: CataractDensityGrade,
    val retroilluminationDefect: Boolean,
    val finding: Finding?
)

data class OpenSetClassificationResult(
    val recognizedClass: String,
    val inDistributionConfidence: Double,
    val isOpenSetUnknown: Boolean,
    val recommendation: String
)

enum class InfectionTriageUrgency {
    ROUTINE,
    SEMICRITICAL,
    EMERGENCY_SAME_DAY
}

data class AnteriorSymptomSigns(
    val hasRedEye: Boolean,
    val hasSeverePain: Boolean = false,
    val hasPhotophobia: Boolean = false,
    val hasVisionDrop: Boolean = false,
    val ciliaryFlushPresent: Boolean = false,
    val cornealOpacityOrInfiltrate: Boolean = false,
    val purulentDischargePresent: Boolean = false,
    val foreignBodySensationOnly: Boolean = false
)

data class InfectionTriageResult(
    val eye: EyeLaterality,
    val urgency: InfectionTriageUrgency,
    val provisionalFindings: List<Finding>,
    val requiresSameDayOphthalmologist: Boolean,
    val differentialDiagnoses: List<String>,
    val triageNote: String
)

class AnteriorTests {

    /**
     * Inspects a multi-gaze series and verifies that laterality and quality criteria are met.
     */
    fun validateMultiGazeSeries(series: MultiGazeSeries): Result<Boolean> {
        val mismatched = series.captures.values.filter { it.eye != series.eye }
        if (mismatched.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(
                "Laterality mismatch in series: expected ${series.eye} but found ${mismatched.map { it.eye }}"
            ))
        }

        if (!series.allGradable) {
            val reasons = series.getUngradableReasons()
            return Result.failure(IllegalStateException(
                "Series quality gate failed: ${reasons.joinToString(", ")}"
            ))
        }

        return Result.success(true)
    }

    /**
     * Calculates Non-Invasive Tear Break-Up Time (NIBUT) from Placido ring video frames.
     * HARD INVARIANT: If the patient blinks before the break occurs, the result is
     * right-censored; we must never invent an earlier break time.
     */
    fun calculateNibut(
        eye: EyeLaterality,
        frames: List<NibutVideoFrame>,
        breakThresholdRatio: Double = 0.85
    ): NibutMeasurement {
        require(frames.isNotEmpty()) { "NIBUT analysis requires non-empty video frames" }

        var breakTime: Double? = null
        var blinkTime = frames.last().timestampSeconds
        var rightCensored = false

        for (frame in frames) {
            if (frame.blinkDetected) {
                blinkTime = frame.timestampSeconds
                rightCensored = true
                break
            }
            if (breakTime == null && frame.ringContinuityRatio < breakThresholdRatio) {
                breakTime = frame.timestampSeconds
            }
        }

        val stability = when {
            breakTime != null && breakTime < 5.0 -> TearFilmStability.SEVERE_DEFICIENCY
            breakTime != null && breakTime < 10.0 -> TearFilmStability.MARGINAL_UNSTABLE
            breakTime != null -> TearFilmStability.NORMAL_STABLE
            rightCensored && blinkTime < 10.0 -> TearFilmStability.RIGHT_CENSORED_EARLY_BLINK
            else -> TearFilmStability.NORMAL_STABLE
        }

        val reportedVal = breakTime ?: blinkTime
        val measurement = Measurement(
            measurementId = "MEAS-NIBUT-${eye.name}-${System.currentTimeMillis()}",
            provenanceIds = listOf("PLACIDO_RING_VIDEO"),
            eye = eye,
            method = "NON_INVASIVE_PLACIDO_BREAK_UP_TIME",
            value = reportedVal,
            units = "SECONDS",
            confidenceLower = if (breakTime != null) (breakTime - 0.5).coerceAtLeast(0.0) else null,
            confidenceUpper = if (breakTime != null) breakTime + 0.5 else null,
            validity = true,
            limits = if (rightCensored && breakTime == null) listOf("RIGHT_CENSORED_EARLY_BLINK") else emptyList()
        )

        return NibutMeasurement(
            eye = eye,
            firstBreakSeconds = breakTime,
            isRightCensored = rightCensored && breakTime == null,
            blinkIntervalSeconds = blinkTime,
            stability = stability,
            measurement = measurement
        )
    }

    /**
     * Staging of pterygium encroachment over the limbus.
     */
    fun evaluatePterygium(
        eye: EyeLaterality,
        encroachmentMm: Double,
        location: String = "NASAL"
    ): PterygiumAssessment {
        val stage = when {
            encroachmentMm <= 0.0 -> PterygiumStage.NONE
            encroachmentMm < 2.0 -> PterygiumStage.GRADE_1_EARLY_LIMBAL
            encroachmentMm <= 4.0 -> PterygiumStage.GRADE_2_INTERMEDIATE
            else -> PterygiumStage.GRADE_3_ADVANCED_PUPILLARY
        }

        val finding = if (stage != PterygiumStage.NONE) {
            Finding(
                eye = eye,
                broadDomain = TestDomain.ANTERIOR,
                finding = "PTERYGIUM_${stage.name}: ${encroachmentMm}mm corneal encroachment ($location)",
                severity = if (stage == PterygiumStage.GRADE_3_ADVANCED_PUPILLARY) "SEVERE" else "MODERATE",
                provenanceIds = listOf("ANTERIOR_SEGMENTATION_LIMBUS"),
                validity = true
            )
        } else null

        return PterygiumAssessment(
            eye = eye,
            stage = stage,
            encroachmentMm = encroachmentMm,
            location = if (stage == PterygiumStage.NONE) "NONE" else location,
            finding = finding
        )
    }

    /**
     * Quantitative pupillary opacity assessment for cataract suspicion.
     */
    fun evaluateCataractOpacity(
        eye: EyeLaterality,
        opacityScore: Double,
        retroilluminationDefect: Boolean = false
    ): CataractOpacityAssessment {
        val grade = when {
            opacityScore < 0.15 && !retroilluminationDefect -> CataractDensityGrade.CLEAR_NO_OPACITY
            opacityScore < 0.40 -> CataractDensityGrade.MILD_SUSPECTED
            opacityScore < 0.70 -> CataractDensityGrade.MODERATE
            else -> CataractDensityGrade.DENSE_MATURE
        }

        val finding = if (grade != CataractDensityGrade.CLEAR_NO_OPACITY) {
            Finding(
                eye = eye,
                broadDomain = TestDomain.ANTERIOR,
                finding = "CATARACT_PUPILLARY_OPACITY_${grade.name}: opacity score ${(opacityScore * 100).toInt()}%",
                severity = if (grade == CataractDensityGrade.DENSE_MATURE) "SEVERE" else "MODERATE",
                provenanceIds = listOf("ANTERIOR_PUPIL_REFLEX"),
                validity = true
            )
        } else null

        return CataractOpacityAssessment(
            eye = eye,
            opacityScore = opacityScore,
            grade = grade,
            retroilluminationDefect = retroilluminationDefect,
            finding = finding
        )
    }

    /**
     * Open-set classification rejection: detects anomalous or out-of-distribution anterior findings.
     * Hard Medical Invariant: Ambiguous features must never be forced into a benign class.
     */
    fun classifyWithOpenSetRejection(
        candidateClass: String,
        confidence: Double,
        distributionDistance: Double,
        maxAllowedDistance: Double = 2.5
    ): OpenSetClassificationResult {
        val isOod = distributionDistance > maxAllowedDistance || confidence < 0.60
        return if (isOod) {
            OpenSetClassificationResult(
                recognizedClass = "OPEN_SET_UNKNOWN",
                inDistributionConfidence = confidence,
                isOpenSetUnknown = true,
                recommendation = "Anomalous anterior feature detected outside trained distribution. Clinical slit-lamp evaluation required."
            )
        } else {
            OpenSetClassificationResult(
                recognizedClass = candidateClass,
                inDistributionConfidence = confidence,
                isOpenSetUnknown = false,
                recommendation = "Standard diagnostic protocol."
            )
        }
    }

    /**
     * Evaluates infection risk and generates triage referral.
     * HARD MEDICAL INVARIANT: Red eye with severe pain, ciliary flush, or corneal infiltrate
     * requires EMERGENCY_SAME_DAY referral. Redness alone is never called definite bacterial
     * conjunctivitis without differential consideration.
     */
    fun performInfectionTriage(
        eye: EyeLaterality,
        signs: AnteriorSymptomSigns,
        series: MultiGazeSeries?
    ): InfectionTriageResult {
        val isImageAvailable = series != null && series.allGradable
        val findings = mutableListOf<Finding>()
        val differentials = mutableListOf<String>()

        if (!signs.hasRedEye) {
            return InfectionTriageResult(
                eye = eye,
                urgency = InfectionTriageUrgency.ROUTINE,
                provisionalFindings = emptyList(),
                requiresSameDayOphthalmologist = false,
                differentialDiagnoses = emptyList(),
                triageNote = "No anterior ciliary or conjunctival injection reported."
            )
        }

        val isEmergency = signs.hasSeverePain || signs.ciliaryFlushPresent ||
                          signs.cornealOpacityOrInfiltrate || signs.hasVisionDrop

        if (isEmergency) {
            val basisList = listOfNotNull(
                if (signs.hasSeverePain) "Severe ocular pain" else null,
                if (signs.ciliaryFlushPresent) "Ciliary flush / perilimbal injection" else null,
                if (signs.cornealOpacityOrInfiltrate) "Suspected corneal infiltrate or ulcer" else null,
                if (signs.hasVisionDrop) "Acute visual drop" else null
            )
            findings.add(Finding(
                eye = eye,
                broadDomain = TestDomain.ANTERIOR,
                finding = "RED_EYE_RED_FLAG_URGENT: ${basisList.joinToString(", ")}",
                severity = "EMERGENCY",
                provenanceIds = basisList,
                validity = isImageAvailable
            ))
            differentials.addAll(listOf("Microbial Keratitis", "Acute Angle Closure Suspicion", "Acute Anterior Uveitis", "Endophthalmitis"))

            return InfectionTriageResult(
                eye = eye,
                urgency = InfectionTriageUrgency.EMERGENCY_SAME_DAY,
                provisionalFindings = findings,
                requiresSameDayOphthalmologist = true,
                differentialDiagnoses = differentials,
                triageNote = "RED FLAG ALERT: Severe pain, corneal involvement, or ciliary flush detected. Immediate same-day ophthalmic evaluation required."
            )
        }

        if (signs.purulentDischargePresent) {
            differentials.add("Bacterial Conjunctivitis suspicion")
            differentials.add("Viral / Chlamydial Conjunctivitis")
        } else if (signs.foreignBodySensationOnly) {
            differentials.add("Dry Eye Syndrome / Tear Film Instability")
            differentials.add("Allergic Conjunctivitis")
            differentials.add("Episcleritis")
        } else {
            differentials.add("Non-specific Conjunctival Injection")
            differentials.add("Viral Conjunctivitis")
            differentials.add("Allergic Conjunctivitis")
        }

        findings.add(Finding(
            eye = eye,
            broadDomain = TestDomain.ANTERIOR,
            finding = "ANTERIOR_HYPEREMIA_NON_CRITICAL: Conjunctival injection without ciliary flush or severe pain",
            severity = "MODERATE",
            provenanceIds = listOf("Conjunctival injection without ciliary flush or severe pain"),
            validity = isImageAvailable
        ))

        return InfectionTriageResult(
            eye = eye,
            urgency = InfectionTriageUrgency.ROUTINE,
            provisionalFindings = findings,
            requiresSameDayOphthalmologist = false,
            differentialDiagnoses = differentials,
            triageNote = "Mild-to-moderate hyperemia without critical red flags. Differential includes allergic/dry eye/infective etiology. Routine ophthalmic examination advised."
        )
    }
}
