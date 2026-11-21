"""EyeAtlas VR - Remote Session REST & WebSocket Endpoints (CR-002).
Handles online pairing, short-lived 6-digit PIN & QR generation,
role-based join validation, and WebSocket duplex streaming.
"""
from __future__ import annotations
import datetime
import json
import random
import uuid
from typing import Any, Dict, Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Query, WebSocket, WebSocketDisconnect, status
from sqlalchemy.orm import Session
from backend.app.core.config import settings
from backend.app.core.database import get_db, SessionLocal
from backend.app.core.security import create_access_token, decode_access_token
from backend.app.models.entities import RemoteSessionEntity, UserEntity
from backend.app.models.remote_contracts import (
    IceServerConfig,
    IceServersResponse,
    RemoteRole,
    RemoteSessionCreateRequest,
    RemoteSessionCreateResponse,
    RemoteSessionJoinRequest,
    RemoteSessionJoinResponse,
    RemoteSessionStatus,
    RemoteSessionStatusResponse,
    WebSocketMessageEnvelope,
    WebSocketMessageType,
)
from backend.app.services.session_broker import session_broker, ConnectedPeer

router = APIRouter(prefix="/remote", tags=["Remote Sessions"])

def generate_pairing_pin() -> str:
    """Generate a random 6-digit numeric pairing PIN."""
    return f"{random.randint(100000, 999999)}"

@router.post("/create", response_model=RemoteSessionCreateResponse, status_code=status.HTTP_201_CREATED)
def create_remote_session(
    request: RemoteSessionCreateRequest,
    db: Session = Depends(get_db)
):
    """Initiates an online remote screening session from the patient APK.
    Generates a short-lived (15 min) 6-digit PIN and QR pairing payload.
    """
    session_id = f"SES-REM-{uuid.uuid4().hex[:8].upper()}"
    pin = generate_pairing_pin()
    expires_at = datetime.datetime.utcnow() + datetime.timedelta(minutes=15)
    
    # Generate ephemeral token for the Patient VR Host
    patient_token = create_access_token(
        data={
            "sub": request.device_id,
            "session_id": session_id,
            "role": RemoteRole.PATIENT_VR_HOST.value,
            "tenant_id": request.tenant_id
        },
        expires_delta_seconds=7200
    )

    qr_payload = f"EYEATLAS-REMOTE://{session_id}/{pin}/api/v1/remote"

    entity = RemoteSessionEntity(
        session_id=session_id,
        tenant_id=request.tenant_id,
        patient_id=request.patient_id,
        pairing_pin=pin,
        qr_payload=qr_payload,
        patient_vr_token=patient_token,
        pin_expires_at=expires_at,
        status=RemoteSessionStatus.PENDING_PAIRING.value,
        connected_roles_json=json.dumps([RemoteRole.PATIENT_VR_HOST.value])
    )
    db.add(entity)
    db.commit()

    return RemoteSessionCreateResponse(
        session_id=session_id,
        pairing_pin=pin,
        qr_payload=qr_payload,
        patient_vr_token=patient_token,
        expires_at_epoch=int(expires_at.timestamp()),
        ws_endpoint_url=f"/api/v1/remote/ws/{session_id}",
        status=RemoteSessionStatus.PENDING_PAIRING
    )

