"""EyeAtlas VR - Offline-First Sync & Idempotency Tests.
Verifies batch synchronization, duplicate event idempotency, and deterministic conflict handling.
"""
import pytest

def test_sync_push_and_idempotency(client, clinician_apex_token):
    batch_payload = {
        "batchId": "BATCH-OFFLINE-001",
        "clientDeviceId": "PHONE-NORD-CE3-5G",
        "sequenceNum": 1,
        "clientTimestampNs": 1700000000000000000,
        "patients": [
            {
                "patientId": "P-SYNC-101",
                "ageBand": "GERIATRIC_65_PLUS",
                "hasDiabetes": True,
                "symptoms": ["DIMINISHED_NIGHT_VISION"],
                "currentCorrection": "BEST_CORRECTED",
                "comorbidityFlags": {"CATARACT_SUSPECT": True},
                "version": 1
            }
        ],
        "sessions": [
            {
                "sessionId": "S-SYNC-101",
                "patientId": "P-SYNC-101",
                "patientPhoneId": "PHONE-PATIENT-01",
                "clinicianPhoneId": "PHONE-NORD-CE3-5G",
                "responsePhoneId": "PHONE-NORD-CE3-5G",
                "responseRole": "RESPONSE_PAD",
                "mode": "SIMULATED",
                "state": "COMPLETE",
                "startedAtNs": 1700000000000000000
            }
        ],
        "trials": [
            {
                "trialId": "TR-SYNC-01",
                "sessionId": "S-SYNC-101",
                "eye": "OD",
                "testCode": "PERIMETRY_54PT",
                "targetAngleX": 15.0,
                "targetAngleY": -1.5,
                "luminanceNits": 10.0,
                "opticalIndex": 0,
                "monotonicPresentedNs": 1000000,
                "framesRendered": 24,
                "calibrationHash": "HASH-120HZ"
            }
        ],
        "measurements": [
            {
                "measurementId": "M-SYNC-01",
                "sessionId": "S-SYNC-101",
                "eye": "OD",
                "method": "LOGMAR_LANDOLT_C",
                "value": 0.10,
                "units": "logMAR",
                "ciLower": 0.05,
                "ciUpper": 0.15,
                "validity": True
            }
        ],
        "findings": [],
        "inferences": [],
        "referrals": []
    }

    # 1. First sync push -> must succeed with processedCount >= 3
    resp1 = client.post(
        "/api/v1/sync/push",
        json=batch_payload,
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert resp1.status_code == 200
    res1_data = resp1.json()
    assert res1_data["status"] == "SUCCESS"
    assert res1_data["processedCount"] >= 3
    assert res1_data["conflictCount"] == 0

    # 2. Second identical sync push -> must return IDEMPOTENT_SUCCESS with 0 new mutations
    resp2 = client.post(
        "/api/v1/sync/push",
        json=batch_payload,
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert resp2.status_code == 200
    res2_data = resp2.json()
    assert res2_data["status"] == "IDEMPOTENT_SUCCESS"
    assert res2_data["processedCount"] == 0

    # 3. Verify patient exists in database
    get_p = client.get(
        "/api/v1/patients/P-SYNC-101",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert get_p.status_code == 200
    assert get_p.json()["patientId"] == "P-SYNC-101"
