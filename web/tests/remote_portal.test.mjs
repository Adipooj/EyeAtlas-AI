/**
 * EyeAtlas VR - Phase CR-002 Remote Physician Web Portal & Response Pad Test Suite.
 * Executed via Node.js native test runner (node --test).
 */

import test from 'node:test';
import assert from 'node:assert';
import { RemoteWebSocketClient } from '../src/api/remote_client.ts';
import { RemotePortalView } from '../src/components/RemotePortalView.ts';

test('CR-002 Client - Session lifecycle & envelope construction', () => {
  const client = new RemoteWebSocketClient({ baseUrl: 'http://127.0.0.1:8008', wsUrl: 'ws://127.0.0.1:8008' });
  
  assert.strictEqual(client.getIsConnected(), false);
  assert.strictEqual(client.getRole(), 'PHYSICIAN_CONTROLLER');

  // Verify sendEnvelope builds valid envelope matching Pydantic schema
  let dispatchedPayload = null;
  const mockWs = {
    readyState: 1, // OPEN
    send: (data) => {
      dispatchedPayload = data;
    },
    close: () => {}
  };

  client.ws = mockWs;
  client.sessionId = 'SES-TEST-001';

  const ok = client.sendPatientTap(14, 'VALID');
  assert.strictEqual(ok, true);
  assert.ok(dispatchedPayload !== null);

  const parsed = JSON.parse(dispatchedPayload);
  assert.strictEqual(parsed.version, '1.0');
  assert.strictEqual(parsed.session_id, 'SES-TEST-001');
  assert.strictEqual(parsed.message_type, 'RESPONSE_EVENT');
  assert.strictEqual(parsed.sequence_number, 1);
  assert.strictEqual(parsed.payload.trial_index, 14);
  assert.strictEqual(parsed.payload.input_source, 'WEB_RESPONSE_PAD');
  assert.strictEqual(parsed.payload.input_quality, 'VALID');
  assert.ok(parsed.timestamp_ns > 0);
  assert.ok(parsed.payload.local_monotonic_ns > 0);
});

test('CR-002 Client - Sequence numbering increments strictly monotonically', () => {
  const client = new RemoteWebSocketClient();
  const mockWs = {
    readyState: 1,
    send: () => {},
    close: () => {}
  };
  client.ws = mockWs;
  client.sessionId = 'SES-TEST-002';

  client.sendPhysicianCommand('PAUSE');
  client.sendPhysicianCommand('RESUME');
  client.sendPhysicianObservation('OD', 'PUPILLARY_RAPD', { rapd_grade: 2 }, 'Subtle dilation on swing');

  assert.strictEqual(client.sequenceNumber, 3);
});

test('CR-002 Client - Emergency stop broadcast envelope', () => {
  const client = new RemoteWebSocketClient();
  let dispatchedText = '';
  client.ws = {
    readyState: 1,
    send: (msg) => { dispatchedText = msg; },
    close: () => {}
  };
  client.sessionId = 'SES-TEST-003';

  client.sendEmergencyStop('Patient reported severe ocular pain');
  assert.ok(dispatchedText.length > 0);
  const parsed = JSON.parse(dispatchedText);
  assert.strictEqual(parsed.message_type, 'EMERGENCY_STOP');
  assert.strictEqual(parsed.payload.reason, 'Patient reported severe ocular pain');
});

test('CR-002 Client - Incoming envelope dispatch to registered callbacks', () => {
  const client = new RemoteWebSocketClient();
  let receivedStimulus = null;
  let receivedEmergency = null;

  client.onStimulusEvent = (event) => { receivedStimulus = event; };
  client.onEmergencyStop = (reason) => { receivedEmergency = reason; };

  // Dispatch mock stimulus envelope
  client.handleIncomingEnvelope({
    version: '1.0',
    session_id: 'SES-001',
    message_id: 'msg-1',
    sender_id: 'host-apk',
    sender_role: 'PATIENT_VR_HOST',
    message_type: 'STIMULUS_EVENT',
    sequence_number: 10,
    timestamp_ns: 1000,
    payload: {
      trial_index: 5,
      test_module: 'PERIMETRY_24_2',
      eye_laterality: 'OD',
      coordinates: { x_deg: 12.0, y_deg: -6.0 },
      stimulus_properties: { goldmann_size: 'III', luminance_nits: 31.8, contrast_percent: 100 },
      presentation_monotonic_ns: 5000000,
      fixation_status: 'STEADY',
      reliability_metrics: { fixation_loss_rate: 0.0 }
    }
  });

  assert.ok(receivedStimulus !== null);
  assert.strictEqual(receivedStimulus.trial_index, 5);
  assert.strictEqual(receivedStimulus.coordinates.x_deg, 12.0);

  // Dispatch emergency stop envelope
  client.handleIncomingEnvelope({
    version: '1.0',
    session_id: 'SES-001',
    message_id: 'msg-2',
    sender_id: 'pad-phone',
    sender_role: 'PATIENT_RESPONSE_PAD',
    message_type: 'EMERGENCY_STOP',
    sequence_number: 11,
    timestamp_ns: 2000,
    payload: { reason: 'Hardware button held' }
  });

  assert.strictEqual(receivedEmergency, 'Hardware button held');
});

