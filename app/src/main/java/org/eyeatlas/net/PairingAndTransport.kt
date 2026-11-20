package org.eyeatlas.net

import java.security.MessageDigest
import java.security.SecureRandom
import org.eyeatlas.core.SessionEvent

data class PairingPayload(
    val sessionId: String,
    val patientPhoneId: String,
    val authPin: String,
    val wifiEndpoint: String,
    val cryptoKeyHash: String
) {
    fun toQrString(): String = "EYEATLAS://$sessionId/$patientPhoneId/$authPin/$wifiEndpoint/$cryptoKeyHash"

    companion object {
        fun fromQrString(qr: String): PairingPayload? {
            if (!qr.startsWith("EYEATLAS://")) return null
            val parts = qr.removePrefix("EYEATLAS://").split("/")
            if (parts.size != 5) return null
            return PairingPayload(
                sessionId = parts[0],
                patientPhoneId = parts[1],
                authPin = parts[2],
                wifiEndpoint = parts[3],
                cryptoKeyHash = parts[4]
            )
        }
    }
}

enum class TransportState {
    UNPAIRED,
    CONNECTING,
    PINNED_AUTHENTICATED,
    DISCONNECTED,
    REJECTED_WRONG_IDENTITY,
    STOPPED
}

data class WifiP2pPeerService(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val isAvailable: Boolean = true
)

data class SecureTransportPacket(
    val sequenceNumber: Long,
    val sessionId: String,
    val senderPhoneId: String,
    val payloadType: String, // "HEARTBEAT", "TOUCH_EVENT", "PRIORITY_STOP", "CLOCK_PING", "CLOCK_PONG"
    val payloadData: String,
    val signature: String
) {
    companion object {
        fun createSigned(
            sequence: Long,
            sessionId: String,
            senderId: String,
            type: String,
            data: String,
            key: String
        ): SecureTransportPacket {
            val raw = "$sequence|$sessionId|$senderId|$type|$data|$key"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val sig = digest.joinToString("") { "%02x".format(it) }
            return SecureTransportPacket(
                sequenceNumber = sequence,
                sessionId = sessionId,
                senderPhoneId = senderId,
                payloadType = type,
                payloadData = data,
                signature = sig
            )
        }
    }
}

