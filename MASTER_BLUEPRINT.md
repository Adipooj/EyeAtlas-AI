# EYEATLAS VR - MASTER CONTROL BLUEPRINT
**Controlled architecture revision | Version 1.2 | CR-001 + CR-002 | 20 November 2026**

## M0. Why this document exists
This document is the canonical source of truth for all subsequent Antigravity coding, phone/hardware work, research and clinical validation. It compiles the three separately rendered technical blueprints, every phase and the exact prompt to use before and after each phase. Version 1.2 retains CR-001 (two-phone visual perimetry) and records CR-002 (native-first Android delivery followed by a separately gated shared-backend web/iOS expansion). Android remains the only active client implementation through the existing P00-P21 sequence. Web and iOS work is explicitly deferred until the Android release decision and will reuse shared clinical contracts, APIs and model interfaces without assuming browser/iOS parity for hardware-dependent tests. This is an architecture revision, not evidence of clinical validity. A new chat can resume from these files without rediscovery or changing direction. On error, repair the same phase and refactor canonical files; do not create duplicate systems or silently change requirements. No single prompt can guarantee zero bugs or validated clinical performance.

## M1. Frozen user goals and exclusions
- Portable, cheap and small phone-based cardboard-like headset; two Android phones only (patient VR/stimulus phone + clinician/response phone), with the second phone temporarily serving as the patient-operated response pad during visual-field testing. No separate BLE or other electronic response controller.
- Ten eye condition groups plus red-green colour screening, no tonometry, two-level broad functional findings then ranked **nonexclusive** disease possibilities, further specific diagnostic referrals.
- Per-eye multi-disease output; same symptom may have several causes. AI handles capture quality, adaptive tests, multi-label disease models, correction and referral. Do not collapse to single-label softmax.
- Work strictly phase by phase in Antigravity; first establish entire repo structure; implement one cohesive Kotlin Android app and lean Python ML scripts; verify each gate, update log and code dump. No one-function modules, no redundant patch files. Android-first is deliberate; web/iOS are deferred until the Android release gate.
- Start with software and sim hardware, get app installed on selected Android phones and do real capability probes, then integrate optics, external-eye illumination and fundus module.
- Dataset use free, permissionless *when confirmed*, licensing MIT/Apache/BSD for code and CC BY/CC0 for data preferred; restricted data blocked rather than pretending to have rights.
- High sensitivity/specification ambitions are study **targets**, not achieved claims. A device is not medically cleared just because it uses a familiar test.
- Phone makes/models not yet selected. Exact camera hardware geometry, optics, chassis dimensions, optical safety and price quotations must remain controlled TBD until named phones and supplier measurements; do not manufacture fabricated BOM values.

## M2. Decisions frozen now
1. Software-first in simulation -> Android device profiling -> bench optical integration -> clinical validation.
2. One APK role-based app. The patient phone runs the VR stimulus role; the clinician phone runs setup/review and, during visual-field testing, the patient-operated response-pad role. Two phones communicate over authenticated encrypted local Wi-Fi; records are local-first. No cloud requirement and no separate response peripheral.
3. Wide fixed eyepiece plus narrow high-PPD option only if optical test proves viability; optical insert must not introduce extra uncalibrated errors.
4. The doctor phone captures ocular surface and fundus; the VR patient phone is never used as an improvised fundus camera through the display lenses.
5. External-eye 5V/LED circuit prototypable; retinal illumination ONLY via independently evaluated imaging system.
6. Runtime models frozen; individual tests adapt, AI weights do not self-train on clinical encounters.
7. For each patient: Level 1 broad observations, Level 2 independent disease scores; mandatory risk-based testing not cancelled by benign Level 1.
8. Three handoff files: this master, PROJECT_LOG and CODE_DUMP. Each phase keeps them updated and checks hashes.
9. Visual perimetry uses the patient VR phone to render calibrated stimuli and the paired clinician/response phone as a touch-response instrument. The BLE response controller is removed from the design and BOM.
10. Delivery strategy is native-first: complete and validate the Android application under P00-P21 before starting web/iOS clients.
11. Preserve platform-independent clinical schemas, test definitions, decision semantics, model I/O contracts and versioned API boundaries during Android implementation; do not prematurely migrate the local-first Android product to mandatory cloud services.
12. After P21 passes its release decision, start a new, separately gated cross-platform expansion programme (X01 onward) using a shared backend. The web and iOS clients must not redefine clinical algorithms or silently claim equivalence with Android hardware tests.

## M3. Canonical hierarchy
If a discrepancy appears, use priority: (1) patient safety and physics (cannot override), (2) validated optical and clinical facts, (3) frozen test definitions, (4) master plan, (5) code implementation details. A change requires `CR-###`, cause, impact across all three blueprints, independent audit, and phase-specific acceptance; no stealth new phase or feature. Structural files only added when a genuinely new requirement makes existing grouping unsafe/unmaintainable. Unplanned architecture rewrite forbidden.

**CR-001 — Two-phone visual-perimetry response architecture (approved in this revision):** Replace the dedicated BLE 5-key response controller with a response mode in the existing clinician-phone APK. The patient VR phone remains the sole authority for calibrated stimulus rendering, actual frame-presentation timestamps, trial order and trial validity. The second phone is paired through the same app and becomes a patient-operated touch-response handset for the duration of the visual-field block. A short tap records “seen”; absence of a tap is “no response” only when the response channel, test window and timing checks are all valid. A long-press requests STOP. Other four-direction tasks may use the response handset’s trained touch zones only after input usability is verified. No extra phone, custom controller, BLE peripheral or controller purchase is required. This change affects M1-M3, P02/P08/P09, Appendix A, Appendix B and Appendix C; it does not change the clinical scope, the 54-location suprathreshold concept, the no-Humphrey-equivalence restriction, or the phase count.

## M3.1 CR-001 implementation traceability
The matching hardware, software and hardware-software/validation appendices in this compiled master are revised together. The old `ControllerAndGaze.kt` reservation is renamed `ResponseAndGaze.kt`; the repository tree, starter manifest and phase-0 tree check must use the revised path. P02 owns pairing, response transport and clock synchronization; P08 owns the visual-field stimulus/response protocol and its measurement validation; P09 verifies the two actual phones. No new phase or standalone hardware module is introduced.

## M3.2 CR-002 — Native-first delivery and deferred cross-platform expansion
**Decision (approved): Option A.** Build, install, integrate and validate the Android application first. Do not build the website or iOS app in parallel with P00-P21, and do not replace the Kotlin/Jetpack Compose Android client with Flutter, React Native or a web wrapper. The current Android implementation remains the reference client for the first complete product.

**What must be preserved during Android work:**
- Canonical, versioned clinical data contracts for patient, eye, encounter, session, trial, image, calibration, measurement, finding, disease candidate, referral and report.
- Platform-neutral test protocol definitions, stimulus geometry, decision rules, multi-label semantics, evidence provenance, quality/abstention rules and model input/output schemas.
- A clean separation between UI/platform adapters and clinical/domain logic. Android APIs (CameraX/Camera2, Android Keystore, Room, native touch timestamps, sensors and local Wi-Fi) remain behind Android-specific adapters.
- Explicit service/repository interfaces for patient records, model inference, audit events, report generation and synchronisation. These are boundaries, not a mandate to deploy a cloud service during Android phases.
- API-ready serialization and schema versioning for future synchronisation. Do not add a network dependency to a workflow that must work offline.

**What is deferred:** hosted multi-tenant backend, clinician web portal, patient web portal, iOS app, cross-device cloud synchronisation, browser-based image acquisition, browser visual-field mode, App Store packaging and cross-platform release operations. These are not deliverables of P00-P21 and must not delay Android completion.

**P21 exit condition for expansion:** P21 must pass, the Android release candidate must have a signed/tagged source baseline, unresolved safety/clinical risks must be documented, API/data contracts must be versioned, and a formal go/no-go decision must approve the expansion. Passing P21 does not itself mean regulatory clearance or unrestricted commercial deployment.

**Future cross-platform programme (X01-X06), activated only after P21 approval:**
- **X01 — Shared backend foundation:** define and threat-model a versioned FastAPI service, identity/role model, consent and tenant boundaries, audit events, encrypted transport, key management, backup/restore, data residency and offline-first sync/conflict rules. Keep clinical rules in shared, testable domain contracts; server is not assumed to be the sole source of truth while offline.
- **X02 — Backend and Android integration:** introduce authenticated API synchronisation behind feature flags; preserve existing local-first behaviour, deterministic offline testing and export/recovery. Migrate no data without verified migration, rollback and reconciliation tests.
- **X03 — Responsive web clinician portal:** deliver patient search/records, clinician workflow, review of captured evidence, report viewing/export and administration. Browser camera access is capability-gated; do not promise RAW/manual camera controls or validated fundus acquisition on every browser/device.
- **X04 — iOS client:** implement native iOS interfaces and platform adapters for camera capture, secure storage, sensors, local networking and accessibility. Reuse backend contracts, clinical protocol definitions and validated model artifacts where technically compatible; revalidate model performance and acquisition quality on iPhone hardware.
- **X05 — Cross-platform test parity and security:** contract tests across Android/web/iOS; role/permission tests; sync conflict, offline/online, clock, audit and data export tests; penetration/privacy review; supported-device matrix; accessibility and localisation checks.
- **X06 — Platform-specific clinical validation and release:** validate every measurement on each supported platform/device class. Web/iOS visual perimetry is not presumed equivalent to Android VR. Safari/WebXR or browser API gaps must be handled by a separately validated native iOS VR implementation or the feature must be unavailable on that platform. No clinical claim transfers automatically between platforms.

**Expansion architecture target:** Android, web and iOS are clients of a shared, versioned service layer, but hardware-dependent acquisition remains platform-specific. Clinical test definitions and result semantics remain common. Each client advertises measured capabilities and calibration profiles; unsupported hardware paths return `NOT_SUPPORTED`/`NOT_ASSESSED`, never synthetic results.

**Change-control impact:** CR-002 updates M0-M4, M6-M7 and Appendix B S0/S9/S10. It does not alter the P00-P21 phase count, phase prompts, hardware BOM, CR-001 response-pad protocol, clinical scope or P21 acceptance criteria. Future X phases are a post-P21 programme, not extra active phases in the current Android build.

## M3.3 CR-002 Active Execution Track & Subphase Gate Registry
Change Request **CR-002** (Remote Physician Web Portal, Online APK Pairing, Live Monitoring, MediaPipe Integration, and Zero-Install Mobile Browser Response Pad) is actively executed across 8 sequentially gated subphases:

| Subphase | Scope & Core Deliverables | Status | Gate Verification Evidence |
| :--- | :--- | :---: | :--- |
| **CR002-A** | Baseline Audit, Git State, Genuine vs Simulated Audit, Architectural Conflict Resolution. | **PASSED & ACCEPTED** | Baseline audit accepted; conflicts resolved in architecture docs. |
| **CR002-B** | Session Infrastructure: FastAPI WSS broker, durable `RemoteSessionEntity`, sequence replay, pairing PIN/QR. | **PASSED & ACCEPTED** | Full backend suite: 39/39 passed; snapshot hash `f3ad044d...`. |
| **CR002-C** | APK Integration: Authoritative `RemoteSessionClient.kt`, decoupled multi-channel responses (`INPUT_BT_TACTILE_CLICKER`, `INPUT_WEB_RESPONSE_PAD`, etc.), monotonic timing. | **PASSED & ACCEPTED** | Kotlin unit tests (6/6 passed); snapshot hash `a2837432...`. |
| **CR002-D** | Physician Web UI: Dual-mode Phone 2 Zero-Install Response Pad (`#mobile-response-pad-view`), Twin-Eye Live Stimulus Reconstruction canvas, 8-Module Step-by-Step Guides and Interpretation Tables, CDSCO Class B SaMD screening notices. | **PASSED & ACCEPTED** | Web audit suite: 15/15 passed; backend suite: 39/39 passed; snapshot hash `b112c8bc...`. |
| **CR002-E** | Screen Streaming: MediaProjection-to-WebRTC, foreground service, WebRTC signaling via session broker, STUN/TURN discovery, stream telemetry HUD. | **PASSED & ACCEPTED** | Web audit suite: 20/20 passed; backend suite: 41/41 passed; snapshot hash `e3c5f0b0...`. |
| **CR002-F** | MediaPipe Integration: Face/iris landmark integration, optical occlusion feasibility gate, thermal/CPU benchmarks, diagnostic disable flag. | **PASSED & ACCEPTED** | Web audit suite: 21/21 passed; backend suite: 41/41 passed; snapshot hash `bd3db554...`. |
| **CR002-G** | VRBox Optics & Mechanical Specification: Detachable lens engineering, Gullstrand compound focal collapse derivation, optical state interlocks, physical specification document. | **PASSED & ACCEPTED** | Web suite: 26/26 passed; backend suite: 41/41 passed; snapshot hash `4e65368e...`. |
| **CR002-H** | Final Cross-Device Verification: End-to-end multi-peer integration tests, real browser pairing, full regression suite, final closure. | **PASSED & ACCEPTED** | Full test suite passed (43 backend + 31 web = 74 tests, 100% pass rate). **CR-002 CLOSED & LOCKED.** |

