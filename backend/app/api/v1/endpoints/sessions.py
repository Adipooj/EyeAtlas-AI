"""EyeAtlas VR - Clinical Session Endpoints.
Provides session lifecycle, evidence inspection, and multi-disease summary.
"""
from __future__ import annotations
import json
import time
import uuid
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.security import require_roles
from backend.app.models.contracts import ClinicalSession, SessionState, ClinicalMode
from backend.app.models.entities import (
    SessionEntity, PatientEntity, MeasurementEntity, FindingEntity, StimulusTrialEntity
)
from backend.app.services.audit_service import record_audit_event

router = APIRouter(prefix="/sessions", tags=["Sessions"])

class PerimetrySubmitRequest(BaseModel):
    eye: str = "OD"
    pattern: str = "24-2"
    total_points: int = 54
    points_seen: int = 0
    superior_seen: int = 0
    inferior_seen: int = 0
    mean_reaction_time_ms: float = 0.0
    false_positive_rate: float = 0.0
    scotoma_coordinates: List[Dict[str, Any]] = []

class AmslerSubmitRequest(BaseModel):
    eye: str = "OD"
    metamorphopsia: bool = False
    scotoma: bool = False
    distorted_points_count: int = 0
    notes: Optional[str] = None

class AcuitySubmitRequest(BaseModel):
    eye: str = "OD"
    method: str = "TUMBLING_E"
    logmar: float = 0.0
    snellen: str = "6/6"
    contrast_level: str = "100%"

class ColourVisionSubmitRequest(BaseModel):
    eye: str = "OU"
    test_type: str = "ISHIHARA_PSEUDOISOCHROMATIC"
    total_plates: int = 8
    plates_correct: int = 8
    deficiency_detected: bool = False
    classification: str = "NORMAL"
    mode: str = "WEB_2D"

class ContrastSensitivitySubmitRequest(BaseModel):
    eye: str = "OD"
    method: str = "PELLI_ROBSON"
    log_contrast_sensitivity: float = 1.95
    triplets_seen: int = 14
    severity: str = "NORMAL"

class BinocularVisionSubmitRequest(BaseModel):
    worth_four_dot_response: str = "FOUR_DOTS_FUSION"
    stereoacuity_arcsec: float = 40.0
    fusion_status: str = "NORMAL_FUSION"
    depth_perception_grade: str = "NORMAL"

class OcularMotilitySubmitRequest(BaseModel):
    smooth_pursuit_grade: str = "SMOOTH_FULL"
    nystagmus_detected: bool = False
    cardinal_gaze_deficit: bool = False
    affected_muscles: List[str] = []
    notes: Optional[str] = None

class PupillaryAssessmentSubmitRequest(BaseModel):
    rapd_detected: bool = False
    rapd_grade: str = "NONE"
    pupil_size_photopic_od_mm: float = 3.0
    pupil_size_photopic_os_mm: float = 3.0
    pupil_size_scotopic_od_mm: float = 5.5
    pupil_size_scotopic_os_mm: float = 5.5
    anisocoria_present: bool = False
    light_reflex_speed: str = "BRISK"