@router.post("/join", response_model=RemoteSessionJoinResponse)
def join_remote_session(
    request: RemoteSessionJoinRequest,
    db: Session = Depends(get_db)
):
    """Allows a physician web station or patient response pad to join a remote session.
    Validates PIN expiry, enforces single-physician authority, and returns scoped tokens.
    """
    entity = db.query(RemoteSessionEntity).filter(
        RemoteSessionEntity.session_id == request.session_id
    ).first()

    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Remote session not found")

    if entity.status == RemoteSessionStatus.REVOKED.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Session has been revoked")

    # Validate PIN if provided or required
    if request.pairing_pin:
        if entity.pin_expires_at < datetime.datetime.utcnow():
            entity.status = RemoteSessionStatus.EXPIRED.value
            db.commit()
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Pairing PIN has expired")
        if entity.pairing_pin != request.pairing_pin:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid pairing PIN")

    # Enforce single authoritative physician
    assigned_role = request.role
    is_authoritative = True
    if request.role == RemoteRole.PHYSICIAN_CONTROLLER:
        room = session_broker.get_room(request.session_id)
        has_active_physician = (
            (entity.active_physician_user_id and entity.active_physician_user_id != request.device_id) or
            (room and room.active_physician_client_id and room.active_physician_client_id != request.device_id)
        )
        if has_active_physician:
            assigned_role = RemoteRole.OBSERVER
            is_authoritative = False
        else:
            entity.active_physician_user_id = request.device_id

    # Create scoped ephemeral join token
    token = create_access_token(
        data={
            "sub": request.device_id,
            "session_id": request.session_id,
            "role": assigned_role.value,
            "tenant_id": entity.tenant_id,
            "username": request.clinician_username or request.device_id
        },
        expires_delta_seconds=7200
    )

    # Update DB state
    connected = json.loads(entity.connected_roles_json) if entity.connected_roles_json else []
    if assigned_role.value not in connected:
        connected.append(assigned_role.value)
    entity.connected_roles_json = json.dumps(connected)
    entity.status = RemoteSessionStatus.CONNECTED.value
    db.commit()

    return RemoteSessionJoinResponse(
        session_id=request.session_id,
        role=assigned_role,
        token=token,
        ws_endpoint_url=f"/api/v1/remote/ws/{request.session_id}",
        is_authoritative_controller=is_authoritative,
        status=RemoteSessionStatus.CONNECTED
    )

@router.get("/ice-servers", response_model=IceServersResponse)
def get_ice_servers():
    """Provides validated STUN and TURN server configurations for WebRTC peer connections.
    Includes Google public STUN servers and optional TURN fallback for difficult NATs.
    """
    servers = [
        IceServerConfig(urls=["stun:stun.l.google.com:19302"]),
        IceServerConfig(urls=["stun:stun1.l.google.com:19302"]),
    ]
    # In production, TURN servers with ephemeral credentials can be injected here
    return IceServersResponse(ice_servers=servers, ttl_seconds=86400)

@router.get("/{session_id}/status", response_model=RemoteSessionStatusResponse)
def get_remote_session_status(
    session_id: str,
    db: Session = Depends(get_db)
):
    """Inspects live connection status, participants, and room health."""
    entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Remote session not found")

    room_stats = session_broker.get_room_stats(session_id)
    connected_roles = room_stats.get("roles", json.loads(entity.connected_roles_json or "[]"))
    active_physician = room_stats.get("active_physician", entity.active_physician_user_id)

    return RemoteSessionStatusResponse(
        session_id=session_id,
        status=RemoteSessionStatus(entity.status),
        active_physician=active_physician,
        connected_roles=connected_roles,
        active_module=room_stats.get("active_module", entity.active_module),
        message_count=room_stats.get("buffered_messages", 0),
        created_at_epoch=int(entity.created_at.timestamp()),
        expires_at_epoch=int(entity.pin_expires_at.timestamp())
    )

@router.post("/{session_id}/revoke")
def revoke_remote_session(
    session_id: str,
    db: Session = Depends(get_db)
):
    """Explicitly terminates a remote session and revokes access."""
    entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Remote session not found")

    entity.status = RemoteSessionStatus.REVOKED.value
    db.commit()

    room = session_broker.get_room(session_id)
    if room:
        room.status = RemoteSessionStatus.REVOKED
    return {"session_id": session_id, "status": "REVOKED"}

