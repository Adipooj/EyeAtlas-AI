package org.eyeatlas.sensors

import java.security.MessageDigest
import org.eyeatlas.core.EyeLaterality

data class OpticalBenchReport(
    val reportId: String,
    val benchId: String,
    val hardwareId: String,
    val measuredFovDeg: Double,
    val measuredPpd: Double,
    val mtf50AtNyquist: Double, // Modulation Transfer Function contrast transfer (>= 0.35 required)
    val peakLuminanceCdM2: Double,
    val edgeLuminanceRatio: Double, // edge / center (>= 0.65 required)
    val cieWhitePointX: Double, // D65 target 0.3127 (+/- 0.015)
    val cieWhitePointY: Double, // D65 target 0.3290 (+/- 0.015)
    val ipdMinMm: Double = 48.0,
    val ipdMaxMm: Double = 74.0,
    val isOpticalInsertFitted: Boolean = false
) {
    val isD65Compliant: Boolean
        get() = Math.abs(cieWhitePointX - 0.3127) <= 0.015 && Math.abs(cieWhitePointY - 0.3290) <= 0.015

    val isMtfAcceptable: Boolean
        get() = mtf50AtNyquist >= 0.35

    /**
     * Hard Medical Invariant: 6/6 visual acuity (1 arcmin stroke) strictly requires
     * at least 120 PPD and MTF >= 0.35. Low-PPD wide-field displays (e.g. 13.5 PPD)
     * physically cannot resolve 6/6 and must never be permitted to claim it.
     */
    val is66AcuityResolvable: Boolean
        get() = measuredPpd >= 120.0 && isMtfAcceptable && isOpticalInsertFitted

    fun validatePatientIpd(patientIpdMm: Double): Result<Boolean> {
        if (patientIpdMm < ipdMinMm || patientIpdMm > ipdMaxMm) {
            return Result.failure(IllegalArgumentException(
                "IPD $patientIpdMm mm out of mechanical range [$ipdMinMm, $ipdMaxMm] mm"
            ))
        }
        return Result.success(true)
    }
}