@router.get("", response_model=List[ClinicalSession])
def list_sessions(
    patient_id: Optional[str] = None,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """List examination sessions scoped to the caller's tenant."""
    tenant_id = user["tenant_id"]
    query = db.query(SessionEntity).filter(SessionEntity.tenant_id == tenant_id)
    if patient_id:
        query = query.filter(SessionEntity.patient_id == patient_id)

    entities = query.order_by(SessionEntity.created_at.desc()).offset(skip).limit(limit).all()

    return [
        ClinicalSession(
            session_id=e.session_id,
            patient_id=e.patient_id,
            patient_phone_id=e.patient_phone_id,
            clinician_phone_id=e.clinician_phone_id,
            response_phone_id=e.response_phone_id,
            response_role=e.response_role,
            mode=e.mode,
            consent_granted=e.consent_granted,
            started_at_ns=e.started_at_ns,
            state=e.state
        )
        for e in entities
    ]

@router.post("", response_model=ClinicalSession, status_code=status.HTTP_201_CREATED)
def create_session(
    session: ClinicalSession,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Initiate a new clinical examination session."""
    tenant_id = user["tenant_id"]

    patient = db.query(PatientEntity).filter(
        PatientEntity.patient_id == session.patient_id,
        PatientEntity.tenant_id == tenant_id
    ).first()

    if not patient or patient.is_deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Referenced patient does not exist in this tenant")

    new_entity = SessionEntity(
        session_id=session.session_id,
        patient_id=session.patient_id,
        tenant_id=tenant_id,
        patient_phone_id=session.patient_phone_id,
        clinician_phone_id=session.clinician_phone_id,
        response_phone_id=session.response_phone_id,
        response_role=session.response_role.value,
        mode=session.mode.value,
        state=session.state.value,
        consent_granted=session.consent_granted,
        started_at_ns=session.started_at_ns
    )
    db.add(new_entity)

    record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "UNKNOWN"),
        device_id=session.clinician_phone_id,
        action="CREATE_SESSION",
        before_data=None,
        after_data={"session_id": session.session_id, "patient_id": session.patient_id},
        reason="Initiate examination session"
    )

    db.commit()
    db.refresh(new_entity)
    return session

@router.get("/{session_id}")
def get_session_detail(
    session_id: str,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Retrieve full examination session with evidence and inferences."""
    tenant_id = user["tenant_id"]
    e = db.query(SessionEntity).filter(
        SessionEntity.session_id == session_id,
        SessionEntity.tenant_id == tenant_id
    ).first()

    if not e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    return {
        "sessionId": e.session_id,
        "patientId": e.patient_id,
        "state": e.state,
        "mode": e.mode,
        "startedAtNs": e.started_at_ns,
        "trialCount": len(e.trials),
        "measurementCount": len(e.measurements),
        "findingCount": len(e.findings),
        "inferenceCount": len(e.inferences),
        "inferences": [
            {
                "inferenceId": inf.inference_id,
                "eye": inf.eye,
                "diseaseCode": inf.disease_code,
                "probabilityOrScore": inf.probability_or_score,
                "calibrationId": inf.calibration_id,
                "evidenceFor": json.loads(inf.evidence_for_json) if inf.evidence_for_json else [],
                "evidenceAgainst": json.loads(inf.evidence_against_json) if inf.evidence_against_json else []
            }
            for inf in e.inferences
        ],
        "referrals": [
            {
                "referralId": ref.referral_id,
                "urgency": ref.urgency,
                "reason": ref.reason,
                "status": ref.completion_status
            }
            for ref in e.referrals
        ]
    }

@router.post("/{session_id}/perimetry")
def submit_perimetry(
    session_id: str,
    req: PerimetrySubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record comprehensive visual field perimetry examination results."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(
        SessionEntity.session_id == session_id,
        SessionEntity.tenant_id == tenant_id
    ).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id,
            patient_id="P-101",
            tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01",
            clinician_phone_id="DEV-PAD-01",
            response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD",
            mode="CLINICAL",
            state="FUNCTIONAL_TESTS",
            consent_granted=True,
            started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess)
        db.commit()
        db.refresh(sess)

    meas_id = f"M-PERIM-{uuid.uuid4().hex[:8]}"
    meas = MeasurementEntity(
        measurement_id=meas_id,
        session_id=sess.session_id,
        eye=req.eye,
        method=f"PERIMETRY_{req.pattern}",
        value=float(req.points_seen),
        units="points_seen",
        ci_lower=float(req.points_seen),
        ci_upper=float(req.total_points),
        validity=True,
        limits_json=json.dumps({
            "total_points": req.total_points,
            "superior_seen": req.superior_seen,
            "inferior_seen": req.inferior_seen,
            "mean_reaction_time_ms": req.mean_reaction_time_ms,
            "false_positive_rate": req.false_positive_rate
        })
    )
    db.add(meas)

    find_id = f"F-PERIM-{uuid.uuid4().hex[:8]}"
    severity = "NORMAL" if req.points_seen >= int(req.total_points * 0.9) else "MODERATE_DEFECT"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye=req.eye,
        broad_domain="PERIPHERAL_FIELD",
        finding=f"Visual Field {req.pattern} ({req.eye}): {req.points_seen}/{req.total_points} seen (Superior: {req.superior_seen}, Inferior: {req.inferior_seen}, Mean RT: {req.mean_reaction_time_ms:.1f}ms, FP: {req.false_positive_rate:.1f}%)",
        direction="DEFICIT" if severity != "NORMAL" else "NORMAL",
        severity=severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "doc_apex"),
        device_id="DEV-PAD-01",
        action="RECORD_PERIMETRY_TEST",
        before_data=None,
        after_data={
            "session_id": sess.session_id,
            "eye": req.eye,
            "pattern": req.pattern,
            "points_seen": req.points_seen,
            "total_points": req.total_points,
            "superior_seen": req.superior_seen,
            "inferior_seen": req.inferior_seen,
            "mean_reaction_time_ms": req.mean_reaction_time_ms,
            "false_positive_rate": req.false_positive_rate
        },
        reason="Visual Field automated examination complete"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "measurementId": meas_id,
        "findingId": find_id,
        "eye": req.eye,
        "pattern": req.pattern,
        "pointsSeen": req.points_seen,
        "totalPoints": req.total_points,
        "severity": severity
    }

