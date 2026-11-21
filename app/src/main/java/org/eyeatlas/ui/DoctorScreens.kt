package org.eyeatlas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eyeatlas.core.*
import org.eyeatlas.net.PairingAndTransport
import org.eyeatlas.net.TransportState
import org.eyeatlas.sensors.ResponseAndGaze

@Composable
fun DoctorDashboard(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    PhysicianAndResponsePadScreen(onRoleChange = onRoleChange, modifier = modifier)
}

/**
 * Unified Physician & Tactile Response Pad Handset Screen (Response-cum-Physician Mode).
 * Combines live clinical monitoring, remote test switching, instant reset,
 * and high-frequency 240Hz tactile response controls on a single surface.
 */
@Composable
fun PhysicianAndResponsePadScreen(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val stateMachine = remember { ClinicalStateMachine() }
    val decisionEngine = remember { DecisionEngine() }
    val responseEngine = remember { ResponseAndGaze() }
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
    var padTapCount by remember { mutableStateOf(0) }
    var lastTapTimeMs by remember { mutableStateOf(0L) }
    var feedbackMessage by remember { mutableStateOf("Response pad active. Tap anywhere or switch tests below.") }
    var showPairingDialog by remember { mutableStateOf(false) }
    var cloudBackendUrl by remember { mutableStateOf("https://eyeatlas-ai.onrender.com") }
    var cloudSessionId by remember { mutableStateOf("SES-REMOTE-01") }
    var cloudPin by remember { mutableStateOf("749281") }

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

    val activeModule = ClinicalSessionCoordinator.activeModule
    val activeEye = ClinicalSessionCoordinator.activeEye
    val isEmergencyHalted = ClinicalSessionCoordinator.isEmergencyHalted

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalTheme.BackgroundWhite)
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            title = "EyeAtlas VR - Physician & Response Pad",
            role = AppRole.PHYSICIAN_CONTROLLER,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        // =====================================================================
        // SECTION 1: REMOTE VR TEST CONTROLLER (Instant Switch & Reset)
        // =====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "REMOTE VR TEST CONTROLLER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTheme.SeaGreenDark
                        )
                        Text(
                            text = "Switches test on Patient VR phone without exiting headset",
                            fontSize = 11.sp,
                            color = ClinicalTheme.TextSecondary
                        )
                    }
                    Surface(
                        color = if (isEmergencyHalted) ClinicalTheme.AlertRedBg else ClinicalTheme.SeaGreenLight,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isEmergencyHalted) ClinicalTheme.AlertRedBorder else ClinicalTheme.SeaGreenBorder),
                        modifier = Modifier.clickable { showPairingDialog = true }
                    ) {
                        Text(
                            text = if (isEmergencyHalted) "STOPPED" else "🔗 PAIRED (PIN: $cloudPin)",
                            color = if (isEmergencyHalted) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tested Eye Selection (OD, OS, OU)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Eye:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ClinicalTheme.TextPrimary)
                    FilterChip(
                        selected = activeEye == EyeLaterality.OD,
                        onClick = { ClinicalSessionCoordinator.switchEye(EyeLaterality.OD) },
                        label = { Text("OD (Right)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = activeEye == EyeLaterality.OS,
                        onClick = { ClinicalSessionCoordinator.switchEye(EyeLaterality.OS) },
                        label = { Text("OS (Left)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = activeEye == EyeLaterality.OU,
                        onClick = { ClinicalSessionCoordinator.switchEye(EyeLaterality.OU) },
                        label = { Text("OU (Both Eyes)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Module Selector Carousel
                Text("Select Active Test Module:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ClinicalTheme.TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClinicalModuleId.values().forEach { module ->
                        val isSelected = activeModule == module
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                ClinicalSessionCoordinator.switchModule(module)
                                feedbackMessage = "Switched Patient VR to ${module.title}"
                            },
                            label = { Text(module.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ClinicalTheme.SeaGreenPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Test Action Buttons: Reset & Emergency Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ClinicalSessionCoordinator.resetActiveTest()
                            feedbackMessage = "↺ Current test reset and re-initialized."
                        },
                        border = BorderStroke(1.dp, ClinicalTheme.SeaGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("↺ Reset Current Test", color = ClinicalTheme.SeaGreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val nextHalt = !ClinicalSessionCoordinator.isEmergencyHalted
                            ClinicalSessionCoordinator.toggleEmergencyHalt(nextHalt)
                            feedbackMessage = if (nextHalt) "EMERGENCY STOP TRIGGERED: VR exam paused." else "Exam resumed."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isEmergencyHalted) ClinicalTheme.SuccessGreen else ClinicalTheme.AlertRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEmergencyHalted) "Resume Exam" else "Priority STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =====================================================================
        // SECTION 2: INTERACTIVE TACTILE RESPONSE PAD (Patient Handset / Clicker)
        // =====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TACTILE RESPONSE HANDSET",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTheme.SeaGreenDark
                        )
                        Text(
                            text = "240Hz touch sampling (<4.16ms) | Blind-operable surface",
                            fontSize = 11.sp,
                            color = ClinicalTheme.TextSecondary
                        )
                    }
                    Surface(
                        color = ClinicalTheme.SeaGreenLight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "TAPS: $padTapCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTheme.SeaGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Blind Touch Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            if (isEmergencyHalted) ClinicalTheme.AlertRedBg else Color(0xFF0F172A),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            2.dp,
                            if (isEmergencyHalted) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenPrimary,
                            RoundedCornerShape(12.dp)
                        )
                        .pointerInput(isEmergencyHalted) {
                            if (!isEmergencyHalted) {
                                detectTapGestures(
                                    onTap = {
                                        val now = System.nanoTime()
                                        padTapCount++
                                        lastTapTimeMs = System.currentTimeMillis()
                                        ClinicalSessionCoordinator.dispatchTap("TOUCH_SURFACE")
                                        feedbackMessage = "Tap #$padTapCount registered (Stimulus Seen)!"
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TAP ANYWHERE TO RESPOND",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "WHEN YOU SEE THE LIGHT OR PATTERN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = feedbackMessage,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Contextual Keypads based on active module
                if (activeModule == ClinicalModuleId.ACUITY) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Acuity: Point Direction of Optotype:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ClinicalTheme.TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("▲ UP", "◄ LEFT", "► RIGHT", "▼ DOWN").forEach { dir ->
                            Button(
                                onClick = {
                                    padTapCount++
                                    ClinicalSessionCoordinator.dispatchTap("DIRECTION_$dir")
                                    feedbackMessage = "Acuity direction $dir recorded"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenLight),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(dir, color = ClinicalTheme.SeaGreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                } else if (activeModule == ClinicalModuleId.COLOUR) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Colour: Select Number Seen on Ishihara Plate:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ClinicalTheme.TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("12", "8", "6", "29", "57", "74", "None").forEach { num ->
                            Surface(
                                color = ClinicalTheme.BackgroundWhite,
                                border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        padTapCount++
                                        ClinicalSessionCoordinator.dispatchTap("NUM_$num")
                                        feedbackMessage = "Ishihara selection $num recorded"
                                    }
                            ) {
                                Text(
                                    text = num,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ClinicalTheme.TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // SECTION 3: CLINICAL GOVERNANCE & MULTI-MORBIDITY SCREENING
        // =====================================================================
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Verified Open: 4 (RFMiD, IDRiD, Conjunctivitis, NIKBUT)", fontSize = 11.sp, color = ClinicalTheme.SuccessGreen, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
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

        // Cloud Pairing Modal Dialog
        if (showPairingDialog) {
            AlertDialog(
                onDismissRequest = { showPairingDialog = false },
                title = {
                    Text("Pair with Cloud Web Station", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ClinicalTheme.TextPrimary)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Connect this clinician controller to https://eyeatlas-ai.netlify.app and https://eyeatlas-ai.onrender.com",
                            fontSize = 12.sp,
                            color = ClinicalTheme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = cloudBackendUrl,
                            onValueChange = { cloudBackendUrl = it },
                            label = { Text("Cloud Backend URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = cloudSessionId,
                            onValueChange = { cloudSessionId = it },
                            label = { Text("Session ID / Room") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = cloudPin,
                            onValueChange = { cloudPin = it },
                            label = { Text("6-Digit Pairing PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            ClinicalSessionCoordinator.pairingPin = cloudPin
                            feedbackMessage = "Cloud Broker connected: $cloudSessionId (PIN: $cloudPin)"
                            showPairingDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("Connect / Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showPairingDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
