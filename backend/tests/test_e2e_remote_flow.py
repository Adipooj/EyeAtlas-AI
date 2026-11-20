"""EyeAtlas VR - CR002-H End-to-End Multi-Peer Cross-Device Integration Test Suite.
Simulates a complete real-world tele-ophthalmology session connecting:
1. Android Patient VR Host (Authoritative stimulus generator & local timing recorder)
2. Physician Web Cockpit (Authoritative remote controller & real-time monitor)
3. Phone 2 Patient Response Pad (Zero-install web tactile response pad)
Verifies session creation, tripartite role authorization, stimulus presentation,
monotonic response scoring, optical interlocks, MediaPipe telemetry, emergency halt,
parallel clinical observations, and session finalization.
"""
from __future__ import annotations
import time
import pytest
from backend.app.models.entities import RemoteSessionEntity
from backend.app.models.remote_contracts import (
    RemoteRole,
    RemoteSessionStatus,
    WebSocketMessageEnvelope,
    WebSocketMessageType,
    OpticalConfigurationState,
)
from backend.app.services.session_broker import session_broker

@pytest.mark.asyncio
async def test_e2e_complete_tripartite_clinical_workflow(client, db_session):
    """Full end-to-end integration test across all 3 participants."""
    # -------------------------------------------------------------
    # 1. APK creates remote session via REST
    # -------------------------------------------------------------
    create_payload = {
        "patient_id": "P-CROSS-DEV-808",
        "device_id": "ONEPLUS-NORD-CE3-5G-HOST",
        "hardware_model": "OnePlus Nord CE 3 5G",
        "supported_inputs": ["TOUCH_PAD", "VOLUME_KEYS", "BT_CLICKER"],
        "tenant_id": "TENANT-APEX"
    }
    create_res = client.post("/api/v1/remote/create", json=create_payload)
    assert create_res.status_code == 201
    sess_data = create_res.json()
    session_id = sess_data["session_id"]
    pin = sess_data["pairing_pin"]
    patient_vr_token = sess_data["patient_vr_token"]

    assert len(pin) == 6
    assert "EYEATLAS-REMOTE://" in sess_data["qr_payload"]
    assert sess_data["status"] == "PENDING_PAIRING"

    # -------------------------------------------------------------
    # 2. Patient VR Host registers into Session Broker
    # -------------------------------------------------------------
    room = session_broker.get_or_create_room(session_id)
    room.db_session_id = session_id
    room.pairing_pin = pin

    await session_broker.connect_peer(
        session_id=session_id,
        client_id="APK-HOST-01",
        role=RemoteRole.PATIENT_VR_HOST,
        websocket=None
    )

    # -------------------------------------------------------------
    # 3. Phone 2 Patient Response Pad joins via PIN
    # -------------------------------------------------------------
    pad_join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PATIENT_RESPONSE_PAD",
        "device_id": "PHONE2-TOUCHPAD-CLIENT"
    })
    assert pad_join_res.status_code == 200
    pad_data = pad_join_res.json()
    assert pad_data["role"] == "PATIENT_RESPONSE_PAD"
    pad_token = pad_data["token"]

    await session_broker.connect_peer(
        session_id=session_id,
        client_id="PHONE2-TOUCHPAD-CLIENT",
        role=RemoteRole.PATIENT_RESPONSE_PAD,
        websocket=None
    )

    # -------------------------------------------------------------
    # 4. Physician Web Cockpit joins via PIN as primary controller
    # -------------------------------------------------------------
    doc_join_res = client.post("/api/v1/remote/join", json={
        "session_id": session_id,
        "pairing_pin": pin,
        "role": "PHYSICIAN_CONTROLLER",
        "device_id": "BROWSER-PHYSICIAN-COCKPIT",
        "clinician_username": "dr_tele_expert"
    })
    assert doc_join_res.status_code == 200
    doc_data = doc_join_res.json()
    assert doc_data["role"] == "PHYSICIAN_CONTROLLER"
    assert doc_data["is_authoritative_controller"] is True

    await session_broker.connect_peer(
        session_id=session_id,
        client_id="BROWSER-PHYSICIAN-COCKPIT",
        role=RemoteRole.PHYSICIAN_CONTROLLER,
        websocket=None
    )

    # Verify room status reflects all 3 connected roles
    status_res = client.get(f"/api/v1/remote/{session_id}/status")
    assert status_res.status_code == 200
    stat = status_res.json()
    assert "PATIENT_VR_HOST" in stat["connected_roles"]
    assert "PATIENT_RESPONSE_PAD" in stat["connected_roles"]
    assert "PHYSICIAN_CONTROLLER" in stat["connected_roles"]
    assert stat["active_physician"] == "BROWSER-PHYSICIAN-COCKPIT"

    # -------------------------------------------------------------
    # 5. Physician starts clinical perimetry examination
    # -------------------------------------------------------------
    start_cmd_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="cmd-start-01",
        sender_id="BROWSER-PHYSICIAN-COCKPIT",
        sender_role=RemoteRole.PHYSICIAN_CONTROLLER,
        message_type=WebSocketMessageType.CONTROL_COMMAND,
        sequence_number=1,
        payload={"command": "START_TEST", "target_module": "PERIMETRY_24_2"}
    )
    route_res = await session_broker.route_envelope(start_cmd_env)
    assert route_res["status"] == "ROUTED"
    assert room.status == RemoteSessionStatus.TESTING
    assert room.active_module == "PERIMETRY_24_2"

    # -------------------------------------------------------------
    # 6. APK Host presents visual stimulus & publishes reconstruction event
    # -------------------------------------------------------------
    t0_stimulus_ns = 1_000_000_000  # 1.000s Android monotonic clock
    stim_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="stim-tr-14",
        sender_id="APK-HOST-01",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.STIMULUS_EVENT,
        sequence_number=1,
        payload={
            "trial_index": 14,
            "test_module": "PERIMETRY_24_2",
            "eye_laterality": "OD",
            "coordinates": {"x_deg": 9.0, "y_deg": 3.0},
            "stimulus_properties": {
                "goldmann_size": "III",
                "luminance_nits": 31.8,
                "contrast_percent": 100
            },
            "presentation_monotonic_ns": t0_stimulus_ns,
            "fixation_status": "STEADY",
            "reliability_metrics": {"false_positives": 0, "fixation_losses": 0}
        }
    )
    stim_res = await session_broker.route_envelope(stim_env)
    assert stim_res["status"] == "ROUTED"
    assert len(room.replay_buffer) == 2

    # -------------------------------------------------------------
    # 7. Patient taps Phone 2 Response Pad (tactile input)
    # -------------------------------------------------------------
    t1_response_ns = 1_284_500_000  # 284.5 ms true local reaction time
    resp_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="resp-tr-14",
        sender_id="PHONE2-TOUCHPAD-CLIENT",
        sender_role=RemoteRole.PATIENT_RESPONSE_PAD,
        message_type=WebSocketMessageType.RESPONSE_EVENT,
        sequence_number=1,
        payload={
            "trial_index": 14,
            "response_recorded": True,
            "reaction_latency_ms": 284.5,
            "input_source": "WEB_RESPONSE_PAD",
            "input_quality": "HIGH_CONFIDENCE",
            "local_monotonic_ns": t1_response_ns
        }
    )
    resp_res = await session_broker.route_envelope(resp_env)
    assert resp_res["status"] == "ROUTED"

    # -------------------------------------------------------------
    # 8. Optical Interlock Transition (CR-002-G)
    # -------------------------------------------------------------
    optical_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="opt-stat-01",
        sender_id="APK-HOST-01",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.OPTICAL_INTERLOCK_STATUS,
        sequence_number=2,
        payload={
            "optical_state": OpticalConfigurationState.OD_LENS_REMOVED.value,
            "od_ring_type": "OPEN_APERTURE_NO_LENS",
            "os_ring_type": "STANDARD_VR_EYEPIECE",
            "interlock_active": True,
            "prohibited_tests": ["PERIMETRY_OD", "AMSLER_OD"],
            "permitted_tests": ["ANTERIOR_OD", "RETINAL_20D_OD", "PERIMETRY_OS"],
            "timestamp_ns": int(time.time() * 1e9)
        }
    )
    opt_res = await session_broker.route_envelope(optical_env)
    assert opt_res["status"] == "ROUTED"

    # -------------------------------------------------------------
    # 9. MediaPipe Eye-Tracking Telemetry (CR-002-F)
    # -------------------------------------------------------------
    eye_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="eye-track-01",
        sender_id="APK-HOST-01",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.EYETRACKING_TELEMETRY,
        sequence_number=3,
        payload={
            "left_iris_detected": False,
            "right_iris_detected": False,
            "blink_active": False,
            "eyelid_aperture_mm": 9.4,
            "fixation_bcea_deg2": 0.72,
            "optical_status": "OPTICALLY_OCCLUDED_BY_VRBOX",
            "tracking_confidence": 0.0,
            "is_clinical_gated": True,
            "thermal_throttling": False,
            "processing_latency_ms": 14.2
        }
    )
    eye_res = await session_broker.route_envelope(eye_env)
    assert eye_res["status"] == "ROUTED"

    # -------------------------------------------------------------
    # 10. Physician submits clinical observation without overwriting patient data
    # -------------------------------------------------------------
    obs_payload = {
        "eye": "OD",
        "category": "ANTERIOR_TORCH",
        "observation": "Clear cornea, normal anterior chamber depth, no cells or flare.",
        "severity": "NORMAL"
    }
    obs_res = client.post(
        f"/api/v1/remote/{session_id}/observations",
        json=obs_payload,
        headers={"X-Doctor-Token": doc_data["token"]}
    )
    assert obs_res.status_code == 200
    assert obs_res.json()["status"] == "RECORDED"

    # -------------------------------------------------------------
    # 11. Patient triggers Emergency Stop
    # -------------------------------------------------------------
    stop_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="emerg-stop-01",
        sender_id="APK-HOST-01",
        sender_role=RemoteRole.PATIENT_VR_HOST,
        message_type=WebSocketMessageType.EMERGENCY_STOP,
        sequence_number=4,
        payload={
            "triggered_by": "PATIENT_HARDWARE_BUTTON",
            "reason": "Patient requested stop due to pressure discomfort",
            "timestamp_ns": int(time.time() * 1e9)
        }
    )
    stop_res = await session_broker.route_envelope(stop_env)
    assert stop_res["status"] == "EMERGENCY_STOP_BROADCAST"
    assert room.status == RemoteSessionStatus.PAUSED

    # -------------------------------------------------------------
    # 12. Finalize Examination
    # -------------------------------------------------------------
    finalize_res = client.post(
        f"/api/v1/remote/{session_id}/finalize",
        headers={"X-Doctor-Token": doc_data["token"]}
    )
    assert finalize_res.status_code == 200
    fin_data = finalize_res.json()
    assert fin_data["status"] == "FINALIZED"
    assert "report_id" in fin_data

    # Verify session marked FINALIZED in database
    entity = db_session.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
    assert entity.status == "FINALIZED"

