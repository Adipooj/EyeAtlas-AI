//
//  ClinicalContracts.swift
//  EyeAtlasVR
//
//  Created for EyeAtlas VR Cross-Platform Expansion (Phase X04).
//  Strict 1:1 parity with Kotlin Models.kt and Python contracts.py.
//

import Foundation

public enum EyeLaterality: String, Codable {
    case OD = "OD" // Right eye
    case OS = "OS" // Left eye
}

public enum CorrectionStatus: String, Codable {
    case UNAIDED = "UNAIDED"
    case CURRENT_HABITUAL = "CURRENT_HABITUAL"
    case PINHOLE = "PINHOLE"
    case BEST_CORRECTED = "BEST_CORRECTED"
}

public enum FixationStatus: String, Codable {
    case STEADY = "STEADY"
    case UNSTEADY = "UNSTEADY"
    case LOST = "LOST"
    case NOT_ASSESSED = "NOT_ASSESSED"
}

public enum SessionState: String, Codable {
    case INITIALIZING = "INITIALIZING"
    case TRIAGED = "TRIAGED"
    case DEVICE_CALIBRATED = "DEVICE_CALIBRATED"
    case FUNCTIONAL_TESTS = "FUNCTIONAL_TESTS"
    case ANTERIOR_CAPTURE = "ANTERIOR_CAPTURE"
    case RETINAL_CAPTURE = "RETINAL_CAPTURE"
    case COMPLETE = "COMPLETE"
    case EMERGENCY_HALT = "EMERGENCY_HALT"
    case RECOVERY = "RECOVERY"
}

public enum ClinicalMode: String, Codable {
    case SIMULATED = "SIMULATED"
    case CLINICAL = "CLINICAL"
}

public enum AppRole: String, Codable {
    case PATIENT_VR = "PATIENT_VR"
    case DOCTOR_SUPERVISOR = "DOCTOR_SUPERVISOR"
    case RESPONSE_PAD = "RESPONSE_PAD"
    case ADMIN = "ADMIN"
}

public enum BroadDomain: String, Codable {
    case VISUAL_CLARITY = "VISUAL_CLARITY"
    case NEAR_VISION = "NEAR_VISION"
    case CHROMATIC = "CHROMATIC"
    case CENTRAL_VISION = "CENTRAL_VISION"
    case PERIPHERAL_FIELD = "PERIPHERAL_FIELD"
    case BINOCULAR = "BINOCULAR"
    case ANTERIOR_SURFACE = "ANTERIOR_SURFACE"
}

public enum DiseaseCode: String, Codable {
    case REFRACTIVE_AMBLYOPIA = "REFRACTIVE_AMBLYOPIA"
    case PRESBYOPIA = "PRESBYOPIA"
    case DRY_EYE = "DRY_EYE"
    case CATARACT = "CATARACT"
    case PTERYGIUM = "PTERYGIUM"
    case RED_EYE_CONJUNCTIVITIS = "RED_EYE_CONJUNCTIVITIS"
    case AMD = "AMD"
    case GLAUCOMA = "GLAUCOMA"
    case DIABETIC_RETINOPATHY = "DIABETIC_RETINOPATHY"
    case STRABISMUS = "STRABISMUS"
    case COLOUR_VISION_DEFICIENCY = "COLOUR_VISION_DEFICIENCY"
    case OTHER_RETINAL = "OTHER_RETINAL"
}

public enum ReferralUrgency: String, Codable {
    case ROUTINE = "ROUTINE"
    case URGENT_24_TO_48_HOURS = "URGENT_24_TO_48_HOURS"
    case EMERGENCY_IMMEDIATE = "EMERGENCY_IMMEDIATE"
}

public enum ClinicalReleaseClassification: String, Codable {
    case VALIDATED = "VALIDATED"
    case TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED = "TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED"
    case NOT_SUPPORTED = "NOT_SUPPORTED"
    case NOT_ASSESSED = "NOT_ASSESSED"
    case UNGRADABLE = "UNGRADABLE"
}

