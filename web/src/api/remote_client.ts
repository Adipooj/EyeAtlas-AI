/**
 * EyeAtlas VR - Remote WebSocket Client (CR-002).
 * Provides authenticated duplex communication between the Web Physician Cockpit,
 * Web Patient Response Pad (Phone 2), and the Cloud Session Broker.
 */
import type {
  RemoteRole,
  RemoteSessionStatus,
  WebSocketMessageEnvelope,
  WebSocketMessageType,
  StimulusReconstructionEvent,
  PatientResponseEvent,
  WebRtcSignalingPayload,
  StreamStatusPayload,
  IceServerConfig,
  EyeTrackingTelemetryPayload,
  OpticalInterlockTelemetryPayload,
} from '../types/contracts.ts';

export interface RemoteSessionConfig {
  baseUrl: string; // e.g. "http://127.0.0.1:8008"
  wsUrl: string;   // e.g. "ws://127.0.0.1:8008"
}

export class RemoteWebSocketClient {
  private config: RemoteSessionConfig;
  private ws: WebSocket | null = null;
  private sessionId: string = '';
  private clientId: string = '';
  private role: RemoteRole = 'PHYSICIAN_CONTROLLER';
  private token: string = '';
  private sequenceNumber: number = 0;
  private isConnected: boolean = false;

  // Listeners
  public onStimulusEvent?: (event: StimulusReconstructionEvent) => void;
  public onResponseEvent?: (event: PatientResponseEvent) => void;
  public onPeerStatus?: (status: { connectedPeers: number; roles: string[]; activePhysician?: string; activeModule?: string }) => void;
  public onWebRtcOffer?: (payload: WebRtcSignalingPayload) => void;
  public onWebRtcAnswer?: (payload: WebRtcSignalingPayload) => void;
  public onIceCandidate?: (payload: WebRtcSignalingPayload) => void;
  public onStreamStatus?: (payload: StreamStatusPayload) => void;
  public onEyeTrackingTelemetry?: (payload: EyeTrackingTelemetryPayload) => void;
  public onOpticalInterlockStatus?: (payload: OpticalInterlockTelemetryPayload) => void;
  public onEmergencyStop?: (reason: string) => void;
  public onStateChange?: (state: string) => void;
  public onError?: (error: string) => void;

  constructor(config: RemoteSessionConfig = { baseUrl: 'http://127.0.0.1:8008', wsUrl: 'ws://127.0.0.1:8008' }) {
    this.config = config;
    this.clientId = `web-${Math.random().toString(36).substring(2, 9)}`;
  }

  public getSessionId(): string {
    return this.sessionId;
  }

  public getRole(): RemoteRole {
    return this.role;
  }

  public getIsConnected(): boolean {
    return this.isConnected;
  }

