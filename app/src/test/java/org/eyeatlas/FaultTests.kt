package org.eyeatlas

import org.eyeatlas.core.*
import org.eyeatlas.net.*
import org.eyeatlas.sensors.ResponseAndGaze
import org.junit.Assert.*
import org.junit.Test

class FaultTests {

    private val baseTrial = StimulusTrial(
        trialId = "TR-P02-001",
        sessionId = "SESSION-RESEARCH-001",
        eye = EyeLaterality.OD,
        testCode = "PERIMETRY_54PT",
        targetAngleDeg = 15.0,
        luminanceCdM2 = 10.0,
        opticalIndex = 1.0,
        monotonicPresentedNs = 1_000_000_000L,
        expectedAnswer = "SEEN",
        framesRendered = 60,
        calibrationHash = "CAL-P01-BASELINE"
    )

    private fun getSynchronizedEngine(uncertaintyNs: Long = 10_000_000L): ClockSyncEngine {
        val sync = ClockSyncEngine()
        // t0 = 100ms, t1 = 105ms, t2 = 106ms, t3 = 111ms
        // RTT = (111 - 100) - (106 - 105) = 10ms -> uncertainty = 5ms
        sync.addSample(
            ClockSyncSample(
                sampleId = "SYNC-01",
                t0Ns = 100_000_000L,
                t1Ns = 100_000_000L + 20_000_000L, // offset +20ms
                t2Ns = 101_000_000L + 20_000_000L,
                t3Ns = 100_000_000L + (uncertaintyNs * 2)
            )
        )
        return sync
    }

    @Test
    fun baselineFaultVerification() {
        val transport = PairingAndTransport()
        assertEquals(TransportState.UNPAIRED, transport.getState())
    }

    @Test
    fun packetReorderAndDropInjectionTest() {
        val transport = PairingAndTransport(
            localDeviceId = "PHONE-PATIENT-VR",
            expectedSessionId = "SESSION-RESEARCH-001",
            expectedPeerDeviceId = "PHONE-CLINICIAN-PAD",
            sharedSecretKey = "SECRET-KEY-EYEATLAS"
        )
        val payload = transport.initiatePairing()
        assertTrue(transport.completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD"))

        // Packet 1 (valid)
        val p1 = SecureTransportPacket.createSigned(1, "SESSION-RESEARCH-001", "PHONE-CLINICIAN-PAD", "HEARTBEAT", "OK", "SECRET-KEY-EYEATLAS")
        assertTrue(transport.processIncomingPacket(p1).isSuccess)

        // Inject packet drop: skip sequence 2 and 3, send sequence 4
        val p4 = SecureTransportPacket.createSigned(4, "SESSION-RESEARCH-001", "PHONE-CLINICIAN-PAD", "HEARTBEAT", "OK", "SECRET-KEY-EYEATLAS")
        assertTrue(transport.processIncomingPacket(p4).isSuccess)

        assertEquals(2, transport.totalPacketsReceived)
        assertEquals(2, transport.droppedPacketsDetected)
        assertEquals(1, transport.reorderedPacketsDetected)
    }

    @Test
    fun wrongDeviceAndSessionInjectionTest() {
        val transport = PairingAndTransport(
            localDeviceId = "PHONE-PATIENT-VR",
            expectedSessionId = "SESSION-RESEARCH-001",
            expectedPeerDeviceId = "PHONE-CLINICIAN-PAD",
            sharedSecretKey = "SECRET-KEY-EYEATLAS"
        )
        val payload = transport.initiatePairing()
        transport.completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD")

        // 1. Injected wrong session ID
        val pWrongSession = SecureTransportPacket.createSigned(1, "WRONG-SESSION-999", "PHONE-CLINICIAN-PAD", "HEARTBEAT", "OK", "SECRET-KEY-EYEATLAS")
        val resSession = transport.processIncomingPacket(pWrongSession)
        assertTrue(resSession.isFailure)
        assertTrue(resSession.exceptionOrNull() is SecurityException)

        // 2. Injected wrong device ID (unpinned peer)
        val pWrongDevice = SecureTransportPacket.createSigned(2, "SESSION-RESEARCH-001", "ROGUE-PHONE-ATTACKER", "HEARTBEAT", "OK", "SECRET-KEY-EYEATLAS")
        val resDevice = transport.processIncomingPacket(pWrongDevice)
        assertTrue(resDevice.isFailure)
        assertTrue(resDevice.exceptionOrNull() is SecurityException)
    }

    @Test
    fun duplicateSequenceReplayInjectionTest() {
        val transport = PairingAndTransport()
        val payload = transport.initiatePairing()
        transport.completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD")

        val p1 = SecureTransportPacket.createSigned(1, "SESSION-RESEARCH-001", "PHONE-CLINICIAN-PAD", "HEARTBEAT", "OK", "SECRET-KEY-EYEATLAS")
        assertTrue(transport.processIncomingPacket(p1).isSuccess)

        // Inject duplicate sequence 1
        val p1Duplicate = SecureTransportPacket.createSigned(1, "SESSION-RESEARCH-001", "PHONE-CLINICIAN-PAD", "HEARTBEAT", "REPLAY", "SECRET-KEY-EYEATLAS")
        val result = transport.processIncomingPacket(p1Duplicate)
        assertTrue(result.isFailure)
        assertEquals(1, transport.duplicatePacketsRejected)
    }

    @Test
    fun multiTouchAmbiguityFaultInjectionTest() {
        val responseEngine = ResponseAndGaze()
        val clockSync = getSynchronizedEngine()

        // Inject 2 pointers (multi-touch ambiguity)
        val multiTouchEvent = responseEngine.registerTouchEvent(
            sessionId = "SESSION-RESEARCH-001",
            responsePhoneId = "PHONE-CLINICIAN-PAD",
            touchDownNs = 1_200_000_000L,
            touchUpNs = 1_300_000_000L,
            pointerCount = 2
        )
        assertEquals("MULTI_TOUCH_AMBIGUITY", multiTouchEvent.inputQuality)

        val evaluatedTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(multiTouchEvent),
            clockSync = clockSync,
            isTransportConnected = true
        )

        // Must produce INVALID/UNGRADABLE, NEVER false NO_RESPONSE
        assertEquals("INVALID_UNGRADABLE", evaluatedTrial.observedAnswer)
        assertTrue(evaluatedTrial.validityFlags.contains("INVALID_MULTI_TOUCH"))
        assertTrue(evaluatedTrial.isUngradableOrInvalid)
    }

