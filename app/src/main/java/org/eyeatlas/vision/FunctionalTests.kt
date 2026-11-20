package org.eyeatlas.vision

import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.Finding
import org.eyeatlas.core.Measurement
import org.eyeatlas.core.TestDomain
import kotlin.math.*

enum class OptotypeOrientation {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

data class OptotypeTrial(
    val trialId: String,
    val eye: EyeLaterality,
    val logMar: Double,
    val strokeArcmin: Double,
    val strokePixels: Double,
    val orientation: OptotypeOrientation,
    val vergenceDiopters: Double,
    val isPhysicallyResolvable: Boolean
)

data class AcuityResult(
    val eye: EyeLaterality,
    val estimatedLogMar: Double,
    val snellenEquivalent: String,
    val isResolutionLimited: Boolean,
    val physicalResolutionFloorLogMar: Double,
    val accommodationConfounded: Boolean,
    val vergenceDiopters: Double,
    val reversals: Int
)

class FunctionalTests(
    val displayPpd: Double = 13.5, // Standard cardboard / wide-field PPD baseline
    val focalLengthMm: Double = 45.0
) {
    companion object {
        const val NYQUIST_MIN_PIXELS_PER_STROKE = 1.5
        const val PPD_REQUIRED_FOR_SIX_SIX = 90.0 // Minimum PPD physically required for 6/6 acuity
        const val ONEPLUS_NORD_CE3_REFRESH_RATE_HZ = 120.0
        const val ONEPLUS_NORD_CE3_TOUCH_SAMPLING_HZ = 240.0
        const val GOLDMANN_III_STIMULUS_DURATION_MS = 200.0

        /**
         * Computes the exact VSYNC frame presentation count for Goldmann III (200 ms) stimulus
         * at the device's native refresh rate (e.g. 24 frames @ 120 Hz on OnePlus Nord CE 3 5G).
         */
        fun calculateStimulusFrames(refreshRateHz: Double = ONEPLUS_NORD_CE3_REFRESH_RATE_HZ): Int {
            val frameDurationMs = 1000.0 / refreshRateHz
            return round(GOLDMANN_III_STIMULUS_DURATION_MS / frameDurationMs).toInt()
        }

        /**
         * Touch sampling latency floor on high-refresh digitizer (e.g. 4.167 ms @ 240 Hz on OnePlus Nord CE 3 5G).
         */
        fun getTouchSamplingIntervalMs(samplingRateHz: Double = ONEPLUS_NORD_CE3_TOUCH_SAMPLING_HZ): Double {
            return 1000.0 / samplingRateHz
        }

        /**
         * Calibrated blind spot coordinate for Heijl-Krakau fixation monitor.
         * Physiological blind spot: 15° temporal, 1.5° inferior.
         */
        fun getBlindSpotCoordinates(eye: EyeLaterality): Pair<Double, Double> {
            val temporalSign = if (eye == EyeLaterality.OD) 1.0 else -1.0
            val blindSpotX = 15.0 * temporalSign
            val blindSpotY = -1.5
            return Pair(blindSpotX, blindSpotY)
        }
    }

    /**
     * Calculates stroke size in pixels for a given logMAR optotype.
     * 6/6 Snellen = 0.0 logMAR = 1 arcmin stroke.
     */
    fun calculateStrokePixels(logMar: Double): Double {
        val strokeArcmin = 10.0.pow(logMar)
        val strokeDegrees = strokeArcmin / 60.0
        return strokeDegrees * displayPpd
    }

    /**
     * Determines the physical resolution floor in logMAR for the current display PPD.
     * Displays with low PPD (e.g. 13.5 PPD) CANNOT physically resolve 6/6 (0.0 logMAR).
     */
    fun getPhysicalResolutionFloorLogMar(): Double {
        val minStrokeDegrees = NYQUIST_MIN_PIXELS_PER_STROKE / displayPpd
        val minStrokeArcmin = minStrokeDegrees * 60.0
        return log10(minStrokeArcmin)
    }

    /**
     * Generates a calibrated ETDRS Tumbling E optotype trial.
     */
    fun generateOptotypeTrial(
        trialId: String,
        eye: EyeLaterality,
        targetLogMar: Double,
        orientation: OptotypeOrientation,
        vergenceDiopters: Double = 0.0
    ): OptotypeTrial {
        val strokeArcmin = 10.0.pow(targetLogMar)
        val strokePixels = calculateStrokePixels(targetLogMar)
        val isResolvable = strokePixels >= NYQUIST_MIN_PIXELS_PER_STROKE

        return OptotypeTrial(
            trialId = trialId,
            eye = eye,
            logMar = targetLogMar,
            strokeArcmin = strokeArcmin,
            strokePixels = strokePixels,
            orientation = orientation,
            vergenceDiopters = vergenceDiopters,
            isPhysicallyResolvable = isResolvable
        )
    }

    /**
     * Simulates adaptive staircase psychometric convergence to measure monocular acuity.
     * Enforces the 6/6 impossible resolution gate: if display PPD < 90, 6/6 claims are rejected.
     */
    fun measureMonocularAcuity(
        eye: EyeLaterality,
        responses: List<Pair<OptotypeOrientation, OptotypeOrientation>>, // Pair(presented, observed)
        startingLogMar: Double = 1.0, // Start at 6/60
        vergenceDiopters: Double = 0.0
    ): AcuityResult {
        var currentLogMar = startingLogMar
        val stepSize = 0.1 // 1 line on ETDRS chart
        var consecutiveCorrect = 0
        var reversals = 0
        var lastDirection = 0 // -1 down (better acuity), +1 up (worse acuity)

        for ((presented, observed) in responses) {
            val isCorrect = presented == observed
            if (isCorrect) {
                consecutiveCorrect++
                if (consecutiveCorrect >= 3) {
                    // Step down (smaller letters, better acuity)
                    currentLogMar = max(-0.3, currentLogMar - stepSize)
                    consecutiveCorrect = 0
                    if (lastDirection == 1) reversals++
                    lastDirection = -1
                }
            } else {
                // Step up immediately on error (1-up)
                currentLogMar = min(1.6, currentLogMar + stepSize)
                consecutiveCorrect = 0
                if (lastDirection == -1) reversals++
                lastDirection = 1
            }
        }

        // Apply physical resolution gate
        val resolutionFloor = getPhysicalResolutionFloorLogMar()
        val isLimited = currentLogMar < resolutionFloor

        val finalLogMar = if (isLimited) {
            max(currentLogMar, resolutionFloor)
        } else {
            currentLogMar
        }

        // Convert to approximate Snellen
        val snellenDenominator = round(6.0 * 10.0.pow(finalLogMar)).toInt()
        val snellen = "6/$snellenDenominator"

        return AcuityResult(
            eye = eye,
            estimatedLogMar = (round(finalLogMar * 100) / 100.0),
            snellenEquivalent = snellen,
            isResolutionLimited = isLimited,
            physicalResolutionFloorLogMar = (round(resolutionFloor * 100) / 100.0),
            accommodationConfounded = false,
            vergenceDiopters = vergenceDiopters,
            reversals = reversals
        )
    }

    /**
     * Compares distance acuity against near-view acuity (-2.5 D vergence).
     * If near acuity drops significantly, flags accommodation confounding / presbyopia suspicion.
     */
    fun evaluateNearAccommodation(
        distanceResult: AcuityResult,
        nearResult: AcuityResult
    ): Pair<Boolean, String?> {
        val dropLogMar = nearResult.estimatedLogMar - distanceResult.estimatedLogMar
        if (dropLogMar >= 0.3) { // Significant drop >= 3 lines at near
            return Pair(true, "Near-view acuity drop of ${round(dropLogMar * 100) / 100.0} logMAR at -2.5 D vergence indicates accommodation insufficiency / presbyopia.")
        }
        return Pair(false, null)
    }

    /**
     * Converts acuity result to standard clinical Measurement and Finding models.
     */
    fun toClinicalFindings(result: AcuityResult): Pair<Measurement, Finding> {
        val measurement = Measurement(
            measurementId = "M-ACUITY-${result.eye.name}",
            provenanceIds = listOf("TRIAL-ACUITY-${result.eye.name}"),
            eye = result.eye,
            method = "MONOCULAR_ETDRS_LOGMAR",
            value = result.estimatedLogMar,
            units = "logMAR",
            validity = !result.isResolutionLimited,
            limits = if (result.isResolutionLimited) listOf("RESOLUTION_LIMITED_AT_PPD") else emptyList()
        )

        val severity = when {
            result.estimatedLogMar <= 0.3 -> "NORMAL_OR_MILD"
            result.estimatedLogMar <= 0.6 -> "MODERATE_VISUAL_IMPAIRMENT"
            else -> "SEVERE_VISUAL_IMPAIRMENT"
        }

        val finding = Finding(
            eye = result.eye,
            broadDomain = TestDomain.ACUITY,
            finding = "Monocular Acuity: ${result.snellenEquivalent} (${result.estimatedLogMar} logMAR)",
            severity = severity,
            validity = true
        )

        return Pair(measurement, finding)
    }

    /**
     * P07: Validates display gamma and white-point calibration.
     * Prevents invalid colour screening on uncalibrated consumer screens.
     */
    fun validateDisplayColorProfile(gamma: Double, whitePoint: String): ColorProfileValidation {
        val flags = mutableListOf<String>()
        var isValid = true

        if (gamma !in 2.1..2.3) {
            isValid = false
            flags.add("GAMMA_OUT_OF_BOUNDS")
        }
        if (!whitePoint.equals("D65", ignoreCase = true)) {
            isValid = false
            flags.add("WHITE_POINT_NOT_D65")
        }

        return ColorProfileValidation(
            gamma = gamma,
            whitePoint = whitePoint,
            isCalibrated = isValid,
            validationFlags = flags
        )
    }

    /**
     * P07: Central macular scotoma evaluation with repeat-scotoma confirmation.
     * Hard Medical Invariant: Transient missed points (1 miss) must NOT be diagnosed
     * as macular scotoma. Requires >= 2 confirmed misses at the same coordinate.
     */
    fun evaluateMacularScotomas(points: List<MacularScotomaPoint>): Pair<Boolean, List<MacularScotomaPoint>> {
        val confirmedScotomas = points.filter { it.isConfirmedRepeatScotoma }
        val hasDefect = confirmedScotomas.isNotEmpty()
        return Pair(hasDefect, confirmedScotomas)
    }

    /**
     * P07: Contrast sensitivity validation with anti-aliasing inspection.
     * Rejects optotypes that leak high-frequency subpixel aliasing cues.
     */
    fun validateContrastOptotype(contrastPercent: Double, isAntiAliased: Boolean, aliasingDetected: Boolean): Boolean {
        if (!isAntiAliased || aliasingDetected) {
            return false // Subpixel artifacts invalidate contrast measurement
        }
        return contrastPercent in 0.5..100.0
    }

    /**
     * P08: Generates the canonical 54-point suprathreshold visual field test coordinates.
     * Covers +/- 30 degrees horizontal (nasal step) and +/- 24 degrees vertical (24-2 pattern).
     */
    fun generateFiftyFourPointMap(eye: EyeLaterality): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val xSteps = listOf(-27.0, -21.0, -15.0, -9.0, -3.0, 3.0, 9.0, 15.0, 21.0, 27.0)
        val ySteps = listOf(-21.0, -15.0, -9.0, -3.0, 3.0, 15.0, 21.0)

        for (x in xSteps) {
            for (y in ySteps) {
                // Radial filter to form standard 24-2 footprint (approx 54 valid points)
                val r = sqrt(x.pow(2) + y.pow(2))
                if (r in 4.0..28.5) {
                    points.add(Pair(x, y))
                }
            }
        }
        return points.take(54)
    }

