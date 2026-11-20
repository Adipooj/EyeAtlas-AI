/**
 * EyeAtlas VR - Remote Physician Web Portal & Mobile Response Pad Controller (CR-002).
 * Implements real-time stimulus reconstruction math, dual-mode Phone 2 touch pad logic,
 * non-exclusive Level 1 & Level 2 multi-morbidity evaluation, and clinical observation contracts.
 */

import type {
  EyeLaterality,
  DiseaseCode,
  ReferralUrgency,
  RemoteRole,
  StimulusReconstructionEvent,
  PatientResponseEvent,
} from '../types/contracts.ts';

export type AppPortalMode = 'PHYSICIAN_COCKPIT' | 'PHONE2_RESPONSE_PAD';

export interface RemotePortalState {
  mode: AppPortalMode;
  sessionId: string;
  pairingPin: string;
  connectionStatus: 'DISCONNECTED' | 'PAIRING' | 'CONNECTED' | 'TESTING' | 'ERROR';
  role: RemoteRole;
  activeStimulus: StimulusReconstructionEvent | null;
  lastResponse: PatientResponseEvent | null;
  connectedPeers: number;
  emergencyHalted: boolean;
  haltReason: string;
}

export interface StimulusRenderResult {
  eye: EyeLaterality | 'OU';
  canvasX: number;
  canvasY: number;
  radiusPx: number;
  color: string;
  fixationStatus: string;
}

export class RemotePortalView {
  public static readonly DEGREES_PER_RADIAN = 180 / Math.PI;
  public static readonly MAX_FIELD_DEG = 30.0; // 30-2 Field of View

  /**
   * Projects visual field polar/Cartesian degrees (-30° to +30°) into 2D canvas pixel coordinates.
   */
  public static projectDegreesToPixels(
    degX: number,
    degY: number,
    canvasWidth: number,
    canvasHeight: number,
    maxDeg: number = 30.0
  ): { x: number; y: number } {
    const centerX = canvasWidth / 2;
    const centerY = canvasHeight / 2;
    const scale = (Math.min(canvasWidth, canvasHeight) * 0.42) / maxDeg;

    // In visual perimetry: +x is right (temporal for OD, nasal for OS), +y is superior
    // Canvas y is inverted (+y is downward)
    const x = centerX + degX * scale;
    const y = centerY - degY * scale;

    return { x, y };
  }

  /**
   * Reconstructs stimulus on a twin-eye canvas.
   * Can be tested with mock or real CanvasRenderingContext2D.
   */
  public static renderStimulusGeometry(
    stimulus: StimulusReconstructionEvent,
    targetEye: EyeLaterality,
    canvasWidth: number,
    canvasHeight: number
  ): StimulusRenderResult | null {
    // If stimulus is for OD only and target canvas is OS, do not render target (render background only)
    if (stimulus.eye_laterality !== 'OU' && stimulus.eye_laterality !== targetEye) {
      return null;
    }

    const degX = stimulus.coordinates?.x_deg ?? 0;
    const degY = stimulus.coordinates?.y_deg ?? 0;
    const { x, y } = this.projectDegreesToPixels(degX, degY, canvasWidth, canvasHeight, this.MAX_FIELD_DEG);

    // Calculate Goldmann stimulus size in pixels (Goldmann III is approx 0.43° = 26 arcmin)
    const goldmannSize = stimulus.stimulus_properties?.goldmann_size || 'III';
    let sizeDeg = 0.43;
    if (goldmannSize === 'I') sizeDeg = 0.11;
    else if (goldmannSize === 'II') sizeDeg = 0.22;
    else if (goldmannSize === 'III') sizeDeg = 0.43;
    else if (goldmannSize === 'IV') sizeDeg = 0.86;
    else if (goldmannSize === 'V') sizeDeg = 1.72;

    const scale = (Math.min(canvasWidth, canvasHeight) * 0.42) / this.MAX_FIELD_DEG;
    const radiusPx = Math.max(2, (sizeDeg * scale) / 2);

    // Calculate luminance/contrast color
    const luminanceNits = stimulus.stimulus_properties?.luminance_nits ?? 31.8;
    const contrastRatio = stimulus.stimulus_properties?.contrast_percent ?? 100;
    const brightness = Math.min(255, Math.max(50, Math.round(luminanceNits * 2.5)));
    const color = `rgba(${brightness}, ${brightness}, ${brightness}, ${contrastRatio / 100})`;

    return {
      eye: stimulus.eye_laterality,
      canvasX: Math.round(x * 10) / 10,
      canvasY: Math.round(y * 10) / 10,
      radiusPx: Math.round(radiusPx * 10) / 10,
      color,
      fixationStatus: stimulus.fixation_status || 'STEADY',
    };
  }

