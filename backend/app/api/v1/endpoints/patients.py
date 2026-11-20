"""EyeAtlas VR - Patient Management Endpoints.
Enforces multi-tenant isolation, role checks, and DPDP/GDPR compliant export/deletion.
"""
from __future__ import annotations
import json
import uuid
from typing import Any, Dict, List
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.security import get_current_user_payload, require_roles
from backend.app.models.contracts import PatientRecord
from backend.app.models.entities import PatientEntity
from backend.app.services.audit_service import record_audit_event

router = APIRouter(prefix="/patients", tags=["Patients"])

@router.get("", response_model=List[PatientRecord])
def list_patients(
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Retrieve patients strictly scoped to the authenticated user's tenant."""
    tenant_id = user["tenant_id"]
    entities = db.query(PatientEntity).filter(
        PatientEntity.tenant_id == tenant_id,
        PatientEntity.is_deleted == False
    ).offset(skip).limit(limit).all()

    results = []
    for e in entities:
        results.append(PatientRecord(
            patient_id=e.patient_id,
            age_band=e.age_band,
            has_diabetes=e.has_diabetes,
            symptoms=json.loads(e.symptoms_json) if e.symptoms_json else [],
            current_correction=e.current_correction,
            comorbidity_flags=json.loads(e.comorbidity_flags_json) if e.comorbidity_flags_json else {}
        ))
    return results

@router.post("", response_model=PatientRecord, status_code=status.HTTP_201_CREATED)
def create_patient(
    patient: PatientRecord,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Register a new patient record within the user's assigned tenant."""
    tenant_id = user["tenant_id"]

    existing = db.query(PatientEntity).filter(
        PatientEntity.patient_id == patient.patient_id,
        PatientEntity.tenant_id == tenant_id
    ).first()

    if existing and not existing.is_deleted:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Patient already exists")

    new_entity = PatientEntity(
        patient_id=patient.patient_id,
        tenant_id=tenant_id,
        age_band=patient.age_band,
        has_diabetes=patient.has_diabetes,
        symptoms_json=json.dumps(patient.symptoms),
        current_correction=patient.current_correction.value,
        comorbidity_flags_json=json.dumps(patient.comorbidity_flags),
        consent_granted=True,
        version=1
    )
    db.add(new_entity)

    record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "UNKNOWN"),
        device_id="WEB_PORTAL",
        action="CREATE_PATIENT",
        before_data=None,
        after_data={"patient_id": patient.patient_id, "tenant_id": tenant_id},
        reason="Clinical intake via API"
    )

    db.commit()
    db.refresh(new_entity)
    return patient

@router.get("/{patient_id}", response_model=PatientRecord)
def get_patient(
    patient_id: str,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Retrieve patient record with cross-tenant isolation enforcement."""
    tenant_id = user["tenant_id"]
    entity = db.query(PatientEntity).filter(
        PatientEntity.patient_id == patient_id,
        PatientEntity.tenant_id == tenant_id,
        PatientEntity.is_deleted == False
    ).first()

    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Patient not found")

    return PatientRecord(
        patient_id=entity.patient_id,
        age_band=entity.age_band,
        has_diabetes=entity.has_diabetes,
        symptoms=json.loads(entity.symptoms_json) if entity.symptoms_json else [],
        current_correction=entity.current_correction,
        comorbidity_flags=json.loads(entity.comorbidity_flags_json) if entity.comorbidity_flags_json else {}
    )

@router.delete("/{patient_id}")
def delete_patient(
    patient_id: str,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("ADMINISTRATOR", "TENANT_ADMIN"))
):
    """GDPR/DPDP compliant soft-delete and anonymization."""
    tenant_id = user["tenant_id"]
    entity = db.query(PatientEntity).filter(
        PatientEntity.patient_id == patient_id,
        PatientEntity.tenant_id == tenant_id,
        PatientEntity.is_deleted == False
    ).first()

    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Patient not found")

    before_data = {"patient_id": entity.patient_id, "is_deleted": False}
    entity.is_deleted = True
    entity.symptoms_json = "[]"
    entity.comorbidity_flags_json = "{}"

    record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "UNKNOWN"),
        device_id="API",
        action="ANONYMIZE_DELETE_PATIENT",
        before_data=before_data,
        after_data={"patient_id": entity.patient_id, "is_deleted": True},
        reason="Patient consent withdrawal / DPDP request"
    )

    db.commit()
    return {"status": "SUCCESS", "message": f"Patient {patient_id} anonymized and deleted"}
