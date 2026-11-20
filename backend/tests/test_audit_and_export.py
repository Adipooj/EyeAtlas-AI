"""EyeAtlas VR - Audit Trail & Clinical Report Tests.
Verifies append-only audit event logging and CDSCO regulatory status gating on reports.
"""
import pytest

def test_audit_event_logging_and_report_generation(client, clinician_apex_token, admin_token):
    # 1. Create patient and examination session
    p_resp = client.post(
        "/api/v1/patients",
        json={
            "patientId": "P-AUDIT-001",
            "ageBand": "ADULT_18_TO_64",
            "hasDiabetes": False,
            "symptoms": ["RED_EYE"],
            "currentCorrection": "UNAIDED",
            "comorbidityFlags": {}
        },
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert p_resp.status_code == 201

    s_resp = client.post(
        "/api/v1/sessions",
        json={
            "sessionId": "S-AUDIT-001",
            "patientId": "P-AUDIT-001",
            "patientPhoneId": "PHONE-PATIENT-01",
            "clinicianPhoneId": "PHONE-NORD-CE3",
            "responsePhoneId": "PHONE-NORD-CE3",
            "responseRole": "RESPONSE_PAD",
            "mode": "SIMULATED",
            "consentGranted": True,
            "startedAtNs": 1700000000000000000,
            "state": "COMPLETE"
        },
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert s_resp.status_code == 201

    # 2. Retrieve report and verify CDSCO regulatory lock is strictly reported
    rep_resp = client.get(
        "/api/v1/reports/S-AUDIT-001",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert rep_resp.status_code == 200
    report_data = rep_resp.json()
    assert report_data["regulatoryStatus"] == "INVESTIGATIONAL_ONLY_NOT_FOR_COMMERCIAL_DISTRIBUTION"
    assert report_data["isReleaseApproved"] is False
    assert len(report_data["reportDigestSha256"]) == 64

    # 3. Sign the report
    sign_resp = client.post(
        "/api/v1/reports/S-AUDIT-001/sign",
        json={
            "clinicianNotes": "Mild conjunctival injection; non-urgent follow-up.",
            "decision": "ROUTINE_REVIEW"
        },
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert sign_resp.status_code == 200
    assert sign_resp.json()["status"] == "SIGNED"

    # 4. Verify Administrator can query audit trail and see the signed event
    audit_resp = client.get(
        "/api/v1/admin/audit-logs",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert audit_resp.status_code == 200
    events = audit_resp.json()
    actions = [ev["action"] for ev in events]
    assert "SIGN_CLINICAL_REPORT" in actions
    assert "CREATE_PATIENT" in actions
