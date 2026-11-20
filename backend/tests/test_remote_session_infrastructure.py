"""EyeAtlas VR - CR002-B Session Infrastructure & Remote WebSocket Tests.
Verifies authenticated online pairing, short-lived 6-digit PIN/QR generation,
single-physician exclusivity, sequence replay, duplicate rejection, and emergency stop.
"""
from __future__ import annotations
import datetime
import pytest
from backend.app.models.entities import RemoteSessionEntity
from backend.app.models.remote_contracts import (
    RemoteRole,
    RemoteSessionStatus,
    WebSocketMessageEnvelope,
    WebSocketMessageType,
)
from backend.app.services.session_broker import session_broker

def test_remote_session_create(client):
    """Verify APK can create a remote session with 6-digit PIN and QR payload."""
    payload = {
        "patient_id": "P-101",
        "device_id": "PHONE-VR-TEST-01",
        "hardware_model": "OnePlus-Nord-CE-3",
        "supported_inputs": ["TOUCH_PAD", "VOLUME_KEYS"],
        "tenant_id": "TENANT-APEX"
    }
    response = client.post("/api/v1/remote/create", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert data["session_id"].startswith("SES-REM-")
    assert len(data["pairing_pin"]) == 6
    assert data["pairing_pin"].isdigit()
    assert "EYEATLAS-REMOTE://" in data["qr_payload"]
    assert data["status"] == "PENDING_PAIRING"
    assert data["patient_vr_token"] is not None
    assert data["expires_at_epoch"] > int(datetime.datetime.utcnow().timestamp())

def test_remote_session_join_physician_and_response_pad(client):
    """Verify physician web station and patient response pad can join via PIN."""
    # 1. Create session
    create_res = client.post("/api/v1/remote/create", json={"tenant_id": "TENANT-APEX"})
    assert create_res.status_code == 201
    sess_data = create_res.json()
    session_id = sess_data["session_id"]
    pin = sess_data["pairing_pin"]

    # 2. Response Pad joins using PIN
    pad_join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PATIENT_RESPONSE_PAD",
        "device_id": "PHONE-2-BROWSER"
    })
    assert pad_join_res.status_code == 200
    pad_data = pad_join_res.json()
    assert pad_data["role"] == "PATIENT_RESPONSE_PAD"
    assert pad_data["token"] is not None

    # 3. Physician joins using PIN as primary controller
    doc1_join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "DOC-LAPTOP-01",
        "clinician_username": "dr_sharma"
    })
    assert doc1_join_res.status_code == 200
    doc1_data = doc1_join_res.json()
    assert doc1_data["role"] == "PHYSICIAN_CONTROLLER"
    assert doc1_data["is_authoritative_controller"] is True

    # 4. Status reflects both connected roles
    status_res = client.get(f"/api/v1/remote/{session_id}/status")
    assert status_res.status_code == 200
    stat_data = status_res.json()
    assert "PATIENT_RESPONSE_PAD" in stat_data["connected_roles"]
    assert "PHYSICIAN_CONTROLLER" in stat_data["connected_roles"]
    assert stat_data["active_physician"] == "DOC-LAPTOP-01"

def test_single_physician_exclusivity(client):
    """Verify that a second connecting physician is demoted to OBSERVER."""
    create_res = client.post("/api/v1/remote/create", json={"tenant_id": "TENANT-APEX"})
    sess_data = create_res.json()
    session_id = sess_data["session_id"]
    pin = sess_data["pairing_pin"]

    # First physician connects in broker
    room = session_broker.get_or_create_room(session_id)
    room.active_physician_client_id = "DOC-PRIMARY"

    # Second physician joins via API
    doc2_join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "DOC-SECONDARY",
        "clinician_username": "dr_patel"
    })
    assert doc2_join_res.status_code == 200
    doc2_data = doc2_join_res.json()
    assert doc2_data["role"] == "OBSERVER"
    assert doc2_data["is_authoritative_controller"] is False

def test_expired_pin_rejection(client, db_session):
    """Verify expired PIN cannot join."""
    create_res = client.post("/api/v1/remote/create", json={"tenant_id": "TENANT-APEX"})
    sess_data = create_res.json()
    session_id = sess_data["session_id"]
    pin = sess_data["pairing_pin"]

    # Manually backdate expiry in DB
    entity = db_session.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
    entity.pin_expires_at = datetime.datetime.utcnow() - datetime.timedelta(minutes=1)
    db_session.commit()

    join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PATIENT_RESPONSE_PAD",
        "device_id": "PHONE-PAD-LATE"
    })
    assert join_res.status_code == 401
    assert "expired" in join_res.json()["detail"].lower()

def test_session_revocation(client):
    """Verify explicit session revocation blocks further access."""
    create_res = client.post("/api/v1/remote/create", json={"tenant_id": "TENANT-APEX"})
    sess_data = create_res.json()
    session_id = sess_data["session_id"]
    pin = sess_data["pairing_pin"]

    # Revoke
    rev_res = client.post(f"/api/v1/remote/{session_id}/revoke")
    assert rev_res.status_code == 200
    assert rev_res.json()["status"] == "REVOKED"

    # Subsequent join must fail
    join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PATIENT_RESPONSE_PAD",
        "device_id": "PAD-AFTER-REVOKE"
    })
    assert join_res.status_code == 403

