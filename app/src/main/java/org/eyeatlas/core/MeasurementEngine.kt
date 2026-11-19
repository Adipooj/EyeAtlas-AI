package org.eyeatlas.core

class MeasurementEngine {
    private val measurements = mutableListOf<Measurement>()

    fun recordMeasurement(measurement: Measurement): Boolean {
        // Physical plausibility checks
        val isPlausible = when (measurement.method) {
            "ACUITY_LOGMAR" -> measurement.value in -0.3..2.5
            "PERIMETRY_54PT" -> measurement.value in 0.0..54.0
            "MEDIA_OPACITY" -> measurement.value in 0.0..1.0
            else -> true
        }
        val validated = measurement.copy(validity = isPlausible && measurement.validity)
        measurements.add(validated)
        return isPlausible
    }

    fun getMeasurementsForEye(eye: EyeLaterality): List<Measurement> {
        return measurements.filter { it.eye == eye }
    }

    fun clear() {
        measurements.clear()
    }
}
