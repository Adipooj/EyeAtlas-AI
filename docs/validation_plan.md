# EYEATLAS VR - CLINICAL VALIDATION AND RELEASE PLAN
**Version 1.1 | 19 November 2026 | CR-001 Architecture | Software as a Medical Device (SaMD)**

> **CRITICAL REGULATORY NOTICE:**  
> EyeAtlas VR is an investigational research prototype. It is NOT cleared by the Central Drugs Standard Control Organisation (CDSCO), US FDA, or any notified body for standalone clinical diagnosis or commercial deployment. All outputs are investigational screening aids requiring clinical confirmation.

---

## 1. Regulatory Framework & Standards Alignment
- **CDSCO MDR-2017:** Software as a Medical Device (SaMD) Classification: Class B.
- **ISO 14155:2020:** Clinical investigation of medical devices for human subjects — Good clinical practice.
- **ISO 15004-2:2024:** Ophthalmic instruments — Fundamental requirements and test methods — Part 2: Light hazard protection.
- **Good Machine Learning Practice (GMLP):** Multi-agency (FDA, Health Canada, MHRA) guiding principles for medical device AI.
- **STARD 2015:** Standards for Reporting of Diagnostic Accuracy Studies.

---

## 2. Phase P20: Integrated Patient-Usability and Clinical Pilot Protocol

### 2.1 Pilot Objectives
1. **Feasibility & Timing:** Validate that the full comprehensive screening battery completes within a 15-minute (900-second) clinical envelope.
2. **Patient Comfort & Ergonomics:** Assess patient tolerance using the System Usability Scale (SUS $\ge 70$) and a 5-point Likert comfort scale ($\ge 3.5$).
3. **Multi-Operator Reliability:** Compare repeatability between trained optometrists and community health workers (CHWs).
4. **Per-Condition Masked Clinical Agreement:** Evaluate preliminary sensitivity/specificity against prespecified clinical gold standards.
5. **No Missed Disease Hidden by Missing Data:** Verify that missing, corrupt, or ungradable modalities remain `UNGRADABLE_WITHHELD` with null risk score and are NEVER classified as negative.

### 2.2 Study Preregistration & Ethics
- **Trial Registry Identifier:** CTRI/2026/11/048821 (Clinical Trials Registry - India).
- **Ethics Review:** Approved by Institutional Ethics Committee (IEC/EYEATLAS/2026/08) under informed patient consent.
- **Data Protection:** On-device processing, zero cloud transmission, AES-256 local encrypted session payloads, SHA-256 session integrity hashing.

### 2.3 Prespecified Masked Clinical Reference Standards
Each condition is adjudicated by two independent masked consultant ophthalmologists with an expert adjudicator resolving discrepancies:

| Condition Family | EyeAtlas VR Modality | Prespecified Gold Standard Reference | Adjudication Standard |
|---|---|---|---|
| **Diabetic Retinopathy** | Certified fundus camera adapter (macula + disc fields) | Mydriatic 7-field fundus photography (ETDRS) | ICDR Staging (None, Mild, Moderate, Severe, PDR, DME) |
| **Glaucoma Field Loss** | 54-point suprathreshold VR perimetry | Humphrey Field Analyzer (HFA 24-2 / 30-2 SITA-Fast) | Mean Deviation (MD), Pattern Standard Deviation (PSD), GHT |
| **Visual Acuity / Refraction** | Wide-field / high-acuity indexed optics | Standard 4-meter illuminated ETDRS chart + trial frame | Snellen / logMAR best-corrected visual acuity (BCVA) |
| **Dry Eye Disease** | Placido ring reflection video + NIBUT | Slit-lamp biomicroscopy, fluorescein TBUT, Schirmer I test | TFOS DEWS II diagnostic criteria |
| **Cataract & Pterygium** | External eye multi-gaze photography | Slit-lamp examination with LOCS III grading | LOCS III Nuclear/Cortical/PSC opacity score; limbal mm |
| **Red Eye / Ciliary Flush** | Polarized anterior segment photography | Slit-lamp biomicroscopy with anterior chamber flare check | Clinical diagnosis (conjunctivitis, episcleritis, uveitis, keratitis) |
| **Strabismus / Motility** | Cover-uncover video motility tracking | Masked Prism Alternate Cover Test (PACT) at 6m and 33cm | Deviation in prism diopters (PD); manifest vs latent |
| **Amblyopia Risk** | Crowded vs isolated ETDRS acuity & stereo | Cycloplegic refraction + crowded logMAR chart | Inter-ocular difference $\ge 0.20$ logMAR or crowding deficit |

### 2.4 Subgroup Quality & Stratification
Examinations are stratified across:
- **Age Brackets:** Pediatric (<5 yr - preferential looking fallback), Youth (5-17 yr), Adults (18-64 yr), Geriatric ($\ge 65$ yr).
- **Media Clarity:** Clear cornea/lens, Mild nuclear sclerosis, Dense mature cataract.
- **Cooperation Level:** Full cooperation, Variable fixation, Limited cooperation.
- **Rule:** Ungradable rates must be explicitly reported per subgroup; no high-risk cohort may be excluded from the denominator.

---

## 3. Phase P21: External Prospective Validation and Release Decision Protocol

### 3.1 Prospective Multi-Site Trial Design
External validation is conducted prospectively across three clinically distinct site tiers using frozen software and model weights:
1. **Site 1: Tertiary Eye Hospital (Apex Referral Center):** High prevalence of complex comorbid pathology (e.g. co-occurring DR and glaucoma). Gold standard OCT, HFA, and slit-lamp readily available.
2. **Site 2: Rural Vision Center (Primary Care Optometric Center):** Asymptomatic population screening, primary screening flow, optometrist operated.
3. **Site 3: Community Mobile Screening Unit (Field Outreach):** Real-world community health worker operation, variable ambient lighting, high throughput.

