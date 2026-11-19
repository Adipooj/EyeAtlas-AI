package org.eyeatlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.eyeatlas.ui.AppRole
import org.eyeatlas.ui.DoctorDashboard
import org.eyeatlas.ui.PatientScreen
import org.eyeatlas.ui.ResponsePadScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentRole by remember { mutableStateOf(AppRole.PATIENT) }
                    when (currentRole) {
                        AppRole.PATIENT -> PatientScreen(onRoleChange = { currentRole = it })
                        AppRole.CLINICIAN -> DoctorDashboard(onRoleChange = { currentRole = it })
                        AppRole.RESPONSE_PAD -> ResponsePadScreen(onRoleChange = { currentRole = it })
                    }
                }
            }
        }
    }
}
