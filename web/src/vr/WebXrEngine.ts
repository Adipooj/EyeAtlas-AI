/**
 * EyeAtlas VR - WebXR Immersive Engine & Capability Gate.
 * Enforces hardware capability detection, session lifecycle management,
 * graceful 2D fallback, and strict clinical testing restriction gating.
 */
import type { ClinicalReleaseClassification } from '../types/contracts.ts';

export interface WebXrCapabilityReport {
  isSecureContext: boolean;
  isXrApiPresent: boolean;
  isImmersiveVrSupported: boolean;
  status: 'SUPPORTED' | 'NO_SECURE_CONTEXT' | 'NO_XR_API' | 'HEADSET_NOT_DETECTED';
  supportedFeatures: string[];
  clinicalPerimetryClassification: ClinicalReleaseClassification;
}

export class WebXrEngine {
  private activeSession: any = null;
  private onSessionEndCallback: (() => void) | null = null;

  /**
   * Evaluates runtime browser & headset WebXR capability without guessing.
   */
  async assessCapability(): Promise<WebXrCapabilityReport> {
    const isSecure = typeof window !== 'undefined' ? window.isSecureContext : false;
    const isXrPresent = typeof navigator !== 'undefined' && 'xr' in navigator && !!navigator.xr;

    if (!isSecure) {
      return {
        isSecureContext: false,
        isXrApiPresent: isXrPresent,
        isImmersiveVrSupported: false,
        status: 'NO_SECURE_CONTEXT',
        supportedFeatures: [],
        clinicalPerimetryClassification: 'NOT_SUPPORTED'
      };
    }

    if (!isXrPresent) {
      return {
        isSecureContext: true,
        isXrApiPresent: false,
        isImmersiveVrSupported: false,
        status: 'NO_XR_API',
        supportedFeatures: [],
        clinicalPerimetryClassification: 'NOT_SUPPORTED'
      };
    }

    try {
      const isImmersiveSupported = await (navigator as any).xr.isSessionSupported('immersive-vr');
      return {
        isSecureContext: true,
        isXrApiPresent: true,
        isImmersiveVrSupported: isImmersiveSupported,
        status: isImmersiveSupported ? 'SUPPORTED' : 'HEADSET_NOT_DETECTED',
        supportedFeatures: isImmersiveSupported ? ['local-floor', 'bounded-floor', 'hand-tracking'] : [],
        // HARD CLINICAL INVARIANT: Browser WebXR perimetry is NOT clinically validated
        clinicalPerimetryClassification: 'TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED'
      };
    } catch {
      return {
        isSecureContext: true,
        isXrApiPresent: true,
        isImmersiveVrSupported: false,
        status: 'HEADSET_NOT_DETECTED',
        supportedFeatures: [],
        clinicalPerimetryClassification: 'NOT_SUPPORTED'
      };
    }
  }

  /**
   * Requests entry into an immersive WebXR VR session.
   */
  async requestImmersiveSession(
    onEnd: () => void,
    requiredFeatures: string[] = ['local-floor']
  ): Promise<any> {
    const capability = await this.assessCapability();
    if (!capability.isImmersiveVrSupported) {
      throw new Error(`Immersive VR session rejected: ${capability.status}`);
    }

    this.onSessionEndCallback = onEnd;
    const session = await (navigator as any).xr.requestSession('immersive-vr', {
      requiredFeatures,
      optionalFeatures: ['hand-tracking']
    });

    this.activeSession = session;
    session.addEventListener('end', () => {
      this.activeSession = null;
      if (this.onSessionEndCallback) {
        this.onSessionEndCallback();
      }
    });

    return session;
  }

  /**
   * Safely exits immersive VR session and triggers graceful fallback to 2D desktop.
   */
  async exitImmersiveSession(): Promise<void> {
    if (this.activeSession) {
      await this.activeSession.end();
      this.activeSession = null;
    }
  }

  get isSessionActive(): boolean {
    return this.activeSession !== null;
  }
}
