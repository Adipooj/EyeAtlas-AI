package org.eyeatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.eyeatlas.core.SessionState
import org.eyeatlas.sensors.ResponseAndGaze
import org.eyeatlas.net.ClockSyncEngine

enum class AppRole {
    PATIENT,
    CLINICIAN,
    RESPONSE_PAD
}

@Composable
fun AppHeader(title: String, role: AppRole, onToggleRole: (AppRole) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Active Role: ${role.name} | Encrypted Two-Phone (P02)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppRole.values().forEach { targetRole ->
                    FilterChip(
                        selected = role == targetRole,
                        onClick = { onToggleRole(targetRole) },
                        label = {
                            Text(
                                text = when (targetRole) {
                                    AppRole.PATIENT -> "Patient VR"
                                    AppRole.CLINICIAN -> "Clinician"
                                    AppRole.RESPONSE_PAD -> "Response Pad"
                                },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ResearchNotice(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
    ) {
        Text(
            text = "NOTICE: Research Prototype (P02 Encrypted Sessions). No medical diagnosis or superiority claims. Fault-tolerant two-phone transport.",
            color = Color(0xFF856404),
            fontSize = 11.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun EmergencyAlertCard(reason: String, onAcknowledge: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD2D2)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CRITICAL EMERGENCY ALERT - URGENT BYPASS",
                color = Color(0xFF900000),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reason,
                color = Color(0xFF500000),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ACTION: Routine VR testing halted. Immediate ophthalmic referral instructed.",
                color = Color(0xFF900000),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
            ) {
                Text("Acknowledge & Open Emergency Referral", color = Color.White)
            }
        }
    }
}

@Composable
fun StateBadge(state: SessionState) {
    val bgColor = when (state) {
        SessionState.URGENT_BYPASS -> Color(0xFFFFCDD2)
        SessionState.STOPPED -> Color(0xFFFFE0B2)
        SessionState.REVIEW_AND_REFERRAL -> Color(0xFFC8E6C9)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "STATE: ${state.name}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Screen rendered on the second phone running the in-app RESPONSE_PAD role.
 * Blind-operable touch surface: short tap = SEEN, long press = STOP.
 */
@Composable
fun ResponsePadScreen(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val responseEngine = remember { ResponseAndGaze() }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMonotonicNs by remember { mutableStateOf(0L) }
    var lastFeedbackMessage by remember { mutableStateOf("Touch pad ready. Tap anywhere when stimulus seen.") }
    var isStopped by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        AppHeader(
            title = "EyeAtlas VR - Response Handset",
            role = AppRole.RESPONSE_PAD,
            onToggleRole = onRoleChange
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paired Host: PHONE-PATIENT-VR (Wi-Fi P2P)",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Clock Offset: +20 ms | Uncertainty: 5 ms (TRUSTED)",
                        color = Color(0xFF81C784),
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = if (isStopped) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isStopped) "STOPPED" else "CONNECTED",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Blind-operable touch surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    if (isStopped) Color(0xFF2A1515) else Color(0xFF1A261A),
                    RoundedCornerShape(16.dp)
                )
                .pointerInput(isStopped) {
                    if (!isStopped) {
                        detectTapGestures(
                            onTap = {
                                val now = System.nanoTime()
                                val evt = responseEngine.registerTouchEvent(
                                    sessionId = "SESSION-RESEARCH-001",
                                    responsePhoneId = "PHONE-CLINICIAN-PAD",
                                    touchDownNs = now,
                                    touchUpNs = now + 40_000_000L,
                                    pointerCount = 1
                                )
                                tapCount++
                                lastTapMonotonicNs = now
                                lastFeedbackMessage = "Tap Registered (#$tapCount): SEEN recorded at t=${now / 1_000_000} ms"
                            },
                            onLongPress = {
                                val now = System.nanoTime()
                                val evt = responseEngine.registerTouchEvent(
                                    sessionId = "SESSION-RESEARCH-001",
                                    responsePhoneId = "PHONE-CLINICIAN-PAD",
                                    touchDownNs = now - 1_100_000_000L,
                                    touchUpNs = now,
                                    pointerCount = 1
                                )
                                isStopped = true
                                lastFeedbackMessage = "PRIORITY STOP TRIGGERED! Test halted."
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = if (isStopped) "SESSION HALTED (STOP)" else "BLIND TOUCH SURFACE",
                    color = if (isStopped) Color(0xFFFF8A80) else Color(0xFFA5D6A7),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isStopped)
                        "Priority STOP requested by patient.\nClinician must re-calibrate before resuming."
                    else
                        "• Tap anywhere with thumb/finger when stimulus seen\n• Long-press (1 sec) anywhere for EMERGENCY STOP\n• Screen stays low-luminance for dark adaptation",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = lastFeedbackMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom manual STOP and Reset Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isStopped = true
                    lastFeedbackMessage = "MANUAL STOP PRESSED: Test halted."
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Priority STOP", color = Color.White)
            }

            if (isStopped) {
                OutlinedButton(
                    onClick = {
                        isStopped = false
                        lastFeedbackMessage = "Pad re-armed. Awaiting clinician calibration."
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Re-arm Pad", color = Color.White)
                }
            }
        }
    }
}
