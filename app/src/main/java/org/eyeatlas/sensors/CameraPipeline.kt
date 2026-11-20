package org.eyeatlas.sensors

import java.security.MessageDigest

/**
 * Hardware capability inspection, camera controls, quality gating,
 * and thermal benchmarking for the two-phone screening platform.
 */
enum class OpticalProfileStatus {
    SIMULATED_BENCH_PENDING,
    REAL_BENCH_VERIFIED,
    MISSING_OR_UNVERIFIED
}

data class CameraHardwareCapabilities(
    val cameraId: String,
    val facing: String, // "BACK" or "FRONT"
    val supportsManualExposure: Boolean,
    val supportsManualFocus: Boolean,
    val supportsManualWhiteBalance: Boolean,
    val supportsRawCapture: Boolean,
    val minExposureNs: Long = 100_000L,
    val maxExposureNs: Long = 1_000_000_000L,
    val minFocusDistanceDiopters: Float = 10.0f, // 10 D = 10 cm macro
    val maxIso: Int = 3200
)

data class PhoneHardwareMetrics(
    val phoneRole: String, // "PATIENT_VR" or "CLINICIAN_RESPONSE"
    val modelName: String,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val refreshRateHz: Double,
    val estimatedPpd: Double,
    val thermalHeadroomDegC: Double,
    val batteryTemperatureDegC: Double,
    val opticalProfileStatus: OpticalProfileStatus,
    val rearCamera: CameraHardwareCapabilities?,
    val frontCamera: CameraHardwareCapabilities?
)

data class ThermalStressRun(
    val durationMinutes: Int,
    val startTempDegC: Double,
    val endTempDegC: Double,
    val peakTempDegC: Double,
    val framesRendered: Long,
    val framesDropped: Long,
    val thermalThrottlingOccurred: Boolean,
    val survivedFullDuration: Boolean
)

data class TouchLatencyBenchmark(
    val sampleCount: Int,
    val meanLatencyMs: Double,
    val p95LatencyMs: Double,
    val p99LatencyMs: Double,
    val maxJitterMs: Double,
    val dropRate: Double
)

data class LockedCameraSettings(
    val exposureTimeNs: Long = 20_000_000L, // 20ms
    val iso: Int = 200,
    val whiteBalanceKelvin: Int = 5500,
    val focusDistanceDiopters: Float = 12.5f, // 8cm fixed working distance
    val isLocked: Boolean = true
)

data class RingIlluminationElectricalRecord(
    val recordId: String,
    val supplyVoltageV: Double = 5.0,
    val branchCount: Int = 8,
    val branchResistorOhms: Double = 330.0,
    val nominalVfVolts: Double = 3.0,
    val measuredBranchCurrentMa: Double = 6.06,
    val totalCurrentMa: Double = 48.48,
    val polyfuseHoldCurrentA: Double = 0.25,
    val operatingTempDegC: Double = 27.5,
    val isCircuitInsulated: Boolean = true,
    val isSeparatedFromRetinalPath: Boolean = true
) {
    fun verifyElectricalSafety(): Result<Boolean> {
        if (supplyVoltageV < 4.75 || supplyVoltageV > 5.25) {
            return Result.failure(IllegalStateException("Supply voltage $supplyVoltageV V outside allowed 5.0V +/- 0.25V range"))
        }
        if (branchCount != 8) {
            return Result.failure(IllegalStateException("Hardware contract requires exactly 8 individual LED branches (got $branchCount)"))
        }
        if (branchResistorOhms != 330.0) {
            return Result.failure(IllegalStateException("Hardware contract requires 330R current-limiting resistors (got $branchResistorOhms)"))
        }
        if (totalCurrentMa > 80.0) {
            return Result.failure(IllegalStateException("Total current $totalCurrentMa mA exceeds safe bench threshold 80.0 mA"))
        }
        if (operatingTempDegC > 35.0) {
            return Result.failure(IllegalStateException("Operating temperature $operatingTempDegC C exceeds 35.0 C limit"))
        }
        if (!isSeparatedFromRetinalPath) {
            return Result.failure(SecurityException("CRITICAL SAFETY VIOLATION: External ring illumination must never couple into retinal path"))
        }
        return Result.success(true)
    }

    fun verifyPlacidoRepeatability(variance: Double): Boolean {
        return variance < 0.05 // Under 5% distortion variance across repeated frames
    }
}

data class ImageQualityAssessment(
    val focusMetric: Double,
    val meanLuminance: Double,
    val glarePercentage: Double,
    val scaleMarkerPixelsPerMm: Double,
    val qualityScore: Double,
    val isPassed: Boolean,
    val failureReasons: List<String>
)

class CameraPipeline {
    var isInitialized: Boolean = false
        private set

    private var activeMetrics: PhoneHardwareMetrics? = null

    fun initialize(): Boolean {
        isInitialized = true
        return true
    }

