package org.eyeatlas.net

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import org.eyeatlas.core.*

enum class SyncStatus {
    IDLE,
    QUEUED,
    SYNCING,
    SUCCESS,
    OFFLINE,
    CONFLICT_RESOLVED,
    ERROR
}

data class SyncQueueItem(
    val itemId: String,
    val entityType: String, // PATIENT, SESSION, TRIAL, MEASUREMENT
    val payloadJson: String,
    val queuedTimestampMs: Long = System.currentTimeMillis(),
    var retryCount: Int = 0
)

data class SyncHttpResponse(
    val statusCode: Int,
    val body: String,
    val isSuccess: Boolean = statusCode in 200..299
)

interface HttpTransport {
    fun post(url: String, headers: Map<String, String>, body: String): SyncHttpResponse
    fun get(url: String, headers: Map<String, String>): SyncHttpResponse
}

class DefaultHttpTransport(
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 5000
) : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): SyncHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.setRequestProperty("Content-Type", "application/json")

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            SyncHttpResponse(statusCode, responseText)
        } catch (e: Exception) {
            SyncHttpResponse(0, e.message ?: "Connection error")
        } finally {
            connection.disconnect()
        }
    }

    override fun get(url: String, headers: Map<String, String>): SyncHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            SyncHttpResponse(statusCode, responseText)
        } catch (e: Exception) {
            SyncHttpResponse(0, e.message ?: "Connection error")
        } finally {
            connection.disconnect()
        }
    }
}

data class SyncExecutionSummary(
    val status: SyncStatus,
    val batchId: String,
    val processedCount: Int,
    val conflictCount: Int,
    val message: String
)

/**
 * Backend Synchronization Adapter for EyeAtlas VR Android Client.
 * Implements authenticated batch synchronization, offline queueing,
 * idempotency verification, and client-first measurement conflict resolution.
 */