## M4. First-run exact operating instructions
1. Unzip `EyeAtlas_VR_Starter_Repository.zip`; open `eyeatlas-vr` in Antigravity. Reserve the blank planned source files (do not delete them).
2. Paste only `P00-A` implementation prompt from M5. Antigravity runs terminal, produces file changes, full verification output. Do not ask it to finish all phases.
3. Paste `P00-B` audit prompt. Request full hard-gate evidence; if fail, tell it to fix within P00 then rerun the same audit, no phase advancement.
4. Send this chat: three handoff files (`MASTER_BLUEPRINT.md`, `PROJECT_LOG.md`, `CODE_DUMP.md`), plus Antigravity's latest phase gate output and any failing hardware measurements. I audit and supply `P01-A` only after pass.
5. Use a Git commit/tag after each accepted phase; log commit SHA, device profile, datasets, tests, hashes, known risks. No background work assumed.
## M5. Complete fixed phase list and exact prompts

## P00: Repository lock and runnable baseline
**Scope:** Create entire fixed tree, signed manifest, build scripts, basic valid Gradle app, simulator fixtures, installable hello-world APK; initialise log/dump.

**Required gate:** All planned files exist, Gradle assembleDebug + unit baseline, empty missing datasets do not break build, lint and integrity; tree check zero unapproved files.


### P00-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P00 Repository lock and runnable baseline ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Create entire fixed tree, signed manifest, build scripts, basic valid Gradle app, simulator fixtures, installable hello-world APK; initialise log/dump.
ACCEPTANCE: All planned files exist, Gradle assembleDebug + unit baseline, empty missing datasets do not break build, lint and integrity; tree check zero unapproved files.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P00-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P00: Repository lock and runnable baseline. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P00` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: All planned files exist, Gradle assembleDebug + unit baseline, empty missing datasets do not break build, lint and integrity; tree check zero unapproved files. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P01: Data contracts and clinical semantics
**Scope:** Implement patient/eye/session/trial/image schemas, state machine, immutable evidence, emergency and privacy logic; patient/doctor intake UI.

**Required gate:** Model serialization, laterality, dual disease and missing evidence property tests; UI form, urgent bypass and recovery tested.


### P01-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P01 Data contracts and clinical semantics ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement patient/eye/session/trial/image schemas, state machine, immutable evidence, emergency and privacy logic; patient/doctor intake UI.
ACCEPTANCE: Model serialization, laterality, dual disease and missing evidence property tests; UI form, urgent bypass and recovery tested.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P01-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P01: Data contracts and clinical semantics. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P01` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Model serialization, laterality, dual disease and missing evidence property tests; UI form, urgent bypass and recovery tested. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P02: Two-phone encrypted sessions
**Scope:** Implement offline local QR pairing, identity pinning, event sequences, secure Wi-Fi transport, response-phone role and touch-event capture, monotonic timestamps and clock-offset estimation; UX for both phones. No BLE response peripheral.

**Required gate:** Packet reorder/drop, wrong device/session, duplicate response, missing/late touch, false tap, response-phone disconnect, clock-offset uncertainty, airplane-mode interruption/resume and STOP command are injected; no dropped or invalid response is fabricated as a negative visual-field result.


### P02-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P02 Two-phone encrypted sessions ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement offline local QR pairing, identity pinning, event sequences and secure Wi-Fi transport between the patient stimulus phone and the clinician/response phone. Add the in-app RESPONSE_PAD role on the second phone, capture touch events with native monotonic timestamps, estimate cross-phone clock offset and uncertainty, and implement priority STOP plus disconnect watchdogs. Do not add BLE, Bluetooth controller code or a third device.
ACCEPTANCE: Packet reorder/drop, wrong device/session, duplicate/missing/late touch, false taps, multi-touch, response-phone disconnect, clock-sync uncertainty, airplane-mode interruption/resume and STOP are injected. Confirm that transport/input faults produce INVALID/UNGRADABLE trials, never false NO_RESPONSE or false-negative field points.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P02-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P02: Two-phone encrypted sessions. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P02` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: packet reorder/drop, wrong device/session, duplicate/missing/late touch, false taps, multi-touch, response-phone disconnect, clock-sync uncertainty, airplane-mode interruption/resume and STOP; no invalid input may be converted into a negative visual-field response. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P03: Dataset and dependency provenance
**Scope:** Implement licence-gated automatic download manifest with only verified open data, data hashing, patient metadata and provenance; dataset browser/admin UI.

**Required gate:** No authentication bypass, withheld/unlicensed dataset auto skipped, hash/duplicate/patient isolation, every licence attribution checked.


### P03-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P03 Dataset and dependency provenance ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement licence-gated automatic download manifest with only verified open data, data hashing, patient metadata and provenance; dataset browser/admin UI.
ACCEPTANCE: No authentication bypass, withheld/unlicensed dataset auto skipped, hash/duplicate/patient isolation, every licence attribution checked.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P03-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P03: Dataset and dependency provenance. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P03` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: No authentication bypass, withheld/unlicensed dataset auto skipped, hash/duplicate/patient isolation, every licence attribution checked. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P04: Instrument profile and physics simulator
**Scope:** Signed device profiles and simulated calibration, optical modes, deterministic stimulus renderer, one test end-to-end UI; select inference runtime after benchmark.

**Required gate:** Stimulus angular math, optics ID mismatch, eye map, real frame timestamp, fake monitor configs, failure-state propagation all pass.


### P04-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P04 Instrument profile and physics simulator ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Signed device profiles and simulated calibration, optical modes, deterministic stimulus renderer, one test end-to-end UI; select inference runtime after benchmark.
ACCEPTANCE: Stimulus angular math, optics ID mismatch, eye map, real frame timestamp, fake monitor configs, failure-state propagation all pass.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P04-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P04: Instrument profile and physics simulator. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P04` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Stimulus angular math, optics ID mismatch, eye map, real frame timestamp, fake monitor configs, failure-state propagation all pass. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P05: Patient and clinician UX complete skeleton
**Scope:** Build all 14 mapped screens and paths for every condition with research-mode fixtures, state restoration, accessibility and independent coaching-free doctor view.

**Required gate:** Automated navigation to every screen, no dead button, no fake clinical scoring, repeatable crash/rotation/process-death tests.


### P05-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P05 Patient and clinician UX complete skeleton ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Build all 14 mapped screens and paths for every condition with research-mode fixtures, state restoration, accessibility and independent coaching-free doctor view.
ACCEPTANCE: Automated navigation to every screen, no dead button, no fake clinical scoring, repeatable crash/rotation/process-death tests.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P05-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P05: Patient and clinician UX complete skeleton. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P05` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Automated navigation to every screen, no dead button, no fake clinical scoring, repeatable crash/rotation/process-death tests. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P06: Broad acuity and optical focus
**Scope:** Implement adaptive monocular acuity, near-view threshold, chart generator, true measured vergence index, directional response and broad findings.

**Required gate:** Synthetic psychometric convergence, 6/6 impossible resolution gates, accommodation confounding flags, ETDRS test harness and right-left tests.


### P06-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P06 Broad acuity and optical focus ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement adaptive monocular acuity, near-view threshold, chart generator, true measured vergence index, directional response and broad findings.
ACCEPTANCE: Synthetic psychometric convergence, 6/6 impossible resolution gates, accommodation confounding flags, ETDRS test harness and right-left tests.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P06-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P06: Broad acuity and optical focus. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P06` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Synthetic psychometric convergence, 6/6 impossible resolution gates, accommodation confounding flags, ETDRS test harness and right-left tests. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P07: Colour, contrast, central macular
**Scope:** Implement original calibrated colour discrimination, contrast and forced-choice hyperacuity/scotoma screen, active confirmation.

**Required gate:** Display gamma / invalid colour profile gates, alias prevention, repeat-scotoma validation, varied response latency tests.


### P07-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P07 Colour, contrast, central macular ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement original calibrated colour discrimination, contrast and forced-choice hyperacuity/scotoma screen, active confirmation.
ACCEPTANCE: Display gamma / invalid colour profile gates, alias prevention, repeat-scotoma validation, varied response latency tests.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P07-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P07: Colour, contrast, central macular. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P07` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Display gamma / invalid colour profile gates, alias prevention, repeat-scotoma validation, varied response latency tests. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P08: Wide-field and binocular function
**Scope:** Implement the calibrated suprathreshold field map on the patient VR phone, per-eye stimulus sequencing, fixation/reliability checks, and paired response-phone touch capture. Implement stereo/fusion/cover-task representations using the same two-phone app where appropriate. No BLE controller.

**Required gate:** 54-point visible/hidden and catch-trial tests, response-phone touch registration and timing, clock-offset uncertainty, false-positive/false-negative controls, blink/motion handling, disconnect/STOP behavior and abnormal-field repeatability; no quantitative Humphrey equivalence claim.


### P08-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P08 Wide-field and binocular function ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement the patient-phone visual-field stimulus renderer and deterministic per-eye suprathreshold protocol; pair the second phone in RESPONSE_PAD mode and capture one-tap “seen” events, no-response windows, catch trials, response timestamps and STOP. Add fixation/reliability checks and stereo/fusion/cover-task representations without a BLE or external response controller.
ACCEPTANCE: 54-point mapping, angular visibility, stimulus frame timing, response-touch registration, clock-sync uncertainty, false-positive catch trials, false-negative controls, blink/head-motion handling, response-phone disconnect, long-press STOP and abnormal-field repeatability pass. Mark unsupported coordinates or invalid response channels ungradable; make no quantitative Humphrey equivalence claim.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P08-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P08: Wide-field and binocular function. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P08` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: 54-point layout and angular visibility; actual stimulus frame timestamps; response-phone touch registration and timestamp mapping; clock-offset uncertainty; catch-trial false positives; no-tap versus invalid-channel distinction; false-negative controls; blink/head-motion/fixation reliability; disconnect/STOP handling; abnormal-field repeatability; no quantitative Humphrey equivalence claim. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P09: Install software-first on real Android phones
**Scope:** Build signed research APK; install both real phones once named; measure display/camera/thermal capability, pair the same app on both phones and exercise the response-pad role while optical profiles remain simulated pending bench.

**Required gate:** Real 2-phone smoke, no overheating/crash across 30 min simulated use, offline QR/local-Wi-Fi pairing, response-touch latency distribution and cross-phone clock-offset uncertainty recorded, disconnect/STOP watchdog, screenshots every screen and explicit missing-profile status.


### P09-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P09 Install software-first on real Android phones ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Build signed research APK; install both real phones once named; measure display/camera/thermal capability; pair the same APK in PATIENT_VR and CLINICIAN/RESPONSE_PAD roles; benchmark touchscreen event capture, cross-phone clock offset/uncertainty, response delivery and fail-safe stop while optical profiles remain simulated pending bench.
ACCEPTANCE: Real 2-phone smoke, no overheating/crash across 30 min simulated use, offline authenticated pairing, touch-event latency and clock-sync evidence, response-channel disconnect/STOP behavior, screenshots every screen and explicit missing-profile status.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P09-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P09: Install software-first on real Android phones. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P09` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Real 2-phone smoke, no overheating/crash across 30 min simulated use, offline authenticated pairing, response-touch event latency and cross-phone clock-offset uncertainty, response-channel disconnect/STOP watchdog, screenshots every screen, profile missing status explicit. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P10: External-eye image acquisition
**Scope:** Implement CameraX/Camera2 fixed lens/exposure/WB/focus, ROI and pose guidance, raw preserve, multi-gaze capture, infection-triage screens.

**Required gate:** Fault images/lighting/blur, colour balance, scale, side labels and independent quality gate; camera-output hashes stable.


### P10-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P10 External-eye image acquisition ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement CameraX/Camera2 fixed lens/exposure/WB/focus, ROI and pose guidance, raw preserve, multi-gaze capture, infection-triage screens.
ACCEPTANCE: Fault images/lighting/blur, colour balance, scale, side labels and independent quality gate; camera-output hashes stable.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P10-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P10: External-eye image acquisition. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P10` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Fault images/lighting/blur, colour balance, scale, side labels and independent quality gate; camera-output hashes stable. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P11: Dry eye and cataract/pterygium/red-eye algorithms
**Scope:** Add Placido blink-video/NIBUT, anterior segmentation, visible-opacity/limbus and red-eye suspicion with open-set unknown and safe triage.

**Required gate:** Reference datasets where licensed; patient/site splits, no confusion of redness and conjunctivitis; retrain not just overlay.


### P11-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P11 Dry eye and cataract/pterygium/red-eye algorithms ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Add Placido blink-video/NIBUT, anterior segmentation, visible-opacity/limbus and red-eye suspicion with open-set unknown and safe triage.
ACCEPTANCE: Reference datasets where licensed; patient/site splits, no confusion of redness and conjunctivitis; retrain not just overlay.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P11-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P11: Dry eye and cataract/pterygium/red-eye algorithms. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P11` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Reference datasets where licensed; patient/site splits, no confusion of redness and conjunctivitis; retrain not just overlay. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P12: Retina optical interface and acquisition
**Scope:** Add declared fundus adapter interface, attachment/profile interlocks, macula/disc capture, montage alignment and quality/coverage cues.

**Required gate:** Artificial retina first, correct retina-only certified light configuration, missing-field and ungradable tests; no retina photo == no DR-negative.


