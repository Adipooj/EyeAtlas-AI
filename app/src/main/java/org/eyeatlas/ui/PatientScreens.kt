package org.eyeatlas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eyeatlas.core.SessionState
import org.eyeatlas.net.RemoteClientState
import org.eyeatlas.net.RemoteSessionClient
import org.eyeatlas.sensors.Calibration
import org.eyeatlas.sensors.ResponseAndGaze
import org.eyeatlas.sensors.VrPresenter

@Composable
fun PatientScreen(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val calibration = remember {
        Calibration().apply {
            val profile = Calibration.createOnePlusNordCe3Profile()
            loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        }
    }
    val presenter = remember { VrPresenter(calibration).withNordCe3Layout() }
    val responseEngine = remember { ResponseAndGaze() }
    val remoteClient = remember { RemoteSessionClient(sessionId = "SES-REM-LIVE-01") }

    var currentScreen by remember { mutableStateOf(ScreenId.UX_07) }
    var isEmergencyHalted by remember { mutableStateOf(false) }
    var pairingPin by remember { mutableStateOf("839201") }
    var isOnlineMode by remember { mutableStateOf(true) }
    var activeInputSource by remember { mutableStateOf("Bluetooth Clicker / Volume Keys") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalTheme.BackgroundWhite)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppHeader(
                title = "EyeAtlas VR - Patient VR View",
                role = AppRole.PATIENT,
                onToggleRole = onRoleChange
            )
            ResearchNotice()

            // Online Session Telemetry & Input Channel Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOnlineMode) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                ),
                border = BorderStroke(1.dp, if (isOnlineMode) Color(0xFF86EFAC) else Color(0xFFCBD5E1)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOnlineMode) "ONLINE SESSION: $pairingPin" else "LOCAL WI-FI MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnlineMode) Color(0xFF15803D) else Color(0xFF475569)
                    )
                    Text(
                        text = "Input: $activeInputSource",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155)
                    )
                }
            }

            ScreenNavigator(
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it }
            )
        }

        if (isEmergencyHalted) {
            EmergencyAlertCard(
                reason = "Patient triggered emergency quick-release STOP.",
                onAcknowledge = {
                    isEmergencyHalted = false
                    remoteClient.clearEmergencyHalt()
                }
            )
        }

        // Central VR Optical Viewport (Dark-adapted photopic/mesopic field)
        Card(
            modifier = Modifier
                .size(240.dp)
                .padding(8.dp),
            shape = CircleShape,
            border = BorderStroke(3.dp, if (isEmergencyHalted) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenPrimary),
            colors = CardDefaults.cardColors(
                containerColor = if (isEmergencyHalted) Color(0xFF4A0000) else Color(0xFF0F172A)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isEmergencyHalted) {
                    Text(text = "STOP", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                } else {
                    when (currentScreen) {
                        ScreenId.UX_01 -> Text(text = "PAIR PIN:\n$pairingPin\nScan QR to Join", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_02 -> Text(text = "FIT HEADSET\nCentral Fixation Dot", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_03 -> Text(text = "PRACTICE TRIAL\nTap Clicker or Pad Once", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_04 -> Text(text = "COLOUR PLATE\nRed / Green", color = Color(0xFFFDE047), fontSize = 16.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_05 -> Text(text = "E", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
                        ScreenId.UX_06 -> Text(text = "VERNIER\n| |", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_07 -> Text(text = "+", color = Color(0xFF38BDF8), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        ScreenId.UX_08 -> Text(text = "LOOK STRAIGHT\nAnterior Camera", color = Color(0xFF86EFAC), fontSize = 14.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_09 -> Text(text = "BLINK NATURALLY\n3... 2... 1...", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_10 -> Text(text = "+\nCentral Target", color = Color(0xFF4ADE80), fontSize = 22.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_11 -> Text(text = "FOLLOW TARGET\nLateral Alignment", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_12 -> Text(text = "EXAM COMPLETE\nRemove Headset", color = Color(0xFFA7F3D0), fontSize = 15.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_13 -> Text(text = "DISCOMFORT STOP\nHalt Available", color = Color(0xFFFCA5A5), fontSize = 15.sp, textAlign = TextAlign.Center)
                        ScreenId.UX_14 -> Text(text = "BENCH LAB\nStandby", color = Color.Gray, fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Bottom Clinical Information & Audio Guidance Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stage: ${currentScreen.name} - ${currentScreen.title}",
                        color = ClinicalTheme.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = ClinicalTheme.SeaGreenLight,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Calibrated 120Hz VSYNC",
                            color = ClinicalTheme.SeaGreenDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentScreen.description,
                    color = ClinicalTheme.TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Spoken Audio Guidance Cue for visually impaired / cataract patients
                val audioPrompt = when (currentScreen) {
                    ScreenId.UX_01 -> "Audio Guidance: Please hold your response clicker or phone pad while pairing completes."
                    ScreenId.UX_02 -> "Audio Guidance: Adjust the headset strap and pupil carriage dial until the center mark is clear."
                    ScreenId.UX_03 -> "Audio Guidance: Practice trial. When you feel ready, click your response button once."
                    ScreenId.UX_04 -> "Audio Guidance: Look at the circle. Press button if you distinguish a colored shape."
                    ScreenId.UX_05 -> "Audio Guidance: Letters will appear. Use directional keys to indicate the opening direction."
                    ScreenId.UX_06 -> "Audio Guidance: Look at the vertical lines. Notice if the center line shifts."
                    ScreenId.UX_07 -> "Audio Guidance: Keep your gaze on the central cross (+). Tap button whenever you see a spot flash."
                    ScreenId.UX_08 -> "Audio Guidance: Please look straight ahead with wide open eyes for front image."
                    ScreenId.UX_09 -> "Audio Guidance: Blink naturally three times, then keep eyes open for tear film scan."
                    ScreenId.UX_10 -> "Audio Guidance: Fixate on the central target inside the viewer."
                    ScreenId.UX_11 -> "Audio Guidance: Follow the moving target with both eyes without turning your head."
                    ScreenId.UX_12 -> "Audio Guidance: All screening tasks are complete. You may now remove the headset."
                    ScreenId.UX_13 -> "Audio Guidance: Examination stopped. Please take a moment to rest."
                    ScreenId.UX_14 -> "Audio Guidance: System diagnostic and calibration standby."
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = ClinicalTheme.SeaGreenLight),
                    border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder)
                ) {
                    Text(
                        text = audioPrompt,
                        color = ClinicalTheme.SeaGreenDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isEmergencyHalted = !isEmergencyHalted
                            if (isEmergencyHalted) {
                                remoteClient.triggerEmergencyStop("Patient pressed quick-release STOP")
                            } else {
                                remoteClient.clearEmergencyHalt()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEmergencyHalted) ClinicalTheme.SeaGreenPrimary else ClinicalTheme.AlertRed
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isEmergencyHalted) "Resume Examination" else "Emergency STOP / Quick Release",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
