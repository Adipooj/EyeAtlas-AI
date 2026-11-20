package org.eyeatlas

import org.eyeatlas.core.ClinicalStateMachine
import org.eyeatlas.core.EyeLaterality
import org.eyeatlas.core.SessionEvent
import org.eyeatlas.core.SessionState
import org.eyeatlas.sensors.AdapterRingType
import org.eyeatlas.sensors.Calibration
import org.eyeatlas.sensors.OpticalConfigurationState
import org.eyeatlas.sensors.OpticalInterlockManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for VRBox Detachable Lens Engineering and Optical Interlocks (CR-002-G).
 * Enforces optical physics invariants, compound lens hazard protection, and recalibration triggers.
 */
class OpticalInterlockTests {

    @Test
    fun testDefaultOpticalConfigurationState() {
        val manager = OpticalInterlockManager()
        assertEquals(OpticalConfigurationState.BOTH_VR_LENSES_INSTALLED, manager.currentOpticalState)
        assertEquals(AdapterRingType.STANDARD_VR_EYEPIECE, manager.odRingType)
        assertEquals(AdapterRingType.STANDARD_VR_EYEPIECE, manager.osRingType)

        val telemetry = manager.getTelemetryPayload()
        assertEquals("BOTH_VR_LENSES_INSTALLED", telemetry["optical_state"])
        assertEquals("STANDARD_VR_EYEPIECE", telemetry["od_ring_type"])
    }

    @Test
    fun testWideFieldVrTestsPermittedWithBothLenses() {
        val manager = OpticalInterlockManager()

        val odPerimetry = manager.verifyInterlockForTest("PERIMETRY_54PT", EyeLaterality.OD)
        assertTrue(odPerimetry.isPermitted)

        val osPerimetry = manager.verifyInterlockForTest("PERIMETRY_24_2", EyeLaterality.OS)
        assertTrue(osPerimetry.isPermitted)

        val amsler = manager.verifyInterlockForTest("AMSLER_GRID", EyeLaterality.OD)
        assertTrue(amsler.isPermitted)

        val ishihara = manager.verifyInterlockForTest("COLOUR_ISHIHARA", EyeLaterality.OS)
        assertTrue(ishihara.isPermitted)

        val contrast = manager.verifyInterlockForTest("CONTRAST_PFE", EyeLaterality.OD)
        assertTrue(contrast.isPermitted)
    }

    @Test
    fun testWideFieldVrTestRefusedWhenTargetEyeLensRemoved() {
        val manager = OpticalInterlockManager()
        // Physician removes OD eyepiece for external direct inspection
        manager.setOpticalConfiguration(OpticalConfigurationState.OD_LENS_REMOVED)

        // Attempting perimetry on OD must be blocked: lack of VR eyepiece prevents collimation to infinity
        val odCheck = manager.verifyInterlockForTest("PERIMETRY_24_2", EyeLaterality.OD)
        assertFalse(odCheck.isPermitted)
        assertEquals("INTERLOCK_VIOLATION_VR_LENS_REQUIRED", odCheck.violationCode)
        assertTrue(odCheck.reason.contains("requires standard VR eyepiece installed for OD"))

        // Contralateral OS eyepiece is still seated: perimetry on OS must remain permitted
        val osCheck = manager.verifyInterlockForTest("PERIMETRY_24_2", EyeLaterality.OS)
        assertTrue(osCheck.isPermitted)
    }

    @Test
    fun testAnteriorInspectionRefusedWhenVrEyepieceInstalled() {
        val manager = OpticalInterlockManager()

        // With standard VR eyepieces installed, direct anterior camera capture is blocked to prevent compound distortion
        val anteriorBlocked = manager.verifyInterlockForTest("ANTERIOR_TORCH", EyeLaterality.OD)
        assertFalse(anteriorBlocked.isPermitted)
        assertEquals("INTERLOCK_VIOLATION_VR_LENS_MUST_BE_REMOVED", anteriorBlocked.violationCode)
        assertTrue(anteriorBlocked.reason.contains("requires VR eyepiece removed"))

        // Once OD lens is removed, anterior capture on OD is permitted
        manager.setOpticalConfiguration(
            OpticalConfigurationState.OD_LENS_REMOVED,
            odRing = AdapterRingType.OPEN_APERTURE_NO_LENS
        )
        val anteriorPermitted = manager.verifyInterlockForTest("ANTERIOR_TORCH", EyeLaterality.OD)
        assertTrue(anteriorPermitted.isPermitted)
    }

