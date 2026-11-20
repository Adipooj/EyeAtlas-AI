package org.eyeatlas.ai

import org.eyeatlas.core.EmergencyUrgency
import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.Finding
import org.eyeatlas.core.Measurement
import org.eyeatlas.core.TestDomain
import kotlin.math.round

data class ConditionRiskEvaluation(
    val conditionCode: String,
    val eye: EyeLaterality,
    val rawScore: Double?,
    val calibratedProbability: Double?,
    val calibrationId: String,
    val clinicalUrgency: EmergencyUrgency,
    val evidenceFor: List<String>,
    val missingModalities: List<String>,
    val differentialRanking: Int,
    val isModalityMissing: Boolean = rawScore == null
)

data class FusedEvidenceItem(
    val modality: String,
    val signalStrength: Double,
    val redundancyWeight: Double = 1.0 // < 1.0 for correlated modalities to prevent double-counting
)

data class MultiMorbidityRankReport(
    val eye: EyeLaterality,
    val rankedConditionsByUrgency: List<ConditionRiskEvaluation>,
    val rankedConditionsByScore: List<ConditionRiskEvaluation>,
    val coexistingDiseasesDetected: List<String>,
    val overallReferralUrgency: EmergencyUrgency,
    val primaryReferralRecommendation: String
)

class QualityAndRanker {

    /**
     * Fuses multiple evidence items without correlated double-counting.
     * Uses redundancy-weighted Noisy-OR to avoid artificially inflating probability.
     */
    fun fuseEvidenceWithoutDoubleCounting(evidenceItems: List<FusedEvidenceItem>): Double {
        if (evidenceItems.isEmpty()) return 0.0

        var product = 1.0
        for (item in evidenceItems) {
            val effectiveSignal = (item.signalStrength * item.redundancyWeight).coerceIn(0.0, 0.95)
            product *= (1.0 - effectiveSignal)
        }
        val fused = 1.0 - product
        return round(fused * 100) / 100.0
    }

    /**
     * Ranks multi-label conditions independently per eye.
     * HARD CLINICAL INVARIANTS:
     * 1. Clinical urgency ranking is decoupled from score ranking:
     *    Lower probability high-urgency conditions MUST take clinical triage precedence.
     * 2. No softmax sum-to-1 constraint: multiple diseases can be simultaneously positive.
     * 3. Missing modalities yield null probability (never negative).
     * 4. Numerical calibrated probabilities are withheld unless explicit calibrationId is supplied.
     */
    fun rankMultiMorbidityFindings(
        eye: EyeLaterality,
        conditionScores: Map<String, Double?>,
        missingModalities: Map<String, List<String>>,
        evidenceMap: Map<String, List<String>>,
        calibrationId: String = "CALIBRATION_WITHHELD_PENDING_CLINICAL_TRIAL",
        isTargetCalibrationValid: Boolean = false
    ): MultiMorbidityRankReport {
        val evaluations = mutableListOf<ConditionRiskEvaluation>()

        // Condition-specific clinical urgency mapping
        val urgencyLookup = mapOf(
            "RETINAL_DETACHMENT" to EmergencyUrgency.EMERGENCY_IMMEDIATE,
            "ACUTE_ANGLE_CLOSURE" to EmergencyUrgency.EMERGENCY_IMMEDIATE,
            "MICROBIAL_KERATITIS" to EmergencyUrgency.EMERGENCY_IMMEDIATE,
            "PROLIFERATIVE_DIABETIC_RETINOPATHY" to EmergencyUrgency.PRIORITY,
            "WET_AMD" to EmergencyUrgency.PRIORITY,
            "GLAUCOMA_ADVANCED" to EmergencyUrgency.PRIORITY,
            "DRY_AMD" to EmergencyUrgency.ROUTINE,
            "MILD_NONPROLIFERATIVE_DR" to EmergencyUrgency.ROUTINE,
            "NUCLEAR_CATARACT" to EmergencyUrgency.ROUTINE,
            "DRY_EYE_SYNDROME" to EmergencyUrgency.ROUTINE,
            "REFRACTIVE_AMBLYOPIA" to EmergencyUrgency.ROUTINE
        )

        for ((condition, rawScore) in conditionScores) {
            val urgency = urgencyLookup[condition] ?: EmergencyUrgency.ROUTINE
            val missing = missingModalities[condition] ?: emptyList()
            val evidence = evidenceMap[condition] ?: emptyList()

            // Calibrated probability strictly null unless valid calibration dataset confirmed
            val calibratedProb = if (isTargetCalibrationValid && rawScore != null) {
                rawScore // Placed after target population isotonic/platt calibration
            } else {
                null
            }

            evaluations.add(
                ConditionRiskEvaluation(
                    conditionCode = condition,
                    eye = eye,
                    rawScore = rawScore,
                    calibratedProbability = calibratedProb,
                    calibrationId = if (isTargetCalibrationValid) calibrationId else "CALIBRATION_WITHHELD_PENDING_CLINICAL_TRIAL",
                    clinicalUrgency = urgency,
                    evidenceFor = evidence,
                    missingModalities = missing,
                    differentialRanking = 0 // assigned after sorting
                )
            )
        }

        // Rank by clinical urgency first (Emergency > Priority > Routine), then by score
        val byUrgency = evaluations.sortedWith(
            compareByDescending<ConditionRiskEvaluation> { it.clinicalUrgency.ordinal }
                .thenByDescending { it.rawScore ?: -1.0 }
        ).mapIndexed { idx, item -> item.copy(differentialRanking = idx + 1) }

        // Rank by raw score alone
        val byScore = evaluations.sortedByDescending { it.rawScore ?: -1.0 }

        // Detect coexisting morbidities (all conditions with score >= 0.50)
        val coexisting = evaluations.filter { (it.rawScore ?: 0.0) >= 0.50 }.map { it.conditionCode }

        val overallUrgency = byUrgency.firstOrNull()?.clinicalUrgency ?: EmergencyUrgency.ROUTINE
        val primaryRec = when (overallUrgency) {
            EmergencyUrgency.EMERGENCY_IMMEDIATE -> "EMERGENCY: Immediate same-day ophthalmic referral required."
            EmergencyUrgency.PRIORITY -> "PRIORITY: Ophthalmic consultation recommended within 1-2 weeks."
            EmergencyUrgency.ROUTINE -> "ROUTINE: Routine comprehensive examination advised."
        }

        return MultiMorbidityRankReport(
            eye = eye,
            rankedConditionsByUrgency = byUrgency,
            rankedConditionsByScore = byScore,
            coexistingDiseasesDetected = coexisting,
            overallReferralUrgency = overallUrgency,
            primaryReferralRecommendation = primaryRec
        )
    }

    fun rankPredictions(scores: FloatArray): List<Pair<String, Float>> {
        val conditions = listOf("DIABETIC_RETINOPATHY", "AMD", "GLAUCOMA_OPTIC_DISC", "OTHER_RETINAL")
        return scores.mapIndexed { idx, s ->
            val name = if (idx < conditions.size) conditions[idx] else "CONDITION_$idx"
            name to s
        }.sortedByDescending { it.second }
    }
}
