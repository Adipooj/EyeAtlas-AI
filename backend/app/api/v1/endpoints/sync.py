"""EyeAtlas VR - Offline-First Synchronization Endpoints.
Receives batched offline transactions, verifies idempotency, applies deterministic
reconciliation, and returns conflict reports.
"""
from __future__ import annotations
import json
import time
from typing import Any, Dict, List
from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.security import require_roles
from backend.app.services.sync_service import (
    SyncBatchPayload, SyncBatchResult, process_sync_batch
)
from backend.app.models.entities import PatientEntity, SessionEntity

router = APIRouter(prefix="/sync", tags=["Synchronization"])

@router.post("/push", response_model=SyncBatchResult)
def push_sync_batch(
    payload: SyncBatchPayload,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Ingest an offline-recorded batch of patients, sessions, trials, and measurements."""
    tenant_id = user["tenant_id"]
    actor = user.get("username", "DEVICE_SYNC")
    return process_sync_batch(db=db, tenant_id=tenant_id, actor=actor, payload=payload)

@router.get("/pull")
def pull_delta_changes(
    since_timestamp_ns: int = Query(0, alias="sinceTimestampNs"),
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Download changes since a specified timestamp for bidirectional offline sync."""
    tenant_id = user["tenant_id"]
    patients = db.query(PatientEntity).filter(
        PatientEntity.tenant_id == tenant_id,
        PatientEntity.is_deleted == False
    ).all()

    return {
        "serverTimestampNs": int(time.time() * 1_000_000_000),
        "patients": [
            {
                "patientId": p.patient_id,
                "ageBand": p.age_band,
                "hasDiabetes": p.has_diabetes,
                "symptoms": json.loads(p.symptoms_json) if p.symptoms_json else [],
                "version": p.version
            }
            for p in patients
        ]
    }
