package org.eyeatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PatientScreen(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AppHeader(
            title = "EyeAtlas VR - Patient Mode",
            role = AppRole.PATIENT,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        Card(
            modifier = Modifier
                .size(240.dp)
                .padding(8.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color.White,
                    fontSize = 48.sp,
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
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Status: Stimulus Alignment Ready",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Text(
                    text = "Perimetry Response: Touch Input Paired",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }
    }
}