  /**
   * Draws a complete clinical perimetry grid onto a 2D canvas context.
   */
  public static drawPerimetryCanvas(
    ctx: any,
    eye: EyeLaterality,
    width: number,
    height: number,
    activeStimulus: StimulusReconstructionEvent | null
  ): void {
    if (!ctx) return;

    // Background
    ctx.fillStyle = '#0F172A';
    ctx.fillRect(0, 0, width, height);

    const centerX = width / 2;
    const centerY = height / 2;
    const scale = (Math.min(width, height) * 0.42) / this.MAX_FIELD_DEG;

    // Concentric degree rings (10°, 20°, 30°)
    ctx.strokeStyle = '#1E293B';
    ctx.lineWidth = 1;
    [10, 20, 30].forEach(deg => {
      ctx.beginPath();
      ctx.arc(centerX, centerY, deg * scale, 0, 2 * Math.PI);
      ctx.stroke();
    });

    // Crosshairs
    ctx.beginPath();
    ctx.moveTo(centerX - 30 * scale, centerY);
    ctx.lineTo(centerX + 30 * scale, centerY);
    ctx.moveTo(centerX, centerY - 30 * scale);
    ctx.lineTo(centerX, centerY + 30 * scale);
    ctx.stroke();

    // Physiological Blind Spot (Mariotte)
    // OD: +15° Temporal, -1.5° Inferior
    // OS: -15° Temporal, -1.5° Inferior
    const blindSpotX = eye === 'OD' ? centerX + 15 * scale : centerX - 15 * scale;
    const blindSpotY = centerY + 1.5 * scale; // inverted y
    ctx.fillStyle = 'rgba(100, 116, 139, 0.3)';
    ctx.beginPath();
    ctx.ellipse(blindSpotX, blindSpotY, 2.5 * scale, 3.5 * scale, 0, 0, 2 * Math.PI);
    ctx.fill();

    // Fixation Crosshair (Center)
    ctx.strokeStyle = '#10B981'; // Emerald Green
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(centerX - 6, centerY);
    ctx.lineTo(centerX + 6, centerY);
    ctx.moveTo(centerX, centerY - 6);
    ctx.lineTo(centerX, centerY + 6);
    ctx.stroke();

    // Render Active Stimulus if present
    if (activeStimulus) {
      const render = this.renderStimulusGeometry(activeStimulus, eye, width, height);
      if (render) {
        ctx.fillStyle = '#F8FAFC';
        ctx.shadowColor = '#38BDF8';
        ctx.shadowBlur = 8;
        ctx.beginPath();
        ctx.arc(render.canvasX, render.canvasY, render.radiusPx, 0, 2 * Math.PI);
        ctx.fill();
        ctx.shadowBlur = 0;
      }
    }
  }

  /**
   * Formats a clinical event for the live chronological timeline.
   */
  public static formatTimelineEvent(type: string, payload: any): { timeStr: string; text: string; badgeClass: string } {
    const time = new Date().toLocaleTimeString();
    if (type === 'STIMULUS_EVENT') {
      const eye = payload.eye_laterality || 'OU';
      const mod = payload.test_module || 'PERIMETRY';
      const trial = payload.trial_index ?? 0;
      const x = payload.coordinates?.x_deg ?? 0;
      const y = payload.coordinates?.y_deg ?? 0;
      return {
        timeStr: time,
        text: `[Trial #${trial}] ${mod} (${eye}) at (${x.toFixed(1)}°, ${y.toFixed(1)}°) presented`,
        badgeClass: 'badge-stimulus',
      };
    } else if (type === 'RESPONSE_EVENT') {
      const latency = payload.reaction_latency_ms !== undefined ? `${payload.reaction_latency_ms.toFixed(1)}ms` : 'Recorded';
      const src = payload.input_source || 'WEB_RESPONSE_PAD';
      const trial = payload.trial_index ?? 0;
      return {
        timeStr: time,
        text: `[Trial #${trial}] Patient tap detected via ${src} — Latency: ${latency}`,
        badgeClass: 'badge-response',
      };
    } else if (type === 'OBSERVATION_SUBMIT') {
      const eye = payload.eye || 'OD';
      const cat = payload.category || 'GENERAL';
      return {
        timeStr: time,
        text: `Physician recorded ${cat} observation for ${eye}`,
        badgeClass: 'badge-obs',
      };
    } else if (type === 'EMERGENCY_STOP') {
      return {
        timeStr: time,
        text: `EMERGENCY STOP TRIGGERED: ${payload.reason || 'User halt'}`,
        badgeClass: 'badge-emergency',
      };
    }
    return {
      timeStr: time,
      text: `${type}: ${JSON.stringify(payload)}`,
      badgeClass: 'badge-info',
    };
  }

