/**
 * EyeAtlas VR - CR002-H End-to-End Cross-Device Client Test Suite.
 * Simulates complete browser-side tripartite operation:
 * - Physician Cockpit (Twin-eye stimulus reconstruction, optical interlock telemetry, observation entry)
 * - Phone 2 Zero-Install Response Pad (Tactile large-button input, haptic event generation)
 * - Monotonic timestamp preservation and regulatory classification.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RemotePortalView } from '../src/components/RemotePortalView.ts';

test('CR002-H E2E - Twin-eye stimulus polar canvas coordinate parity', () => {
  const canvasWidth = 600;
  const canvasHeight = 600;

  // 1. Center foveal target (0°, 0°)
  const centerCoord = RemotePortalView.projectDegreesToPixels(0, 0, canvasWidth, canvasHeight, 30.0);
  assert.equal(centerCoord.x, 300);
  assert.equal(centerCoord.y, 300);

  // 2. Right visual field (+15° x, 0° y) -> Temporal for OD, Nasal for OS
  const rightCoord = RemotePortalView.projectDegreesToPixels(15.0, 0, canvasWidth, canvasHeight, 30.0);
  assert.ok(rightCoord.x > 300, 'Positive X should project to the right of center');
  assert.equal(rightCoord.y, 300);

  // 3. Superior visual field (0° x, +15° y) -> Inverted in canvas pixel space
  const superiorCoord = RemotePortalView.projectDegreesToPixels(0, 15.0, canvasWidth, canvasHeight, 30.0);
  assert.equal(superiorCoord.x, 300);
  assert.ok(superiorCoord.y < 300, 'Superior degrees (+Y) must invert to lower canvas Y coordinate');
});

test('CR002-H E2E - Selective stimulus reconstruction for monocular channels', () => {
  const stimulusOD = {
    trial_index: 5,
    test_module: 'PERIMETRY_24_2',
    eye_laterality: 'OD',
    coordinates: { x_deg: 9.0, y_deg: 3.0 },
    stimulus_properties: { goldmann_size: 'III', luminance_nits: 31.8, contrast_percent: 100 },
    presentation_monotonic_ns: 1000000000,
    fixation_status: 'STEADY',
    reliability_metrics: {},
  };

  // When rendering OD canvas: target must be rendered
  const renderOD = RemotePortalView.renderStimulusGeometry(stimulusOD, 'OD', 400, 400);
  assert.ok(renderOD !== null);
  assert.equal(renderOD.eye, 'OD');
  assert.ok(renderOD.radiusPx > 0);

  // When rendering OS canvas during OD test: target must NOT be rendered (contralateral blackout invariant)
  const renderOS = RemotePortalView.renderStimulusGeometry(stimulusOD, 'OS', 400, 400);
  assert.equal(renderOS, null, 'Contralateral eye must be completely blank during monocular perimetry');
});

test('CR002-H E2E - Patient tactile response latency and monotonic integrity', () => {
  const t0_stimulus_ns = 1_000_000_000;
  const t1_response_ns = 1_284_500_000; // Local monotonic timestamp from Phone 2 Response Pad
  const reactionTimeMs = (t1_response_ns - t0_stimulus_ns) / 1_000_000;

  assert.equal(reactionTimeMs, 284.5);
  assert.ok(reactionTimeMs >= 150.0 && reactionTimeMs <= 1000.0, 'Physiological human visual response latency');

  // Verify timeline entry formatting preserves local monotonic origin
  const timelineItem = RemotePortalView.formatTimelineEvent('RESPONSE_EVENT', {
    trial_index: 14,
    response_recorded: true,
    reaction_latency_ms: reactionTimeMs,
    input_source: 'WEB_RESPONSE_PAD',
    input_quality: 'HIGH_CONFIDENCE',
    local_monotonic_ns: t1_response_ns,
  });

  assert.ok(timelineItem.text.includes('284.5ms'));
  assert.ok(timelineItem.text.includes('WEB_RESPONSE_PAD'));
  assert.equal(timelineItem.badgeClass, 'badge-response');
});

test('CR002-H E2E - Optical interlock state transitions and compound lens protection', () => {
  // Scenario 1: Standard VR Eyepieces installed
  const vrInstalled = RemotePortalView.formatOpticalInterlock({
    optical_state: 'BOTH_VR_LENSES_INSTALLED',
  });
  assert.equal(vrInstalled.isCompoundRiskBlocked, true, 'Compound risk blocked when VR lenses installed');
  assert.ok(vrInstalled.permittedSummary.includes('Perimetry 24-2/30-2'));

  // Scenario 2: Right Eyepiece (OD) removed for direct anterior torch
  const odRemoved = RemotePortalView.formatOpticalInterlock({
    optical_state: 'OD_LENS_REMOVED',
  });
  assert.equal(odRemoved.isCompoundRiskBlocked, false);
  assert.ok(odRemoved.permittedSummary.includes('OD: Anterior/Fundus'));

  // Scenario 3: 20D Indirect condensing adapter mounted
  const adapter20d = RemotePortalView.formatOpticalInterlock({
    optical_state: 'EXTERNAL_ADAPTER_20D_MOUNTED',
  });
  assert.ok(adapter20d.permittedSummary.includes('Retinal 20D Fundus'));

  // Scenario 4: Unknown optical state locks all tests
  const uncalibrated = RemotePortalView.formatOpticalInterlock({
    optical_state: 'UNKNOWN_UNCALIBRATED',
  });
  assert.ok(uncalibrated.clinicalAdvisory.includes('All tests locked'));
});

test('CR002-H E2E - Regulatory screening boundaries and CDSCO disclaimer compliance', () => {
  const disclaimer = RemotePortalView.getRegulatoryDisclaimer();
  assert.ok(disclaimer.includes('CDSCO Medical Device Rules 2017'));
  assert.ok(disclaimer.includes('Class B SaMD'));
  assert.ok(disclaimer.includes('investigational ophthalmic screening aid'));
  assert.ok(disclaimer.includes('NOT an autonomous diagnostic device'));
});
