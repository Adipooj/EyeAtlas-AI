/**
 * EyeAtlas VR - WebRTC MediaProjection Streaming Audit Suite (CR-002-E).
 * Executed via Node.js native test runner (node --test).
 */

import test from 'node:test';
import assert from 'node:assert';
import { RemoteWebSocketClient } from '../src/api/remote_client.ts';
import { WebRtcStreamReceiver } from '../src/api/webrtc_receiver.ts';
import { RemotePortalView } from '../src/components/RemotePortalView.ts';

test('CR-002-E WebRTC - Receiver initialization and ICE servers configuration', async () => {
  const client = new RemoteWebSocketClient();
  const receiver = new WebRtcStreamReceiver({
    remoteClient: client,
    iceServers: [
      { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }
    ]
  });

  assert.strictEqual(receiver.getIsStreaming(), false);
  const metrics = receiver.getMetrics();
  assert.strictEqual(metrics.width, 0);
  assert.strictEqual(metrics.fps, 0);
  assert.strictEqual(metrics.isStreaming, false);
});

test('CR-002-E WebRTC - Offer handling and answer generation', async () => {
  const client = new RemoteWebSocketClient();
  let dispatchedAnswer = null;

  client.sendEnvelope = (type, payload) => {
    if (type === 'WEBRTC_ANSWER') {
      dispatchedAnswer = payload;
      return true;
    }
    return false;
  };

  const receiver = new WebRtcStreamReceiver({ remoteClient: client });
  const mockSdpOffer = 'v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=ScreenStream\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n';

  const answerSdp = await receiver.handleOffer(mockSdpOffer);
  assert.ok(answerSdp.length > 0);
  assert.ok(dispatchedAnswer !== null);
  assert.strictEqual(dispatchedAnswer.type, 'answer');
  assert.ok(dispatchedAnswer.sdp.includes('v=0'));
});

test('CR-002-E WebRTC - Remote ICE candidate ingestion', async () => {
  const client = new RemoteWebSocketClient();
  const receiver = new WebRtcStreamReceiver({ remoteClient: client });

  // Initialize peer connection
  await receiver.initializePeerConnection();

  const ok = await receiver.addIceCandidate(
    'candidate:842163049 1 udp 1677729535 192.168.1.100 58432 typ srflx raddr 192.168.1.100 rport 58432',
    '0',
    0
  );
  assert.strictEqual(ok, true);
});

test('CR-002-E WebRTC - Stream status telemetry and interruption callback', () => {
  const client = new RemoteWebSocketClient();
  const receiver = new WebRtcStreamReceiver({ remoteClient: client });

  let interruptedReason = null;
  receiver.onStreamInterrupted = (reason) => {
    interruptedReason = reason;
  };

  // 1. Normal active stream status
  receiver.handleStreamStatusTelemetry({
    is_streaming: true,
    resolution_width: 1080,
    resolution_height: 2400,
    frame_rate: 30,
    dropped_frames: 0,
    bitrate_kbps: 2200,
    orientation: 90,
    capture_interrupted: false
  });

  assert.strictEqual(receiver.getIsStreaming(), true);
  const metrics = receiver.getMetrics();
  assert.strictEqual(metrics.width, 1080);
  assert.strictEqual(metrics.height, 2400);
  assert.strictEqual(metrics.fps, 30);
  assert.strictEqual(metrics.droppedFrames, 0);

  // 2. Stream interrupted (patient locked screen / privacy guard)
  receiver.handleStreamStatusTelemetry({
    is_streaming: false,
    resolution_width: 1080,
    resolution_height: 2400,
    frame_rate: 0,
    dropped_frames: 4,
    bitrate_kbps: 0,
    orientation: 90,
    capture_interrupted: true,
    reason: 'Screen locked by patient'
  });

  assert.strictEqual(receiver.getIsStreaming(), false);
  assert.strictEqual(interruptedReason, 'Screen locked by patient');
});

test('CR-002-E WebRTC - HUD Telemetry formatting and quality grading', () => {
  // Normal streaming
  const normalHud = RemotePortalView.formatStreamTelemetry({
    width: 1080,
    height: 2400,
    fps: 30,
    droppedFrames: 0,
    bitrateKbps: 2200,
    isStreaming: true
  });
  assert.strictEqual(normalHud.qualityGrade, 'EXCELLENT');
  assert.strictEqual(normalHud.resolutionText, '1080x2400 @ 30fps');
  assert.match(normalHud.statusText, /LIVE STREAMING \(EXCELLENT\)/);

  // Degraded streaming (high dropped frames)
  const degradedHud = RemotePortalView.formatStreamTelemetry({
    width: 1080,
    height: 2400,
    fps: 18,
    droppedFrames: 25,
    bitrateKbps: 900,
    isStreaming: true
  });
  assert.strictEqual(degradedHud.qualityGrade, 'DEGRADED');

  // Standby
  const standbyHud = RemotePortalView.formatStreamTelemetry({
    width: 0,
    height: 0,
    fps: 0,
    droppedFrames: 0,
    bitrateKbps: 0,
    isStreaming: false
  });
  assert.strictEqual(standbyHud.qualityGrade, 'OFFLINE');
  assert.match(standbyHud.statusText, /STANDBY/);
});