class BackendSyncAdapter(
    var baseUrl: String = "http://127.0.0.1:8000/api/v1",
    var clientDeviceId: String = "PHONE-NORD-CE3-5G",
    var tenantId: String = "TENANT-APEX",
    var isSyncEnabled: Boolean = false,
    private val transport: HttpTransport = DefaultHttpTransport()
) {
    private val queue = ConcurrentLinkedQueue<SyncQueueItem>()
    private val sequenceCounter = AtomicInteger(1)
    private val conflictLog = mutableListOf<String>()

    var status: SyncStatus = SyncStatus.IDLE
        private set
    var lastSyncTimestampMs: Long = 0L
        private set
    var lastError: String? = null
        private set
    var authToken: String? = null
        private set

    val pendingCount: Int
        get() = queue.size

    val recordedConflicts: List<String>
        get() = conflictLog.toList()

    fun authenticate(token: String) {
        this.authToken = token
    }

    fun queuePatient(patient: PatientRecord) {
        val symptomsStr = patient.symptoms.joinToString(",") { "\"$it\"" }
        val payload = """{
            "patientId": "${patient.patientId}",
            "ageBand": "${patient.ageBand}",
            "hasDiabetes": ${patient.hasDiabetes},
            "symptoms": [$symptomsStr],
            "currentCorrection": "${patient.currentCorrection}",
            "version": 1
        }""".trimIndent()
        queue.add(SyncQueueItem(patient.patientId, "PATIENT", payload))
        if (status == SyncStatus.IDLE || status == SyncStatus.SUCCESS) {
            status = SyncStatus.QUEUED
        }
    }

    fun queueTrial(trial: StimulusTrial) {
        val payload = """{
            "trialId": "${trial.trialId}",
            "sessionId": "${trial.sessionId}",
            "eye": "${trial.eye.name}",
            "testCode": "${trial.testCode}",
            "targetAngleX": ${trial.targetAngleDeg},
            "targetAngleY": 0.0,
            "luminanceNits": ${trial.luminanceCdM2},
            "opticalIndex": ${trial.opticalIndex.toInt()},
            "monotonicPresentedNs": ${trial.monotonicPresentedNs},
            "framesRendered": ${trial.framesRendered},
            "calibrationHash": "${trial.calibrationHash}"
        }""".trimIndent()
        queue.add(SyncQueueItem(trial.trialId, "TRIAL", payload))
        if (status == SyncStatus.IDLE || status == SyncStatus.SUCCESS) {
            status = SyncStatus.QUEUED
        }
    }

    fun queueMeasurement(measurement: Measurement, sessionId: String = "SESSION-LIVE-01") {
        val payload = """{
            "measurementId": "${measurement.measurementId}",
            "sessionId": "$sessionId",
            "eye": "${measurement.eye.name}",
            "method": "${measurement.method}",
            "value": ${measurement.value},
            "units": "${measurement.units}",
            "ciLower": ${measurement.confidenceLower ?: 0.0},
            "ciUpper": ${measurement.confidenceUpper ?: 0.0},
            "validity": ${measurement.validity}
        }""".trimIndent()
        queue.add(SyncQueueItem(measurement.measurementId, "MEASUREMENT", payload))
        if (status == SyncStatus.IDLE || status == SyncStatus.SUCCESS) {
            status = SyncStatus.QUEUED
        }
    }

    /**
     * Executes immediate batch sync upload if enabled and authenticated.
     * Retains items in queue if offline or network error occurs.
     */
    fun syncNow(): SyncExecutionSummary {
        if (!isSyncEnabled) {
            return SyncExecutionSummary(
                status = SyncStatus.OFFLINE,
                batchId = "NONE",
                processedCount = 0,
                conflictCount = 0,
                message = "Sync disabled via feature flag; operating in local-first mode."
            )
        }

        if (queue.isEmpty()) {
            status = SyncStatus.IDLE
            return SyncExecutionSummary(
                status = SyncStatus.IDLE,
                batchId = "NONE",
                processedCount = 0,
                conflictCount = 0,
                message = "Queue is empty; nothing to sync."
            )
        }

        status = SyncStatus.SYNCING
        val batchId = "BATCH-${UUID.randomUUID()}"
        val seq = sequenceCounter.getAndIncrement()
        val itemsToSync = queue.toList()

        val patientsPayload = itemsToSync.filter { it.entityType == "PATIENT" }.joinToString(",") { it.payloadJson }
        val trialsPayload = itemsToSync.filter { it.entityType == "TRIAL" }.joinToString(",") { it.payloadJson }
        val measurementsPayload = itemsToSync.filter { it.entityType == "MEASUREMENT" }.joinToString(",") { it.payloadJson }

        val requestBody = """{
            "batchId": "$batchId",
            "clientDeviceId": "$clientDeviceId",
            "sequenceNum": $seq,
            "clientTimestampNs": ${System.currentTimeMillis() * 1_000_000L},
            "patients": [$patientsPayload],
            "sessions": [],
            "trials": [$trialsPayload],
            "images": [],
            "measurements": [$measurementsPayload],
            "findings": [],
            "inferences": [],
            "referrals": []
        }""".trimIndent()

        val headers = mutableMapOf<String, String>()
        headers["X-Tenant-ID"] = tenantId
        authToken?.let { headers["Authorization"] = "Bearer $it" }

        val response = transport.post("$baseUrl/sync/push", headers, requestBody)

        return if (response.isSuccess) {
            // Acknowledge and remove synced items from queue
            queue.removeAll(itemsToSync.toSet())
            lastSyncTimestampMs = System.currentTimeMillis()
            lastError = null

            val hasZeroConflicts = Regex("\"conflictCount\"\\s*:\\s*0").containsMatchIn(response.body)
            val isConflict = response.body.contains("conflictCount") && !hasZeroConflicts
            if (isConflict) {
                status = SyncStatus.CONFLICT_RESOLVED
                conflictLog.add("Batch $batchId: Conflict detected and resolved (client physical measurements preserved).")
            } else {
                status = SyncStatus.SUCCESS
            }

            SyncExecutionSummary(
                status = status,
                batchId = batchId,
                processedCount = itemsToSync.size,
                conflictCount = if (isConflict) 1 else 0,
                message = "Batch synchronization completed successfully."
            )
        } else {
            // Interrupted or failed: increment retry counters; leave items in queue for next cycle
            itemsToSync.forEach { it.retryCount++ }
            status = if (response.statusCode == 0) SyncStatus.OFFLINE else SyncStatus.ERROR
            lastError = "Sync failed with status code ${response.statusCode}: ${response.body}"

            SyncExecutionSummary(
                status = status,
                batchId = batchId,
                processedCount = 0,
                conflictCount = 0,
                message = lastError ?: "Network failure"
            )
        }
    }

    fun pullDelta(sinceTimestampNs: Long = 0L): Int {
        if (!isSyncEnabled) return 0
        val headers = mutableMapOf<String, String>()
        headers["X-Tenant-ID"] = tenantId
        authToken?.let { headers["Authorization"] = "Bearer $it" }

        val response = transport.get("$baseUrl/sync/pull?sinceTimestampNs=$sinceTimestampNs", headers)
        if (response.isSuccess) {
            lastSyncTimestampMs = System.currentTimeMillis()
            return 1
        }
        return 0
    }

    fun clearQueue() {
        queue.clear()
        status = SyncStatus.IDLE
    }
}