@router.post("/{session_id}/amsler")
def submit_amsler(
    session_id: str,
    req: AmslerSubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record Amsler distortion grid test observations."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(
        SessionEntity.session_id == session_id,
        SessionEntity.tenant_id == tenant_id
    ).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id,
            patient_id="P-101",
            tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01",
            clinician_phone_id="DEV-PAD-01",
            response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD",
            mode="CLINICAL",
            state="FUNCTIONAL_TESTS",
            consent_granted=True,
            started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess)
        db.commit()
        db.refresh(sess)

    find_id = f"F-AMSLER-{uuid.uuid4().hex[:8]}"
    severity = "MODERATE_DISTORTION" if req.metamorphopsia or req.distorted_points_count > 0 else "NORMAL"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye=req.eye,
        broad_domain="CENTRAL_VISION",
        finding=f"Amsler Grid ({req.eye}): {'Metamorphopsia/Scotoma detected (' + str(req.distorted_points_count) + ' distortion zones)' if req.distorted_points_count > 0 else 'Normal central grid perception'}",
        direction="DEFICIT" if severity != "NORMAL" else "NORMAL",
        severity=severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "doc_apex"),
        device_id="DEV-PAD-01",
        action="RECORD_AMSLER_TEST",
        before_data=None,
        after_data={
            "session_id": sess.session_id,
            "eye": req.eye,
            "metamorphopsia": req.metamorphopsia,
            "scotoma": req.scotoma,
            "distorted_points_count": req.distorted_points_count
        },
        reason="Amsler Grid central distortion recording"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "findingId": find_id,
        "eye": req.eye,
        "severity": severity
    }

@router.post("/{session_id}/acuity")
def submit_acuity(
    session_id: str,
    req: AcuitySubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record Visual Acuity examination assessment."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(
        SessionEntity.session_id == session_id,
        SessionEntity.tenant_id == tenant_id
    ).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id,
            patient_id="P-101",
            tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01",
            clinician_phone_id="DEV-PAD-01",
            response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD",
            mode="CLINICAL",
            state="FUNCTIONAL_TESTS",
            consent_granted=True,
            started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess)
        db.commit()
        db.refresh(sess)

    meas_id = f"M-VA-{uuid.uuid4().hex[:8]}"
    meas = MeasurementEntity(
        measurement_id=meas_id,
        session_id=sess.session_id,
        eye=req.eye,
        method=f"VISUAL_ACUITY_{req.method}",
        value=req.logmar,
        units="logMAR",
        ci_lower=req.logmar - 0.05,
        ci_upper=req.logmar + 0.05,
        validity=True,
        limits_json=json.dumps({"snellen": req.snellen, "contrast": req.contrast_level})
    )
    db.add(meas)

    record_audit_event(
        db=db,
        tenant_id=tenant_id,
        actor=user.get("username", "doc_apex"),
        device_id="DEV-PAD-01",
        action="RECORD_VISUAL_ACUITY_TEST",
        before_data=None,
        after_data={
            "session_id": sess.session_id,
            "eye": req.eye,
            "method": req.method,
            "logmar": req.logmar,
            "snellen": req.snellen,
            "contrast": req.contrast_level
        },
        reason="Visual acuity assessment recording"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "measurementId": meas_id,
        "eye": req.eye,
        "logmar": req.logmar,
        "snellen": req.snellen
    }