### P12-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P12 Retina optical interface and acquisition ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Add declared fundus adapter interface, attachment/profile interlocks, macula/disc capture, montage alignment and quality/coverage cues.
ACCEPTANCE: Artificial retina first, correct retina-only certified light configuration, missing-field and ungradable tests; no retina photo == no DR-negative.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P12-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P12: Retina optical interface and acquisition. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P12` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Artificial retina first, correct retina-only certified light configuration, missing-field and ungradable tests; no retina photo == no DR-negative. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P13: Retina disease model development
**Scope:** Use verified CC BY data for multi-label DR/AMD/optic disc/other retinal anomaly and independent lesion localization, calibration withheld until target population study.

**Required gate:** Patient/site holdout, source dedup, label leakage, no unapproved datasets, retinal size/coverage, inference on phone benchmark; no numerical fake p.


### P13-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P13 Retina disease model development ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Use verified CC BY data for multi-label DR/AMD/optic disc/other retinal anomaly and independent lesion localization, calibration withheld until target population study.
ACCEPTANCE: Patient/site holdout, source dedup, label leakage, no unapproved datasets, retinal size/coverage, inference on phone benchmark; no numerical fake p.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P13-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P13: Retina disease model development. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P13` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Patient/site holdout, source dedup, label leakage, no unapproved datasets, retinal size/coverage, inference on phone benchmark; no numerical fake p. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P14: Strabismus and amblyopia risk
**Scope:** Implement repeat binocular alignment, external cover video, crowded acuity and age-specific response logic; objective photorefraction remains disabled until separately optically validated.

**Required gate:** Intermittent deviation simulations, crowded-vs-isolated discrepancy, eye correction case, children uncooperative alternative pathway.


### P14-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P14 Strabismus and amblyopia risk ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement repeat binocular alignment, external cover video, crowded acuity and age-specific response logic; objective photorefraction remains disabled until separately optically validated.
ACCEPTANCE: Intermittent deviation simulations, crowded-vs-isolated discrepancy, eye correction case, children uncooperative alternative pathway.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P14-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P14: Strabismus and amblyopia risk. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P14` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Intermittent deviation simulations, crowded-vs-isolated discrepancy, eye correction case, children uncooperative alternative pathway. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P15: Multi-label AI and probability calibration
**Scope:** Implement separate per-eye sigmoid diseases, calibrated probabilities only after valid calibration dataset, fusion without correlated double count, differential/risk and urgency rank independent.

**Required gate:** Double/triple simultaneous disease, false exclusivity, different disease each eye, prior-shift, interval and coexisting evidence, N/A missing modalities.


### P15-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P15 Multi-label AI and probability calibration ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement separate per-eye sigmoid diseases, calibrated probabilities only after valid calibration dataset, fusion without correlated double count, differential/risk and urgency rank independent.
ACCEPTANCE: Double/triple simultaneous disease, false exclusivity, different disease each eye, prior-shift, interval and coexisting evidence, N/A missing modalities.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P15-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P15: Multi-label AI and probability calibration. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P15` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Double/triple simultaneous disease, false exclusivity, different disease each eye, prior-shift, interval and coexisting evidence, N/A missing modalities. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P16: Adaptive test selection and guided recovery
**Scope:** Implement expected information gain under mandatory clinical actions, causal fault recovery, finite repeat and alternatives, symptom safety precedence.

**Required gate:** Synthetic cases normal acuity+diabetes, cataract+DR ungradable, red eye severe pain, multiple disease, retake bias, target time benchmarking.


### P16-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P16 Adaptive test selection and guided recovery ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Implement expected information gain under mandatory clinical actions, causal fault recovery, finite repeat and alternatives, symptom safety precedence.
ACCEPTANCE: Synthetic cases normal acuity+diabetes, cataract+DR ungradable, red eye severe pain, multiple disease, retake bias, target time benchmarking.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P16-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P16: Adaptive test selection and guided recovery. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P16` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Synthetic cases normal acuity+diabetes, cataract+DR ungradable, red eye severe pain, multiple disease, retake bias, target time benchmarking. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P17: Optical/mechanical bench integration
**Scope:** After selecting phone, integrate real optical profile, wide field / narrow acuity insert, IPD, lens index, optical QA and camera geometry.

**Required gate:** Measure real angular map/MTF/luminance/focus/temporal and colour; disable unsupported test range; no unverified 6/6.


### P17-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P17 Optical/mechanical bench integration ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: After selecting phone, integrate real optical profile, wide field / narrow acuity insert, IPD, lens index, optical QA and camera geometry.
ACCEPTANCE: Measure real angular map/MTF/luminance/focus/temporal and colour; disable unsupported test range; no unverified 6/6.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P17-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P17: Optical/mechanical bench integration. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P17` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Measure real angular map/MTF/luminance/focus/temporal and colour; disable unsupported test range; no unverified 6/6. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P18: External lighting and tear optics hardware
**Scope:** Assemble ring 5V circuit per hardware spec, bench currents and thermal, mount hood/Placido target; update software calibration on actual module.

**Required gate:** Electrical/safety test records, image repeatability, 5V 330R branches confirmed and documented, retake success and glare controls.


### P18-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P18 External lighting and tear optics hardware ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Assemble ring 5V circuit per hardware spec, bench currents and thermal, mount hood/Placido target; update software calibration on actual module.
ACCEPTANCE: Electrical/safety test records, image repeatability, 5V 330R branches confirmed and documented, retake success and glare controls.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P18-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P18: External lighting and tear optics hardware. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P18` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Electrical/safety test records, image repeatability, 5V 330R branches confirmed and documented, retake success and glare controls. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P19: Certified retinal hardware interface
**Scope:** Mount medically evaluated/safe fundus module on clinician phone, image phantom, optics and illumination safety evidence, link capture pipeline.

**Required gate:** Retinal light safety document and no improvised flash, disc/macula coverage, minimum lesion detectability, camera/adapter locked.


### P19-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P19 Certified retinal hardware interface ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Mount medically evaluated/safe fundus module on clinician phone, image phantom, optics and illumination safety evidence, link capture pipeline.
ACCEPTANCE: Retinal light safety document and no improvised flash, disc/macula coverage, minimum lesion detectability, camera/adapter locked.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P19-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P19: Certified retinal hardware interface. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P19` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Retinal light safety document and no improvised flash, disc/macula coverage, minimum lesion detectability, camera/adapter locked. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P20: Integrated patient-usability and clinical pilot
**Scope:** Ethics-approved feasibility, time, comfort, operators, per-condition masked clinical references, complete full/abbreviated protocol.

**Required gate:** No missed disease hidden by missing data, subgroup quality, study preregistration, safety and CI reports, no deployment claim.


### P20-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P20 Integrated patient-usability and clinical pilot ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Ethics-approved feasibility, time, comfort, operators, per-condition masked clinical references, complete full/abbreviated protocol.
ACCEPTANCE: No missed disease hidden by missing data, subgroup quality, study preregistration, safety and CI reports, no deployment claim.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P20-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P20: Integrated patient-usability and clinical pilot. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P20` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: No missed disease hidden by missing data, subgroup quality, study preregistration, safety and CI reports, no deployment claim. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## P21: External validation and release decision
**Scope:** Prospective multi-site, multi-phone-batch validation against prespecified reference tests; lock final AI calibration, risk file and intended-use labels.

**Required gate:** Power/noninferiority/superiority analysis, per-condition metrics/CIs, all attrition, residual risk, CDSCO regulatory review before commercial use.


### P21-A - Antigravity implementation prompt (paste verbatim)

```text
FOLLOW MASTER_BLUEPRINT.md strictly, including hardware + software + integration contracts. THIS REQUEST IS FOR P21 External validation and release decision ONLY. Never work ahead, improvise new features, rename planned folders, create extra helper files or add one-function files. Use the existing planned canonical files. If a proposed change is unavoidable, first explain CR-ID and wait for explicit approval. The device has no tonometer. Preserve independent per-eye multi-morbidity and no-pretend-diagnosis rules. Keep all user interfaces wired and usable. No medical performance invented. Every dataset or dependency is licence audited and version pinned; no CAPTCHA/login bypass. Commit only after gates. Update PROJECT_LOG.md and CODE_DUMP.md with real observed changes and tests, not guessed success.
TASK: Prospective multi-site, multi-phone-batch validation against prespecified reference tests; lock final AI calibration, risk file and intended-use labels.
ACCEPTANCE: Power/noninferiority/superiority analysis, per-condition metrics/CIs, all attrition, residual risk, CDSCO regulatory review before commercial use.
DELIVERABLES: (1) precise list of files edited; (2) functioning patient and clinician UI in the current feature; (3) tests added in the allocated existing test files; (4) executed commands and exit codes; (5) dependency/data licence check; (6) regression test results; (7) updated code dump and project log; (8) current exact git diff/status; (9) explicit blockers and residual risks. If machine/device is not available, perform mock/simulator gate honestly and leave real-device acceptance pending. Do not claim phase passed unless every required gate genuinely passed. Fix failures by editing existing canonical files and rerun all affected tests. Do not implement the next phase.
```

### P21-B - Antigravity independent audit prompt (paste verbatim)

```text
READ-ONLY ADVERSARIAL AUDIT FOR P21: External validation and release decision. Read MASTER_BLUEPRINT.md, PROJECT_LOG.md, CODE_DUMP.md and the actual source/config/test files. Inspect all git diffs from the previous accepted tag. Independently run `scripts/verify.sh --phase P21` plus relevant actual compilation/instrumented tests; do not trust previous summaries. Falsify the following gate: Power/noninferiority/superiority analysis, per-condition metrics/CIs, all attrition, residual risk, CDSCO regulatory review before commercial use. Check licensing/provenance, patient-level data leakage, laterality, multi-label comorbidity, temporal event causality, physical measurement validity, failure recovery, both phone UI paths, no fake clinical claims, all file checksums and repo cleanliness. Return a PASS/FAIL matrix with command, evidence/path, stdout summary, exit code, unresolved risks, and exact remediation in existing files. In audit mode DO NOT change source; if failure, request a correction pass **within this same phase** and then rerun audit. Do not advance to another phase. Finally verify log and code dump match the actual repository.
```

## M6. Exact new-chat handoff instruction
Paste this text in a new chat together with the THREE files: `MASTER_BLUEPRINT.md`, `PROJECT_LOG.md`, and `CODE_DUMP.md`, plus the latest Antigravity gate output and any physical instrument data. This revision is v1.2 / CR-002; do not start X01-X06 until P21 has passed and expansion is explicitly approved:

> Continue EyeAtlas VR from these authoritative files. Read MASTER_BLUEPRINT, PROJECT_LOG and CODE_DUMP in full. Compare actual state, last accepted phase, unresolved errors, phone profile, dataset approvals and verification evidence. Do not redesign the fixed architecture or skip a phase. Audit the last phase's tests/diffs and tell me only (a) exact concerns/corrections needed in the current phase or (b) the next approved Pxx-A Antigravity prompt. Preserve two-level broad vs multi-label disease-specific output, per-eye causality, hardware safety, lean file structure and verifiable clinical claims. Follow CR-002: finish P00-P21 Android-first; preserve versioned platform-neutral contracts and clean adapter boundaries; defer all web/iOS/backend expansion X01-X06 until P21 release approval. Verify that the patient phone rendered each visual-field stimulus and the paired response phone recorded every touch/no-response/STOP event with valid clock-sync evidence; distinguish invalid channels from genuine no-response trials. Never trust a claimed PASS without logs and deterministic checks.

## M7. Completion states and what this initial package DOES and DOES NOT include
**Included now:** three research blueprints, a compiled master, 22 prescribed phase-A and 22 audit-B prompts, dataset provenance starters, external-eye hardware electronics illustrative schematic, two-phone visual-field stimulus/response architecture, entire planned repository hierarchy of placeholder code paths, initial handoff log and code dump, and the CR-002 native-first / deferred cross-platform architecture. No standalone response controller is part of the BOM.
**Not included now:** completed mobile application, hosted shared backend, website, iOS application, trained or externally validated models, confirmed automatically downloadable data for every condition, physically verified phone/lens dimensions, a purchased safe fundus module, measured hardware performance, human-study approval or evidence that a single sensitivity/specificity target beats any universal government standard. Android implementation and validation depend on P00-P21; web/iOS/backend depend on post-P21 X01-X06.

## M8. Appendices: authoritative three technical blueprints follow


---

# APPENDIX A: HARDWARE BLUEPRINT

# EYEATLAS VR - HARDWARE AND ELECTRONICS BLUEPRINT
**Version 1.2 | 20 November 2026 | CR-001 + CR-002 | Research prototype; not a cleared medical device**

## H0. Fixed mission, boundary, and governing architecture
Two Android phones only: PATIENT handset inside a low-cost blacked-out VR chassis; CLINICIAN handset acts as UI, external-eye camera and fundus camera host, and temporarily becomes the patient-operated RESPONSE handset during visual-field testing. The patient handset renders the visual-field analyser simulation; the response handset records touch responses through the same APK. No separate response controller or BLE peripheral, no tonometer, no laser, no improvised retinal flash. Ten screening families: refractive impairment, presbyopia, dry eye, cataract, pterygium, red eye/conjunctivitis, AMD, glaucoma damage, diabetic retinopathy, strabismus/amblyopia; add colour-vision deficiency as a separate functional module. Per-eye, multi-condition inference. Broad findings first; then independently ranked possible diseases and clinically appropriate referral. No claim of eliminating all false negatives or being superior to official tests until clinical trials prove it.

**Design preference:** use fixed wide-field main eyepieces with one physically switchable / indexed high-acuity optical mechanism only if optical bench proves it. Avoid multiple frequently swapped binocular cassettes and motorised mechanisms. If high-acuity optics do not meet targets, use an inexpensive standard external chart rather than a fake 6/6 claim. Physical fitting, not AI hallucination, supplies missing information.

## H1. System partition and interfaces
```
[Patient handset in VR chassis] <--authenticated local Wi-Fi--> [Clinician / response handset]
   |  OLED / IMU / optional selfie camera                    | same APK: clinician + RESPONSE_PAD roles
   |  calibrated VR stereo optics + divider                  | patient touch surface + event timestamping
   |  deterministic visual-field stimulus renderer          | local encrypted event queue / STOP control
   +-- replaceable face interface                             +-- external-eye hood + ring (used before/after)
                                                               +-- validated fundus camera (used before/after)
                                                                   (own safety-controlled light)
