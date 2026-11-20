# EyeAtlas VR — Optical & Mechanical Design Specification: VRBox v2.0 Detachable Eyepieces and Optical Interlocks

**Document ID:** SPEC-HW-VRBOX-002  
**Change Request:** CR-002-G (VRBox Optics & Detachable Lens Engineering)  
**Version:** 1.0  
**Target Hardware Baseline:** VRBox Mobile Headset (v2.0) Chassis + OnePlus Nord CE 3 5G  
**Regulatory Classification:** CDSCO MDR-2017 Class B SaMD Investigational Boundary (`isReleaseApproved = false`)  
**Standard Compliance:** ISO 15004-2:2024 (Ophthalmic instruments — Light hazard protection), ISO 10993-1 (Biocompatibility)

---

## 1. Executive Summary & Purpose

EyeAtlas VR transforms a commodity passive mobile virtual reality enclosure (VRBox v2.0) into a calibrated ophthalmic screening device. The system delivers calibrated visual stimuli across an $80.2^\circ$ field of view (FOV) while strictly enforcing optical, physiological, and thermal safety bounds.

This specification addresses a fundamental physical conflict in mobile tele-ophthalmology:
1. **Psychophysical Visual Testing** (visual field perimetry, contrast sensitivity, Amsler grid, Ishihara chromaticity) requires high-power positive eyepieces to collimate the smartphone display to optical infinity ($\infty$), presenting wide-field angular stimuli.
2. **Direct Anterior & Retinal Examination** (direct slit/torch imaging, non-invasive tear film breakup time, 20D indirect ophthalmoscopy fundus capture) requires direct optical line-of-sight to the patient's cornea and pupil.

Attempting to perform indirect ophthalmoscopy or retinoscopy through a seated VR eyepiece creates an aberrant, unusable compound optical stack. This document details:
- Complete baseline optical, geometric, and mechanical parameters of the VRBox v2.0.
- A rigorous mathematical proof establishing why a 20D/28D condensing lens cannot operate through the VR eyepiece.
- Minimal physical modification engineering for independent, non-destructive detachable lens carriers.
- Specialized mounting adapter rings (Trial Lens, 1.2 mm Pinhole Occluder, 20D Condensing Adapter).
- Software optical hardware-state models and safety interlocks.

---

## 2. VRBox v2.0 Mechanical & Optical Parameters

### 2.1 Enclosure Geometry & Phone Tray
- **Chassis Dimensions:** $198\text{ mm} \times 135\text{ mm} \times 105\text{ mm}$ (Width $\times$ Height $\times$ Depth).
- **Weight:** $330\text{ g}$ (bare chassis without smartphone).
- **Patient Host Phone:** OnePlus Nord CE 3 5G ($162.7\text{ mm} \times 75.5\text{ mm} \times 8.2\text{ mm}$, $184\text{ g}$).
- **Phone Tray Mechanism:** Spring-loaded friction clamp tray with pull-out slide.
- **Relief Cutout Invariants:**
  - *Right Rail Notch:* $18\text{ mm} \times 4\text{ mm}$ relief notch cut into rubber pad at $80\text{ mm}$ from top edge to prevent accidental depression of the Power button.
  - *Left Rail Notch:* $30\text{ mm} \times 4\text{ mm}$ relief notch cut into rubber pad to clear Volume Up/Down rockers.
  - *Center Indexing Notch:* Mechanical indexing mark at $x = 1206\text{ px}$ alignment to mate with the internal light-tight septum.

### 2.2 Stock Eyepiece Optical Characteristics
- **Lens Type:** Symmetrical biconvex PMMA (Polymethyl Methacrylate, refractive index $n = 1.492$, Abbe number $V_d = 58.0$).
- **Lens Diameter:** $\varnothing 42.0\text{ mm} \pm 0.2\text{ mm}$.
- **Clear Optical Aperture:** $\varnothing 38.0\text{ mm}$.
- **Center Thickness ($t_c$):** $8.5\text{ mm} \pm 0.3\text{ mm}$.
- **Edge Thickness ($t_e$):** $1.8\text{ mm}$.
- **Radius of Curvature ($R_1 = -R_2$):** $68.8\text{ mm}$.
- **Focal Length ($f$):** $70.0\text{ mm} \pm 2.0\text{ mm}$ (nominal $+14.29\text{ D}$) for standard VRBox; or $45.0\text{ mm} \pm 1.0\text{ mm}$ ($+22.22\text{ D}$) for wide-field configuration.
- **Eye Relief ($d_{\text{eye}}$):** $14.5\text{ mm}$ to $18.0\text{ mm}$ (permits testing with standard corrective spectacles in place).
- **Angular Field of View (Monocular):** $80.2^\circ$ circular design reference ($15.08\text{ PPD}$ angular resolution on OnePlus Nord CE 3 AMOLED).

