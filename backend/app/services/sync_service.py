"""EyeAtlas VR - Offline-First Sync & Conflict Resolution Service.
Provides idempotent batch ingestion, duplicate prevention, deterministic reconciliation,
and client-first clinical measurement preservation.
"""
from __future__ import annotations
import json
import time
import uuid
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session
from backend.app.models.entities import (
    PatientEntity, SessionEntity, StimulusTrialEntity, ImageCaptureEntity,
    MeasurementEntity, FindingEntity, ConditionInferenceEntity, ReferralEntity,
    SyncRecordEntity
)
from backend.app.services.audit_service import record_audit_event

class SyncBatchPayload(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    batch_id: str = Field(..., alias="batchId")
    client_device_id: str = Field(..., alias="clientDeviceId")
    sequence_num: int = Field(..., alias="sequenceNum")
    client_timestamp_ns: int = Field(..., alias="clientTimestampNs")
    patients: List[Dict[str, Any]] = Field(default_factory=list)
    sessions: List[Dict[str, Any]] = Field(default_factory=list)
    trials: List[Dict[str, Any]] = Field(default_factory=list)
    images: List[Dict[str, Any]] = Field(default_factory=list)
    measurements: List[Dict[str, Any]] = Field(default_factory=list)
    findings: List[Dict[str, Any]] = Field(default_factory=list)
    inferences: List[Dict[str, Any]] = Field(default_factory=list)
    referrals: List[Dict[str, Any]] = Field(default_factory=list)

class SyncBatchResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    status: str
    batch_id: str = Field(..., alias="batchId")
    processed_count: int = Field(..., alias="processedCount")
    conflict_count: int = Field(..., alias="conflictCount")
    server_timestamp_ns: int = Field(..., alias="serverTimestampNs")
    conflicts: List[Dict[str, Any]] = Field(default_factory=list)

def process_sync_batch(
    db: Session,
    tenant_id: str,
    actor: str,
    payload: SyncBatchPayload
) -> SyncBatchResult:
    """Process an offline batch upload with idempotency and conflict resolution."""
    now_ns = int(time.time() * 1_000_000_000)

    # 1. Idempotency Check
    existing_sync = db.query(SyncRecordEntity).filter(
        SyncRecordEntity.client_device_id == payload.client_device_id,
        SyncRecordEntity.batch_id == payload.batch_id
    ).first()

    if existing_sync:
        # Idempotent response: batch previously processed, return success with zero duplicate mutations
        conflicts = json.loads(existing_sync.conflicts_detected_json) if existing_sync.conflicts_detected_json else []
        return SyncBatchResult(
            status="IDEMPOTENT_SUCCESS",
            batch_id=payload.batch_id,
            processed_count=0,
            conflict_count=len(conflicts),
            server_timestamp_ns=existing_sync.server_timestamp,
            conflicts=conflicts
        )

    processed_count = 0
    conflicts: List[Dict[str, Any]] = []

    # 2. Process Patients
    for p_data in payload.patients:
        patient_id = p_data.get("patient_id") or p_data.get("patientId")
        if not patient_id:
            continue

        existing_patient = db.query(PatientEntity).filter(
            PatientEntity.patient_id == patient_id,
            PatientEntity.tenant_id == tenant_id
        ).first()

        client_version = p_data.get("version", 1)

        if existing_patient:
            if existing_patient.version > client_version:
                # Concurrent update on server: flag conflict
                conflict_entry = {
                    "entity": "patient",
                    "id": patient_id,
                    "server_version": existing_patient.version,
                    "client_version": client_version,
                    "resolution": "MERGED_SERVER_VERSION_BUMPED"
                }
                conflicts.append(conflict_entry)

            # Update patient data
            before_data = {
                "age_band": existing_patient.age_band,
                "has_diabetes": existing_patient.has_diabetes,
                "version": existing_patient.version
            }
            existing_patient.age_band = p_data.get("age_band") or p_data.get("ageBand", existing_patient.age_band)
            existing_patient.has_diabetes = p_data.get("has_diabetes", p_data.get("hasDiabetes", existing_patient.has_diabetes))
            existing_patient.symptoms_json = json.dumps(p_data.get("symptoms", []))
            existing_patient.current_correction = p_data.get("current_correction") or p_data.get("currentCorrection", existing_patient.current_correction)
            existing_patient.comorbidity_flags_json = json.dumps(p_data.get("comorbidity_flags") or p_data.get("comorbidityFlags", {}))
            existing_patient.version = max(existing_patient.version, client_version) + 1

            record_audit_event(
                db=db,
                tenant_id=tenant_id,
                actor=actor,
                device_id=payload.client_device_id,
                action="SYNC_UPDATE_PATIENT",
                before_data=before_data,
                after_data={"age_band": existing_patient.age_band, "version": existing_patient.version},
                reason=f"Batch {payload.batch_id} reconciliation"
            )
        else:
            # Create new patient
            new_patient = PatientEntity(
                patient_id=patient_id,
                tenant_id=tenant_id,
                age_band=p_data.get("age_band") or p_data.get("ageBand", "ADULT_18_TO_64"),
                has_diabetes=p_data.get("has_diabetes", p_data.get("hasDiabetes", False)),
                symptoms_json=json.dumps(p_data.get("symptoms", [])),
                current_correction=p_data.get("current_correction") or p_data.get("currentCorrection", "UNAIDED"),
                comorbidity_flags_json=json.dumps(p_data.get("comorbidity_flags") or p_data.get("comorbidityFlags", {})),
                consent_granted=p_data.get("consent_granted", p_data.get("consentGranted", True)),
                version=client_version
            )
            db.add(new_patient)
            record_audit_event(
                db=db,
                tenant_id=tenant_id,
                actor=actor,
                device_id=payload.client_device_id,
                action="SYNC_CREATE_PATIENT",
                before_data=None,
                after_data={"patient_id": patient_id, "tenant_id": tenant_id},
                reason=f"Batch {payload.batch_id} creation"
            )
        processed_count += 1

    # 3. Process Sessions
    for s_data in payload.sessions:
        session_id = s_data.get("session_id") or s_data.get("sessionId")
        patient_id = s_data.get("patient_id") or s_data.get("patientId")
        if not session_id or not patient_id:
            continue

        existing_session = db.query(SessionEntity).filter(
            SessionEntity.session_id == session_id,
            SessionEntity.tenant_id == tenant_id
        ).first()

        if not existing_session:
            new_session = SessionEntity(
                session_id=session_id,
                patient_id=patient_id,
                tenant_id=tenant_id,
                patient_phone_id=s_data.get("patient_phone_id") or s_data.get("patientPhoneId", "UNKNOWN"),
                clinician_phone_id=s_data.get("clinician_phone_id") or s_data.get("clinicianPhoneId", "UNKNOWN"),
                response_phone_id=s_data.get("response_phone_id") or s_data.get("responsePhoneId", "UNKNOWN"),
                response_role=s_data.get("response_role") or s_data.get("responseRole", "RESPONSE_PAD"),
                mode=s_data.get("mode", "SIMULATED"),
                state=s_data.get("state", "COMPLETE"),
                consent_granted=s_data.get("consent_granted", s_data.get("consentGranted", True)),
                started_at_ns=s_data.get("started_at_ns") or s_data.get("startedAtNs", now_ns)
            )
            db.add(new_session)
            processed_count += 1

    # 4. Process Trials
    for t_data in payload.trials:
        trial_id = t_data.get("trial_id") or t_data.get("trialId")
        session_id = t_data.get("session_id") or t_data.get("sessionId")
        if not trial_id or not session_id:
            continue

        existing_trial = db.query(StimulusTrialEntity).filter(StimulusTrialEntity.trial_id == trial_id).first()
        if not existing_trial:
            new_trial = StimulusTrialEntity(
                trial_id=trial_id,
                session_id=session_id,
                eye=t_data.get("eye", "OD"),
                test_code=t_data.get("test_code") or t_data.get("testCode", "PERIMETRY_54PT"),
                target_angle_x=float(t_data.get("target_angle_x") or t_data.get("targetAngleX", 0.0)),
                target_angle_y=float(t_data.get("target_angle_y") or t_data.get("targetAngleY", 0.0)),
                luminance_nits=float(t_data.get("luminance_nits") or t_data.get("luminanceNits", 10.0)),
                optical_index=int(t_data.get("optical_index") or t_data.get("opticalIndex", 0)),
                monotonic_presented_ns=int(t_data.get("monotonic_presented_ns") or t_data.get("monotonicPresentedNs", 0)),
                response_phone_monotonic_ns=t_data.get("response_phone_monotonic_ns") or t_data.get("responsePhoneMonotonicNs"),
                corrected_response_ns=t_data.get("corrected_response_ns") or t_data.get("correctedResponseNs"),
                clock_offset_ns=int(t_data.get("clock_offset_ns") or t_data.get("clockOffsetNs", 0)),
                clock_uncertainty_ns=int(t_data.get("clock_uncertainty_ns") or t_data.get("clockUncertaintyNs", 0)),
                expected_answer=t_data.get("expected_answer") or t_data.get("expectedAnswer"),
                observed_answer=t_data.get("observed_answer") or t_data.get("observedAnswer"),
                frames_rendered=int(t_data.get("frames_rendered") or t_data.get("framesRendered", 24)),
                calibration_hash=t_data.get("calibration_hash") or t_data.get("calibrationHash", "HASH"),
                response_event_id=t_data.get("response_event_id") or t_data.get("responseEventId"),
                validity_flags_json=json.dumps(t_data.get("validity_flags") or t_data.get("validityFlags", []))
            )
            db.add(new_trial)
            processed_count += 1

    # 5. Process Measurements
    for m_data in payload.measurements:
        measurement_id = m_data.get("measurement_id") or m_data.get("measurementId")
        session_id = m_data.get("session_id") or m_data.get("sessionId")
        if not measurement_id or not session_id:
            continue

        existing_m = db.query(MeasurementEntity).filter(MeasurementEntity.measurement_id == measurement_id).first()
        if not existing_m:
            new_m = MeasurementEntity(
                measurement_id=measurement_id,
                session_id=session_id,
                eye=m_data.get("eye", "OD"),
                method=m_data.get("method", "UNKNOWN"),
                value=float(m_data.get("value", 0.0)),
                units=m_data.get("units", ""),
                ci_lower=float(m_data.get("ci_lower") or m_data.get("ciLower", 0.0)),
                ci_upper=float(m_data.get("ci_upper") or m_data.get("ciUpper", 0.0)),
                validity=bool(m_data.get("validity", True)),
                limits_json=json.dumps(m_data.get("limits", [])),
                provenance_ids_json=json.dumps(m_data.get("provenance_ids") or m_data.get("provenanceIds", []))
            )
            db.add(new_m)
            processed_count += 1

    # 6. Process Findings
    for f_data in payload.findings:
        session_id = f_data.get("session_id") or f_data.get("sessionId")
        if not session_id:
            continue
        finding_id = f_data.get("finding_id") or str(uuid.uuid4())
        existing_f = db.query(FindingEntity).filter(FindingEntity.finding_id == finding_id).first()
        if not existing_f:
            new_f = FindingEntity(
                finding_id=finding_id,
                session_id=session_id,
                eye=f_data.get("eye", "OD"),
                broad_domain=f_data.get("broad_domain") or f_data.get("broadDomain", "VISUAL_CLARITY"),
                finding=f_data.get("finding", ""),
                direction=f_data.get("direction", "NORMAL"),
                severity=f_data.get("severity", "NONE"),
                validity=bool(f_data.get("validity", True)),
                provenance_ids_json=json.dumps(f_data.get("provenance_ids") or f_data.get("provenanceIds", []))
            )
            db.add(new_f)
            processed_count += 1

    # 7. Process Inferences
    for i_data in payload.inferences:
        session_id = i_data.get("session_id") or i_data.get("sessionId")
        if not session_id:
            continue
        inference_id = i_data.get("inference_id") or str(uuid.uuid4())
        existing_i = db.query(ConditionInferenceEntity).filter(ConditionInferenceEntity.inference_id == inference_id).first()
        if not existing_i:
            new_i = ConditionInferenceEntity(
                inference_id=inference_id,
                session_id=session_id,
                eye=i_data.get("eye", "OD"),
                disease_code=i_data.get("disease_code") or i_data.get("diseaseCode", "OTHER_RETINAL"),
                probability_or_score=float(i_data.get("probability_or_score") or i_data.get("probabilityOrScore", 0.0)),
                calibration_id=i_data.get("calibration_id") or i_data.get("calibrationId", "CALIBRATION_WITHHELD"),
                evidence_for_json=json.dumps(i_data.get("evidence_for") or i_data.get("evidenceFor", [])),
                evidence_against_json=json.dumps(i_data.get("evidence_against") or i_data.get("evidenceAgainst", [])),
                unknown_evidence_json=json.dumps(i_data.get("unknown_evidence") or i_data.get("unknownEvidence", [])),
                recommended_test=i_data.get("recommended_test") or i_data.get("recommendedTest")
            )
            db.add(new_i)
            processed_count += 1

    # Record Sync Transaction Record for Idempotency
    sync_record = SyncRecordEntity(
        sync_id=str(uuid.uuid4()),
        client_device_id=payload.client_device_id,
        tenant_id=tenant_id,
        batch_id=payload.batch_id,
        sequence_num=payload.sequence_num,
        client_timestamp=payload.client_timestamp_ns,
        server_timestamp=now_ns,
        status="SUCCESS",
        conflicts_detected_json=json.dumps(conflicts)
    )
    db.add(sync_record)
    db.commit()

    return SyncBatchResult(
        status="SUCCESS",
        batch_id=payload.batch_id,
        processed_count=processed_count,
        conflict_count=len(conflicts),
        server_timestamp_ns=now_ns,
        conflicts=conflicts
    )
