"""EyeAtlas VR - Phase X02 Android Client & FastAPI Integration Tests.
Verifies bidirectional synchronization between Android BackendSyncAdapter payloads and FastAPI backend.
"""
import pytest

def test_android_adapter_push_and_query(client, clinician_apex_token):
    # 1. Android BackendSyncAdapter pushes batched patient & trial
    android_batch = {
        "batchId": "BATCH-ANDROID-NORD-001",
        "clientDeviceId": "PHONE-NORD-CE3-5G",
        "sequenceNum": 101,
        "clientTimestampNs": 1700000000000000000,
        "patients": [
            {
                "patientId": "P-NORD-201",
                "ageBand": "ADULT_18_TO_64",
                "hasDiabetes": True,
                "symptoms": ["BLURRED_VISION", "RED_EYE"],
                "currentCorrection": "UNAIDED",
                "version": 1
            }
        ],
        "sessions": [
            {
                "sessionId": "S-NORD-201",
                "patientId": "P-NORD-201",
                "patientPhoneId": "PHONE-NORD-CE3-5G",
                "clinicianPhoneId": "PHONE-NORD-CE3-5G",
                "responsePhoneId": "PHONE-NORD-CE3-5G",
                "responseRole": "RESPONSE_PAD",
                "mode": "CLINICAL",
                "startedAtNs": 1700000000000000000,
                "state": "COMPLETE"
            }
        ],
        "trials": [
            {
                "trialId": "TR-NORD-01",
                "sessionId": "S-NORD-201",
                "eye": "OD",
                "testCode": "PERIMETRY_54PT",
                "targetAngleX": 15.0,
                "targetAngleY": -1.5,
                "luminanceNits": 10.0,
                "opticalIndex": 0,
                "monotonicPresentedNs": 1700000000100000000,
                "framesRendered": 24,
                "calibrationHash": "HASH-NORD-120HZ"
            }
        ],
        "images": [],
        "measurements": [
            {
                "measurementId": "M-NORD-01",
                "sessionId": "S-NORD-201",
                "eye": "OD",
                "method": "LOGMAR",
                "value": 0.00,
                "units": "logMAR",
                "ciLower": 0.00,
                "ciUpper": 0.05,
                "validity": True
            }
        ],
        "findings": [],
        "inferences": [],
        "referrals": []
    }

    push_resp = client.post(
        "/api/v1/sync/push",
        json=android_batch,
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert push_resp.status_code == 200
    push_data = push_resp.json()
    assert push_data["status"] == "SUCCESS"
    assert push_data["processedCount"] >= 3

    # 2. Query patient from REST endpoint
    p_resp = client.get(
        "/api/v1/patients/P-NORD-201",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert p_resp.status_code == 200
    p_data = p_resp.json()
    assert p_data["patientId"] == "P-NORD-201"
    assert p_data["hasDiabetes"] is True

    # 3. Query session details from REST endpoint
    s_resp = client.get(
        "/api/v1/sessions/S-NORD-201",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert s_resp.status_code == 200
    s_data = s_resp.json()
    assert s_data["sessionId"] == "S-NORD-201"
    assert s_data["trialCount"] == 1
    assert s_data["measurementCount"] == 1

    # 4. Pull delta changes
    pull_resp = client.get(
        "/api/v1/sync/pull?sinceTimestampNs=0",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert pull_resp.status_code == 200
    pull_data = pull_resp.json()
    patient_ids = [p["patientId"] for p in pull_data["patients"]]
    assert "P-NORD-201" in patient_ids
