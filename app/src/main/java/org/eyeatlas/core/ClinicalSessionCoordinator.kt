package org.eyeatlas.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.eyeatlas.ui.ClinicalModuleId

/**
 * Singleton state coordinator bridging Patient VR mode, Physician & Response Pad mode,
 * and remote WebSocket/P2P command streams.
 *
 * Enables on-the-fly module switching, binocular OU support, trial resetting,
 * and zero-latency tactile response dispatch without exiting the VR headset.
 */
object ClinicalSessionCoordinator {
    var activeModule by mutableStateOf(ClinicalModuleId.PERIMETRY)
    var activeEye by mutableStateOf(EyeLaterality.OD)
    var isVrModeActive by mutableStateOf(false)
    var isEmergencyHalted by mutableStateOf(false)
    var sessionId by mutableStateOf("SES-EYEATLAS-01")
    var pairingPin by mutableStateOf("839201")

    // Event listeners
    var onRemoteTapListener: ((source: String) -> Unit)? = null
    var onResetTestListener: (() -> Unit)? = null
    var onModuleSwitchedListener: ((module: ClinicalModuleId, eye: EyeLaterality) -> Unit)? = null

    fun switchModule(module: ClinicalModuleId, eye: EyeLaterality = activeEye) {
        activeModule = module
        activeEye = eye
        onModuleSwitchedListener?.invoke(module, eye)
    }

    fun switchEye(eye: EyeLaterality) {
        activeEye = eye
        onModuleSwitchedListener?.invoke(activeModule, eye)
    }

    fun resetActiveTest() {
        onResetTestListener?.invoke()
    }

    fun dispatchTap(source: String = "REMOTE_RESPONSE_PAD") {
        onRemoteTapListener?.invoke(source)
    }

    fun toggleEmergencyHalt(halt: Boolean) {
        isEmergencyHalted = halt
    }
}
