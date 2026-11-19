package org.eyeatlas.net

data class ClockSyncSample(
    val sampleId: String,
    val t0Ns: Long, // Phone A transmit monotonic
    val t1Ns: Long, // Phone B receive monotonic
    val t2Ns: Long, // Phone B transmit monotonic
    val t3Ns: Long  // Phone A receive monotonic
) {
    val roundTripDelayNs: Long
        get() = (t3Ns - t0Ns) - (t2Ns - t1Ns)

    val clockOffsetNs: Long
        get() = ((t1Ns - t0Ns) + (t2Ns - t3Ns)) / 2

    val uncertaintyNs: Long
        get() = roundTripDelayNs / 2
}

class ClockSyncEngine(
    val maxAllowedUncertaintyNs: Long = 50_000_000L // 50ms tolerance threshold
) {
    private val samples = mutableListOf<ClockSyncSample>()

    val sampleCount: Int
        get() = samples.size

    val isSynchronized: Boolean
        get() = samples.isNotEmpty()

    val currentOffsetNs: Long
        get() {
            if (samples.isEmpty()) return 0L
            // Best estimate is the sample with lowest round trip delay (least jitter)
            return samples.minByOrNull { it.roundTripDelayNs }?.clockOffsetNs ?: 0L
        }

    val currentUncertaintyNs: Long
        get() {
            if (samples.isEmpty()) return Long.MAX_VALUE
            return samples.minByOrNull { it.roundTripDelayNs }?.uncertaintyNs ?: Long.MAX_VALUE
        }

    fun isUncertaintyAcceptable(): Boolean {
        if (!isSynchronized) return false
        return currentUncertaintyNs <= maxAllowedUncertaintyNs
    }

    fun addSample(sample: ClockSyncSample) {
        samples.add(sample)
        // Keep a rolling window of recent samples
        if (samples.size > 20) {
            samples.removeAt(0)
        }
    }

    /**
     * Corrects a remote Phone B monotonic timestamp to Phone A local monotonic reference.
     * Phone A Reference = Phone B Timestamp - Offset
     */
    fun correctRemoteTimestamp(remoteMonotonicNs: Long): Long {
        return remoteMonotonicNs - currentOffsetNs
    }

    fun reset() {
        samples.clear()
    }
}