  /**
   * Evaluates Level 1 Broad Functional Domains.
   */
  public static evaluateLevel1FunctionalDomains(data: {
    visualAcuityLogmarOD?: number;
    visualAcuityLogmarOS?: number;
    visualFieldMeanDefectDbOD?: number;
    visualFieldMeanDefectDbOS?: number;
    colourErrorScoreOD?: number;
    colourErrorScoreOS?: number;
    contrastLogOD?: number;
    contrastLogOS?: number;
    hasRapd?: boolean;
    motilityDeficit?: boolean;
  }): Array<{ domain: string; eye: EyeLaterality; status: 'NORMAL' | 'ABNORMAL' | 'SUSPECT' | 'UNASSESSED'; summary: string }> {
    const results: Array<{ domain: string; eye: EyeLaterality; status: 'NORMAL' | 'ABNORMAL' | 'SUSPECT' | 'UNASSESSED'; summary: string }> = [];

    // 1. Visual Clarity (ETDRS)
    if (data.visualAcuityLogmarOD !== undefined) {
      const status = data.visualAcuityLogmarOD <= 0.1 ? 'NORMAL' : data.visualAcuityLogmarOD <= 0.4 ? 'SUSPECT' : 'ABNORMAL';
      results.push({
        domain: 'VISUAL_CLARITY',
        eye: 'OD',
        status,
        summary: `logMAR ${data.visualAcuityLogmarOD.toFixed(2)} (${status})`,
      });
    } else {
      results.push({ domain: 'VISUAL_CLARITY', eye: 'OD', status: 'UNASSESSED', summary: 'Not assessed' });
    }

    if (data.visualAcuityLogmarOS !== undefined) {
      const status = data.visualAcuityLogmarOS <= 0.1 ? 'NORMAL' : data.visualAcuityLogmarOS <= 0.4 ? 'SUSPECT' : 'ABNORMAL';
      results.push({
        domain: 'VISUAL_CLARITY',
        eye: 'OS',
        status,
        summary: `logMAR ${data.visualAcuityLogmarOS.toFixed(2)} (${status})`,
      });
    } else {
      results.push({ domain: 'VISUAL_CLARITY', eye: 'OS', status: 'UNASSESSED', summary: 'Not assessed' });
    }

    // 2. Peripheral Field
    if (data.visualFieldMeanDefectDbOD !== undefined) {
      const status = data.visualFieldMeanDefectDbOD > -2.0 ? 'NORMAL' : data.visualFieldMeanDefectDbOD > -6.0 ? 'SUSPECT' : 'ABNORMAL';
      results.push({
        domain: 'PERIPHERAL_FIELD',
        eye: 'OD',
        status,
        summary: `MD ${data.visualFieldMeanDefectDbOD.toFixed(1)} dB (${status})`,
      });
    }

    // 3. Pupillary Assessment (RAPD)
    if (data.hasRapd !== undefined) {
      results.push({
        domain: 'PUPILLARY_REFLEX',
        eye: 'OD',
        status: data.hasRapd ? 'ABNORMAL' : 'NORMAL',
        summary: data.hasRapd ? 'Relative Afferent Pupillary Defect Observed' : 'No RAPD detected',
      });
    }

    return results;
  }

