package org.eyeatlas.vision

import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.Finding
import org.eyeatlas.core.Measurement
import org.eyeatlas.core.TestDomain
import kotlin.math.abs
import kotlin.math.round

data class StereoTrial(
    val trialId: String,
    val disparityArcsec: Double,
    val targetShape: String,
    val observedShape: String? = null
)

data class StereoResult(
    val thresholdArcsec: Double?,
    val isStereoBlind: Boolean,
    val category: String
)

data class CoverTestResult(
    val eye: EyeLaterality,
    val deviationPrismDiopters: Double,
    val deviationType: String, // "ORTHOPHORIA_NORMAL", "MANIFEST_ESOTROPIA", "MANIFEST_EXOTROPIA", "INTERMITTENT_TROPIA"
    val isStrabismusSuspected: Boolean,
    val isIntermittent: Boolean = false
)

enum class AmblyopiaRiskLevel {
    LOW_NO_CROWDING,
    ELEVATED_CROWDING_DETECTED,
    HIGH_RISK_ANISOMETROPIC_OR_STRABISMIC,
    UNGRADABLE_UNCOOPERATIVE
}

data class CrowdedAcuityEvaluation(
    val eye: EyeLaterality,
    val isolatedLogMar: Double,
    val crowdedLogMar: Double,
    val crowdingDeficitLogMar: Double, // crowded - isolated (>= 0.20 logMAR indicates pathological crowding)
    val interOcularAcuityDiffLogMar: Double,
    val riskLevel: AmblyopiaRiskLevel,
    val isPathologicalCrowding: Boolean,
    val finding: Finding?
)

data class CoverCycleSample(
    val cycleIndex: Int,
    val refixationDisplacementMm: Double,
    val durationMs: Long
)

data class RepeatCoverAlignmentResult(
    val eye: EyeLaterality,
    val cyclesEvaluated: Int,
    val meanDeviationPrismDiopters: Double,
    val maxDeviationPrismDiopters: Double,
    val isIntermittent: Boolean,
    val alignmentDiagnosis: String,
    val coverResult: CoverTestResult
)

data class CooperationPathwayEvaluation(
    val ageYears: Int,
    val isCooperative: Boolean,
    val pathwaySelected: String, // "STANDARD_PSYCHOMETRIC_STAIRCASE", "PREFERENTIAL_LOOKING_ALTERNATIVE_PATHWAY"
    val isStandardTestFeasible: Boolean,
    val note: String
)

class BinocularTests {

    var isObjectivePhotorefractionEnabled: Boolean = false
        private set // Strictly disabled pending optical bench validation

    val photorefractionStatus: String = "PHOTOREFRACTION_DISABLED_PENDING_OPTICAL_VALIDATION"

    /**
     * Measures stereoacuity using disparity optotypes in VR binocular split-screen.
     */
    fun measureStereoAcuity(trials: List<StereoTrial>): StereoResult {
        val correctTrials = trials.filter { it.observedShape != null && it.observedShape.equals(it.targetShape, ignoreCase = true) }
        if (correctTrials.isEmpty()) {
            return StereoResult(
                thresholdArcsec = null,
                isStereoBlind = true,
                category = "STEREO_BLIND_OR_SUPPRESSION"
            )
        }

        val minDisparity = correctTrials.minOf { it.disparityArcsec }
        val category = when {
            minDisparity <= 60.0 -> "NORMAL_HIGH_GRADE_STEREO"
            minDisparity <= 200.0 -> "MODERATE_STEREO"
            else -> "COARSE_STEREO_REDUCED"
        }

        return StereoResult(
            thresholdArcsec = minDisparity,
            isStereoBlind = false,
            category = category
        )
    }

