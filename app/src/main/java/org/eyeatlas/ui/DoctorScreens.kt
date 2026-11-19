package org.eyeatlas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DoctorDashboard(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AppHeader(
            title = "EyeAtlas VR - Clinician Station",
            role = AppRole.CLINICIAN,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Active Clinical Session (Baseline)",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Patient ID: P-DEMO-001", fontWeight = FontWeight.Bold)
                Text(text = "Laterality: Bilateral Protocol (OD / OS Independent)", fontSize = 13.sp)
                Text(text = "Response Pad Mode: Active on Clinician Handset", fontSize = 13.sp)
                Text(text = "Optical Profile: Baseline / Simulated", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Diagnostic Test Modules", fontWeight = FontWeight.SemiBold)

        Spacer(modifier = Modifier.height(8.dp))

        val modules = listOf(
            "Visual Acuity & Optical Focus (P06)",
            "Colour, Contrast & Central Macular (P07)",
            "Visual Field Suprathreshold 54-pt (P08)",
            "External Eye & Anterior Segment (P10/P11)",
            "Retinal Fundus Imaging (P12/P13)"
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modules.size) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = modules[index], fontSize = 14.sp)
                        Text(text = "Ready", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
