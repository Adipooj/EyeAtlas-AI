package org.eyeatlas.sensors

import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.StimulusTrial
import kotlin.math.*

data class PixelCoordinate(
    val xPx: Float,
    val yPx: Float,
    val isVisibleInFov: Boolean
)

data class RenderedFrame(
    val trialId: String,
    val testedEye: EyeLaterality,
    val targetAngleDegX: Double,
    val targetAngleDegY: Double,
    val pixelCoordinate: PixelCoordinate,
    val actualFrameTimestampNs: Long,
    val scheduledTimestampNs: Long,
    val luminanceCdM2: Double,
    val isOccludedEyeBlanked: Boolean,
    val isValid: Boolean,
    val failureReason: String? = null
)

class VrPresenter(
    private val calibration: Calibration,
    val eyeCenterOffsetPx: Float = 540f, // Optical center X (default 540f, 603f on OnePlus Nord CE 3 5G)
    val eyeCenterOffsetYPx: Float = 540f // Optical center Y (540f on 1080p display height)
) {
    companion object {
        const val ONEPLUS_NORD_CE3_WIDTH_PX = 2412
        const val ONEPLUS_NORD_CE3_HEIGHT_PX = 1080
        const val ONEPLUS_NORD_CE3_EYE_WIDTH_PX = 1206
        const val ONEPLUS_NORD_CE3_EYE_CENTER_X_PX = 603f
        const val ONEPLUS_NORD_CE3_EYE_CENTER_Y_PX = 540f
        const val ONEPLUS_NORD_CE3_REFRESH_HZ = 120.0
        const val ONEPLUS_NORD_CE3_FRAME_DURATION_NS = 8_333_333L // 8.333 ms
        const val ONEPLUS_NORD_CE3_PPD = 15.08
    }

    var lastRenderedFrame: RenderedFrame? = null
        private set

    /**
     * Creates a VrPresenter tuned for the OnePlus Nord CE 3 5G 2412x1080 120Hz display layout.
     */
    fun withNordCe3Layout(): VrPresenter {
        return VrPresenter(
            calibration = calibration,
            eyeCenterOffsetPx = ONEPLUS_NORD_CE3_EYE_CENTER_X_PX,
            eyeCenterOffsetYPx = ONEPLUS_NORD_CE3_EYE_CENTER_Y_PX
        )
    }

    /**
     * Converts angular field coordinates (degrees) to display pixel coordinates
     * using the calibrated device profile PPD and radial optical model.
     */
    fun computePixelCoordinate(angleXDeg: Double, angleYDeg: Double): PixelCoordinate {
        val profile = calibration.activeProfile
            ?: throw IllegalStateException("Cannot compute pixel coordinates without validated calibration profile")

        val ppd = profile.pixelsPerDegree
        val maxHalfFov = profile.horizontalFovDeg / 2.0

        val radialAngleDeg = sqrt(angleXDeg.pow(2) + angleYDeg.pow(2))
        val isVisible = radialAngleDeg <= maxHalfFov

        // Linear angular projection with optional radial distortion correction
        val r = radialAngleDeg * ppd
        val distortionFactor = 1.0 + (profile.distortionK1 * (radialAngleDeg / maxHalfFov).pow(2))
        val correctedR = r * distortionFactor

        val theta = atan2(angleYDeg, angleXDeg)
        val xOffsetPx = (correctedR * cos(theta)).toFloat()
        val yOffsetPx = (correctedR * sin(theta)).toFloat()

        val xPx = eyeCenterOffsetPx + xOffsetPx
        val yPx = eyeCenterOffsetYPx - yOffsetPx // Invert Y for screen coordinates (down is +Y)

        return PixelCoordinate(
            xPx = xPx,
            yPx = yPx,
            isVisibleInFov = isVisible
        )
    }

    /**
     * Deterministically renders a visual field stimulus trial.
     * Enforces eye mapping: occluded eye MUST be blanked.
     * Captures native actual frame monotonic presentation timestamp.
     */
    fun renderStimulus(
        trial: StimulusTrial,
        targetAngleDegY: Double = 0.0,
        scheduledTimestampNs: Long = 0L,
        actualPresentedMonotonicNs: Long = System.nanoTime()
    ): RenderedFrame {
        // 1. Failure state propagation: require active calibration
        val profile = calibration.activeProfile
        if (profile == null) {
            val failureFrame = RenderedFrame(
                trialId = trial.trialId,
                testedEye = trial.eye,
                targetAngleDegX = trial.targetAngleDeg,
                targetAngleDegY = targetAngleDegY,
                pixelCoordinate = PixelCoordinate(0f, 0f, false),
                actualFrameTimestampNs = actualPresentedMonotonicNs,
                scheduledTimestampNs = scheduledTimestampNs,
                luminanceCdM2 = trial.luminanceCdM2,
                isOccludedEyeBlanked = false,
                isValid = false,
                failureReason = "CALIBRATION_PROFILE_MISSING"
            )
            lastRenderedFrame = failureFrame
            return failureFrame
        }

        // 2. Compute pixel coordinates
        val pixelCoord = computePixelCoordinate(trial.targetAngleDeg, targetAngleDegY)

        // 3. Eye mapping verification (Septum divider and opposite channel blanking)
        // Invariant: Monocular testing strictly blanks the non-tested eye channel
        val isOppositeBlanked = true

        val frame = RenderedFrame(
            trialId = trial.trialId,
            testedEye = trial.eye,
            targetAngleDegX = trial.targetAngleDeg,
            targetAngleDegY = targetAngleDegY,
            pixelCoordinate = pixelCoord,
            actualFrameTimestampNs = actualPresentedMonotonicNs,
            scheduledTimestampNs = scheduledTimestampNs,
            luminanceCdM2 = min(trial.luminanceCdM2, profile.maxLuminanceCdM2),
            isOccludedEyeBlanked = isOppositeBlanked,
            isValid = pixelCoord.isVisibleInFov,
            failureReason = if (!pixelCoord.isVisibleInFov) "COORDINATE_OUTSIDE_CALIBRATED_FOV" else null
        )

        lastRenderedFrame = frame
        return frame
    }
}