  /**
   * Creates a new remote session via REST API.
   */
  async createSession(patientId = 'P-101', tenantId = 'TENANT-APEX'): Promise<{ sessionId: string; pin: string; qrPayload: string; token: string }> {
    const res = await fetch(`${this.config.baseUrl}/api/v1/remote/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        patient_id: patientId,
        device_id: this.clientId,
        tenant_id: tenantId,
      }),
    });
    if (!res.ok) throw new Error(`Failed to create remote session: ${res.statusText}`);
    const data = await res.json();
    this.sessionId = data.session_id;
    this.token = data.patient_vr_token;
    return {
      sessionId: data.session_id,
      pin: data.pairing_pin,
      qrPayload: data.qr_payload,
      token: data.patient_vr_token,
    };
  }

  /**
   * Joins an existing remote session via 6-digit PIN.
   */
  async joinSession(
    sessionId: string,
    pin: string,
    role: RemoteRole = 'PHYSICIAN_CONTROLLER',
    clinicianUsername = 'dr_remote'
  ): Promise<{ sessionId: string; role: RemoteRole; isAuthoritative: boolean }> {
    const res = await fetch(`${this.config.baseUrl}/api/v1/remote/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        session_id: sessionId,
        pairing_pin: pin,
        role: role,
        device_id: this.clientId,
        clinician_username: clinicianUsername,
      }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ detail: res.statusText }));
      throw new Error(err.detail || `Join failed with status ${res.status}`);
    }
    const data = await res.json();
    this.sessionId = data.session_id;
    this.role = data.role;
    this.token = data.token;
    return {
      sessionId: data.session_id,
      role: data.role,
      isAuthoritative: data.is_authoritative_controller,
    };
  }

  /**
   * Connects to the WebSocket session broker.
   */
  connectWebSocket(wsOverride?: any): Promise<boolean> {
    return new Promise((resolve, reject) => {
      const wsUrl = `${this.config.wsUrl}/api/v1/remote/ws/${this.sessionId}?token=${encodeURIComponent(this.token)}&role=${this.role}&client_id=${this.clientId}`;
      
      try {
        const WebSocketCtor = wsOverride || (typeof window !== 'undefined' ? (window as any).WebSocket : null);
        if (!WebSocketCtor) {
          this.isConnected = true;
          this.onStateChange?.('CONNECTED_MOCK');
          resolve(true);
          return;
        }

        this.ws = new WebSocketCtor(wsUrl);

        this.ws!.onopen = () => {
          this.isConnected = true;
          this.onStateChange?.('CONNECTED');
          resolve(true);
        };

        this.ws!.onmessage = (event: any) => {
          try {
            const envelope: WebSocketMessageEnvelope = JSON.parse(event.data);
            this.handleIncomingEnvelope(envelope);
          } catch (e: any) {
            this.onError?.(`Failed to parse envelope: ${e.message}`);
          }
        };

        this.ws!.onerror = (event: any) => {
          this.onError?.('WebSocket connection error');
          reject(new Error('WebSocket connection error'));
        };

        this.ws!.onclose = () => {
          this.isConnected = false;
          this.onStateChange?.('DISCONNECTED');
        };
      } catch (err: any) {
        reject(err);
      }
    });
  }

  public handleIncomingEnvelope(envelope: WebSocketMessageEnvelope) {
    switch (envelope.message_type) {
      case 'STIMULUS_EVENT':
        if (this.onStimulusEvent) {
          this.onStimulusEvent(envelope.payload as StimulusReconstructionEvent);
        }
        break;

      case 'RESPONSE_EVENT':
        if (this.onResponseEvent) {
          this.onResponseEvent(envelope.payload as PatientResponseEvent);
        }
        break;

      case 'PEER_STATUS':
        if (this.onPeerStatus) {
          this.onPeerStatus({
            connectedPeers: envelope.payload.connected_peers || 0,
            roles: envelope.payload.roles || [],
            activePhysician: envelope.payload.active_physician,
            activeModule: envelope.payload.active_module,
          });
        }
        break;

      case 'EMERGENCY_STOP':
        if (this.onEmergencyStop) {
          this.onEmergencyStop(envelope.payload.reason || 'Emergency halt triggered');
        }
        break;

      case 'WEBRTC_OFFER':
        this.onWebRtcOffer?.(envelope.payload as WebRtcSignalingPayload);
        break;

      case 'WEBRTC_ANSWER':
        this.onWebRtcAnswer?.(envelope.payload as WebRtcSignalingPayload);
        break;

      case 'WEBRTC_ICE_CANDIDATE':
        this.onIceCandidate?.(envelope.payload as WebRtcSignalingPayload);
        break;

      case 'STREAM_STATUS':
        this.onStreamStatus?.(envelope.payload as StreamStatusPayload);
        break;

      case 'EYETRACKING_TELEMETRY':
        this.onEyeTrackingTelemetry?.(envelope.payload as EyeTrackingTelemetryPayload);
        break;

      case 'OPTICAL_INTERLOCK_STATUS':
        this.onOpticalInterlockStatus?.(envelope.payload as OpticalInterlockTelemetryPayload);
        break;

      case 'ERROR':
        this.onError?.(envelope.payload.error || 'Server error');
        break;
    }
  }

  /**
   * Dispatches a typed envelope over the WebSocket connection.
   */
  public sendEnvelope(type: WebSocketMessageType, payload: Record<string, any>): boolean {
    this.sequenceNumber++;
    const envelope: WebSocketMessageEnvelope = {
      version: '1.0',
      session_id: this.sessionId,
      message_id: `msg-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      sender_id: this.clientId,
      sender_role: this.role,
      message_type: type,
      sequence_number: this.sequenceNumber,
      timestamp_ns: Date.now() * 1_000_000,
      payload: payload,
    };

    if (this.ws && this.ws.readyState === 1) { // OPEN
      this.ws.send(JSON.stringify(envelope));
      return true;
    }
    return false;
  }

  // --- Convenience Actions ---

  /**
   * Dispatches patient touch tap from Web Response Pad on Phone 2.
   */
  sendPatientTap(trialIndex: number, inputQuality = 'VALID'): boolean {
    return this.sendEnvelope('RESPONSE_EVENT', {
      trial_index: trialIndex,
      response_recorded: true,
      input_source: 'WEB_RESPONSE_PAD',
      input_quality: inputQuality,
      local_monotonic_ns: Date.now() * 1_000_000,
    });
  }

  /**
   * Dispatches physician control command.
   */
  sendPhysicianCommand(command: string, targetModule?: string, targetEye = 'OD', params: Record<string, any> = {}): boolean {
    return this.sendEnvelope('CONTROL_COMMAND', {
      command: command,
      target_module: targetModule,
      target_eye: targetEye,
      parameters: params,
    });
  }

  /**
   * Dispatches physician clinical observation without modifying raw patient psychophysics.
   */
  sendPhysicianObservation(eye: string, category: string, findings: Record<string, any>, notes?: string, overrideReason?: string): boolean {
    return this.sendEnvelope('OBSERVATION_SUBMIT', {
      eye: eye,
      category: category,
      findings: findings,
      notes: notes,
      override_reason: overrideReason,
      timestamp_utc: new Date().toISOString(),
    });
  }

  /**
   * Dispatches urgent emergency stop.
   */
  sendEmergencyStop(reason = 'Physician or patient requested immediate halt'): boolean {
    return this.sendEnvelope('EMERGENCY_STOP', {
      triggered_by: this.role,
      reason: reason,
      timestamp_ns: Date.now() * 1_000_000,
    });
  }

  /**
   * Dispatches WebRTC SDP Offer.
   */
  sendWebRtcOffer(sdp: string): boolean {
    return this.sendEnvelope('WEBRTC_OFFER', {
      type: 'offer',
      sdp: sdp,
    });
  }

  /**
   * Dispatches WebRTC SDP Answer.
   */
  sendWebRtcAnswer(sdp: string): boolean {
    return this.sendEnvelope('WEBRTC_ANSWER', {
      type: 'answer',
      sdp: sdp,
    });
  }

  /**
   * Dispatches WebRTC ICE Candidate.
   */
  sendIceCandidate(candidate: string, sdpMid?: string, sdpMLineIndex?: number): boolean {
    return this.sendEnvelope('WEBRTC_ICE_CANDIDATE', {
      candidate: candidate,
      sdpMid: sdpMid,
      sdpMLineIndex: sdpMLineIndex,
    });
  }

  /**
   * Dispatches stream telemetry status.
   */
  sendStreamStatus(status: StreamStatusPayload): boolean {
    return this.sendEnvelope('STREAM_STATUS', status);
  }

  /**
   * Dispatches eye-tracking and fixation stability telemetry.
   */
  sendEyeTrackingTelemetry(telemetry: EyeTrackingTelemetryPayload): boolean {
    return this.sendEnvelope('EYETRACKING_TELEMETRY', telemetry);
  }

  /**
   * Fetches validated STUN and TURN configurations from backend.
   */
  async fetchIceServers(): Promise<IceServerConfig[]> {
    try {
      const res = await fetch(`${this.config.baseUrl}/api/v1/remote/ice-servers`);
      if (res.ok) {
        const data = await res.json();
        return data.ice_servers || [];
      }
    } catch (e) {
      console.warn('Failed to fetch ICE servers, using defaults:', e);
    }
    return [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }];
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.isConnected = false;
    this.onStateChange?.('DISCONNECTED');
  }
}
