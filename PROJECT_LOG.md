# EYEATLAS VR - PROJECT LOG
**Created:** 2026-11-19 | **Current gate:** P00 PASSED | **Current accepted phase:** P00

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
**Git commit SHA/tag:** bfd0a36 (tag: v0.0.0-p00)
**Code dump hash:** `9322d9d0aaa8d63dfd7c45e0b60a4d974849cf531722225f90833dac8958a56f`
**Next approved action:** P00-B independent adversarial audit.
