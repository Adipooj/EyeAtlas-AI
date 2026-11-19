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
                    text = "Role: ${role.name} | Baseline P00",
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
            text = "NOTICE: Research Prototype (P00 Baseline). No medical diagnosis or superiority claims. Calibration active.",
            color = Color(0xFF856404),
            fontSize = 11.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}
