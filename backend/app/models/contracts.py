"""EyeAtlas VR - Platform-Neutral Clinical Data Contracts.
Strict Pydantic v2 schemas mirroring app/src/main/java/org/eyeatlas/core/Models.kt.
Preserves per-eye laterality, independent multi-label disease representations,
and physical measurement validity semantics.
"""
from __future__ import annotations
from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field

class EyeLaterality(str, Enum):
    OD = "OD"  # Right eye (Oculus Dexter)
    OS = "OS"  # Left eye (Oculus Sinister)

class CorrectionStatus(str, Enum):
    UNAIDED = "UNAIDED"
    CURRENT_HABITUAL = "CURRENT_HABITUAL"
    PINHOLE = "PINHOLE"
    BEST_CORRECTED = "BEST_CORRECTED"

class FixationStatus(str, Enum):
    STEADY = "STEADY"
    UNSTEADY = "UNSTEADY"
    LOST = "LOST"
    NOT_ASSESSED = "NOT_ASSESSED"

class SessionState(str, Enum):
    INITIALIZING = "INITIALIZING"
    TRIAGED = "TRIAGED"
    DEVICE_CALIBRATED = "DEVICE_CALIBRATED"
    FUNCTIONAL_TESTS = "FUNCTIONAL_TESTS"
    ANTERIOR_CAPTURE = "ANTERIOR_CAPTURE"
    RETINAL_CAPTURE = "RETINAL_CAPTURE"
    COMPLETE = "COMPLETE"
    EMERGENCY_HALT = "EMERGENCY_HALT"
    RECOVERY = "RECOVERY"

class ClinicalMode(str, Enum):
    SIMULATED = "SIMULATED"
    CLINICAL = "CLINICAL"

class AppRole(str, Enum):
    PATIENT_VR = "PATIENT_VR"
    DOCTOR_SUPERVISOR = "DOCTOR_SUPERVISOR"
    RESPONSE_PAD = "RESPONSE_PAD"
    ADMIN = "ADMIN"

class BroadDomain(str, Enum):
    VISUAL_CLARITY = "VISUAL_CLARITY"
    NEAR_VISION = "NEAR_VISION"
    CHROMATIC = "CHROMATIC"
    CENTRAL_VISION = "CENTRAL_VISION"
    PERIPHERAL_FIELD = "PERIPHERAL_FIELD"
    BINOCULAR = "BINOCULAR"
    ANTERIOR_SURFACE = "ANTERIOR_SURFACE"

class DiseaseCode(str, Enum):
    REFRACTIVE_AMBLYOPIA = "REFRACTIVE_AMBLYOPIA"
    PRESBYOPIA = "PRESBYOPIA"
    DRY_EYE = "DRY_EYE"
    CATARACT = "CATARACT"
    PTERYGIUM = "PTERYGIUM"
    RED_EYE_CONJUNCTIVITIS = "RED_EYE_CONJUNCTIVITIS"
    AMD = "AMD"
    GLAUCOMA = "GLAUCOMA"
    DIABETIC_RETINOPATHY = "DIABETIC_RETINOPATHY"
    STRABISMUS = "STRABISMUS"
    COLOUR_VISION_DEFICIENCY = "COLOUR_VISION_DEFICIENCY"
    OTHER_RETINAL = "OTHER_RETINAL"

class ReferralUrgency(str, Enum):
    ROUTINE = "ROUTINE"
    URGENT_24_TO_48_HOURS = "URGENT_24_TO_48_HOURS"
    EMERGENCY_IMMEDIATE = "EMERGENCY_IMMEDIATE"

class ClinicalReleaseClassification(str, Enum):
    VALIDATED = "VALIDATED"
    TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED = "TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED"
    NOT_SUPPORTED = "NOT_SUPPORTED"
    NOT_ASSESSED = "NOT_ASSESSED"
    UNGRADABLE = "UNGRADABLE"

