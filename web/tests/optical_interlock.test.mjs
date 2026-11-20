/**
 * Unit test suite for VRBox Optics & Detachable Lens Engineering (CR-002-G).
 * Tests optical configuration formatting, compound lens hazard protection, and safety interlock contracts.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RemotePortalView } from '../src/components/RemotePortalView.ts';

test('CR-002-G Optical Interlock - Default both VR lenses installed state', () => {
  const telemetry = {
    optical_state: 'BOTH_VR_LENSES_INSTALLED',
    od_ring_type: 'STANDARD_VR_EYEPIECE',
    os_ring_type: 'STANDARD_VR_EYEPIECE',
  };

  const formatted = RemotePortalView.formatOpticalInterlock(telemetry);
  assert.equal(formatted.stateLabel, 'Both VR Eyepieces Installed (+14.3D)');
  assert.equal(formatted.badgeClass, 'badge-success');
  assert.equal(formatted.isCompoundRiskBlocked, true); // Direct camera capture locked to prevent compound distortion
  assert.ok(formatted.permittedSummary.includes('Perimetry 24-2/30-2'));
});

test('CR-002-G Optical Interlock - Monocular lens removed states (OD & OS)', () => {
  const odRemoved = RemotePortalView.formatOpticalInterlock({
    optical_state: 'OD_LENS_REMOVED',
    od_ring_type: 'OPEN_APERTURE_NO_LENS',
  });
  assert.ok(odRemoved.stateLabel.includes('OD) Removed'));
  assert.equal(odRemoved.badgeClass, 'badge-warning');
  assert.equal(odRemoved.isCompoundRiskBlocked, false);
  assert.ok(odRemoved.permittedSummary.includes('OD: Anterior/Fundus | OS: Wide-Field VR'));

  const osRemoved = RemotePortalView.formatOpticalInterlock({
    optical_state: 'OS_LENS_REMOVED',
    os_ring_type: 'OPEN_APERTURE_NO_LENS',
  });
  assert.ok(osRemoved.stateLabel.includes('OS) Removed'));
  assert.equal(osRemoved.badgeClass, 'badge-warning');
  assert.ok(osRemoved.permittedSummary.includes('OS: Anterior/Fundus | OD: Wide-Field VR'));
});

test('CR-002-G Optical Interlock - External 20D indirect condensing adapter mounted', () => {
  const adapter20d = RemotePortalView.formatOpticalInterlock({
    optical_state: 'EXTERNAL_ADAPTER_20D_MOUNTED',
    od_ring_type: 'INDIRECT_20D_CONDENSING',
  });
  assert.ok(adapter20d.stateLabel.includes('20D Indirect Condensing Adapter'));
  assert.equal(adapter20d.badgeClass, 'badge-primary');
  assert.equal(adapter20d.isCompoundRiskBlocked, false);
  assert.ok(adapter20d.permittedSummary.includes('Retinal 20D Fundus Indirect Examination'));
  assert.ok(adapter20d.clinicalAdvisory.includes('focal collapse prevented'));
});

test('CR-002-G Optical Interlock - Pinhole occluder adapter mounted', () => {
  const pinhole = RemotePortalView.formatOpticalInterlock({
    optical_state: 'EXTERNAL_ADAPTER_PINHOLE_MOUNTED',
    od_ring_type: 'PINHOLE_1_2MM_OCCLUDER',
  });
  assert.ok(pinhole.stateLabel.includes('1.2mm Pinhole Occluder'));
  assert.equal(pinhole.badgeClass, 'badge-primary');
  assert.ok(pinhole.permittedSummary.includes('Pinhole Differential Visual Acuity'));
});

test('CR-002-G Optical Interlock - Uncalibrated / unknown optical state blocks all tests', () => {
  const uncalibrated = RemotePortalView.formatOpticalInterlock({
    optical_state: 'UNKNOWN_UNCALIBRATED',
  });
  assert.equal(uncalibrated.badgeClass, 'badge-danger');
  assert.ok(uncalibrated.permittedSummary.includes('None (Interlock Active)'));
  assert.ok(uncalibrated.clinicalAdvisory.includes('All tests locked'));
});
