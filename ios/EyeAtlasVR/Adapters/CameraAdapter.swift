//
//  CameraAdapter.swift
//  EyeAtlasVR
//
//  AVFoundation Camera Adapter & Clinical Hardware Gating for iOS.
//  Enforces strict optical safety: smartphone LED flash is PROHIBITED for retinal imaging.
//

import Foundation

public struct IosCameraCapabilityReport {
    public let isManualExposureSupported: Bool
    public let isManualFocusSupported: Bool
    public let isAnteriorImagingSupported: Bool
    public let isFundusImagingSupported: Bool
    public let fundusClassification: ClinicalReleaseClassification
    public let safetyNotice: String
}

public class IosCameraAdapter {
    public init() {}

    /**
     * Assesses iOS camera capabilities against clinical requirements.
     */
    public func assessCapabilities() -> IosCameraCapabilityReport {
        return IosCameraCapabilityReport(
            isManualExposureSupported: true,
            isManualFocusSupported: true,
            isAnteriorImagingSupported: true,
            // HARD MEDICAL INVARIANT: iPhone cannot be used as an improvised fundus camera
            isFundusImagingSupported: false,
            fundusClassification: .NOT_SUPPORTED,
            safetyNotice: "PROHIBITION: Direct smartphone LED flash is strictly forbidden for retinal imaging per ISO 15004-2:2024. Retinal screening requires an independently certified optical module."
        )
    }

    /**
     * Clinical testing gate: Assesses whether iOS visual perimetry can be performed.
     */
    public func assessPerimetryClassification() -> ClinicalReleaseClassification {
        // iOS visual perimetry is NOT clinically validated
        return .NOT_SUPPORTED
    }
}