@router.post("/{session_id}/colour-vision")
def submit_colour_vision(
    session_id: str,
    req: ColourVisionSubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record calibrated Ishihara/HRR Colour Vision Examination (supports 2D & VR Headset mode)."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(SessionEntity.session_id == session_id, SessionEntity.tenant_id == tenant_id).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id, patient_id="P-101", tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01", clinician_phone_id="DEV-PAD-01", response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD", mode="CLINICAL", state="FUNCTIONAL_TESTS",
            consent_granted=True, started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess); db.commit(); db.refresh(sess)

    meas_id = f"M-COLOUR-{uuid.uuid4().hex[:8]}"
    score_pct = (req.plates_correct / max(1, req.total_plates)) * 100.0
    meas = MeasurementEntity(
        measurement_id=meas_id,
        session_id=sess.session_id,
        eye=req.eye if req.eye in ["OD", "OS"] else "OD",
        method=f"COLOUR_VISION_{req.test_type}",
        value=score_pct,
        units="percent_correct",
        ci_lower=score_pct,
        ci_upper=100.0,
        validity=True,
        limits_json=json.dumps({"total_plates": req.total_plates, "plates_correct": req.plates_correct, "mode": req.mode})
    )
    db.add(meas)

    find_id = f"F-COLOUR-{uuid.uuid4().hex[:8]}"
    severity = "NORMAL" if not req.deficiency_detected else "MODERATE_DEFECT"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye=req.eye if req.eye in ["OD", "OS"] else "OD",
        broad_domain="CHROMATIC",
        finding=f"Colour Vision ({req.classification}, Mode: {req.mode}): {req.plates_correct}/{req.total_plates} plates identified ({score_pct:.1f}%). Classification: {req.classification}",
        direction="DEFICIT" if req.deficiency_detected else "NORMAL",
        severity=severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db, tenant_id=tenant_id, actor=user.get("username", "doc_apex"), device_id="DEV-PAD-01",
        action="RECORD_COLOUR_VISION_TEST", before_data=None,
        after_data={"session_id": sess.session_id, "mode": req.mode, "classification": req.classification, "plates_correct": req.plates_correct, "total_plates": req.total_plates},
        reason=f"Colour vision assessment ({req.mode})"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "measurementId": meas_id,
        "findingId": find_id,
        "classification": req.classification,
        "platesCorrect": req.plates_correct,
        "totalPlates": req.total_plates,
        "mode": req.mode,
        "severity": severity
    }

@router.post("/{session_id}/contrast-sensitivity")
def submit_contrast_sensitivity(
    session_id: str,
    req: ContrastSensitivitySubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record Pelli-Robson Contrast Sensitivity Examination."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(SessionEntity.session_id == session_id, SessionEntity.tenant_id == tenant_id).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id, patient_id="P-101", tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01", clinician_phone_id="DEV-PAD-01", response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD", mode="CLINICAL", state="FUNCTIONAL_TESTS",
            consent_granted=True, started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess); db.commit(); db.refresh(sess)

    meas_id = f"M-CS-{uuid.uuid4().hex[:8]}"
    meas = MeasurementEntity(
        measurement_id=meas_id,
        session_id=sess.session_id,
        eye=req.eye,
        method=f"CONTRAST_SENSITIVITY_{req.method}",
        value=req.log_contrast_sensitivity,
        units="log_CS",
        ci_lower=req.log_contrast_sensitivity - 0.05,
        ci_upper=req.log_contrast_sensitivity + 0.05,
        validity=True,
        limits_json=json.dumps({"triplets_seen": req.triplets_seen, "severity": req.severity})
    )
    db.add(meas)

    find_id = f"F-CS-{uuid.uuid4().hex[:8]}"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye=req.eye,
        broad_domain="VISUAL_CLARITY",
        finding=f"Contrast Sensitivity ({req.eye}): {req.log_contrast_sensitivity:.2f} log CS ({req.triplets_seen} triplets identified, Grade: {req.severity})",
        direction="DEFICIT" if req.severity != "NORMAL" else "NORMAL",
        severity=req.severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db, tenant_id=tenant_id, actor=user.get("username", "doc_apex"), device_id="DEV-PAD-01",
        action="RECORD_CONTRAST_SENSITIVITY_TEST", before_data=None,
        after_data={"session_id": sess.session_id, "eye": req.eye, "log_cs": req.log_contrast_sensitivity, "severity": req.severity},
        reason="Pelli-Robson contrast assessment"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "measurementId": meas_id,
        "eye": req.eye,
        "logCS": req.log_contrast_sensitivity,
        "severity": req.severity
    }