    /**
     * Evaluates simulated alternating cover-uncover ocular alignment for a single event.
     * Computes objective prism deviation: Delta = (refixation displacement mm / viewing distance m).
     *
     * HARD CLINICAL INVARIANT:
     * Stereo impairment ALONE does not establish strabismus.
     * Strabismus requires demonstrated tropia / manifest deviation on cover test.
     */
    fun evaluateCoverTest(
        eye: EyeLaterality,
        refixationMovementMm: Double,
        viewingDistanceM: Double = 0.4
    ): CoverTestResult {
        val prismDiopters = round((refixationMovementMm / viewingDistanceM) * 10) / 10.0

        val (deviationType, isSuspected) = when {
            prismDiopters <= 2.0 -> Pair("ORTHOPHORIA_NORMAL", false)
            refixationMovementMm > 0.0 -> Pair("MANIFEST_ESOTROPIA", true)
            else -> Pair("MANIFEST_EXOTROPIA", true)
        }

        return CoverTestResult(
            eye = eye,
            deviationPrismDiopters = prismDiopters,
            deviationType = deviationType,
            isStrabismusSuspected = isSuspected
        )
    }

    /**
     * Evaluates repeat cover-uncover video sequences across multiple cycles to capture
     * intermittent deviations (e.g. intermittent exotropia/esotropia).
     */
    fun evaluateRepeatCoverAlignment(
        eye: EyeLaterality,
        cycles: List<CoverCycleSample>,
        viewingDistanceM: Double = 0.4
    ): RepeatCoverAlignmentResult {
        require(cycles.isNotEmpty()) { "Repeat alignment requires at least one cycle" }

        val deviations = cycles.map { (it.refixationDisplacementMm / viewingDistanceM) }
        val meanDev = round(deviations.average() * 10) / 10.0
        val maxDev = round(deviations.maxOrNull()!! * 10) / 10.0
        val minDev = round(deviations.minOrNull()!! * 10) / 10.0

        // Intermittent if some cycles are orthophoric (<=2.0 PD) while other cycles exhibit manifest deviation (>2.0 PD)
        val hasManifest = deviations.any { it > 2.0 }
        val hasOrtho = deviations.any { it <= 2.0 }
        val isIntermittent = hasManifest && hasOrtho && cycles.size > 1

        val diagnosis = when {
            isIntermittent && meanDev > 0 -> "INTERMITTENT_ESOTROPIA"
            isIntermittent && meanDev < 0 -> "INTERMITTENT_EXOTROPIA"
            meanDev > 2.0 -> "CONSTANT_MANIFEST_ESOTROPIA"
            meanDev < -2.0 -> "CONSTANT_MANIFEST_EXOTROPIA"
            else -> "ORTHOPHORIA_NORMAL"
        }

        val cover = CoverTestResult(
            eye = eye,
            deviationPrismDiopters = abs(maxDev),
            deviationType = diagnosis,
            isStrabismusSuspected = hasManifest,
            isIntermittent = isIntermittent
        )

        return RepeatCoverAlignmentResult(
            eye = eye,
            cyclesEvaluated = cycles.size,
            meanDeviationPrismDiopters = abs(meanDev),
            maxDeviationPrismDiopters = abs(maxDev),
            isIntermittent = isIntermittent,
            alignmentDiagnosis = diagnosis,
            coverResult = cover
        )
    }

