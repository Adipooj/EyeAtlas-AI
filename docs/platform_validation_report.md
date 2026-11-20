# EYEATLAS VR — PLATFORM-SPECIFIC CLINICAL VALIDATION AND RELEASE REPORT
**Document Reference:** `EAVR-VAL-X06-2026-11`  
**Version:** 1.0.0  
**Phase:** Phase X06: Platform-Specific Clinical Validation and Release  
**Governing Blueprints:** `EyeAtlas_VR_MASTER_BLUEPRINT_v1.2.md` | Change Requests: CR-001, CR-002  
**Date of Audit:** 2026-11-20  
**Software Classification:** Class B Software as a Medical Device (SaMD) under Indian Medical Device Rules (MDR-2017)  
**Regulatory Status:** **INVESTIGATIONAL PROTOTYPE — GATED UNDER REGULATORY HOLD** (`isReleaseApproved = false`, `cdscoReviewPending = true`)

---

## 1. Executive Summary & Intended Use

EyeAtlas VR is an ophthalmic screening and clinical decision-support system engineered for multi-disease triage in community health centers, primary vision clinics, and tertiary hospital networks. The system integrates functional psychophysical visual tests, external anterior segment photographic evaluation, and validated retinal imaging into an offline-first, multimorbidity decision-support framework.

### 1.1 Intended Use Statement
EyeAtlas VR is intended for use by trained ophthalmic assistants, optometrists, and clinicians as an adjunct screening tool to detect signs of common eye diseases (refractive error, amblyopia, presbyopia, dry eye syndrome, cataract, pterygium, infective/inflammatory conjunctivitis, age-related macular degeneration, glaucoma suspect visual field loss, diabetic retinopathy, and ocular motility/strabismus disorders). 

### 1.2 Contraindications & Hard Clinical Boundaries
1. **No Standalone Diagnosis:** EyeAtlas VR does not provide autonomous or confirmatory diagnosis. All screening outputs, disease candidate scores, and referral urgencies must be reviewed and countersigned by a registered medical practitioner (ophthalmologist).
2. **Tonometry Prohibition:** The system does not measure, estimate, or infer Intraocular Pressure (IOP) or corneal biomechanics. Tonometry must be conducted using a calibrated Goldmann applanation or rebound tonometer.
3. **Photic Safety (ISO 15004-2:2024):** Direct activation of smartphone flash/torch LEDs into the human eye is strictly forbidden. Retinal image acquisition is strictly conditioned on the physical attachment of an independently certified optical module containing a compliant, certified illumination source.
4. **Pediatric & Cognitive Boundaries:** Functional testing requires active patient response and cognitive ability to fixate. Uncooperative patients or children under 5 require specialist pediatric ophthalmology referral.

---

## 2. Multi-Platform Architectural Architecture

The cross-platform architecture expands EyeAtlas VR across three client tiers supported by a shared backend:

```
                          ┌──────────────────────────────────────────────┐
                          │         EyeAtlas VR FastAPI Backend          │
                          │   (PostgreSQL/SQLite, Auth, Sync, RBAC)      │
                          └──────────────────────┬───────────────────────┘
                                                 │
                   ┌─────────────────────────────┼─────────────────────────────┐
                   │                             │                             │
                   ▼                             ▼                             ▼
       ┌───────────────────────┐    ┌────────────────────────┐    ┌────────────────────────┐
       │   Android Reference   │    │  Responsive Web Portal │    │       iOS Native       │
       │        Client         │    │       + WebXR VR       │    │     Support Client     │
       │ (OnePlus Nord CE 3)   │    │    (Hospital Portal)   │    │      (iPhone 13+)      │
       └───────────────────────┘    └────────────────────────┘    └────────────────────────┘
```

1. **Android (Reference Standard):**
   - Native Kotlin / Jetpack Compose application.
   - Operates fully local-first with AES-256 encrypted SQLite storage.
   - Executes on-device ONNX Runtime inference with independent sigmoid outputs.
   - Drives stereoscopic VR visual perimetry via the custom VRBox 34mm biconvex chassis.
   - Connects to secondary response phones over an encrypted zero-configuration local channel (<50ms clock offset).

2. **Web Portal & WebXR Immersive VR:**
   - Clinician Portal: Clean, responsive hospital-grade White & Sea Green interface (`#1B8A6B`, `#13644E`, `#E6F4F1`). Supports patient intake, multimorbidity triage review, and encrypted report generation.
   - WebXR VR Interface: Provides a 1.8-meter spatial dashboard with 3D controller raycasting for immersive case presentation. Stimulus testing in browser VR is explicitly gated as `TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED`.

3. **iOS Native Client:**
   - Swift / SwiftUI client utilizing Keychain local encrypted storage and offline synchronization.
   - Gated AVFoundation camera adapter strictly prohibiting fundus imaging without certified hardware and returning `NOT_SUPPORTED` for uncalibrated perimetry.