    /**
     * P08: Evaluates visual field catch-trial reliability metrics.
     * Hard Invariant: If false positives > 20% or fixation loss > 20%, test is flagged UNRELIABLE.
     * Output explicitly declares research screening; zero Humphrey SAP equivalence claimed.
     */
    fun evaluateFieldReliability(catchTrials: List<CatchTrialResult>): FieldReliability {
        val fpTrials = catchTrials.filter { it.trialType == CatchTrialType.BLANK_FALSE_POSITIVE }
        val fnTrials = catchTrials.filter { it.trialType == CatchTrialType.SUPRATHRESHOLD_FALSE_NEGATIVE }
        val flTrials = catchTrials.filter { it.trialType == CatchTrialType.BLIND_SPOT_FIXATION_LOSS }

        val fpRate = if (fpTrials.isNotEmpty()) fpTrials.count { it.patientResponded }.toDouble() / fpTrials.size else 0.0
        val fnRate = if (fnTrials.isNotEmpty()) fnTrials.count { !it.patientResponded }.toDouble() / fnTrials.size else 0.0
        val flRate = if (flTrials.isNotEmpty()) flTrials.count { it.patientResponded }.toDouble() / flTrials.size else 0.0

        val isUnreliable = fpRate > 0.20 || fnRate > 0.20 || flRate > 0.20
        val warning = if (isUnreliable) {
            "TEST_RELIABILITY_LOW: Catch-trial errors exceed 20% threshold (FP=${round(fpRate*100)}%, FN=${round(fnRate*100)}%, FL=${round(flRate*100)}%). Research screening only; NO Humphrey equivalence."
        } else {
            "Field Reliability High: Catch-trial errors within acceptable boundaries. Research screening only; NO Humphrey equivalence."
        }

        return FieldReliability(
            falsePositiveRate = fpRate,
            falseNegativeRate = fnRate,
            fixationLossRate = flRate,
            isReliable = !isUnreliable,
            reliabilityWarning = warning
        )
    }
}

enum class CatchTrialType {
    BLANK_FALSE_POSITIVE,
    SUPRATHRESHOLD_FALSE_NEGATIVE,
    BLIND_SPOT_FIXATION_LOSS
}

data class CatchTrialResult(
    val trialType: CatchTrialType,
    val patientResponded: Boolean
)

data class FieldReliability(
    val falsePositiveRate: Double,
    val falseNegativeRate: Double,
    val fixationLossRate: Double,
    val isReliable: Boolean,
    val reliabilityWarning: String? = null
)

data class ColorProfileValidation(
    val gamma: Double,
    val whitePoint: String,
    val isCalibrated: Boolean,
    val validationFlags: List<String> = emptyList()
)

data class MacularScotomaPoint(
    val angleXDeg: Double,
    val angleYDeg: Double,
    val presentations: Int,
    val misses: Int
) {
    val isConfirmedRepeatScotoma: Boolean
        get() = presentations >= 2 && misses >= 2
}