    @Test
    fun testRetinalIndirect20dRefusedWhenVrEyepieceInstalled() {
        val manager = OpticalInterlockManager()

        // Gullstrand compound lens calculation: 20D condensing lens + 14.3D VR eyepiece causes focal collapse to 18mm
        val retinalBlocked = manager.verifyInterlockForTest("RETINAL_20D_FUNDUS", EyeLaterality.OD)
        assertFalse(retinalBlocked.isPermitted)
        assertEquals("INTERLOCK_VIOLATION_20D_ADAPTER_REQUIRED", retinalBlocked.violationCode)
        assertTrue(retinalBlocked.reason.contains("compound lens collapses focal plane to 18mm"))

        // Once 20D condensing adapter is mounted in place of VR eyepiece, fundus examination is permitted
        manager.setOpticalConfiguration(
            OpticalConfigurationState.EXTERNAL_ADAPTER_20D_MOUNTED,
            odRing = AdapterRingType.INDIRECT_20D_CONDENSING
        )
        val retinalPermitted = manager.verifyInterlockForTest("RETINAL_20D_FUNDUS", EyeLaterality.OD)
        assertTrue(retinalPermitted.isPermitted)
    }

    @Test
    fun testPinholeDifferentialRequiresPinholeAdapter() {
        val manager = OpticalInterlockManager()

        // Standard VR lens invalidates pinhole 1.2mm depth-of-field geometry
        val pinholeBlocked = manager.verifyInterlockForTest("ACUITY_PINHOLE_DIFFERENTIAL", EyeLaterality.OD)
        assertFalse(pinholeBlocked.isPermitted)
        assertEquals("INTERLOCK_VIOLATION_PINHOLE_ADAPTER_REQUIRED", pinholeBlocked.violationCode)

        // Mounting the calibrated 1.2mm pinhole adapter enables the test
        manager.setOpticalConfiguration(
            OpticalConfigurationState.EXTERNAL_ADAPTER_PINHOLE_MOUNTED,
            odRing = AdapterRingType.PINHOLE_1_2MM_OCCLUDER
        )
        val pinholePermitted = manager.verifyInterlockForTest("ACUITY_PINHOLE_DIFFERENTIAL", EyeLaterality.OD)
        assertTrue(pinholePermitted.isPermitted)
    }

    @Test
    fun testUnknownOpticalStateBlocksAllTests() {
        val manager = OpticalInterlockManager(
            currentOpticalState = OpticalConfigurationState.UNKNOWN_UNCALIBRATED
        )

        val check1 = manager.verifyInterlockForTest("PERIMETRY_24_2", EyeLaterality.OD)
        assertFalse(check1.isPermitted)
        assertEquals("INTERLOCK_UNCALIBRATED_STATE", check1.violationCode)

        val check2 = manager.verifyInterlockForTest("ANTERIOR_TORCH", EyeLaterality.OD)
        assertFalse(check2.isPermitted)
        assertEquals("INTERLOCK_UNCALIBRATED_STATE", check2.violationCode)
    }

    @Test
    fun testStateChangeInvalidatesCalibrationHash() {
        val calibration = Calibration()
        val profile = Calibration.createSignedProfile()
        calibration.loadAndVerifyProfile(profile, "HW-VR-CHASSIS-v1.1")
        assertTrue(calibration.isCalibrated)
        assertNotEquals("INVALID", calibration.calibrationHash)

        val manager = OpticalInterlockManager()
        // Changing optical configuration must invalidate calibration
        manager.setOpticalConfiguration(
            OpticalConfigurationState.OD_LENS_REMOVED,
            calibration = calibration
        )

        assertFalse(calibration.isCalibrated)
        assertEquals("INVALID", calibration.calibrationHash)
    }

    @Test
    fun testClinicalStateMachineOpticalStateChangedTrigger() {
        val sm = ClinicalStateMachine()
        sm.processEvent(SessionEvent.TriagePassed)
        sm.processEvent(SessionEvent.CalibrationVerified)
        assertEquals(SessionState.FUNCTIONAL_TESTS, sm.getState())

        // Physical lens change mid-test triggers transition back to DEVICE_CALIBRATION
        val res = sm.processEvent(SessionEvent.OpticalStateChanged("OD_LENS_REMOVED"))
        assertTrue(res.isSuccess)
        assertEquals(SessionState.DEVICE_CALIBRATION, sm.getState())
    }
}
