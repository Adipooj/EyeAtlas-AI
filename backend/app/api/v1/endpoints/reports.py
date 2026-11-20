"""EyeAtlas VR - Clinical Report & Governance Endpoints.
Compiles multi-morbidity reports with CDSCO regulatory status annotations.
"""
from __future__ import annotations
import hashlib
import json
import time
from typing import Any, Dict
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session
from backend.app.core.config import settings
from backend.app.core.database import get_db
from backend.app.core.security import require_roles
from backend.app.models.entities import SessionEntity, PatientEntity
from backend.app.services.audit_service import record_audit_event

router = APIRouter(prefix="/reports", tags=["Reports"])

class SignReportRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    clinician_notes: str = Field(..., alias="clinicianNotes")
    decision: str  # CONFIRMED_REFERRAL, ROUTINE_REVIEW, OVERRIDDEN


@router.get("/{session_id}")
def generate_report(
    session_id: str,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Retrieve full structured clinical screening report."""
    tenant_id = user["tenant_id"]
    s = db.query(SessionEntity).filter(
        SessionEntity.session_id == session_id,
        SessionEntity.tenant_id == tenant_id
    ).first()

    if not s:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    p = db.query(PatientEntity).filter(PatientEntity.patient_id == s.patient_id).first()

    # Calculate report digest
    report_body = {
        "sessionId": s.session_id,
        "patientId": s.patient_id,
        "state": s.state,
        "findings": [f.finding for f in s.findings],
        "inferences": [f"{inf.eye}:{inf.disease_code}:{inf.probability_or_score}" for inf in s.inferences],
        "referrals": [r.urgency for r in s.referrals]
    }
    digest = hashlib.sha256(json.dumps(report_body, sort_keys=True).encode("utf-8")).hexdigest()

    return {
        "reportId": f"REP-{s.session_id[:8]}",
        "sessionId": s.session_id,
        "patientId": s.patient_id,
        "patientAgeBand": p.age_band if p else "UNKNOWN",
        "hasDiabetes": p.has_diabetes if p else False,
        "mode": s.mode,
        "reportDigestSha256": digest,
        "regulatoryStatus": settings.REGULATORY_STATUS,
        "isReleaseApproved": settings.IS_RELEASE_APPROVED,
        "findingsCount": len(s.findings),
        "inferencesCount": len(s.inferences),
        "inferences": [
            {
                "eye": inf.eye,
                "diseaseCode": inf.disease_code,
                "score": inf.probability_or_score,
                "calibration": inf.calibration_id
            }
            for inf in s.inferences
        ],
        "referrals": [
            {
                "urgency": r.urgency,
                "reason": r.reason
            }
            for r in s.referrals
        ]
    }

@router.post("/{session_id}/sign")
def sign_report(
    session_id: str,
    request: SignReportRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN"))
):
    """Electronically sign and seal clinical screening report."""
    tenant_id = user["tenant_id"]
    s = db.query(SessionEntity).filter(
        SessionEntity.session_id == session_id,
        SessionEntity.tenant_id == tenant_id
    ).first()

    if not s:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    event = record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "CLINICIAN"),
        device_id="WEB_PORTAL",
        action="SIGN_CLINICAL_REPORT",
        before_data={"sessionId": session_id, "status": "UNSIGNED"},
        after_data={"sessionId": session_id, "status": "SIGNED", "decision": request.decision},
        reason=f"Clinician sign-off: {request.clinician_notes}"
    )

    return {
        "status": "SIGNED",
        "sessionId": session_id,
        "auditEventId": event.event_id,
        "signedAt": event.timestamp_ns
    }
