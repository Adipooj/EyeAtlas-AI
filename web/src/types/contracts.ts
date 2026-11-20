/**
 * EyeAtlas VR - Cross-Platform TypeScript Clinical Contracts.
 * Strict 1:1 parity with Kotlin Models.kt and Python contracts.py.
 */

export type EyeLaterality = 'OD' | 'OS';

export type CorrectionStatus = 'UNAIDED' | 'CURRENT_HABITUAL' | 'PINHOLE' | 'BEST_CORRECTED';

export type FixationStatus = 'STEADY' | 'UNSTEADY' | 'LOST' | 'NOT_ASSESSED';

export type SessionState =
  | 'INITIALIZING'
  | 'TRIAGED'
  | 'DEVICE_CALIBRATED'
  | 'FUNCTIONAL_TESTS'
  | 'ANTERIOR_CAPTURE'
  | 'RETINAL_CAPTURE'
  | 'COMPLETE'
  | 'EMERGENCY_HALT'
  | 'RECOVERY';

export type ClinicalMode = 'SIMULATED' | 'CLINICAL';

export type AppRole = 'PATIENT_VR' | 'DOCTOR_SUPERVISOR' | 'RESPONSE_PAD' | 'ADMIN';

export type BroadDomain =
  | 'VISUAL_CLARITY'
  | 'NEAR_VISION'
  | 'CHROMATIC'
  | 'CENTRAL_VISION'
  | 'PERIPHERAL_FIELD'
  | 'BINOCULAR'
  | 'ANTERIOR_SURFACE';

export type DiseaseCode =
  | 'REFRACTIVE_AMBLYOPIA'
  | 'PRESBYOPIA'
  | 'DRY_EYE'
  | 'CATARACT'
  | 'PTERYGIUM'
  | 'RED_EYE_CONJUNCTIVITIS'
  | 'AMD'
  | 'GLAUCOMA'
  | 'DIABETIC_RETINOPATHY'
  | 'STRABISMUS'
  | 'COLOUR_VISION_DEFICIENCY'
  | 'OTHER_RETINAL';

export type ReferralUrgency = 'ROUTINE' | 'URGENT_24_TO_48_HOURS' | 'EMERGENCY_IMMEDIATE';

export type ClinicalReleaseClassification =
  | 'VALIDATED'
  | 'TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED'
  | 'NOT_SUPPORTED'
  | 'NOT_ASSESSED'
  | 'UNGRADABLE';

export interface PatientRecord {
  patientId: string;
  ageBand: string;
  hasDiabetes: boolean;
  symptoms: string[];
  currentCorrection: CorrectionStatus;
  comorbidityFlags?: Record<string, boolean>;
}

export interface ClinicalSession {
  sessionId: string;
  patientId: string;
  patientPhoneId: string;
  clinicianPhoneId: string;
  responsePhoneId: string;
  responseRole: AppRole;
  mode: ClinicalMode;
  consentGranted: boolean;
  startedAtNs: number;
  state: SessionState;
}

export interface StimulusTrial {
  trialId: string;
  sessionId: string;
  eye: EyeLaterality;
  testCode: string;
  targetAngleX: number;
  targetAngleY: number;
  luminanceNits: number;
  opticalIndex: number;
  monotonicPresentedNs: number;
  responsePhoneMonotonicNs?: number;
  correctedResponseNs?: number;
  clockOffsetNs: number;
  clockUncertaintyNs: number;
  expectedAnswer?: string;
  observedAnswer?: string;
  framesRendered: number; // 24 frames @ 120Hz = 200ms Goldmann III
  calibrationHash: string;
  validityFlags: string[];
}

export interface Measurement {
  measurementId: string;
  sessionId?: string;
  provenanceIds: string[];
  eye: EyeLaterality;
  method: string;
  value: number;
  units: string;
  ciLower: number;
  ciUpper: number;
  validity: boolean;
  limits?: number[];
}

export interface Finding {
  eye: EyeLaterality;
  broadDomain: BroadDomain;
  finding: string;
  direction: string;
  severity: string;
  provenanceIds: string[];
  validity: boolean;
}

export interface ConditionInference {
  eye: EyeLaterality;
  diseaseCode: DiseaseCode;
  probabilityOrScore: number;
  calibrationId: string;
  evidenceFor: string[];
  evidenceAgainst: string[];
  unknownEvidence: string[];
  recommendedTest?: string;
}

export interface Referral {
  urgency: ReferralUrgency;
  reason: string;
  diseaseSpecificTests: string[];
  completionStatus: string;
}

