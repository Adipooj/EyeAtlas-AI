package org.eyeatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eyeatlas.core.SessionState

@Composable
fun PatientScreen(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEmergencyHalted by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AppHeader(
            title = "EyeAtlas VR - Patient VR Mode",
            role = AppRole.PATIENT,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        if (isEmergencyHalted) {
            EmergencyAlertCard(
                reason = "Patient triggered emergency quick-release STOP.",
                onAcknowledge = { isEmergencyHalted = false }
            )
        }

        // Central VR Stimulus Viewport
        Card(
            modifier = Modifier
                .size(240.dp)
                .padding(8.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = if (isEmergencyHalted) Color(0xFF4A0000) else Color.DarkGray)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEmergencyHalted) "STOP" else "+",
                    color = Color.White,
                    fontSize = if (isEmergencyHalted) 28.sp else 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Perimetry Response Pad: Clinician Phone Paired",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { isEmergencyHalted = !isEmergencyHalted },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEmergencyHalted) Color.DarkGray else Color(0xFFB00020)
                    )
                ) {
                    Text(if (isEmergencyHalted) "Reset Patient View" else "Emergency STOP / Quick Release")
                }
            }
        }
    }
}
