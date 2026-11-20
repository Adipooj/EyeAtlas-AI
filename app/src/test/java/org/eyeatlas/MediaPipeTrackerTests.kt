package org.eyeatlas

import org.eyeatlas.ai.EyeTrackingOpticalStatus
import org.eyeatlas.ai.IrisLandmark
import org.eyeatlas.ai.MediaPipeTracker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MediaPipeTrackerTests {

    private lateinit var tracker: MediaPipeTracker

    @Before
    fun setup() {
        tracker = MediaPipeTracker(useGpuDelegate = true)
        tracker.calibrateCameraGeometry(
            isFrontFacing = true,
            widthPx = 1080,
            heightPx = 2400,
            focalLength = 1400.0f
        )
    }

    @Test
    fun testPhysicalOcclusionGateByVrBoxCentralDivider() {
        // When enclosed inside VRBox, front punch-hole camera is occluded by the central divider
        val frame = tracker.processFrame(isOccludedByVrDivider = true)

        assertEquals(EyeTrackingOpticalStatus.OPTICALLY_OCCLUDED_BY_VRBOX, frame.opticalStatus)
        assertNull("Iris center must be null when camera is occluded", frame.rightIrisCenter)
        assertNull("Iris center must be null when camera is occluded", frame.leftIrisCenter)
        assertEquals(0.0f, frame.trackingConfidence, 0.001f)
        assertTrue(frame.clinicalNotice.contains("VRBox Central Divider Occlusion"))
        assertTrue("Hard clinical gate must always be active", frame.isClinicalDecisionGated)
    }

    @Test
    fun testInsufficientIlluminationGate() {
        val frame = tracker.processFrame(
            isOccludedByVrDivider = false,
            ambientLuminanceLux = 0.2f // Dark chamber
        )

        assertEquals(EyeTrackingOpticalStatus.INSUFFICIENT_ILLUMINATION, frame.opticalStatus)
        assertNull(frame.rightIrisCenter)
        assertTrue(frame.trackingConfidence < 0.1f)
    }

    @Test
    fun testLandmarkMirroringAndGeometry() {
        val landmarks = listOf(
            IrisLandmark(468, 0.70f, 0.40f, -0.02f), // Right eye
            IrisLandmark(473, 0.30f, 0.40f, -0.02f)  // Left eye
        )

        val frame = tracker.processFrame(
            mockLandmarks = landmarks,
            isOccludedByVrDivider = false,
            ambientLuminanceLux = 200.0f
        )

        assertEquals(EyeTrackingOpticalStatus.OPTIMAL_FRONTAL, frame.opticalStatus)
        assertNotNull(frame.rightIrisCenter)
        // Since mirrorX = true (front camera), x = 1.0 - 0.70 = 0.30
        assertEquals(0.30f, frame.rightIrisCenter!!.x, 0.001f)
        assertEquals(0.40f, frame.rightIrisCenter!!.y, 0.001f)
    }

    @Test
    fun testEyelidApertureAndBlinkDetection() {
        // Normal open eye: Top Y = 0.38, Bottom Y = 0.46 (delta = 0.08 * 2400 = 192px)
        // With iris diameter 230px (~11.7mm), aperture is approx 9.8mm
        val openApertureMm = tracker.calculatePalpebralApertureMm(
            upperLidY = 0.38f,
            lowerLidY = 0.46f,
            irisDiameterPixels = 230.0f
        )
        assertTrue("Normal adult aperture should be ~8-11mm", openApertureMm in 8.0f..11.5f)

        // Closed eye / Blink: Top Y = 0.42, Bottom Y = 0.425 (delta = 0.005 * 2400 = 12px)
        val closedApertureMm = tracker.calculatePalpebralApertureMm(
            upperLidY = 0.42f,
            lowerLidY = 0.425f,
            irisDiameterPixels = 230.0f
        )
        assertTrue("Blink aperture should be < 2.0mm", closedApertureMm < 2.0f)
    }

    @Test
    fun testFixationSpreadBceaCalculation() {
        // 1. Tightly clustered points around (0, 0) -> small BCEA
        val tightPoints = mutableListOf<Pair<Float, Float>>()
        repeat(20) { i ->
            tightPoints.add(Pair(0.1f * (i % 3), 0.1f * (i % 2)))
        }
        val tightBcea = tracker.calculateBceaDeg2(tightPoints)
        assertNotNull(tightBcea)
        assertTrue("Tight fixation should produce BCEA < 0.5 deg²", tightBcea!! < 0.5f)

        // 2. Widely dispersed points (unsteady fixation / nystagmus)
        val dispersedPoints = mutableListOf<Pair<Float, Float>>()
        repeat(20) { i ->
            dispersedPoints.add(Pair(1.5f * (i % 5) - 3.0f, 1.2f * (i % 4) - 2.0f))
        }
        val wideBcea = tracker.calculateBceaDeg2(dispersedPoints)
        assertNotNull(wideBcea)
        assertTrue("Dispersed fixation should produce larger BCEA", wideBcea!! > tightBcea)
    }

    @Test
    fun testHardClinicalGovernanceAssertion() {
        val assertion = tracker.getClinicalSafetyAssertion()
        assertTrue(assertion.contains("HARD CLINICAL SAFETY GATE"))
        assertTrue(assertion.contains("DO NOT claim that MediaPipe directly provides true clinical gaze angles"))
        assertTrue(assertion.contains("CDSCO MDR-2017 Class B SaMD"))

        // Frame level gate check
        val frame = tracker.processFrame(isOccludedByVrDivider = false, ambientLuminanceLux = 100.0f)
        assertTrue("Clinical gate must remain strictly enabled", frame.isClinicalDecisionGated)
    }

    @Test
    fun testBenchmarkTelemetry() {
        repeat(5) {
            tracker.processFrame(isOccludedByVrDivider = false, ambientLuminanceLux = 100.0f)
        }
        tracker.setThermalThrottling(false)

        val bench = tracker.getBenchmarkTelemetry()
        assertEquals(5L, bench.framesProcessed)
        assertTrue(bench.gpuDelegateEnabled)
        assertFalse(bench.thermalThrottlingDetected)
        assertTrue(bench.averageProcessingLatencyMs >= 0.0)
    }
}
