"""EyeAtlas VR - Multi-Tenant Isolation Tests.
Verifies that data within Tenant A is strictly inaccessible to Tenant B.
"""
import pytest

def test_cross_tenant_isolation(client, clinician_apex_token, clinician_rural_token):
    # 1. Clinician in Apex Hospital creates a patient
    create_resp = client.post(
        "/api/v1/patients",
        json={
            "patientId": "P-APEX-001",
            "ageBand": "ADULT_18_TO_64",
            "hasDiabetes": True,
            "symptoms": ["BLURRED_VISION"],
            "currentCorrection": "UNAIDED",
            "comorbidityFlags": {}
        },
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert create_resp.status_code == 201

    # 2. Clinician in Apex Hospital can retrieve the patient
    get_apex = client.get(
        "/api/v1/patients/P-APEX-001",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert get_apex.status_code == 200
    assert get_apex.json()["patientId"] == "P-APEX-001"

    # 3. Clinician in Rural Clinic tries to retrieve Apex's patient -> 404 NOT FOUND (zero data leak)
    get_rural = client.get(
        "/api/v1/patients/P-APEX-001",
        headers={"Authorization": f"Bearer {clinician_rural_token}"}
    )
    assert get_rural.status_code == 404
    assert get_rural.json()["detail"] == "Patient not found"

    # 4. Clinician in Rural Clinic lists patients -> sees only Rural patients (not P-APEX-001)
    list_rural = client.get(
        "/api/v1/patients",
        headers={"Authorization": f"Bearer {clinician_rural_token}"}
    )
    assert list_rural.status_code == 200
    rural_patient_ids = [p["patientId"] for p in list_rural.json()]
    assert "P-APEX-001" not in rural_patient_ids
