/**
 * EyeAtlas VR - Spatial WebXR Dashboard Layout & Interaction Architecture.
 * Designed specifically for VR headsets with 1.8m viewing envelope,
 * high-contrast clinical Sea Green & White palette, and safe 2D exit anchor.
 */

export interface SpatialPanelConfig {
  panelId: string;
  title: string;
  position: { x: number; y: number; z: number };
  rotationY: number; // radians
  width: number;     // meters
  height: number;    // meters
  backgroundColor: string;
  headerColor: string;
}

export class SpatialDashboardLayout {
  // Optimal VR viewing distance to avoid vergence-accommodation conflict
  static readonly VIEWING_DISTANCE_METERS = 1.8;
  static readonly EXIT_ANCHOR_DISTANCE_METERS = 1.4;

  static getPanels(): SpatialPanelConfig[] {
    return [
      {
        panelId: 'PANEL_LEFT_PATIENT_HISTORY',
        title: 'Patient Profile & Longitudinal History',
        position: { x: -1.2, y: 0.1, z: -SpatialDashboardLayout.VIEWING_DISTANCE_METERS },
        rotationY: 0.25, // Angled 14.3 degrees toward user
        width: 1.0,
        height: 1.2,
        backgroundColor: '#FFFFFF',
        headerColor: '#1B8A6B'
      },
      {
        panelId: 'PANEL_CENTER_ACTIVE_EXAM',
        title: 'Active Clinical Findings & Multimorbidity Candidates',
        position: { x: 0.0, y: 0.1, z: -SpatialDashboardLayout.VIEWING_DISTANCE_METERS },
        rotationY: 0.0,
        width: 1.2,
        height: 1.3,
        backgroundColor: '#FFFFFF',
        headerColor: '#13644E'
      },
      {
        panelId: 'PANEL_RIGHT_EVIDENCE_GOVERNANCE',
        title: 'Objective Evidence & Regulatory Hold',
        position: { x: 1.2, y: 0.1, z: -SpatialDashboardLayout.VIEWING_DISTANCE_METERS },
        rotationY: -0.25, // Angled 14.3 degrees toward user
        width: 1.0,
        height: 1.2,
        backgroundColor: '#FFFFFF',
        headerColor: '#1B8A6B'
      }
    ];
  }

  static getReturnTo2dAnchor(): { position: { x: number; y: number; z: number }; label: string } {
    return {
      position: { x: 0.0, y: -0.6, z: -SpatialDashboardLayout.EXIT_ANCHOR_DISTANCE_METERS },
      label: 'Exit Immersive VR — Return to 2D Desktop'
    };
  }

  /**
   * Raycast intersection test between controller ray and spatial panel.
   */
  static raycastPanel(
    rayOrigin: { x: number; y: number; z: number },
    rayDirection: { x: number; y: number; z: number },
    panel: SpatialPanelConfig
  ): { hit: boolean; distance: number; localX: number; localY: number } {
    // Standard plane intersection calculation at z = panel.position.z
    const dz = panel.position.z - rayOrigin.z;
    if (Math.abs(rayDirection.z) < 1e-6) {
      return { hit: false, distance: 0, localX: 0, localY: 0 };
    }
    const t = dz / rayDirection.z;
    if (t < 0) {
      return { hit: false, distance: 0, localX: 0, localY: 0 };
    }

    const hitX = rayOrigin.x + t * rayDirection.x;
    const hitY = rayOrigin.y + t * rayDirection.y;

    const halfW = panel.width / 2;
    const halfH = panel.height / 2;

    const hit =
      hitX >= panel.position.x - halfW &&
      hitX <= panel.position.x + halfW &&
      hitY >= panel.position.y - halfH &&
      hitY <= panel.position.y + halfH;

    return {
      hit,
      distance: t,
      localX: hitX - panel.position.x,
      localY: hitY - panel.position.y
    };
  }
}
