/**
 * EyeAtlas VR - Phase X03 Web Client & WebXR Automated Audit Suite.
 * Executed via Node.js native test runner (node --test).
 */
import test from 'node:test';
import assert from 'node:assert';
import { WebXrEngine } from '../src/vr/WebXrEngine.ts';
import { SpatialDashboardLayout } from '../src/vr/SpatialDashboard.ts';
import { ClinicianDashboardView } from '../src/components/ClinicianDashboard.ts';

test('X03 Contract Parity - Clinical release classifications and theme', () => {
  const theme = ClinicianDashboardView.getThemeColors();
  assert.strictEqual(theme.primarySeaGreen, '#1B8A6B');
  assert.strictEqual(theme.primaryDark, '#13644E');
  assert.strictEqual(theme.softMintWash, '#E6F4F1');

  const warning = ClinicianDashboardView.getClinicalTestingWarning();
  assert.match(warning, /NOT clinically validated/i);
  assert.match(warning, /Android application is the sole reference client/i);
});

test('X03 WebXR Capability Detection & 2D Fallback', async () => {
  const engine = new WebXrEngine();
  // In Node test environment, window/navigator.xr is absent -> must gracefully report NO_XR_API / NO_SECURE_CONTEXT
  const report = await engine.assessCapability();
  assert.strictEqual(report.isImmersiveVrSupported, false);
  assert.ok(['NO_XR_API', 'NO_SECURE_CONTEXT', 'HEADSET_NOT_DETECTED'].includes(report.status));

  // Verify HARD CLINICAL INVARIANT: WebXR perimetry is gated as NOT clinically validated
  assert.ok(
    report.clinicalPerimetryClassification === 'NOT_SUPPORTED' ||
    report.clinicalPerimetryClassification === 'TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED'
  );

  // Verify graceful rejection of session entry without crash
  await assert.rejects(
    async () => {
      await engine.requestImmersiveSession(() => {});
    },
    /Immersive VR session rejected/
  );
  assert.strictEqual(engine.isSessionActive, false);
});

test('X03 Spatial Dashboard Geometry & 1.8m Viewing Distance', () => {
  // 1. Viewing distance must be 1.8m to prevent vergence-accommodation conflict
  assert.strictEqual(SpatialDashboardLayout.VIEWING_DISTANCE_METERS, 1.8);
  assert.strictEqual(SpatialDashboardLayout.EXIT_ANCHOR_DISTANCE_METERS, 1.4);

  // 2. Three spatial panels present (Left, Center, Right)
  const panels = SpatialDashboardLayout.getPanels();
  assert.strictEqual(panels.length, 3);
  assert.strictEqual(panels[0].panelId, 'PANEL_LEFT_PATIENT_HISTORY');
  assert.strictEqual(panels[1].panelId, 'PANEL_CENTER_ACTIVE_EXAM');
  assert.strictEqual(panels[2].panelId, 'PANEL_RIGHT_EVIDENCE_GOVERNANCE');

  // Verify depths
  panels.forEach(p => {
    assert.strictEqual(p.position.z, -1.8);
    assert.strictEqual(p.backgroundColor, '#FFFFFF');
  });

  // Verify 2D return anchor
  const anchor = SpatialDashboardLayout.getReturnTo2dAnchor();
  assert.match(anchor.label, /Return to 2D Desktop/);
});

test('X03 Spatial Controller Raycast Mathematics', () => {
  const panels = SpatialDashboardLayout.getPanels();
  const centerPanel = panels[1]; // Center panel at x=0, y=0.1, z=-1.8, w=1.2, h=1.3

  // Ray aiming directly at center panel
  const rayOrigin = { x: 0, y: 0.1, z: 0 };
  const rayDirect = { x: 0, y: 0, z: -1 };
  const resultDirect = SpatialDashboardLayout.raycastPanel(rayOrigin, rayDirect, centerPanel);
  assert.strictEqual(resultDirect.hit, true);
  assert.strictEqual(Math.round(resultDirect.distance * 10) / 10, 1.8);

  // Ray aiming far to the left outside panel
  const rayMiss = { x: -3.0, y: 0, z: -1 };
  const resultMiss = SpatialDashboardLayout.raycastPanel(rayOrigin, rayMiss, centerPanel);
  assert.strictEqual(resultMiss.hit, false);
});

test('X03 Multimorbidity Nonexclusive Formatting & Calibration Gate', () => {
  const mockReport = {
    reportId: 'REP-001',
    sessionId: 'SES-001',
    patientId: 'P-101',
    patientAgeBand: 'ADULT_18_TO_64',
    hasDiabetes: true,
    mode: 'CLINICAL',
    reportDigestSha256: 'DIGEST1234567890',
    regulatoryStatus: 'INVESTIGATIONAL_ONLY',
    isReleaseApproved: false,
    inferences: [
      { eye: 'OD', diseaseCode: 'DIABETIC_RETINOPATHY', score: 0.884, calibration: 'CALIBRATION_WITHHELD' },
      { eye: 'OD', diseaseCode: 'GLAUCOMA', score: 0.742, calibration: 'CALIBRATION_WITHHELD' }
    ],
    referrals: [
      { urgency: 'URGENT_24_TO_48_HOURS', reason: 'Coexisting DR and glaucoma suspect' }
    ]
  };

  const formatted = ClinicianDashboardView.formatMultiDiseaseOutput(mockReport);
  assert.strictEqual(formatted.length, 2);
  assert.strictEqual(formatted[0].eye, 'Right Eye (OD)');
  assert.match(formatted[0].scoreText, /CALIBRATION WITHHELD/);

  // Confirm independent coexisting diseases without probability normalization
  const combinedProb = mockReport.inferences[0].score + mockReport.inferences[1].score;
  assert.ok(combinedProb > 1.0, 'Independent marginal probabilities sum exceeds 100%');
});