public struct PatientRecord: Codable, Identifiable {
    public var id: String { patientId }
    public let patientId: String
    public let ageBand: String
    public let hasDiabetes: boolean_alias?
    public let symptoms: [String]
    public let currentCorrection: CorrectionStatus
    public let comorbidityFlags: [String: Bool]?

    public init(patientId: String, ageBand: String, hasDiabetes: Bool = false, symptoms: [String] = [], currentCorrection: CorrectionStatus = .UNAIDED, comorbidityFlags: [String: Bool]? = nil) {
        self.patientId = patientId
        self.ageBand = ageBand
        self.hasDiabetes = hasDiabetes
        self.symptoms = symptoms
        self.currentCorrection = currentCorrection
        self.comorbidityFlags = comorbidityFlags
    }
}
public typealias boolean_alias = Bool

public struct ClinicalSession: Codable, Identifiable {
    public var id: String { sessionId }
    public let sessionId: String
    public let patientId: String
    public let patientPhoneId: String
    public let clinicianPhoneId: String
    public let responsePhoneId: String
    public let responseRole: AppRole
    public let mode: ClinicalMode
    public let consentGranted: Bool
    public let startedAtNs: Int64
    public let state: SessionState
}

public struct StimulusTrial: Codable, Identifiable {
    public var id: String { trialId }
    public let trialId: String
    public let sessionId: String
    public let eye: EyeLaterality
    public let testCode: String
    public let targetAngleX: Double
    public let targetAngleY: Double
    public let luminanceNits: Double
    public let opticalIndex: Int
    public let monotonicPresentedNs: Int64
    public let responsePhoneMonotonicNs: Int64?
    public let correctedResponseNs: Int64?
    public let clockOffsetNs: Int64
    public let clockUncertaintyNs: Int64
    public let expectedAnswer: String?
    public let observedAnswer: String?
    public let framesRendered: Int // 24 frames @ 120Hz = 200ms Goldmann III
    public let calibrationHash: String
    public let validityFlags: [String]
}

public struct Measurement: Codable, Identifiable {
    public var id: String { measurementId }
    public let measurementId: String
    public let sessionId: String?
    public let provenanceIds: [String]
    public let eye: EyeLaterality
    public let method: String
    public let value: Double
    public let units: String
    public let ciLower: Double
    public let ciUpper: Double
    public let validity: Bool
    public let limits: [Double]?
}

public struct Finding: Codable {
    public let eye: EyeLaterality
    public let broadDomain: BroadDomain
    public let finding: String
    public let direction: String
    public let severity: String
    public let provenanceIds: [String]
    public let validity: Bool
}

public struct ConditionInference: Codable {
    public let eye: EyeLaterality
    public let diseaseCode: DiseaseCode
    public let probabilityOrScore: Double
    public let calibrationId: String
    public let evidenceFor: [String]
    public let evidenceAgainst: [String]
    public let unknownEvidence: [String]
    public let recommendedTest: String?
}

public struct Referral: Codable {
    public let urgency: ReferralUrgency
    public let reason: String
    public let diseaseSpecificTests: [String]
    public let completionStatus: String
}

public struct ClinicalReport: Codable, Identifiable {
    public var id: String { reportId }
    public let reportId: String
    public let sessionId: String
    public let patientId: String
    public let patientAgeBand: String
    public let hasDiabetes: Bool
    public let mode: ClinicalMode
    public let reportDigestSha256: String
    public let regulatoryStatus: String
    public let isReleaseApproved: Bool
    public let inferences: [ReportInference]
    public let referrals: [ReportReferral]
}

public struct ReportInference: Codable {
    public let eye: EyeLaterality
    public let diseaseCode: DiseaseCode
    public let score: Double
    public let calibration: String
}

public struct ReportReferral: Codable {
    public let urgency: ReferralUrgency
    public let reason: String
}
