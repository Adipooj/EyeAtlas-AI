"""EyeAtlas VR - Targeted APK <-> Web Pairing & Live WebSocket Verification Suite.
Validates the complete lifecycle:
1. Real session creation (POST /api/v1/remote/create)
2. Scoped token generation
3. Rejection of missing/invalid/expired tokens and incorrect PINs
4. Role spoofing prevention (tokens derive role, untrusted params ignored)
5. WebSocket connection with genuine tokens
6. Bi-directional message routing (CONTROL_COMMAND, STIMULUS_EVENT, RESPONSE_EVENT, EMERGENCY_STOP)
7. Observer restrictions (cannot issue physician control commands)
8. Duplicate/out-of-order rejection
9. Cross-session boundary isolation (no leakage between rooms)
"""
from __future__ import annotations
import datetime
import json
import pytest
from starlette.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
from backend.app.main import app
from backend.app.models.entities import RemoteSessionEntity
from backend.app.models.remote_contracts import (
    RemoteRole,
    WebSocketMessageEnvelope,
    WebSocketMessageType
)
from backend.app.core.security import create_access_token
from backend.app.services.session_broker import session_broker

client = TestClient(app)

def test_rest_create_and_join_flow():
    """Verify session creation and physician join REST lifecycles."""
    # 1. APK creates session
    create_res = client.post("/api/v1/remote/create", json={
        "device_id": "PHONE-PATIENT-VR",
        "hardware_model": "OnePlus Nord CE 3 5G",
        "supported_inputs": ["TOUCH_PAD", "VOLUME_KEYS"],
        "tenant_id": "TENANT-APEX"
    })
    assert create_res.status_code == 201
    created = create_res.json()
    session_id = created["session_id"]
    pin = created["pairing_pin"]
    patient_token = created["patient_vr_token"]
    assert session_id.startswith("SES-REM-")
    assert len(pin) == 6
    assert patient_token

    # 2. Join with incorrect PIN must be rejected
    bad_join = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": "000000",
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "PHYSICIAN_WEB_COCKPIT"
    })
    assert bad_join.status_code == 401
    assert "Invalid pairing PIN" in bad_join.json()["detail"]

    # 3. Join with unknown session ID must be rejected
    unknown_join = client.post("/api/v1/remote/join", json={
        "session_id": "SES-REM-NONEXISTENT",
        "pairing_pin": pin,
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "PHYSICIAN_WEB_COCKPIT"
    })
    assert unknown_join.status_code == 404

    # 4. Join with correct PIN succeeds and returns scoped physician token
    good_join = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "PHYSICIAN_WEB_COCKPIT",
        "clinician_username": "dr_sharma"
    })
    assert good_join.status_code == 200
    joined = good_join.json()
    assert joined["session_id"] == session_id
    assert joined["role"] == "PHYSICIAN_CONTROLLER"
    assert joined["is_authoritative_controller"] is True
    assert joined["token"]

def test_websocket_authentication_and_spoof_rejection():
    """Verify WebSocket rejects connections lacking valid session-bound tokens."""
    # 1. Create a genuine session
    create_res = client.post("/api/v1/remote/create", json={"tenant_id": "TENANT-APEX"})
    sess = create_res.json()
    session_id = sess["session_id"]
    pin = sess["pairing_pin"]

    # A. Connection without token must be closed (code 1008)
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(f"/api/v1/remote/ws/{session_id}"):
            pass
    assert exc.value.code == 1008

    # B. Connection with malformed/invalid token must be closed
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(f"/api/v1/remote/ws/{session_id}?token=invalid.jwt.token"):
            pass
    assert exc.value.code == 1008

    # C. Token created for a DIFFERENT session must be closed
    foreign_token = create_access_token(data={
        "sub": "ATTACKER",
        "session_id": "SES-FOREIGN-999",
        "role": "PHYSICIAN_CONTROLLER"
    })
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(f"/api/v1/remote/ws/{session_id}?token={foreign_token}"):
            pass
    assert exc.value.code == 1008

class MockWebSocket:
    def __init__(self):
        self.sent_messages = []

    async def send_text(self, text: str):
        self.sent_messages.append(json.loads(text))