test('CR-002 Mathematics - Degree to pixel projection geometry', () => {
  const width = 300;
  const height = 300;

  // Center (0, 0) -> should be center of canvas (150, 150)
  const center = RemotePortalView.projectDegreesToPixels(0, 0, width, height, 30.0);
  assert.strictEqual(center.x, 150);
  assert.strictEqual(center.y, 150);

  // +15 degrees right (degX = 15, degY = 0) -> x > 150, y = 150
  const right = RemotePortalView.projectDegreesToPixels(15, 0, width, height, 30.0);
  assert.ok(right.x > 150);
  assert.strictEqual(right.y, 150);

  // +10 degrees up (degX = 0, degY = 10) -> x = 150, y < 150 (canvas y inverted)
  const up = RemotePortalView.projectDegreesToPixels(0, 10, width, height, 30.0);
  assert.strictEqual(up.x, 150);
  assert.ok(up.y < 150);
});

test('CR-002 Mathematics - Stimulus geometry twin-eye selective rendering', () => {
  const width = 300;
  const height = 300;

  const odStimulus = {
    trial_index: 12,
    test_module: 'PERIMETRY_24_2',
    eye_laterality: 'OD',
    coordinates: { x_deg: 9.0, y_deg: 3.0 },
    stimulus_properties: { goldmann_size: 'III', luminance_nits: 31.8, contrast_percent: 100 },
    presentation_monotonic_ns: 1234567,
    fixation_status: 'STEADY',
    reliability_metrics: {}
  };

  // Rendering OD stimulus on OD canvas -> should return render geometry
  const renderOD = RemotePortalView.renderStimulusGeometry(odStimulus, 'OD', width, height);
  assert.ok(renderOD !== null);
  assert.strictEqual(renderOD.eye, 'OD');
  assert.strictEqual(renderOD.fixationStatus, 'STEADY');
  assert.ok(renderOD.radiusPx > 0);

  // Rendering OD stimulus on OS canvas -> MUST be null (monocular suppression)
  const renderOS = RemotePortalView.renderStimulusGeometry(odStimulus, 'OS', width, height);
  assert.strictEqual(renderOS, null);

  // Binocular OU stimulus -> renders on both OD and OS
  const binocularStimulus = {
    ...odStimulus,
    eye_laterality: 'OU'
  };
  assert.ok(RemotePortalView.renderStimulusGeometry(binocularStimulus, 'OD', width, height) !== null);
  assert.ok(RemotePortalView.renderStimulusGeometry(binocularStimulus, 'OS', width, height) !== null);
});

