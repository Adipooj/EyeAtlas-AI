package org.eyeatlas

import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.ResponseEvent
import org.eyeatlas.core.StimulusTrial
import org.eyeatlas.net.*
import org.eyeatlas.sensors.ResponseAndGaze
import org.junit.Assert.*
import org.junit.Test

class RemoteApkIntegrationTests {

    private val baseTrial = StimulusTrial(
        trialId = "TR-REMOTE-001",
        sessionId = "SES-REMOTE-TEST",
        eye = EyeLaterality.OD,
        testCode = "PERIMETRY_24_2",
        targetAngleDeg = 9.0,
        luminanceCdM2 = 10.0,
        opticalIndex = 1.0,
        monotonicPresentedNs = 1_000_000_000L,
        expectedAnswer = "SEEN",
        framesRendered = 60,
        calibrationHash = "CAL-REMOTE-BASELINE"
    )

    private fun getSynchronizedEngine(): ClockSyncEngine {
        val sync = ClockSyncEngine()
        sync.addSample(
            ClockSyncSample(
                sampleId = "SYNC-01",
                t0Ns = 100_000_000L,
                t1Ns = 110_000_000L,
                t2Ns = 111_000_000L,
                t3Ns = 120_000_000L
            )
        )
        return sync
    }

    @Test
    fun testRemoteSessionClientSequenceAndStimulusPublication() {
        val client = RemoteSessionClient(sessionId = "SES-TEST-001")
        assertEquals(RemoteClientState.DISCONNECTED, client.getState())

        val mockTransport = object : RemoteWebSocketTransport {
            var connected = false
            override fun connect(endpointUrl: String, headers: Map<String, String>) { connected = true }
            override fun send(envelope: RemoteEnvelope): Boolean = true
            override fun disconnect() { connected = false }
            override fun isConnected(): Boolean = connected
        }
        client.transport = mockTransport

        val sent = client.startRemoteSession("/ws/remote/SES-TEST-001", "mock-patient-token")
        assertTrue(sent)
        // startRemoteSession transitions to CONNECTING, NOT prematurely to AUTHENTICATED
        assertEquals(RemoteClientState.CONNECTING, client.getState())

        // Feed genuine AUTH_ACK to transition to AUTHENTICATED
        val authAck = RemoteEnvelope(
            sessionId = "SES-TEST-001",
            messageId = "ack-1",
            senderId = "SESSION_BROKER",
            senderRole = "OBSERVER",
            messageType = RemoteMessageType.AUTH_ACK,
            sequenceNumber = 1L,
            timestampNs = System.nanoTime()
        )
        client.processIncomingEnvelope(authAck)
        assertEquals(RemoteClientState.AUTHENTICATED, client.getState())

        val stimEnv = client.publishStimulusPresented(
            trialIndex = 5,
            testModule = "PERIMETRY_24_2",
            eyeLaterality = EyeLaterality.OD,
            xDeg = -15.0,
            yDeg = 3.0,
            presentationMonotonicNs = 1_000_000_000L
        )

        assertEquals("PATIENT_VR_HOST", stimEnv.senderRole)
        assertEquals(RemoteMessageType.STIMULUS_EVENT, stimEnv.messageType)
        assertTrue(stimEnv.sequenceNumber > 0)
        assertEquals(1_000_000_000L, stimEnv.timestampNs)
        assertTrue(client.getPendingOutboxCount() > 0)
    }

    @Test
    fun testRemoteSessionClientEmergencyStop() {
        val client = RemoteSessionClient(sessionId = "SES-TEST-002")
        var emergencyTriggered = false
        client.onEmergencyStopListener = { emergencyTriggered = true }

        val stopEnv = client.triggerEmergencyStop("Patient felt dizzy")
        assertTrue(client.isEmergencyHalted)
        assertEquals(RemoteClientState.PAUSED_BY_EMERGENCY, client.getState())
        assertEquals(RemoteMessageType.EMERGENCY_STOP, stopEnv.messageType)
        assertTrue(emergencyTriggered)

        client.clearEmergencyHalt()
        assertFalse(client.isEmergencyHalted)
        assertEquals(RemoteClientState.AUTHENTICATED, client.getState())
    }

