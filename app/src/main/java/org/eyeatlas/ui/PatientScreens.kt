package org.eyeatlas.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.eyeatlas.core.ClinicalSessionCoordinator
import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.net.RemoteSessionClient
import org.eyeatlas.sensors.Calibration
import org.eyeatlas.sensors.ResponseAndGaze
import org.eyeatlas.sensors.VrPresenter
import org.eyeatlas.vision.FunctionalTests
import org.eyeatlas.vision.OptotypeOrientation
import kotlin.math.*
import kotlin.random.Random

/**
 * Diagnostic Clinical Modules for EyeAtlas VR.
 */
enum class ClinicalModuleId(val title: String, val shortDesc: String) {
    PERIMETRY("Visual Field (24-2)", "54-point suprathreshold perimetry & scotoma mapping"),
    ACUITY("Visual Acuity (ETDRS)", "Adaptive ETDRS logMAR & Snellen acuity staircase"),
    COLOUR("Colour Vision (Ishihara)", "24 authentic pseudo-isochromatic plates & deficiency triage"),
    CONTRAST("Contrast Sensitivity", "Pelli-Robson triplet staircase & logCS determination"),
    AMSLER("Central Amsler (10°)", "Maculopathy, metamorphopsia & central scotoma map"),
    WORTH_DOT("Binocular (Worth 4-Dot)", "Sensory fusion, suppression & diplopia detection"),
    MOTILITY("Ocular Motility (Broad-H)", "9 cardinal gazes smooth pursuit & cranial nerve paresis"),
    PUPIL_RAPD("Pupillary Exam (RAPD)", "Swinging flashlight test & afferent defect assessment"),
    GUIDED_PROTOCOL("Guided 14-Stage Protocol", "Standardized ophthalmic screening examination workflow")
}

enum class AcuityOptotypeMode(val label: String) {
    TUMBLING_E("Tumbling E"),
    LANDOLT_C("Landolt C"),
    SLOAN("Sloan Letters")
}

enum class PointStatus {
    PENDING,
    SEEN,
    MISSED,
    BLIND_SPOT
}

