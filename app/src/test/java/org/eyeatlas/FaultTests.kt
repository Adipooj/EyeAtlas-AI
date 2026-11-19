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
}