```
The patient phone owns visual stimulus presentation, actual frame timestamps, trial order and trial validity. The clinician/response phone owns session setup/review, external image acquisition and report, and temporarily runs the patient-operated touch-response pad during the field-test block. The two handsets use one APK and authenticated encrypted local Wi-Fi. During the response block, the second phone is placed in the patient’s hand or on a stable lap/arm support; the clinician does not proxy the patient’s taps. Camera/fundus acquisition on that handset occurs before or after the response block, not concurrently. Link loss freezes scored stimuli; completed events remain durably stored on their originating phone.

### H1.1 Two-phone visual-field stimulus/response protocol (authoritative)
1. **Stimulus handset (Phone A):** mounted in the VR headset. It tests one eye at a time: the non-tested display channel is blanked and the headset divider maintains monocular separation. It renders the central fixation target and randomized suprathreshold stimuli at calibrated angular coordinates, one trial at a time. It interleaves blank and fixation-loss/catch trials and records the actual displayed-frame timestamp, eye (OD/OS), target coordinate, luminance/profile, catch-trial type, response-window bounds and all validity flags. Scheduled presentation time is not substituted for actual frame presentation. Headset IMU data may flag gross head movement but is not eye tracking.
2. **Response handset (Phone B):** the same APK is paired to Phone A and enters `RESPONSE_PAD`. During perimetry, the patient—not the clinician—uses a short screen tap to mean “I saw the stimulus.” The whole screen is a single blind-operable touch surface: the patient does not need to see the second screen or aim at a small control. Phone B is held securely or placed screen-up on a stable lap/arm support, with the hand position rehearsed before testing; keep the app awake and use a non-distracting low-luminance pad display. Optional haptic acknowledgement follows a registered tap. A long-press requests `STOP`. The app records the native monotonic touch-down timestamp, pointer count, input source, debounce result and local event ID. A response is not inferred from network packet arrival time.
3. **Per-trial interpretation:** one valid tap in the allowed response window is `SEEN`; a complete response window with no touch is `NO_RESPONSE` only if Phone B remained foreground/awake, touch calibration passed, pairing and heartbeat remained valid, the trial schedule was intact, and the response window elapsed. A disconnect, app backgrounding, screen timeout, untrusted clock mapping, touch fault, multi-touch ambiguity or interrupted window is `INVALID/UNGRADABLE`, never `NO_RESPONSE`. Taps during randomized blank/catch trials contribute to false-positive reliability checks.
4. **Cross-phone timing:** before a block, run repeated authenticated ping-pong clock exchanges and estimate Phone B-to-Phone A monotonic-clock offset plus uncertainty. Repeat checks during long blocks and after reconnect. Phone B timestamps touch locally; Phone A timestamps the actual stimulus frame locally. Merge events using the estimated offset and retain the offset, uncertainty, sync sample IDs and both raw timestamps. Do not use network arrival time as response time. If uncertainty exceeds the predeclared trial-matching/latency tolerance, pause and resynchronise or mark timing-dependent metrics ungradable. Response latency is a secondary reliability measure until input/display latency is bench-characterised; detection outcome is never rescued by an invented latency correction.
5. **Fail-safe and recovery:** STOP is a priority event from Phone B. Phone A also stops stimuli on heartbeat expiry, transport loss, app lifecycle loss or internal watchdog fault. The headset must have a physical quick-release; the patient may remove it immediately. After any disconnect, invalidate the in-flight trial, preserve completed trials, re-pair/re-authenticate, re-estimate clock offset and repeat the relevant calibration block before resuming. Do not fill gaps with synthetic responses.
6. **Accessibility and response-mode boundaries:** test the patient’s ability to hold/reach and reliably tap the handset before scored testing. Four-direction tasks may use trained large touch zones only if their spatial selection is demonstrated in practice trials. If touch use is unreliable, route to a separately validated alternative or mark the task `NOT_ASSESSED`; do not silently substitute clinician-entered responses or combine different input modes without validation. Voice/gesture input is not assumed equivalent.
7. **What this simulates—and what it does not:** the VR phone simulates the visual-field analyser’s stimulus display and suprathreshold trial protocol; the second phone measures the patient’s report that a stimulus was seen. Unless an optional gaze camera independently passes its gaze-error validation gate, fixation is instruction- and catch-trial-based; IMU head-motion data must not be represented as eye-position tracking. Report fixation limitations explicitly and do not claim a quantitatively verified fixation trace. This remains a research suprathreshold screening implementation, not a calibrated commercial perimeter and not Humphrey SAP equivalence. The response phone does not measure retinal physiology or independently verify perception.

## H2. Optical feasibility calculations - minimum before selecting parts
**Cardboard reference:** Google manufacturer design target 80 degrees circular total FOV. Actual FOV varies with lens, screen crop, IPD, eye relief, nose bridge and mechanical walls. Characterise each eye at 9 gaze positions and across supported faces. A 24-2-like field needs roughly horizontal +/-30 degrees (nasal locations), vertical +/-24 degrees. Test every stimulus coordinate against measured visible/calibrated angular footprint; do not infer from nominal FOV.

**Sampling:** display pixels-per-degree (PPD) approx N_pixels_per_eye / horizontal_FOV for ideal uniform field. With 1080 px per eye and 80 deg => 13.5 PPD (4.44 arcmin per pixel). For 6/6 acuity, ~1 arcmin critical stroke; 2 pixels/stroke suggests >=120 PPD before lens MTF, subpixel structure and aliasing. A nominal 10 deg setting at 1080 px yields 108 PPD, still below 120; 9 deg yields 120 ideal. A mechanical change in optical magnification, not rendering zoom, is required to improve PPD. Exact pixel counts and FOV await named phone and bench readings; high acuity remains a **conditional requirement**, not an achieved feature.

**Focus:** use optical vergence, not screen letter size. Thin-lens starting relation 1/f = 1/u + 1/v (sign convention explicitly handled); solve physically for virtual target vergence and optical conjugates. For 45 mm thin lens, moving a screen from f to conjugate for ~0.4 m virtual distance suggests a few mm axial travel; this is only a sizing illustration, not a production dimension. Eye relief, lens thickness and aberrations require ray tracing/measurement. Patient setting is indexed and separately verified for each eye. Accommodative artefacts prevent claiming cycloplegic-equivalent refraction.

**VR functional test group:** (1) central sharp acuity and near/focus; (2) wide-field suprathreshold location mapping (not full Humphrey SAP equivalence); (3) central hyperacuity, contrast, colour and binocular tasks. Use actual linear luminance calibration through eyepiece, display frame timing, lens mapping and optical leakage tests. Colour screens need spectral/white-point validation; create original digital plate stimuli, never claim digital Ishihara equivalence without study.

## H3. Mechanical chassis / sections / assembly
Cross section (not to scale):
```
FOREHEAD / FACE --> replaceable facial gasket --> guarded eye cavity
    -> left/right eyepiece with adjustable IPD (48-74 mm design envelope)
    -> opaque midline divider + anti-reflection baffles
    -> indexed optic carriage (clear mechanical lock and stops)
    -> rigid smartphone tray / clamp / orientation key -> patient screen
    -> accessible selfie-camera aperture only if camera ray path is verified
```
- Use one phone model initially. Set chassis dimensions from its CAD envelope, display active area, cameras, controls, weight and USB position. No guessed exact chassis dimensions before phone selection.
- Face-contact parts cleanable and replaceable. Quick release reachable by operator/patient. Wide, ventilated, wipeable strap; seated use only.
- All moving parts shielded; mechanical stops prevent any optical component approaching cornea. No open threads/rough 3D printed surfaces at face.
- Optional internal gaze module **reserved space only**, not included until front-camera tracking passes gaze-error gate. Optional photorefraction mount on clinician phone reserved; no unverified IR emitter.
- Optical component IDs encoded in fixed readable labels / passive fiducials. No sensor is needed simply to identify component if operator confirmation + camera verification can be proven reliable; otherwise add cheap Hall switch/ID later.
- External-eye hood must support constant working distance, angled safe illumination and 3 views (primary, nasal, temporal). No mechanical device touches cornea. Eye image crop, exposure and camera ID are fixed/recorded.
- Ring target/Placido accessory maintains known geometry, defines region visible after blink, prevents stray reflections; ring attachment is **not** a complete dry-eye diagnostic device.
- Fundus attachment on clinician phone, *not* optical patient VR path. Use a commercially verified/safety-tested imaging module for first human use; do not construct an unverified retina illumination path from a flashlight or USB LEDs. The phone-to-adapter mechanical mount and supported camera must match documented optical geometry.

## H4. Baseline parts, quantities and electrical connection authority
| ID | Component | Qty | Intended specification or acceptance |
|---|---|---:|---|
| HW-01 | Android patient phone | 1 | To be selected; OLED with calibrated display, gyro, enough stable CPU/GPU, documented camera access |
| HW-02 | Android clinician phone | 1 | To be selected; controlled rear capture/RAW or YUV, manual exposure/focus/white balance, local Wi-Fi |
| HW-03 | Opaque VR shell/tray | 1 | Match HW-01 CAD; balanced straps, guarded fit, removable face pad |
| HW-04 | Fixed wide-field eyepieces | 2 | Lens pair to be selected by optical ray trace / 24-2 pattern coverage |
| HW-05 | Central high-resolution optical insert/setting | 1 pair | Indexed, physically effective magnification; conditional pending optical gate |
| HW-06 | IPD mechanism + stops | 1 | Target envelope 48-74 mm; actual fit distribution to verify |
| HW-07 | Eye relief controls and divider/baffles | 1 set | Fit repeatability, monocular isolation |
| HW-08 | Standalone response controller | 0 | Removed by CR-001; HW-02 runs the in-app patient-operated touch-response role. No BLE accessory is fitted. |
| HW-09 | Face contact sets / wipes per protocol | Multiple | Disinfection and patient acceptance |
| HW-10 | Clinician phone ergonomic grip | 1 | Stable orientation, no camera occlusion |
| HW-11 | External-eye spacer/hood + scale marker | 1 | Repeatable pose, field, colour reference |
| HW-12 | External visible-light ring board | 1 | Low-voltage 5 V USB bank prototype only; see H5 |
| HW-13 | White LEDs, diffusers, series resistors | 8 each | External-eye illumination prototype, not for fundus |
| HW-14 | USB power bank and protected cable | 1 | Certified low-voltage 5 V output, no mains charging during patient exams |
| HW-15 | Resettable polyfuse, switch, enclosure | 1 each | Overcurrent, manual off, strain relief |
| HW-16 | Printed concentric rings + fixture | 1 | NIBUT investigation, geometric check |
| HW-17 | Fundus module with own verified optics/light control | 1 | Compatible with HW-02; retinal safety evidence required before patient use |
| HW-18 | Fundus fixation marker/target | 1 | Non-hazardous, operator-assistance |
| HW-19 | External near-chart / occluder with pinhole | 1 each | Low-cost ground-truth fallback during development |
| HW-20 | Calibration phantoms / printed scale, colour reference | 1 set | External-eye geometry; reference eye image / optical test targets |
| HW-21 | Transport case | 1 | Shock, dust, hygiene partition |

**Not selected at baseline:** motor, servo, IR laser/LED eye illuminator, tonometer, third phone, standalone response controller, Bluetooth/BLE peripheral, autonomous pupil-dilation system, retina LED circuit. Do not substitute undocumented components without change control.

## H5. Exact prototype electronics: EXTERNAL-eye illumination only
The only custom powered circuit needed initially is a **non-retinal, non-flashing, diffused external-eye LED ring**. Use a separate certified 5 V USB power bank and a commercial USB-A 5 V cable. Do not connect an LED driver directly to phone USB-C without correct power-role/CC design. All eight LEDs are in **parallel individual resistor-limited branches**, not eight LEDs on one common resistor.

**Electrical example to prototype (not an ophthalmic optical-safety certification):** 8 x ordinary diffused white indicator LEDs, nominal forward drop 3.0 V at low current; 8 x 330-ohm 0.25 W resistor. From a 5.0 V source, expected per-branch I=(5.0-3.0)/330 = 6.06 mA, total ~48.5 mA; total supply ~0.24 W. With actual Vf 2.8-3.3 V, each branch ~5.15-6.67 mA at 5.0 V. Each resistor dissipation <0.015 W nominal. Choose a 0.25 A hold-rated polyfuse at the 5V input, suitable 5 V single-pole switch and insulated enclosure. Verify supply range, worst-case LED forward voltage, brightness, temperature and material contact for exact purchased components. 330-ohm resistors are individual per LED. Select LEDs and diffusers by measured optical/radiometric output; electrical low current does not by itself prove ocular light safety.

```
CERTIFIED 5V USB POWER BANK (USB-A output; NO mains during use)
 VBUS +5 V ----- [F1 resettable fuse, I_hold 0.25 A] -- [SW1 ON/OFF] --+--[R1 330R 1/4W]-->| D1 --+
                                                                      +--[R2 330R 1/4W]-->| D2 --+
                                                                      +--[R3 330R 1/4W]-->| D3 --+
                                                                      +--[R4 330R 1/4W]-->| D4 --+--- GND
                                                                      +--[R5 330R 1/4W]-->| D5 --+
                                                                      +--[R6 330R 1/4W]-->| D6 --+
                                                                      +--[R7 330R 1/4W]-->| D7 --+
                                                                      +--[R8 330R 1/4W]-->| D8 --+
 GND ---------------------------------------------------------------(return)-----------------------+