data class DeviceProfile(
    val profileId: String,
    val hardwareId: String,
    val focalLengthMm: Double,
    val pixelsPerDegree: Double,
    val horizontalFovDeg: Double,
    val maxLuminanceCdM2: Double,
    val refreshRateHz: Double,
    val distortionK1: Double = 0.0,
    val opticalMode: String = "WIDE_FIELD", // "WIDE_FIELD" or "HIGH_PPD_INSERT"
    val benchReport: OpticalBenchReport? = null,
    val signature: String = ""
) {
    init {
        require(profileId.isNotBlank()) { "Profile ID must not be blank" }
        require(hardwareId.isNotBlank()) { "Hardware ID must not be blank" }
        require(pixelsPerDegree > 0.0) { "Pixels per degree must be strictly positive (got $pixelsPerDegree)" }
        require(refreshRateHz >= 30.0) { "Refresh rate must be at least 30 Hz (got $refreshRateHz)" }
        require(maxLuminanceCdM2 > 0.0) { "Maximum luminance must be strictly positive (got $maxLuminanceCdM2)" }
        require(horizontalFovDeg in 10.0..140.0) { "FOV must be physically plausible (10-140 deg, got $horizontalFovDeg)" }
    }

    val canResolve66Acuity: Boolean
        get() = benchReport?.is66AcuityResolvable ?: (pixelsPerDegree >= 120.0 && opticalMode == "HIGH_PPD_INSERT")

    fun computeDigest(): String {
        val raw = "$profileId|$hardwareId|$focalLengthMm|$pixelsPerDegree|$horizontalFovDeg|$maxLuminanceCdM2|$refreshRateHz|$distortionK1|$opticalMode"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Optical configuration states for the VRBox detachable lens system (CR-002-G).
 */
enum class OpticalConfigurationState {
    BOTH_VR_LENSES_INSTALLED,
    OD_LENS_REMOVED,
    OS_LENS_REMOVED,
    BOTH_LENSES_REMOVED,
    EXTERNAL_ADAPTER_20D_MOUNTED,
    EXTERNAL_ADAPTER_PINHOLE_MOUNTED,
    UNKNOWN_UNCALIBRATED
}

/**
 * Interchangeable modular adapter ring types for VRBox lens carriages.
 */
enum class AdapterRingType {
    STANDARD_VR_EYEPIECE,
    TRIAL_LENS_CARRIER,
    PINHOLE_1_2MM_OCCLUDER,
    INDIRECT_20D_CONDENSING,
    OPEN_APERTURE_NO_LENS
}

/**
 * Result of an optical interlock check prior to test presentation or camera capture.
 */
data class InterlockResult(
    val isPermitted: Boolean,
    val reason: String,
    val violationCode: String? = null,
    val requiredConfiguration: OpticalConfigurationState? = null
)

/**
 * Manages optical hardware state transitions and enforces clinical interlocks.
 */
class OpticalInterlockManager(
    var currentOpticalState: OpticalConfigurationState = OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED,
    var odRingType: AdapterRingType = AdapterRingType.STANDARD_VR_EYEPIECE,
    var osRingType: AdapterRingType = AdapterRingType.STANDARD_VR_EYEPIECE
) {
    var lastStateChangeEpochMs: Long = System.currentTimeMillis()
        private set

    /**
     * Updates optical hardware configuration and triggers calibration invalidation if required.
     */
    fun setOpticalConfiguration(
        newState: OpticalConfigurationState,
        odRing: AdapterRingType = odRingType,
        osRing: AdapterRingType = osRingType,
        calibration: Calibration? = null
    ): Result<OpticalConfigurationState> {
        val previousState = currentOpticalState
        currentOpticalState = newState
        odRingType = odRing
        osRingType = osRing
        lastStateChangeEpochMs = System.currentTimeMillis()

        // Optical configuration change automatically invalidates calibration hash to force re-verification
        if (previousState != newState && calibration != null) {
            calibration.invalidateCalibration()
        }

        return Result.success(currentOpticalState)
    }

    /**
     * Verifies whether a specific clinical test can be safely executed under the current optical configuration.
     * Enforces clinical safety invariants:
     * 1. Wide-field VR tests REQUIRE the VR eyepiece in place for the target eye.
     * 2. Direct anterior/retinal imaging STRICTLY PROHIBITS the VR eyepiece (prevents compound focal collapse & Purkinje glare).
     * 3. Pinhole differential visual acuity REQUIRES the calibrated 1.2mm pinhole adapter.
     * 4. Retinal indirect 20D examination REQUIRES the 20D condensing adapter without VR eyepiece.
     */
    fun verifyInterlockForTest(testCode: String, targetEye: EyeLaterality): InterlockResult {
        if (currentOpticalState == OpticalConfigurationState.UNKNOWN_UNCALIBRATED) {
            return InterlockResult(
                isPermitted = false,
                reason = "Hardware optical state is UNKNOWN_UNCALIBRATED. Please verify lens installation.",
                violationCode = "INTERLOCK_UNCALIBRATED_STATE",
                requiredConfiguration = OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED
            )
        }

        val normalizedCode = testCode.uppercase()

        // 1. Wide-Field VR Stimulus Tests
        val isWideFieldVrTest = normalizedCode.contains("PERIMETRY") ||
                normalizedCode.contains("AMSLER") ||
                normalizedCode.contains("CONTRAST") ||
                normalizedCode.contains("COLOUR") ||
                normalizedCode.contains("ISHIHARA") ||
                normalizedCode.contains("TUMBLING") ||
                normalizedCode.contains("STEREO") ||
                normalizedCode.contains("MOTILITY")

        if (isWideFieldVrTest && !normalizedCode.contains("PINHOLE")) {
            val hasTargetEyeLens = when (targetEye) {
                EyeLaterality.OD -> currentOpticalState == OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED ||
                        currentOpticalState == OpticalConfigurationState.OS_LENS_REMOVED
                EyeLaterality.OS -> currentOpticalState == OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED ||
                        currentOpticalState == OpticalConfigurationState.OD_LENS_REMOVED
                EyeLaterality.OU -> currentOpticalState == OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED
            }

            if (!hasTargetEyeLens) {
                return InterlockResult(
                    isPermitted = false,
                    reason = "Wide-field VR test '$testCode' requires standard VR eyepiece installed for $targetEye. Lens is currently removed.",
                    violationCode = "INTERLOCK_VIOLATION_VR_LENS_REQUIRED",
                    requiredConfiguration = OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED
                )
            }
        }

        // 2. Direct Anterior Surface Capture Tests
        val isAnteriorCaptureTest = normalizedCode.contains("ANTERIOR") ||
                normalizedCode.contains("TORCH") ||
                normalizedCode.contains("NIBUT") ||
                normalizedCode.contains("TEAR_FILM") ||
                normalizedCode.contains("SLIT")

        if (isAnteriorCaptureTest) {
            val isTargetEyeLensRemoved = when (targetEye) {
                EyeLaterality.OD -> currentOpticalState == OpticalConfigurationState.OD_LENS_REMOVED ||
                        currentOpticalState == OpticalConfigurationState.BOTH_LENSES_REMOVED ||
                        odRingType == AdapterRingType.OPEN_APERTURE_NO_LENS
                EyeLaterality.OS -> currentOpticalState == OpticalConfigurationState.OS_LENS_REMOVED ||
                        currentOpticalState == OpticalConfigurationState.BOTH_LENSES_REMOVED ||
                        osRingType == AdapterRingType.OPEN_APERTURE_NO_LENS
                EyeLaterality.OU -> currentOpticalState == OpticalConfigurationState.BOTH_LENSES_REMOVED
            }

            if (!isTargetEyeLensRemoved) {
                return InterlockResult(
                    isPermitted = false,
                    reason = "Direct anterior inspection '$testCode' for $targetEye requires VR eyepiece removed to avoid severe compound reflections and optical distortion.",
                    violationCode = "INTERLOCK_VIOLATION_VR_LENS_MUST_BE_REMOVED",
                    requiredConfiguration = if (targetEye == EyeLaterality.OD) OpticalConfigurationState.OD_LENS_REMOVED else OpticalConfigurationState.OS_LENS_REMOVED
                )
            }
        }

        // 3. Indirect Ophthalmoscopy & Retinal 20D Fundus Examination
        val isRetinal20dTest = normalizedCode.contains("RETINAL") ||
                normalizedCode.contains("20D") ||
                normalizedCode.contains("FUNDUS") ||
                normalizedCode.contains("OPHTHALMOSCOPY")

        if (isRetinal20dTest) {
            val has20dMounted = currentOpticalState == OpticalConfigurationState.EXTERNAL_ADAPTER_20D_MOUNTED ||
                    (targetEye == EyeLaterality.OD && odRingType == AdapterRingType.INDIRECT_20D_CONDENSING) ||
                    (targetEye == EyeLaterality.OS && osRingType == AdapterRingType.INDIRECT_20D_CONDENSING)

            if (!has20dMounted) {
                return InterlockResult(
                    isPermitted = false,
                    reason = "Fundus examination '$testCode' requires 20D condensing adapter mounted and VR eyepiece removed (compound lens collapses focal plane to 18mm).",
                    violationCode = "INTERLOCK_VIOLATION_20D_ADAPTER_REQUIRED",
                    requiredConfiguration = OpticalConfigurationState.EXTERNAL_ADAPTER_20D_MOUNTED
                )
            }
        }

        // 4. Pinhole Differential Visual Acuity
        val isPinholeTest = normalizedCode.contains("PINHOLE")

        if (isPinholeTest) {
            val hasPinholeMounted = currentOpticalState == OpticalConfigurationState.EXTERNAL_ADAPTER_PINHOLE_MOUNTED ||
                    (targetEye == EyeLaterality.OD && odRingType == AdapterRingType.PINHOLE_1_2MM_OCCLUDER) ||
                    (targetEye == EyeLaterality.OS && osRingType == AdapterRingType.PINHOLE_1_2MM_OCCLUDER)

            if (!hasPinholeMounted) {
                return InterlockResult(
                    isPermitted = false,
                    reason = "Pinhole differential visual acuity requires 1.2mm pinhole adapter mounted. Standard VR lens invalidates pinhole blur circle geometry.",
                    violationCode = "INTERLOCK_VIOLATION_PINHOLE_ADAPTER_REQUIRED",
                    requiredConfiguration = OpticalConfigurationState.EXTERNAL_ADAPTER_PINHOLE_MOUNTED
                )
            }
        }

        return InterlockResult(
            isPermitted = true,
            reason = "Optical configuration validated for '$testCode' on eye $targetEye."
        )
    }

    /**
     * Serializes telemetry for remote session broadcasting to Physician Cockpit.
     */
    fun getTelemetryPayload(): Map<String, Any> {
        return mapOf(
            "optical_state" to currentOpticalState.name,
            "od_ring_type" to odRingType.name,
            "os_ring_type" to osRingType.name,
            "last_state_change_ms" to lastStateChangeEpochMs
        )
    }
}

class Calibration(
    var calibrationHash: String = "BASE-P00-SIMULATED"
) {
    var activeProfile: DeviceProfile? = null
        private set

    val isCalibrated: Boolean
        get() = activeProfile != null

    companion object {
        fun createSignedProfile(
            profileId: String = "OPT-PROFILE-WIDE-80",
            hardwareId: String = "HW-VR-CHASSIS-v1.1",
            focalLengthMm: Double = 45.0,
            pixelsPerDegree: Double = 13.5,
            horizontalFovDeg: Double = 80.0,
            maxLuminanceCdM2: Double = 450.0,
            refreshRateHz: Double = 60.0,
            opticalMode: String = "WIDE_FIELD",
            benchReport: OpticalBenchReport? = null
        ): DeviceProfile {
            val unsigned = DeviceProfile(
                profileId = profileId,
                hardwareId = hardwareId,
                focalLengthMm = focalLengthMm,
                pixelsPerDegree = pixelsPerDegree,
                horizontalFovDeg = horizontalFovDeg,
                maxLuminanceCdM2 = maxLuminanceCdM2,
                refreshRateHz = refreshRateHz,
                opticalMode = opticalMode,
                benchReport = benchReport
            )
            val sig = unsigned.computeDigest()
            return unsigned.copy(signature = sig)
        }

        fun createOnePlusNordCe3Profile(
            profileId: String = "OPT-PROFILE-NORD-CE3-120HZ",
            hardwareId: String = "HW-VR-CHASSIS-v1.1",
            focalLengthMm: Double = 45.0,
            pixelsPerDegree: Double = 15.08,
            horizontalFovDeg: Double = 80.0,
            maxLuminanceCdM2: Double = 950.0,
            refreshRateHz: Double = 120.0,
            opticalMode: String = "WIDE_FIELD",
            benchReport: OpticalBenchReport? = null
        ): DeviceProfile {
            return createSignedProfile(
                profileId = profileId,
                hardwareId = hardwareId,
                focalLengthMm = focalLengthMm,
                pixelsPerDegree = pixelsPerDegree,
                horizontalFovDeg = horizontalFovDeg,
                maxLuminanceCdM2 = maxLuminanceCdM2,
                refreshRateHz = refreshRateHz,
                opticalMode = opticalMode,
                benchReport = benchReport
            )
        }
    }

    fun loadAndVerifyProfile(profile: DeviceProfile, physicalHeadsetId: String): Result<DeviceProfile> {
        val expectedDigest = profile.computeDigest()
        if (profile.signature != expectedDigest) {
            return Result.failure(SecurityException("Device profile signature mismatch / tampered profile"))
        }

        if (profile.hardwareId != physicalHeadsetId) {
            return Result.failure(IllegalArgumentException(
                "Optics ID mismatch: profile registered for '${profile.hardwareId}' but physical headset tag is '$physicalHeadsetId'"
            ))
        }

        activeProfile = profile
        calibrationHash = profile.computeDigest().take(16)
        return Result.success(profile)
    }

    fun invalidateCalibration() {
        activeProfile = null
        calibrationHash = "INVALID"
    }
}