  /**
   * Evaluates Level 2 Multi-Condition Ranked Screening Candidates.
   * INVARIANT: Independent Sigmoid scores (probabilities do NOT sum to 1.0).
   * Co-existence allowed (e.g. Cataract + Glaucoma).
   */
  public static evaluateLevel2Candidates(
    scores: Partial<Record<DiseaseCode, number>>,
    missingEvidence: string[] = []
  ): Array<{
    diseaseCode: DiseaseCode;
    displayName: string;
    probability: number;
    urgency: ReferralUrgency;
    coexistsWithOthers: boolean;
    missingEvidenceNotes: string[];
    isPrimaryReferral: boolean;
  }> {
    const diseaseNames: Record<DiseaseCode, string> = {
      REFRACTIVE_AMBLYOPIA: 'Refractive Error / Amblyopia',
      PRESBYOPIA: 'Presbyopia',
      DRY_EYE: 'Dry Eye Disease / Ocular Surface Disease',
      CATARACT: 'Cataract / Media Opacity',
      PTERYGIUM: 'Pterygium / Pinguecula',
      RED_EYE_CONJUNCTIVITIS: 'Acute Red Eye / Conjunctivitis',
      AMD: 'Age-Related Macular Degeneration (AMD)',
      GLAUCOMA: 'Suspected Glaucomatous Optic Neuropathy',
      DIABETIC_RETINOPATHY: 'Diabetic Retinopathy',
      STRABISMUS: 'Strabismus / Ocular Misalignment',
      COLOUR_VISION_DEFICIENCY: 'Colour Vision Deficiency (Congenital/Acquired)',
      OTHER_RETINAL: 'Other Retinopathy / Vascular Occlusion',
    };

    const entries = (Object.keys(scores) as DiseaseCode[]).map(code => {
      const prob = scores[code] ?? 0;
      let urgency: ReferralUrgency = 'ROUTINE';

      // Urgent triage triggers
      if (code === 'GLAUCOMA' && prob > 0.7) urgency = 'URGENT_24_TO_48_HOURS';
      if (code === 'AMD' && prob > 0.75) urgency = 'URGENT_24_TO_48_HOURS';
      if (code === 'RED_EYE_CONJUNCTIVITIS' && prob > 0.85) urgency = 'URGENT_24_TO_48_HOURS';

      return {
        diseaseCode: code,
        displayName: diseaseNames[code] || code,
        probability: Math.round(prob * 1000) / 1000,
        urgency,
        coexistsWithOthers: true, // Non-exclusive multi-morbidity
        missingEvidenceNotes: missingEvidence,
        isPrimaryReferral: prob >= 0.5,
      };
    });

    // Sort descending by independent probability score
    return entries.sort((a, b) => b.probability - a.probability);
  }

  /**
   * Formats real-time WebRTC screen stream metrics for HUD display.
   */
  public static formatStreamTelemetry(metrics: {
    width: number;
    height: number;
    fps: number;
    droppedFrames: number;
    bitrateKbps: number;
    isStreaming: boolean;
  }): { statusText: string; resolutionText: string; qualityGrade: string } {
    if (!metrics.isStreaming) {
      return {
        statusText: 'STANDBY (Awaiting patient stream start)',
        resolutionText: 'N/A',
        qualityGrade: 'OFFLINE',
      };
    }
    const resolutionText = `${metrics.width}x${metrics.height} @ ${metrics.fps}fps`;
    let qualityGrade = 'EXCELLENT';
    if (metrics.droppedFrames > 20 || metrics.fps < 20) {
      qualityGrade = 'DEGRADED';
    } else if (metrics.droppedFrames > 5 || metrics.fps < 28) {
      qualityGrade = 'FAIR';
    }
    return {
      statusText: `LIVE STREAMING (${qualityGrade})`,
      resolutionText,
      qualityGrade,
    };
  }

  /**
   * Formats MediaPipe eye-tracking and fixation stability telemetry.
   */
  public static formatEyeTrackingTelemetry(telemetry: {
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
  }): {
    opticalStatusBadge: string;
    fixationStabilityText: string;
    clinicalGateActive: boolean;
    warningText: string;
  } {
    let opticalStatusBadge = 'OPTICALLY OCCLUDED (VRBox Divider)';
    if (telemetry.optical_status === 'OPTIMAL_FRONTAL') {
      opticalStatusBadge = 'OPTIMAL FRONT VIEW';
    } else if (telemetry.optical_status === 'INSUFFICIENT_ILLUMINATION') {
      opticalStatusBadge = 'INSUFFICIENT ILLUMINATION';
    }

    let fixationStabilityText = 'Psychophysical Fallback (Heijl-Krakau Blind Spot Checks)';
    if (telemetry.fixation_bcea_deg2 !== undefined && telemetry.fixation_bcea_deg2 !== null) {
      fixationStabilityText = `BCEA: ${telemetry.fixation_bcea_deg2.toFixed(2)} deg² (${telemetry.fixation_bcea_deg2 < 1.0 ? 'Steady' : 'Unsteady'})`;
    }

    return {
      opticalStatusBadge,
      fixationStabilityText,
      clinicalGateActive: telemetry.is_clinical_gated,
      warningText: 'EXPERIMENTAL MODULE: MediaPipe Iris approximations strictly disabled for clinical diagnostic decisions.',
    };
  }