export interface ClinicalReport {
  reportId: string;
  sessionId: string;
  patientId: string;
  patientAgeBand: string;
  hasDiabetes: boolean;
  mode: ClinicalMode;
  reportDigestSha256: string;
  regulatoryStatus: string;
  isReleaseApproved: boolean;
  inferences: Array<{
    eye: EyeLaterality;
    diseaseCode: DiseaseCode;
    score: number;
    calibration: string;
  }>;
  referrals: Array<{
    urgency: ReferralUrgency;
    reason: string;
  }>;
}

export type RemoteRole = 'PATIENT_VR_HOST' | 'PHYSICIAN_CONTROLLER' | 'PATIENT_RESPONSE_PAD' | 'OBSERVER';

export type RemoteSessionStatus = 'PENDING_PAIRING' | 'CONNECTED' | 'TESTING' | 'PAUSED' | 'FINALIZED' | 'REVOKED' | 'EXPIRED';

export type WebSocketMessageType =
  | 'AUTH'
  | 'AUTH_ACK'
  | 'PEER_STATUS'
  | 'HEARTBEAT'
  | 'STIMULUS_EVENT'
  | 'RESPONSE_EVENT'
  | 'CONTROL_COMMAND'
  | 'OBSERVATION_SUBMIT'
  | 'EMERGENCY_STOP'
  | 'SYNC_ACK'
  | 'REPLAY_REQUEST'
  | 'REPLAY_RESPONSE'
  | 'WEBRTC_OFFER'
  | 'WEBRTC_ANSWER'
  | 'WEBRTC_ICE_CANDIDATE'
  | 'STREAM_STATUS'
  | 'EYETRACKING_TELEMETRY'
  | 'OPTICAL_INTERLOCK_STATUS'
  | 'ERROR';

export type OpticalConfigurationState =
  | 'BOTH_VR_LENSES_INSTALLED'
  | 'OD_LENS_REMOVED'
  | 'OS_LENS_REMOVED'
  | 'BOTH_LENSES_REMOVED'
  | 'EXTERNAL_ADAPTER_20D_MOUNTED'
  | 'EXTERNAL_ADAPTER_PINHOLE_MOUNTED'
  | 'UNKNOWN_UNCALIBRATED';

export type AdapterRingType =
  | 'STANDARD_VR_EYEPIECE'
  | 'TRIAL_LENS_CARRIER'
  | 'PINHOLE_1_2MM_OCCLUDER'
  | 'INDIRECT_20D_CONDENSING'
  | 'OPEN_APERTURE_NO_LENS';

export interface WebSocketMessageEnvelope {
  version: string;
  session_id: string;
  message_id: string;
  sender_id: string;
  sender_role: RemoteRole;
  message_type: WebSocketMessageType;
  sequence_number: number;
  timestamp_ns: number;
  payload: Record<string, any>;
  signature?: string;
}

export interface StimulusReconstructionEvent {
  trial_index: number;
  test_module: string;
  eye_laterality: EyeLaterality | 'OU';
  coordinates: { x_deg?: number; y_deg?: number };
  stimulus_properties: Record<string, any>;
  presentation_monotonic_ns: number;
  fixation_status: string;
  reliability_metrics: Record<string, number>;
}

export interface PatientResponseEvent {
  trial_index: number;
  response_recorded: boolean;
  reaction_latency_ms?: number;
  input_source: string;
  input_quality: string;
  local_monotonic_ns: number;
}

export interface IceServerConfig {
  urls: string[];
  username?: string;
  credential?: string;
}

export interface WebRtcSignalingPayload {
  sdp?: string;
  type?: 'offer' | 'answer';
  candidate?: string;
  sdpMid?: string;
  sdpMLineIndex?: number;
}

export interface StreamStatusPayload {
  is_streaming: boolean;
  resolution_width: number;
  resolution_height: number;
  frame_rate: number;
  dropped_frames: number;
  bitrate_kbps: number;
  orientation: number;
  capture_interrupted: boolean;
  reason?: string;
}

export interface EyeTrackingTelemetryPayload {
  left_iris_detected: boolean;
  right_iris_detected: boolean;
  blink_active: boolean;
  eyelid_aperture_mm?: number;
  fixation_bcea_deg2?: number;
  optical_status: string;
  tracking_confidence: number;
  is_clinical_gated: boolean;
  thermal_throttling: boolean;
  processing_latency_ms: number;
}

export interface OpticalInterlockTelemetryPayload {
  optical_state: OpticalConfigurationState;
  od_ring_type: string;
  os_ring_type: string;
  interlock_active: boolean;
  prohibited_tests: string[];
  permitted_tests: string[];
  timestamp_ns: number;
}