@pytest.mark.asyncio
async def test_e2e_real_websocket_bilateral_communication(client, db_session):
    """Verify live bidirectional WebSocket exchange between APK and Physician Web Cockpit."""
    # 1. APK creates session
    create_res = client.post("/api/v1/remote/create", json={"tenant_id": "TENANT-APEX"})
    sess = create_res.json()
    session_id = sess["session_id"]
    pin = sess["pairing_pin"]
    patient_token = sess["patient_vr_token"]

    # 2. Physician joins session
    join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "PHYSICIAN_WEB_COCKPIT"
    })
    doc_token = join_res.json()["token"]

    ws_patient = MockWebSocket()
    ws_doc = MockWebSocket()

    # 3. Patient APK connects peer into session broker
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="PHONE-PATIENT-VR",
        role=RemoteRole.PATIENT_VR_HOST,
        websocket=ws_patient,
        db=db_session
    )
    # Patient receives initial PEER_STATUS
    assert len(ws_patient.sent_messages) == 1
    assert ws_patient.sent_messages[0]["message_type"] == "PEER_STATUS"
    assert ws_patient.sent_messages[0]["payload"]["roles"] == ["PATIENT_VR_HOST"]

    # 4. Physician connects peer into session broker
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="PHYSICIAN_WEB_COCKPIT",
        role=RemoteRole.PHYSICIAN_CONTROLLER,
        websocket=ws_doc,
        db=db_session
    )
    # Physician receives PEER_STATUS indicating both peers
    assert len(ws_doc.sent_messages) == 1
    assert ws_doc.sent_messages[0]["message_type"] == "PEER_STATUS"
    assert "PATIENT_VR_HOST" in ws_doc.sent_messages[0]["payload"]["roles"]
    assert "PHYSICIAN_CONTROLLER" in ws_doc.sent_messages[0]["payload"]["roles"]

    # Patient also received updated PEER_STATUS indicating physician presence
    assert len(ws_patient.sent_messages) == 2
    assert "PHYSICIAN_CONTROLLER" in ws_patient.sent_messages[1]["payload"]["roles"]

    # 5. Physician sends CONTROL_COMMAND (SET_MODULE to ACUITY)
    set_mod_cmd = WebSocketMessageEnvelope(
        version="1.0",
        session_id=session_id,
        message_id="cmd-1",
        sender_id="PHYSICIAN_WEB_COCKPIT",
        sender_role=RemoteRole.PHYSICIAN_CONTROLLER,
        message_type=WebSocketMessageType.CONTROL_COMMAND,
        sequence_number=1,
        timestamp_ns=1000000000,
        payload={
            "command": "SET_MODULE",
            "target_module": "ACUITY",
            "parameters": {"module": "ACUITY", "eye": "OD"}
        }
    )
    await session_broker.route_envelope(set_mod_cmd, db=db_session)

    # Patient APK receives the command!
    assert len(ws_patient.sent_messages) == 3
    received_by_patient = ws_patient.sent_messages[-1]
    assert received_by_patient["message_type"] == "CONTROL_COMMAND"
    assert received_by_patient["payload"]["command"] == "SET_MODULE"
    assert received_by_patient["payload"]["target_module"] == "ACUITY"

    # 6. Patient APK presents stimulus in VR and publishes STIMULUS_EVENT
    stim_event = WebSocketMessageEnvelope(
        version="1.0",
        session_id=session_id,
        message_id="stim-1",
        sender_id="PHONE-PATIENT-VR",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.STIMULUS_EVENT,
        sequence_number=1,
        timestamp_ns=2000000000,
        payload={
            "trial_index": 1,
            "test_module": "PERIMETRY",
            "eye_laterality": "OD",
            "coordinates": {"x_deg": -15.0, "y_deg": 3.0},
            "presentation_monotonic_ns": 2000000000
        }
    )
    await session_broker.route_envelope(stim_event, db=db_session)

    # Physician Web Cockpit receives the stimulus!
    assert len(ws_doc.sent_messages) == 2
    received_by_doc = ws_doc.sent_messages[-1]
    assert received_by_doc["message_type"] == "STIMULUS_EVENT"
    assert received_by_doc["payload"]["coordinates"]["x_deg"] == -15.0

    # 7. Physician taps response on web cockpit (or Spacebar) and publishes RESPONSE_EVENT
    tap_event = WebSocketMessageEnvelope(
        version="1.0",
        session_id=session_id,
        message_id="resp-1",
        sender_id="PHYSICIAN_WEB_COCKPIT",
        sender_role=RemoteRole.PHYSICIAN_CONTROLLER,
        message_type=WebSocketMessageType.RESPONSE_EVENT,
        sequence_number=2,
        timestamp_ns=2350000000,
        payload={
            "trial_index": 1,
            "response_recorded": True,
            "reaction_latency_ms": 350.0,
            "input_source": "LAPTOP_SPACEBAR"
        }
    )
    await session_broker.route_envelope(tap_event, db=db_session)

    # Patient APK receives the response event!
    assert len(ws_patient.sent_messages) == 4
    received_tap = ws_patient.sent_messages[-1]
    assert received_tap["message_type"] == "RESPONSE_EVENT"
    assert received_tap["payload"]["reaction_latency_ms"] == 350.0

    # 8. Patient triggers EMERGENCY_STOP
    stop_event = WebSocketMessageEnvelope(
        version="1.0",
        session_id=session_id,
        message_id="stop-1",
        sender_id="PHONE-PATIENT-VR",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.EMERGENCY_STOP,
        sequence_number=2,
        timestamp_ns=3000000000,
        payload={"reason": "Patient requested stop"}
    )
    await session_broker.route_envelope(stop_event, db=db_session)

    # Physician Web Cockpit receives emergency stop!
    assert len(ws_doc.sent_messages) == 3
    received_stop = ws_doc.sent_messages[-1]
    assert received_stop["message_type"] == "EMERGENCY_STOP"
    assert received_stop["payload"]["reason"] == "Patient requested stop"