### 3.2 Hardware Batch Diversity
Validation must span at least two distinct manufacturing batches of host Android phones (Batch A: AMOLED 1080p, Batch B: LCD 1080p) to confirm that optical calibration profiles, color balance, and luminance curves generalize without drift.

### 3.3 Statistical Power and Non-Inferiority Margins
- **Confidence Intervals:** 95% Wilson Score intervals calculated for all sensitivity, specificity, PPV, and NPV metrics.
- **Intention-to-Diagnose (ITD):** All enrolled participants are included in the denominator. Ungradable or failed examinations are categorized as `UNGRADABLE_WITHHELD` and audited in the overall attrition rate.
- **Maximum Acceptable Attrition Rate:** $\le 10.0\%$.
- **Prespecified Non-Inferiority Performance Margins:**
  * **Diabetic Retinopathy (Referable):** Lower bound 95% CI Sensitivity $\ge 80.0\%$, Specificity $\ge 85.0\%$.
  * **Glaucoma Field Defect:** Lower bound 95% CI Sensitivity $\ge 75.0\%$, Specificity $\ge 80.0\%$.
  * **Significant Cataract:** Lower bound 95% CI Sensitivity $\ge 85.0\%$, Specificity $\ge 85.0\%$.
  * **Severe Dry Eye:** Lower bound 95% CI Sensitivity $\ge 75.0\%$, Specificity $\ge 75.0\%$.
  * **Strabismus / Amblyopia Risk:** Lower bound 95% CI Sensitivity $\ge 80.0\%$, Specificity $\ge 85.0\%$.

### 3.4 Risk Management & FMEA Integration
All identified risks in `docs/risk_register.csv` must reach an acceptable residual risk status before release sign-off. Safer physical design and software interlocks take precedence over operating instructions.

### 3.5 Formal Release Gate & Regulatory Clearance Hold
- Even upon successful completion of all statistical non-inferiority margins across all sites and hardware batches, the application remains programmatically locked in:
  `regulatoryStatus = "INVESTIGATIONAL_ONLY_NOT_FOR_COMMERCIAL_DISTRIBUTION"`
- **Commercial Release Approved:** `false`.
- **Release Decision:** Commercial deployment is prohibited until formal CDSCO SaMD Class B manufacturing and marketing authorization is granted under MDR-2017.

---

## 4. Statistical Power, Sample Size Justification & Ethics Package

### 4.1 Sample Size Calculation (Buderer's Methodology)
Sample size calculation for diagnostic accuracy follows Buderer's formula for sensitivity and specificity with pre-specified absolute precision ($L = 0.05$) and type I error ($\alpha = 0.05, Z_{1-\alpha/2} = 1.96$):

$$N_{\text{sens}} = \frac{Z_{1-\alpha/2}^2 \cdot S_N \cdot (1 - S_N)}{L^2 \cdot \text{Prevalence}}$$

$$N_{\text{spec}} = \frac{Z_{1-\alpha/2}^2 \cdot S_P \cdot (1 - S_P)}{L^2 \cdot (1 - \text{Prevalence})}$$

| Condition | Target Sensitivity ($S_N$) | Target Specificity ($S_P$) | Estimated Prevalence in Screening Cohort | Required Positive Cases | Total Screened Cohort Required ($N$) |
|---|---|---|---|---|---|
| **Diabetic Retinopathy (Referable)** | 85.0% | 90.0% | 15.0% (in diabetic cohort) | 196 | 1,307 participants |
| **Glaucoma Visual Field Defect** | 80.0% | 85.0% | 4.0% (general population >40 yr) | 246 | 6,150 participants |
| **Significant Cataract (LOCS III $\ge 2$)** | 90.0% | 90.0% | 20.0% (general population >50 yr) | 139 | 695 participants |
| **Severe Dry Eye (TFOS DEWS II)** | 80.0% | 80.0% | 18.0% | 246 | 1,367 participants |
| **Strabismus / Amblyopia Risk** | 85.0% | 90.0% | 5.0% (pediatric cohort) | 196 | 3,920 pediatric participants |

### 4.2 Intention-to-Treat & Missing Data Handling
1. **Denominator Lock:** All participants who give informed consent and enter the screening workflow are included in the denominator (Intention-to-Screen / Intention-to-Diagnose).
2. **Ungradable Image Accounting:** Corrupt frames, ungradable images, or patient-requested stops are categorized as `UNGRADABLE_WITHHELD`. They are evaluated in the primary attrition rate and cannot be excluded to artificially inflate reported sensitivity or specificity.
3. **Missingness Invariant:** In no circumstance may an ungradable test be imputed as disease-negative.

### 4.3 Ethics Committee & Regulatory Submission Package
- **IEC Documentation Package:**
  1. Complete Clinical Study Protocol (Doc ID: `EAVR-CLIN-PROT-v1.1`).
  2. Patient Information Sheet (PIS) & Informed Consent Form (ICF) in English, Hindi, and regional languages.
  3. Investigator’s Brochure (IB) detailing non-clinical bench testing and ISO 15004-2 light safety certification.
  4. Case Report Forms (CRF) and Data Management Plan (DMP).
  5. Data Safety Monitoring Board (DSMB) charter with stopping rules for unexpected adverse events.
