package org.eyeatlas

import org.eyeatlas.ai.Inference
import org.eyeatlas.ai.InferenceRuntime
import org.eyeatlas.ai.RuntimeBenchmark
import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.StimulusTrial
import org.eyeatlas.sensors.Calibration
import org.eyeatlas.sensors.DeviceProfile
import org.eyeatlas.sensors.VrPresenter
import org.junit.Assert.*
import org.junit.Test

class CalibrationTests {

    private val baseTrial = StimulusTrial(
        trialId = "TR-CAL-001",
        sessionId = "SESSION-CAL-001",
        eye = EyeLaterality.OD,
        testCode = "PERIMETRY_54PT",
        targetAngleDeg = 15.0, // 15 degrees right
        luminanceCdM2 = 100.0,
        opticalIndex = 1.0,
        monotonicPresentedNs = 1_000_000_000L,
        expectedAnswer = "SEEN",
        framesRendered = 60,
        calibrationHash = "CAL-P01-BASELINE"
    )

    @Test
    fun baselineCalibrationVerification() {
        val cal = Calibration()
        assertNotNull(cal.calibrationHash)
    }

    @Test
    fun stimulusAngularMathAndPpdConversionTest() {
        val calibration = Calibration()
        val profile = Calibration.createSignedProfile(
            pixelsPerDegree = 13.5,
            horizontalFovDeg = 80.0
        )
        calibration.loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")

        val presenter = VrPresenter(calibration, eyeCenterOffsetPx = 540f)

        // Center target (0, 0)
        val centerCoord = presenter.computePixelCoordinate(0.0, 0.0)
        assertEquals(540f, centerCoord.xPx, 0.01f)
        assertEquals(540f, centerCoord.yPx, 0.01f)
        assertTrue(centerCoord.isVisibleInFov)

        // 15 degrees horizontal offset: 15.0 deg * 13.5 PPD = 202.5 px offset
        val offsetCoord = presenter.computePixelCoordinate(15.0, 0.0)
        assertEquals(540f + 202.5f, offsetCoord.xPx, 0.1f)
        assertEquals(540f, offsetCoord.yPx, 0.1f)
        assertTrue(offsetCoord.isVisibleInFov)

        // Out-of-bounds target: 50 degrees (> 40 degrees half-FOV)
        val outOfBoundsCoord = presenter.computePixelCoordinate(50.0, 0.0)
        assertFalse(outOfBoundsCoord.isVisibleInFov)
    }

    @Test
    fun opticsProfileIdMismatchRejectionTest() {
        val calibration = Calibration()
        val profile = Calibration.createSignedProfile(
            profileId = "OPT-PROFILE-WIDE-80",
            hardwareId = "HW-VR-CHASSIS-v1.1"
        )

        // Physical headset tag mismatch: "HW-DIFFERENT-UNVERIFIED"
        val mismatchResult = calibration.loadAndVerifyProfile(profile, "HW-DIFFERENT-UNVERIFIED")
        assertTrue(mismatchResult.isFailure)
        assertTrue(mismatchResult.exceptionOrNull() is IllegalArgumentException)
        assertFalse(calibration.isCalibrated)
    }

    @Test
    fun eyeMapMonocularSeparationTest() {
        val calibration = Calibration()
        val profile = Calibration.createSignedProfile()
        calibration.loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        val presenter = VrPresenter(calibration)

        // Render OD stimulus
        val odFrame = presenter.renderStimulus(baseTrial)
        assertEquals(EyeLaterality.OD, odFrame.testedEye)
        assertTrue("Non-tested OS channel must be blanked", odFrame.isOccludedEyeBlanked)

        // Render OS stimulus
        val osTrial = baseTrial.copy(trialId = "TR-CAL-OS", eye = EyeLaterality.OS)
        val osFrame = presenter.renderStimulus(osTrial)
        assertEquals(EyeLaterality.OS, osFrame.testedEye)
        assertTrue("Non-tested OD channel must be blanked", osFrame.isOccludedEyeBlanked)
    }

