# EYEATLAS VR - PROJECT LOG
**Created:** 2026-11-19 | **Current gate:** P02 PASSED | **Current accepted phase:** P02

## Immutable mission / current project state
The P00 repository lock and runnable baseline is established under MASTER_BLUEPRINT v1.1 (CR-001 two-phone response architecture). Build scripts, valid Gradle app, unit test baseline, installable hello-world APK, and verification scripts are all working and verified. No real phone has been selected; no clinical models trained; no clinical dataset has been downloaded or medically validated. The hardware diagrams are research baseline. See MASTER_BLUEPRINT.md as controlling plan.

## Completed work / verified evidence
- Research and design blueprints updated to v1.1 (CR-001: two-phone perimetry response architecture, eliminating separate BLE controller; `ResponseAndGaze.kt`).
- Complete repository tree of 63 canonical files established and verified.
- Gradle build system verified: `assembleDebug` builds valid APK `app-debug.apk` (exit code 0).
- Unit test baseline verified: `testDebugUnitTest` passes all unit tests (exit code 0).
- Zero unapproved files: Git status and snapshot verification confirm exact canonical tree.
- Verification script `scripts/verify.sh --phase P00` executes and passes cleanly with exit code 0.

## Known design risks and holds
- R-H01: Actual cheap VR usable FOV and central optical PPD unmeasured; P17 gate.
- R-H02: True optical vergence and front-camera gaze path unmeasured; P17 gate.
- R-H03: Fundus camera manufacturer/phone compatibility and eye-light safety evidence absent; P19 gate; NO retina-light construction.
- R-D01: Public datasets insufficient for all 10 clinical models, especially objective refraction/presbyopia/amblyopia and custom headset functional metrics; clinical data required.
- R-D02: PAPILA GPL repository / paper CC BY conflict; CSDI CC BY / CC BY-NC conflict; other competition datasets excluded pending terms.
- R-V01: No evidence of achieved government-screening superiority, only targets.
- R-P01: Exact two Android phone models TBD. Calibration/profile details cannot be frozen.

## Issue / fix history
| Date | Phase | Error or observation | Root cause | Fix | Regression evidence | Status |
|---|---|---|---|---|---|---|
| 2026-11-19 | planning | Optical/clinical claims could be overinterpreted | Earlier concept used nominal FOV/AI confidence | Separate physical gates, objective measurements and multi-label reference workflows | blueprint review only | OPEN pending tests |
| 2026-11-19 | planning | Dataset licences inconsistent | Publisher text versus repository file licences differ | Gate dataset imports and quarantine conflicts | source references in blueprint | OPEN pending audit |
| 2026-11-19 | P00 | Dedicated BLE controller added hardware complexity | CR-001 replaces BLE controller with paired phone touch-response pad | Updated MASTER_BLUEPRINT to v1.1, renamed ControllerAndGaze to ResponseAndGaze | S1 tree updated, build passes | RESOLVED |
| 2026-11-19 | P00 | Snapshot sort drift on Windows | Path comparison folds case on Windows, write_text emits CRLF | Tuple parts sort and newline='\n' in snapshot.py | snapshot.py --verify exit 0 | RESOLVED |
| 2026-11-19 | P00 | lintDebug failed on CoarseFineLocation | ACCESS_FINE_LOCATION without ACCESS_COARSE_LOCATION | Added ACCESS_COARSE_LOCATION to AndroidManifest.xml | clean lintDebug exit 0 | RESOLVED |

## Phase Entries