```
Use polarity-keyed connector, strain relief, no bare contacts, insulating plastic ring/diffuser and no LED pointing through fundus optics. Human-use illumination requires a separate light exposure and IEC/ISO risk review. A passive, unpowered printed ring can be tested first under controlled room lighting. If auto-dimming is later required, it is a formally approved BOM and circuit revision, not an invisible software patch.

**Retinal device power:** Only manufacturer-defined interface, cable and driver. No arbitrary retina LED resistor, wattage, PWM or concentrated flash circuit is authorised by this document. Treat purchased module's rated input (may be self-powered, USB or integrated to approved phone camera) as a supplier-specific requirement. Hardware interlock/independent watchdog if light can remain on after software failure. Optical-radiation safety assessment against ISO 15004-2:2024 and instrument-specific rules precedes human exposure.

## H6. Assembly steps and electrical/optical checkpoints
1. Inventory each part and photograph serial/model/revision; check cracks, edges, wrong optic and cable polarity.
2. Assemble isolated phone tray + wide eyepieces and divider; measure active display alignment and eye relief at min/mid/max IPD.
3. Mount optional high acuity insert mechanically; prove repeating insertion gives same angular mapping (no assumption of stable focus).
4. Pair HW-01 and HW-02 through the same APK; test RESPONSE_PAD touch registration, one-tap debounce, accidental/multi-touch rejection, native event timestamps, haptic acknowledgement, long-press STOP and fail-safe behavior on Wi-Fi loss without a patient present.
5. Build external LED ring on breadboard for **bench-only** test. Measure 5 V rail, each branch current, fuse behavior, power and temperature; move into insulated assembly and perform optical safety review before patient use.
6. Integrate doctor camera hood; confirm image quality for corneal boundary/limbus under fixed distance, exposure and WB; validate against anatomical phantom.
7. Fit **approved** fundus adapter; no modification to supplied illuminator; test on artificial eye and documented safety configuration before any subject.
8. Re-run secure two-phone pairing and timed simulated visual-field blocks; verify clock-offset/uncertainty logs, touch-event-to-trial matching, durable event queues, disconnect recovery and configuration IDs.
9. Run drop, cable snag, touch temperature, cleanability and EMI/interruption checks before controlled human feasibility.

## H7. Hardware verification gates: mandatory empirical, not AI inferred
- H-G01: optical pupil-box / usable FOV measured across IPD/eye-relief and realistic patient positions; record all targeted visual-field points.
- H-G02: central viewed optical acuity test target ≥ required spatial frequency and contrast transfer; if not, **do not claim normal acuity**.
- H-G03: optical vergence measured for each indexed focus setting, separate from perceived on-screen size.
- H-G04: visible stimuli temporal/luminance/colour measured through eyepiece; no camera automatic settings inside the measurement chain.
- H-G05: correct laterality and monocular separation verified at extremes of fit.
- H-G06: external-eye imaging repeatable; lens ID, WB, exposure, working distance, detail resolution.
- H-G07: fundus artificial-eye focus + coverage + smallest clinically intended lesion-size verification; unit safe illumination documentation.
- H-G08: electrical insulation, fuse function, thermal, mechanical quick release, disinfection material compatibility.
- H-G09: front-camera tracking test including occlusion, pigmentation, glasses, fitting; no gaze-dependent claim until error bounds met.
- H-G10: full assembled system has no altered behavior after resets, cleaning, repeated module insertions.
- H-G11: response handset touch registration, touch-event timestamp integrity, cross-phone clock-offset uncertainty, trial matching, response-channel heartbeat, priority STOP and disconnect watchdog are measured and pass predeclared tolerances; invalid channels cannot create negative field points.

## H8. Hardware error register (major unresolved items)
| Risk ID | Failure | Practical mitigation | Residual acceptance |
|---|---|---|---|
| R-H01 | Camera cannot see eye inside VR | Reserve light-safe geometric path / optional inward cam, fallback to fixation-control trial methods | Calibration study required |
| R-H02 | Optical insert introduces blur/chromatic aberration | Avoid speculative lens stacking; ray trace + measure MTF | Reject insert if target fails |
| R-H03 | FOV clips stimulus | Full angular visibility map; hardware fit adjustment | Disable unsupported coords; validate altered protocol |
| R-H04 | Refraction confounded by accommodation | Objective photorefraction investigation + corrected-acuity baseline | No prescription claim |
| R-H05 | Pupil too small / media opacity hides retina | Guided recapture under safe protocol | Inadequate retinal screen referred |
| R-H06 | Anterior image colour uncontrolled | Lock white balance, white reference/illumination | No colour-sensitive AI if unsupported |
| R-H07 | LED constant-on fault | Manual switch, fuse, hardwired off | Exposure safety test |
| R-H08 | Retinal hardware incompatible with clinician camera | Manufacturer adapter/phone list prequalified | Do not hack with untested flash |
| R-H09 | Face fit unsafe/infectious | Quick release, replaceable interface, preheadset triage | Operator infection-control SOP |
| R-H10 | Response handset touch failure, accidental contact or link/timing fault | Pre-test touch calibration, one-finger/debounce checks, native timestamps, clock-offset uncertainty gate, heartbeat watchdog, invalidate interrupted trials | Bench + supervised usability validation required |

## H9. Hardware source and regulatory references
- Google Cardboard manufacturer design FOV and optics: https://support.google.com/cardboard/manufacturers/answer/6323398
- Google Cardboard calibration viewer profile: https://support.google.com/cardboard/manufacturers/answer/6321873
- India DR consensus including field coverage: https://pmc.ncbi.nlm.nih.gov/articles/PMC7942107/
- Portable glaucoma field-test systematic review (2026): https://pubmed.ncbi.nlm.nih.gov/42563154/
- ISO 15004-2:2024 specification: https://www.iso.org/cms/live/live/en/sites/isoorg/contents/data/standard/07/99/79919.html
- India CDSCO software guidance (July 2026): https://www.cdsco.gov.in/opencms/opencms/en/Medical-Device-Diagnostics/Medical-Device-Diagnostics/


---

# APPENDIX B: SOFTWARE BLUEPRINT

# EYEATLAS VR - SOFTWARE, AI, DATA AND ANTIGRAVITY BLUEPRINT
**Version 1.2 | 20 November 2026 | CR-001 + CR-002 | Android-first; cross-platform expansion deferred**

## S0. Delivery contract and coding philosophy
One Android APK with PATIENT and CLINICIAN roles; local-first pairing and session orchestration; modular but lean packages; no one-function files; no unnecessary microservices or cloud dependency. Start in a simulated-hardware environment; run on the selected Android phone(s) *before* building custom optics; then calibrate and integrate physical modules. One repository created in full at phase 0. All planned code/config/test files are predeclared. Additional files need an approved change request and manifest update; do not clutter the project with patch files, duplicate modules, scratch outputs or undocumented helper scripts.

**Safety and claims:** Every diagnostic score is initially a research placeholder, visibly distinguished from measured and clinically calibrated outputs. Never fabricate real performance or claim superiority before prospective validation. The software must produce per-eye multi-label conditions, coexisting disease support, urgency independently of disease rank, and no false 'all-clear' on absent risk-based retinal screening. No model trains itself on a patient during screening.

**Active stack for P00-P21 (Android only):** Android Kotlin + Jetpack Compose, single APK role selection; CameraX with Camera2 controls and calibrated YUV/RAW path; secure local Wi-Fi authenticated transport for the paired stimulus/response phones; native Android touchscreen input-event timestamping and haptic acknowledgement; local Room encrypted-at-rest storage strategy; ONNX Runtime Android (MIT) **or** LiteRT (Apache 2.0) choose one at phase S04 benchmark, not both long-term; OpenCV >=4.5 Apache 2.0 for controlled image geometry; Python offline training, exports to chosen inference runtime. Cardboard native SDK is an option for head-transform/device profile, but avoid unnecessary 3D engine; render 2D clinical stimuli deterministically with an optics-calibration mapper and frame timing logs. Verify component licence at pinned version before use.

**CR-002 boundary rule:** Do not introduce web frameworks, iOS frameworks, a mandatory hosted API, cloud-only authentication or cross-platform UI abstractions into P00-P21. Keep domain models/protocols/decision semantics serializable and independent of Android UI classes. Put camera, secure storage, clocks, networking, file access and sensors behind the existing platform-specific interfaces. Define versioned DTO/schema compatibility where the current data model already crosses process/device boundaries; do not create speculative microservices or duplicate business logic. Android remains local-first and usable without internet.

## S1. Immutable planned repository tree
```
eyeatlas-vr/
  README.md
  LICENSE
  .gitignore
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  gradle/wrapper/gradle-wrapper.properties
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/org/eyeatlas/MainActivity.kt
  app/src/main/java/org/eyeatlas/App.kt
  app/src/main/java/org/eyeatlas/ui/PatientScreens.kt
  app/src/main/java/org/eyeatlas/ui/DoctorScreens.kt
  app/src/main/java/org/eyeatlas/ui/SharedComponents.kt
  app/src/main/java/org/eyeatlas/core/Models.kt
  app/src/main/java/org/eyeatlas/core/Protocols.kt
  app/src/main/java/org/eyeatlas/core/DecisionEngine.kt
  app/src/main/java/org/eyeatlas/core/MeasurementEngine.kt
  app/src/main/java/org/eyeatlas/sensors/VrPresenter.kt
  app/src/main/java/org/eyeatlas/sensors/CameraPipeline.kt
  app/src/main/java/org/eyeatlas/sensors/ResponseAndGaze.kt
  app/src/main/java/org/eyeatlas/sensors/Calibration.kt
  app/src/main/java/org/eyeatlas/vision/FunctionalTests.kt
  app/src/main/java/org/eyeatlas/vision/AnteriorTests.kt
  app/src/main/java/org/eyeatlas/vision/RetinalTests.kt
  app/src/main/java/org/eyeatlas/vision/BinocularTests.kt
  app/src/main/java/org/eyeatlas/ai/Inference.kt
  app/src/main/java/org/eyeatlas/ai/QualityAndRanker.kt
  app/src/main/java/org/eyeatlas/net/PairingAndTransport.kt
  app/src/main/java/org/eyeatlas/net/SessionSync.kt
  app/src/main/java/org/eyeatlas/store/PatientStore.kt
  app/src/main/java/org/eyeatlas/store/ReportsAndExports.kt
  app/src/test/java/org/eyeatlas/LogicTests.kt
  app/src/test/java/org/eyeatlas/ProtocolTests.kt
  app/src/test/java/org/eyeatlas/CalibrationTests.kt
  app/src/test/java/org/eyeatlas/MultiDiseaseTests.kt
  app/src/test/java/org/eyeatlas/FaultTests.kt
  app/src/androidTest/java/org/eyeatlas/DeviceE2ETests.kt
  ml/requirements.lock
  ml/datasets.yaml
  ml/models.yaml
  ml/download_and_audit.py
  ml/train.py
  ml/evaluate.py
  ml/export.py
  scripts/bootstrap.sh
  scripts/verify.sh
  scripts/snapshot.py
  docs/requirements.csv
  docs/risk_register.csv
  docs/traceability.csv
  docs/data_licenses.csv
  docs/hardware_contract.md
  docs/validation_plan.md
  docs/sources.md
  hardware/bom.csv
  hardware/electrical.md
  hardware/chassis_parameters.json
  hardware/bench_results.csv
  MASTER_BLUEPRINT.md
  PROJECT_LOG.md
  CODE_DUMP.md