class InputType(str, Enum):
    TAP = "TAP"
    LONG_PRESS = "LONG_PRESS"
    MULTI_TOUCH_REJECTED = "MULTI_TOUCH_REJECTED"
    TIMEOUT = "TIMEOUT"
    MANUAL_OVERRIDE = "MANUAL_OVERRIDE"

class InputQuality(str, Enum):
    VALID = "VALID"
    DEBOUNCED = "DEBOUNCED"
    LATE = "LATE"
    BLANK_CATCH_FAIL = "BLANK_CATCH_FAIL"
    HEARBEAT_TIMEOUT = "HEARBEAT_TIMEOUT"

class DurableStatus(str, Enum):
    LOCALLY_COMMITTED = "LOCALLY_COMMITTED"
    REPLICATED_TO_PATIENT_PHONE = "REPLICATED_TO_PATIENT_PHONE"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    PENDING_SYNC = "PENDING_SYNC"
    SYNCED = "SYNCED"

class PatientRecord(BaseModel):
    patient_id: str = Field(..., alias="patientId")
    age_band: str = Field(..., alias="ageBand")
    has_diabetes: bool = Field(False, alias="hasDiabetes")
    symptoms: List[str] = Field(default_factory=list)
    current_correction: CorrectionStatus = Field(CorrectionStatus.UNAIDED, alias="currentCorrection")
    comorbidity_flags: Dict[str, bool] = Field(default_factory=dict, alias="comorbidityFlags")

    model_config = ConfigDict(populate_by_name=True)

class ClinicalSession(BaseModel):
    session_id: str = Field(..., alias="sessionId")
    patient_id: str = Field(..., alias="patientId")
    patient_phone_id: str = Field(..., alias="patientPhoneId")
    clinician_phone_id: str = Field(..., alias="clinicianPhoneId")
    response_phone_id: str = Field(..., alias="responsePhoneId")
    response_role: AppRole = Field(AppRole.RESPONSE_PAD, alias="responseRole")
    mode: ClinicalMode = Field(ClinicalMode.SIMULATED)
    consent_granted: bool = Field(False, alias="consentGranted")
    started_at_ns: int = Field(..., alias="startedAtNs")
    state: SessionState = Field(SessionState.INITIALIZING)

    model_config = ConfigDict(populate_by_name=True)

class EyeRecord(BaseModel):
    laterality: EyeLaterality
    correction_status: CorrectionStatus = Field(CorrectionStatus.UNAIDED, alias="correctionStatus")
    optical_profile_id: str = Field(..., alias="opticalProfileId")
    fixation_status: FixationStatus = Field(FixationStatus.NOT_ASSESSED, alias="fixationStatus")

    model_config = ConfigDict(populate_by_name=True)

class StimulusTrial(BaseModel):
    trial_id: str = Field(..., alias="trialId")
    session_id: str = Field(..., alias="sessionId")
    eye: EyeLaterality
    test_code: str = Field(..., alias="testCode")
    target_angle_x: float = Field(..., alias="targetAngleX")
    target_angle_y: float = Field(..., alias="targetAngleY")
    luminance_nits: float = Field(..., alias="luminanceNits")
    optical_index: int = Field(0, alias="opticalIndex")
    monotonic_presented_ns: int = Field(..., alias="monotonicPresentedNs")
    response_phone_monotonic_ns: Optional[int] = Field(None, alias="responsePhoneMonotonicNs")
    corrected_response_ns: Optional[int] = Field(None, alias="correctedResponseNs")
    clock_offset_ns: int = Field(0, alias="clockOffsetNs")
    clock_uncertainty_ns: int = Field(0, alias="clockUncertaintyNs")
    expected_answer: Optional[str] = Field(None, alias="expectedAnswer")
    observed_answer: Optional[str] = Field(None, alias="observedAnswer")
    frames_rendered: int = Field(24, alias="framesRendered")
    calibration_hash: str = Field(..., alias="calibrationHash")
    response_event_id: Optional[str] = Field(None, alias="responseEventId")
    validity_flags: List[str] = Field(default_factory=list, alias="validityFlags")

    model_config = ConfigDict(populate_by_name=True)

