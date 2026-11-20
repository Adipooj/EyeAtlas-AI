/**
 * EyeAtlas VR - Responsive 2D Clinician Portal Dashboard.
 * Built with hospital-grade White & Sea Green clinical design system,
 * multi-morbidity nonexclusive candidate review, and WebXR integration triggers.
 */
import type { PatientRecord, ClinicalSession, ClinicalReport } from '../types/contracts.ts';
import type { WebXrCapabilityReport } from '../vr/WebXrEngine.ts';

export interface DashboardState {
  activeTab: 'OVERVIEW' | 'PATIENTS' | 'EXAMINATIONS' | 'REPORTS' | 'ADMIN';
  selectedPatientId: string | null;
  selectedSessionId: string | null;
  vrCapability: WebXrCapabilityReport | null;
  regulatoryNotice: string;
}

export class ClinicianDashboardView {
  static getInitialState(): DashboardState {
    return {
      activeTab: 'OVERVIEW',
      selectedPatientId: 'P-101',
      selectedSessionId: 'SESSION-001',
      vrCapability: null,
      regulatoryNotice: 'INVESTIGATIONAL ONLY — NOT CLEARED BY CDSCO/FDA FOR COMMERCIAL CLINICAL USE'
    };
  }

  static getThemeColors() {
    return {
      background: '#F8FAFC',
      surface: '#FFFFFF',
      primarySeaGreen: '#1B8A6B',
      primaryDark: '#13644E',
      softMintWash: '#E6F4F1',
      borderSlate: '#E2E8F0',
      textPrimary: '#0F172A',
      textMuted: '#64748B',
      alertWarning: '#D97706',
      alertEmergency: '#DC2626'
    };
  }

  static formatMultiDiseaseOutput(report: ClinicalReport): Array<{ eye: string; disease: string; scoreText: string }> {
    return report.inferences.map(inf => ({
      eye: inf.eye === 'OD' ? 'Right Eye (OD)' : 'Left Eye (OS)',
      disease: inf.diseaseCode.replace(/_/g, ' '),
      scoreText: inf.calibration === 'CALIBRATION_WITHHELD'
        ? `Ranked Candidate (${(inf.score * 100).toFixed(1)}%) — [CALIBRATION WITHHELD]`
        : `${(inf.score * 100).toFixed(1)}%`
    }));
  }

  static getClinicalTestingWarning(): string {
    return 'CRITICAL MEDICAL NOTICE: Browser-based camera acquisition and WebXR visual-field stimuli are technically available demonstrations, but NOT clinically validated. Standalone diagnosis using browser interfaces is strictly prohibited. The native Android application is the sole reference client.';
  }
}