```
**Important:** phase 0 creates *every named file and path* exactly once. In the starter ZIP, source files are empty reservations, intentionally not claimed runnable. Antigravity's phase-0 prompt fills the minimal compilation scaffold. The Gradle wrapper JAR, when generated by the build tool, is a required binary build support artifact and may be introduced during controlled bootstrap, not treated as arbitrary source proliferation. Generated `.gradle`, `build/`, `.idea`, models, caches, and raw clinical images stay out of CODE_DUMP and git.

## S2. Screen-by-screen UX contract (each phase includes patient and clinician views)
| Screen ID | Patient display | Clinician display | State and failures |
|---|---|---|---|
| UX-01 | Pairing code / consent status | Start session, patient ID, QR pairing | Wrong pairing, duplicate ID, offline |
| UX-02 | Fit instructions and central dot | IPD, eye selection, focus, camera/illumination readiness | Calibration invalid -> stop relevant tests |
| UX-03 | Response-phone touch tutorial and practice trials | RESPONSE_PAD readiness, touch registration, timestamp/clock-sync status, comprehension | Accidental, missed, multi-touch or late input invalidates practice; do not score until recovered |
| UX-04 | Broad binocular / colour screening | Live module stage, no answer coaching | Profile brightness/colour invalid |
| UX-05 | Monocular acuity and near | OD/OS state, clarity and optical index | Subpixel targets forbidden |
| UX-06 | Central hyperacuity | Fixation, repeated abnormal locations | Missing fixation -> retest |
| UX-07 | Peripheral suprathreshold stimuli in VR headset | Live response-pad readiness, progress and reliable trial count (not target answers) | Luminance/angular map and response channel verified; disconnect invalidates in-flight trial |
| UX-08 | External-eye instruction | Pose overlay, illumination, capture, colour/scale QC | Red eye urgent triage prior to VR |
| UX-09 | Dry eye blink sequence guidance | Ring live preview / blink completeness / NIBUT curve | Incomplete blink -> recapture |
| UX-10 | Fundus fixation target | Retinal alignment, focus, disc+macula fields and gradability | No image==no negative DR |
| UX-11 | Binocular alignment task | External video / cover test annotation | Stereo failure not alone strabismus |
| UX-12 | Completion / referral prompt | Broad summary and ranked independent disease candidates, evidence, referral | No fake probabilities before calibration |
| UX-13 | Discomfort STOP anytime; long-press on response phone and immediate headset removal | Priority STOP state, clinician emergency bypass and rationale | STOP must halt stimuli; quick-release remains available if phone link fails |
| UX-14 | N/A | Calibration lab, bench metrics, model governance and audit export | Admin only; research profile required |

**Accessibility:** Large plain-language UI, audible optional instructions (not required), local languages via resources when added, tutorial for non-literate participants, visual target randomisation, no flashing transitions, seating and stop. The response phone provides a large touch surface for `SEEN` and a long-press `STOP`; use trained four-zone touch mapping for direction responses only when the participant can reliably identify zones by touch. Validate low-mobility alternatives separately; never silently substitute clinician-entered answers.

## S3. Data model: enforce per-eye multi-morbidity and independence
```
Patient {patient_id, age_band, diabetes, symptoms[], current_correction, comorbidity_flags}
Session {session_id, patient_id, patient_phone_id, clinician_phone_id, response_phone_id,
         response_role, mode, consent, start, state}
Eye {laterality: OD|OS, correction_status, optical_profile, fixation_status}
StimulusTrial {trial_id, session, eye, test_code, target_angle, luminance, optical_index,
               monotonic_presented_ns, response_phone_monotonic_ns, corrected_response_ns,
               clock_offset_ns, clock_uncertainty_ns, expected_answer, observed_answer,
               frames_rendered, calibration_hash, response_event_id, validity_flags[]}
ResponseEvent {event_id, session_id, trial_id?, response_phone_id, monotonic_event_ns, input_type,
               pointer_count, input_quality, clock_sync_id, received_by_patient_ns, durable_status}
ImageCapture {image_id, session, eye, mode, camera_id, raw_uri, processing_hash,
              focus_metric, exposure, white_balance, visible_structures[], field_coverage, quality}
Measurement {measurement_id, provenance_ids[], eye, method, value, units, CI, validity, limits[]}
Finding {eye, broad_domain, finding, direction, severity, provenance_ids[], validity}
ConditionInference {eye, disease_code, probability_or_unnormalized_score, calibration_id,
                    evidence_for[], evidence_against[], unknown_evidence[], recommended_test}
Referral {urgency, reason, disease_specific_diagnostic_tests[], completion_status}
AuditEvent {who_or_which_process, time, device, action, before_hash, after_hash, reason}
```
Do not use a softmax to force all diseases to sum to 100%. Use independent multi-label probabilities (sigmoid heads or calibrated disease-specific models). Do not multiply scores of correlated features as though independent. Require a `model_calibration_id` before displaying numeric probabilities. Report uncertain alternative diagnoses and diseases beyond the target list; urgent referral is a separate decision. Each eye has independent findings and may carry multiple disorders.

**Separation of a broad finding from disease cause:** `reduced_acuity` is a measured Level-1 finding; refractive error, cataract, corneal opacity, retinal disease and amblyopia are separate Level-2 candidates. A valid independent fundus finding is not erased because refractive correction improved acuity. When patient has cataract + DR, lens opacity may invalidate retina quality; then DR remains unassessed. No negative-by-default values.

## S4. Adaptive protocol engine with causal quality/recovery logic
```
clinical_intake -> urgent triage -> quick external exam -> tear video (if indicated)
 -> patient fit and device calibration -> L1 common functional tasks
 -> mandatory risk-based checks (e.g., diabetes -> retinal images)
 -> L2 active evidence acquisition -> independent per-disease calibration -> referral
```
**Test choice:** choose action with highest expected reduction in clinical decision uncertainty per patient minute, but only among clinically valid and safe tests. Hard-required risk-based checks cannot be waived by a negative quick screen. Use budgeted test selection, capped retakes, and measurement independence. If a test fails, identify mechanism: e.g. shifted phone, eyeglass reflection, occluded pupil, blink, motion, wrong fixation, missing fundus field; adjust specifically and repeat a *new* independent observation. Do not repeat until normal. When no valid physical evidence can be collected, route to the appropriate *actual alternative examination*, not an unsupported disease score.

**Safety checks outrank AI selection:** sudden visual loss, corneal trauma, painful photophobic red eye, possible retinal detachment or chemical exposure -> bypass routine headset; explicit urgency and immediate professional care instructions. Headset physical release accessible. Do not label these cases conjunctivitis automatically.

## S5. Clinical measurement protocols and falsification tests
| Screen | Patient mode or camera | Actual measurement | Required comparator / falsifier |
|---|---|---|---|
| Refractive/acuity | Narrow high-PPD, adaptive Landolt C, test each eye | logMAR threshold (not a prescription); optional focus response | ETDRS and subjective/cycloplegic refraction where appropriate; test accommodation confounding |
| Near/presbyopia | True measured optical conjugate near demand | near acuity and add-response curve | near chart at 40 cm over suitable distance correction |
| Dry eye | Doctor camera ring video before VR + symptoms | blink completeness, NIBUT and variability | TFOS-style clinical NIBUT, symptoms and ocular surface signs |
| Cataract | Red-reflex/controlled anterior images + contrast | media-opacity suspicion + functional impairment | masked slit lamp including posterior subcapsular cases |
| Pterygium | Standardised multiple gaze photos | limbal crossing, corneal encroachment proportion | masked slit lamp; include pinguecula and atypical lesions |
| Red eye | Color-controlled conjunctival images + triage | injection/discharge and danger-sign detection | clinician differentiates conjunctivitis, keratitis, uveitis, glaucoma emergencies |
| AMD | VR hyperacuity and macular fundus | central distortion + visible macular pathology | dilated retina + OCT where required; mild AMD negative cases |
| Glaucoma | Patient VR phone displays calibrated wide-field suprathreshold targets; paired response phone records patient touch responses; clinician phone captures optic disc | Repeatable per-eye field abnormality / suspicious disc, with response-channel and fixation reliability reported separately | SAP plus full optic nerve clinical assessment; no elevated-IOP claim; prototype is not Humphrey-equivalent |
| DR | 2 field fundus images and lesion AI | referable DR where fields/quality adequate | trained graders, masked 2-field or appropriate clinical reference; DME limitation |
| Strabismus/amblyopia | Binocular fusion/crowding + external cover-video | alignment, stereo and interocular crowded acuity | cover/prism test, cycloplegic refraction in children, paediatric exam |
| Colour deficiency | Original calibrated pseudoisochromatic / chromatic contrast tasks | red-green discrimination screen | validated plate/lantern/other appropriate colour vision reference |

**Visual-field response integrity:** Phone A must log the actual stimulus frame time; Phone B must log the patient touch-down time on its own monotonic clock. Use clock-offset estimates with uncertainty, never packet-arrival time. A blank catch-trial tap is a false positive. A completed no-tap window is a non-detection only while the response channel is demonstrably healthy. Any disconnect, clock ambiguity beyond tolerance, app backgrounding or input fault is `INVALID/UNGRADABLE`, not a negative field result. Perimetry remains suprathreshold research screening and is not quantitative Humphrey SAP equivalence.

**Glaucoma limit:** no tonometer, no isolated ocular hypertension detection; early glaucomatous damage can be perimetrically absent. **DR/AMD limit:** fundus image cannot confirm retinal thickening or all OCT-only disease. **Amblyopia limit:** poor visual acuity is not proof of amblyopia. These are physical/clinical limitations; software must not claim they were solved.

## S6. Model library and dataset acquisition: verified licence and adequacy
**Licence policy:** MIT/Apache-2.0/BSD for code; CC BY 4.0/CC0 for research data where licensed by original authoritative release, retaining attribution and required notices. Do not treat public web availability, a CC BY research paper, a Kaggle notebook licence, or a mirror's tag as a licence to its underlying data. Do not add GPL/AGPL code or GPL-licensed data without explicit compatibility review. Do not assume all pretrained weights inherit the code licence. Training data copyright, privacy and clinical consent are separate issues.

| Dataset / source | Evidence and licence status | Covers | Use / download constraints |
|---|---|---|---|
| **RFMiD** (3,200 retina images) | Publisher states CC BY 4.0 | DR, AMD, other multi-label retina patterns | Official `https://riadd.grand-challenge.org/download-all-classes/`; account may be needed; request is not guaranteed auto |
| **IDRiD** (516 fundus images) | Official challenge states CC BY 4.0 | Indian DR grading and lesion masks | `https://idrid.grand-challenge.org/Data/` ; IEEE DataPort official download may require account; verify mirror provenance before automatic use |
| **SLID** (2,617 slit-lamp images) | Paper CC BY; GitHub repo `https://github.com/xumingyu-hub/SLID` publicly referenced. *Dataset file-level licence must be confirmed* | Cataract, pterygium, injection, keratitis, other anterior lesions | Download only after repository dataset licence verified; slit-lamp -> phone domain shift high |
| **NIKBUT / DEWS II data** (99 observations, videos) | RepOD record advertises CC BY 4.0 per files | Placido NIBUT and dry eye symptom/measurements | `https://repod.icm.edu.pl/dataset.xhtml?persistentId=doi:10.18150/TT8JVD`; limited n, 3.2GB NIKBUT zip; verify per-file licence |
| **Conjunctivitis Recognition** | Mendeley page clearly CC BY 4.0 | Conjunctivitis image prototype | `https://data.mendeley.com/datasets/vhy5mjb6wg/1`; confirm labels/quality/patient IDs; too limited for serious red-eye detection alone |
| **Eye Conjunctiva Segmentation** (547 images) | Mendeley page CC BY 4.0 | Ocular-surface ROI and masks | `https://data.mendeley.com/datasets/yxwjgcndg2/1`; no disease diagnosis from segmentation masks |
| **Anterior eye classification (uveitis/conjunctivitis/cataract/eyelid)** | NLM catalog lists CC BY 4.0; upstream details must be confirmed | Red-eye/other pilot | Find original dataset / exact DOI and inspect duplicates and oversampling; HOLD until metadata checked |
| **Nuclear Cataract DB** (1,437 slit lamp) | NLM catalog lists CC BY 4.0; verify Mendeley record | Cataract type/severity pilot | `https://data.mendeley.com/datasets/6wv33nbcvv`; slit-lamp imaging is not rear-phone photography |
| **Pterygium on Roboflow** | Individual pages show CC BY 4.0, provenance/consent uncertain | Pterygium proof of concept | `https://universe.roboflow.com/eyescareworkspace/pterygium-lqtlf`; HOLD until source and dataset rights verified |
| **PAPILA glaucoma fundus** | **Licence conflict**: article CC BY but repository records GPL-3.0+ | Glaucoma disc pilot | `https://figshare.com/articles/dataset/PAPILA/14798004`; quarantine pending authoritative file licence analysis; do not auto-train |
| **CSDI cataract (2026)** | **Licence conflict**: paper states CC BY, GitHub/HF portions say CC BY-NC | Cataract fundus quality | HOLD until resolved with source licence; no commercial training assumption |
| **APTOS / REFUGE / ODIR / Messidor-2 / AREDS** | Noncommercial/competition/gated or separate terms | Potential benchmarks | EXCLUDED from zero-permission auto-download path unless specifically cleared |

**Critical conclusion:** no verified permissionless, sufficiently representative, clinical-grade combined public corpus exists for all ten conditions and our device. We CAN bootstrap retinal/ocular-surface AI from open sets; we CANNOT honestly promise target metrics for all ten on the first deployment without capturing labelled data from the exact phones/optics and collecting reference examinations. The dataset downloader must skip gated sources rather than silently fetch mirror copies.

