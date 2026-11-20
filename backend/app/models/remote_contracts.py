"""EyeAtlas VR - Remote Session & WebSocket Communication Contracts (CR-002).
Defines versioned, strictly-validated schemas for online QR/PIN pairing,
tripartite role authorization, real-time stimulus reconstruction,
patient-side tactile response streaming, and emergency halt signaling.
"""
from __future__ import annotations
import time
from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field

class RemoteRole(str, Enum):
    """Authorized roles within a remote tele-ophthalmology session."""
    PATIENT_VR_HOST = "PATIENT_VR_HOST"          # Authoritative stimulus display inside VR Box (APK)
    PHYSICIAN_CONTROLLER = "PHYSICIAN_CONTROLLER"  # Authoritative single physician station (Web)
    PATIENT_RESPONSE_PAD = "PATIENT_RESPONSE_PAD"  # Patient tactile input touch pad (Browser on Phone 2 or BT)
    OBSERVER = "OBSERVER"                          # Read-only secondary supervisor / observer

class RemoteSessionStatus(str, Enum):
    PENDING_PAIRING = "PENDING_PAIRING"
    CONNECTED = "CONNECTED"
    TESTING = "TESTING"
    PAUSED = "PAUSED"
    FINALIZED = "FINALIZED"
    REVOKED = "REVOKED"
    EXPIRED = "EXPIRED"

class WebSocketMessageType(str, Enum):
    AUTH = "AUTH"
    AUTH_ACK = "AUTH_ACK"
    PEER_STATUS = "PEER_STATUS"
    HEARTBEAT = "HEARTBEAT"
    STIMULUS_EVENT = "STIMULUS_EVENT"
    RESPONSE_EVENT = "RESPONSE_EVENT"
    CONTROL_COMMAND = "CONTROL_COMMAND"
    OBSERVATION_SUBMIT = "OBSERVATION_SUBMIT"
    EMERGENCY_STOP = "EMERGENCY_STOP"
    SYNC_ACK = "SYNC_ACK"
    REPLAY_REQUEST = "REPLAY_REQUEST"
    REPLAY_RESPONSE = "REPLAY_RESPONSE"
    WEBRTC_OFFER = "WEBRTC_OFFER"
    WEBRTC_ANSWER = "WEBRTC_ANSWER"
    WEBRTC_ICE_CANDIDATE = "WEBRTC_ICE_CANDIDATE"
    STREAM_STATUS = "STREAM_STATUS"
    EYETRACKING_TELEMETRY = "EYETRACKING_TELEMETRY"
    OPTICAL_INTERLOCK_STATUS = "OPTICAL_INTERLOCK_STATUS"
    ERROR = "ERROR"

class OpticalConfigurationState(str, Enum):
    """Optical hardware configuration states for detachable lens VR chassis (CR-002-G)."""
    BOTH_VR_LENSES_INSTALLED = "BOTH_VR_LENSES_INSTALLED"
    OD_LENS_REMOVED = "OD_LENS_REMOVED"
    OS_LENS_REMOVED = "OS_LENS_REMOVED"
    BOTH_LENSES_REMOVED = "BOTH_LENSES_REMOVED"
    EXTERNAL_ADAPTER_20D_MOUNTED = "EXTERNAL_ADAPTER_20D_MOUNTED"
    EXTERNAL_ADAPTER_PINHOLE_MOUNTED = "EXTERNAL_ADAPTER_PINHOLE_MOUNTED"
    UNKNOWN_UNCALIBRATED = "UNKNOWN_UNCALIBRATED"

class RemoteSessionCreateRequest(BaseModel):
    patient_id: Optional[str] = Field(default="P-101", description="Patient identifier")
    device_id: str = Field(default="PHONE-VR-PATIENT", description="Hardware ID of the patient VR phone")
    hardware_model: str = Field(default="Generic-VR-Phone", description="Device hardware model name")
    supported_inputs: List[str] = Field(default=["TOUCH_PAD", "VOLUME_KEYS", "BT_CLICKER"])
    tenant_id: str = Field(default="TENANT-APEX")

class RemoteSessionCreateResponse(BaseModel):
    session_id: str
    pairing_pin: str = Field(description="6-digit short-lived PIN (15-minute expiry)")
    qr_payload: str = Field(description="Scannable QR string: EYEATLAS-REMOTE://{sessionId}/{pin}/{host}")
    patient_vr_token: str = Field(description="Ephemeral token for the Patient VR Host")
    expires_at_epoch: int = Field(description="Unix timestamp when PIN expires")
    ws_endpoint_url: str
    status: RemoteSessionStatus = RemoteSessionStatus.PENDING_PAIRING

class RemoteSessionJoinRequest(BaseModel):
    session_id: str
    pairing_pin: Optional[str] = Field(default=None, description="6-digit PIN (required for Response Pad or anonymous)")
    role: RemoteRole = Field(default=RemoteRole.PHYSICIAN_CONTROLLER)
    device_id: str = Field(default="WEB-CLIENT-01")
    clinician_username: Optional[str] = None

class RemoteSessionJoinResponse(BaseModel):
    session_id: str
    role: RemoteRole
    token: str
    ws_endpoint_url: str
    is_authoritative_controller: bool
    status: RemoteSessionStatus

