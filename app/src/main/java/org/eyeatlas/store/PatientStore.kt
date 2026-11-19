package org.eyeatlas.store

import org.eyeatlas.core.PatientRecord

class PatientStore {
    fun getRecord(id: String): PatientRecord = PatientRecord(
        patientId = id,
        ageBand = "40-59",
        hasDiabetes = false,
        symptoms = listOf("NONE"),
        currentCorrection = "NONE"
    )
}