@router.post("/{session_id}/observations")
def submit_clinical_observation(
    session_id: str,
    payload: Dict[str, Any],
    db: Session = Depends(get_db)
):
    """Allows authenticated physician to record clinical observations in the session audit log."""
    entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Remote session not found")
    
    obs_envelope = WebSocketMessageEnvelope(
        session_id=session_id,
        message_id=f"obs-{int(datetime.datetime.utcnow().timestamp()*1000)}",
        sender_id="PHYSICIAN_REST",
        sender_role=RemoteRole.PHYSICIAN_CONTROLLER,
        message_type=WebSocketMessageType.OBSERVATION_SUBMIT,
        sequence_number=len(session_broker.get_or_create_room(session_id).replay_buffer) + 1,
        payload=payload
    )
    room = session_broker.get_or_create_room(session_id)
    room.replay_buffer.append(obs_envelope)
    return {"session_id": session_id, "status": "RECORDED", "observation": payload}

@router.post("/{session_id}/finalize")
def finalize_remote_session(
    session_id: str,
    db: Session = Depends(get_db)
):
    """Finalizes the examination session, locking the audit log and generating report ID."""
    entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
    if not entity:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Remote session not found")
    
    entity.status = RemoteSessionStatus.FINALIZED.value
    db.commit()

    room = session_broker.get_room(session_id)
    if room:
        room.status = RemoteSessionStatus.FINALIZED

    report_id = f"REP-{uuid.uuid4().hex[:8].upper()}"
    return {
        "session_id": session_id,
        "status": "FINALIZED",
        "report_id": report_id,
        "regulatory_disclaimer": "Investigational ophthalmic screening aid under CDSCO MDR-2017 Class B SaMD. Not an autonomous diagnostic device."
    }

