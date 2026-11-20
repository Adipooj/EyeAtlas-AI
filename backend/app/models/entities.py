"""EyeAtlas VR - Relational Database Entities (SQLAlchemy 2.0).
Provides multi-tenant partitioning, indexed foreign keys,
and append-only audit trail storage.
"""
from __future__ import annotations
import datetime
from sqlalchemy import Boolean, Column, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from backend.app.core.database import Base

class TenantEntity(Base):
    __tablename__ = "tenants"

    tenant_id = Column(String(64), primary_key=True, index=True)
    name = Column(String(128), nullable=False)
    tier = Column(String(32), default="TIER_2_VISION_CENTER")  # TIER_1_APEX, TIER_2_VISION_CENTER, TIER_3_OUTREACH
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    users = relationship("UserEntity", back_populates="tenant")
    patients = relationship("PatientEntity", back_populates="tenant")
    sessions = relationship("SessionEntity", back_populates="tenant")
    audit_events = relationship("AuditEventEntity", back_populates="tenant")

class UserEntity(Base):
    __tablename__ = "users"

    user_id = Column(String(64), primary_key=True, index=True)
    tenant_id = Column(String(64), ForeignKey("tenants.tenant_id"), nullable=False, index=True)
    username = Column(String(64), unique=True, nullable=False, index=True)
    email = Column(String(128), unique=True, nullable=False)
    hashed_password = Column(String(256), nullable=False)
    role = Column(String(32), default="CLINICIAN")  # CLINICIAN, ADMINISTRATOR, TENANT_ADMIN, PATIENT
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    tenant = relationship("TenantEntity", back_populates="users")

class PatientEntity(Base):
    __tablename__ = "patients"

    patient_id = Column(String(64), primary_key=True, index=True)
    tenant_id = Column(String(64), ForeignKey("tenants.tenant_id"), nullable=False, index=True)
    age_band = Column(String(32), nullable=False)
    has_diabetes = Column(Boolean, default=False)
    symptoms_json = Column(Text, default="[]")
    current_correction = Column(String(32), default="UNAIDED")
    comorbidity_flags_json = Column(Text, default="{}")
    consent_granted = Column(Boolean, default=False)
    version = Column(Integer, default=1)
    is_deleted = Column(Boolean, default=False)  # Soft delete for GDPR/DPDP compliance
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    tenant = relationship("TenantEntity", back_populates="patients")
    sessions = relationship("SessionEntity", back_populates="patient")

