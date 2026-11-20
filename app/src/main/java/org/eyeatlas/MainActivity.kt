package org.eyeatlas

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
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

    private var onVolumeClickListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentRole by remember { mutableStateOf(AppRole.PATIENT) }
                    when (currentRole) {
                        AppRole.PATIENT -> PatientScreen(
                            onRoleChange = { currentRole = it },
                            onToggleVrOrientation = { isVr ->
                                requestedOrientation = if (isVr) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            },
                            registerVolumeKeyListener = { listener ->
                                onVolumeClickListener = listener
                            }
                        )
                        AppRole.CLINICIAN -> DoctorDashboard(onRoleChange = { currentRole = it })
                        AppRole.RESPONSE_PAD -> ResponsePadScreen(onRoleChange = { currentRole = it })
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            onVolumeClickListener?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