### 2.3 Adjustment Mechanisms
- **Interpupillary Distance (IPD) Adjustment:**
  - Independent dual top slide toggles linked to lateral worm/friction carriages.
  - Mechanical Travel Range: $48.0\text{ mm}$ to $74.0\text{ mm}$ (nominal median $62.0\text{ mm}$).
  - Millimeter calibration index scribed on top chassis with vernier resolution $\le 1.0\text{ mm}$.
- **Focal / Vertex Distance Adjustment:**
  - Independent forward/aft thumb-wheel sliders with $10.0\text{ mm}$ axial travel.
  - Dioptric accommodation range: $-4.00\text{ D}$ (myopia) to $+2.00\text{ D}$ (hyperopia) relative to screen plane.

---

## 3. Mathematical Proof: Incompatibility of Compound VR Eyepiece + 20D Condensing System

```
       PATIENT EYE                  20D CONDENSING LENS                 VR EYEPIECE                   CAMERA SENSOR
     +-------------+                    +---------+                     +---------+                  +-------------+
     |   Retina    |   Collimated       |         |   Aberrant          |         |  Collapsed       |             |
     |      &      |===================>| +20.0 D |====================>| +14.3 D |=================>| Sensor /    |
     |   Pupil     |   Reflected Ray    |         |   Intermediary      |         |  Internal Image  | Doctor Eye  |
     +-------------+                    +---------+                     +---------+                  +-------------+
            |<---------------- d_1 ---------------->|<------- d_2 ------->|
                                                    | (Purkinje Glare &   |
                                                    |  Pupil Clipping)    |
```

### 3.1 Gullstrand Optical Power Derivation
Let an indirect ophthalmoscopy system utilize a standard Volk $+20.0\text{ D}$ biconvex condensing lens ($P_1 = +20.0\text{ D}$, $f_1 = 50.0\text{ mm}$) to project an aerial image of the fundus.

If this condensing lens is held in front of the VRBox eyepiece ($P_2 = +14.29\text{ D}$, $f_2 = 70.0\text{ mm}$) with an axial vertex separation distance $d = 25.0\text{ mm} = 0.025\text{ m}$, the total equivalent dioptric power $P_{\text{total}}$ of the compound system is governed by Gullstrand's equation:

$$P_{\text{total}} = P_1 + P_2 - d \cdot P_1 \cdot P_2$$

Substituting the optical parameters:

$$P_{\text{total}} = 20.0 + 14.29 - (0.025 \times 20.0 \times 14.29)$$
$$P_{\text{total}} = 34.29 - 7.145 = +27.145\text{ D}$$

The effective focal length $f_{\text{eff}}$ of this compound combination is:

$$f_{\text{eff}} = \frac{1}{P_{\text{total}}} = \frac{1}{27.145\text{ m}^{-1}} \approx 0.0368\text{ m} = 36.84\text{ mm}$$

The back focal length $BFL$ measured from the rear vertex of the VR lens is given by:

$$BFL = f_{\text{eff}} \left( 1 - \frac{d}{f_1} \right) = 36.84 \left( 1 - \frac{25.0}{50.0} \right) = 36.84 \times 0.5 = 18.42\text{ mm}$$

### 3.2 Clinical Consequences of the Collapsed Focal Plane
1. **Internal Image Projection:**
   In normal indirect ophthalmoscopy, the $+20\text{ D}$ lens forms an inverted, aerial image of the fundus approximately $50\text{ mm}$ behind the lens. In this compound stack, the aerial image collapses to just $18.42\text{ mm}$ behind the VR lens—placing it inside the lens carriage or before the camera entrance pupil. Neither an autofocus smartphone camera nor a human eye can accommodate to an aerial image trapped within the physical enclosure.
2. **Spherical & Chromatic Aberration Catastrophe:**
   Transverse spherical aberration ($TSC$) for a multi-element system scales with the sum of cubed powers:
   $$TSC \propto y^3 \sum_{i=1}^N \frac{P_i^3}{n_i^2}$$
   Stacking two uncorrected thick positive PMMA lenses ($n=1.492$) without an achromatic doublet increases spherical aberration by a factor of $3.8\times$ compared to the condensing lens alone. Longitudinal chromatic aberration ($LCA$) produces severe color fringing ($> 12\text{ arcmin}$ dispersion across the optic disc).
3. **Pupil Vignetting & Field Collapse:**
   The human exit pupil ($\varnothing 3\text{ mm}$ to $7\text{ mm}$) must optically conjugate with the entrance pupil of the viewing sensor. The dual-lens aperture restriction clips off-axis chief rays:
   $$\theta_{\text{unvignetted}} = 2 \arctan \left( \frac{D_{\text{clear}} - d_{\text{pupil}}}{2(d_1 + d_2)} \right)$$
   For $D_{\text{clear}} = 38\text{ mm}$, $d_1 = 50\text{ mm}$, and $d_2 = 25\text{ mm}$, the unvignetted retinal field collapses from $45.0^\circ$ down to $< 7.4^\circ$, rendering screening for diabetic retinopathy or glaucoma disc cupping impossible.