    @Test
    fun realFrameTimestampRenderingTest() {
        val calibration = Calibration()
        val profile = Calibration.createSignedProfile()
        calibration.loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        val presenter = VrPresenter(calibration)

        val scheduledTimeNs = 1_000_000_000L
        val realFrameTimeNs = 1_016_666_666L // Actual VSYNC timestamp 16.6ms later

        val frame = presenter.renderStimulus(
            trial = baseTrial,
            scheduledTimestampNs = scheduledTimeNs,
            actualPresentedMonotonicNs = realFrameTimeNs
        )

        // Monotonic timestamp must record real frame time, not scheduled time
        assertEquals(realFrameTimeNs, frame.actualFrameTimestampNs)
        assertNotEquals(scheduledTimeNs, frame.actualFrameTimestampNs)
    }

    @Test
    fun fakeMonitorConfigRejectionTest() {
        // Zero PPD must fail
        try {
            DeviceProfile("ID", "HW", 45.0, 0.0, 80.0, 450.0, 60.0)
            fail("Expected failure on zero PPD")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("positive"))
        }

        // Negative luminance must fail
        try {
            DeviceProfile("ID", "HW", 45.0, 13.5, 80.0, -100.0, 60.0)
            fail("Expected failure on negative luminance")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("positive"))
        }

        // Low refresh rate must fail
        try {
            DeviceProfile("ID", "HW", 45.0, 13.5, 80.0, 450.0, 10.0)
            fail("Expected failure on low refresh rate")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("30 Hz"))
        }
    }

    @Test
    fun failureStatePropagationTest() {
        val uncalibrated = Calibration() // No profile loaded
        val presenter = VrPresenter(uncalibrated)

        val frame = presenter.renderStimulus(baseTrial)
        assertFalse(frame.isValid)
        assertEquals("CALIBRATION_PROFILE_MISSING", frame.failureReason)
    }

    @Test
    fun runtimeBenchmarkSelectionTest() {
        val inference = Inference()
        val benchmarks = listOf(
            RuntimeBenchmark(InferenceRuntime.CPU_FALLBACK, latencyMs = 85.0, memoryMb = 12.0, isStable = true),
            RuntimeBenchmark(InferenceRuntime.TFLITE_DELEGATE, latencyMs = 24.0, memoryMb = 18.0, isStable = true),
            RuntimeBenchmark(InferenceRuntime.ONNX_RUNTIME, latencyMs = 15.0, memoryMb = 25.0, isStable = false) // Unstable
        )

        val selected = inference.benchmarkAndSelectRuntime(benchmarks)
        // Must select fastest STABLE runtime (TFLite at 24ms, ignoring unstable ONNX at 15ms)
        assertEquals(InferenceRuntime.TFLITE_DELEGATE, selected)
    }

    @Test
    fun probeHardwareCapabilitiesAndMissingProfileStatusTest() {
        val pipeline = org.eyeatlas.sensors.CameraPipeline()
        assertTrue(pipeline.initialize())

        val probe = pipeline.probeHardwareCapabilities(
            phoneRole = "PATIENT_VR",
            modelName = "Pixel-Test-Bench",
            displayWidthPx = 2400,
            displayHeightPx = 1080,
            refreshRateHz = 90.0,
            batteryTempDegC = 32.0,
            opticalProfileStatus = org.eyeatlas.sensors.OpticalProfileStatus.SIMULATED_BENCH_PENDING
        )

        assertEquals("PATIENT_VR", probe.phoneRole)
        assertEquals("Pixel-Test-Bench", probe.modelName)
        assertEquals(90.0, probe.refreshRateHz, 0.01)
        // Hard medical invariant: Missing real optical bench profile must be explicitly flagged
        assertEquals(org.eyeatlas.sensors.OpticalProfileStatus.SIMULATED_BENCH_PENDING, probe.opticalProfileStatus)
        assertNotNull(probe.rearCamera)
        assertTrue(probe.rearCamera!!.supportsManualExposure)
        assertTrue(probe.rearCamera!!.supportsManualFocus)
    }

    @Test
    fun simulatedThermalStress30MinTest() {
        val pipeline = org.eyeatlas.sensors.CameraPipeline()
        val stress = pipeline.runSimulatedThermalStress(durationMinutes = 30, startingTempDegC = 31.5)

        assertEquals(30, stress.durationMinutes)
        assertTrue("Stress run must survive full 30 minutes without crash", stress.survivedFullDuration)
        assertFalse("Device must not throttle under 40 deg C", stress.thermalThrottlingOccurred)
        assertTrue("Peak temperature must remain under 45C limit", stress.peakTempDegC < 45.0)
        assertTrue(stress.framesDropped < 100L)
    }

    @Test
    fun touchLatencyBenchmarkDistributionTest() {
        val pipeline = org.eyeatlas.sensors.CameraPipeline()
        val latencies = listOf(12.5, 14.0, 15.2, 16.0, 18.1, 14.5, 13.8, 22.0, 15.0, 14.8)
        val benchmark = pipeline.benchmarkTouchLatency(latencies)

        assertEquals(10, benchmark.sampleCount)
        assertTrue("Mean touch latency should be within 10-20 ms range", benchmark.meanLatencyMs in 10.0..20.0)
        assertTrue("P95 latency should be less than 25 ms", benchmark.p95LatencyMs < 25.0)
        assertEquals(0.0, benchmark.dropRate, 0.001)
    }

    @Test
    fun crossPhoneClockSyncUncertaintyGateTest() {
        val engine = org.eyeatlas.net.ClockSyncEngine(maxAllowedUncertaintyNs = 50_000_000L) // 50ms tolerance

        // Valid sync: round-trip 10ms -> uncertainty 5ms (< 50ms)
        engine.addSample(
            org.eyeatlas.net.ClockSyncSample(
                sampleId = "SYNC-001",
                t0Ns = 1_000_000_000L,
                t1Ns = 1_004_000_000L,
                t2Ns = 1_005_000_000L,
                t3Ns = 1_010_000_000L
            )
        )
        assertTrue("Clock sync uncertainty must pass gate", engine.isUncertaintyAcceptable())
        assertTrue(engine.currentUncertaintyNs <= 50_000_000L)

        // Reset and test invalid high-jitter sync: round-trip 200ms -> uncertainty 100ms (> 50ms)
        val jitterEngine = org.eyeatlas.net.ClockSyncEngine(maxAllowedUncertaintyNs = 50_000_000L)
        jitterEngine.addSample(
            org.eyeatlas.net.ClockSyncSample(
                sampleId = "SYNC-HIGH-JITTER",
                t0Ns = 1_000_000_000L,
                t1Ns = 1_090_000_000L,
                t2Ns = 1_100_000_000L,
                t3Ns = 1_200_000_000L
            )
        )
        assertFalse("High jitter sync must fail uncertainty gate", jitterEngine.isUncertaintyAcceptable())
        assertTrue(jitterEngine.currentUncertaintyNs > 50_000_000L)
    }

    @Test
    fun opticalBenchReportRealMeasurementsTest() {
        // Standard wide-field cardboard optical bench report
        val wideReport = org.eyeatlas.sensors.OpticalBenchReport(
            reportId = "BENCH-REPORT-WIDE-01",
            benchId = "OPT-BENCH-COL-01",
            hardwareId = "HW-VR-CHASSIS-v1.1",
            measuredFovDeg = 80.0,
            measuredPpd = 13.5, // Low PPD
            mtf50AtNyquist = 0.42,
            peakLuminanceCdM2 = 450.0,
            edgeLuminanceRatio = 0.72,
            cieWhitePointX = 0.3127,
            cieWhitePointY = 0.3290,
            isOpticalInsertFitted = false
        )

        assertTrue("Must meet D65 white-point spec", wideReport.isD65Compliant)
        assertTrue("Must meet MTF contrast threshold", wideReport.isMtfAcceptable)
        // Hard medical invariant: Low PPD cannot resolve 6/6 acuity
        assertFalse("13.5 PPD must not claim 6/6 resolution", wideReport.is66AcuityResolvable)

        val profile = org.eyeatlas.sensors.Calibration.createSignedProfile(
            benchReport = wideReport
        )
        assertFalse(profile.canResolve66Acuity)
    }

    @Test
    fun highPpdAcuityInsertBenchTest() {
        // High-acuity optical insert bench report
        val insertReport = org.eyeatlas.sensors.OpticalBenchReport(
            reportId = "BENCH-REPORT-INSERT-01",
            benchId = "OPT-BENCH-COL-01",
            hardwareId = "HW-VR-CHASSIS-v1.1",
            measuredFovDeg = 10.0,
            measuredPpd = 120.0, // High PPD meets 6/6 requirement
            mtf50AtNyquist = 0.38,
            peakLuminanceCdM2 = 420.0,
            edgeLuminanceRatio = 0.85,
            cieWhitePointX = 0.3130,
            cieWhitePointY = 0.3285,
            isOpticalInsertFitted = true
        )

        assertTrue(insertReport.isMtfAcceptable)
        assertTrue("High PPD with insert and MTF >= 0.35 resolves 6/6", insertReport.is66AcuityResolvable)

        val profile = org.eyeatlas.sensors.Calibration.createSignedProfile(
            pixelsPerDegree = 120.0,
            horizontalFovDeg = 10.0,
            opticalMode = "HIGH_PPD_INSERT",
            benchReport = insertReport
        )
        assertTrue(profile.canResolve66Acuity)
    }

    @Test
    fun mechanicalIpdEnvelopeAndStopRejectionTest() {
        val report = org.eyeatlas.sensors.OpticalBenchReport(
            reportId = "BENCH-01",
            benchId = "B-01",
            hardwareId = "HW-01",
            measuredFovDeg = 80.0,
            measuredPpd = 13.5,
            mtf50AtNyquist = 0.40,
            peakLuminanceCdM2 = 400.0,
            edgeLuminanceRatio = 0.70,
            cieWhitePointX = 0.3127,
            cieWhitePointY = 0.3290,
            ipdMinMm = 48.0,
            ipdMaxMm = 74.0
        )

        // Valid patient IPD
        assertTrue(report.validatePatientIpd(63.0).isSuccess)

        // Out-of-bounds narrow IPD (< 48mm)
        val narrow = report.validatePatientIpd(44.0)
        assertTrue(narrow.isFailure)
        assertTrue(narrow.exceptionOrNull() is IllegalArgumentException)

        // Out-of-bounds wide IPD (> 74mm)
        val wide = report.validatePatientIpd(78.0)
        assertTrue(wide.isFailure)
        assertTrue(wide.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun unsupportedFieldCoordinateDisabledTest() {
        val calibration = org.eyeatlas.sensors.Calibration()
        val profile = org.eyeatlas.sensors.Calibration.createSignedProfile(
            horizontalFovDeg = 80.0 // half-FOV is 40.0 deg
        )
        calibration.loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        val presenter = org.eyeatlas.sensors.VrPresenter(calibration)

        // 55 degree horizontal eccentricity is outside 40 deg half-FOV
        val outOfBoundsTrial = baseTrial.copy(targetAngleDeg = 55.0)
        val frame = presenter.renderStimulus(outOfBoundsTrial)

        assertFalse("Stimulus outside optical FOV must be marked invalid/disabled", frame.isValid)
        assertEquals("COORDINATE_OUTSIDE_CALIBRATED_FOV", frame.failureReason)
    }

    @Test
    fun ringIllumination5VElectricalSafetySpecTest() {
        // Spec-compliant 5V 8x330R ring illumination circuit
        val record = org.eyeatlas.sensors.RingIlluminationElectricalRecord(
            recordId = "ELEC-RING-BENCH-01",
            supplyVoltageV = 5.0,
            branchCount = 8,
            branchResistorOhms = 330.0,
            measuredBranchCurrentMa = 6.06,
            totalCurrentMa = 48.48,
            polyfuseHoldCurrentA = 0.25,
            operatingTempDegC = 27.2,
            isCircuitInsulated = true,
            isSeparatedFromRetinalPath = true
        )

        val result = record.verifyElectricalSafety()
        assertTrue("Spec-compliant 5V 8x330R circuit must pass safety check", result.isSuccess)
        assertEquals(48.48, record.totalCurrentMa, 0.01)
        assertTrue("Polyfuse hold rating must protect against overcurrent", record.polyfuseHoldCurrentA == 0.25)
    }

    @Test
    fun ringIlluminationCircuitFaultRejectionTest() {
        // 1. Overvoltage fault (6.2V)
        val overVolt = org.eyeatlas.sensors.RingIlluminationElectricalRecord(
            recordId = "FAULT-VOLT",
            supplyVoltageV = 6.2
        )
        val resVolt = overVolt.verifyElectricalSafety()
        assertTrue(resVolt.isFailure)
        assertTrue(resVolt.exceptionOrNull() is IllegalStateException)

        // 2. Non-compliant common resistor topology (e.g. wrong branch resistor)
        val wrongResistor = org.eyeatlas.sensors.RingIlluminationElectricalRecord(
            recordId = "FAULT-RES",
            branchResistorOhms = 100.0 // Wrong resistance
        )
        val resResistor = wrongResistor.verifyElectricalSafety()
        assertTrue(resResistor.isFailure)
        assertTrue(resResistor.exceptionOrNull() is IllegalStateException)

        // 3. Thermal fault (> 35C)
        val hotRecord = org.eyeatlas.sensors.RingIlluminationElectricalRecord(
            recordId = "FAULT-TEMP",
            operatingTempDegC = 42.0
        )
        val resHot = hotRecord.verifyElectricalSafety()
        assertTrue(resHot.isFailure)

        // 4. Critical safety violation: coupling into retinal path
        val retinalCoupled = org.eyeatlas.sensors.RingIlluminationElectricalRecord(
            recordId = "FAULT-RETINA",
            isSeparatedFromRetinalPath = false
        )
        val resRetina = retinalCoupled.verifyElectricalSafety()
        assertTrue(resRetina.isFailure)
        assertTrue(resRetina.exceptionOrNull() is SecurityException)
    }

    @Test
    fun placidoRingOpticalRepeatabilityAndGlareControlTest() {
        val record = org.eyeatlas.sensors.RingIlluminationElectricalRecord("BENCH-OPT-01")

        // Good repeatability (variance 0.018 < 0.05)
        assertTrue(record.verifyPlacidoRepeatability(0.018))

        // Poor repeatability / excessive glare distortion (variance 0.082 >= 0.05)
        assertFalse(record.verifyPlacidoRepeatability(0.082))
    }

    @Test
    fun onePlusNordCe3CalibrationProfileAndGeometryTest() {
        val profile = org.eyeatlas.sensors.Calibration.createOnePlusNordCe3Profile()
        assertEquals("OPT-PROFILE-NORD-CE3-120HZ", profile.profileId)
        assertEquals(120.0, profile.refreshRateHz, 0.001)
        assertEquals(15.08, profile.pixelsPerDegree, 0.01)
        assertEquals(950.0, profile.maxLuminanceCdM2, 0.1)
        assertEquals(80.0, profile.horizontalFovDeg, 0.1)

        val calibration = org.eyeatlas.sensors.Calibration()
        val loadResult = calibration.loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        assertTrue(loadResult.isSuccess)
        assertTrue(calibration.isCalibrated)

        // Verify OnePlus Nord CE 3 Presenter Geometry (1206x1080 per eye viewport, center at 603, 540)
        val presenter = org.eyeatlas.sensors.VrPresenter(calibration).withNordCe3Layout()
        assertEquals(603f, presenter.eyeCenterOffsetPx, 0.01f)
        assertEquals(540f, presenter.eyeCenterOffsetYPx, 0.01f)

        // Center target (0, 0)
        val center = presenter.computePixelCoordinate(0.0, 0.0)
        assertEquals(603f, center.xPx, 0.01f)
        assertEquals(540f, center.yPx, 0.01f)
        assertTrue(center.isVisibleInFov)
    }

    @Test
    fun onePlusNordCe3PerimetryTimingAndBlindSpotTest() {
        // Stimulus timing: 200ms Goldmann III = exactly 24 frames at 120Hz
        val frames120Hz = org.eyeatlas.vision.FunctionalTests.calculateStimulusFrames(120.0)
        assertEquals(24, frames120Hz)

        val frames60Hz = org.eyeatlas.vision.FunctionalTests.calculateStimulusFrames(60.0)
        assertEquals(12, frames60Hz)

        // 240Hz touch sampling rate = 4.167 ms interval
        val touchIntervalMs = org.eyeatlas.vision.FunctionalTests.getTouchSamplingIntervalMs(240.0)
        assertEquals(4.167, touchIntervalMs, 0.01)

        // Heijl-Krakau Blind Spot: Right eye (OD) +15 deg temporal, -1.5 deg vertical
        val blindSpotOD = org.eyeatlas.vision.FunctionalTests.getBlindSpotCoordinates(org.eyeatlas.core.EyeLaterality.OD)
        assertEquals(15.0, blindSpotOD.first, 0.01)
        assertEquals(-1.5, blindSpotOD.second, 0.01)

        // Heijl-Krakau Blind Spot: Left eye (OS) -15 deg temporal, -1.5 deg vertical
        val blindSpotOS = org.eyeatlas.vision.FunctionalTests.getBlindSpotCoordinates(org.eyeatlas.core.EyeLaterality.OS)
        assertEquals(-15.0, blindSpotOS.first, 0.01)
        assertEquals(-1.5, blindSpotOS.second, 0.01)
    }
}
