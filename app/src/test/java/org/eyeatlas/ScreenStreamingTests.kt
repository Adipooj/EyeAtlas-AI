package org.eyeatlas

import android.content.Intent
import org.eyeatlas.net.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScreenStreamingTests {

    private lateinit var client: RemoteSessionClient
    private lateinit var engine: DefaultMediaProjectionEngine
    private lateinit var publisher: ScreenStreamPublisher

    @Before
    fun setup() {
        client = RemoteSessionClient(sessionId = "SES-STREAM-001")
        engine = DefaultMediaProjectionEngine()
        publisher = ScreenStreamPublisher(
            sessionClient = client,
            captureEngine = engine,
            targetWidth = 1080,
            targetHeight = 2400,
            targetFps = 30
        )
    }

    @Test
    fun testConsentEnforcementBeforeStreaming() {
        // Attempting to stream without consent must fail
        val ok = publisher.startScreenStream()
        assertFalse("Streaming must be rejected when user consent is absent", ok)
        assertEquals(ScreenStreamState.ERROR, publisher.getState())
        assertFalse(publisher.isStreaming())
    }

    @Test
    fun testConsentFlowAndOfferSignaling() {
        // In Android, Activity.RESULT_OK is -1
        val mockIntent = Intent()
        val consentGranted = publisher.onConsentResult(-1, mockIntent)
        assertTrue(consentGranted)
        assertEquals(ScreenStreamState.CONSENT_REQUESTED, publisher.getState())

        // Start stream
        val started = publisher.startScreenStream(mockIntent)
        assertTrue(started)
        assertEquals(ScreenStreamState.OFFERING, publisher.getState())
        assertTrue(engine.isCapturing())

        // Check that outbox has WebRTC offer
        assertTrue(client.getPendingOutboxCount() > 0)
    }

    @Test
    fun testRemoteAnswerTransitionsToConnectedStreaming() {
        val mockIntent = Intent()
        publisher.onConsentResult(-1, mockIntent)
        publisher.startScreenStream(mockIntent)
        assertEquals(ScreenStreamState.OFFERING, publisher.getState())

        // Ingest WebRTC Answer from physician
        publisher.handleRemoteAnswer("v=0\r\ns=Physician Portal Answer...")
        assertEquals(ScreenStreamState.CONNECTED_STREAMING, publisher.getState())
        assertTrue(publisher.isStreaming())

        val metrics = publisher.getMetrics()
        assertEquals(1080, metrics.width)
        assertEquals(2400, metrics.height)
        assertEquals(30, metrics.currentFps)
        assertEquals(0, metrics.droppedFrames)
    }

    @Test
    fun testDroppedFramesAndOrientationTracking() {
        val mockIntent = Intent()
        publisher.onConsentResult(-1, mockIntent)
        publisher.startScreenStream(mockIntent)
        publisher.handleRemoteAnswer("v=0\r\ns=Answer...")

        // Record 100 frames and 3 dropped frames
        repeat(100) { publisher.recordFrame(isDropped = false) }
        repeat(3) { publisher.recordFrame(isDropped = true) }

        publisher.updateOrientation(90) // Landscape VR position

        val metrics = publisher.getMetrics()
        assertEquals(100L, metrics.totalFramesRendered)
        assertEquals(3L, metrics.droppedFrames)
        assertEquals(90, metrics.orientationDegrees)
        assertFalse(metrics.isInterrupted)
    }

    @Test
    fun testCaptureInterruptionAndExplicitStop() {
        val mockIntent = Intent()
        publisher.onConsentResult(-1, mockIntent)
        publisher.startScreenStream(mockIntent)
        publisher.handleRemoteAnswer("v=0\r\ns=Answer...")

        // Simulate interruption (user pressed Home or incoming phone call)
        publisher.notifyCaptureInterrupted("User pressed home button")
        assertEquals(ScreenStreamState.INTERRUPTED, publisher.getState())
        assertTrue(publisher.getMetrics().isInterrupted)
        assertEquals("User pressed home button", publisher.getMetrics().interruptionReason)

        // Explicit patient stop
        publisher.stopScreenStream("Patient stopped sharing")
        assertEquals(ScreenStreamState.STOPPED, publisher.getState())
        assertFalse(publisher.isStreaming())
        assertFalse(engine.isCapturing())
    }
}