class ResponseEvent(BaseModel):
    event_id: str = Field(..., alias="eventId")
    session_id: str = Field(..., alias="sessionId")
    trial_id: Optional[str] = Field(None, alias="trialId")
    response_phone_id: str = Field(..., alias="responsePhoneId")
    monotonic_event_ns: int = Field(..., alias="monotonicEventNs")
    input_type: InputType = Field(InputType.TAP, alias="inputType")
    pointer_count: int = Field(1, alias="pointerCount")
    input_quality: InputQuality = Field(InputQuality.VALID, alias="inputQuality")
    clock_sync_id: str = Field(..., alias="clockSyncId")
    received_by_patient_ns: int = Field(..., alias="receivedByPatientNs")
    durable_status: DurableStatus = Field(DurableStatus.LOCALLY_COMMITTED, alias="durableStatus")

    model_config = ConfigDict(populate_by_name=True)

class ImageCapture(BaseModel):
    image_id: str = Field(..., alias="imageId")
    session_id: str = Field(..., alias="sessionId")
    eye: EyeLaterality
    mode: str
    camera_id: str = Field(..., alias="cameraId")
    raw_uri: str = Field(..., alias="rawUri")
    processing_hash: str = Field(..., alias="processingHash")
    focus_metric: float = Field(..., alias="focusMetric")
    exposure_ms: float = Field(..., alias="exposureMs")
    white_balance: str = Field(..., alias="whiteBalance")
    visible_structures: List[str] = Field(default_factory=list, alias="visibleStructures")
    field_coverage: str = Field(..., alias="fieldCoverage")
    quality_score: float = Field(..., alias="qualityScore")

    model_config = ConfigDict(populate_by_name=True)

class Measurement(BaseModel):
    measurement_id: str = Field(..., alias="measurementId")
    provenance_ids: List[str] = Field(default_factory=list, alias="provenanceIds")
    eye: EyeLaterality
    method: str
    value: float
    units: str
    ci_lower: float = Field(..., alias="ciLower")
    ci_upper: float = Field(..., alias="ciUpper")
    validity: bool = True
    limits: List[float] = Field(default_factory=list)

    model_config = ConfigDict(populate_by_name=True)

class Finding(BaseModel):
    eye: EyeLaterality
    broad_domain: BroadDomain = Field(..., alias="broadDomain")
    finding: str
    direction: str
    severity: str
    provenance_ids: List[str] = Field(default_factory=list, alias="provenanceIds")
    validity: bool = True

    model_config = ConfigDict(populate_by_name=True)

class ConditionInference(BaseModel):
    eye: EyeLaterality
    disease_code: DiseaseCode = Field(..., alias="diseaseCode")
    probability_or_score: float = Field(..., alias="probabilityOrScore")
    calibration_id: str = Field(..., alias="calibrationId")
    evidence_for: List[str] = Field(default_factory=list, alias="evidenceFor")
    evidence_against: List[str] = Field(default_factory=list, alias="evidenceAgainst")
    unknown_evidence: List[str] = Field(default_factory=list, alias="unknownEvidence")
    recommended_test: Optional[str] = Field(None, alias="recommendedTest")

    model_config = ConfigDict(populate_by_name=True)

class Referral(BaseModel):
    urgency: ReferralUrgency
    reason: str
    disease_specific_tests: List[str] = Field(default_factory=list, alias="diseaseSpecificTests")
    completion_status: str = Field("PENDING", alias="completionStatus")

    model_config = ConfigDict(populate_by_name=True)

class AuditEvent(BaseModel):
    actor: str
    timestamp_ns: int = Field(..., alias="timestampNs")
    device_id: str = Field(..., alias="deviceId")
    action: str
    before_hash: str = Field(..., alias="beforeHash")
    after_hash: str = Field(..., alias="afterHash")
    reason: str

    model_config = ConfigDict(populate_by_name=True)