    @Test
    fun testBluetoothTactileClickerBypassesNetworkUncertainty() {
        val responseEngine = ResponseAndGaze()
        val syncEngine = getSynchronizedEngine()

        // Presentation at 1.0s, Bluetooth clicker pressed at 1.35s (350ms latency)
        val clickerEvent = responseEngine.registerHardwareButtonEvent(
            sessionId = "SES-REMOTE-TEST",
            inputType = ResponseAndGaze.INPUT_BT_CLICKER,
            eventMonotonicNs = 1_350_000_000L
        )

        assertEquals("VALID", clickerEvent.inputQuality)
        assertEquals(ResponseAndGaze.INPUT_BT_CLICKER, clickerEvent.inputType)

        // Evaluate response: direct hardware input does not depend on network transport
        val evaluated = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(clickerEvent),
            clockSync = syncEngine,
            isTransportConnected = false // Even if network dropped, direct BT input is valid!
        )

        assertEquals("SEEN", evaluated.observedAnswer)
        assertEquals(1_350_000_000L, evaluated.responsePhoneMonotonicNs)
        assertEquals(0L, evaluated.clockOffsetNs)
        assertTrue(evaluated.validityFlags.contains("VALID_SEEN_HARDWARE_DIRECT"))
    }

    @Test
    fun testVolumeRockerKeyInput() {
        val responseEngine = ResponseAndGaze()
        val syncEngine = getSynchronizedEngine()

        // Volume key pressed at 1.25s (250ms reaction time)
        val volEvent = responseEngine.registerHardwareButtonEvent(
            sessionId = "SES-REMOTE-TEST",
            inputType = ResponseAndGaze.INPUT_VOLUME_KEY,
            eventMonotonicNs = 1_250_000_000L
        )

        val evaluated = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(volEvent),
            clockSync = syncEngine,
            isTransportConnected = true
        )

        assertEquals("SEEN", evaluated.observedAnswer)
        assertTrue(evaluated.validityFlags.contains("VALID_SEEN_HARDWARE_DIRECT"))
        assertTrue(evaluated.validityFlags.contains(ResponseAndGaze.INPUT_VOLUME_KEY))
    }

    @Test
    fun testPhysicianVerbalObservationFlagging() {
        val responseEngine = ResponseAndGaze()
        val syncEngine = getSynchronizedEngine()

        val verbalSeen = responseEngine.registerPhysicianVerbalEvent(
            sessionId = "SES-REMOTE-TEST",
            clinicianId = "DOC-SHARMA",
            isSeen = true,
            timestampNs = 1_500_000_000L
        )

        val evaluated = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = listOf(verbalSeen),
            clockSync = syncEngine,
            isTransportConnected = true
        )

        assertEquals("SEEN", evaluated.observedAnswer)
        assertTrue(evaluated.validityFlags.contains("PHYSICIAN_VERBAL_OBSERVATION"))
        assertEquals(0L, evaluated.clockOffsetNs) // No false hardware latency calculation
    }

    @Test
    fun testDisconnectedTransportProducesUngradableNeverFalseScotoma() {
        val responseEngine = ResponseAndGaze()
        val syncEngine = getSynchronizedEngine()

        // No hardware button pressed, network disconnected
        val evaluated = responseEngine.evaluateTrialResponse(
            trial = baseTrial,
            responses = emptyList(),
            clockSync = syncEngine,
            isTransportConnected = false
        )

        // HARD MEDICAL INVARIANT: Disconnected transport must produce INVALID_UNGRADABLE, NOT NO_RESPONSE!
        assertEquals("INVALID_UNGRADABLE", evaluated.observedAnswer)
        assertTrue(evaluated.validityFlags.contains("DISCONNECT_WATCHDOG_TRIGGERED"))
        assertNotEquals("NO_RESPONSE", evaluated.observedAnswer)
    }
}