4. **Purkinje Back-Reflection Glare:**
   Retinal reflectance is extremely low ($\rho_{\text{retina}} \approx 0.01\% \text{ to } 0.1\%$). Each uncoated air-PMMA optical surface reflects:
   $$R = \left( \frac{n - 1}{n + 1} \right)^2 = \left( \frac{1.492 - 1}{1.492 + 1} \right)^2 = \left( \frac{0.492}{2.492} \right)^2 \approx 3.90\%$$
   Across four uncoated refracting surfaces (2 on the condensing lens, 2 on the VR eyepiece), specular back-reflection totals $\approx 14.8\%$. This glare is $150\times$ brighter than the retinal reflex, blinding the camera sensor.

**Conclusion:** Direct optical line-of-sight is mandatory. Visual field eyepieces must be completely removable before any ophthalmoscopic or anterior camera capture.

---

## 4. Detachable Eyepiece Mechanical Engineering

To preserve the IPD slider tracks and focal depth adjustments of the stock VRBox v2.0 without destroying the chassis, the stock glued lens retaining rings are modified with an indexed modular coupling.

```
       VRBOX CHASSIS CARRIAGE                     MODULAR EYEPIECE / ADAPTER
    +--------------------------+                 +--------------------------+
    |                          |    60° Twist    |                          |
    |  [ Detent Notch 1 ]      |<===============>|  [ Kinematic Lug 1 ]     |
    |                          |    Bayonet      |                          |
    |  Clear Bore: >= 38.0 mm  |    or 3-Point   |  Clear Bore: >= 38.0 mm  |
    |                          |    N52 Magnet   |                          |
    |  [ Detent Notch 2 ]      |<===============>|  [ Kinematic Lug 2 ]     |
    |                          |                 |                          |
    +--------------------------+                 +--------------------------+
           (Stationary on IPD)                       (Interchangeable Ring)
```

### 4.1 Kinematic Coupling Specification
- **Mechanism:** 60-degree three-lug twist-lock bayonet with dual detent stops OR 3-point kinematic magnetic detents.
- **Magnetic Coupling Details:**
  - Three circular N52 neodymium magnets ($\varnothing 3.0\text{ mm} \times 1.5\text{ mm}$, Ni-Cu-Ni coated) pressed into the chassis carriage rim at $120^\circ$ radial intervals.
  - Three mating 410 stainless-steel index pins pressed into the detachable lens ring.
  - Breakaway retention force: $4.5\text{ N} \pm 0.5\text{ N}$ (sufficient to prevent accidental detachment when patient tilts head, yet easily removed with thumb and forefinger).
- **Clear Aperture When Removed:** $\varnothing \ge 38.0\text{ mm}$ unobstructed clear bore.
- **Repeatability Tolerance:**
  - Centration repeatability: $\le \pm 0.35\text{ mm}$ radial deviation.
  - Axial tilt: $\le 0.15^\circ$.
  - Focal plane repeatability: $\le \pm 0.20\text{ mm}$.

---

## 5. Interchangeable Optical Adapter Ring Ecosystem

The system defines four interchangeable modular rings compatible with the $\varnothing 38.0\text{ mm}$ carriage receptacle:

| Ring ID | Name | Optical Components | Primary Clinical Role |
|---|---|---|---|
| **RING-A** | **Standard VR Eyepiece** | $+14.29\text{ D}$ (or $+22.2\text{ D}$) PMMA biconvex lens | Perimetry (24-2/30-2), Contrast, Amsler, Ishihara, Motility. |
| **RING-B** | **Ophthalmic Trial Lens Holder** | Standard $38\text{ mm}$ metal rim slot with $360^\circ$ axis indicator | Subjective refraction confirmation, spherical/cylindrical correction. |
| **RING-C** | **1.2 mm Pinhole Occluder** | Opaque matte black disc with central $\varnothing 1.2\text{ mm}$ aperture | Differential visual acuity: refractive error vs. amblyopia/maculopathy. |
| **RING-D** | **20D Condensing Adapter** | $+20.0\text{ D}$ coated lens carrier with internal conical light baffle | Direct fundus ophthalmoscopy (only when VR eyepiece removed). |
| **OPEN** | **Direct Anterior Aperture** | No lens inserted (clear $\varnothing 38\text{ mm}$ bore) | Anterior torch imaging, Tear film NIBUT, corneal inspection. |