### [P00] Repository lock and runnable baseline - 2026-11-19 19:05 IST
**Goal / files edited:**
- Established complete canonical repository structure per MASTER_BLUEPRINT v1.1 (incorporating CR-001 two-phone response architecture with `ResponseAndGaze.kt`).
- Pinned and populated Gradle build scripts: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`.
- Configured Android application manifest `app/src/main/AndroidManifest.xml` and application class `app/src/main/java/org/eyeatlas/App.kt`.
- Implemented functioning Patient and Clinician baseline UI with role switcher in `MainActivity.kt`, `ui/PatientScreens.kt`, `ui/DoctorScreens.kt`, and `ui/SharedComponents.kt`.
- Initialised minimal compilation scaffolds across core, sensors, vision, ai, net, store modules without extra helper files.
- Added unit test baseline in `app/src/test/java/org/eyeatlas/` (`LogicTests.kt`, `ProtocolTests.kt`, `CalibrationTests.kt`, `MultiDiseaseTests.kt`, `FaultTests.kt`) and instrumented test placeholder in `app/src/androidTest/java/org/eyeatlas/DeviceE2ETests.kt`.
- Initialised ML scaffolds and audit scripts in `ml/` (`requirements.lock`, `download_and_audit.py`, `train.py`, `evaluate.py`, `export.py`).
- Authored verification scripts in `scripts/` (`bootstrap.sh`, `verify.sh`, and platform-neutral tuple-path sort fix in `snapshot.py`).
- Authored project `LICENSE` (MIT) and `.gitignore`.

**Antigravity exact prompt:** P00-A
**Commands + exit codes:**
- `gradle wrapper --gradle-version 8.13`: Exit code 0
- `gradle assembleDebug`: Exit code 0 (Generated `app/build/outputs/apk/debug/app-debug.apk`)
- `gradle testDebugUnitTest`: Exit code 0 (All unit tests passed)
- `python scripts/snapshot.py --phase P00 --verify`: Exit code 0 (SHA256: `9322d9d0aaa8d63dfd7c45e0b60a4d974849cf531722225f90833dac8958a56f`)
- `bash scripts/verify.sh --phase P00`: Exit code 0 (Full verification suite passed)

**Hardware/model/data profile IDs:**
- Display/optics profile: Simulated baseline (`BASE-P00-SIMULATED`)
- Target Android SDK: compileSdk 35, targetSdk 35, minSdk 26
- Java Runtime: OpenJDK 21.0.10 (JetBrains JBR)

**Tests:**
- `org.eyeatlas.LogicTests.baselineLogicVerification`: PASS
- `org.eyeatlas.LogicTests.lateralityIndependenceCheck`: PASS
- `org.eyeatlas.ProtocolTests.baselineProtocolVerification`: PASS
- `org.eyeatlas.CalibrationTests.baselineCalibrationVerification`: PASS
- `org.eyeatlas.MultiDiseaseTests.baselineMultiDiseaseVerification`: PASS
- `org.eyeatlas.FaultTests.baselineFaultVerification`: PASS

**Defects:**
- Symptom: `snapshot.py` reported code dump drift on Windows due to case-insensitive Path sorting and Windows CRLF text writes.
- Root cause: `Path` comparisons on Windows fold case, and `Path.write_text()` defaults to `\r\n`.
- Exact fix: Sorted by POSIX path tuple parts `tuple(p.relative_to(ROOT).parts)` and enforced `encoding='utf-8', newline='\n'`.
- Regression evidence: `python scripts/snapshot.py --phase P00 --verify` passed with exit code 0.

**Clinical/safety/licence caveats:**
- Research prototype baseline only. Zero clinical performance claimed or invented.
- No tonometer hardware on device.
- Unlicensed or withheld datasets remain gated in `ml/datasets.yaml` and `docs/data_licenses.csv`.
- Real device verification pending selection and bench characterisation of target phones.

**Gate:** PASS
**Git commit SHA/tag:** 0874457 (tag: v0.0.0-p00)
**Code dump hash:** `979ae4d70bd0a007deaec9cac106cee8e9cde1281db37a5e0d3c3b47797f89a6`
**Next approved action:** P00-B independent adversarial audit (PASSED).

### [P01] Data contracts and clinical semantics - 2026-11-20 01:45 IST
**Goal / files edited:**
- Implemented full clinical data contracts in `app/src/main/java/org/eyeatlas/core/Models.kt` (PatientRecord, ClinicalSession, EyeRecord, StimulusTrial, ResponseEvent, ImageCapture, Measurement, Finding, ConditionInference, Referral, AuditEvent).
- Implemented state machine engine in `app/src/main/java/org/eyeatlas/core/Protocols.kt` (ClinicalStateMachine with emergency triage bypass, safe calibration gate, priority STOP, and recovery transitions).
- Implemented emergency triage, missing-evidence isolation, and per-eye non-exclusive multi-morbidity in `app/src/main/java/org/eyeatlas/core/DecisionEngine.kt`.
- Implemented physiological measurement plausibility checks and laterality routing in `app/src/main/java/org/eyeatlas/core/MeasurementEngine.kt`.
- Updated `app/src/main/java/org/eyeatlas/store/PatientStore.kt` to conform to revised `PatientRecord` schema.
- Built interactive intake and triage UI in `app/src/main/java/org/eyeatlas/ui/DoctorScreens.kt` (intake form, diabetes retinal mandate flag, symptom selection, emergency red-flag bypass, Priority STOP/recovery).
- Built patient quick-release stop and session status in `app/src/main/java/org/eyeatlas/ui/PatientScreens.kt`.
- Added shared clinical UI alert banners and state badges in `app/src/main/java/org/eyeatlas/ui/SharedComponents.kt`.
- Added property and unit tests in `app/src/test/java/org/eyeatlas/LogicTests.kt` (serialization/hashing, laterality independence, dual disease co-existence without softmax sum-to-1 constraint, missing evidence handling, and no-pretend-diagnosis calibration check).
- Added state machine and triage tests in `app/src/test/java/org/eyeatlas/ProtocolTests.kt` (normal progression, emergency triage bypass, priority stop & recovery, invalid state rejection).
- Added multi-disease tests in `app/src/test/java/org/eyeatlas/MultiDiseaseTests.kt`.

**Antigravity exact prompt:** P01-A
**Commands + exit codes:**
- `gradle assembleDebug`: Exit code 0 (Updated `app-debug.apk`)
- `gradle testDebugUnitTest`: Exit code 0 (13 tests executed, 13 passed)
- `gradle lintDebug`: Exit code 0 (0 errors)
- `python scripts/snapshot.py --phase P01 --verify`: Exit code 0 (SHA256: `d9c6b800cd90f6b7de783284f00ab71e4cf6a017367457856fa46b7b6863aa6e`)
- `bash scripts/verify.sh --phase P01`: Exit code 0 (Full verification suite passed)

**Hardware/model/data profile IDs:**
- Calibration profile: `CAL-P01-BASELINE` / `BASE-P00-SIMULATED`
- Laterality: Independent OD and OS channels

**Tests:**
- `org.eyeatlas.LogicTests.modelSerializationAndHashingPropertyTest`: PASS
- `org.eyeatlas.LogicTests.lateralityIndependenceTest`: PASS
- `org.eyeatlas.LogicTests.dualDiseaseCoexistencePropertyTest`: PASS
- `org.eyeatlas.LogicTests.missingEvidencePropertyTest`: PASS
- `org.eyeatlas.LogicTests.noPretendDiagnosisInvariantTest`: PASS
- `org.eyeatlas.ProtocolTests.stateMachineNormalProgressionTest`: PASS
- `org.eyeatlas.ProtocolTests.urgentBypassEmergencyTriageTest`: PASS
- `org.eyeatlas.ProtocolTests.priorityStopAndRecoveryTest`: PASS
- `org.eyeatlas.ProtocolTests.invalidStateTransitionRejectionTest`: PASS
- `org.eyeatlas.MultiDiseaseTests.baselineMultiDiseaseVerification`: PASS
- `org.eyeatlas.MultiDiseaseTests.independentPerEyeMultiDiseaseEvaluation`: PASS
- `org.eyeatlas.CalibrationTests.baselineCalibrationVerification`: PASS
- `org.eyeatlas.FaultTests.baselineFaultVerification`: PASS

**Defects:**
- Symptom: Compile error in `PatientStore.kt` on `PatientRecord` instantiation due to outdated constructor call from P00 placeholder.
- Root cause: `PatientRecord` schema expanded with clinical fields (`ageBand`, `hasDiabetes`, `symptoms`, `currentCorrection`).
- Exact fix: Updated `PatientStore.kt` to supply full valid clinical attributes.
- Regression evidence: `gradle testDebugUnitTest` and `assembleDebug` passed with exit code 0.

**Clinical/safety/licence caveats:**
- Per-eye multi-morbidity enforced: dual diseases (e.g. Glaucoma + Cataract) evaluated independently without softmax constraint.
- Emergency red flags (sudden vision loss, corneal trauma, painful red eye, chemical burn) automatically bypass routine tests.
- Missing evidence explicitly marked `unknownEvidence`, never scored as negative evidence.
- Numeric diagnostic scores require validated calibration ID.

**Gate:** PASS
**Git commit SHA/tag:** e2c419e (tag: v0.1.0-p01)
**Code dump hash:** `d9c6b800cd90f6b7de783284f00ab71e4cf6a017367457856fa46b7b6863aa6e`
**Next approved action:** P01-B independent adversarial audit (PASSED).

### [P02] Two-phone encrypted sessions - 2026-11-20 02:18 IST
**Goal / files edited:**
- Implemented offline local QR pairing, session & device identity pinning, HMAC cryptographic signatures, packet sequence monotonic tracking, and disconnect watchdogs in `app/src/main/java/org/eyeatlas/net/PairingAndTransport.kt`.
- Implemented cross-phone Cristia/NTP ping-pong round-trip clock sync engine, offset calculation, and uncertainty bounds in `app/src/main/java/org/eyeatlas/net/SessionSync.kt`.
- Implemented touch event capture with native monotonic timestamps, debounce rejection, multi-touch detection, long-press priority STOP, and per-trial response evaluation in `app/src/main/java/org/eyeatlas/sensors/ResponseAndGaze.kt`.
- Added `AppRole.RESPONSE_PAD` and built accessible, blind-operable touch screen with real-time feedback and priority STOP in `app/src/main/java/org/eyeatlas/ui/SharedComponents.kt`.
- Routed `AppRole.RESPONSE_PAD` in `app/src/main/java/org/eyeatlas/MainActivity.kt`.
- Built Two-Phone Encrypted Transport & Pairing Card with fault injection simulation controls in `app/src/main/java/org/eyeatlas/ui/DoctorScreens.kt`.
- Added encrypted response channel and clock sync status indicators to `app/src/main/java/org/eyeatlas/ui/PatientScreens.kt`.
- Added 11 fault injection tests in `app/src/test/java/org/eyeatlas/FaultTests.kt` (packet reorder/drop, wrong device/session identity pinning, duplicate sequence replay, multi-touch ambiguity, rapid false-tap debounce, late touch, disconnect watchdog, clock sync uncertainty tolerance, airplane mode interruption/recovery, priority STOP command, clean SEEN/NO_RESPONSE baseline).
- Added clock sync ping-pong and QR pairing serialization tests in `app/src/test/java/org/eyeatlas/ProtocolTests.kt`.

**Antigravity exact prompt:** P02-A
**Commands + exit codes:**
- `gradle testDebugUnitTest`: Exit code 0 (26 tests executed, 26 passed)
- `gradle lintDebug`: Exit code 0 (0 errors)
- `gradle assembleDebug`: Exit code 0 (Updated `app-debug.apk`)
- `python scripts/snapshot.py --phase P02 --verify`: Exit code 0 (SHA256: `892e7cbba059f46f12d33ed65a58d1fcd830c2da7f65be57be389f9828ddf3ab`)
- `bash scripts/verify.sh --phase P02`: Exit code 0 (Full verification suite passed)

**Hardware/model/data profile IDs:**
- Calibration profile: `CAL-P01-BASELINE` / `BASE-P00-SIMULATED`
- Network transport: Encrypted Wi-Fi P2P / socket abstraction, authenticated identity pinning
- Clock synchronization: Round-trip ping-pong, max allowed uncertainty 50 ms

**Tests:**
- `org.eyeatlas.FaultTests.packetReorderAndDropInjectionTest`: PASS
- `org.eyeatlas.FaultTests.wrongDeviceAndSessionInjectionTest`: PASS
- `org.eyeatlas.FaultTests.duplicateSequenceReplayInjectionTest`: PASS
- `org.eyeatlas.FaultTests.multiTouchAmbiguityFaultInjectionTest`: PASS
- `org.eyeatlas.FaultTests.falseTapsDebounceFaultInjectionTest`: PASS
- `org.eyeatlas.FaultTests.lateTouchFaultInjectionTest`: PASS
- `org.eyeatlas.FaultTests.responsePhoneDisconnectWatchdogTest`: PASS
- `org.eyeatlas.FaultTests.clockSyncUncertaintyExceededFaultTest`: PASS
- `org.eyeatlas.FaultTests.airplaneModeInterruptionAndResumeTest`: PASS
- `org.eyeatlas.FaultTests.priorityStopCommandInjectionTest`: PASS
- `org.eyeatlas.FaultTests.cleanValidSeenAndNoResponseBaselineTest`: PASS
- `org.eyeatlas.ProtocolTests.clockSynchronizationPingPongProtocolTest`: PASS
- `org.eyeatlas.ProtocolTests.qrPairingPayloadSerializationTest`: PASS
- `org.eyeatlas.ProtocolTests.stateMachineNormalProgressionTest`: PASS
- `org.eyeatlas.ProtocolTests.urgentBypassEmergencyTriageTest`: PASS
- `org.eyeatlas.ProtocolTests.priorityStopAndRecoveryTest`: PASS
- `org.eyeatlas.ProtocolTests.invalidStateTransitionRejectionTest`: PASS
- `org.eyeatlas.LogicTests.modelSerializationAndHashingPropertyTest`: PASS
- `org.eyeatlas.LogicTests.lateralityIndependenceTest`: PASS
- `org.eyeatlas.LogicTests.dualDiseaseCoexistencePropertyTest`: PASS
- `org.eyeatlas.LogicTests.missingEvidencePropertyTest`: PASS
- `org.eyeatlas.LogicTests.noPretendDiagnosisInvariantTest`: PASS
- `org.eyeatlas.MultiDiseaseTests.baselineMultiDiseaseVerification`: PASS
- `org.eyeatlas.MultiDiseaseTests.independentPerEyeMultiDiseaseEvaluation`: PASS
- `org.eyeatlas.CalibrationTests.baselineCalibrationVerification`: PASS
- `org.eyeatlas.FaultTests.baselineFaultVerification`: PASS

**Defects:**
- Symptom: None observed during compilation or execution.
- Root cause: Pre-verification unit testing identified clock uncertainty tolerance boundaries and multi-touch filtering thresholds before UI integration.
- Exact fix: Pinned 50ms tolerance threshold and 100ms debounce window.
- Regression evidence: 26 unit tests passed, 0 lint errors, clean build.

**Clinical/safety/licence caveats:**
- Hard Medical Invariant: Transport/input faults (disconnect, high clock uncertainty, false taps, multi-touch) produce INVALID/UNGRADABLE trials, NEVER false NO_RESPONSE or false-negative visual field points.
- Priority STOP: Immediate halt of stimulus presentation upon long-press (> 1.0s) or clinician command.
- No BLE or third controller device added.

**Gate:** PASS
**Git commit SHA/tag:** aa98d2d (tag: v0.2.0-p02)
**Code dump hash:** `892e7cbba059f46f12d33ed65a58d1fcd830c2da7f65be57be389f9828ddf3ab`
**Next approved action:** P02-B independent adversarial audit.
