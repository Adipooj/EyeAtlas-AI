# EYEATLAS VR — SUPPORTED DEVICE AND PLATFORM MATRIX
**Document Version:** 1.0.0  
**Governing Specification:** `EyeAtlas_VR_MASTER_BLUEPRINT_v1.2.md` | CR-002  
**Regulatory Notice:** Investigational Medical Software. Not cleared by CDSCO/FDA for standalone clinical diagnosis (Class B SaMD Hold).

---

## 1. Executive Summary

EyeAtlas VR operates across three client platforms (Android, Web, iOS) supported by a unified, versioned FastAPI backend. Because ophthalmic measurements depend on precise optical geometry, photic safety limits, display luminance, refresh rates, and input latency, **clinical validation is platform- and hardware-specific**.

The **native Android application on the OnePlus Nord CE 3 5G** serves as the sole clinical reference standard for visual perimetry and optical capture. All other platforms and devices are strictly capability-gated according to their validated boundaries.

---

## 2. Platform & Hardware Classification Matrix

| Platform | Target Hardware / Environment | Operating System | Optical / Sensor Profile | Clinical Release Classification | Supported Modalities |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Android (Reference Client)** | **OnePlus Nord CE 3 5G** (Snapdragon 782G, 120Hz Fluid AMOLED) | OxygenOS 13 / 14 (Android 13 / 14) | VRBox Mobile Headset (34mm biconvex, focal length 45mm, eye relief 15mm, calibrated IPD 62mm) | **VALIDATED** *(Research Prototype)* | Suprathreshold 24-2 perimetry (54-point), Anterior external capture, Retinal screening (ISO 15004-2 optical module), Binocular motility/stereopsis, Response Pad pairing |
| **Android (Secondary Phone)** | Any Android 10+ smartphone | Android 10, 11, 12, 13, 14 | Touchscreen response surface | **VALIDATED** *(Response Pad Only)* | Low-latency response pad, Clock synchronization (<50ms jitter), Clinician supervisor monitor |
| **Web (Clinician Portal)** | Desktop / Laptop / Tablet | Windows 10/11, macOS 12+, iPadOS 16+, Linux | High-DPI monitor, standard keyboard/mouse/touch | **VALIDATED** *(Record Review Only)* | Patient intake, Medical history, Per-eye multimorbidity candidate review, PDF report review & signing, Audit inspection |
| **WebXR (Immersive VR)** | Meta Quest 2 / 3 / Pro, Vive Focus 3, Pico 4 | Meta Quest OS / Horizon OS / Android XR | Dual pancake/Fresnel lenses, 6DOF spatial controllers | **TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED** | 1.8m spatial multimorbidity dashboard, 3D raycast interaction; Perimetric stimulus testing is **strictly experimental** |
| **iOS (Native Client)** | iPhone 13, 14, 15, 16 series | iOS 16.0+ | AVFoundation camera, Secure Enclave / Keychain | **TECHNICALLY_AVAILABLE_NOT_CLINICALLY_VALIDATED** | Clinician review dashboard, Local encrypted storage, Offline sync; Retinal flash **PROHIBITED**; Perimetry **NOT_SUPPORTED** |

---

## 3. Modality-by-Modality Platform Support Breakdown

| Clinical Modality | Android (Nord CE 3) | Web 2D Portal | WebXR Immersive VR | Native iOS | Governing Standard / Rationale |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Visual Acuity (ETDRS)** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Requires calibrated 4m / 1m viewing distance and optical angle |
| **Visual Field Perimetry (24-2)** | `VALIDATED` | `NOT_SUPPORTED` | `TECH_AVAIL_NOT_VALIDATED` | `NOT_SUPPORTED` | Requires locked 120Hz display, 200ms Goldmann III stimulus, measured lens distortion |
| **Contrast Sensitivity (Pelli-Robson)** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Requires calibrated display luminance (85–120 cd/m²) |
| **Colour Vision (Ishihara / D-15)** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Requires calibrated D65 chromaticity spectrum |
| **Amsler Central Distortion** | `VALIDATED` | `NOT_SUPPORTED` | `TECH_AVAIL_NOT_VALIDATED` | `NOT_SUPPORTED` | Requires fixed 10° foveal fixation grid |
| **Binocular Alignment & Motility** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Requires dual-eye stereoscopic septal isolation |
| **Stereopsis Disparity Threshold** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | Requires calibrated sub-pixel disparity rendering |
| **Anterior External Imaging** | `VALIDATED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED`* | *iOS camera physically capable, but clinical pipeline pending validation |
| **Retinal Screening** | `VALIDATED`** | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | **Requires independently certified ISO 15004-2 optical module; smartphone LED flash prohibited |
| **Tonometry (IOP)** | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | **Prohibited across all platforms** (no physical applanation/rebound mechanism) |
| **Patient Record & Intake** | `VALIDATED` | `VALIDATED` | `VALIDATED` | `VALIDATED` | Pure informational data management |
| **Multimorbidity Scoring Review** | `VALIDATED` | `VALIDATED` | `VALIDATED` | `VALIDATED` | Clinical decision support presentation |

---

## 4. Hardware Safety & Clinical Invariants

1. **Photic Retinal Safety (ISO 15004-2:2024):**
   - **PROHIBITION:** The smartphone LED torch/flash must NEVER be activated directly into the human eye for fundus illumination.
   - Fundus imaging requires an optical attachment with an integrated, certified illumination source complying with Group 1 medical instrument optical radiation limits.

2. **Perimetry Calibration Gating:**
   - Visual perimetry requires exact stimulus timing (24 frames @ 120Hz = 200ms ± 8.3ms), Goldmann III angular size (0.43°), and Goldmann background luminance (10 cd/m²).
   - WebXR and iOS devices lacking bench-calibrated display luminance and chromaticity profiles are restricted from generating diagnostic perimetry scores.

3. **Tonometry Prohibition:**
   - Under no circumstances shall EyeAtlas VR estimate, infer, or report Intraocular Pressure (IOP) or corneal biomechanics without an approved physical tonometer.

4. **Independent Multimorbidity Assessment:**
   - All client platforms display Level 2 disease candidates independently using sigmoid marginal probabilities ($P(\text{Disease}_i \mid \mathbf{x}) \in [0, 1]$).
   - Scores never collapse into a single mutually exclusive category via softmax sum-to-1 normalization.

5. **CDSCO Regulatory Classification:**
   - Software is classified under the Indian Medical Device Rules (MDR-2017) as **Class B Software as a Medical Device (SaMD)**.
   - Status: **Investigational Prototype — Gated for Clinical Evaluation**.
   - Standalone diagnostic release is locked (`isReleaseApproved = false`, `cdscoReviewPending = true`).
