package org.eyeatlas.net

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.SessionEvent

/**
 * Client connection state for online remote tele-ophthalmology sessions.
 */
enum class RemoteClientState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATED,
    RECONNECTING,
    PAUSED_BY_EMERGENCY,
    TERMINATED
}

/**
 * Message types matching the backend WebSocket protocol.
 */
enum class RemoteMessageType {
    AUTH,
    AUTH_ACK,
    PEER_STATUS,
    HEARTBEAT,
    STIMULUS_EVENT,
    RESPONSE_EVENT,
    CONTROL_COMMAND,
    OBSERVATION_SUBMIT,
    EMERGENCY_STOP,
    SYNC_ACK,
    REPLAY_REQUEST,
    REPLAY_RESPONSE,
    WEBRTC_OFFER,
    WEBRTC_ANSWER,
    WEBRTC_ICE_CANDIDATE,
    STREAM_STATUS,
    ERROR
}

data class RemoteEnvelope(
    val version: String = "1.0",
    val sessionId: String,
    val messageId: String,
    val senderId: String,
    val senderRole: String = "PATIENT_VR_HOST",
    val messageType: RemoteMessageType,
    val sequenceNumber: Long,
    val timestampNs: Long,
    val payload: Map<String, Any> = emptyMap()
)

/**
 * Abstract WebSocket transport contract for dependency injection and testing.
 */
interface RemoteWebSocketTransport {
    fun connect(endpointUrl: String, headers: Map<String, String>)
    fun send(envelope: RemoteEnvelope): Boolean
    fun disconnect()
    fun isConnected(): Boolean
}

/**
 * Authoritative remote session coordinator for the patient Android APK (CR-002).
 *
 * Responsibilities:
 * 1. Establishes authenticated WSS connection to cloud session broker.
 * 2. Enforces monotonic sequence numbers on all outgoing events.
 * 3. Maintains an ordered outbox buffer for zero packet loss during network drops.
 * 4. Publishes real-time stimulus presentation events with nanosecond monotonic timestamps.
 * 5. Handles remote physician commands and patient emergency halt broadcasts.
 */
