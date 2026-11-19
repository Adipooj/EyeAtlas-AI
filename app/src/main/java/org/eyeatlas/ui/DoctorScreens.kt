package org.eyeatlas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eyeatlas.core.*

@Composable
fun DoctorDashboard(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val stateMachine = remember { ClinicalStateMachine() }
    val decisionEngine = remember { DecisionEngine() }

    var currentState by remember { mutableStateOf(stateMachine.getState()) }
    var patientId by remember { mutableStateOf("P-101") }
    var hasDiabetes by remember { mutableStateOf(false) }
    var selectedSymptom by remember { mutableStateOf("BLURRED_VISION") }
    var hasConsent by remember { mutableStateOf(true) }
    var emergencyReason by remember { mutableStateOf<String?>(null) }
    var recoveryNotice by remember { mutableStateOf<String?>(null) }

    val symptomOptions = listOf(
        "NONE",
        "BLURRED_VISION",
        "EYE_STRAIN",
        "DRYNESS",
        "SUDDEN_VISION_LOSS",
        "CORNEAL_TRAUMA",
        "PAINFUL_PHOTOPHOBIC_RED_EYE",
        "CHEMICAL_EXPOSURE"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        AppHeader(
            title = "EyeAtlas VR - Clinician Station",
            role = AppRole.CLINICIAN,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Clinical Intake & Session State",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            StateBadge(state = currentState)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentState == SessionState.URGENT_BYPASS && emergencyReason != null) {
            EmergencyAlertCard(
                reason = emergencyReason!!,
                onAcknowledge = {
                    emergencyReason = null
                    stateMachine.processEvent(SessionEvent.FinalizeReview)
                    currentState = stateMachine.getState()
                }
            )
        }

        if (recoveryNotice != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Text(
                    text = recoveryNotice!!,
                    color = Color(0xFF2E7D32),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Patient Intake Form", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = patientId,
                    onValueChange = { patientId = it },
                    label = { Text("Patient Identifier (Pseudonymized)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasDiabetes,
                        onCheckedChange = { hasDiabetes = it }
                    )
                    Text("History of Diabetes Mellitus (Mandates Retinal Screening)")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasConsent,
                        onCheckedChange = { hasConsent = it }
                    )
                    Text("Informed Consent Signed & Logged")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Primary Presenting Symptom:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                symptomOptions.chunked(2).forEach { rowSymptoms ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSymptoms.forEach { sym ->
                            FilterChip(
                                selected = selectedSymptom == sym,
                                onClick = { selectedSymptom = sym },
                                label = { Text(sym.replace("_", " "), fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val referral = decisionEngine.screenIntakeForEmergency(listOf(selectedSymptom))
                            if (referral != null) {
                                emergencyReason = referral.reason
                                stateMachine.processEvent(SessionEvent.TriggerEmergencyBypass(referral.reason))
                                currentState = stateMachine.getState()
                            } else {
                                stateMachine.processEvent(SessionEvent.TriagePassed)
                                currentState = stateMachine.getState()
                                recoveryNotice = "Triage Passed: Proceeding to safe device calibration."
                            }
                        },
                        enabled = hasConsent && currentState != SessionState.URGENT_BYPASS
                    ) {
                        Text("Process Intake & Triage")
                    }

                    if (currentState != SessionState.STOPPED) {
                        OutlinedButton(
                            onClick = {
                                stateMachine.processEvent(SessionEvent.PriorityStop("Clinician requested STOP"))
                                currentState = stateMachine.getState()
                                recoveryNotice = "STOP engaged: Routine stimulus halted immediately."
                            }
                        ) {
                            Text("Priority STOP")
                        }
                    } else {
                        Button(
                            onClick = {
                                stateMachine.processEvent(SessionEvent.ResumeSession)
                                currentState = stateMachine.getState()
                                recoveryNotice = "Session resumed safely into calibration state."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("Resume Safe Session")
                        }
                    }
                }
            }
        }

        // Per-Eye Laterality Independence Display
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Per-Eye Multi-Morbidity & Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "OD (Right Eye)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Status: Independent assessment", fontSize = 12.sp)
                        Text(text = "Glaucoma: Suspicion pending field", fontSize = 11.sp)
                        Text(text = "Cataract: Independent score", fontSize = 11.sp)
                    }
                    VerticalDivider(modifier = Modifier.height(60.dp).width(1.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(text = "OS (Left Eye)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Status: Independent assessment", fontSize = 12.sp)
                        Text(text = "Retina: No negative on missing data", fontSize = 11.sp)
                        Text(text = "Acuity: Independent logMAR", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