test('CR-002 Clinical - Event timeline formatting', () => {
  const stimEvent = RemotePortalView.formatTimelineEvent('STIMULUS_EVENT', {
    trial_index: 3,
    test_module: 'PERIMETRY_24_2',
    eye_laterality: 'OD',
    coordinates: { x_deg: 6.0, y_deg: 3.0 }
  });
  assert.strictEqual(stimEvent.badgeClass, 'badge-stimulus');
  assert.match(stimEvent.text, /\[Trial #3\]/);
  assert.match(stimEvent.text, /OD/);

  const respEvent = RemotePortalView.formatTimelineEvent('RESPONSE_EVENT', {
    trial_index: 3,
    input_source: 'WEB_RESPONSE_PAD',
    reaction_latency_ms: 242.8
  });
  assert.strictEqual(respEvent.badgeClass, 'badge-response');
  assert.match(respEvent.text, /242.8ms/);
  assert.match(respEvent.text, /WEB_RESPONSE_PAD/);
});

test('CR-002 Clinical - Level 1 Broad Functional Domains evaluation', () => {
  const domains = RemotePortalView.evaluateLevel1FunctionalDomains({
    visualAcuityLogmarOD: 0.00,
    visualAcuityLogmarOS: 0.38,
    visualFieldMeanDefectDbOD: -1.2,
    hasRapd: true
  });

  assert.strictEqual(domains.length, 4);

  const vaOD = domains.find(d => d.domain === 'VISUAL_CLARITY' && d.eye === 'OD');
  assert.strictEqual(vaOD?.status, 'NORMAL');

  const vaOS = domains.find(d => d.domain === 'VISUAL_CLARITY' && d.eye === 'OS');
  assert.strictEqual(vaOS?.status, 'SUSPECT');

  const fieldOD = domains.find(d => d.domain === 'PERIPHERAL_FIELD' && d.eye === 'OD');
  assert.strictEqual(fieldOD?.status, 'NORMAL');

  const rapd = domains.find(d => d.domain === 'PUPILLARY_REFLEX');
  assert.strictEqual(rapd?.status, 'ABNORMAL');
});

test('CR-002 Clinical - Level 2 Independent Sigmoid heads (no softmax sum-to-1)', () => {
  const candidates = RemotePortalView.evaluateLevel2Candidates(
    {
      DIABETIC_RETINOPATHY: 0.884,
      CATARACT: 0.762,
      GLAUCOMA: 0.742,
      REFRACTIVE_AMBLYOPIA: 0.120
    },
    ['Missing: Dilated Fundus Exam', 'Missing: Corneal Pachymetry']
  );

  // Invariant: sum of scores > 100% allowed (independent sigmoid models)
  const sumScores = candidates.reduce((acc, c) => acc + c.probability, 0);
  assert.ok(sumScores > 1.5, `Sum of scores ${sumScores} demonstrates non-exclusive independent heads`);

  // Verify co-existence
  assert.strictEqual(candidates[0].diseaseCode, 'DIABETIC_RETINOPATHY');
  assert.strictEqual(candidates[1].diseaseCode, 'CATARACT');
  assert.strictEqual(candidates[0].coexistsWithOthers, true);

  // Verify Glaucoma > 0.7 triggers urgent referral
  const glaucoma = candidates.find(c => c.diseaseCode === 'GLAUCOMA');
  assert.strictEqual(glaucoma?.urgency, 'URGENT_24_TO_48_HOURS');

  // Verify missing evidence notes preserved
  assert.strictEqual(candidates[0].missingEvidenceNotes.length, 2);
});

test('CR-002 Regulatory - Mandatory screening classification and CDSCO disclaimer', () => {
  const disclaimer = RemotePortalView.getRegulatoryDisclaimer();
  assert.match(disclaimer, /CDSCO Medical Device Rules 2017/);
  assert.match(disclaimer, /Class B SaMD/);
  assert.match(disclaimer, /NOT an autonomous diagnostic device/);
  assert.match(disclaimer, /licensed ophthalmologist/);
});

test('CR-002-F MediaPipe - Eye tracking telemetry formatting and clinical safety gate', () => {
  const occludedTelemetry = RemotePortalView.formatEyeTrackingTelemetry({
    left_iris_detected: false,
    right_iris_detected: false,
    blink_active: false,
    eyelid_aperture_mm: 9.4,
    fixation_bcea_deg2: 0.72,
    optical_status: 'OPTICALLY_OCCLUDED_BY_VRBOX',
    tracking_confidence: 0.0,
    is_clinical_gated: true,
    thermal_throttling: false,
    processing_latency_ms: 14.2
  });

  assert.strictEqual(occludedTelemetry.opticalStatusBadge, 'OPTICALLY OCCLUDED (VRBox Divider)');
  assert.strictEqual(occludedTelemetry.clinicalGateActive, true);
  assert.match(occludedTelemetry.fixationStabilityText, /BCEA: 0.72 deg²/);
  assert.match(occludedTelemetry.warningText, /MediaPipe Iris approximations strictly disabled for clinical diagnostic decisions/);
});
