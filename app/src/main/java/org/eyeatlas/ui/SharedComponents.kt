package org.eyeatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eyeatlas.core.SessionState

enum class AppRole {
    PATIENT,
    CLINICIAN
}

@Composable
fun AppHeader(title: String, role: AppRole, onToggleRole: (AppRole) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Role: ${role.name} | Data Contracts (P01)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Button(
                onClick = {
                    onToggleRole(if (role == AppRole.PATIENT) AppRole.CLINICIAN else AppRole.PATIENT)
                }
            ) {
                Text(if (role == AppRole.PATIENT) "Switch to Doctor" else "Switch to Patient")
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
            text = "NOTICE: Research Prototype (P01 Data Contracts). No medical diagnosis or superiority claims. Full multi-morbidity support.",
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