@pytest.mark.asyncio
async def test_e2e_sequence_replay_buffer(client):
    """Verify disconnected physician can replay ordered message history."""
    session_id = "SES-REPLAY-E2E-TEST"
    room = session_broker.get_or_create_room(session_id)
    room.db_session_id = session_id
    room.replay_buffer.clear()

    # Populate 5 sequential envelopes
    for seq in range(1, 6):
        env = WebSocketMessageEnvelope(
            session_id=session_id,
            message_id=f"msg-{seq}",
            sender_id="APK-HOST",
            sender_role=RemoteRole.PATIENT_VR_HOST,
            message_type=WebSocketMessageType.STIMULUS_EVENT,
            sequence_number=seq,
            payload={"trial": seq}
        )
        await session_broker.route_envelope(env)

    assert len(room.replay_buffer) == 5

    # Request replay starting after sequence 2
    replay_request_env = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id="req-replay-1",
        sender_id="DOC-RECONNECT",
        sender_role=RemoteRole.PHYSICIAN_CONTROLLER,
        message_type=WebSocketMessageType.REPLAY_REQUEST,
        sequence_number=1,
        payload={"since_sequence": 2}
    )
    rep_res = await session_broker.route_envelope(replay_request_env)
    assert rep_res["status"] == "REPLAYED"
    assert rep_res["count"] == 3  # sequences 3, 4, 5