    companion object {
        fun computeSha256(rawBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(rawBytes).joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Probes phone capabilities. Explicitly flags that real optical bench profiles
     * are pending verification until physical calibration (P17).
     */
    fun probeHardwareCapabilities(
        phoneRole: String,
        modelName: String = "Generic-Android-Phone",
        displayWidthPx: Int = 2400,
        displayHeightPx: Int = 1080,
        refreshRateHz: Double = 60.0,
        batteryTempDegC: Double = 31.5,
        opticalProfileStatus: OpticalProfileStatus = OpticalProfileStatus.SIMULATED_BENCH_PENDING
    ): PhoneHardwareMetrics {
        val estimatedPpd = (displayWidthPx / 2.0) / 80.0 // nominal 80 deg FOV

        val rearCam = CameraHardwareCapabilities(
            cameraId = "0",
            facing = "BACK",
            supportsManualExposure = true,
            supportsManualFocus = true,
            supportsManualWhiteBalance = true,
            supportsRawCapture = true,
            minExposureNs = 50_000L,
            maxExposureNs = 500_000_000L,
            minFocusDistanceDiopters = 12.5f // 8 cm macro capable
        )

        val frontCam = CameraHardwareCapabilities(
            cameraId = "1",
            facing = "FRONT",
            supportsManualExposure = false,
            supportsManualFocus = false,
            supportsManualWhiteBalance = false,
            supportsRawCapture = false
        )

        val metrics = PhoneHardwareMetrics(
            phoneRole = phoneRole,
            modelName = modelName,
            displayWidthPx = displayWidthPx,
            displayHeightPx = displayHeightPx,
            refreshRateHz = refreshRateHz,
            estimatedPpd = estimatedPpd,
            thermalHeadroomDegC = 45.0 - batteryTempDegC,
            batteryTemperatureDegC = batteryTempDegC,
            opticalProfileStatus = opticalProfileStatus,
            rearCamera = rearCam,
            frontCamera = frontCam
        )
        activeMetrics = metrics
        return metrics
    }

    /**
     * Evaluates external/retinal capture quality against blur, exposure, glare, and scale.
     */
    fun evaluateCaptureQuality(
        focusMetric: Double,
        meanLuminance: Double,
        glarePercentage: Double,
        scaleMarkerPixelsPerMm: Double = 25.0
    ): ImageQualityAssessment {
        val failures = mutableListOf<String>()

        // 1. Sharpness / Blur check
        if (focusMetric < 15.0) {
            failures.add("BLUR_EXCEEDS_LIMIT: focus metric $focusMetric below threshold 15.0")
        }

        // 2. Exposure check (acceptable mean luminance 40.0..220.0)
        if (meanLuminance < 40.0) {
            failures.add("UNDEREXPOSED: mean luminance $meanLuminance < 40.0")
        } else if (meanLuminance > 220.0) {
            failures.add("OVEREXPOSED: mean luminance $meanLuminance > 220.0")
        }

        // 3. Glare saturation check (maximum 5.0% saturated pixels allowed)
        if (glarePercentage > 5.0) {
            failures.add("EXCESSIVE_GLARE: glare ratio $glarePercentage% > 5.0% threshold")
        }

        // 4. Physical scale marker check
        if (scaleMarkerPixelsPerMm <= 0.0) {
            failures.add("SCALE_MARKER_MISSING: invalid pixels per mm calibration")
        }

        val isPassed = failures.isEmpty()
        val qualityScore = if (isPassed) {
            val focusNorm = (focusMetric / 50.0).coerceIn(0.0, 1.0)
            val expNorm = (1.0 - Math.abs(meanLuminance - 128.0) / 128.0).coerceIn(0.0, 1.0)
            (focusNorm * 0.6 + expNorm * 0.4)
        } else {
            0.0
        }

        return ImageQualityAssessment(
            focusMetric = focusMetric,
            meanLuminance = meanLuminance,
            glarePercentage = glarePercentage,
            scaleMarkerPixelsPerMm = scaleMarkerPixelsPerMm,
            qualityScore = (qualityScore * 100.0).toInt() / 100.0,
            isPassed = isPassed,
            failureReasons = failures
        )
    }

    /**
     * Simulates a 30-minute stress run to ensure no overheating (>45C) or crashing.
     */
    fun runSimulatedThermalStress(durationMinutes: Int = 30, startingTempDegC: Double = 31.0): ThermalStressRun {
        require(durationMinutes > 0) { "Stress duration must be positive" }
        var currentTemp = startingTempDegC
        var peakTemp = startingTempDegC
        var dropped = 0L
        val totalFrames = durationMinutes * 60L * 60L

        for (minute in 1..durationMinutes) {
            val rise = if (currentTemp < 38.5) 0.06 else 0.01
            currentTemp += rise
            if (currentTemp > peakTemp) peakTemp = currentTemp
            if (minute % 10 == 0) dropped += 2
        }

        val throttled = peakTemp >= 45.0
        return ThermalStressRun(
            durationMinutes = durationMinutes,
            startTempDegC = startingTempDegC,
            endTempDegC = (currentTemp * 10.0).toInt() / 10.0,
            peakTempDegC = (peakTemp * 10.0).toInt() / 10.0,
            framesRendered = totalFrames - dropped,
            framesDropped = dropped,
            thermalThrottlingOccurred = throttled,
            survivedFullDuration = !throttled
        )
    }

    /**
     * Touchscreen event capture latency benchmark.
     */
    fun benchmarkTouchLatency(sampleIntervalsMs: List<Double>): TouchLatencyBenchmark {
        require(sampleIntervalsMs.isNotEmpty()) { "Benchmark requires non-empty sample intervals" }
        val sorted = sampleIntervalsMs.sorted()
        val mean = sorted.average()
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
        val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]
        val maxJitter = sorted.last() - sorted.first()

        return TouchLatencyBenchmark(
            sampleCount = sampleIntervalsMs.size,
            meanLatencyMs = (mean * 100.0).toInt() / 100.0,
            p95LatencyMs = p95,
            p99LatencyMs = p99,
            maxJitterMs = (maxJitter * 100.0).toInt() / 100.0,
            dropRate = 0.0
        )
    }
}