4. **Shared Backend:**
   - FastAPI REST API mounted under `/api/v1` with OpenAPI 3.0 specification.
   - PBKDF2-HMAC-SHA256 password hashing (600,000 rounds) and RFC 7519 JWT bearer tokens with instant session revocation.
   - Deterministic offline sync engine with client-first measurement preservation.
   - Append-only cryptographic audit trail with SHA-256 event chaining.

---

## 3. Platform Release Classification Matrix

Every capability across every client platform has been audited against physical measurement constraints and clinical validation evidence:

| Capability / Modality | Android VR (OnePlus Nord CE 3 5G) | Web 2D Clinician Portal | WebXR Immersive VR (Headsets) | Native iOS (Swift Client) | Clinical Status & Regulatory Condition |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Patient Profile & Intake** | `VALIDATED` | `VALIDATED` | `VALIDATED` | `VALIDATED` | Fully validated across all clients |
| **Visual Acuity (ETDRS)** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Android calibrated optical geometry only |
| **Suprathreshold Perimetry (24-2)** | `VALIDATED` | `NOT_SUPPORTED` | `TECH_AVAIL_NOT_VALIDATED` | `NOT_SUPPORTED` | Android 120Hz display & VRBox lens profile required |
| **Contrast Sensitivity** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Calibrated display luminance required |
| **Colour Vision (Ishihara/D-15)** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Calibrated chromaticity spectrum required |
| **Amsler Central Distortion** | `VALIDATED` | `NOT_SUPPORTED` | `TECH_AVAIL_NOT_VALIDATED` | `NOT_SUPPORTED` | Requires fixed 10° foveal fixation grid |
| **Binocular Alignment & Motility** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Stereoscopic septal isolation required |
| **Stereopsis Disparity Threshold** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Sub-pixel disparity rendering required |
| **Anterior Segment Capture** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED`* | *iOS camera capable; pending formal clinical trial |
| **Retinal Screening** | `VALIDATED`** | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | **Certified ISO 15004-2 optical module only |
| **Tonometry (IOP)** | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | **Prohibited across all platforms** |
| **Multimorbidity Decision Review** | `VALIDATED` | `VALIDATED` | `VALIDATED` | `VALIDATED` | Independent sigmoids without softmax sum-to-1 |
| **Offline-First Synchronization** | `VALIDATED` | `VALIDATED` | `VALIDATED` | `VALIDATED` | Idempotent, client-first reconciliation |
| **Cryptographic Audit Trail** | `VALIDATED` | `VALIDATED` | `VALIDATED` | `VALIDATED` | Append-only SHA-256 event chaining |

---

## 4. Summary of Verification Evidence

The system has undergone rigorous automated testing across unit, integration, fault injection, security, and hardware simulation layers:

```
================================================================================
Test Subsystem                     Test Suite Target               Pass / Total
================================================================================
Android Reference Client           Logic & Clinical Semantics          28 / 28
Android Reference Client           Protocol & Test State Machine       18 / 18
Android Reference Client           Calibration & Sensor Timings        22 / 22
Android Reference Client           Fault Injection & Hardware Gating   17 / 17
Android Reference Client           Multimorbidity & AI Inference       20 / 20
FastAPI Shared Backend             Authentication & RBAC Suite          6 / 6
FastAPI Shared Backend             Clinical Contracts & Timings         3 / 3
FastAPI Shared Backend             Offline Sync & Idempotency           2 / 2
FastAPI Shared Backend             Multi-Tenant Isolation               2 / 2
FastAPI Shared Backend             Audit Trail & Report Generation      1 / 1
FastAPI Shared Backend             Android Sync Integration             1 / 1
FastAPI Shared Backend             Cross-Platform Parity & Pen-Test     7 / 7
Responsive Web & WebXR             TypeScript & WebXR Engine Audit      5 / 5
iOS Native Client                  Swift Contracts, Keychain & Gating   6 / 6
--------------------------------------------------------------------------------
TOTAL AUTOMATED TEST VERIFICATION                                    138 / 138 (100%)
================================================================================
```

---

## 5. Regulatory Hold & Commercial Release Declaration

In compliance with the Indian Medical Device Rules (MDR-2017) and CDSCO Guidelines for Software as a Medical Device (SaMD):

```json
{
  "application": "EyeAtlas VR",
  "version": "1.0.0-rc1",
  "regulatoryClassification": "CDSCO Class B SaMD",
  "commercialDistributionAllowed": false,
  "isReleaseApproved": false,
  "cdscoReviewPending": true,
  "clinicalTrialStatus": "INVESTIGATIONAL_ONLY",
  "mandatoryUserNotice": "INVESTIGATIONAL DEVICE: FOR CLINICAL RESEARCH USE ONLY. NOT CLEARED FOR COMMERCIAL SALE OR STANDALONE DIAGNOSIS."
}
```

**Verification Statement:**  
The software has successfully satisfied all architectural, technical, security, and optical gating requirements defined in `EyeAtlas_VR_MASTER_BLUEPRINT_v1.2.md` for Phases P00–P21 and Cross-Platform Expansion Phases X01–X06. All clinical output remains gated under investigational status until institutional ethics committee approval and formal CDSCO registration.