class DeviceProfile(BaseModel):
    profile_id: str = Field(..., alias="profileId")
    patient_phone_make_model: str = Field(..., alias="patientPhoneMakeModel")
    serial_hash: str = Field(..., alias="serialHash")
    os_build: str = Field(..., alias="osBuild")
    app_version: str = Field(..., alias="appVersion")
    display_px_per_eye_x: int = Field(1206, alias="displayPxPerEyeX")
    display_px_per_eye_y: int = Field(1080, alias="displayPxPerEyeY")
    refresh_rate_hz: float = Field(120.0, alias="refreshRateHz")
    measured_luminance_curve_hash: str = Field(..., alias="measuredLuminanceCurveHash")
    colour_profile_id: str = Field(..., alias="colourProfileId")
    wide_optic_id: str = Field(..., alias="wideOpticId")
    central_optic_id: Optional[str] = Field(None, alias="centralOpticId")
    lens_distortion_map_hash: str = Field(..., alias="lensDistortionMapHash")
    angular_visibility_map_od_hash: str = Field(..., alias="angularVisibilityMapOdHash")
    angular_visibility_map_os_hash: str = Field(..., alias="angularVisibilityMapOsHash")
    ipd_min_mm: float = Field(48.0, alias="ipdMinMm")
    ipd_max_mm: float = Field(74.0, alias="ipdMaxMm")
    eye_relief_mm: float = Field(14.5, alias="eyeReliefMm")
    indexed_focus_map: Dict[int, float] = Field(default_factory=dict, alias="indexedFocusMap")
    anterior_camera_id: str = Field(..., alias="anteriorCameraId")
    anterior_intrinsic_hash: str = Field(..., alias="anteriorIntrinsicHash")
    camera_color_profile_id: str = Field(..., alias="cameraColorProfileId")
    fundus_attachment_id: str = Field(..., alias="fundusAttachmentId")
    fundus_optical_safety_cert_id: str = Field(..., alias="fundusOpticalSafetyCertId")
    timestamping_validation: bool = Field(True, alias="timestampingValidation")
    validation_date: str = Field(..., alias="validationDate")
    expiry_date: str = Field(..., alias="expiryDate")
    signed_digest: str = Field(..., alias="signedDigest")

    model_config = ConfigDict(populate_by_name=True)

class ReportInference(BaseModel):
    eye: EyeLaterality
    disease_code: DiseaseCode = Field(..., alias="diseaseCode")
    score: float
    calibration: str = Field("STANDARD", alias="calibration")
    model_config = ConfigDict(populate_by_name=True)

class ReportReferral(BaseModel):
    urgency: ReferralUrgency
    reason: str
    model_config = ConfigDict(populate_by_name=True)

class ClinicalReport(BaseModel):
    report_id: str = Field(..., alias="reportId")
    session_id: str = Field(..., alias="sessionId")
    patient_id: str = Field(..., alias="patientId")
    patient_age_band: str = Field("40-59", alias="patientAgeBand")
    has_diabetes: bool = Field(False, alias="hasDiabetes")
    mode: ClinicalMode = Field(ClinicalMode.CLINICAL)
    report_digest_sha256: str = Field(..., alias="reportDigestSha256")
    regulatory_status: str = Field("CDSCO_INVESTIGATIONAL_NOT_APPROVED", alias="regulatoryStatus")
    is_release_approved: bool = Field(False, alias="isReleaseApproved")
    inferences: List[ReportInference] = Field(default_factory=list)
    referrals: List[ReportReferral] = Field(default_factory=list)
    model_config = ConfigDict(populate_by_name=True)

# Aliases for explicit Contract suffix compatibility
PatientRecordContract = PatientRecord
StimulusTrialContract = StimulusTrial
MeasurementContract = Measurement
FindingContract = Finding
ConditionInferenceContract = ConditionInference
ReferralContract = Referral
ClinicalReportContract = ClinicalReport
ReportInferenceContract = ReportInference
ReportReferralContract = ReportReferral
ReportReferralContract = ReportReferral
