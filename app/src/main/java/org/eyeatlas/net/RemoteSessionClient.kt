package org.eyeatlas.net

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.SessionEvent
import org.json.JSONObject

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
 * Production OkHttp WebSocket transport for cloud tele-ophthalmology.
 */
class OkHttpRemoteWebSocketTransport(
    private val onEnvelopeReceived: (RemoteEnvelope) -> Unit,
    private val onStateChanged: (RemoteClientState) -> Unit
) : RemoteWebSocketTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeWs: WebSocket? = null
    private var connected = false

    override fun connect(endpointUrl: String, headers: Map<String, String>) {
        disconnect()
        val requestBuilder = Request.Builder().url(endpointUrl)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val request = requestBuilder.build()

        activeWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                // Do NOT set AUTHENTICATED here; state transitions only after receiving AUTH_ACK
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val sessionId = json.optString("session_id", "")
                    val messageId = json.optString("message_id", "")
                    val senderId = json.optString("sender_id", "")
                    val senderRole = json.optString("sender_role", "OBSERVER")
                    val msgTypeStr = json.optString("message_type", "CONTROL_COMMAND")
                    val msgType = try {
                        RemoteMessageType.valueOf(msgTypeStr)
                    } catch (e: Exception) {
                        RemoteMessageType.CONTROL_COMMAND
                    }
                    val seq = json.optLong("sequence_number", 0L)
                    val ts = json.optLong("timestamp_ns", System.nanoTime())

                    val payloadObj = json.optJSONObject("payload")
                    val payloadMap = mutableMapOf<String, Any>()
                    if (payloadObj != null) {
                        val keys = payloadObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            payloadMap[k] = payloadObj.get(k)
                        }
                    }

                    val env = RemoteEnvelope(
                        sessionId = sessionId,
                        messageId = messageId,
                        senderId = senderId,
                        senderRole = senderRole,
                        messageType = msgType,
                        sequenceNumber = seq,
                        timestampNs = ts,
                        payload = payloadMap
                    )
                    onEnvelopeReceived(env)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                onStateChanged(RemoteClientState.DISCONNECTED)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                onStateChanged(RemoteClientState.DISCONNECTED)
            }
        })
    }

    override fun send(envelope: RemoteEnvelope): Boolean {
        val ws = activeWs ?: return false
        if (!connected) return false
        return try {
            val json = JSONObject().apply {
                put("version", envelope.version)
                put("session_id", envelope.sessionId)
                put("message_id", envelope.messageId)
                put("sender_id", envelope.senderId)
                put("sender_role", envelope.senderRole)
                put("message_type", envelope.messageType.name)
                put("sequence_number", envelope.sequenceNumber)
                put("timestamp_ns", envelope.timestampNs)
                put("payload", JSONObject(envelope.payload))
            }
            ws.send(json.toString())
        } catch (e: Exception) {
            false
        }
    }

    override fun disconnect() {
        connected = false
        activeWs?.close(1000, "Client disconnect")
        activeWs = null
    }

    override fun isConnected(): Boolean = connected
}

/**
 * Authoritative remote session coordinator for the patient Android APK (CR-002).
 */