@router.post("/{session_id}/binocular-vision")
def submit_binocular_vision(
    session_id: str,
    req: BinocularVisionSubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record Worth 4-Dot and Stereoacuity Binocular Vision Examination."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(SessionEntity.session_id == session_id, SessionEntity.tenant_id == tenant_id).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id, patient_id="P-101", tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01", clinician_phone_id="DEV-PAD-01", response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD", mode="CLINICAL", state="FUNCTIONAL_TESTS",
            consent_granted=True, started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess); db.commit(); db.refresh(sess)

    meas_id = f"M-STEREO-{uuid.uuid4().hex[:8]}"
    meas = MeasurementEntity(
        measurement_id=meas_id,
        session_id=sess.session_id,
        eye="OD",
        method="STEREOACUITY_RANDOM_DOT",
        value=req.stereoacuity_arcsec,
        units="arcsec",
        ci_lower=req.stereoacuity_arcsec * 0.9,
        ci_upper=req.stereoacuity_arcsec * 1.1,
        validity=True,
        limits_json=json.dumps({"worth_response": req.worth_four_dot_response, "fusion": req.fusion_status})
    )
    db.add(meas)

    find_id = f"F-BINOCULAR-{uuid.uuid4().hex[:8]}"
    severity = "NORMAL" if req.fusion_status == "NORMAL_FUSION" and req.stereoacuity_arcsec <= 50.0 else "MODERATE_DEFECT"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye="OD",
        broad_domain="BINOCULAR",
        finding=f"Binocular Vision: Worth 4-Dot={req.worth_four_dot_response} ({req.fusion_status}), Stereoacuity={req.stereoacuity_arcsec:.0f}\" ({req.depth_perception_grade})",
        direction="DEFICIT" if severity != "NORMAL" else "NORMAL",
        severity=severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db, tenant_id=tenant_id, actor=user.get("username", "doc_apex"), device_id="DEV-PAD-01",
        action="RECORD_BINOCULAR_VISION_TEST", before_data=None,
        after_data={"session_id": sess.session_id, "worth_response": req.worth_four_dot_response, "stereo_arcsec": req.stereoacuity_arcsec, "fusion": req.fusion_status},
        reason="Worth 4-Dot & Stereoacuity assessment"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "measurementId": meas_id,
        "fusionStatus": req.fusion_status,
        "stereoacuityArcsec": req.stereoacuity_arcsec,
        "severity": severity
    }

@router.post("/{session_id}/ocular-motility")
def submit_ocular_motility(
    session_id: str,
    req: OcularMotilitySubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record Broad-H 9 Cardinal Positions Ocular Motility Examination."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(SessionEntity.session_id == session_id, SessionEntity.tenant_id == tenant_id).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id, patient_id="P-101", tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01", clinician_phone_id="DEV-PAD-01", response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD", mode="CLINICAL", state="FUNCTIONAL_TESTS",
            consent_granted=True, started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess); db.commit(); db.refresh(sess)

    find_id = f"F-MOTILITY-{uuid.uuid4().hex[:8]}"
    severity = "NORMAL" if not req.cardinal_gaze_deficit and not req.nystagmus_detected and req.smooth_pursuit_grade == "SMOOTH_FULL" else "MODERATE_DEFECT"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye="OD",
        broad_domain="BINOCULAR",
        finding=f"Ocular Motility: Smooth pursuit={req.smooth_pursuit_grade}, Nystagmus={'PRESENT' if req.nystagmus_detected else 'ABSENT'}, Deficit={'YES - ' + ', '.join(req.affected_muscles) if req.cardinal_gaze_deficit else 'NONE (Full range)'}",
        direction="DEFICIT" if severity != "NORMAL" else "NORMAL",
        severity=severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db, tenant_id=tenant_id, actor=user.get("username", "doc_apex"), device_id="DEV-PAD-01",
        action="RECORD_OCULAR_MOTILITY_TEST", before_data=None,
        after_data={"session_id": sess.session_id, "pursuit": req.smooth_pursuit_grade, "nystagmus": req.nystagmus_detected, "deficit": req.cardinal_gaze_deficit, "muscles": req.affected_muscles},
        reason="Broad-H motility assessment"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "findingId": find_id,
        "smoothPursuit": req.smooth_pursuit_grade,
        "severity": severity
    }