    /**
     * Evaluates crowded vs isolated visual acuity for amblyopia risk detection.
     * HARD CLINICAL INVARIANT: Crowding deficit >= 0.20 logMAR (2 lines) or significant
     * inter-ocular acuity difference (>= 0.20 logMAR) triggers elevated amblyopia risk.
     */
    fun evaluateCrowdedVsIsolatedAcuity(
        testedEye: EyeLaterality,
        isolatedLogMar: Double,
        crowdedLogMar: Double,
        contralateralAcuityLogMar: Double
    ): CrowdedAcuityEvaluation {
        val crowdingDeficit = round((crowdedLogMar - isolatedLogMar) * 100) / 100.0
        val interOcularDiff = round(abs(crowdedLogMar - contralateralAcuityLogMar) * 100) / 100.0
        val isPathological = crowdingDeficit >= 0.20

        val riskLevel = when {
            isPathological && interOcularDiff >= 0.20 -> AmblyopiaRiskLevel.HIGH_RISK_ANISOMETROPIC_OR_STRABISMIC
            isPathological || interOcularDiff >= 0.20 -> AmblyopiaRiskLevel.ELEVATED_CROWDING_DETECTED
            else -> AmblyopiaRiskLevel.LOW_NO_CROWDING
        }

        val finding = if (riskLevel != AmblyopiaRiskLevel.LOW_NO_CROWDING) {
            Finding(
                eye = testedEye,
                broadDomain = TestDomain.BINOCULAR,
                finding = "AMBLYOPIA_RISK_${riskLevel.name}: Crowding deficit ${crowdingDeficit} logMAR, Inter-ocular diff ${interOcularDiff} logMAR",
                severity = if (riskLevel == AmblyopiaRiskLevel.HIGH_RISK_ANISOMETROPIC_OR_STRABISMIC) "SEVERE" else "MODERATE",
                provenanceIds = listOf("CROWDED_ETDRS_STAIRCASE"),
                validity = true
            )
        } else null

        return CrowdedAcuityEvaluation(
            eye = testedEye,
            isolatedLogMar = isolatedLogMar,
            crowdedLogMar = crowdedLogMar,
            crowdingDeficitLogMar = crowdingDeficit,
            interOcularAcuityDiffLogMar = interOcularDiff,
            riskLevel = riskLevel,
            isPathologicalCrowding = isPathological,
            finding = finding
        )
    }

    /**
     * Selects appropriate evaluation pathway based on age and patient cooperation.
     * Uncooperative young children are routed to alternative preferential-looking tasks
     * rather than recorded as false acuity failures.
     */
    fun selectCooperationPathway(ageYears: Int, isCooperative: Boolean): CooperationPathwayEvaluation {
        return if (ageYears < 5 || !isCooperative) {
            CooperationPathwayEvaluation(
                ageYears = ageYears,
                isCooperative = isCooperative,
                pathwaySelected = "PREFERENTIAL_LOOKING_ALTERNATIVE_PATHWAY",
                isStandardTestFeasible = false,
                note = "Standard subjective psychometric staircase bypassed due to young age / uncooperative status. Routed to preferential fixation and motor tracking protocol."
            )
        } else {
            CooperationPathwayEvaluation(
                ageYears = ageYears,
                isCooperative = isCooperative,
                pathwaySelected = "STANDARD_PSYCHOMETRIC_STAIRCASE",
                isStandardTestFeasible = true,
                note = "Standard adult/school-age two-phone response psychometric staircase applicable."
            )
        }
    }

    fun toClinicalFindings(stereo: StereoResult, coverOD: CoverTestResult, coverOS: CoverTestResult): Pair<Measurement, Finding> {
        val measurement = Measurement(
            measurementId = "M-BINOCULAR-01",
            provenanceIds = listOf("TRIAL-STEREO-01"),
            eye = EyeLaterality.OD,
            method = "STEREOPSIS_AND_COVER_TEST",
            value = stereo.thresholdArcsec ?: -1.0,
            units = "arcsec",
            validity = true
        )

        val strabismusPresent = coverOD.isStrabismusSuspected || coverOS.isStrabismusSuspected
        val findingText = if (strabismusPresent) {
            "Manifest Strabismus: OD=${coverOD.deviationType} (${coverOD.deviationPrismDiopters} PD), OS=${coverOS.deviationType} (${coverOS.deviationPrismDiopters} PD)"
        } else {
            "Binocular Alignment Normal: Stereo=${stereo.category} (${stereo.thresholdArcsec ?: 0.0} arcsec)"
        }

        val finding = Finding(
            eye = EyeLaterality.OD,
            broadDomain = TestDomain.BINOCULAR,
            finding = findingText,
            severity = if (strabismusPresent) "MODERATE" else "NORMAL",
            validity = true
        )

        return Pair(measurement, finding)
    }
}
