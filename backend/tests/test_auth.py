"""EyeAtlas VR - Authentication & RBAC Tests.
Tests token generation, bad password rejection, unauthorized access, and role escalation.
"""
import pytest

def test_health_endpoint(client):
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "HEALTHY"
    assert data["databaseConnected"] is True

def test_login_valid_credentials(client):
    response = client.post("/api/v1/auth/token", json={
        "username": "doc_apex",
        "password": "DocPass123!"
    })
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["role"] == "CLINICIAN"
    assert data["tenant_id"] == "TENANT-APEX"

def test_login_invalid_password(client):
    response = client.post("/api/v1/auth/token", json={
        "username": "doc_apex",
        "password": "WrongPassword!"
    })
    assert response.status_code == 401
    assert "Incorrect username or password" in response.json()["detail"]

def test_unauthenticated_request_rejected(client):
    response = client.get("/api/v1/patients")
    assert response.status_code == 401

def test_role_escalation_forbidden(client, clinician_apex_token):
    # Clinician trying to call Administrator endpoint -> must receive 403 Forbidden
    response = client.get(
        "/api/v1/admin/tenants",
        headers={"Authorization": f"Bearer {clinician_apex_token}"}
    )
    assert response.status_code == 403
    assert "Access forbidden" in response.json()["detail"]

def test_admin_access_allowed(client, admin_token):
    # Administrator calling admin endpoint -> must receive 200 OK
    response = client.get(
        "/api/v1/admin/tenants",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert response.status_code == 200
    tenants = response.json()
    assert len(tenants) >= 2