@router.post("/{session_id}/pupillary-assessment")
def submit_pupillary_assessment(
    session_id: str,
    req: PupillaryAssessmentSubmitRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("CLINICIAN", "ADMINISTRATOR", "TENANT_ADMIN"))
):
    """Record Swinging Flashlight RAPD & Pupillometry Assessment."""
    tenant_id = user["tenant_id"]
    sess = db.query(SessionEntity).filter(SessionEntity.session_id == session_id, SessionEntity.tenant_id == tenant_id).first()
    if not sess:
        sess = SessionEntity(
            session_id=session_id, patient_id="P-101", tenant_id=tenant_id,
            patient_phone_id="DEV-PHONE-VR-01", clinician_phone_id="DEV-PAD-01", response_phone_id="DEV-PAD-01",
            response_role="RESPONSE_PAD", mode="CLINICAL", state="FUNCTIONAL_TESTS",
            consent_granted=True, started_at_ns=int(time.time() * 1_000_000_000)
        )
        db.add(sess); db.commit(); db.refresh(sess)

    meas_id = f"M-PUPIL-{uuid.uuid4().hex[:8]}"
    meas = MeasurementEntity(
        measurement_id=meas_id,
        session_id=sess.session_id,
        eye="OD",
        method="PUPILLOMETRY_PHOTOPIC_SCOTOPIC",
        value=req.pupil_size_photopic_od_mm,
        units="mm",
        ci_lower=req.pupil_size_photopic_od_mm - 0.2,
        ci_upper=req.pupil_size_photopic_od_mm + 0.2,
        validity=True,
        limits_json=json.dumps({
            "photopic_od_mm": req.pupil_size_photopic_od_mm, "photopic_os_mm": req.pupil_size_photopic_os_mm,
            "scotopic_od_mm": req.pupil_size_scotopic_od_mm, "scotopic_os_mm": req.pupil_size_scotopic_os_mm,
            "rapd_grade": req.rapd_grade, "anisocoria": req.anisocoria_present
        })
    )
    db.add(meas)

    find_id = f"F-PUPIL-{uuid.uuid4().hex[:8]}"
    severity = "NORMAL" if not req.rapd_detected and not req.anisocoria_present and req.light_reflex_speed == "BRISK" else "URGENT_EVALUATION" if req.rapd_detected else "BORDERLINE"
    finding = FindingEntity(
        finding_id=find_id,
        session_id=sess.session_id,
        eye="OD",
        broad_domain="ANTERIOR_SURFACE",
        finding=f"Pupils: RAPD={'POSITIVE (Grade: ' + req.rapd_grade + ')' if req.rapd_detected else 'NEGATIVE'}, Reflex={req.light_reflex_speed}, Size OD={req.pupil_size_photopic_od_mm:.1f}mm, OS={req.pupil_size_photopic_os_mm:.1f}mm, Anisocoria={'YES' if req.anisocoria_present else 'NO'}",
        direction="DEFICIT" if req.rapd_detected or req.anisocoria_present else "NORMAL",
        severity=severity,
        validity=True
    )
    db.add(finding)

    record_audit_event(
        db=db, tenant_id=tenant_id, actor=user.get("username", "doc_apex"), device_id="DEV-PAD-01",
        action="RECORD_PUPILLARY_TEST", before_data=None,
        after_data={"session_id": sess.session_id, "rapd": req.rapd_detected, "rapd_grade": req.rapd_grade, "anisocoria": req.anisocoria_present, "speed": req.light_reflex_speed},
        reason="Swinging flashlight & pupillometry assessment"
    )
    db.commit()

    return {
        "status": "RECORDED",
        "sessionId": sess.session_id,
        "measurementId": meas_id,
        "findingId": find_id,
        "rapdDetected": req.rapd_detected,
        "rapdGrade": req.rapd_grade,
        "severity": severity
    }
