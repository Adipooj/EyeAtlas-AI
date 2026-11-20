/**
 * EyeAtlas VR - WebRTC Screen Stream Receiver (CR-002-E).
 * Connects to the Android MediaProjection stream via WebRTC SDP offer/answer signaling,
 * binds incoming video tracks to HTML5 video elements, and monitors frame/bitrate telemetry.
 */

import type { RemoteWebSocketClient } from './remote_client.ts';
import type {
  WebRtcSignalingPayload,
  StreamStatusPayload,
  IceServerConfig,
} from '../types/contracts.ts';

export interface WebRtcReceiverConfig {
  remoteClient: RemoteWebSocketClient;
  videoElement?: any; // HTMLVideoElement | null
  iceServers?: IceServerConfig[];
}

export interface ReceiverTelemetry {
  connectionState: string;
  iceConnectionState: string;
  width: number;
  height: number;
  fps: number;
  droppedFrames: number;
  bitrateKbps: number;
  isStreaming: boolean;
}

export class WebRtcStreamReceiver {
  private remoteClient: RemoteWebSocketClient;
  private videoElement: any | null = null;
  private peerConnection: any | null = null;
  private iceServers: IceServerConfig[] = [];
  private isStreaming: boolean = false;
  private currentMetrics: ReceiverTelemetry = {
    connectionState: 'new',
    iceConnectionState: 'new',
    width: 0,
    height: 0,
    fps: 0,
    droppedFrames: 0,
    bitrateKbps: 0,
    isStreaming: false,
  };

  // Callbacks
  public onStreamReady?: (stream: any) => void;
  public onMetricsUpdated?: (metrics: ReceiverTelemetry) => void;
  public onConnectionStateChange?: (state: string) => void;
  public onStreamInterrupted?: (reason: string) => void;

  constructor(config: WebRtcReceiverConfig) {
    this.remoteClient = config.remoteClient;
    this.videoElement = config.videoElement || null;
    this.iceServers = config.iceServers || [
      { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] },
    ];

