package org.eyeatlas.core

enum class EyeLaterality {
    OD, // Right eye
    OS  // Left eye
}

enum class TestDomain {
    ACUITY,
    COLOUR_CONTRAST,
    VISUAL_FIELD,
    ANTERIOR,
    RETINAL,
    BINOCULAR
}

data class PatientRecord(
    val id: String,
    val timestamp: Long,
    val notes: String = ""
)