@pytest.mark.asyncio
async def test_session_broker_sequence_duplicate_rejection():
    """Verify broker sequence monotonic validation and duplicate rejection."""
    session_id = "SES-TEST-BROKER-SEQ"
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="PATIENT-VR-HOST",
        role=RemoteRole.PATIENT_VR_HOST,
        websocket=None
    )

    env1 = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="msg-1",
        sender_id="PATIENT-VR-HOST",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.STIMULUS_EVENT,
        sequence_number=1,
        payload={"trial_index": 1, "test": "PERIMETRY"}
    )
    res1 = await session_broker.route_envelope(env1)
    assert res1["status"] in ("ROUTED", "BROADCAST")

    # Duplicate sequence 1 from same sender
    dup_res = await session_broker.route_envelope(env1)
    assert dup_res["status"] == "REJECTED_DUPLICATE"

    # Higher sequence 2 accepted
    env2 = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="msg-2",
        sender_id="PATIENT-VR-HOST",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.STIMULUS_EVENT,
        sequence_number=2,
        payload={"trial_index": 2, "test": "PERIMETRY"}
    )
    res2 = await session_broker.route_envelope(env2)
    assert res2["status"] in ("ROUTED", "BROADCAST")

@pytest.mark.asyncio
async def test_session_broker_emergency_stop_priority():
    """Verify patient emergency stop pauses session immediately."""
    session_id = "SES-TEST-EMERGENCY"
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="PATIENT-VR",
        role=RemoteRole.PATIENT_VR_HOST,
        websocket=None
    )

    stop_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="stop-1",
        sender_id="PATIENT-VR",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.EMERGENCY_STOP,
        sequence_number=10,
        payload={"reason": "Patient tapped emergency stop button on headset"}
    )
    res = await session_broker.route_envelope(stop_env)
    assert res["status"] == "EMERGENCY_STOP_BROADCAST"

    room = session_broker.get_room(session_id)
    assert room.status == RemoteSessionStatus.PAUSED

@pytest.mark.asyncio
async def test_session_broker_replay_buffer():
    """Verify message replay buffer retains messages for reconnection."""
    session_id = "SES-TEST-REPLAY"
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="VR-HOST",
        role=RemoteRole.PATIENT_VR_HOST,
        websocket=None
    )

    # Route 3 messages
    for i in range(1, 4):
        msg = WebSocketMessageEnvelope(
            session_id=session_id,
            message_id=f"seq-{i}",
            sender_id="VR-HOST",
            sender_role=RemoteRole.PATIENT_VR_HOST,
            message_type=WebSocketMessageType.STIMULUS_EVENT,
            sequence_number=i,
            payload={"trial": i}
        )
        await session_broker.route_envelope(msg)

    room = session_broker.get_room(session_id)
    assert len(room.replay_buffer) == 3
    assert room.replay_buffer[0].sequence_number == 1
    assert room.replay_buffer[2].sequence_number == 3

def test_ice_servers_endpoint(client):
    """Verify STUN/TURN server discovery endpoint."""
    res = client.get("/api/v1/remote/ice-servers")
    assert res.status_code == 200
    data = res.json()
    assert "ice_servers" in data
    assert len(data["ice_servers"]) >= 2
    urls = [s["urls"][0] for s in data["ice_servers"]]
    assert "stun:stun.l.google.com:19302" in urls
    assert "stun:stun1.l.google.com:19302" in urls

@pytest.mark.asyncio
async def test_webrtc_signaling_and_stream_status_routing():
    """Verify WebRTC offer/answer and stream status routing via session broker."""
    session_id = "SES-TEST-WEBRTC"
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="VR-HOST",
        role=RemoteRole.PATIENT_VR_HOST,
        websocket=None
    )
    await session_broker.connect_peer(
        session_id=session_id,
        client_id="DR-WEB",
        role=RemoteRole.PHYSICIAN_CONTROLLER,
        websocket=None
    )

    # 1. VR Host sends WebRTC Offer
    offer_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="offer-1",
        sender_id="VR-HOST",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.WEBRTC_OFFER,
        sequence_number=1,
        payload={"type": "offer", "sdp": "v=0\r\no=- 12345 2 IN IP4 127.0.0.1..."}
    )
    res_offer = await session_broker.route_envelope(offer_env)
    assert res_offer["status"] == "ROUTED"
    assert res_offer["target"] == "PHYSICIAN_CONTROLLER"

    # 2. Physician sends WebRTC Answer
    answer_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="answer-1",
        sender_id="DR-WEB",
        sender_role=RemoteRole.PHYSICIAN_CONTROLLER,
        message_type=WebSocketMessageType.WEBRTC_ANSWER,
        sequence_number=1,
        payload={"type": "answer", "sdp": "v=0\r\no=- 67890 2 IN IP4 127.0.0.1..."}
    )
    res_answer = await session_broker.route_envelope(answer_env)
    assert res_answer["status"] == "ROUTED"
    assert res_answer["target"] == "PATIENT_VR_HOST"

    # 3. Stream Status telemetry publication
    stream_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="stream-1",
        sender_id="VR-HOST",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.STREAM_STATUS,
        sequence_number=2,
        payload={
            "is_streaming": True,
            "resolution_width": 1080,
            "resolution_height": 2400,
            "frame_rate": 30,
            "dropped_frames": 0,
            "bitrate_kbps": 2200,
            "orientation": 90,
            "capture_interrupted": False
        }
    )
    res_stream = await session_broker.route_envelope(stream_env)
    assert res_stream["status"] == "ROUTED"