class RemoteSessionClient(
    var sessionId: String = "",
    val localDeviceId: String = "PHONE-PATIENT-VR",
    var transport: RemoteWebSocketTransport? = null,
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
    var onResponseListener: ((source: String) -> Unit)? = null
    var onEmergencyStopListener: ((reason: String) -> Unit)? = null
    var onPeerStatusListener: ((roles: List<String>, physician: String?) -> Unit)? = null
    var onStateChangedListener: ((RemoteClientState) -> Unit)? = null
    var onWebRtcAnswerListener: ((sdp: String) -> Unit)? = null
    var onIceCandidateListener: ((candidate: String, sdpMid: String?, sdpMLineIndex: Int?) -> Unit)? = null
    var onStreamStatusListener: ((isStreaming: Boolean, width: Int, height: Int) -> Unit)? = null

    fun getState(): RemoteClientState = state

    fun getPendingOutboxCount(): Int = outboxBuffer.size

    fun getNextSequence(): Long = sequenceCounter.incrementAndGet()

    /**
     * Connects to the remote session broker using an ephemeral session token.
     * Sets state to CONNECTING and sends AUTH envelope.
     * State only transitions to AUTHENTICATED upon receiving valid AUTH_ACK.
     */
    fun startRemoteSession(endpointUrl: String, authToken: String): Boolean {
        state = RemoteClientState.CONNECTING
        val currentTransport = transport ?: OkHttpRemoteWebSocketTransport(
            onEnvelopeReceived = { env -> processIncomingEnvelope(env) },
            onStateChanged = { newState ->
                state = newState
                onStateChangedListener?.invoke(newState)
            }
        ).also { transport = it }

        currentTransport.connect(
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
        return sendEnvelope(authEnv)
    }

    /**
     * Connects directly to the cloud WebSocket broker with query token.
     */
    fun connectToCloud(baseUrl: String, token: String) {
        state = RemoteClientState.CONNECTING
        val cleanHost = baseUrl.replace(Regex("^https?://"), "").trimEnd('/')
        val scheme = if (baseUrl.startsWith("https://")) "wss" else "ws"
        val wsUrl = "$scheme://$cleanHost/api/v1/remote/ws/$sessionId?token=${java.net.URLEncoder.encode(token, "UTF-8")}&client_id=$localDeviceId"
        val okTransport = OkHttpRemoteWebSocketTransport(
            onEnvelopeReceived = { env -> processIncomingEnvelope(env) },
            onStateChanged = { newState ->
                state = newState
                onStateChangedListener?.invoke(newState)
            }
        )
        this.transport = okTransport
        okTransport.connect(wsUrl, emptyMap())
    }

    data class CreatedSession(
        val sessionId: String,
        val pairingPin: String,
        val token: String,
        val wsEndpointUrl: String
    )

    /**
     * Creates a genuine remote session on the backend (POST /api/v1/remote/create).
     */
    fun createRemoteSessionOnBackend(
        baseUrl: String,
        tenantId: String = "TENANT-APEX",
        onSuccess: (CreatedSession) -> Unit,
        onError: (String) -> Unit
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val json = JSONObject().apply {
            put("device_id", localDeviceId)
            put("hardware_model", "OnePlus Nord CE 3 5G")
            put("supported_inputs", org.json.JSONArray(listOf("TOUCH_PAD", "VOLUME_KEYS", "BT_CLICKER")))
            put("tenant_id", tenantId)
        }

        val cleanUrl = baseUrl.trimEnd('/')
        val createUrl = "$cleanUrl/api/v1/remote/create"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(createUrl)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onError(e.message ?: "Network error connecting to backend")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onError("Backend returned HTTP ${response.code}")
                        return
                    }
                    val respBody = response.body?.string() ?: ""
                    try {
                        val respJson = JSONObject(respBody)
                        val newSessionId = respJson.getString("session_id")
                        val pin = respJson.getString("pairing_pin")
                        val token = respJson.getString("patient_vr_token")
                        val wsEndpoint = respJson.optString("ws_endpoint_url", "/api/v1/remote/ws/$newSessionId")
                        this@RemoteSessionClient.sessionId = newSessionId
                        onSuccess(CreatedSession(newSessionId, pin, token, wsEndpoint))
                    } catch (e: Exception) {
                        onError("Parse error: ${e.message}")
                    }
                }
            }
        })
    }

    /**
     * Publishes a stimulus presentation event to the remote physician for live twin-reconstruction.
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
     * Publishes local screen streaming status telemetry (resolution, fps, dropped frames).
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

        val currentTransport = transport
        if (currentTransport != null && currentTransport.isConnected()) {
            return currentTransport.send(envelope)
        }
        return false
    }

    /**
     * Ingests incoming WebSocket envelopes from the broker.
     */
    fun processIncomingEnvelope(envelope: RemoteEnvelope): Result<String> {
        // Enforce session boundary
        if (sessionId.isNotEmpty() && envelope.sessionId != sessionId) {
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
                onStateChangedListener?.invoke(RemoteClientState.AUTHENTICATED)
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
                val src = (envelope.payload["input_source"] as? String)
                    ?: (envelope.payload["source"] as? String ?: "REMOTE_PAD")
                onResponseListener?.invoke(src)
                return Result.success("EXTERNAL_RESPONSE_RECEIVED: $src")
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