    @Test
    fun falseTapsDebounceFaultInjectionTest() {
        val responseEngine = ResponseAndGaze()
        val clockSync = getSynchronizedEngine()

        // First tap
        responseEngine.registerTouchEvent("SESSION-001", "PAD-01", 1_000_000_000L, 1_050_000_000L, 1)

        // Rapid false tap 30ms later (< 100ms debounce)
        val rapidTap = responseEngine.registerTouchEvent("SESSION-001", "PAD-01", 1_030_000_000L, 1_060_000_000L, 1)
        assertEquals("DEBOUNCE_REJECTED", rapidTap.inputQuality)

        val evaluatedTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(rapidTap),
            clockSync = clockSync,
            isTransportConnected = true
        )

        assertEquals("INVALID_UNGRADABLE", evaluatedTrial.observedAnswer)
        assertTrue(evaluatedTrial.validityFlags.contains("INVALID_FALSE_TAP"))
        assertTrue(evaluatedTrial.isUngradableOrInvalid)
    }

    @Test
    fun lateTouchFaultInjectionTest() {
        val responseEngine = ResponseAndGaze()
        val clockSync = getSynchronizedEngine()

        // Response event with explicit late fault injection
        val lateResponse = ResponseEvent(
            eventId = "LATE-01",
            sessionId = "SESSION-RESEARCH-001",
            responsePhoneId = "PHONE-CLINICIAN-PAD",
            monotonicEventNs = 2_800_000_000L,
            inputType = "TOUCH_TAP",
            pointerCount = 1,
            inputQuality = "LATE_FAULT_ARRIVED_OUTSIDE_WINDOW"
        )

        val evaluatedTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(lateResponse),
            clockSync = clockSync,
            isTransportConnected = true
        )

        assertEquals("INVALID_UNGRADABLE", evaluatedTrial.observedAnswer)
        assertTrue(evaluatedTrial.validityFlags.contains("INVALID_LATE_TOUCH"))
        assertTrue(evaluatedTrial.isUngradableOrInvalid)
    }

    @Test
    fun responsePhoneDisconnectWatchdogTest() {
        val responseEngine = ResponseAndGaze()
        val clockSync = getSynchronizedEngine()

        // Transport disconnect injected (isTransportConnected = false)
        val evaluatedTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = emptyList(),
            clockSync = clockSync,
            isTransportConnected = false
        )

        // Invariant: Disconnect must NEVER be fabricated as a negative visual field point (NO_RESPONSE)
        assertEquals("INVALID_UNGRADABLE", evaluatedTrial.observedAnswer)
        assertTrue(evaluatedTrial.validityFlags.contains("DISCONNECT_WATCHDOG_TRIGGERED"))
        assertTrue(evaluatedTrial.isUngradableOrInvalid)
    }

    @Test
    fun clockSyncUncertaintyExceededFaultTest() {
        val responseEngine = ResponseAndGaze()
        // Inject high clock sync uncertainty (100ms > 50ms tolerance)
        val highJitterSync = getSynchronizedEngine(uncertaintyNs = 100_000_000L)
        assertFalse(highJitterSync.isUncertaintyAcceptable())

        val evaluatedTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = emptyList(),
            clockSync = highJitterSync,
            isTransportConnected = true
        )

        assertEquals("INVALID_UNGRADABLE", evaluatedTrial.observedAnswer)
        assertTrue(evaluatedTrial.validityFlags.contains("INVALID_TIMING_UNCERTAINTY"))
        assertTrue(evaluatedTrial.isUngradableOrInvalid)
    }

    @Test
    fun airplaneModeInterruptionAndResumeTest() {
        val transport = PairingAndTransport()
        val payload = transport.initiatePairing()
        transport.completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD")
        assertEquals(TransportState.PINNED_AUTHENTICATED, transport.getState())

        // Injected airplane mode interruption
        transport.setAirplaneModeInterruption()
        assertEquals(TransportState.DISCONNECTED, transport.getState())
        assertFalse(transport.checkWatchdog())

        // Resume after interruption
        transport.resumeAfterInterruption()
        assertEquals(TransportState.PINNED_AUTHENTICATED, transport.getState())
        assertTrue(transport.checkWatchdog())
    }

    @Test
    fun priorityStopCommandInjectionTest() {
        val transport = PairingAndTransport()
        val payload = transport.initiatePairing()
        transport.completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD")

        val responseEngine = ResponseAndGaze()
        val clockSync = getSynchronizedEngine()

        // Inject long press (1200ms >= 1000ms threshold)
        val stopEvent = responseEngine.registerTouchEvent(
            sessionId = "SESSION-RESEARCH-001",
            responsePhoneId = "PHONE-CLINICIAN-PAD",
            touchDownNs = 1_000_000_000L,
            touchUpNs = 2_200_000_000L,
            pointerCount = 1
        )
        assertEquals("LONG_PRESS_STOP", stopEvent.inputType)
        assertEquals("PRIORITY_STOP", stopEvent.inputQuality)

        // Process in transport packet
        val stopPacket = SecureTransportPacket.createSigned(
            1, "SESSION-RESEARCH-001", "PHONE-CLINICIAN-PAD", "PRIORITY_STOP", "PATIENT_HALT", "SECRET-KEY-EYEATLAS"
        )
        val res = transport.processIncomingPacket(stopPacket)
        assertTrue(res.isSuccess)
        assertEquals(TransportState.STOPPED, transport.getState())

        // Evaluate trial under STOP
        val evaluatedTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(stopEvent),
            clockSync = clockSync,
            isTransportConnected = true
        )
        assertEquals("INVALID_UNGRADABLE", evaluatedTrial.observedAnswer)
        assertTrue(evaluatedTrial.validityFlags.contains("STOP_REQUESTED"))
    }

    @Test
    fun cleanValidSeenAndNoResponseBaselineTest() {
        val responseEngine = ResponseAndGaze()
        val clockSync = getSynchronizedEngine()

        // Case 1: Valid tap at 1.4s (within [1.1s, 2.3s] window)
        // Note: clockSync has offset +20ms, so remote 1.42s corrects to 1.40s
        val validTap = ResponseEvent(
            eventId = "RESP-VALID-1",
            sessionId = "SESSION-RESEARCH-001",
            responsePhoneId = "PHONE-CLINICIAN-PAD",
            monotonicEventNs = 1_400_000_000L + clockSync.currentOffsetNs,
            inputType = "TOUCH_TAP",
            pointerCount = 1,
            inputQuality = "VALID"
        )

        val seenTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(validTap),
            clockSync = clockSync,
            isTransportConnected = true
        )
        assertEquals("SEEN", seenTrial.observedAnswer)
        assertFalse(seenTrial.isUngradableOrInvalid)
        assertEquals("VALID_SEEN", seenTrial.validityFlags.first())

        // Case 2: Clean full window with zero taps and healthy connection
        val noResponseTrial = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = emptyList(),
            clockSync = clockSync,
            isTransportConnected = true
        )
        assertEquals("NO_RESPONSE", noResponseTrial.observedAnswer)
        assertFalse(noResponseTrial.isUngradableOrInvalid)
        assertEquals("VALID_NO_RESPONSE", noResponseTrial.validityFlags.first())
    }

    @Test
    fun cameraCaptureBlurAndExposureFaultInjectionTest() {
        val pipeline = org.eyeatlas.sensors.CameraPipeline()

        // 1. Injected Blur
        val blurResult = pipeline.evaluateCaptureQuality(focusMetric = 8.5, meanLuminance = 120.0, glarePercentage = 1.0)
        assertFalse(blurResult.isPassed)
        assertTrue(blurResult.failureReasons.any { it.contains("BLUR_EXCEEDS_LIMIT") })
        assertEquals(0.0, blurResult.qualityScore, 0.01)

        // 2. Injected Underexposure
        val darkResult = pipeline.evaluateCaptureQuality(focusMetric = 35.0, meanLuminance = 22.0, glarePercentage = 0.0)
        assertFalse(darkResult.isPassed)
        assertTrue(darkResult.failureReasons.any { it.contains("UNDEREXPOSED") })

        // 3. Injected Overexposure
        val brightResult = pipeline.evaluateCaptureQuality(focusMetric = 35.0, meanLuminance = 235.0, glarePercentage = 2.0)
        assertFalse(brightResult.isPassed)
        assertTrue(brightResult.failureReasons.any { it.contains("OVEREXPOSED") })

        // 4. Injected Glare
        val glareResult = pipeline.evaluateCaptureQuality(focusMetric = 35.0, meanLuminance = 140.0, glarePercentage = 9.8)
        assertFalse(glareResult.isPassed)
        assertTrue(glareResult.failureReasons.any { it.contains("EXCESSIVE_GLARE") })

        // 5. Clean passing capture
        val cleanResult = pipeline.evaluateCaptureQuality(focusMetric = 42.0, meanLuminance = 125.0, glarePercentage = 1.2)
        assertTrue(cleanResult.isPassed)
        assertTrue(cleanResult.qualityScore > 0.7)
        assertTrue(cleanResult.failureReasons.isEmpty())
    }

    @Test
    fun scaleMarkerAndSideLabelIntegrityTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()
        val pipeline = org.eyeatlas.sensors.CameraPipeline()
        val goodQuality = pipeline.evaluateCaptureQuality(focusMetric = 40.0, meanLuminance = 120.0, glarePercentage = 0.5)

        val lockedSettings = org.eyeatlas.sensors.LockedCameraSettings()

        // Build valid OD multi-gaze series
        val odCaptures = org.eyeatlas.vision.AnteriorGazeDirection.values().associateWith { gaze ->
            org.eyeatlas.vision.AnteriorImageCapture(
                captureId = "CAP-OD-${gaze.name}",
                sessionId = "SESSION-RESEARCH-001",
                eye = EyeLaterality.OD,
                gazeDirection = gaze,
                rawImageHash = "HASH-OD-${gaze.name}",
                cameraSettings = lockedSettings,
                quality = goodQuality,
                scaleMarkerDetected = true
            )
        }
        val validSeries = org.eyeatlas.vision.MultiGazeSeries("SESSION-RESEARCH-001", EyeLaterality.OD, odCaptures)
        assertTrue(anterior.validateMultiGazeSeries(validSeries).isSuccess)

        // Inject Laterality Mismatch (substituting OS image into OD series)
        val tamperedCaptures = odCaptures.toMutableMap()
        tamperedCaptures[org.eyeatlas.vision.AnteriorGazeDirection.UPGAZE] = tamperedCaptures[org.eyeatlas.vision.AnteriorGazeDirection.UPGAZE]!!.copy(
            eye = EyeLaterality.OS
        )
        val tamperedSeries = org.eyeatlas.vision.MultiGazeSeries("SESSION-RESEARCH-001", EyeLaterality.OD, tamperedCaptures)
        val tamperedResult = anterior.validateMultiGazeSeries(tamperedSeries)
        assertTrue(tamperedResult.isFailure)
        assertTrue(tamperedResult.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun cameraOutputHashStabilityTest() {
        val sample1 = "RAW_CAMERA_FRAME_DATA_LOCKED_PARAMETERS_OD_PRIMARY".toByteArray(Charsets.UTF_8)
        val sample2 = "RAW_CAMERA_FRAME_DATA_LOCKED_PARAMETERS_OD_PRIMARY".toByteArray(Charsets.UTF_8)
        val sample3 = "RAW_CAMERA_FRAME_DATA_LOCKED_PARAMETERS_OD_PRIMARY_TAMPERED".toByteArray(Charsets.UTF_8)

        val hash1 = org.eyeatlas.sensors.CameraPipeline.computeSha256(sample1)
        val hash2 = org.eyeatlas.sensors.CameraPipeline.computeSha256(sample2)
        val hash3 = org.eyeatlas.sensors.CameraPipeline.computeSha256(sample3)

        assertEquals("Hashes for identical captures must be stable and match", hash1, hash2)
        assertNotEquals("Tampered capture must produce distinct hash", hash1, hash3)
        assertEquals(64, hash1.length) // 256-bit hex
    }

    @Test
    fun infectionTriageRedFlagAndConjunctivitisDifferentialTest() {
        val anterior = org.eyeatlas.vision.AnteriorTests()

        // 1. Emergency Red Flag: Red eye + severe pain + corneal infiltrate + ciliary flush
        val emergencySigns = org.eyeatlas.vision.AnteriorSymptomSigns(
            hasRedEye = true,
            hasSeverePain = true,
            ciliaryFlushPresent = true,
            cornealOpacityOrInfiltrate = true
        )
        val emergencyResult = anterior.performInfectionTriage(EyeLaterality.OD, emergencySigns, null)
        assertEquals(org.eyeatlas.vision.InfectionTriageUrgency.EMERGENCY_SAME_DAY, emergencyResult.urgency)
        assertTrue(emergencyResult.requiresSameDayOphthalmologist)
        assertTrue(emergencyResult.differentialDiagnoses.contains("Microbial Keratitis"))

        // 2. Non-emergency red eye with purulent discharge (bacterial/viral differential)
        val dischargeSigns = org.eyeatlas.vision.AnteriorSymptomSigns(
            hasRedEye = true,
            purulentDischargePresent = true,
            hasSeverePain = false,
            ciliaryFlushPresent = false
        )
        val dischargeResult = anterior.performInfectionTriage(EyeLaterality.OD, dischargeSigns, null)
        assertEquals(org.eyeatlas.vision.InfectionTriageUrgency.ROUTINE, dischargeResult.urgency)
        assertFalse(dischargeResult.requiresSameDayOphthalmologist)
        assertTrue(dischargeResult.differentialDiagnoses.any { it.contains("Bacterial Conjunctivitis") })

        // 3. Dry eye / mild irritation (no discharge, no red flags)
        val dryEyeSigns = org.eyeatlas.vision.AnteriorSymptomSigns(
            hasRedEye = true,
            foreignBodySensationOnly = true,
            hasSeverePain = false
        )
        val dryEyeResult = anterior.performInfectionTriage(EyeLaterality.OS, dryEyeSigns, null)
        assertEquals(org.eyeatlas.vision.InfectionTriageUrgency.ROUTINE, dryEyeResult.urgency)
        assertTrue(dryEyeResult.differentialDiagnoses.any { it.contains("Dry Eye") })
    }

    // --- Defect Remediation Test (DEF-09) ---

    @Test
    fun dynamicCryptoKeyAndRandomPinPairingTest() {
        val transport = org.eyeatlas.net.PairingAndTransport(
            localDeviceId = "PHONE-PATIENT-VR",
            expectedSessionId = "SES-DYNAMIC-01",
            expectedPeerDeviceId = "PHONE-CLINICIAN-PAD"
        )

        // 1. Dynamic pairing initiation generates fresh key and random 6-digit PIN
        val payload = transport.initiateDynamicPairing(sessionId = "SES-DYNAMIC-01")
        assertNotNull(payload)
        assertEquals(6, payload.authPin.length)
        assertTrue("PIN must be numeric 6 digits", payload.authPin.toIntOrNull() != null)
        assertEquals(16, payload.cryptoKeyHash.length)

        // 2. Peer discovery
        val peers = transport.discoverLocalPeers()
        assertTrue(peers.isNotEmpty())
        assertEquals("EyeAtlas_VR_Service", peers[0].serviceName)

        // 3. Complete pairing
        val paired = transport.completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD")
        assertTrue(paired)
        assertEquals(org.eyeatlas.net.TransportState.PINNED_AUTHENTICATED, transport.getState())

        // 4. Dynamic key is used to sign and process packets
        val packet = org.eyeatlas.net.SecureTransportPacket.createSigned(
            sequence = 1L,
            sessionId = "SES-DYNAMIC-01",
            senderId = "PHONE-CLINICIAN-PAD",
            type = "TOUCH_EVENT",
            data = "SEEN_TAP",
            key = transport.getSharedSecretKey()
        )
        val result = transport.processIncomingPacket(packet)
        assertTrue(result.isSuccess)
        assertEquals("SEEN_TAP", result.getOrNull())
    }

    // --- X02 Backend Integration Tests ---

    class MockHttpTransport : HttpTransport {
        var lastUrl: String? = null
        var lastHeaders: Map<String, String>? = null
        var lastBody: String? = null
        var mockResponse: SyncHttpResponse = SyncHttpResponse(200, "{\"status\": \"SUCCESS\"}")

        override fun post(url: String, headers: Map<String, String>, body: String): SyncHttpResponse {
            lastUrl = url
            lastHeaders = headers
            lastBody = body
            return mockResponse
        }

        override fun get(url: String, headers: Map<String, String>): SyncHttpResponse {
            lastUrl = url
            lastHeaders = headers
            return mockResponse
        }
    }

    @Test
    fun backendSyncLocalFirstOfflinePreservationTest() {
        val mockTransport = MockHttpTransport()
        val adapter = BackendSyncAdapter(
            isSyncEnabled = false, // Disabled: local-first mode
            transport = mockTransport
        )
        val store = org.eyeatlas.store.PatientStore()
        store.syncAdapter = adapter

        val patient = PatientRecord(
            patientId = "P-LOCAL-999",
            ageBand = "40-59",
            hasDiabetes = true,
            symptoms = listOf("BLURRED_VISION"),
            currentCorrection = "CURRENT_HABITUAL"
        )
        assertTrue(store.saveRecord(patient))

        // Verify that local store immediately decrypts and returns record without any network call
        val retrieved = store.getRecord("P-LOCAL-999")
        assertEquals("P-LOCAL-999", retrieved.patientId)
        assertTrue(retrieved.hasDiabetes)
        assertEquals(0, adapter.pendingCount)
        assertNull(mockTransport.lastUrl)
    }

    @Test
    fun backendSyncUploadQueueAndBatchPackagingTest() {
        val mockTransport = MockHttpTransport()
        mockTransport.mockResponse = SyncHttpResponse(
            200,
            "{\"status\": \"SUCCESS\", \"processedCount\": 3, \"conflictCount\": 0}"
        )

        val adapter = BackendSyncAdapter(
            isSyncEnabled = true,
            transport = mockTransport
        )
        adapter.authenticate("MOCK-JWT-TOKEN-APEX")

        val patient = PatientRecord(
            patientId = "P-SYNC-01",
            ageBand = "ADULT_18_TO_64",
            hasDiabetes = false,
            symptoms = listOf("DRYNESS"),
            currentCorrection = "UNAIDED"
        )
        val trial = baseTrial.copy(trialId = "TR-SYNC-01")
        val measurement = Measurement(
            measurementId = "M-SYNC-01",
            sessionId = "SESSION-RESEARCH-001",
            eye = EyeLaterality.OD,
            method = "LOGMAR",
            value = 0.05,
            units = "logMAR",
            ciLower = 0.0,
            ciUpper = 0.10,
            validity = true
        )

        adapter.queuePatient(patient)
        adapter.queueTrial(trial)
        adapter.queueMeasurement(measurement)
        assertEquals(3, adapter.pendingCount)

        val summary = adapter.syncNow()
        assertEquals(SyncStatus.SUCCESS, summary.status)
        assertEquals(3, summary.processedCount)
        assertEquals(0, adapter.pendingCount)

        // Verify sent payload
        assertNotNull(mockTransport.lastBody)
        assertTrue(mockTransport.lastBody!!.contains("P-SYNC-01"))
        assertTrue(mockTransport.lastBody!!.contains("TR-SYNC-01"))
        assertTrue(mockTransport.lastBody!!.contains("M-SYNC-01"))
        assertEquals("Bearer MOCK-JWT-TOKEN-APEX", mockTransport.lastHeaders?.get("Authorization"))
    }

    @Test
    fun backendSyncInterruptedNetworkRetryTest() {
        val mockTransport = MockHttpTransport()
        // Simulate network connection timeout / network drop
        mockTransport.mockResponse = SyncHttpResponse(0, "Connection timeout to 127.0.0.1:8000")

        val adapter = BackendSyncAdapter(
            isSyncEnabled = true,
            transport = mockTransport
        )
        adapter.authenticate("MOCK-TOKEN")

        val patient = PatientRecord(
            patientId = "P-RETRY-01",
            ageBand = "ADULT_18_TO_64",
            hasDiabetes = true,
            symptoms = listOf("FLOATERS"),
            currentCorrection = "NONE"
        )
        adapter.queuePatient(patient)
        assertEquals(1, adapter.pendingCount)

        val summary = adapter.syncNow()
        assertEquals(SyncStatus.OFFLINE, summary.status)
        assertEquals(1, adapter.pendingCount) // Unsynced record remains safely queued!
        assertNotNull(adapter.lastError)

        // Next attempt: network recovers
        mockTransport.mockResponse = SyncHttpResponse(200, "{\"status\": \"SUCCESS\", \"processedCount\": 1}")
        val retrySummary = adapter.syncNow()
        assertEquals(SyncStatus.SUCCESS, retrySummary.status)
        assertEquals(0, adapter.pendingCount)
    }

    @Test
    fun backendSyncConflictReconciliationTest() {
        val mockTransport = MockHttpTransport()
        mockTransport.mockResponse = SyncHttpResponse(
            200,
            "{\"status\": \"SUCCESS\", \"processedCount\": 1, \"conflictCount\": 1, \"conflicts\": [{\"entity\": \"patient\", \"id\": \"P-CONF-01\", \"resolution\": \"MERGED_SERVER_VERSION_BUMPED\"}]}"
        )

        val adapter = BackendSyncAdapter(
            isSyncEnabled = true,
            transport = mockTransport
        )
        adapter.authenticate("MOCK-TOKEN")

        val patient = PatientRecord(
            patientId = "P-CONF-01",
            ageBand = "40-59",
            hasDiabetes = false,
            symptoms = listOf("TIRED_EYES"),
            currentCorrection = "NONE"
        )
        adapter.queuePatient(patient)

        val summary = adapter.syncNow()
        assertEquals(SyncStatus.CONFLICT_RESOLVED, summary.status)
        assertEquals(1, summary.conflictCount)
        assertEquals(1, adapter.recordedConflicts.size)
        assertTrue(adapter.recordedConflicts[0].contains("Conflict detected"))
        assertEquals(0, adapter.pendingCount)
    }

    @Test
    fun backendSyncDuplicateRetransmissionIdempotencyTest() {
        val mockTransport = MockHttpTransport()
        // Server recognizes duplicate batch_id and returns IDEMPOTENT_SUCCESS
        mockTransport.mockResponse = SyncHttpResponse(
            200,
            "{\"status\": \"IDEMPOTENT_SUCCESS\", \"processedCount\": 0, \"conflictCount\": 0}"
        )

        val adapter = BackendSyncAdapter(
            isSyncEnabled = true,
            transport = mockTransport
        )
        adapter.authenticate("MOCK-TOKEN")

        val patient = PatientRecord(
            patientId = "P-IDEM-01",
            ageBand = "40-59",
            hasDiabetes = false,
            symptoms = listOf("NONE"),
            currentCorrection = "NONE"
        )
        adapter.queuePatient(patient)

        val summary = adapter.syncNow()
        assertEquals(SyncStatus.SUCCESS, summary.status)
        assertEquals(0, adapter.pendingCount)
    }
}