@Composable
fun PatientScreen(
    onRoleChange: (AppRole) -> Unit,
    modifier: Modifier = Modifier,
    onToggleVrOrientation: (Boolean) -> Unit = {},
    registerVolumeKeyListener: ((() -> Unit)?) -> Unit = {}
) {
    val calibration = remember {
        Calibration().apply {
            val profile = Calibration.createOnePlusNordCe3Profile()
            loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        }
    }
    val presenter = remember { VrPresenter(calibration).withNordCe3Layout() }
    val functionalTests = remember { FunctionalTests() }
    val responseEngine = remember { ResponseAndGaze() }
    val remoteClient = remember { RemoteSessionClient(sessionId = "SES-REM-LIVE-01") }

    // Navigation and Mode States
    var selectedModule by remember { mutableStateOf(ClinicalSessionCoordinator.activeModule) }
    var testedEye by remember { mutableStateOf(ClinicalSessionCoordinator.activeEye) }
    var isVrMode by remember { mutableStateOf(false) }
    var isEmergencyHalted by remember { mutableStateOf(ClinicalSessionCoordinator.isEmergencyHalted) }
    var pairingPin by remember { mutableStateOf("749281") }
    var feedbackToast by remember { mutableStateOf("Ready. Select module or enter VR Mode.") }
    var currentScreen by remember { mutableStateOf(ScreenId.UX_07) }

    // --- Cloud Pairing (CR-002) State ---
    var showCloudPairingDialog by remember { mutableStateOf(false) }
    var cloudBackendUrl by remember { mutableStateOf("https://eyeatlas-ai.onrender.com") }
    var cloudSessionId by remember { mutableStateOf("SES-REMOTE-01") }
    var cloudPin by remember { mutableStateOf("749281") }
    var cloudConnectionStatus by remember { mutableStateOf("Ready to Pair") }

    // --- Acuity Engine State ---
    var acuityOptotypeMode by remember { mutableStateOf(AcuityOptotypeMode.TUMBLING_E) }
    var acuitySloanLetter by remember { mutableStateOf('E') }
    var acuityLogMar by remember { mutableStateOf(1.0) } // Starts at 6/60
    var acuityOrientation by remember { mutableStateOf(OptotypeOrientation.RIGHT) }
    var acuityTrialsDone by remember { mutableStateOf(0) }
    var acuityConsecutiveCorrect by remember { mutableStateOf(0) }
    var acuityScore by remember { mutableStateOf("Measuring (Current: 6/60)...") }

    // --- Perimetry Engine State (RANDOMIZED 24-2 SEQUENCE) ---
    val perimetryPoints = remember(testedEye) { functionalTests.generateFiftyFourPointMap(testedEye) }
    val perimetryOrder = remember(testedEye) { (0 until perimetryPoints.size).shuffled().toMutableList() }
    var activePerimetryStep by remember { mutableStateOf(0) }
    var activePerimetryIndex by remember { mutableStateOf(perimetryOrder.firstOrNull() ?: 0) }
    var isPerimetryRunning by remember { mutableStateOf(false) }
    var isStimulusActive by remember { mutableStateOf(false) }
    var stimulusAngleX by remember { mutableStateOf(0.0) }
    var stimulusAngleY by remember { mutableStateOf(0.0) }
    var stimulusStartNs by remember { mutableStateOf(0L) }
    val perimetryResults = remember { mutableStateMapOf<Int, PointStatus>() }
    val perimetryReactionTimes = remember { mutableStateListOf<Long>() }
    var fixationLosses by remember { mutableStateOf(0) }
    var falsePositives by remember { mutableStateOf(0) }

    // --- Colour Vision State (24 ISHIHARA PLATES) ---
    var colourPlateIndex by remember { mutableStateOf(0) }
    val colourPlateAnswers = remember { mutableStateListOf<String>() }
    var colourDiagnosis by remember { mutableStateOf<String?>(null) }

    // --- Contrast Sensitivity State ---
    var contrastStep by remember { mutableStateOf(0) }
    var contrastLogCsScore by remember { mutableStateOf<Double?>(null) }

    // --- Amsler Grid State ---
    val amslerDefectPoints = remember { mutableStateListOf<Pair<Int, Int>>() }
    var amslerResult by remember { mutableStateOf<String?>(null) }

    // --- Worth 4-Dot State ---
    var worthDotResult by remember { mutableStateOf<String?>(null) }

    // --- Motility Broad-H State ---
    var isMotilityRunning by remember { mutableStateOf(false) }
    var motilityAngleX by remember { mutableStateOf(0f) }
    var motilityAngleY by remember { mutableStateOf(0f) }
    var motilityGazeLabel by remember { mutableStateOf("Primary Fixation (Center)") }
    var motilityResult by remember { mutableStateOf<String?>(null) }

    // --- Pupillary RAPD State ---
    var isPupilRunning by remember { mutableStateOf(false) }
    var pupilIlluminatedEye by remember { mutableStateOf<EyeLaterality?>(null) }
    var pupilRapdResult by remember { mutableStateOf<String?>(null) }

    // RESET HANDLER: Restarts and clears state for whichever test is active
    val resetCurrentModule: () -> Unit = {
        when (selectedModule) {
            ClinicalModuleId.PERIMETRY -> {
                perimetryOrder.clear()
                perimetryOrder.addAll((0 until perimetryPoints.size).shuffled())
                activePerimetryStep = 0
                activePerimetryIndex = perimetryOrder.firstOrNull() ?: 0
                perimetryReactionTimes.clear()
                perimetryResults.clear()
                falsePositives = 0
                fixationLosses = 0
                isPerimetryRunning = false
                isStimulusActive = false
                feedbackToast = "24-2 Perimetry reset & point order randomized."
            }
            ClinicalModuleId.ACUITY -> {
                acuityLogMar = 1.0
                acuityTrialsDone = 0
                acuityConsecutiveCorrect = 0
                acuityScore = "Staircase reset (Current: 6/60)..."
                feedbackToast = "Visual Acuity staircase reset to initial step."
            }
            ClinicalModuleId.COLOUR -> {
                colourPlateIndex = 0
                colourPlateAnswers.clear()
                colourDiagnosis = null
                feedbackToast = "Ishihara Colour test reset to Plate 1."
            }
            ClinicalModuleId.CONTRAST -> {
                contrastStep = 0
                contrastLogCsScore = null
                feedbackToast = "Contrast Sensitivity reset to 100%."
            }
            ClinicalModuleId.AMSLER -> {
                amslerDefectPoints.clear()
                amslerResult = null
                feedbackToast = "Amsler grid cleared."
            }
            ClinicalModuleId.WORTH_DOT -> {
                worthDotResult = null
                feedbackToast = "Worth 4-Dot test reset."
            }
            ClinicalModuleId.MOTILITY -> {
                isMotilityRunning = false
                motilityAngleX = 0f
                motilityAngleY = 0f
                motilityGazeLabel = "Primary Position (Center)"
                motilityResult = null
                feedbackToast = "Motility test reset to primary fixation."
            }
            ClinicalModuleId.PUPIL_RAPD -> {
                isPupilRunning = false
                pupilIlluminatedEye = null
                pupilRapdResult = null
                feedbackToast = "Pupillary RAPD test reset."
            }
            else -> {
                feedbackToast = "${selectedModule.title} reset."
            }
        }
    }

    // Generic Clicker Handler (dispatched from screen tap, physical volume rocker, or remote response pad)
    val handleTactileResponse = {
        val now = System.nanoTime()
        if (isEmergencyHalted) {
            feedbackToast = "Halted by Emergency STOP."
        } else if (selectedModule == ClinicalModuleId.PERIMETRY) {
            if (isStimulusActive || (now - stimulusStartNs < 800_000_000L && stimulusStartNs > 0)) {
                val rt = (now - stimulusStartNs) / 1_000_000L
                perimetryReactionTimes.add(rt)
                perimetryResults[activePerimetryIndex] = PointStatus.SEEN
                isStimulusActive = false
                feedbackToast = "Stimulus SEEN! RT: ${rt} ms (#${activePerimetryIndex + 1})"
            } else {
                falsePositives++
                feedbackToast = "Click when NO stimulus active (False Positive #$falsePositives)"
            }
        } else {
            feedbackToast = "Tap registered at ${System.currentTimeMillis() % 10000} ms"
        }
    }

    // Listen to remote module switching without leaving VR
    LaunchedEffect(ClinicalSessionCoordinator.activeModule, ClinicalSessionCoordinator.activeEye) {
        if (selectedModule != ClinicalSessionCoordinator.activeModule || testedEye != ClinicalSessionCoordinator.activeEye) {
            selectedModule = ClinicalSessionCoordinator.activeModule
            testedEye = ClinicalSessionCoordinator.activeEye
            resetCurrentModule()
            feedbackToast = "Switched to ${selectedModule.title} (${testedEye.name})"
        }
    }

    LaunchedEffect(ClinicalSessionCoordinator.isEmergencyHalted) {
        isEmergencyHalted = ClinicalSessionCoordinator.isEmergencyHalted
    }

    // Register hardware volume rocker and remote coordinator listener
    DisposableEffect(Unit) {
        registerVolumeKeyListener { handleTactileResponse() }
        ClinicalSessionCoordinator.onRemoteTapListener = { _ -> handleTactileResponse() }
        ClinicalSessionCoordinator.onResetTestListener = { resetCurrentModule() }
        onDispose {
            registerVolumeKeyListener(null)
            ClinicalSessionCoordinator.onRemoteTapListener = null
            ClinicalSessionCoordinator.onResetTestListener = null
        }
    }

    // Synchronize landscape/portrait orientation with VR Mode
    LaunchedEffect(isVrMode) {
        onToggleVrOrientation(isVrMode)
    }

    // Perimetry Stimulus Presentation Loop (RANDOMIZED ORDER)
    LaunchedEffect(isPerimetryRunning, activePerimetryStep) {
        if (isPerimetryRunning && activePerimetryStep < perimetryOrder.size && !isEmergencyHalted) {
            delay(1100L + Random.nextLong(0, 400))
            val pointIdx = perimetryOrder[activePerimetryStep]
            activePerimetryIndex = pointIdx
            val currentPt = perimetryPoints[pointIdx]
            stimulusAngleX = currentPt.first
            stimulusAngleY = currentPt.second
            stimulusStartNs = System.nanoTime()
            isStimulusActive = true

            // Flashed for 200ms (Goldmann III clinical standard)
            delay(200L)
            isStimulusActive = false

            // Wait 600ms for response window
            delay(600L)
            if (!perimetryResults.containsKey(pointIdx)) {
                perimetryResults[pointIdx] = PointStatus.MISSED
            }

            if (activePerimetryStep + 1 < perimetryOrder.size) {
                activePerimetryStep++
            } else {
                isPerimetryRunning = false
                feedbackToast = "24-2 Visual Field Complete! Seen: ${perimetryResults.values.count { it == PointStatus.SEEN }}/54"
            }
        }
    }

    // Ocular Motility Broad-H Animation Loop
    LaunchedEffect(isMotilityRunning) {
        if (isMotilityRunning) {
            val trajectory = listOf(
                Triple(0f, 0f, "Primary Position (Center)"),
                Triple(0.8f, 0f, "Dextroversion (Looking Right)"),
                Triple(0.8f, 0.7f, "Dextroelevation (Up and Right)"),
                Triple(0.8f, -0.7f, "Dextrodepression (Down and Right)"),
                Triple(0f, 0f, "Return to Center"),
                Triple(-0.8f, 0f, "Levoversion (Looking Left)"),
                Triple(-0.8f, 0.7f, "Levoelevation (Up and Left)"),
                Triple(-0.8f, -0.7f, "Levodepression (Down and Left)"),
                Triple(0f, 0f, "Examination Complete")
            )
            for (step in trajectory) {
                motilityAngleX = step.first
                motilityAngleY = step.second
                motilityGazeLabel = step.third
                delay(2000L)
            }
            isMotilityRunning = false
        }
    }

    // Pupillary RAPD Swinging Light Loop
    LaunchedEffect(isPupilRunning) {
        if (isPupilRunning) {
            for (cycle in 1..3) {
                pupilIlluminatedEye = EyeLaterality.OD
                feedbackToast = "Light on Right Eye (OD)... Cycle $cycle"
                delay(2500L)
                pupilIlluminatedEye = EyeLaterality.OS
                feedbackToast = "Light on Left Eye (OS)... Cycle $cycle"
                delay(2500L)
            }
            pupilIlluminatedEye = null
            isPupilRunning = false
            feedbackToast = "Swinging Flashlight Complete. Assess reflex symmetry."
        }
    }

    // =========================================================================
    // RENDER: TRUE DUAL-VIEWPORT STEREOSCOPIC VR BOX MODE
    // =========================================================================
    if (isVrMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { handleTactileResponse() })
                }
        ) {
            // Stereo Split Screen: Left Eye (OS) | Vertical Septum | Right Eye (OD)
            Row(modifier = Modifier.fillMaxSize()) {
                val isOsActive = testedEye == EyeLaterality.OS || testedEye == EyeLaterality.OU ||
                        selectedModule == ClinicalModuleId.WORTH_DOT || selectedModule == ClinicalModuleId.MOTILITY
                val isOdActive = testedEye == EyeLaterality.OD || testedEye == EyeLaterality.OU ||
                        selectedModule == ClinicalModuleId.WORTH_DOT || selectedModule == ClinicalModuleId.MOTILITY

                // Left Eye Viewport (OS)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (!isOsActive) Color.Black else Color(0xFF050811)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isOsActive) {
                        RenderActiveStimulusInVr(
                            eye = EyeLaterality.OS,
                            module = selectedModule,
                            isStimulusActive = isStimulusActive,
                            stimulusAngleX = stimulusAngleX,
                            stimulusAngleY = stimulusAngleY,
                            acuityOptotypeMode = acuityOptotypeMode,
                            acuityOrientation = acuityOrientation,
                            acuitySloanLetter = acuitySloanLetter,
                            acuityLogMar = acuityLogMar,
                            colourPlateIndex = colourPlateIndex,
                            contrastStep = contrastStep,
                            motilityAngleX = motilityAngleX,
                            motilityAngleY = motilityAngleY,
                            pupilIlluminated = pupilIlluminatedEye == EyeLaterality.OS
                        )
                    } else {
                        Text("OCCLUDED (OD TESTED)", color = Color(0xFF334155), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Physical Optical Septum Divider (Prevents cross-eye optical bleed)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1E293B))
                )

                // Right Eye Viewport (OD)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (!isOdActive) Color.Black else Color(0xFF050811)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isOdActive) {
                        RenderActiveStimulusInVr(
                            eye = EyeLaterality.OD,
                            module = selectedModule,
                            isStimulusActive = isStimulusActive,
                            stimulusAngleX = stimulusAngleX,
                            stimulusAngleY = stimulusAngleY,
                            acuityOptotypeMode = acuityOptotypeMode,
                            acuityOrientation = acuityOrientation,
                            acuitySloanLetter = acuitySloanLetter,
                            acuityLogMar = acuityLogMar,
                            colourPlateIndex = colourPlateIndex,
                            contrastStep = contrastStep,
                            motilityAngleX = motilityAngleX,
                            motilityAngleY = motilityAngleY,
                            pupilIlluminated = pupilIlluminatedEye == EyeLaterality.OD
                        )
                    } else {
                        Text("OCCLUDED (OS TESTED)", color = Color(0xFF334155), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Top HUD Overlay: Module Name, Active Eye, Reset & Exit Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "VR MODE • ${selectedModule.title} • Eye: ${testedEye.name} • Tap Screen or Volume Key",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { resetCurrentModule() },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("↺ Reset", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            isVrMode = false
                            ClinicalSessionCoordinator.isVrModeActive = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Exit VR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // =========================================================================
    // RENDER: HANDHELD CLINICAL CONTROLLER & MODULE LAUNCHER MODE
    // =========================================================================
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalTheme.BackgroundWhite)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        AppHeader(
            title = "EyeAtlas VR - Patient & Clinical Suite",
            role = AppRole.PATIENT,
            onToggleRole = onRoleChange
        )

        ResearchNotice()

        // 1. TOP CONTROL BAR: Eye Laterality Toggle (OD, OS, OU) + VR Box Launcher + Reset Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Tested Eye Selector Row with OD, OS, OU
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Eye: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ClinicalTheme.TextPrimary)
                    FilterChip(
                        selected = testedEye == EyeLaterality.OD,
                        onClick = {
                            testedEye = EyeLaterality.OD
                            ClinicalSessionCoordinator.switchEye(EyeLaterality.OD)
                        },
                        label = { Text("OD (Right)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = testedEye == EyeLaterality.OS,
                        onClick = {
                            testedEye = EyeLaterality.OS
                            ClinicalSessionCoordinator.switchEye(EyeLaterality.OS)
                        },
                        label = { Text("OS (Left)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = testedEye == EyeLaterality.OU,
                        onClick = {
                            testedEye = EyeLaterality.OU
                            ClinicalSessionCoordinator.switchEye(EyeLaterality.OU)
                        },
                        label = { Text("OU (Both)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row: Launch VR Box & Reset Test
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isVrMode = true
                            ClinicalSessionCoordinator.isVrModeActive = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("🥽 Launch VR Box", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { resetCurrentModule() },
                        border = BorderStroke(1.dp, ClinicalTheme.SeaGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("↺ Reset Test", color = ClinicalTheme.SeaGreenDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                // Status Toast / Monitored Prompt
                Text(
                    text = feedbackToast,
                    color = ClinicalTheme.SeaGreenDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClinicalTheme.SeaGreenLight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Online Cloud Pairing Bar
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCloudPairingDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pair Cloud Station: $cloudSessionId",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ClinicalTheme.SeaGreenDark
                                )
                            }
                            Text(
                                text = "PIN: $cloudPin | $cloudConnectionStatus",
                                fontSize = 10.sp,
                                color = ClinicalTheme.TextSecondary
                            )
                        }
                        Surface(
                            color = ClinicalTheme.SeaGreenPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Pair Online",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Cloud Pairing Modal Dialog
        if (showCloudPairingDialog) {
            AlertDialog(
                onDismissRequest = { showCloudPairingDialog = false },
                title = {
                    Text("Pair with Cloud Web Station", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ClinicalTheme.TextPrimary)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Connect this phone to the web controller at https://eyeatlas-ai.netlify.app via the Render broker.",
                            fontSize = 12.sp,
                            color = ClinicalTheme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = cloudBackendUrl,
                            onValueChange = { cloudBackendUrl = it },
                            label = { Text("Cloud Backend URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = cloudSessionId,
                            onValueChange = { cloudSessionId = it },
                            label = { Text("Session ID / Room") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = cloudPin,
                            onValueChange = { cloudPin = it },
                            label = { Text("6-Digit Pairing PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = ClinicalTheme.SeaGreenLight,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("HOW TO CONNECT ON LAPTOP:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ClinicalTheme.SeaGreenDark)
                                Text("1. Open https://eyeatlas-ai.netlify.app in your browser.\n2. Click 'Pair Online APK (CR-002)'.\n3. Match Room: $cloudSessionId and PIN: $cloudPin.\n4. You can immediately control tests, switch modules, and use Spacebar/Click as response pad!", fontSize = 11.sp, color = ClinicalTheme.TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            cloudConnectionStatus = "Connected to $cloudBackendUrl (Room: $cloudSessionId)"
                            feedbackToast = "Cloud broker linked: $cloudSessionId (PIN: $cloudPin)"
                            showCloudPairingDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("Connect / Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showCloudPairingDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 2. HORIZONTAL MODULE SELECTION CAROUSEL
        Text(
            text = "CHOOSE CLINICAL MODULE TO LAUNCH",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ClinicalTheme.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ClinicalModuleId.values().forEach { module ->
                val isSelected = selectedModule == module
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedModule = module
                        ClinicalSessionCoordinator.switchModule(module)
                        feedbackToast = "Launched ${module.title}. Configure and test below."
                    },
                    label = {
                        Text(
                            text = module.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ClinicalTheme.SeaGreenPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. INTERACTIVE ACTIVE MODULE CANVAS & CONTROLLER VIEWPORT
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTheme.PureWhite),
            border = BorderStroke(1.dp, ClinicalTheme.CardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header of Active Module
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = selectedModule.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ClinicalTheme.SeaGreenDark
                        )
                        Text(
                            text = selectedModule.shortDesc,
                            fontSize = 11.sp,
                            color = ClinicalTheme.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Text(
                            text = "Eye: ${testedEye.name}",
                            color = Color(0xFF1D4ED8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // DISPATCH INTERACTIVE MODULE UI
                when (selectedModule) {
                    ClinicalModuleId.PERIMETRY -> {
                        PerimetryModuleView(
                            eye = testedEye,
                            points = perimetryPoints,
                            activeTrialIndex = activePerimetryIndex,
                            isRunning = isPerimetryRunning,
                            isStimulusActive = isStimulusActive,
                            stimulusAngleX = stimulusAngleX,
                            stimulusAngleY = stimulusAngleY,
                            results = perimetryResults,
                            reactionTimes = perimetryReactionTimes,
                            fixationLosses = fixationLosses,
                            falsePositives = falsePositives,
                            onStart = {
                                perimetryResults.clear()
                                perimetryReactionTimes.clear()
                                activePerimetryIndex = 0
                                isPerimetryRunning = true
                                feedbackToast = "24-2 Visual Field Exam Started. Fixate central + mark."
                            },
                            onPause = { isPerimetryRunning = false },
                            onReset = { resetCurrentModule() },
                            onTap = handleTactileResponse
                        )
                    }

                    ClinicalModuleId.ACUITY -> {
                        AcuityModuleView(
                            eye = testedEye,
                            mode = acuityOptotypeMode,
                            logMar = acuityLogMar,
                            orientation = acuityOrientation,
                            sloanLetter = acuitySloanLetter,
                            trialsDone = acuityTrialsDone,
                            scoreText = acuityScore,
                            onModeChange = { acuityOptotypeMode = it },
                            onResponseOrientation = { chosenOrientation ->
                                acuityTrialsDone++
                                val isCorrect = chosenOrientation == acuityOrientation
                                if (isCorrect) {
                                    acuityConsecutiveCorrect++
                                    feedbackToast = "Correct! Orientation matched."
                                    if (acuityConsecutiveCorrect >= 2) {
                                        acuityLogMar = max(0.0, acuityLogMar - 0.2) // Step down (better acuity)
                                        acuityConsecutiveCorrect = 0
                                    }
                                } else {
                                    feedbackToast = "Incorrect. Stepping to larger optotype."
                                    acuityLogMar = min(1.2, acuityLogMar + 0.2)
                                    acuityConsecutiveCorrect = 0
                                }
                                acuityOrientation = OptotypeOrientation.values().random()
                                val snellenDenominator = round(6.0 * 10.0.pow(acuityLogMar)).toInt()
                                acuityScore = "Snellen: 6/$snellenDenominator (${round(acuityLogMar * 100) / 100.0} logMAR)"
                            },
                            onResponseLetter = { chosenLetter ->
                                acuityTrialsDone++
                                val isCorrect = chosenLetter == acuitySloanLetter
                                if (isCorrect) {
                                    acuityConsecutiveCorrect++
                                    feedbackToast = "Correct! Letter matched."
                                    if (acuityConsecutiveCorrect >= 2) {
                                        acuityLogMar = max(0.0, acuityLogMar - 0.2)
                                        acuityConsecutiveCorrect = 0
                                    }
                                } else {
                                    feedbackToast = "Incorrect. Stepping to larger optotype."
                                    acuityLogMar = min(1.2, acuityLogMar + 0.2)
                                    acuityConsecutiveCorrect = 0
                                }
                                val sloanOptions = listOf('C', 'D', 'E', 'F', 'L', 'O', 'P', 'T', 'Z')
                                acuitySloanLetter = sloanOptions.random()
                                val snellenDenominator = round(6.0 * 10.0.pow(acuityLogMar)).toInt()
                                acuityScore = "Snellen: 6/$snellenDenominator (${round(acuityLogMar * 100) / 100.0} logMAR)"
                            },
                            onReset = { resetCurrentModule() }
                        )
                    }

                    ClinicalModuleId.COLOUR -> {
                        ColourVisionModuleView(
                            plateIndex = colourPlateIndex,
                            diagnosis = colourDiagnosis,
                            onAnswer = { ans ->
                                colourPlateAnswers.add(ans)
                                if (colourPlateIndex + 1 < 24) {
                                    colourPlateIndex++
                                    feedbackToast = "Plate ${colourPlateIndex + 1} of 24. Record number seen."
                                } else {
                                    val errorCount = colourPlateAnswers.filterIndexed { idx, answer ->
                                        when (idx) {
                                            0 -> answer != "12"
                                            1 -> answer != "8"
                                            2 -> answer != "6"
                                            3 -> answer != "29"
                                            4 -> answer != "57"
                                            5 -> answer != "5"
                                            6 -> answer != "3"
                                            7 -> answer != "15"
                                            8 -> answer != "74"
                                            9 -> answer != "2"
                                            10 -> answer != "6"
                                            11 -> answer != "97"
                                            12 -> answer != "45"
                                            13 -> answer != "5"
                                            14 -> answer != "7"
                                            15 -> answer != "16"
                                            16 -> answer != "73"
                                            17, 18, 19, 20 -> answer != "Blank" && answer.isNotEmpty()
                                            21 -> !answer.contains("26")
                                            22 -> !answer.contains("42")
                                            23 -> !answer.contains("35")
                                            else -> false
                                        }
                                    }.size
                                    colourDiagnosis = if (errorCount <= 2) {
                                        "NORMAL TRICHROMAT (≤2 errors / 24 plates)"
                                    } else if (colourPlateAnswers.getOrNull(21) == "2" || colourPlateAnswers.getOrNull(22) == "4" || colourPlateAnswers.getOrNull(23) == "3") {
                                        "DEUTERANOPIA / DEUTERANOMALY (Green deficiency)"
                                    } else if (colourPlateAnswers.getOrNull(21) == "6" || colourPlateAnswers.getOrNull(22) == "2" || colourPlateAnswers.getOrNull(23) == "5") {
                                        "PROTANOPIA / PROTANOMALY (Red deficiency)"
                                    } else {
                                        "RED-GREEN DEFICIENCY DETECTED ($errorCount errors / 24 plates)"
                                    }
                                    feedbackToast = "Ishihara 24-Plate Complete: $colourDiagnosis"
                                }
                            },
                            onReset = { resetCurrentModule() }
                        )
                    }

                    ClinicalModuleId.CONTRAST -> {
                        ContrastSensitivityModuleView(
                            contrastStep = contrastStep,
                            score = contrastLogCsScore,
                            onAnswer = { canRead ->
                                if (canRead && contrastStep < 6) {
                                    contrastStep++
                                } else {
                                    val logCsTable = listOf(0.0, 0.45, 0.90, 1.35, 1.65, 1.95, 2.25)
                                    contrastLogCsScore = logCsTable[contrastStep]
                                    feedbackToast = "Contrast Threshold: ${contrastLogCsScore} logCS"
                                }
                            },
                            onReset = {
                                contrastStep = 0
                                contrastLogCsScore = null
                            }
                        )
                    }

                    ClinicalModuleId.AMSLER -> {
                        AmslerGridModuleView(
                            defectPoints = amslerDefectPoints,
                            result = amslerResult,
                            onTogglePoint = { x, y ->
                                val pt = Pair(x, y)
                                if (amslerDefectPoints.contains(pt)) {
                                    amslerDefectPoints.remove(pt)
                                } else {
                                    amslerDefectPoints.add(pt)
                                }
                            },
                            onClear = { amslerDefectPoints.clear(); amslerResult = null },
                            onSave = {
                                amslerResult = if (amslerDefectPoints.isEmpty()) {
                                    "NORMAL (No Metamorphopsia / Central Scotoma)"
                                } else {
                                    "ABNORMAL: ${amslerDefectPoints.size} central macular distortion points marked"
                                }
                            }
                        )
                    }

                    ClinicalModuleId.WORTH_DOT -> {
                        WorthFourDotModuleView(
                            result = worthDotResult,
                            onSelect = { ans, diag ->
                                worthDotResult = "$ans — $diag"
                                feedbackToast = "Worth 4-Dot: $diag"
                            }
                        )
                    }

                    ClinicalModuleId.MOTILITY -> {
                        OcularMotilityModuleView(
                            isRunning = isMotilityRunning,
                            targetX = motilityAngleX,
                            targetY = motilityAngleY,
                            gazeLabel = motilityGazeLabel,
                            result = motilityResult,
                            onStart = {
                                isMotilityRunning = true
                                motilityResult = null
                            },
                            onSelectResult = { res ->
                                motilityResult = res
                                feedbackToast = "Motility finding recorded: $res"
                            }
                        )
                    }

                    ClinicalModuleId.PUPIL_RAPD -> {
                        PupillaryRapdModuleView(
                            isRunning = isPupilRunning,
                            illuminatedEye = pupilIlluminatedEye,
                            result = pupilRapdResult,
                            onStart = {
                                isPupilRunning = true
                                pupilRapdResult = null
                            },
                            onSelectResult = { res ->
                                pupilRapdResult = res
                                feedbackToast = "RAPD result: $res"
                            }
                        )
                    }

                    ClinicalModuleId.GUIDED_PROTOCOL -> {
                        GuidedProtocolView(
                            currentScreen = currentScreen,
                            pairingPin = pairingPin,
                            onNavigate = { currentScreen = it }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. EMERGENCY QUICK RELEASE BUTTON
        Button(
            onClick = {
                isEmergencyHalted = !isEmergencyHalted
                if (isEmergencyHalted) {
                    remoteClient.triggerEmergencyStop("Patient pressed Quick-Release STOP")
                    feedbackToast = "EXAMINATION PAUSED: Patient triggered Priority STOP."
                } else {
                    remoteClient.clearEmergencyHalt()
                    feedbackToast = "Examination resumed."
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isEmergencyHalted) ClinicalTheme.SeaGreenPrimary else ClinicalTheme.AlertRed
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isEmergencyHalted) "Resume Examination Protocol" else "Emergency STOP / Quick Release",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}

// =============================================================================
// SUB-COMPONENTS: VR VIEWPORT STIMULUS RENDERER
// =============================================================================
@Composable
fun RenderActiveStimulusInVr(
    eye: EyeLaterality,
    module: ClinicalModuleId,
    isStimulusActive: Boolean,
    stimulusAngleX: Double,
    stimulusAngleY: Double,
    acuityOptotypeMode: AcuityOptotypeMode = AcuityOptotypeMode.TUMBLING_E,
    acuityOrientation: OptotypeOrientation = OptotypeOrientation.UP,
    acuitySloanLetter: Char = 'E',
    acuityLogMar: Double = 0.0,
    colourPlateIndex: Int = 0,
    contrastStep: Int = 0,
    motilityAngleX: Float = 0f,
    motilityAngleY: Float = 0f,
    pupilIlluminated: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(320.dp)
            .background(Color(0xFF0F172A), CircleShape)
            .border(2.dp, Color(0xFF334155), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (module) {
            ClinicalModuleId.PERIMETRY -> {
                // Central Fixation Crosshair
                Text("+", color = Color(0xFF38BDF8), fontSize = 32.sp, fontWeight = FontWeight.Bold)

                // Active flashing Goldmann III dot
                if (isStimulusActive) {
                    val offsetX = (stimulusAngleX * 4.5).dp
                    val offsetY = (-stimulusAngleY * 4.5).dp
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(16.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            ClinicalModuleId.ACUITY -> {
                val sizeDp = max(18.0, 90.0 * 10.0.pow(acuityLogMar - 1.0)).dp
                when (acuityOptotypeMode) {
                    AcuityOptotypeMode.TUMBLING_E -> TumblingECanvas(orientation = acuityOrientation, sizeDp = sizeDp, color = Color.White)
                    AcuityOptotypeMode.LANDOLT_C -> LandoltCCanvas(orientation = acuityOrientation, sizeDp = sizeDp, color = Color.White)
                    AcuityOptotypeMode.SLOAN -> SloanLetterDisplay(letter = acuitySloanLetter, sizeDp = sizeDp, color = Color.White)
                }
            }

            ClinicalModuleId.COLOUR -> {
                IshiharaPlateBitmapView(plateNumber = colourPlateIndex + 1, diameterDp = 220.dp)
            }

            ClinicalModuleId.CONTRAST -> {
                val alpha = max(0.02f, 1f / 2f.pow(contrastStep.toFloat()))
                Text("C D E", color = Color.White.copy(alpha = alpha), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }

            ClinicalModuleId.AMSLER -> {
                AmslerCanvas(defectPoints = emptyList(), onTogglePoint = { _, _ -> }, sizeDp = 240.dp)
            }

            ClinicalModuleId.WORTH_DOT -> {
                WorthDotsCanvas(sizeDp = 220.dp)
            }

            ClinicalModuleId.MOTILITY -> {
                Box(
                    modifier = Modifier
                        .offset(x = (motilityAngleX * 100).dp, y = (-motilityAngleY * 80).dp)
                        .size(24.dp)
                        .background(Color(0xFF38BDF8), CircleShape)
                )
            }

            ClinicalModuleId.PUPIL_RAPD -> {
                val pupilSize = if (pupilIlluminated) 28.dp else 70.dp
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(if (pupilIlluminated) Color(0xFFFDE047) else Color(0xFF1E293B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(pupilSize)
                            .background(Color.Black, CircleShape)
                    )
                }
            }

            else -> {
                Text("+", color = Color(0xFF38BDF8), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================================
// MODULE 1: PERIMETRY VIEW
// =============================================================================
@Composable
fun PerimetryModuleView(
    eye: EyeLaterality,
    points: List<Pair<Double, Double>>,
    activeTrialIndex: Int,
    isRunning: Boolean,
    isStimulusActive: Boolean,
    stimulusAngleX: Double,
    stimulusAngleY: Double,
    results: Map<Int, PointStatus>,
    reactionTimes: List<Long>,
    fixationLosses: Int,
    falsePositives: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onTap: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Polar Perimetry Map Canvas
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(Color(0xFF0F172A), CircleShape)
                .border(2.dp, ClinicalTheme.SeaGreenBorder, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.width / 2f - 16f
                val scale = maxRadius / 30f // 30 degrees max field

                // Concentric Isopter Rings: 10°, 20°, 30°
                drawCircle(color = Color(0xFF1E293B), radius = 10f * scale, center = center, style = Stroke(width = 1f))
                drawCircle(color = Color(0xFF334155), radius = 20f * scale, center = center, style = Stroke(width = 1f))
                drawCircle(color = Color(0xFF475569), radius = 30f * scale, center = center, style = Stroke(width = 1.5f))

                // Orthogonal Meridia Axes
                drawLine(color = Color(0xFF1E293B), start = Offset(center.x, 10f), end = Offset(center.x, size.height - 10f), strokeWidth = 1f)
                drawLine(color = Color(0xFF1E293B), start = Offset(10f, center.y), end = Offset(size.width - 10f, center.y), strokeWidth = 1f)

                // Render Tested & Pending Points
                points.forEachIndexed { idx, pt ->
                    val ptOffset = Offset(center.x + (pt.first.toFloat() * scale), center.y - (pt.second.toFloat() * scale))
                    when (results[idx]) {
                        PointStatus.SEEN -> drawCircle(color = Color(0xFF10B981), radius = 5f, center = ptOffset)
                        PointStatus.MISSED -> {
                            drawCircle(color = Color(0xFFEF4444), radius = 5f, center = ptOffset, style = Stroke(2f))
                        }
                        PointStatus.BLIND_SPOT -> drawCircle(color = Color(0xFFFBBF24), radius = 6f, center = ptOffset)
                        else -> drawCircle(color = Color(0xFF475569), radius = 2.5f, center = ptOffset)
                    }
                }
            }

            // Central Fixation Crosshair
            Text("+", color = Color(0xFF38BDF8), fontSize = 28.sp, fontWeight = FontWeight.Bold)

            // Flashing Stimulus
            if (isStimulusActive) {
                val scale = (280f / 2f - 16f) / 30f
                val offsetX = (stimulusAngleX.toFloat() * scale).dp
                val offsetY = (-stimulusAngleY.toFloat() * scale).dp
                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(14.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress & Clinical Metrics
        val seenCount = results.values.count { it == PointStatus.SEEN }
        val missedCount = results.values.count { it == PointStatus.MISSED }
        val meanRt = if (reactionTimes.isNotEmpty()) reactionTimes.average().roundToInt() else 0

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricPill("Trial", "${activeTrialIndex + 1}/54")
            MetricPill("Seen", "$seenCount", Color(0xFF10B981))
            MetricPill("Missed", "$missedCount", Color(0xFFEF4444))
            MetricPill("Mean RT", "${meanRt} ms", Color(0xFF0284C7))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Controls - Tier 1: Action triggers, Tier 2: Large tactile response button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { if (isRunning) onPause() else onStart() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFF59E0B) else ClinicalTheme.SeaGreenPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(if (isRunning) "Pause Test" else "Start 24-2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ClinicalTheme.SeaGreenPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text("↺ Reset Test", color = ClinicalTheme.SeaGreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onTap,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
        ) {
            Text("Seen (Tap Response)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

// =============================================================================
// MODULE 2: VISUAL ACUITY VIEW (ETDRS: TUMBLING E, LANDOLT C, SLOAN LETTERS)
// =============================================================================
@Composable
fun AcuityModuleView(
    eye: EyeLaterality,
    mode: AcuityOptotypeMode,
    logMar: Double,
    orientation: OptotypeOrientation,
    sloanLetter: Char,
    trialsDone: Int,
    scoreText: String,
    onModeChange: (AcuityOptotypeMode) -> Unit,
    onResponseOrientation: (OptotypeOrientation) -> Unit,
    onResponseLetter: (Char) -> Unit,
    onReset: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf(
                AcuityOptotypeMode.TUMBLING_E to "Tumbling E",
                AcuityOptotypeMode.LANDOLT_C to "Landolt C",
                AcuityOptotypeMode.SLOAN to "Sloan Letters"
            ).forEach { (m, label) ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (mode == m) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ClinicalTheme.SeaGreenLight,
                        selectedLabelColor = ClinicalTheme.SeaGreenDark
                    ),
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Acuity Box
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(Color.White)
                .border(2.dp, ClinicalTheme.CardBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val sizeDp = max(24.0, 110.0 * 10.0.pow(logMar - 1.0)).dp
            when (mode) {
                AcuityOptotypeMode.TUMBLING_E -> TumblingECanvas(orientation = orientation, sizeDp = sizeDp, color = Color.Black)
                AcuityOptotypeMode.LANDOLT_C -> LandoltCCanvas(orientation = orientation, sizeDp = sizeDp, color = Color.Black)
                AcuityOptotypeMode.SLOAN -> SloanLetterDisplay(letter = sloanLetter, sizeDp = sizeDp, color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(scoreText, fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenDark, fontSize = 13.sp)
            OutlinedButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("↺ Reset", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Response Controls
        if (mode == AcuityOptotypeMode.TUMBLING_E || mode == AcuityOptotypeMode.LANDOLT_C) {
            Text(
                if (mode == AcuityOptotypeMode.TUMBLING_E) "Which direction is the letter E facing?" else "Where is the opening of the ring?",
                fontSize = 12.sp,
                color = ClinicalTheme.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { onResponseOrientation(OptotypeOrientation.UP) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                ) {
                    Text("▲ UP", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { onResponseOrientation(OptotypeOrientation.LEFT) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("◄ LEFT", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onResponseOrientation(OptotypeOrientation.RIGHT) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                    ) {
                        Text("► RIGHT", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { onResponseOrientation(OptotypeOrientation.DOWN) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                ) {
                    Text("▼ DOWN", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text("Select the Sloan letter displayed:", fontSize = 12.sp, color = ClinicalTheme.TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            val sloanLetters = listOf('C', 'D', 'E', 'F', 'L', 'O', 'P', 'T', 'Z')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                sloanLetters.take(5).forEach { l ->
                    Button(
                        onClick = { onResponseLetter(l) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(l.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                sloanLetters.drop(5).forEach { l ->
                    Button(
                        onClick = { onResponseLetter(l) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(l.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// MODULE 3: COLOUR VISION VIEW (24 AUTHENTIC ISHIHARA PLATES)
// =============================================================================
@Composable
fun ColourVisionModuleView(
    plateIndex: Int,
    diagnosis: String?,
    onAnswer: (String) -> Unit,
    onReset: () -> Unit
) {
    val plateNumber = plateIndex + 1
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ishihara Plate $plateNumber of 24 (Authentic Plates)",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = ClinicalTheme.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Display authentic extracted plate from assets
        IshiharaPlateBitmapView(plateNumber = plateNumber, diameterDp = 220.dp)

        Spacer(modifier = Modifier.height(12.dp))

        if (diagnosis != null) {
            Surface(
                color = ClinicalTheme.SeaGreenLight,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DIAGNOSTIC ASSESSMENT:", fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenDark, fontSize = 11.sp)
                    Text(diagnosis, fontWeight = FontWeight.Bold, color = ClinicalTheme.TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)) {
                        Text("Retest All 24 Plates", color = Color.White)
                    }
                }
            }
        } else {
            Text("Select or enter the numeral seen:", fontSize = 12.sp, color = ClinicalTheme.TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))

            val choices = when (plateNumber) {
                1 -> listOf("12", "Can't See / Blank")
                2 -> listOf("8", "3", "Can't See / Blank")
                3 -> listOf("6", "5", "Can't See / Blank")
                4 -> listOf("29", "70", "Can't See / Blank")
                5 -> listOf("57", "35", "Can't See / Blank")
                6 -> listOf("5", "2", "Can't See / Blank")
                7 -> listOf("3", "5", "Can't See / Blank")
                8 -> listOf("15", "17", "Can't See / Blank")
                9 -> listOf("74", "21", "Can't See / Blank")
                10 -> listOf("2", "Can't See / Blank")
                11 -> listOf("6", "Can't See / Blank")
                12 -> listOf("97", "Can't See / Blank")
                13 -> listOf("45", "Can't See / Blank")
                14 -> listOf("5", "Can't See / Blank")
                15 -> listOf("7", "Can't See / Blank")
                16 -> listOf("16", "Can't See / Blank")
                17 -> listOf("73", "Can't See / Blank")
                18 -> listOf("Blank (Normal)", "5 (Deficient)")
                19 -> listOf("Blank (Normal)", "2 (Deficient)")
                20 -> listOf("Blank (Normal)", "45 (Deficient)")
                21 -> listOf("Blank (Normal)", "73 (Deficient)")
                22 -> listOf("26 (Normal)", "6 (Protan)", "2 (Deutan)", "Blank")
                23 -> listOf("42 (Normal)", "2 (Protan)", "4 (Deutan)", "Blank")
                24 -> listOf("35 (Normal)", "5 (Protan)", "3 (Deutan)", "Blank")
                else -> listOf("Blank", "Other")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                choices.forEach { opt ->
                    Button(
                        onClick = { onAnswer(opt.split(" ").first()) },
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 3.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(opt, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onReset) {
                    Text("↺ Restart", color = ClinicalTheme.TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

// =============================================================================
// MODULE 4: CONTRAST SENSITIVITY VIEW
// =============================================================================
@Composable
fun ContrastSensitivityModuleView(
    contrastStep: Int,
    score: Double?,
    onAnswer: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val contrastPercentages = listOf("100%", "50%", "25%", "12.5%", "6.25%", "3.12%", "1.56%")
        val currentContrast = contrastPercentages.getOrElse(contrastStep) { "1.56%" }

        Text("Current Contrast: $currentContrast (Step ${contrastStep + 1}/7)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(width = 280.dp, height = 160.dp)
                .background(Color.White)
                .border(1.dp, ClinicalTheme.CardBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val alpha = max(0.015f, 1f / 2f.pow(contrastStep.toFloat()))
            Text(
                text = "H R Z",
                color = Color.Black.copy(alpha = alpha),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (score != null) {
            Text("Result: $score logCS", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ClinicalTheme.SeaGreenDark)
            val interp = if (score >= 1.65) "Normal Contrast Sensitivity" else "Deficient Contrast Sensitivity (Cataract/Glaucoma Risk)"
            Text(interp, fontSize = 12.sp, color = ClinicalTheme.TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)) {
                Text("Retest Contrast", color = Color.White)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onAnswer(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("I Can Read Letters", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onAnswer(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cannot Read", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// MODULE 5: CENTRAL AMSLER GRID (10°)
// =============================================================================
@Composable
fun AmslerGridModuleView(
    defectPoints: List<Pair<Int, Int>>,
    result: String?,
    onTogglePoint: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Central Amsler Grid (10°)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Fixate the center dot. Tap any square where lines look wavy, missing or dark.", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        AmslerCanvas(defectPoints = defectPoints, onTogglePoint = onTogglePoint, sizeDp = 240.dp)

        Spacer(modifier = Modifier.height(10.dp))

        if (result != null) {
            Text(result, fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenDark, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)) {
                Text("Confirm / Save Grid", color = Color.White, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onClear) {
                Text("Clear Markers", color = ClinicalTheme.TextSecondary)
            }
        }
    }
}

// =============================================================================
// MODULE 6: WORTH 4-DOT VIEW
// =============================================================================
@Composable
fun WorthFourDotModuleView(
    result: String?,
    onSelect: (String, String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Worth 4-Dot Binocular Test", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Looking with red filter OD & green filter OS, how many lights do you see?", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        WorthDotsCanvas(sizeDp = 200.dp)

        Spacer(modifier = Modifier.height(12.dp))

        if (result != null) {
            Surface(
                color = ClinicalTheme.SeaGreenLight,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ClinicalTheme.SeaGreenBorder)
            ) {
                Text(result, fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenDark, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onSelect("4 Dots", "Normal Binocular Fusion") },
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("4 Dots: Normal Sensory Fusion", color = Color.White)
                }
                Button(
                    onClick = { onSelect("2 Red Dots", "Left Eye (OS) Suppression") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2 Red Dots: Left Eye (OS) Suppressed", color = Color.White)
                }
                Button(
                    onClick = { onSelect("3 Green Dots", "Right Eye (OD) Suppression") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3 Green Dots: Right Eye (OD) Suppressed", color = Color.White)
                }
                Button(
                    onClick = { onSelect("5 Dots", "Diplopia / Strabismus") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("5 Dots: Diplopia (Double Vision)", color = Color.White)
                }
            }
        }
    }
}

// =============================================================================
// MODULE 7: OCULAR MOTILITY VIEW (BROAD-H)
// =============================================================================
@Composable
fun OcularMotilityModuleView(
    isRunning: Boolean,
    targetX: Float,
    targetY: Float,
    gazeLabel: String,
    result: String?,
    onStart: () -> Unit,
    onSelectResult: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ocular Motility (Broad-H Pursuit)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Follow the moving blue marker with both eyes without turning head.", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Broad H Guide Outline
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val w = size.width * 0.4f
                val h = size.height * 0.35f
                drawLine(Color(0xFF1E293B), Offset(cx - w, cy - h), Offset(cx - w, cy + h), 2f)
                drawLine(Color(0xFF1E293B), Offset(cx + w, cy - h), Offset(cx + w, cy + h), 2f)
                drawLine(Color(0xFF1E293B), Offset(cx - w, cy), Offset(cx + w, cy), 2f)
            }

            // Moving Target Dot
            Box(
                modifier = Modifier
                    .offset(x = (targetX * 100).dp, y = (-targetY * 80).dp)
                    .size(22.dp)
                    .background(Color(0xFF38BDF8), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Gaze: $gazeLabel", fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenDark, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(8.dp))

        if (result != null) {
            Text("Recorded: $result", fontWeight = FontWeight.Bold, color = ClinicalTheme.TextPrimary, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                ) {
                    Text(if (isRunning) "Tracking..." else "Start Motility Sequence", color = Color.White)
                }
                Button(
                    onClick = { onSelectResult("Normal Full Extraocular Motility") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Mark Normal", color = Color.White)
                }
            }
        }
    }
}

// =============================================================================
// MODULE 8: PUPILLARY RAPD (SWINGING FLASHLIGHT)
// =============================================================================
@Composable
fun PupillaryRapdModuleView(
    isRunning: Boolean,
    illuminatedEye: EyeLaterality?,
    result: String?,
    onStart: () -> Unit,
    onSelectResult: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pupillary Assessment (RAPD)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Swinging flashlight test alternates light to identify afferent defect.", fontSize = 11.sp, color = ClinicalTheme.TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Right Eye Pupil
            PupilIndicator(eyeName = "Right Eye (OD)", isIlluminated = illuminatedEye == EyeLaterality.OD)
            // Left Eye Pupil
            PupilIndicator(eyeName = "Left Eye (OS)", isIlluminated = illuminatedEye == EyeLaterality.OS)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (result != null) {
            Text("Assessment: $result", fontWeight = FontWeight.Bold, color = ClinicalTheme.SeaGreenDark, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTheme.SeaGreenPrimary)
                ) {
                    Text(if (isRunning) "Swinging Light..." else "Start Light Sequence", color = Color.White)
                }
                Button(
                    onClick = { onSelectResult("RAPD Negative (Symmetric Constriction)") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("RAPD Negative", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PupilIndicator(eyeName: String, isIlluminated: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eyeName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(if (isIlluminated) Color(0xFFFEF08A) else Color(0xFFE2E8F0), CircleShape)
                .border(2.dp, if (isIlluminated) Color(0xFFEAB308) else Color(0xFF94A3B8), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val pupilSize = if (isIlluminated) 28.dp else 60.dp
            Box(
                modifier = Modifier
                    .size(pupilSize)
                    .background(Color.Black, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(if (isIlluminated) "LIGHT BEAM ON" else "Ambient", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isIlluminated) Color(0xFFB45309) else Color(0xFF64748B))
    }
}

// =============================================================================
// MODULE 9: GUIDED 14-STAGE PROTOCOL VIEW
// =============================================================================
@Composable
fun GuidedProtocolView(
    currentScreen: ScreenId,
    pairingPin: String,
    onNavigate: (ScreenId) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenNavigator(
            currentScreen = currentScreen,
            onNavigate = onNavigate
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Center Stage Preview Card
        Card(
            modifier = Modifier
                .size(220.dp)
                .padding(8.dp),
            shape = CircleShape,
            border = BorderStroke(2.dp, ClinicalTheme.SeaGreenPrimary),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (currentScreen) {
                    ScreenId.UX_01 -> Text("PIN: $pairingPin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    ScreenId.UX_02 -> Text("ALIGN IPD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    ScreenId.UX_03 -> Text("PRACTICE TAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    ScreenId.UX_04 -> Text("COLOUR", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    ScreenId.UX_05 -> Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 48.sp)
                    ScreenId.UX_06 -> Text("VERNIER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    ScreenId.UX_07 -> Text("+", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 38.sp)
                    ScreenId.UX_08 -> Text("ANTERIOR", color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    ScreenId.UX_09 -> Text("TEAR FILM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    ScreenId.UX_10 -> Text("RETINA", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    ScreenId.UX_11 -> Text("MOTILITY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    ScreenId.UX_12 -> Text("COMPLETE", color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    ScreenId.UX_13 -> Text("STOP", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    ScreenId.UX_14 -> Text("BENCH LAB", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(currentScreen.description, fontSize = 12.sp, color = ClinicalTheme.TextSecondary, textAlign = TextAlign.Center)
    }
}

// =============================================================================
// GEOMETRIC CANVASES (TUMBLING E, LANDOLT C, SLOAN, ISHIHARA, AMSLER, WORTH DOTS)
// =============================================================================
@Composable
fun TumblingECanvas(
    orientation: OptotypeOrientation,
    sizeDp: androidx.compose.ui.unit.Dp,
    color: Color = Color.Black
) {
    Canvas(modifier = Modifier.size(sizeDp)) {
        val s = size.width
        val strokeW = s / 5f
        val drawColor = color

        when (orientation) {
            OptotypeOrientation.RIGHT -> {
                drawRect(drawColor, Offset(0f, 0f), Size(strokeW, s))
                drawRect(drawColor, Offset(0f, 0f), Size(s, strokeW))
                drawRect(drawColor, Offset(0f, s / 2f - strokeW / 2f), Size(s * 0.75f, strokeW))
                drawRect(drawColor, Offset(0f, s - strokeW), Size(s, strokeW))
            }
            OptotypeOrientation.LEFT -> {
                drawRect(drawColor, Offset(s - strokeW, 0f), Size(strokeW, s))
                drawRect(drawColor, Offset(0f, 0f), Size(s, strokeW))
                drawRect(drawColor, Offset(s * 0.25f, s / 2f - strokeW / 2f), Size(s * 0.75f, strokeW))
                drawRect(drawColor, Offset(0f, s - strokeW), Size(s, strokeW))
            }
            OptotypeOrientation.UP -> {
                drawRect(drawColor, Offset(0f, s - strokeW), Size(s, strokeW))
                drawRect(drawColor, Offset(0f, 0f), Size(strokeW, s))
                drawRect(drawColor, Offset(s / 2f - strokeW / 2f, s * 0.25f), Size(strokeW, s * 0.75f))
                drawRect(drawColor, Offset(s - strokeW, 0f), Size(strokeW, s))
            }
            OptotypeOrientation.DOWN -> {
                drawRect(drawColor, Offset(0f, 0f), Size(s, strokeW))
                drawRect(drawColor, Offset(0f, 0f), Size(strokeW, s))
                drawRect(drawColor, Offset(s / 2f - strokeW / 2f, 0f), Size(strokeW, s * 0.75f))
                drawRect(drawColor, Offset(s - strokeW, 0f), Size(strokeW, s))
            }
        }
    }
}

@Composable
fun LandoltCCanvas(
    orientation: OptotypeOrientation,
    sizeDp: androidx.compose.ui.unit.Dp,
    color: Color = Color.Black
) {
    Canvas(modifier = Modifier.size(sizeDp)) {
        val s = size.width
        val strokeW = s / 5f
        val gapSweep = 45f
        val startAngle = when (orientation) {
            OptotypeOrientation.RIGHT -> gapSweep / 2f
            OptotypeOrientation.DOWN -> 90f + gapSweep / 2f
            OptotypeOrientation.LEFT -> 180f + gapSweep / 2f
            OptotypeOrientation.UP -> 270f + gapSweep / 2f
        }
        val sweepAngle = 360f - gapSweep

        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(s - strokeW, s - strokeW),
            style = Stroke(width = strokeW)
        )
    }
}

@Composable
fun SloanLetterDisplay(
    letter: Char,
    sizeDp: androidx.compose.ui.unit.Dp,
    color: Color = Color.Black
) {
    Box(
        modifier = Modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            color = color,
            fontSize = (sizeDp.value * 0.75).sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun IshiharaPlateBitmapView(plateNumber: Int, diameterDp: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val plateClamped = plateNumber.coerceIn(1, 24)
    val bitmapState = remember(plateClamped) {
        derivedStateOf {
            try {
                val filename = "ishihara/plate_%02d.png".format(plateClamped)
                context.assets.open(filename).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    val bmp = bitmapState.value
    Box(
        modifier = Modifier
            .size(diameterDp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color(0xFF94A3B8), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Authentic Ishihara Plate $plateClamped",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            IshiharaPlateCanvas(plateIndex = (plateClamped - 1).coerceIn(0, 3), diameterDp = diameterDp)
        }
    }
}

@Composable
fun IshiharaPlateCanvas(plateIndex: Int, diameterDp: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(diameterDp)
            .background(Color(0xFFE2E8F0), CircleShape)
            .border(2.dp, Color(0xFF94A3B8), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 8f

            // Pseudo-random deterministic dot matrix
            val seed = 42L + plateIndex
            val rand = Random(seed)

            for (i in 0..180) {
                val rDist = rand.nextDouble(0.0, radius.toDouble() - 6.0).toFloat()
                val angle = rand.nextDouble(0.0, 2 * PI).toFloat()
                val dotX = center.x + rDist * cos(angle)
                val dotY = center.y + rDist * sin(angle)
                val dotR = rand.nextDouble(4.0, 10.0).toFloat()

                // Color assign: background dots vs numeral dots
                val isInCenterNum = (rDist < radius * 0.55f && abs(dotX - center.x) < radius * 0.45f)
                val dotColor = if (isInCenterNum) {
                    when (plateIndex) {
                        0 -> Color(0xFFEA580C) // Orange numeral 12
                        1 -> Color(0xFFDC2626) // Red numeral 8
                        2 -> Color(0xFFF97316) // Orange numeral 29
                        else -> Color(0xFF16A34A) // Green numeral 74
                    }
                } else {
                    when (plateIndex) {
                        0 -> if (rand.nextBoolean()) Color(0xFF15803D) else Color(0xFF64748B) // Green background
                        1 -> if (rand.nextBoolean()) Color(0xFF16A34A) else Color(0xFF84CC16) // Muted green
                        2 -> if (rand.nextBoolean()) Color(0xFF0284C7) else Color(0xFF14B8A6) // Blue-green
                        else -> if (rand.nextBoolean()) Color(0xFFB45309) else Color(0xFFEA580C) // Orange background
                    }
                }
                drawCircle(color = dotColor, radius = dotR, center = Offset(dotX, dotY))
            }
        }

        // Distinct clear numeral label for clinical verification
        val plateNumber = when (plateIndex) {
            0 -> "12"
            1 -> "8"
            2 -> "29"
            else -> "74"
        }
        Text(
            text = plateNumber,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun AmslerCanvas(
    defectPoints: List<Pair<Int, Int>>,
    onTogglePoint: (Int, Int) -> Unit,
    sizeDp: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(Color.Black, RoundedCornerShape(8.dp))
            .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cellSize = size.width / 10f
                    val gridX = (offset.x / cellSize).toInt().coerceIn(0, 9)
                    val gridY = (offset.y / cellSize).toInt().coerceIn(0, 9)
                    onTogglePoint(gridX, gridY)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / 10f
            val cellH = size.height / 10f

            for (i in 1..9) {
                drawLine(Color.White.copy(alpha = 0.85f), Offset(i * cellW, 0f), Offset(i * cellW, size.height), 1f)
                drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, i * cellH), Offset(size.width, i * cellH), 1f)
            }

            // Defect markers
            defectPoints.forEach { pt ->
                drawRect(
                    color = Color(0xFFEF4444).copy(alpha = 0.65f),
                    topLeft = Offset(pt.first * cellW, pt.second * cellH),
                    size = Size(cellW, cellH)
                )
            }

            // Central White Fixation Dot
            drawCircle(Color.White, radius = 5f, center = Offset(size.width / 2f, size.height / 2f))
        }
    }
}

@Composable
fun WorthDotsCanvas(sizeDp: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(Color.Black, CircleShape)
            .border(2.dp, Color(0xFF334155), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            // Top: Red Dot
            Box(modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape))

            // Middle: 2 Green Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(28.dp).background(Color(0xFF10B981), CircleShape))
                Box(modifier = Modifier.size(28.dp).background(Color(0xFF10B981), CircleShape))
            }

            // Bottom: White Dot
            Box(modifier = Modifier.size(28.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
fun MetricPill(label: String, value: String, valueColor: Color = ClinicalTheme.TextPrimary) {
    Surface(
        color = ClinicalTheme.BackgroundWhite,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ClinicalTheme.CardBorder)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(label, fontSize = 9.sp, color = ClinicalTheme.TextSecondary, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.ExtraBold)
        }
    }
}