class RemoteSessionStatusResponse(BaseModel):
    session_id: str
    status: RemoteSessionStatus
    active_physician: Optional[str] = None
    connected_roles: List[str] = []
    active_module: Optional[str] = None
    message_count: int = 0
    created_at_epoch: int
    expires_at_epoch: int

class StimulusReconstructionPayload(BaseModel):
    trial_index: int
    test_module: str  # PERIMETRY_24_2, ACUITY_LOGMAR, ISHIHARA_24, CONTRAST_PELLI, AMSLER
    eye_laterality: str = "OD"  # OD, OS, OU
    coordinates: Dict[str, float] = Field(default_factory=dict)  # e.g. {"x_deg": -15.0, "y_deg": 3.0}
    stimulus_properties: Dict[str, Any] = Field(default_factory=dict)  # size, luminance, contrast, duration
    presentation_monotonic_ns: int = Field(description="Hardware monotonic nanoseconds from Android APK")
    fixation_status: str = "FIXATION_HELD"
    reliability_metrics: Dict[str, float] = Field(default_factory=dict)

class PatientResponsePayload(BaseModel):
    trial_index: int
    response_recorded: bool = True
    reaction_latency_ms: Optional[float] = None
    input_source: str = "TOUCH_PAD"  # TOUCH_PAD, VOLUME_KEY, BT_CLICKER, PHYSICIAN_VERBAL
    input_quality: str = "VALID"  # VALID, DEBOUNCE_REJECTED, MULTI_TOUCH_AMBIGUITY, DISCONNECT_INVALID
    local_monotonic_ns: int

class PhysicianCommandPayload(BaseModel):
    command: str  # START_TEST, PAUSE_TEST, RESUME_TEST, ABORT_TEST, SKIP_TRIAL, NEXT_MODULE
    target_module: Optional[str] = None
    target_eye: Optional[str] = None
    parameters: Dict[str, Any] = Field(default_factory=dict)

class PhysicianObservationPayload(BaseModel):
    eye: str = "OD"
    category: str  # PUPILLARY, MOTILITY, ANTERIOR_CHAMBER, FUNDUS
    findings: Dict[str, Any]
    notes: Optional[str] = None
    override_reason: Optional[str] = None

class EmergencyStopPayload(BaseModel):
    triggered_by: str  # PATIENT_HARDWARE_BUTTON, PHYSICIAN_ABORT, SUPERVISOR
    reason: str = "Patient reported discomfort or requested halt"
    timestamp_ns: int

class IceServerConfig(BaseModel):
    urls: List[str]
    username: Optional[str] = None
    credential: Optional[str] = None

class IceServersResponse(BaseModel):
    ice_servers: List[IceServerConfig]
    ttl_seconds: int = 86400

class WebRtcSignalingPayload(BaseModel):
    sdp: Optional[str] = None
    type: Optional[str] = None  # "offer" or "answer"
    candidate: Optional[str] = None
    sdpMid: Optional[str] = None
    sdpMLineIndex: Optional[int] = None

class StreamStatusPayload(BaseModel):
    is_streaming: bool = False
    resolution_width: int = 1080
    resolution_height: int = 2400
    frame_rate: int = 30
    dropped_frames: int = 0
    bitrate_kbps: int = 2000
    orientation: int = 0  # 0, 90, 180, 270 degrees
    capture_interrupted: bool = False
    reason: Optional[str] = None

class EyeTrackingTelemetryPayload(BaseModel):
    left_iris_detected: bool = False
    right_iris_detected: bool = False
    blink_active: bool = False
    eyelid_aperture_mm: Optional[float] = None
    fixation_bcea_deg2: Optional[float] = None
    optical_status: str = "OPTICALLY_OCCLUDED_BY_VRBOX"
    tracking_confidence: float = 0.0
    is_clinical_gated: bool = True
    thermal_throttling: bool = False
    processing_latency_ms: float = 0.0

class OpticalInterlockTelemetryPayload(BaseModel):
    optical_state: OpticalConfigurationState = OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED
    od_ring_type: str = "STANDARD_VR_EYEPIECE"
    os_ring_type: str = "STANDARD_VR_EYEPIECE"
    interlock_active: bool = False
    prohibited_tests: List[str] = Field(default_factory=list)
    permitted_tests: List[str] = Field(default_factory=list)
    timestamp_ns: int = Field(default_factory=lambda: int(time.time() * 1_000_000_000))

class WebSocketMessageEnvelope(BaseModel):
    """Canonical envelope for all remote session WebSocket traffic."""
    model_config = ConfigDict(extra="ignore")

    version: str = "1.0"
    session_id: str
    message_id: str
    sender_id: str
    sender_role: RemoteRole
    message_type: WebSocketMessageType
    sequence_number: int = Field(ge=0, description="Monotonically incrementing sequence number per sender")
    timestamp_ns: int = Field(default_factory=lambda: int(time.time() * 1_000_000_000))
    payload: Dict[str, Any] = Field(default_factory=dict)
    signature: Optional[str] = None  # Optional HMAC signature for audit verification