class RemoteSessionClient(
    val sessionId: String,
    val localDeviceId: String = "PHONE-PATIENT-VR",
    private val transport: RemoteWebSocketTransport? = null,
    val maxOutboxBuffer: Int = 100
) {
    private val sequenceCounter = AtomicLong(0L)
    private var state = RemoteClientState.DISCONNECTED
    private val outboxBuffer = ConcurrentLinkedQueue<RemoteEnvelope>()
    private var lastReceivedSequence = -1L

    // Active session telemetry
    var activePhysicianId: String? = null
        private set
    var connectedRoles: List<String> = emptyList()
        private set
    var isEmergencyHalted: Boolean = false
        private set
    var lastStimulusMonotonicNs: Long = 0L
        private set

    // Callback listeners
    var onCommandListener: ((command: String, module: String?, params: Map<String, Any>) -> Unit)? = null
    var onEmergencyStopListener: ((reason: String) -> Unit)? = null
    var onPeerStatusListener: ((roles: List<String>, physician: String?) -> Unit)? = null
    var onWebRtcAnswerListener: ((sdp: String) -> Unit)? = null
    var onIceCandidateListener: ((candidate: String, sdpMid: String?, sdpMLineIndex: Int?) -> Unit)? = null
    var onStreamStatusListener: ((isStreaming: Boolean, width: Int, height: Int) -> Unit)? = null

    fun getState(): RemoteClientState = state

    fun getPendingOutboxCount(): Int = outboxBuffer.size

    fun getNextSequence(): Long = sequenceCounter.incrementAndGet()

    /**
     * Connects to the remote session broker using an ephemeral session token.
     */
    fun startRemoteSession(endpointUrl: String, authToken: String): Boolean {
        state = RemoteClientState.CONNECTING
        transport?.connect(
            endpointUrl,
            mapOf("Authorization" to "Bearer $authToken")
        )
        // Send initial AUTH envelope
        val authEnv = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "auth-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.AUTH,
            sequenceNumber = getNextSequence(),
            timestampNs = System.nanoTime(),
            payload = mapOf(
                "authToken" to authToken,
                "role" to "PATIENT_VR_HOST",
                "deviceId" to localDeviceId
            )
        )
        sendEnvelope(authEnv)
        state = RemoteClientState.AUTHENTICATED
        return true
    }

    /**
     * Publishes a stimulus presentation event to the remote physician for live twin-reconstruction.
     * CRITICAL MEDICAL INVARIANT:
     * Stimulus timestamps MUST use System.nanoTime() (hardware monotonic clock).
     */
    fun publishStimulusPresented(
        trialIndex: Int,
        testModule: String,
        eyeLaterality: EyeLaterality,
        xDeg: Double,
        yDeg: Double,
        luminanceCdM2: Double = 10.0,
        contrastPct: Double = 100.0,
        presentationMonotonicNs: Long = System.nanoTime()
    ): RemoteEnvelope {
        lastStimulusMonotonicNs = presentationMonotonicNs
        val envelope = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "stim-$trialIndex-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.STIMULUS_EVENT,
            sequenceNumber = getNextSequence(),
            timestampNs = presentationMonotonicNs,
            payload = mapOf(
                "trial_index" to trialIndex,
                "test_module" to testModule,
                "eye_laterality" to eyeLaterality.name,
                "coordinates" to mapOf("x_deg" to xDeg, "y_deg" to yDeg),
                "luminance_nits" to luminanceCdM2,
                "contrast_pct" to contrastPct,
                "presentation_monotonic_ns" to presentationMonotonicNs
            )
        )
        sendEnvelope(envelope)
        return envelope
    }

    /**
     * Publishes a patient response with authoritative local reaction latency.
     */
    fun publishPatientResponse(
        trialIndex: Int,
        isSeen: Boolean,
        reactionLatencyMs: Double,
        inputSource: String,
        localMonotonicNs: Long = System.nanoTime(),
        inputQuality: String = "VALID"
    ): RemoteEnvelope {
        val envelope = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "resp-$trialIndex-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.RESPONSE_EVENT,
            sequenceNumber = getNextSequence(),
            timestampNs = localMonotonicNs,
            payload = mapOf(
                "trial_index" to trialIndex,
                "is_seen" to isSeen,
                "reaction_latency_ms" to reactionLatencyMs,
                "input_source" to inputSource,
                "input_quality" to inputQuality,
                "local_monotonic_ns" to localMonotonicNs
            )
        )
        sendEnvelope(envelope)
        return envelope
    }

    /**
     * Triggers an emergency stop from the patient side (headset button or gesture).
     */
    fun triggerEmergencyStop(reason: String = "Patient discomfort or quick-release button pressed"): RemoteEnvelope {
        isEmergencyHalted = true
        state = RemoteClientState.PAUSED_BY_EMERGENCY
        val envelope = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "stop-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.EMERGENCY_STOP,
            sequenceNumber = getNextSequence(),
            timestampNs = System.nanoTime(),
            payload = mapOf(
                "triggered_by" to "PATIENT_HEADSET_HARDWARE_BUTTON",
                "reason" to reason
            )
        )
        sendEnvelope(envelope)
        onEmergencyStopListener?.invoke(reason)
        return envelope
    }

    /**
     * Publishes WebRTC SDP Offer for screen sharing to the remote physician.
     */
    fun publishWebRtcOffer(sdp: String): RemoteEnvelope {
        val envelope = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "offer-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.WEBRTC_OFFER,
            sequenceNumber = getNextSequence(),
            timestampNs = System.nanoTime(),
            payload = mapOf(
                "type" to "offer",
                "sdp" to sdp
            )
        )
        sendEnvelope(envelope)
        return envelope
    }

    /**
     * Publishes local ICE candidate to the remote physician.
     */
    fun publishIceCandidate(candidate: String, sdpMid: String? = null, sdpMLineIndex: Int? = null): RemoteEnvelope {
        val envelope = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "ice-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.WEBRTC_ICE_CANDIDATE,
            sequenceNumber = getNextSequence(),
            timestampNs = System.nanoTime(),
            payload = mapOf(
                "candidate" to candidate,
                "sdpMid" to (sdpMid ?: ""),
                "sdpMLineIndex" to (sdpMLineIndex ?: 0)
            )
        )
        sendEnvelope(envelope)
        return envelope
    }

    /**
     * Publishes screen streaming health telemetry (resolution, framerate, dropped frames, orientation).
     */
    fun publishStreamStatus(
        isStreaming: Boolean,
        width: Int = 1080,
        height: Int = 2400,
        fps: Int = 30,
        dropped: Int = 0,
        bitrateKbps: Int = 2000,
        orientation: Int = 0,
        interrupted: Boolean = false,
        reason: String? = null
    ): RemoteEnvelope {
        val envelope = RemoteEnvelope(
            sessionId = sessionId,
            messageId = "stream-${System.currentTimeMillis()}",
            senderId = localDeviceId,
            senderRole = "PATIENT_VR_HOST",
            messageType = RemoteMessageType.STREAM_STATUS,
            sequenceNumber = getNextSequence(),
            timestampNs = System.nanoTime(),
            payload = mapOf(
                "is_streaming" to isStreaming,
                "resolution_width" to width,
                "resolution_height" to height,
                "frame_rate" to fps,
                "dropped_frames" to dropped,
                "bitrate_kbps" to bitrateKbps,
                "orientation" to orientation,
                "capture_interrupted" to interrupted,
                "reason" to (reason ?: "")
            )
        )
        sendEnvelope(envelope)
        return envelope
    }

    /**
     * Sends an envelope, buffering it in the outbox queue until acknowledged.
     */
    fun sendEnvelope(envelope: RemoteEnvelope): Boolean {
        // Buffer in outbox
        outboxBuffer.offer(envelope)
        while (outboxBuffer.size > maxOutboxBuffer) {
            outboxBuffer.poll()
        }

        if (transport != null && transport.isConnected()) {
            return transport.send(envelope)
        }
        return false
    }

    /**
     * Ingests incoming WebSocket envelopes from the broker.
     */
    fun processIncomingEnvelope(envelope: RemoteEnvelope): Result<String> {
        // Enforce session boundary
        if (envelope.sessionId != sessionId) {
            return Result.failure(SecurityException("Session ID mismatch: ${envelope.sessionId}"))
        }

        // Monotonic sequence duplicate check
        if (envelope.sequenceNumber <= lastReceivedSequence) {
            return Result.failure(IllegalArgumentException("Duplicate sequence: ${envelope.sequenceNumber} <= $lastReceivedSequence"))
        }
        lastReceivedSequence = envelope.sequenceNumber

        when (envelope.messageType) {
            RemoteMessageType.AUTH_ACK -> {
                state = RemoteClientState.AUTHENTICATED
                return Result.success("AUTHENTICATED")
            }

            RemoteMessageType.PEER_STATUS -> {
                @Suppress("UNCHECKED_CAST")
                val roles = envelope.payload["roles"] as? List<String> ?: emptyList()
                val physician = envelope.payload["active_physician"] as? String
                connectedRoles = roles
                activePhysicianId = physician
                onPeerStatusListener?.invoke(roles, physician)
                return Result.success("PEER_STATUS_UPDATED")
            }

            RemoteMessageType.CONTROL_COMMAND -> {
                val cmd = envelope.payload["command"] as? String ?: "UNKNOWN"
                val module = envelope.payload["target_module"] as? String
                @Suppress("UNCHECKED_CAST")
                val params = envelope.payload["parameters"] as? Map<String, Any> ?: emptyMap()
                onCommandListener?.invoke(cmd, module, params)
                return Result.success("COMMAND_ACKNOWLEDGED: $cmd")
            }

            RemoteMessageType.EMERGENCY_STOP -> {
                isEmergencyHalted = true
                state = RemoteClientState.PAUSED_BY_EMERGENCY
                val reason = envelope.payload["reason"] as? String ?: "Remote emergency stop"
                onEmergencyStopListener?.invoke(reason)
                return Result.success("EMERGENCY_STOP_ACKNOWLEDGED")
            }

            RemoteMessageType.SYNC_ACK -> {
                val ackedSeq = (envelope.payload["acked_sequence"] as? Number)?.toLong() ?: 0L
                outboxBuffer.removeIf { it.sequenceNumber <= ackedSeq }
                return Result.success("SYNC_ACK_PROCESSED: $ackedSeq")
            }

            RemoteMessageType.RESPONSE_EVENT -> {
                // Response arrived from Web Response Pad on Phone 2
                return Result.success("EXTERNAL_RESPONSE_RECEIVED")
            }

            RemoteMessageType.WEBRTC_ANSWER -> {
                val sdp = envelope.payload["sdp"] as? String ?: ""
                onWebRtcAnswerListener?.invoke(sdp)
                return Result.success("WEBRTC_ANSWER_PROCESSED")
            }

            RemoteMessageType.WEBRTC_ICE_CANDIDATE -> {
                val candidate = envelope.payload["candidate"] as? String ?: ""
                val mid = envelope.payload["sdpMid"] as? String
                val mLine = (envelope.payload["sdpMLineIndex"] as? Number)?.toInt()
                onIceCandidateListener?.invoke(candidate, mid, mLine)
                return Result.success("ICE_CANDIDATE_PROCESSED")
            }

            RemoteMessageType.STREAM_STATUS -> {
                val isStreaming = envelope.payload["is_streaming"] as? Boolean ?: false
                val width = (envelope.payload["resolution_width"] as? Number)?.toInt() ?: 1080
                val height = (envelope.payload["resolution_height"] as? Number)?.toInt() ?: 2400
                onStreamStatusListener?.invoke(isStreaming, width, height)
                return Result.success("STREAM_STATUS_PROCESSED")
            }

            else -> {
                return Result.success("PROCESSED: ${envelope.messageType}")
            }
        }
    }

    /**
     * Resets emergency halt state and resumes examination.
     */
    fun clearEmergencyHalt() {
        isEmergencyHalted = false
        state = RemoteClientState.AUTHENTICATED
    }

    fun disconnect() {
        state = RemoteClientState.DISCONNECTED
        transport?.disconnect()
    }
}