  /**
   * Formats optical hardware state and detachable lens interlock status for Physician Cockpit (CR-002-G).
   */
  public static formatOpticalInterlock(telemetry: {
    optical_state: string;
    od_ring_type?: string;
    os_ring_type?: string;
    interlock_active?: boolean;
    prohibited_tests?: string[];
    permitted_tests?: string[];
  }): {
    stateLabel: string;
    badgeClass: string;
    isCompoundRiskBlocked: boolean;
    clinicalAdvisory: string;
    permittedSummary: string;
  } {
    const state = telemetry.optical_state || 'UNKNOWN_UNCALIBRATED';
    let stateLabel = 'Unknown / Uncalibrated';
    let badgeClass = 'badge-danger';
    let isCompoundRiskBlocked = false;
    let clinicalAdvisory = 'Verify hardware optical configuration before presenting stimuli.';
    let permittedSummary = 'None';

    switch (state) {
      case 'BOTH_VR_LENSES_INSTALLED':
        stateLabel = 'Both VR Eyepieces Installed (+14.3D)';
        badgeClass = 'badge-success';
        clinicalAdvisory = 'Standard wide-field psychophysical testing ready. Direct anterior/retinal capture is locked.';
        permittedSummary = 'Perimetry 24-2/30-2, Contrast, Amsler, Ishihara, Tumbling E';
        break;
      case 'OD_LENS_REMOVED':
        stateLabel = 'Right Eyepiece (OD) Removed (Direct Bore)';
        badgeClass = 'badge-warning';
        clinicalAdvisory = 'OD ready for anterior torch or 20D fundus imaging. OS ready for wide-field VR.';
        permittedSummary = 'OD: Anterior/Fundus | OS: Wide-Field VR';
        break;
      case 'OS_LENS_REMOVED':
        stateLabel = 'Left Eyepiece (OS) Removed (Direct Bore)';
        badgeClass = 'badge-warning';
        clinicalAdvisory = 'OS ready for anterior torch or 20D fundus imaging. OD ready for wide-field VR.';
        permittedSummary = 'OS: Anterior/Fundus | OD: Wide-Field VR';
        break;
      case 'BOTH_LENSES_REMOVED':
        stateLabel = 'Both VR Eyepieces Removed (Clear Aperture)';
        badgeClass = 'badge-info';
        clinicalAdvisory = 'Unobstructed direct line-of-sight for direct anterior segment & 20D fundus examination.';
        permittedSummary = 'Direct Anterior (OU), Indirect Retinal 20D (OU)';
        break;
      case 'EXTERNAL_ADAPTER_20D_MOUNTED':
        stateLabel = 'External 20D Indirect Condensing Adapter Mounted';
        badgeClass = 'badge-primary';
        clinicalAdvisory = '20D Fundus indirect ophthalmoscopy active. VR eyepiece compound lens focal collapse prevented.';
        permittedSummary = 'Retinal 20D Fundus Indirect Examination';
        break;
      case 'EXTERNAL_ADAPTER_PINHOLE_MOUNTED':
        stateLabel = '1.2mm Pinhole Occluder Adapter Mounted';
        badgeClass = 'badge-primary';
        clinicalAdvisory = 'Pinhole visual acuity differential active (refractive vs. media/macular amblyopia).';
        permittedSummary = 'Pinhole Differential Visual Acuity';
        break;
      default:
        stateLabel = 'Uncalibrated / Unknown Optical State';
        badgeClass = 'badge-danger';
        clinicalAdvisory = 'All tests locked until optical carriage configuration is confirmed.';
        permittedSummary = 'None (Interlock Active)';
        break;
    }

    if (state === 'BOTH_VR_LENSES_INSTALLED') {
      isCompoundRiskBlocked = true;
    }

    return {
      stateLabel,
      badgeClass,
      isCompoundRiskBlocked,
      clinicalAdvisory,
      permittedSummary,
    };
  }

  /**
   * Regulatory Notice string for compliance with CDSCO MDR-2017 Class B SaMD.
   */
  public static getRegulatoryDisclaimer(): string {
    return 'REGULATORY SCREENING NOTICE: EyeAtlas VR is an investigational ophthalmic screening aid (Class B SaMD under CDSCO Medical Device Rules 2017). It is NOT an autonomous diagnostic device. All scores, optotype estimations, and visual field maps require correlation by a licensed ophthalmologist or optometrist before initiating medical or surgical interventions.';
  }
}
