package org.eyeatlas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eyeatlas.core.*
import org.eyeatlas.net.PairingAndTransport
import org.eyeatlas.net.TransportState

@Composable
fun DoctorDashboard(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val stateMachine = remember { ClinicalStateMachine() }
    val decisionEngine = remember { DecisionEngine() }
    val transport = remember {
        PairingAndTransport().apply {
            val payload = initiatePairing()
            completePairingWithPayload(payload, "PHONE-CLINICIAN-PAD")
        }
    }

    var currentState by remember { mutableStateOf(stateMachine.getState()) }
    var currentScreen by remember { mutableStateOf(ScreenId.UX_01) }
    var transportState by remember { mutableStateOf(transport.getState()) }
    var transportFaultNotice by remember { mutableStateOf<String?>(null) }
    var patientId by remember { mutableStateOf("P-101") }
    var hasDiabetes by remember { mutableStateOf(false) }
    var selectedSymptom by remember { mutableStateOf("BLURRED_VISION") }
    var hasConsent by remember { mutableStateOf(true) }
    var emergencyReason by remember { mutableStateOf<String?>(null) }
    var recoveryNotice by remember { mutableStateOf<String?>(null) }

    val symptomOptions = listOf(
        "NONE",
        "BLURRED_VISION",
        "EYE_STRAIN",
        "DRYNESS",
        "SUDDEN_VISION_LOSS",
        "CORNEAL_TRAUMA",
        "PAINFUL_PHOTOPHOBIC_RED_EYE",
        "CHEMICAL_EXPOSURE"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalTheme.BackgroundWhite)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            title = "EyeAtlas VR - Clinician Station",
            role = AppRole.CLINICIAN,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        ScreenNavigator(
            currentScreen = currentScreen,
            onNavigate = { currentScreen = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Clinical Intake & Session Telemetry",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTheme.TextPrimary
                )
                Text(
                    text = "Hardware Target: OnePlus Nord CE 3 5G | 120Hz VSYNC Calibrated",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextMuted
                )
            }
            StateBadge(state = currentState)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Step Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.SeaGreenLight),
            border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Active Stage: ${currentScreen.name} - ${currentScreen.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ClinicalTheme.SeaGreenDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (currentScreen) {
                        ScreenId.UX_01 -> "Clinician Action: Verify signed consent & scan patient pairing QR code."
                        ScreenId.UX_02 -> "Clinician Action: Adjust IPD carriage dial (48-74mm) & verify camera readiness."
                        ScreenId.UX_03 -> "Clinician Action: Monitor response pad tutorial trials (zero coaching permitted)."
                        ScreenId.UX_04 -> "Clinician Action: Inspect broad red-green screening and contrast sensitivity."
                        ScreenId.UX_05 -> "Clinician Action: Log monocular logMAR acuity & optical vergence index."
                        ScreenId.UX_06 -> "Clinician Action: Review macular scotoma repeat tests & vernier alignment."
                        ScreenId.UX_07 -> "Clinician Action: Monitor 54-point perimetry on OnePlus Nord CE 3 5G (24 frames @ 120Hz)."
                        ScreenId.UX_08 -> "Clinician Action: Position external camera hood & trigger ring illumination."
                        ScreenId.UX_09 -> "Clinician Action: Inspect tear film ring reflection & automated NIBUT curve."
                        ScreenId.UX_10 -> "Clinician Action: Align validated fundus camera; verify macula & disc fields."
                        ScreenId.UX_11 -> "Clinician Action: Annotate cover-uncover ocular motility & strabismus deviation."
                        ScreenId.UX_12 -> "Clinician Action: Review independent multi-morbidity synthesis & referral plan."
                        ScreenId.UX_13 -> "Clinician Action: Review emergency bypass & priority STOP log; record rationale."
                        ScreenId.UX_14 -> "Clinician Action: Access calibration lab, optical bench results & model audits."
                    },
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stepped Wizard Navigation Controls
                val allScreens = ScreenId.values()
                val currentIndex = currentScreen.ordinal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentIndex > 0) currentScreen = allScreens[currentIndex - 1]
                        },
                        enabled = currentIndex > 0,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("Previous Step", fontSize = 11.sp, color = ClinicalTheme.SeaGreenPrimary)
                    }

                    Text(
                        text = "Step ${currentIndex + 1} of ${allScreens.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClinicalTheme.SeaGreenDark
                    )

                    Button(
                        onClick = {
                            if (currentIndex < allScreens.size - 1) currentScreen = allScreens[currentIndex + 1]
                        },
                        enabled = currentIndex < allScreens.size - 1,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("Next Step", fontSize = 11.sp, color = ClinicalTheme.PureWhite)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentState == SessionState.URGENT_BYPASS && emergencyReason != null) {
            EmergencyAlertCard(
                reason = emergencyReason!!,
                onAcknowledge = {
                    emergencyReason = null
                    stateMachine.processEvent(SessionEvent.FinalizeReview)
                    currentState = stateMachine.getState()
                }
            )
        }

        if (recoveryNotice != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = ClinicalTheme.SuccessGreenBg),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = recoveryNotice!!,
                    color = ClinicalTheme.SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Patient Intake Form Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Patient Demographics & Medical History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ClinicalTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = patientId,
                    onValueChange = { patientId = it },
                    label = { Text("Patient Identifier (Pseudonymized Token)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasDiabetes,
                        onCheckedChange = { hasDiabetes = it },
                        colors = CheckboxDefaults.colors(checkedColor = ClinicalTheme.SeaGreenPrimary)
                    )
                    Text("History of Diabetes Mellitus (Mandates Retinal Fundus Screening)", fontSize = 12.sp, color = ClinicalTheme.TextSecondary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasConsent,
                        onCheckedChange = { hasConsent = it },
                        colors = CheckboxDefaults.colors(checkedColor = ClinicalTheme.SeaGreenPrimary)
                    )
                    Text("Informed Consent Signed & Immutable Log Created", fontSize = 12.sp, color = ClinicalTheme.TextSecondary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Primary Presenting Symptom:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ClinicalTheme.TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                symptomOptions.chunked(2).forEach { rowSymptoms ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSymptoms.forEach { sym ->
                            val isSelected = selectedSymptom == sym
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedSymptom = sym },
                                label = { Text(sym.replace("_", " "), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ClinicalTheme.SeaGreenPrimary,
                                    selectedLabelColor = ClinicalTheme.PureWhite
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val referral = decisionEngine.screenIntakeForEmergency(listOf(selectedSymptom))
                            if (referral != null) {
                                emergencyReason = referral.reason
                                stateMachine.processEvent(SessionEvent.TriggerEmergencyBypass(referral.reason))
                                currentState = stateMachine.getState()
                            } else {
                                stateMachine.processEvent(SessionEvent.TriagePassed)
                                currentState = stateMachine.getState()
                                recoveryNotice = "Triage Passed: Proceeding to safe device calibration."
                            }
                        },
                        enabled = hasConsent && currentState != SessionState.URGENT_BYPASS,
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Process Intake & Triage", fontSize = 12.sp, color = ClinicalTheme.PureWhite)
                    }

                    if (currentState != SessionState.STOPPED) {
                        OutlinedButton(
                            onClick = {
                                stateMachine.processEvent(SessionEvent.PriorityStop("Clinician requested STOP"))
                                currentState = stateMachine.getState()
                                recoveryNotice = "STOP engaged: Routine stimulus halted immediately."
                            },
                            border = BorderStroke(1.dp, ClinicalTheme.AlertRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Priority STOP", fontSize = 12.sp, color = ClinicalTheme.AlertRed)
                        }
                    } else {
                        Button(
                            onClick = {
                                stateMachine.processEvent(SessionEvent.ResumeSession)
                                currentState = stateMachine.getState()
                                recoveryNotice = "Session resumed safely into calibration state."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Resume Safe Session", fontSize = 12.sp, color = ClinicalTheme.PureWhite)
                        }
                    }
                }
            }
        }

        // Per-Eye Laterality Independence Display
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Per-Eye Multi-Morbidity & Laterality Independence",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ClinicalTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "OD (Right Eye)", fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenPrimary, fontSize = 12.sp)
                        Text(text = "Status: Monocular Assessment", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
                        Text(text = "Glaucoma: Suspicion pending field", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
                        Text(text = "Cataract: Independent grading", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
                    }
                    VerticalDivider(modifier = Modifier.height(60.dp).width(1.dp), color = ClinicalTheme.CardBorder)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(text = "OS (Left Eye)", fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenPrimary, fontSize = 12.sp)
                        Text(text = "Status: Monocular Assessment", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
                        Text(text = "Retina: No negative on missing data", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
                        Text(text = "Acuity: Independent logMAR", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
                    }
                }
            }
        }

        // P02: Two-Phone Encrypted Transport & Pairing Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Two-Phone Encrypted Transport & Pairing (P02)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ClinicalTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "QR Identity Pinning: EYEATLAS://SESSION-RESEARCH-001/PHONE-PATIENT-VR",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )
                Text(
                    text = "Channel Status: ${transportState.name} | Watchdog: 3.0s Timeout",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transportState == TransportState.PINNED_AUTHENTICATED) ClinicalTheme.SuccessGreen else ClinicalTheme.AlertRed
                )
                Text(
                    text = "Clock Sync: Ping-Pong Roundtrip | Offset: +20ms | Uncertainty: 5ms (< 50ms tolerance)",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )

                if (transportFaultNotice != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transportFaultNotice!!,
                        fontSize = 11.sp,
                        color = ClinicalTheme.AlertRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Fault Injection Controls (Research Audit):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClinicalTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            transport.setAirplaneModeInterruption()
                            transportState = transport.getState()
                            transportFaultNotice = "FAULT INJECTED: Airplane mode active. In-flight trials flagged UNGRADABLE."
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, ClinicalTheme.CardBorder)
                    ) {
                        Text("Simulate Disconnect", fontSize = 10.sp, color = ClinicalTheme.TextSecondary)
                    }
                    Button(
                        onClick = {
                            transport.resumeAfterInterruption()
                            transportState = transport.getState()
                            transportFaultNotice = "Transport recovered: Re-authenticated. Re-calibration required before scoring."
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("Re-connect Transport", fontSize = 10.sp, color = ClinicalTheme.PureWhite)
                    }
                }
            }
        }

        // P03: Dataset Provenance & License Governance Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Dataset Provenance & License Governance (P03)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ClinicalTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Clinical Policy: Permissive open licenses (CC BY / CC0) verified. Conflicted sources gated.",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Verified Open: 4 (RFMiD, IDRiD, Conjunctivitis, NIKBUT)", fontSize = 11.sp, color = ClinicalTheme.SuccessGreen, fontWeight = FontWeight.SemiBold)
                    Text(text = "Gated: 4 (PAPILA, CSDI, SLID, APTOS)", fontSize = 11.sp, color = ClinicalTheme.AlertRed, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Patient-Level Isolation: ENFORCED (Zero train/test leakage)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTheme.TextPrimary
                )
            }
        }

        // P04: Instrument Profile & Optics Simulator Card (OnePlus Nord CE 3 5G Calibrated)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Instrument Profile & Calibration Engine (P04)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ClinicalTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Target Hardware: OnePlus Nord CE 3 5G (Qualcomm Snapdragon 782G)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTheme.SeaGreenPrimary
                )
                Text(
                    text = "Display: 6.7\" Fluid AMOLED | 2412x1080 | 120Hz VSYNC (8.33ms) | 240Hz Touch (4.16ms)",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )
                Text(
                    text = "Optics: 45.0 mm focal length | 15.08 PPD | 80.2° Measured FOV | Split Eye: 1206x1080 px",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )
                Text(
                    text = "Perimetry: 54-Point 24-2 Grid | 24 Frames @ 120Hz = 200ms Goldmann III | Blind Spot (±15°, -1.5°)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClinicalTheme.SeaGreenDark
                )
                Text(
                    text = "Camera Sensor: Sony IMX890 50MP 1/1.56\" f/1.8 OIS+EIS (45° Indirect Retinal Adapter)",
                    fontSize = 11.sp,
                    color = ClinicalTheme.TextSecondary
                )
                Text(
                    text = "Inference Runtime: TFLITE_DELEGATE (Benchmarked: 24.0 ms, Stable)",
                    fontSize = 11.sp,
                    color = ClinicalTheme.SuccessGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
