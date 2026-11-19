package org.eyeatlas.store

import org.eyeatlas.core.PatientRecord

class PatientStore {
    fun getRecord(id: String): PatientRecord = PatientRecord(id, System.currentTimeMillis())
}
