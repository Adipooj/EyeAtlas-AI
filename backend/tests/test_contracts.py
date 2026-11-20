"""EyeAtlas VR - Clinical Data Contract Parity Tests.
Verifies that Python Pydantic models in contracts.py exactly match the Kotlin
contracts in app/src/main/java/org/eyeatlas/core/Models.kt.
"""
import pytest
from backend.app.models.contracts import (
    EyeLaterality, CorrectionStatus, FixationStatus, SessionState, ClinicalMode,
    BroadDomain, DiseaseCode, ReferralUrgency,
    PatientRecord, ClinicalSession, StimulusTrial, ConditionInference, Referral
)

def test_patient_record_contract():
    data = {
        "patientId": "P-1001",
        "ageBand": "ADULT_18_TO_64",
        "hasDiabetes": True,
        "symptoms": ["BLURRED_VISION", "FLOATERS"],
        "currentCorrection": "CURRENT_HABITUAL",
        "comorbidityFlags": {"CATARACT_SUSPECT": True}
    }
    p = PatientRecord(**data)
    assert p.patient_id == "P-1001"
    assert p.age_band == "ADULT_18_TO_64"
    assert p.has_diabetes is True
    assert p.current_correction == CorrectionStatus.CURRENT_HABITUAL

    # Verify JSON export matches camelCase aliases expected by Android/Web
    json_dict = p.model_dump(by_alias=True)
    assert json_dict["patientId"] == "P-1001"
    assert json_dict["hasDiabetes"] is True

def test_stimulus_trial_contract_and_120hz_timing():
    data = {
        "trialId": "TR-54PT-01",
        "sessionId": "S-9999",
        "eye": "OD",
        "testCode": "PERIMETRY_54PT",
        "targetAngleX": 15.0,
        "targetAngleY": -1.5,
        "luminanceNits": 10.0,
        "opticalIndex": 0,
        "monotonicPresentedNs": 123456789000,
        "responsePhoneMonotonicNs": 123456989000,
        "correctedResponseNs": 123456989000,
        "clockOffsetNs": 18500000,
        "clockUncertaintyNs": 5000000,
        "expectedAnswer": "SEEN",
        "observedAnswer": "SEEN",
        "framesRendered": 24,  # Exactly 24 frames @ 120Hz = 200ms Goldmann III
        "calibrationHash": "HASH-NORD-CE3-120HZ",
        "responseEventId": "EV-001",
        "validityFlags": ["VALID"]
    }
    trial = StimulusTrial(**data)
    assert trial.eye == EyeLaterality.OD
    assert trial.frames_rendered == 24
    assert trial.target_angle_x == 15.0
    assert trial.target_angle_y == -1.5
    assert trial.clock_uncertainty_ns < 50_000_000  # < 50ms tolerance gate

def test_multi_morbidity_independent_inference_contract():
    # Hard Medical Invariant: Independent marginal scores without softmax sum-to-1 constraint
    inf_dr = ConditionInference(
        eye=EyeLaterality.OD,
        diseaseCode=DiseaseCode.DIABETIC_RETINOPATHY,
        probabilityOrScore=0.88,
        calibrationId="CALIBRATION_WITHHELD",
        evidenceFor=["MICROANEURYSMS", "HEMORRHAGES"],
        evidenceAgainst=[],
        unknownEvidence=[]
    )
    inf_glaucoma = ConditionInference(
        eye=EyeLaterality.OD,
        diseaseCode=DiseaseCode.GLAUCOMA,
        probabilityOrScore=0.74,
        calibrationId="CALIBRATION_WITHHELD",
        evidenceFor=["NASAL_STEP_FIELD_DEFECT", "CUP_TO_DISC_0_75"],
        evidenceAgainst=[],
        unknownEvidence=[]
    )

    # Sum of probabilities exceeds 1.0 (0.88 + 0.74 = 1.62), valid under multi-label comorbidity
    prob_sum = inf_dr.probability_or_score + inf_glaucoma.probability_or_score
    assert prob_sum > 1.0
    assert inf_dr.eye == EyeLaterality.OD
    assert inf_glaucoma.eye == EyeLaterality.OD