class SessionEntity(Base):
    __tablename__ = "sessions"

    session_id = Column(String(64), primary_key=True, index=True)
    patient_id = Column(String(64), ForeignKey("patients.patient_id"), nullable=False, index=True)
    tenant_id = Column(String(64), ForeignKey("tenants.tenant_id"), nullable=False, index=True)
    patient_phone_id = Column(String(64), nullable=False)
    clinician_phone_id = Column(String(64), nullable=False)
    response_phone_id = Column(String(64), nullable=False)
    response_role = Column(String(32), default="RESPONSE_PAD")
    mode = Column(String(32), default="SIMULATED")
    state = Column(String(32), default="INITIALIZING")
    consent_granted = Column(Boolean, default=False)
    started_at_ns = Column(Integer, nullable=False)
    completed_at_ns = Column(Integer, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    patient = relationship("PatientEntity", back_populates="sessions")
    tenant = relationship("TenantEntity", back_populates="sessions")
    trials = relationship("StimulusTrialEntity", back_populates="session")
    images = relationship("ImageCaptureEntity", back_populates="session")
    measurements = relationship("MeasurementEntity", back_populates="session")
    findings = relationship("FindingEntity", back_populates="session")
    inferences = relationship("ConditionInferenceEntity", back_populates="session")
    referrals = relationship("ReferralEntity", back_populates="session")

class StimulusTrialEntity(Base):
    __tablename__ = "stimulus_trials"

    trial_id = Column(String(64), primary_key=True, index=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id"), nullable=False, index=True)
    eye = Column(String(8), nullable=False)  # OD, OS
    test_code = Column(String(64), nullable=False)
    target_angle_x = Column(Float, nullable=False)
    target_angle_y = Column(Float, nullable=False)
    luminance_nits = Column(Float, nullable=False)
    optical_index = Column(Integer, default=0)
    monotonic_presented_ns = Column(Integer, nullable=False)
    response_phone_monotonic_ns = Column(Integer, nullable=True)
    corrected_response_ns = Column(Integer, nullable=True)
    clock_offset_ns = Column(Integer, default=0)
    clock_uncertainty_ns = Column(Integer, default=0)
    expected_answer = Column(String(64), nullable=True)
    observed_answer = Column(String(64), nullable=True)
    frames_rendered = Column(Integer, default=24)
    calibration_hash = Column(String(64), nullable=False)
    response_event_id = Column(String(64), nullable=True)
    validity_flags_json = Column(Text, default="[]")

    session = relationship("SessionEntity", back_populates="trials")

class ImageCaptureEntity(Base):
    __tablename__ = "image_captures"

    image_id = Column(String(64), primary_key=True, index=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id"), nullable=False, index=True)
    eye = Column(String(8), nullable=False)  # OD, OS
    mode = Column(String(64), nullable=False)
    camera_id = Column(String(64), nullable=False)
    raw_uri = Column(String(256), nullable=False)
    processing_hash = Column(String(64), nullable=False)
    focus_metric = Column(Float, nullable=False)
    exposure_ms = Column(Float, nullable=False)
    white_balance = Column(String(64), nullable=False)
    visible_structures_json = Column(Text, default="[]")
    field_coverage = Column(String(64), nullable=False)
    quality_score = Column(Float, nullable=False)

    session = relationship("SessionEntity", back_populates="images")

class MeasurementEntity(Base):
    __tablename__ = "measurements"

    measurement_id = Column(String(64), primary_key=True, index=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id"), nullable=False, index=True)
    eye = Column(String(8), nullable=False)  # OD, OS
    method = Column(String(64), nullable=False)
    value = Column(Float, nullable=False)
    units = Column(String(32), nullable=False)
    ci_lower = Column(Float, nullable=False)
    ci_upper = Column(Float, nullable=False)
    validity = Column(Boolean, default=True)
    limits_json = Column(Text, default="[]")
    provenance_ids_json = Column(Text, default="[]")

    session = relationship("SessionEntity", back_populates="measurements")

class FindingEntity(Base):
    __tablename__ = "findings"

    finding_id = Column(String(64), primary_key=True, index=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id"), nullable=False, index=True)
    eye = Column(String(8), nullable=False)  # OD, OS
    broad_domain = Column(String(64), nullable=False)
    finding = Column(String(128), nullable=False)
    direction = Column(String(64), nullable=False)
    severity = Column(String(64), nullable=False)
    validity = Column(Boolean, default=True)
    provenance_ids_json = Column(Text, default="[]")

    session = relationship("SessionEntity", back_populates="findings")

class ConditionInferenceEntity(Base):
    __tablename__ = "condition_inferences"

    inference_id = Column(String(64), primary_key=True, index=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id"), nullable=False, index=True)
    eye = Column(String(8), nullable=False)  # OD, OS
    disease_code = Column(String(64), nullable=False)
    probability_or_score = Column(Float, nullable=False)
    calibration_id = Column(String(64), nullable=False)
    evidence_for_json = Column(Text, default="[]")
    evidence_against_json = Column(Text, default="[]")
    unknown_evidence_json = Column(Text, default="[]")
    recommended_test = Column(String(128), nullable=True)

    session = relationship("SessionEntity", back_populates="inferences")

class ReferralEntity(Base):
    __tablename__ = "referrals"

    referral_id = Column(String(64), primary_key=True, index=True)
    session_id = Column(String(64), ForeignKey("sessions.session_id"), nullable=False, index=True)
    urgency = Column(String(32), nullable=False)  # ROUTINE, URGENT_24_TO_48_HOURS, EMERGENCY_IMMEDIATE
    reason = Column(Text, nullable=False)
    disease_specific_tests_json = Column(Text, default="[]")
    completion_status = Column(String(32), default="PENDING")

    session = relationship("SessionEntity", back_populates="referrals")

class AuditEventEntity(Base):
    __tablename__ = "audit_events"

    event_id = Column(String(64), primary_key=True, index=True)
    tenant_id = Column(String(64), ForeignKey("tenants.tenant_id"), nullable=False, index=True)
    actor = Column(String(64), nullable=False)
    timestamp_ns = Column(Integer, nullable=False)
    device_id = Column(String(64), nullable=False)
    action = Column(String(64), nullable=False)
    before_hash = Column(String(64), nullable=False)
    after_hash = Column(String(64), nullable=False)
    reason = Column(String(256), nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    tenant = relationship("TenantEntity", back_populates="audit_events")

class SyncRecordEntity(Base):
    __tablename__ = "sync_records"

    sync_id = Column(String(64), primary_key=True, index=True)
    client_device_id = Column(String(64), nullable=False, index=True)
    tenant_id = Column(String(64), nullable=False, index=True)
    batch_id = Column(String(64), nullable=False, index=True)
    sequence_num = Column(Integer, nullable=False)
    client_timestamp = Column(Integer, nullable=False)
    server_timestamp = Column(Integer, nullable=False)
    status = Column(String(32), default="SUCCESS")
    conflicts_detected_json = Column(Text, default="[]")
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

class RemoteSessionEntity(Base):
    __tablename__ = "remote_sessions"

    session_id = Column(String(64), primary_key=True, index=True)
    tenant_id = Column(String(64), ForeignKey("tenants.tenant_id"), nullable=False, index=True)
    patient_id = Column(String(64), nullable=True, index=True)
    pairing_pin = Column(String(8), nullable=False, index=True)
    qr_payload = Column(String(512), nullable=False)
    patient_vr_token = Column(String(256), nullable=False)
    pin_expires_at = Column(DateTime, nullable=False)
    active_physician_user_id = Column(String(64), nullable=True)
    status = Column(String(32), default="PENDING_PAIRING")
    connected_roles_json = Column(Text, default="[]")
    active_module = Column(String(64), nullable=True)
    message_sequence_number = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