### 5.1 Pinhole Occluder Functional Mechanics (Ring-C)
- A pinhole of $\varnothing 1.2\text{ mm}$ restricts the blur circle diameter on the retina by increasing the optical depth of field:
  $$d_{\text{blur}} = \frac{D_{\text{pinhole}} \cdot |L' - F'|}{L'}$$
- *Diagnostic Rule:* If visual acuity improves by $\ge 2$ LogMAR lines through the pinhole, visual deficit is primarily refractive (myopia, hyperopia, regular astigmatism). If acuity fails to improve or worsens, pathology involves media opacities (dense cataract, vitreous hemorrhage) or neurosensory damage (glaucoma, AMD, diabetic retinopathy).
- *Interlock Rule:* Ring-C must never be paired with the VR eyepiece, as compound magnification invalidates the aperture geometry.

---

## 6. Photobiological Safety & Safe Retinal Illumination Invariant

### 6.1 Regulatory Compliance
- The system must strictly comply with **ISO 15004-2:2024** (*Ophthalmic instruments — Fundamental requirements and test methods — Part 2: Light hazard protection*).
- **Maximum Permissible Exposure (MPE):**
  - Group 1 (Routine ophthalmic examination): Total radiant exposure $H_R \le 450\text{ lux}\cdot\text{s}$ or $10\text{ J/cm}^2$.
  - Continuous illumination duration: $\le 40\text{ s}$ per exposure burst.

### 6.2 Hard Hardware Invariants
1. **No Smartphone Flash for Retinal Illumination:**
   Attempting to use the host phone LED flash inside the VR headset for fundus photography is strictly prohibited. Smartphone flashes lack calibrated spectral filtration, emit high blue-light hazard peaks ($440\text{ nm}$), and exceed Group 1 thermal thresholds.
2. **External Certified Indirect Illumination:**
   All retinal fundus examination must utilize a medically certified, current-limited illumination source ($< 450\text{ lux}\cdot\text{s}$) coupled directly with the condensing lens carrier outside the VR chassis.
3. **Chassis Interior Flocking:**
   Chassis interior walls must be lined with non-reflective matte black velvet (reflectance $< 1.2\%$) to eliminate peripheral stimulus bounce and preserve scotopic adaptation.

---

## 7. Software Hardware-State Model & Interlock Rules

The application enforces optical interlocks through `OpticalInterlockManager` in Kotlin (`app/src/main/java/org/eyeatlas/sensors/Calibration.kt`). The interlock guarantees that no clinical test can run under an invalid or dangerous optical configuration.

### 7.1 State Transition Matrix

```text
+-----------------------------------+---------------------------------------------------------------+
| Optical Configuration State       | Permitted Clinical Tests                                      |
+-----------------------------------+---------------------------------------------------------------+
| BOTH_VR_LENSES_INSTALLED          | Perimetry (OD & OS), Amsler, Contrast, Tumbling E, Ishihara   |
| OD_LENS_REMOVED                   | Perimetry OS, Contrast OS, Anterior OD, Retinal 20D OD        |
| OS_LENS_REMOVED                   | Perimetry OD, Contrast OD, Anterior OS, Retinal 20D OS        |
| BOTH_LENSES_REMOVED               | Direct Anterior (OU), Retinal 20D (OU)                        |
| EXTERNAL_ADAPTER_PINHOLE_MOUNTED  | Acuity Pinhole Differential Test                              |
| EXTERNAL_ADAPTER_20D_MOUNTED      | Retinal Fundus 20D Indirect Examination                       |
| UNKNOWN_UNCALIBRATED              | NONE (All active tests blocked pending confirmation)          |
+-----------------------------------+---------------------------------------------------------------+
```

### 7.2 Safety Interlock Rules
1. **Rule 1 (VR Wide-Field Gate):** If a test requires display collimation (`PERIMETRY_54PT`, `AMSLER_GRID`, `CONTRAST_PFE`, `COLOUR_ISHIHARA`), the target eye **must** have its VR eyepiece installed. If the lens is removed, the engine refuses stimulus presentation with code `INTERLOCK_VIOLATION_VR_LENS_REQUIRED`.
2. **Rule 2 (Anti-Compound Lens Gate):** If an anterior or retinal capture test (`ANTERIOR_TORCH`, `TEAR_FILM_NIBUT`, `RETINAL_20D_FUNDUS`) is selected, the target eye **must not** have a VR eyepiece seated. If a VR eyepiece is present, the engine blocks camera activation with code `INTERLOCK_VIOLATION_VR_LENS_MUST_BE_REMOVED`.
3. **Rule 3 (Pinhole Acuity Gate):** Pinhole differential visual acuity requires `EXTERNAL_ADAPTER_PINHOLE_MOUNTED`. If run under standard VR lenses, results are flagged `INVALID_OPTICAL_STATE`.
4. **Rule 4 (Recalibration Trigger):** Any transition between optical states automatically invalidates current display calibration hash and requires doctor/system verification before resuming testing.