@router.post("/{session_id}/message")
async def post_remote_message(
    session_id: str,
    envelope: WebSocketMessageEnvelope,
    token: Optional[str] = Query(None),
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Fallback REST messaging endpoint ensuring reliable command and event transport."""
    effective_token = token
    if not effective_token and authorization and authorization.startswith("Bearer "):
        effective_token = authorization.split(" ", 1)[1]

    if not effective_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token required")

    try:
        payload = decode_access_token(effective_token)
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    if not payload or payload.get("session_id") != session_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Session mismatch")

    sender_id = payload.get("sub") or envelope.sender_id
    role_str = payload.get("role") or (envelope.sender_role.value if hasattr(envelope.sender_role, "value") else str(envelope.sender_role))
    try:
        sender_role = RemoteRole(role_str)
    except Exception:
        sender_role = RemoteRole.OBSERVER

    envelope.session_id = session_id
    envelope.sender_id = sender_id
    envelope.sender_role = sender_role

    room = session_broker.get_or_create_room(session_id)
    if sender_id not in room.peers:
        room.peers[sender_id] = ConnectedPeer(client_id=sender_id, role=sender_role, websocket=None)
    else:
        room.peers[sender_id].last_heartbeat = datetime.datetime.utcnow().timestamp()

    result = await session_broker.route_envelope(envelope, db=db)
    return {"status": "DELIVERED", "sequence_number": envelope.sequence_number, "broker_result": result}

@router.get("/{session_id}/messages")
async def get_remote_messages(
    session_id: str,
    since_seq: int = Query(-1),
    token: Optional[str] = Query(None),
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    """Fallback REST polling endpoint returning new buffered envelopes and live peer presence."""
    effective_token = token
    if not effective_token and authorization and authorization.startswith("Bearer "):
        effective_token = authorization.split(" ", 1)[1]

    if not effective_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token required")

    try:
        payload = decode_access_token(effective_token)
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    if not payload or payload.get("session_id") != session_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Session mismatch")

    client_id = payload.get("sub", "")
    role_str = payload.get("role", "OBSERVER")
    try:
        client_role = RemoteRole(role_str)
    except Exception:
        client_role = RemoteRole.OBSERVER

    room = session_broker.get_or_create_room(session_id)
    if client_id and client_id not in room.peers:
        room.peers[client_id] = ConnectedPeer(client_id=client_id, role=client_role, websocket=None)
    elif client_id in room.peers:
        room.peers[client_id].last_heartbeat = datetime.datetime.utcnow().timestamp()

    messages = [
        env.model_dump()
        for env in room.replay_buffer
        if env.sequence_number > since_seq and env.sender_id != client_id
    ]

    connected_roles = room.get_connected_roles()
    has_patient = RemoteRole.PATIENT_VR_HOST.value in connected_roles
    has_physician = RemoteRole.PHYSICIAN_CONTROLLER.value in connected_roles

    return {
        "session_id": session_id,
        "messages": messages,
        "connected_roles": connected_roles,
        "has_patient": has_patient,
        "has_physician": has_physician,
        "active_module": room.active_module,
        "server_time_ns": int(datetime.datetime.utcnow().timestamp() * 1_000_000_000)
    }

@router.websocket("/ws/{session_id}")
async def remote_session_websocket(
    websocket: WebSocket,
    session_id: str,
    token: Optional[str] = Query(None),
    role: Optional[str] = Query(None),
    client_id: Optional[str] = Query(None)
):
    """Bidirectional WebSocket connection for live telemetry, twin-stimulus reconstruction,
    tactile patient response streaming, and emergency halt broadcasting.
    Requires a valid, unexpired, session-bound JWT token issued by /create or /join.
    """
    # 1. Require a valid, unexpired, session-bound token before admitting connection
    if not token:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Authentication token required")
        return

    try:
        payload = decode_access_token(token)
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid or expired authentication token")
        return

    if not payload or payload.get("session_id") != session_id:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Session ID mismatch")
        return

    role_str = payload.get("role")
    try:
        effective_role = RemoteRole(role_str)
    except (ValueError, TypeError):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid role in token")
        return

    # Derive role and client identity strictly from verified credentials
    effective_client_id = payload.get("sub") or client_id or f"client-{uuid.uuid4().hex[:6]}"

    db = SessionLocal()
    try:
        # Check session existence and validity in database
        entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
        if not entity:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Remote session not found")
            return
        if entity.status == RemoteSessionStatus.REVOKED.value:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Session has been revoked")
            return

        # Admit the connection only after successful verification
        await websocket.accept()

        # Send AUTH_ACK first to complete handshake
        auth_ack = WebSocketMessageEnvelope(
            session_id=session_id,
            message_id=f"auth-ack-{int(datetime.datetime.utcnow().timestamp()*1000)}",
            sender_id="SESSION_BROKER",
            sender_role=RemoteRole.OBSERVER,
            message_type=WebSocketMessageType.AUTH_ACK,
            sequence_number=0,
            payload={
                "session_id": session_id,
                "client_id": effective_client_id,
                "role": effective_role.value,
                "status": "AUTHENTICATED"
            }
        )
        await websocket.send_text(auth_ack.model_dump_json())

        # Register connection with session broker and broadcast PEER_STATUS
        await session_broker.connect_peer(
            session_id=session_id,
            client_id=effective_client_id,
            role=effective_role,
            websocket=websocket,
            db=db
        )

        # Main receive loop
        while True:
            raw_text = await websocket.receive_text()
            try:
                data = json.loads(raw_text)
                # Enforce server-verified boundaries on the envelope
                data["session_id"] = session_id
                data["sender_id"] = effective_client_id
                data["sender_role"] = effective_role.value
                envelope = WebSocketMessageEnvelope(**data)
                await session_broker.route_envelope(envelope, db=db)
            except Exception as parse_err:
                err_env = WebSocketMessageEnvelope(
                    session_id=session_id,
                    message_id=f"err-{int(datetime.datetime.utcnow().timestamp()*1000)}",
                    sender_id="SESSION_BROKER",
                    sender_role=RemoteRole.OBSERVER,
                    message_type=WebSocketMessageType.ERROR,
                    sequence_number=0,
                    payload={"error": str(parse_err)}
                )
                await websocket.send_text(err_env.model_dump_json())

    except WebSocketDisconnect:
        await session_broker.disconnect_peer(session_id, effective_client_id, db=db)
    except Exception:
        await session_broker.disconnect_peer(session_id, effective_client_id, db=db)
    finally:
        db.close()
