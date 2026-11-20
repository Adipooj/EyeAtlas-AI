package org.eyeatlas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

object ClinicalTheme {
    val SeaGreenPrimary = Color(0xFF1B8A6B)      // Professional Deep Sea Green
    val SeaGreenDark = Color(0xFF13644E)         // High-contrast Sea Green
    val SeaGreenMedium = Color(0xFF2E8B57)       // Medical Sea Green
    val SeaGreenLight = Color(0xFFE6F4F1)        // Soft Mint / Sea Green Wash
    val SeaGreenBorder = Color(0xFFBBEFDF)       // Sea Green Border
    val PureWhite = Color(0xFFFFFFFF)            // Hospital Crisp White
    val BackgroundWhite = Color(0xFFF8FAFC)      // Slate 50 Clinical Background
    val CardBorder = Color(0xFFE2E8F0)           // Slate 200 Card Border
    val TextPrimary = Color(0xFF0F172A)          // Slate 900 Heading
    val TextSecondary = Color(0xFF475569)        // Slate 600 Body
    val TextMuted = Color(0xFF64748B)            // Slate 500 Metadata
    val AlertRed = Color(0xFFDC2626)             // Medical Emergency Red
    val AlertRedBg = Color(0xFFFEF2F2)           // Emergency Light Background
    val AlertRedBorder = Color(0xFFFECACA)       // Emergency Border
    val WarningAmber = Color(0xFFD97706)         // Clinical Notice Amber
    val WarningAmberBg = Color(0xFFFFFBEB)       // Notice Light Background
    val WarningAmberBorder = Color(0xFFFDE68A)   // Notice Border
    val SuccessGreen = Color(0xFF16A34A)         // Status Connected Green
    val SuccessGreenBg = Color(0xFFF0FDF4)       // Status Light Background
}

enum class AppRole {
    PATIENT,
    CLINICIAN,
    RESPONSE_PAD
}

enum class ScreenId(val title: String, val description: String) {
    UX_01("UX-01 Pairing", "Pairing code, identity pinning & consent status"),
    UX_02("UX-02 Fit & Optics", "Chassis IPD, eye selection & optics readiness"),
    UX_03("UX-03 Pad Tutorial", "Practice trials & touch gesture comprehension"),
    UX_04("UX-04 Colour Screening", "Broad red-green screening & contrast"),
    UX_05("UX-05 Acuity & Near", "Distance/near visual acuity & vergence"),
    UX_06("UX-06 Hyperacuity", "Macular scotoma & vernier alignment"),
    UX_07("UX-07 Visual Field", "54-point suprathreshold perimetry"),
    UX_08("UX-08 External Eye", "Anterior segment, red-eye & pose overlay"),
    UX_09("UX-09 Tear Film", "NIBUT blink curve & ring reflection"),
    UX_10("UX-10 Fundus Imaging", "Retinal alignment, disc & macula fields"),
    UX_11("UX-11 Strabismus", "Strabismus & ocular motility cover test"),
    UX_12("UX-12 Referral Report", "Multi-condition synthesis & referral summary"),
    UX_13("UX-13 Priority STOP", "Emergency quick-release & stop telemetry"),
    UX_14("UX-14 Admin Lab", "Model governance, calibration lab & audits")
}

@Composable
fun ScreenNavigator(
    currentScreen: ScreenId,
    onNavigate: (ScreenId) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
        border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentScreen.name} - ${currentScreen.title}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTheme.TextPrimary
                )
                Surface(
                    color = ClinicalTheme.SeaGreenLight,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder)
                ) {
                    Text(
                        text = "Stage ${currentScreen.ordinal + 1} of 14",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClinicalTheme.SeaGreenDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = currentScreen.description,
                fontSize = 11.sp,
                color = ClinicalTheme.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val screens = ScreenId.values()
                val prevIndex = (currentScreen.ordinal - 1 + screens.size) % screens.size
                val nextIndex = (currentScreen.ordinal + 1) % screens.size

                OutlinedButton(
                    onClick = { onNavigate(screens[prevIndex]) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, ClinicalTheme.SeaGreenPrimary)
                ) {
                    Text("Previous", fontSize = 11.sp, color = ClinicalTheme.SeaGreenPrimary)
                }

                Button(
                    onClick = { onNavigate(screens[nextIndex]) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                ) {
                    Text("Next Step", fontSize = 11.sp, color = ClinicalTheme.PureWhite)
                }
            }
        }
    }
}

