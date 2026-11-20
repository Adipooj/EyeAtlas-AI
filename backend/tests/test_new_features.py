"""Test suite for new clinical session functional test endpoints."""
import pytest
from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

def get_auth_token():
    resp = client.post("/api/v1/auth/token", json={"username": "doc_apex", "password": "DocPass123!"})
    assert resp.status_code == 200
    return resp.json()["access_token"]

def test_health_check_endpoint():
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "HEALTHY"
    assert data["databaseConnected"] is True

def test_submit_perimetry():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "eye": "OD",
        "pattern": "24-2",
        "total_points": 54,
        "points_seen": 52,
        "superior_seen": 26,
        "inferior_seen": 26,
        "mean_reaction_time_ms": 315.4,
        "false_positive_rate": 3.7,
        "scotoma_coordinates": [{"x": 15, "y": -3}]
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/perimetry", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["pointsSeen"] == 52
    assert data["severity"] == "NORMAL"

def test_submit_amsler():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "eye": "OS",
        "metamorphopsia": True,
        "scotoma": False,
        "distorted_points_count": 3,
        "notes": "Paracentral wavy lines"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/amsler", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["severity"] == "MODERATE_DISTORTION"

def test_submit_acuity():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "eye": "OD",
        "method": "TUMBLING_E",
        "logmar": 0.1,
        "snellen": "6/7.5",
        "contrast_level": "100%"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/acuity", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["logmar"] == 0.1

def test_clinician_audit_trail_access():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    resp = client.get("/api/v1/admin/audit-logs", headers=headers)
    assert resp.status_code == 200
    logs = resp.json()
    assert isinstance(logs, list)
    assert len(logs) > 0

def test_submit_colour_vision_2d():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "eye": "OU",
        "test_type": "ISHIHARA_PSEUDOISOCHROMATIC",
        "total_plates": 8,
        "plates_correct": 8,
        "deficiency_detected": False,
        "classification": "NORMAL",
        "mode": "WEB_2D"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/colour-vision", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["classification"] == "NORMAL"
    assert data["mode"] == "WEB_2D"

def test_submit_colour_vision_vr():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "eye": "OU",
        "test_type": "ISHIHARA_PSEUDOISOCHROMATIC",
        "total_plates": 8,
        "plates_correct": 4,
        "deficiency_detected": True,
        "classification": "RED_GREEN_PROTANOPIA",
        "mode": "VR_HEADSET"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/colour-vision", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["mode"] == "VR_HEADSET"
    assert data["severity"] == "MODERATE_DEFECT"

def test_submit_contrast_sensitivity():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "eye": "OD",
        "method": "PELLI_ROBSON",
        "log_contrast_sensitivity": 1.95,
        "triplets_seen": 14,
        "severity": "NORMAL"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/contrast-sensitivity", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["logCS"] == 1.95

def test_submit_binocular_vision():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "worth_four_dot_response": "FOUR_DOTS_FUSION",
        "stereoacuity_arcsec": 40.0,
        "fusion_status": "NORMAL_FUSION",
        "depth_perception_grade": "NORMAL"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/binocular-vision", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["fusionStatus"] == "NORMAL_FUSION"

def test_submit_ocular_motility():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "smooth_pursuit_grade": "SMOOTH_FULL",
        "nystagmus_detected": False,
        "cardinal_gaze_deficit": False,
        "affected_muscles": []
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/ocular-motility", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["smoothPursuit"] == "SMOOTH_FULL"

def test_submit_pupillary_assessment():
    token = get_auth_token()
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "rapd_detected": False,
        "rapd_grade": "NONE",
        "pupil_size_photopic_od_mm": 3.2,
        "pupil_size_photopic_os_mm": 3.2,
        "pupil_size_scotopic_od_mm": 5.4,
        "pupil_size_scotopic_os_mm": 5.4,
        "anisocoria_present": False,
        "light_reflex_speed": "BRISK"
    }
    resp = client.post("/api/v1/sessions/S-DEMO-01/pupillary-assessment", json=payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "RECORDED"
    assert data["rapdDetected"] is False