**Download policy (build S03):** `ml/datasets.yaml` records exact upstream URL, licence URL/text, citation, consent/provenance, hash, modality, allowed use, patient IDs, labels, expected sample count, size, manual login required. Script defaults to dry-run; auto-download ONLY if direct documented URL/API + appropriate licence + checksum + no access prohibition. Emit structured BLOCKED_AUTH / BLOCKED_LICENSE / CHECKSUM_FAIL; never bypass website controls, hidden APIs, captchas or rate limits. Large datasets stay external. Store raw manifests, no data or weights in repo snapshots.

## S7. Training/testing/validation separation
- Split by **patient** and, where feasible, by **site, camera, time, laterality, visit**, rather than by individual image. Never put different eyes from same patient in separate splits.
- Preserve source official test split; hold out a truly external source/site. Deduplicate by content and near-duplicate visual hashes across all datasets.
- Multi-label retina model with independent disease heads and evidence localization; add quality model trained with real degradation labels; anterior segmentation + disease heads and unknown-lesion detection. Do not use free-text multimodal LLM as primary disease scorer.
- Avoid imputed ground truth: validate negative labels and 'normal' eyes by appropriate clinical reference, not absence of annotation. A fundus labelled normal for DR may still have glaucoma or AMD.
- Calibrate probability with held-out data representing actual target prevalence; report per-condition reliability, Brier/ECE, ROC/PR, CIs, PPV and referral burden. Treat age/diabetes risk as priors but avoid age-alone diagnoses.
- Run ablation: (1) broad screen only; (2) imagery only; (3) fusion; (4) adaptive vs full fixed pathway. Show incremental detection and time savings. Count abstained/failed/missing measurements in intention-to-screen denominators.
- Pretrained weights must pass separate open-weights licence audit; test actual ONNX mobile inference profile on two target phones (memory, fps, thermals, offline start).

## S8. Performance targets as prospective acceptance criteria, not achieved claims
| Endpoint | Proposed engineering target / comparison |
|---|---|
| Acuity | >=90% of repeatable tests within 0.1 logMAR of ETDRS; no 6/6 claim below actual resolved PPD; agreement limits reported |
| Near vision | Corrected near acuity reproducible; compare with 40 cm near-chart and refraction, no faux optical distance |
| Dry eye | NIBUT repeatability and DED sensitivity/specificity vs TFOS workup; no arbitrary universal 10 s threshold across devices |
| Referable anterior abnormalities | Aim sensitivity >=90%, specificity >=90% by condition; high sensitivity for serious red eye triage |
| AMD | Referable AMD target >=90% sensitivity at clinically workable specificity, validated by severity; no OCT-only disease claim |
| Established glaucoma damage | Seek >=90% sensitivity / >=90% specificity against reference; benchmark portable perimetry pooled 86%/88%, not universal govt standard |
| Referable DR | Seek >=90% sensitivity, >=85% specificity, gradable coverage >=95% in defined population; predeclare clinical reference |
| Strabismus/amblyopia | Independent targets for manifest deviation and age-specific amblyopia risk; exclude no child silently |
| Multi-label | Sensitivity and specificity per eye and per condition with comorbidity subset; no probability sum constraint |
| Efficiency | Time-to-decision and incomplete rate with one-side and two-side CI; show non-inferiority then potential superiority |

A superiority claim requires a comparator defined *per test* and study design with pre-specified margin/power; there is no single nationally mandated government accuracy number to beat. No universal 95% claim. In a 3% prevalence condition, 90% sensitivity/90% specificity in 1,000 tested produces approx 27 true positives and 97 false positives, illustrating capacity/burden issues.

## S9. Security, quality, privacy and release
- All phone data local by default; authenticated QR pairing with short-lived key material and TLS/session encryption over local Wi-Fi. No BLE or Bluetooth response peripheral is used. QR displays public key/fingerprint + one-time code, not personal identifiers. Bind every response event to the patient, session, eye, trial block and paired response-phone identity.
- Patient identifiers de-identified in research data; encrypted file storage, local Android Keystore for keys, granular clinician permission; audit logs and restore tests. No training on patient data without relevant consent, governance, ethics approval.
- UI every phase (setup, test, capture, review, export); none of the underlying engines may be implemented without reachable UX and an automated test for it. Avoid mock data in final clinical report: explicit `SIMULATED` mode flag and watermark during development.
- Build with exact pinned dependency versions and offline lockfiles; no unreviewed dependency updates. Prefer MIT project core with notices for Apache/CC BY material; keep external data outside code license.
- Tests: unit, property-based optotype geometry and laterality, instrument profile hash verification, fault injection, Android instrumented offline, supervised hands-on with simulated eyes, prospective clinical validations.

## S10. Phase progression and hard gates
The full Antigravity exact per-phase prompts are in MASTER_BLUEPRINT.md (sections M5-M6). Each phase uses precisely two prompts: A implementation, B adversarial audit. A phase passes only when `scripts/verify.sh --phase Pxx` succeeds, project log captures failure/root cause/fix, and code-dump snapshot exactly matches tracked essential files. Commit and tag once all checks pass. Never advance on expected, partial or simulated test success. On failed gate fix *within the same phase*; refactor the canonical file instead of adding `_fixed` copies or patches. Audit prompt is read-only until separately authorised correction.

## S11. Software implementation references
- Android CameraX/camera manual control: https://developer.android.com/media/camera/camerax
- Google Cardboard source, Apache 2.0 terms/trademark caveat: https://github.com/googlevr/cardboard
- OpenCV licence (>=4.5 Apache 2.0): https://opencv.org/license/
- ONNX Runtime GitHub MIT: https://github.com/microsoft/onnxruntime
- Google LiteRT Apache 2.0: https://github.com/google-ai-edge/LiteRT
- TFOS DEWS II diagnostics: https://tfosdewsreport.org/report-diagnostic_methodology/131_36/en/
- DR India guidelines: https://pmc.ncbi.nlm.nih.gov/articles/PMC7942107/
- SLID anterior multimorbidity and leakage example: https://pmc.ncbi.nlm.nih.gov/articles/PMC12832839/
- India's CDSCO medical software guidance: https://www.cdsco.gov.in/opencms/export/sites/CDSCO_WEB/Pdf-documents/Guidance-document-on-Medical-Device-Software-under-MDR-2017.pdf


## S12. CR-002 cross-platform expansion contract (post-P21 only)
This section is a future architecture contract, not an instruction to implement web/iOS during P00-P21.

| Layer | Android-first release | Post-P21 target |
|---|---|---|
| Client | Kotlin + Jetpack Compose single APK | Keep Android client; add responsive web client and native iOS client |
| Clinical/domain contracts | Versioned, platform-neutral schemas and test definitions | Shared contract package/specification with generated or verified client models |
| Backend | Local-first Room and on-device inference; no mandatory cloud | FastAPI service, authenticated APIs, shared patient/session/report services, controlled model services |
| Sync | Local records and explicit export/recovery | Encrypted offline-first synchronisation, conflict resolution, audit trail |
| Imaging | Android CameraX/Camera2 and validated accessories | Separate browser/iOS acquisition adapters with capability and calibration gates |
| VR perimetry | Android headset phone + paired response phone | Android remains reference; iOS native VR option must be independently engineered/validated; browser VR only if capability tests pass |
| AI | Python training; selected mobile runtime | Shared model registry/artifact versioning; mobile and server inference parity tests |

**Backend design constraints for X01:** API versioning; OpenAPI contract; OIDC-compatible identity or equivalent reviewed auth; role-based access; tenant isolation; consent and purpose limitation; encrypted transport/storage; managed secrets/keys; append-only audit events; backup/restore and deletion/export workflows; data residency/legal review; rate limiting and abuse protection; offline sync idempotency and conflict policy. Do not place identifiable patient data in URLs, analytics or logs. No multi-tenant deployment before a threat model and privacy review.

**Clinical consistency constraints:** backend and clients must use the same versioned protocol definitions, units, laterality, calibration/profile IDs, evidence provenance, quality flags, abstention semantics and disease-code mappings. Never recompute a clinical result with an unversioned algorithm. Every result records app/client version, model version, protocol version, device profile and calibration hash.

**Platform capability declaration:** each client reports its supported acquisition capabilities and validation status. If camera control, display calibration, angular geometry, stimulus timing, touch timing, local link or accessory support is not verified, mark the corresponding test `NOT_SUPPORTED`, `NOT_ASSESSED` or `UNGRADABLE`. Do not silently substitute browser defaults or infer that a web/iOS result is interchangeable with Android.

**Release rule:** X01-X06 require a separate implementation prompt and independent audit for each phase. No web/iOS release or clinical-use claim is authorised by the existence of this plan alone.

---

# APPENDIX C: HARDWARE-SOFTWARE / VALIDATION BLUEPRINT

# EYEATLAS VR - HARDWARE-SOFTWARE INTEGRATION, FMEA AND VALIDATION BLUEPRINT
**Version 1.2 | 20 November 2026 | CR-001 + CR-002 | Android-first hardware/software contract; cross-platform expansion deferred**

## I0. Why this contract exists
A camera can capture a sharp image of the wrong anatomy; a headset can correctly display pixels at the wrong angle; an ML model can be accurate on the original dataset but wrong on an unseen phone. Software must **not silently correct or smooth these physical failures**. Instead, each measurement records its calibration and raw evidence, the runtime actively acquires better evidence where possible, and the result is emitted only if both physical and clinical conditions pass. Multi-disease coexistence is expected, not an exception. All parts are deliberately lean but interfaces are explicit, versioned, testable and reversible.

## I1. Single shared versioned hardware contract
```
DeviceProfile v1 {
   profile_id, patient_phone_make_model, serial_hash, os_build, app_version,
   display_px_per_eye, refresh_rate, measured_luminance_curve, colour_profile,
   wide_optic_id, central_optic_id, lens_distortion_map_hash,
   angular_visibility_map_OD_hash, angular_visibility_map_OS_hash,
   IPD_supported, eye_relief_range, indexed_focus_map,
   anterior_camera_id, anterior_intrinsic_hash, camera_color_profile,
   fundus_attachment_id, fundus_optical_safety_cert_id,
   timestamping_validation, validation_date, expiry, signed_digest
}

CaptureManifest v1 {
   session, patient, eye, mode, module_id, profile_id, calibration_hash,
   local_timestamp_ns, corrected_sync_offset_ns,
   position, field_coverage, input_quality,
   original_sha256, image_format, camera_controls, processing_pipeline_hash
}

ClinicalEvidence v1 {
   finding, eye, source_measurement_ids[], valid_physical_observation,
   reference_method, uncertainty, assessed_disease_codes[], prohibited_claims[]
}
```
Profile changes trigger impact graph invalidation of dependent measurements (not blanket erase of unrelated tests). An optical cassette replacement invalidates those VR tests but not previously valid anterior photographs; clinician camera profile change invalidates image tests, not earlier valid acuity.

## I2. Physical link and app ownership
**Patient stimulus phone:** deterministic frame scheduler and stereo/optical mapping; local immutable record of all stimulus trials; actual frame-presentation timestamps; local sensor/fit status; no dependence on response-phone network timing to schedule stimuli. It must freeze scored trials on response-channel heartbeat expiry or transport failure.
**Clinician/response phone:** camera capture with RAW/YUV access where supported; accessory selection; quality overlays; session and risk-based protocol control; in-app `RESPONSE_PAD`; native monotonic touch-event capture; local encrypted durable queue; STOP control; signed results export and clinician confirmation. The clinician/response phone is handed to or placed within reach of the patient only during the response block; camera/fundus capture is performed before or after that block.
**Connection:** one-time QR pairing and authenticated encrypted local Wi-Fi; unique session/device IDs, monotonically increasing event sequences, acknowledgements, idempotency on replay and clock-offset/uncertainty estimation. Touch timestamps are captured on the response phone and mapped to the patient phone's monotonic timeline; packet-arrival time is never the response time. Never accept wrong patient/eye/session responses. On disconnect, preserve completed events, invalidate the in-flight trial and stop new stimuli. No mandatory cloud and no BLE response accessory.
**Response semantics:** a short tap on the response pad means `SEEN`; a complete valid response window with no touch means `NO_RESPONSE`; channel failure, timing ambiguity, backgrounding or interrupted windows mean `INVALID/UNGRADABLE`. Blank catch trials detect false positives. A long-press sends priority `STOP`; loss of link also triggers a local patient-phone watchdog. A no-touch event is not a negative field point unless all channel-validity gates pass.

## I3. Versioned acquisition modules and geometry interfaces
| Module | Mechanism and precise contract | Interaction and failure handling |
|---|---|---|
| Wide VR optics | Patient-phone screen coordinates -> perceived angular x/y, calibrated intensity/time; stimulus schedule and actual frame timestamps | Test only mapped angular cells; detect headset shift and fixation issues; response comes from paired phone touch events |
| Narrow central vision | Physical optics change PPD and true optical vergence | Verify optics ID, focus-index and calibration hash before showing fine optotypes |
| Front-camera gaze (optional) | Eye ROI, gaze ray and calibrated error distribution | Never use as quantitative perimetry fixator before field-position uncertainty bound verified |
| Response handset | Patient-operated, full-screen blind-operable touch pad in the same APK; native monotonic touch timestamps mapped to patient-phone clock | One short tap anywhere = `SEEN`; no visual aiming required; no tap = `NO_RESPONSE` only with a healthy response channel and completed window; otherwise `INVALID`; long-press STOP and reconnect are fail-safe |
| Clinician external camera | Intrinsics, pose, exposure, WB, working distance | Live cornea/limbus overlay, focus/scale/colour gating, patient moves gaze when asked |
| Placido ring | Ring image geometry, full blink event, stable corneal region | Retake on ring occlusion, missing complete blink, specular artefacts; retain repeat statistics |
| Fundus camera | Field of view, retinal resolution, centre, permitted operating mode | Pre-check safety certification, fixation target, macula/disc coverage, recapture holes; original images immutable |
| Photorefraction (future) | Fixed illumination baseline, camera-pupil geometry, eye-safe source | NOT ENABLED without measured optics and clinical comparator; no prescription claim |