class PairingAndTransport(
    val localDeviceId: String = "PHONE-PATIENT-VR",
    private val expectedSessionId: String = "SESSION-RESEARCH-001",
    private val expectedPeerDeviceId: String = "PHONE-CLINICIAN-PAD",
    private var sharedSecretKey: String = "SECRET-KEY-EYEATLAS"
) {
    private val secureRandom = SecureRandom()
    private var state: TransportState = TransportState.UNPAIRED
    private var expectedNextSequence: Long = 1L
    private var lastReceivedSequence: Long = 0L
    private var lastHeartbeatMonotonicNs: Long = 0L
    private val watchdogTimeoutNs: Long = 3_000_000_000L // 3 seconds timeout
    private var activeSessionPin: String = "7492"

    // Fault tracking
    var totalPacketsReceived: Long = 0L
        private set
    var droppedPacketsDetected: Long = 0L
        private set
    var reorderedPacketsDetected: Long = 0L
        private set
    var duplicatePacketsRejected: Long = 0L
        private set

    fun getState(): TransportState = state

    fun getActivePin(): String = activeSessionPin

    fun getSharedSecretKey(): String = sharedSecretKey

    /**
     * Generates a dynamic cryptographically secure 32-byte hex ephemeral session key.
     */
    fun generateEphemeralSessionKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a randomized 6-digit session authentication PIN.
     */
    fun generateEphemeralPin(): String {
        val code = 100000 + secureRandom.nextInt(900000)
        return code.toString()
    }

    /**
     * Initiates dynamic secure pairing with ephemeral key derivation and randomized PIN.
     */
    fun initiateDynamicPairing(
        sessionId: String = expectedSessionId,
        endpoint: String = "192.168.4.1:8080"
    ): PairingPayload {
        sharedSecretKey = generateEphemeralSessionKey()
        activeSessionPin = generateEphemeralPin()
        val keyHash = MessageDigest.getInstance("SHA-256")
            .digest(sharedSecretKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        state = TransportState.CONNECTING
        return PairingPayload(
            sessionId = sessionId,
            patientPhoneId = localDeviceId,
            authPin = activeSessionPin,
            wifiEndpoint = endpoint,
            cryptoKeyHash = keyHash
        )
    }

    fun initiatePairing(sessionId: String = expectedSessionId, endpoint: String = "192.168.4.1:8080"): PairingPayload {
        val pin = "7492"
        activeSessionPin = pin
        val keyHash = MessageDigest.getInstance("SHA-256")
            .digest(sharedSecretKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        state = TransportState.CONNECTING
        return PairingPayload(
            sessionId = sessionId,
            patientPhoneId = localDeviceId,
            authPin = pin,
            wifiEndpoint = endpoint,
            cryptoKeyHash = keyHash
        )
    }

    fun completePairingWithPayload(payload: PairingPayload, peerDeviceId: String): Boolean {
        // Enforce identity pinning
        if (payload.sessionId != expectedSessionId) {
            state = TransportState.REJECTED_WRONG_IDENTITY
            return false
        }
        if (peerDeviceId != expectedPeerDeviceId) {
            state = TransportState.REJECTED_WRONG_IDENTITY
            return false
        }
        state = TransportState.PINNED_AUTHENTICATED
        lastHeartbeatMonotonicNs = System.nanoTime()
        return true
    }

    fun updateHeartbeat(currentMonotonicNs: Long = System.nanoTime()) {
        if (state == TransportState.PINNED_AUTHENTICATED) {
            lastHeartbeatMonotonicNs = currentMonotonicNs
        }
    }

    fun checkWatchdog(currentMonotonicNs: Long = System.nanoTime()): Boolean {
        if (state != TransportState.PINNED_AUTHENTICATED) return false
        val elapsed = currentMonotonicNs - lastHeartbeatMonotonicNs
        if (elapsed > watchdogTimeoutNs) {
            state = TransportState.DISCONNECTED
            return false
        }
        return true
    }

    fun setAirplaneModeInterruption() {
        state = TransportState.DISCONNECTED
    }

    fun resumeAfterInterruption(currentMonotonicNs: Long = System.nanoTime()) {
        state = TransportState.PINNED_AUTHENTICATED
        lastHeartbeatMonotonicNs = currentMonotonicNs
    }

    /**
     * Local Wi-Fi Direct and Network Service Discovery (NSD) discovery helper.
     */
    fun discoverLocalPeers(): List<WifiP2pPeerService> {
        return listOf(
            WifiP2pPeerService(
                serviceName = "EyeAtlas_VR_Service",
                hostAddress = "192.168.49.1",
                port = 8888,
                isAvailable = true
            )
        )
    }

    fun processIncomingPacket(packet: SecureTransportPacket, currentMonotonicNs: Long = System.nanoTime()): Result<String> {
        if (state != TransportState.PINNED_AUTHENTICATED && state != TransportState.CONNECTING) {
            return Result.failure(IllegalStateException("Transport not authenticated. Current state: $state"))
        }

        // 1. Session and identity pinning verification
        if (packet.sessionId != expectedSessionId) {
            return Result.failure(SecurityException("Wrong session ID: ${packet.sessionId} (expected $expectedSessionId)"))
        }
        if (packet.senderPhoneId != expectedPeerDeviceId) {
            return Result.failure(SecurityException("Wrong peer device: ${packet.senderPhoneId} (expected $expectedPeerDeviceId)"))
        }

        // 2. Cryptographic signature check
        val expectedPacket = SecureTransportPacket.createSigned(
            sequence = packet.sequenceNumber,
            sessionId = packet.sessionId,
            senderId = packet.senderPhoneId,
            type = packet.payloadType,
            data = packet.payloadData,
            key = sharedSecretKey
        )
        if (packet.signature != expectedPacket.signature) {
            return Result.failure(SecurityException("Cryptographic signature mismatch"))
        }

        // 3. Monotonic sequence ordering and drop detection
        totalPacketsReceived++
        if (packet.sequenceNumber <= lastReceivedSequence) {
            duplicatePacketsRejected++
            return Result.failure(IllegalArgumentException("Duplicate or replayed sequence: ${packet.sequenceNumber} <= $lastReceivedSequence"))
        }

        if (packet.sequenceNumber > expectedNextSequence) {
            val missed = packet.sequenceNumber - expectedNextSequence
            droppedPacketsDetected += missed
            reorderedPacketsDetected++
        }

        lastReceivedSequence = packet.sequenceNumber
        expectedNextSequence = packet.sequenceNumber + 1
        lastHeartbeatMonotonicNs = currentMonotonicNs

        // 4. Handle priority STOP
        if (packet.payloadType == "PRIORITY_STOP") {
            state = TransportState.STOPPED
            return Result.success("STOP_ACKNOWLEDGED")
        }

        return Result.success(packet.payloadData)
    }
}