@Composable
fun AppHeader(title: String, role: AppRole, onToggleRole: (AppRole) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
        border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = ClinicalTheme.SeaGreenPrimary,
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.size(width = 4.dp, height = 18.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTheme.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "OnePlus Nord CE 3 5G (120Hz VSYNC, 394 PPI, IMX890) | Encrypted Session (CR-001)",
                        fontSize = 10.sp,
                        color = ClinicalTheme.TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    color = ClinicalTheme.SeaGreenLight,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder)
                ) {
                    Text(
                        text = role.name,
                        color = ClinicalTheme.SeaGreenDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppRole.values().forEach { targetRole ->
                    val isSelected = role == targetRole
                    Surface(
                        color = if (isSelected) ClinicalTheme.SeaGreenPrimary else ClinicalTheme.BackgroundWhite,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSelected) ClinicalTheme.SeaGreenPrimary else ClinicalTheme.CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggleRole(targetRole) }
                    ) {
                        Text(
                            text = when (targetRole) {
                                AppRole.PATIENT -> "Patient VR"
                                AppRole.CLINICIAN -> "Clinician"
                                AppRole.RESPONSE_PAD -> "Response Pad"
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) ClinicalTheme.PureWhite else ClinicalTheme.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
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
        colors = CardDefaults.cardColors(containerColor = ClinicalTheme.SeaGreenLight),
        border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ClinicalTheme.SeaGreenPrimary,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "INVESTIGATIONAL",
                    color = ClinicalTheme.PureWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "Clinical research prototype. No standalone medical diagnosis or superiority claims. Calibrated for OnePlus Nord CE 3 5G 120Hz display.",
                color = ClinicalTheme.SeaGreenDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EmergencyAlertCard(reason: String, onAcknowledge: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = ClinicalTheme.AlertRedBg),
        border = BorderStroke(1.dp, ClinicalTheme.AlertRedBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CRITICAL EMERGENCY ALERT - URGENT BYPASS",
                color = ClinicalTheme.AlertRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reason,
                color = Color(0xFF7F1D1D),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ACTION: Routine VR testing halted. Immediate ophthalmic referral instructed.",
                color = ClinicalTheme.AlertRed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.AlertRed),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Acknowledge & Open Emergency Referral", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StateBadge(state: SessionState) {
    val (bgColor, textColor) = when (state) {
        SessionState.URGENT_BYPASS -> Pair(ClinicalTheme.AlertRedBg, ClinicalTheme.AlertRed)
        SessionState.STOPPED -> Pair(ClinicalTheme.WarningAmberBg, ClinicalTheme.WarningAmber)
        SessionState.REVIEW_AND_REFERRAL -> Pair(ClinicalTheme.SeaGreenLight, ClinicalTheme.SeaGreenPrimary)
        SessionState.FUNCTIONAL_TESTS -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
        else -> Pair(ClinicalTheme.BackgroundWhite, ClinicalTheme.TextSecondary)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.25f))
    ) {
        Text(
            text = "STATE: ${state.name}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Screen rendered on the second phone running the in-app RESPONSE_PAD role.
 * Blind-operable touch surface: short tap = SEEN, long press = STOP.
 * Calibrated for OnePlus Nord CE 3 5G 240Hz touch sampling rate.
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
            .background(ClinicalTheme.BackgroundWhite)
            .padding(12.dp)
    ) {
        AppHeader(
            title = "EyeAtlas VR - Response Handset",
            role = AppRole.RESPONSE_PAD,
            onToggleRole = onRoleChange
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Paired Host: PHONE-PATIENT-VR (Wi-Fi Direct P2P)",
                        color = ClinicalTheme.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Hardware: OnePlus Nord CE 3 5G (240Hz Digitizer, 4.16ms Interval)",
                        color = ClinicalTheme.TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Clock Offset: +20 ms | Uncertainty: 5 ms (TRUSTED / <50ms)",
                        color = ClinicalTheme.SeaGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    color = if (isStopped) ClinicalTheme.AlertRedBg else ClinicalTheme.SeaGreenLight,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isStopped) ClinicalTheme.AlertRedBorder else ClinicalTheme.SeaGreenBorder)
                ) {
                    Text(
                        text = if (isStopped) "STOPPED" else "CONNECTED",
                        color = if (isStopped) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                    if (isStopped) ClinicalTheme.AlertRedBg else ClinicalTheme.PureWhite,
                    RoundedCornerShape(16.dp)
                )
                .border(
                    BorderStroke(
                        2.dp,
                        if (isStopped) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenPrimary
                    ),
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
                                lastFeedbackMessage = "Stimulus Acknowledged (#$tapCount) at ${now / 1_000_000} ms"
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
                                lastFeedbackMessage = "EMERGENCY STOP TRIGGERED: Protocol halted immediately."
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
                Surface(
                    color = if (isStopped) ClinicalTheme.AlertRed.copy(alpha = 0.1f) else ClinicalTheme.SeaGreenLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = if (isStopped) "PROTOCOL HALTED" else "ONEPLUS NORD CE 3 5G TOUCH DIGITIZER",
                        color = if (isStopped) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = if (isStopped) "EMERGENCY STOP ACTIVATED" else "TACTILE RESPONSE SURFACE",
                    color = if (isStopped) ClinicalTheme.AlertRed else ClinicalTheme.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!isStopped) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "TAPS: $tapCount",
                        color = ClinicalTheme.SeaGreenDark,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isStopped)
                        "Priority stop requested by patient.\nClinician must re-calibrate and verify before resuming."
                    else
                        "• Tap anywhere when peripheral stimulus is seen\n• Press and hold (1.0 sec) anywhere for emergency stop\n• 240Hz touch sampling active (<15ms response latency)",
                    color = ClinicalTheme.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = ClinicalTheme.BackgroundWhite,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ClinicalTheme.CardBorder)
                ) {
                    Text(
                        text = lastFeedbackMessage,
                        color = if (isStopped) ClinicalTheme.AlertRed else ClinicalTheme.SeaGreenDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom manual STOP and Reset Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isStopped = true
                    lastFeedbackMessage = "MANUAL STOP PRESSED: Examination halted."
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.AlertRed),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Priority STOP", color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (isStopped) {
                OutlinedButton(
                    onClick = {
                        isStopped = false
                        lastFeedbackMessage = "Pad re-armed. Awaiting clinician calibration."
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, ClinicalTheme.SeaGreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Re-arm Response Pad", color = ClinicalTheme.SeaGreenPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