## I4. Two-level screening algorithm, no competing single-cause labels
**Level 1:** patient-phone stimuli plus paired response-phone events and anterior photos provide broad `VisualClarity`, `NearVision`, `Chromatic`, `CentralVision`, `PeripheralField`, `Binocular`, `AnteriorSurface` evidence per eye. For `PeripheralField`, retain stimulus validity, response-channel validity, fixation reliability and clock-sync uncertainty as separate fields. Define age and cooperation eligibility and optical validity separately. Level 1 cannot rule out structural retinal disease or ocular hypertension.

**Level 2:** posterior/structural or condition-specific tests triggered by Level 1 and by mandatory *risk* independently: diabetes -> DR fundus, suspicious disc/family risk -> glaucoma pathway, older age/other macular risk -> appropriate macular imaging; symptomatic urgent findings -> bypass. Disease inference for all 10 uses independent marginal calibrated probabilities, **not** single top-class softmax. Patients can have refractive error + cataract + dry eye + DR; same symptom has multiple causes. Evidence provenance shows which measurement raised/lowered each disease candidate. None/other disease is an explicit competing explanation, not a dropped case.

**Probability calibration design:** `p_i=P(disease_i | data for this eye and context)`. The sum of p_i may exceed 1; each p_i is calibrated on representative data. Report rankings + intervals/uncertainty or only qualitative ordering until trained/calibrated data exist. Avoid conflating model confidence and measurement quality. Urgency/referral separately determined. Unknown/not-tested pathways carry `NOT_ASSESSED`, not zero probability.

**Example logical safeguards:**
- Acuity low and focus helps: refractive impairment candidate increases, but does NOT erase independent cataract/retina signs.
- Cataract obscures retina: cataract may be positive, DR is NOT_ASSESSED; diabetes triggers retinal referral.
- Red eye with pain/photophobia: urgent ocular evaluation, not 'conjunctivitis 97%'.
- Normal Amsler/acuity + diabetes: still fundus imaging.
- Abnormal perimetry but normal fundus: test reliability and non-glaucomatous defects examined; no false exclusive diagnosis.
- Both eyes may have different conditions; cross-eye symmetry is supportive only, never an identity substitute.

## I5. Runtime state machine with timeouts and recovery
```
NEW -> ID_VERIFIED -> TRIAGED -> DEVICE_READY -> PATIENT_CALIBRATED -> TEST_ACTIVE
 -> MEASUREMENT_VALID -> TARGETED_MORE_EVIDENCE -> INTERPRETED -> CLINICIAN_REVIEWED -> LOCKED_EXPORT

Failed node -> CAUSE_DETECTED -> CAUSE_SPECIFIC_CORRECTION -> FRESH_VALIDATION -> return to node.
Cannot recover within prescribed attempts -> ALTERNATIVE_REFERENCE_EXAM (not a forced disease score).
Any state + emergency -> HALT -> EMERGENCY_PROTOCOL (preserve prior data, stop stimulus).
```
Do not count a failed read as a clinically normal eye. A `RETRY` must include causal correction and a genuinely new independent sample, not reusing same frame. A model can abstain, but software must preferentially guide acquisition where possible. Preserve original trials and reason for each rejection. The same finding may be supported by multiple tests but correlated inputs must not be double-counted.

## I6. Integration tests: traceability matrix
| Requirement | Simulated test | Bench/human feasibility test | Clinical reference |
|---|---|---|---|
| Optical angle | Given distorted map, verify arcminute target math | Optical camera viewed through eyepiece at known eye relief | ETDRS acuity |
| Visual field | 54-point angular visibility/brightness tests; stimulus/response clock-mapping and no-response state tests | Real per-eye exposure grid through the headset plus patient touch-response on the paired phone; measure input latency, clock uncertainty, false taps, missed taps and disconnect behavior | Humphrey SAP, stage-specific; no equivalence assumed |
| Near-focus | Given lens index -> true vergence, monotonicity | Measure virtual conjugates at every indexed position | Corrected 40 cm near vision/add |
| Colour vision | Channel gamma and colour transform tests | Spectroradiometric/photometric headset reading | Validated chromatic screen |
| Anterior eye | Pose/crop/lighting/left-right fault injection | Standard eye phantom / repeat operators | Slit lamp masked reader |
| Dry eye | Blink/no-blink, absent rings, motion model tests | Tear-film setup with artificial/clinical controlled eye | TFOS clinical NIBUT and symptoms |
| Retinal images | Coverage, disc/macula geometry, lesion size and blur tests | Artificial retina; approved illuminated optical configuration | Graded DR/AMD/optic disc, OCT if clinically indicated |
| Strabismus | Repeated intermittent deviation simulated | Eye-alignment phantom plus cover-video | Prism-cover tests |
| Amblyopia | Crowding/eye difference property checks | Age/cooperation pilot | Corrected crowded acuity/cycloplegic refraction |
| Two phones | Race/packet loss/reconnect/session swap | Actual phone offline/hotspot flight | No direct clinical standard; must be deterministic |
| Multimorbidity | Co-occurring labels/competing pathways test suite | Dual-disease image/pathology phantoms | Clinical expert multi-disease adjudication |
| Safety | LED stuck-on, low battery, UI stop, app crash | Thermal/electrical/optical exposure | External safety review |

**Independent reference:** Mask clinicians to prototype scores where appropriate; avoid spectrum bias. All screen-negative patients must undergo adequate reference testing or appropriately justified random verification that supports bias correction; no cases cherry-picked based on image quality. Also report referral and safety errors when no label is available.

## I7. Fault mode inventory and coverage, using correct FMEA concepts
FMEA severity, occurrence, detectability scores require observed/justified inputs; **do not invent RPN values before prototypes**. The baseline risk register should list risk IDs and controls with `severity=TBD`, `occurrence=TBD`, `detectability=TBD`, owner, gate and test evidence. Risk controls follow sequence: safer design first, protective safeguards second, instructions last. Patient instructions alone are not a sufficient fix for a severe preventable hazard.

**Hardware risks:** H01 eye-safe light and timeout; H02 facial pressure/contact; H03 broken lens/mount; H04 battery thermal/electrical; H05 poor IPD/eye relief; H06 optical shift/cassette mismatch; H07 screen FOV/MTF/colour/luminance; H08 selfie camera occluded; H09 ungradable retina and missed fields; H10 contamination; H11 charging/ground loop; H12 incompatible phone camera/adapter; H13 miscalibrated optical conjugates.

**Software risks:** S01 wrong patient/OD-OS; S02 stimulus frame-timing or cross-phone response-time misalignment; S03 response-phone touch failure, accidental input or local-Wi-Fi dropout; S04 network event replay; S06 incorrect disease probability sum constraint; S07 confounded conditions; S08 invalid model/device combination; S09 domain shift; S10 sample leakage; S11 image AI overprocessing; S12 unverified auto-download licence; S13 off-screen/too-small stimuli; S14 untraceable manual override; S15 DB crash/partial commit; S16 unreliable fixation; S17 UI coaching; S18 retries until favourable score; S19 age/language bias; S20 emergency signs omitted; S21 mislabeled images; S22 expired profile; S23 clinical model self-modifies; S24 export misinformation; S25 data consent breach.

**Hard physical gaps not resolvable by AI:** retina structures absent from optics; 6/6 details below angular resolution; no IOP without sensor; no OCT-like thickness from colour fundus. These remain scope-limited and require actual alternative examinations. This is not a 'disclaimer workaround', it is enforcing valid measurement physics.

## I8. Four-level calibration plan
1. **Factory optical and camera characterisation:** signed per-phone and per-attachment geometry, luminance, colour and camera intrinsic maps. Use real measurement apparatus.
2. **Daily bench/operation check:** no damaged optic, compatible phone/app, no stuck LED, clean optics, response-phone touchscreen and haptic check passed, local-Wi-Fi pairing/heartbeat and clock-sync check passed, stable temperature.
3. **Patient-specific fit:** physical IPD, eye relief and spectacle status; fixation/blink comprehension; fit map hash.
4. **Run-time:** missed stimulus frames, response-touch event quality, clock offset/uncertainty, response-window completion, gaze/fixation quality, image focus/field/colour and movement; rescope only impacted measurements.

Calibration must not depend exclusively on subjective patient perception when disease can invalidate their answer. Mechanical and optical reference standards precede patient fine-tuning. Document measured values and uncertainty with tolerance; do not accept arbitrary constants. Quantify lens/camera/drift error propagation, including high myopia and accommodation and cataract-induced retinal opacity.

## I9. Human-use and performance validation roadmap
- **Bench, no patient:** electrical checks, artificial eyes, display optical calibration, printed targets, mock API/battery/connection faults.
- **Healthy adult usability:** approved ethics/consent where required; comfort, response tutorials, inter-operator repeatability, re-seating and spectacle variants. Do not expose eye to unverified retinal light.
- **Clinical pilot:** each modality's agreement with reference; tune acquisition and thresholds using *development* cohort only.
- **External validation:** new clinic/community sites/operators and phone batches; fixed frozen software and models, independently graded reference; image failure included in denominator.
- **Prospective intended-use study:** screening programme with random/unselected people, age groups and multi-disease prevalence; assess safety, diagnostic yields, per-condition sensitivity/specificity with CI, PPV at prevalence, false referrals, test time and re-screen rate.
- **Superiority comparison:** head-to-head, matched participants, prespecified standard comparator and noninferiority/superiority margin; powered sample size based on expected difference and disease prevalence. Avoid labelling technical proof as clinical equivalence.

**Reference examples:** ETDRS chart, clinical refraction, clinical tear film evaluation, slit lamp, standard automated perimetry, dilated retinal examination/fundus grading and OCT where indicated; cover/prism/paediatric cycloplegic examination. Complete reference workup can be different per endpoint; the reference itself must be adequate to support negative labels.

**Example 95% CI calculation:** For sensitivity p=0.9 with desired normal-approx half width 0.05, n_positive approx (1.96^2*0.9*0.1)/(0.05^2) = 138.3 affected individuals per independent condition before losses, subgroup precision and clustering; multi-condition clinical trials require more. This is an illustration, not a powered trial plan.

## I10. Non-functional performance and experience gates
- Load entire app offline; no server login needed. Eye-camera capture is local. Patient stop is available on the response phone over the paired link, with an independent local watchdog on the stimulus phone and physical headset quick-release; loss of the link itself halts stimuli.
- Support 2 phone roles and pinned profiles; cold start, idle RAM, on-device inference latency, battery, thermal drift measured on both actual phones. No unconditional target timing guarantee until benchmarking.
- Clinical workflow shall preserve completed trials after process death or network interruption; no partial record represented complete.
- Every unit has operator calibration UI, patient task UI, guided camera UI, report UI and export. All transitions unit/instrumented tested on target device.
- User should not have to manipulate tiny optics between every test; progressive optical setting or separate central insert only if repeatable and operator-manageable. Automated instructions avoid guessing.
- Medical forms and image reports must include units, eye laterality, captured modality, validated scope, and provenance (source image/evidence). Never generate a ranking from absent data as a probability.

## I11. Stop/go acceptance criteria
**Stop** if optical exposure unverified, angular resolution insufficient for claimed acuity, incomplete required field geometry, untrusted device profile, unsafe illumination, inaccurate eye identity, absent evidence for a claimed negative, or clinical dataset access/licence invalid. **Correct** root cause in same phase; log test and regression; do not advance to new phase. **Go** only when all relevant automated gates and physical evidence pass, baseline hash/tree updated, tests rerun and independent audit verifies state.

## I12. External references and validation literature
- Google Cardboard 80deg design and viewer profile: https://support.google.com/cardboard/manufacturers/answer/6323398
- Portable perimetry meta-analysis: https://pubmed.ncbi.nlm.nih.gov/42563154/
- India diabetic retinal screening consensus: https://pmc.ncbi.nlm.nih.gov/articles/PMC7942107/
- TFOS DEWS II diagnostic sequence: https://tfosdewsreport.org/report-diagnostic_methodology/131_36/en/
- SLID multi-disease anterior images, patient split performance: https://pmc.ncbi.nlm.nih.gov/articles/PMC12832839/
- ISO 15004-2:2024 optical exposure: https://www.iso.org/cms/live/live/en/sites/isoorg/contents/data/standard/07/99/79919.html
- India medical-device software guidance (21 July 2026): https://www.cdsco.gov.in/opencms/export/sites/CDSCO_WEB/Pdf-documents/Guidance-document-on-Medical-Device-Software-under-MDR-2017.pdf
- Open source GMLP AI risk principles: https://www.fda.gov/medical-devices/software-medical-device-samd/good-machine-learning-practice-medical-device-development-guiding-principles