    this.bindSignalingListeners();
  }

  public getIsStreaming(): boolean {
    return this.isStreaming;
  }

  public getMetrics(): ReceiverTelemetry {
    return { ...this.currentMetrics };
  }

  public setVideoElement(video: any) {
    this.videoElement = video;
  }

  /**
   * Wires WebSocket envelope listeners for WebRTC signaling from the Android host.
   */
  private bindSignalingListeners() {
    this.remoteClient.onWebRtcOffer = async (payload: WebRtcSignalingPayload) => {
      if (payload.sdp) {
        await this.handleOffer(payload.sdp);
      }
    };

    this.remoteClient.onIceCandidate = async (payload: WebRtcSignalingPayload) => {
      if (payload.candidate) {
        await this.addIceCandidate(payload.candidate, payload.sdpMid, payload.sdpMLineIndex);
      }
    };

    this.remoteClient.onStreamStatus = (payload: StreamStatusPayload) => {
      this.handleStreamStatusTelemetry(payload);
    };
  }

  /**
   * Initializes RTCPeerConnection with STUN/TURN configurations.
   */
  public async initializePeerConnection(peerCtorOverride?: any): Promise<any> {
    const RTCPeerCtor =
      peerCtorOverride ||
      (typeof window !== 'undefined' ? (window as any).RTCPeerConnection : null);

    if (!RTCPeerCtor) {
      // Mockable fallback for Node test environments
      this.peerConnection = this.createMockPeerConnection();
      return this.peerConnection;
    }

    const rtcConfig: any = {
      iceServers: this.iceServers.length > 0 ? this.iceServers : [
        { urls: 'stun:stun.l.google.com:19302' },
      ],
    };

    this.peerConnection = new RTCPeerCtor(rtcConfig);

    this.peerConnection.onicecandidate = (event: any) => {
      if (event.candidate) {
        this.remoteClient.sendIceCandidate(
          event.candidate.candidate,
          event.candidate.sdpMid,
          event.candidate.sdpMLineIndex
        );
      }
    };

    this.peerConnection.ontrack = (event: any) => {
      this.isStreaming = true;
      this.currentMetrics.isStreaming = true;
      if (this.videoElement && event.streams && event.streams[0]) {
        try {
          this.videoElement.srcObject = event.streams[0];
          this.videoElement.play?.();
        } catch (e) {
          console.warn('Video element play notice:', e);
        }
      }
      this.onStreamReady?.(event.streams ? event.streams[0] : null);
    };

    this.peerConnection.onconnectionstatechange = () => {
      const state = this.peerConnection.connectionState;
      this.currentMetrics.connectionState = state;
      this.onConnectionStateChange?.(state);

      if (state === 'failed' || state === 'disconnected' || state === 'closed') {
        this.isStreaming = false;
        this.currentMetrics.isStreaming = false;
      }
    };

    return this.peerConnection;
  }

  /**
   * Handles incoming SDP Offer from Android MediaProjection host.
   */
  public async handleOffer(sdp: string): Promise<string> {
    if (!this.peerConnection) {
      await this.initializePeerConnection();
    }

    // Set remote description
    if (this.peerConnection.setRemoteDescription) {
      await this.peerConnection.setRemoteDescription({ type: 'offer', sdp });
    }

    // Create SDP Answer
    let answerSdp = '';
    if (this.peerConnection.createAnswer) {
      const answer = await this.peerConnection.createAnswer();
      if (this.peerConnection.setLocalDescription) {
        await this.peerConnection.setLocalDescription(answer);
      }
      answerSdp = answer.sdp || `v=0\r\ns=WebRTC Answer for ${this.remoteClient.getSessionId()}`;
    } else {
      answerSdp = `v=0\r\ns=Simulated WebRTC Answer`;
    }

    // Dispatch answer back to Android host via WebSocket
    this.remoteClient.sendWebRtcAnswer(answerSdp);
    return answerSdp;
  }

  /**
   * Ingests remote ICE candidate from Android host.
   */
  public async addIceCandidate(candidate: string, sdpMid?: string, sdpMLineIndex?: number): Promise<boolean> {
    if (!this.peerConnection) return false;

    if (this.peerConnection.addIceCandidate) {
      try {
        await this.peerConnection.addIceCandidate({
          candidate,
          sdpMid: sdpMid || undefined,
          sdpMLineIndex: sdpMLineIndex !== undefined ? sdpMLineIndex : undefined,
        });
        return true;
      } catch (e) {
        console.warn('Error adding ICE candidate:', e);
        return false;
      }
    }
    return true;
  }

  /**
   * Processes stream telemetry from Android APK.
   */
  public handleStreamStatusTelemetry(status: StreamStatusPayload) {
    this.isStreaming = status.is_streaming;
    this.currentMetrics = {
      ...this.currentMetrics,
      width: status.resolution_width,
      height: status.resolution_height,
      fps: status.frame_rate,
      droppedFrames: status.dropped_frames,
      bitrateKbps: status.bitrate_kbps,
      isStreaming: status.is_streaming,
    };

    if (status.capture_interrupted) {
      this.isStreaming = false;
      this.currentMetrics.isStreaming = false;
      this.onStreamInterrupted?.(status.reason || 'Screen capture interrupted by patient');
    }

    this.onMetricsUpdated?.(this.currentMetrics);
  }

  public close() {
    if (this.peerConnection) {
      try {
        this.peerConnection.close();
      } catch (e) {}
      this.peerConnection = null;
    }
    this.isStreaming = false;
    this.currentMetrics.isStreaming = false;
  }

  private createMockPeerConnection(): any {
    return {
      connectionState: 'connected',
      iceConnectionState: 'connected',
      setRemoteDescription: async () => {},
      createAnswer: async () => ({ type: 'answer', sdp: 'v=0\r\ns=Mock SDP Answer' }),
      setLocalDescription: async () => {},
      addIceCandidate: async () => {},
      close: () => {},
    };
  }
}
