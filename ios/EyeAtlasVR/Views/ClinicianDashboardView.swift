//
//  ClinicianDashboardView.swift
//  EyeAtlasVR
//
//  SwiftUI Clinician Dashboard in hospital-grade White & Sea Green.
//  Displays independent multimorbidity candidates and CDSCO regulatory status.
//

import SwiftUI

public struct EyeAtlasTheme {
    public static let seaGreen = Color(red: 27/255, green: 138/255, blue: 107/255)       // #1B8A6B
    public static let seaGreenDark = Color(red: 19/255, green: 100/255, blue: 78/255)    // #13644E
    public static let mintWash = Color(red: 230/255, green: 244/255, blue: 241/255)      // #E6F4F1
    public static let slateBg = Color(red: 248/255, green: 250/255, blue: 252/255)       // #F8FAFC
    public static let textMain = Color(red: 15/255, green: 23/255, blue: 42/255)         // #0F172A
    public static let amber = Color(red: 217/255, green: 119/255, blue: 6/255)          // #D97706
}

public struct ClinicianDashboardView: View {
    @State private var patientId: String = "P-101"
    @State private var isDiabetesPositive: Bool = true
    @State private var showingSyncAlert = false

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // 1. CDSCO Regulatory Notice Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("INVESTIGATIONAL PROTOTYPE")
                            .font(.caption2)
                            .fontWeight(.heavy)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.yellow.opacity(0.3))
                            .foregroundColor(.orange)
                            .cornerRadius(4)
                        Text("Not cleared by CDSCO/FDA for standalone clinical diagnosis.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Text("Class B SaMD")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(EyeAtlasTheme.seaGreenDark)
                }
                .padding()
                .background(Color.yellow.opacity(0.1))
                .cornerRadius(8)

                // 2. iOS Capability Gating Alert
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(EyeAtlasTheme.amber)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Platform Hardware Capability Boundary")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(EyeAtlasTheme.textMain)
                        Text("iOS Visual Perimetry and Retinal Imaging are NOT CLINICALLY VALIDATED on this device. The native Android application (OnePlus Nord CE 3 5G) is the sole clinical reference.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(EyeAtlasTheme.mintWash)
                .cornerRadius(8)

                // 3. Patient Demographics & Intake
                VStack(alignment: .leading, spacing: 8) {
                    Text("Patient Profile (Active Intake)")
                        .font(.headline)
                        .foregroundColor(EyeAtlasTheme.seaGreenDark)
                    HStack {
                        Text("Patient ID:")
                            .fontWeight(.semibold)
                        Text(patientId)
                        Spacer()
                        Text("Age: 40-59 yr")
                    }
                    HStack {
                        Text("Diabetic Status:")
                            .fontWeight(.semibold)
                        Text(isDiabetesPositive ? "Positive (Mandatory Retinal Pathway)" : "Negative")
                            .foregroundColor(isDiabetesPositive ? EyeAtlasTheme.amber : .primary)
                            .fontWeight(isDiabetesPositive ? .bold : .regular)
                    }
                }
                .padding()
                .background(Color.white)
                .cornerRadius(12)
                .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)

                // 4. Per-Eye Independent Multimorbidity Candidates
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Multimorbidity Candidates")
                            .font(.headline)
                            .foregroundColor(EyeAtlasTheme.seaGreenDark)
                        Spacer()
                        Text("[CALIBRATION WITHHELD]")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(EyeAtlasTheme.amber)
                    }

                    Text("Hard Medical Invariant: Independent marginal sigmoid predictions. Probabilities do not sum to 100%. Same eye may express multiple coexisting disorders.")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    // Candidate 1 (Right Eye)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Right Eye (OD): Diabetic Retinopathy")
                                .fontWeight(.semibold)
                            Spacer()
                            Text("88.4%")
                                .fontWeight(.bold)
                                .foregroundColor(EyeAtlasTheme.seaGreenDark)
                        }
                        Text("Evidence: Microaneurysms detected in posterior pole macula field")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(10)
                    .background(EyeAtlasTheme.mintWash)
                    .cornerRadius(8)

                    // Candidate 2 (Right Eye)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Right Eye (OD): Glaucoma Suspect")
                                .fontWeight(.semibold)
                            Spacer()
                            Text("74.2%")
                                .fontWeight(.bold)
                                .foregroundColor(EyeAtlasTheme.seaGreenDark)
                        }
                        Text("Evidence: Nasal step defect & enlarged cup-to-disc ratio (0.75)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(10)
                    .background(EyeAtlasTheme.slateBg)
                    .cornerRadius(8)

                    // Candidate 3 (Left Eye)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Left Eye (OS): Significant Cataract")
                                .fontWeight(.semibold)
                            Spacer()
                            Text("91.0%")
                                .fontWeight(.bold)
                                .foregroundColor(EyeAtlasTheme.seaGreenDark)
                        }
                        Text("Evidence: LOCS III Grade 2 nuclear opacity confirmed")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(10)
                    .background(EyeAtlasTheme.slateBg)
                    .cornerRadius(8)
                }
                .padding()
                .background(Color.white)
                .cornerRadius(12)
                .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
            }
            .padding()
        }
        .background(EyeAtlasTheme.slateBg)
    }
}
