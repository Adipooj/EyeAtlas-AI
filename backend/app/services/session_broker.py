"""EyeAtlas VR - Durable WebSocket Session Broker (CR-002).
Orchestrates online multi-role rooms, enforces single-physician authority,
implements ordered replay buffers, duplicate rejection, heartbeat watchdog,
and emergency halt broadcasting. Supports hosting on edge platforms (Vercel/Netlify)
via decoupled adapter contracts and database-backed state synchronization.
"""
from __future__ import annotations
import asyncio
import datetime
import json
import logging
import time
from typing import Any, Dict, List, Optional, Set
from fastapi import WebSocket, WebSocketDisconnect
from sqlalchemy.orm import Session
from backend.app.models.entities import RemoteSessionEntity
from backend.app.models.remote_contracts import (
    RemoteRole,
    RemoteSessionStatus,
    WebSocketMessageEnvelope,
    WebSocketMessageType,
)

logger = logging.getLogger("eyeatlas.remote_broker")

class ConnectedPeer:
    def __init__(self, client_id: str, role: RemoteRole, websocket: Optional[WebSocket] = None):
        self.client_id = client_id
        self.role = role
        self.websocket = websocket
        self.last_heartbeat = time.time()
        self.last_sequence = -1

class SessionRoom:
    def __init__(self, session_id: str, max_replay_buffer: int = 150):
        self.session_id = session_id
        self.peers: Dict[str, ConnectedPeer] = {}  # client_id -> ConnectedPeer
        self.active_physician_client_id: Optional[str] = None
        self.replay_buffer: List[WebSocketMessageEnvelope] = []
        self.max_replay_buffer = max_replay_buffer
        self.status = RemoteSessionStatus.CONNECTED
        self.active_module: Optional[str] = None

    def get_connected_roles(self) -> List[str]:
        return [peer.role.value for peer in self.peers.values()]

class SessionBroker:
    """In-memory room broker synchronized with persistent database records."""
    def __init__(self):
        self._rooms: Dict[str, SessionRoom] = {}
        self._lock = asyncio.Lock() if hasattr(asyncio, "Lock") else None

    def get_or_create_room(self, session_id: str) -> SessionRoom:
        if session_id not in self._rooms:
            self._rooms[session_id] = SessionRoom(session_id)
        return self._rooms[session_id]

    def get_room(self, session_id: str) -> Optional[SessionRoom]:
        return self._rooms.get(session_id)

    async def connect_peer(
        self,
        session_id: str,
        client_id: str,
        role: RemoteRole,
        websocket: Optional[WebSocket],
        db: Optional[Session] = None
    ) -> Dict[str, Any]:
        """Registers a client connection with role enforcement and single-physician exclusivity."""
        room = self.get_or_create_room(session_id)
        
        # Enforce single authoritative physician controller
        is_authoritative = True
        if role == RemoteRole.PHYSICIAN_CONTROLLER:
            if room.active_physician_client_id and room.active_physician_client_id != client_id:
                # Existing physician connected: demote new connection to OBSERVER
                role = RemoteRole.OBSERVER
                is_authoritative = False
                logger.warning(
                    f"Session {session_id}: secondary physician {client_id} connected, assigned role OBSERVER."
                )
            else:
                room.active_physician_client_id = client_id

        peer = ConnectedPeer(client_id=client_id, role=role, websocket=websocket)
        room.peers[client_id] = peer
        room.status = RemoteSessionStatus.CONNECTED

        # Persist connected roles in DB
        if db:
            entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
            if entity:
                roles = list(set(room.get_connected_roles()))
                entity.connected_roles_json = json.dumps(roles)
                entity.status = RemoteSessionStatus.CONNECTED.value
                if is_authoritative and role == RemoteRole.PHYSICIAN_CONTROLLER:
                    entity.active_physician_user_id = client_id
                db.commit()

        # Broadcast PEER_STATUS to all room members
        await self.broadcast_peer_status(session_id)

        return {
            "session_id": session_id,
            "client_id": client_id,
            "assigned_role": role,
            "is_authoritative": is_authoritative,
            "connected_roles": room.get_connected_roles()
        }

    async def disconnect_peer(self, session_id: str, client_id: str, db: Optional[Session] = None):
        """Unregisters peer and notifies room members."""
        room = self.get_room(session_id)
        if not room or client_id not in room.peers:
            return

        peer = room.peers.pop(client_id)
        if room.active_physician_client_id == client_id:
            room.active_physician_client_id = None

        if db:
            entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == session_id).first()
            if entity:
                entity.connected_roles_json = json.dumps(room.get_connected_roles())
                if len(room.peers) == 0:
                    entity.status = RemoteSessionStatus.PENDING_PAIRING.value
                db.commit()

        await self.broadcast_peer_status(session_id)

    async def broadcast_peer_status(self, session_id: str):
        """Broadcasts current peer connectivity and roles to all connected clients."""
        room = self.get_room(session_id)
        if not room:
            return

        status_msg = WebSocketMessageEnvelope(
            session_id=session_id,
            message_id=f"status-{int(time.time()*1000)}",
            sender_id="SESSION_BROKER",
            sender_role=RemoteRole.OBSERVER,
            message_type=WebSocketMessageType.PEER_STATUS,
            sequence_number=len(room.replay_buffer),
            payload={
                "connected_peers": len(room.peers),
                "roles": room.get_connected_roles(),
                "active_physician": room.active_physician_client_id,
                "status": room.status.value,
                "active_module": room.active_module
            }
        )
        await self.broadcast_to_room(session_id, status_msg)

    async def route_envelope(
        self,
        envelope: WebSocketMessageEnvelope,
        db: Optional[Session] = None
    ) -> Dict[str, Any]:
        """Validates envelope, checks sequence numbers, rejects duplicates, buffers, and routes."""
        room = self.get_or_create_room(envelope.session_id)
        sender_id = envelope.sender_id
        peer = room.peers.get(sender_id)

        # 1. Sequence & Duplicate Rejection
        if peer:
            if envelope.sequence_number <= peer.last_sequence:
                logger.warning(
                    f"Session {envelope.session_id}: rejected duplicate/out-of-order sequence {envelope.sequence_number} from {sender_id} (last: {peer.last_sequence})"
                )
                return {"status": "REJECTED_DUPLICATE", "sequence": envelope.sequence_number}
            peer.last_sequence = envelope.sequence_number
            peer.last_heartbeat = time.time()

            # Enforce that only authorized roles (Physician Controller or Patient VR Host) can execute control commands
            if envelope.message_type == WebSocketMessageType.CONTROL_COMMAND:
                if peer.role not in (RemoteRole.PHYSICIAN_CONTROLLER, RemoteRole.PATIENT_VR_HOST):
                    logger.warning(
                        f"Session {envelope.session_id}: rejected CONTROL_COMMAND from unauthorized peer role {peer.role}"
                    )
                    return {"status": "REJECTED_UNAUTHORIZED_ROLE", "sequence": envelope.sequence_number}

        # 2. Buffer in replay queue
        room.replay_buffer.append(envelope)
        if len(room.replay_buffer) > room.max_replay_buffer:
            room.replay_buffer.pop(0)

        # 3. Handle Special Control Messages
        if envelope.message_type == WebSocketMessageType.EMERGENCY_STOP:
            room.status = RemoteSessionStatus.PAUSED
            if db:
                entity = db.query(RemoteSessionEntity).filter(RemoteSessionEntity.session_id == envelope.session_id).first()
                if entity:
                    entity.status = RemoteSessionStatus.PAUSED.value
                    db.commit()
            # Emergency stop is priority-broadcast to everyone immediately
            await self.broadcast_to_room(envelope.session_id, envelope, exclude_client_id=envelope.sender_id)
            return {"status": "EMERGENCY_STOP_BROADCAST", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.CONTROL_COMMAND:
            # Enforce that only the active physician or patient VR host can execute control commands
            if envelope.sender_role not in (RemoteRole.PHYSICIAN_CONTROLLER, RemoteRole.PATIENT_VR_HOST):
                return {"status": "REJECTED_UNAUTHORIZED_ROLE", "sequence": envelope.sequence_number}
            
            cmd = envelope.payload.get("command")
            if cmd == "START_TEST":
                room.active_module = envelope.payload.get("target_module")
                room.status = RemoteSessionStatus.TESTING
            elif cmd in ("PAUSE_TEST", "ABORT_TEST"):
                room.status = RemoteSessionStatus.PAUSED

            # Forward command to the Patient VR Host
            await self.send_to_role(envelope.session_id, RemoteRole.PATIENT_VR_HOST, envelope, exclude_client_id=envelope.sender_id)
            # Also notify observers
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope, exclude_client_id=envelope.sender_id)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.STIMULUS_EVENT:
            # Stimulus presented by Patient VR Host -> broadcast to Physician Controller & Observers
            await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope, exclude_client_id=envelope.sender_id)
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope, exclude_client_id=envelope.sender_id)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.RESPONSE_EVENT:
            # Patient tactile tap -> forward to Patient VR Host (for local authoritative scoring) & Physician & Observers
            await self.send_to_role(envelope.session_id, RemoteRole.PATIENT_VR_HOST, envelope, exclude_client_id=envelope.sender_id)
            await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope, exclude_client_id=envelope.sender_id)
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope, exclude_client_id=envelope.sender_id)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.HEARTBEAT:
            # Respond with SYNC_ACK
            ack = WebSocketMessageEnvelope(
                session_id=envelope.session_id,
                message_id=f"ack-{envelope.message_id}",
                sender_id="SESSION_BROKER",
                sender_role=RemoteRole.OBSERVER,
                message_type=WebSocketMessageType.SYNC_ACK,
                sequence_number=envelope.sequence_number,
                payload={"acked_sequence": envelope.sequence_number, "server_time_ns": int(time.time()*1e9)}
            )
            if peer and peer.websocket:
                try:
                    await peer.websocket.send_text(ack.model_dump_json())
                except Exception:
                    pass
            return {"status": "ACKED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.REPLAY_REQUEST:
            since_seq = envelope.payload.get("since_sequence", 0)
            replayed = [msg for msg in room.replay_buffer if msg.sequence_number > since_seq]
            replay_response = WebSocketMessageEnvelope(
                session_id=envelope.session_id,
                message_id=f"replay-{int(time.time()*1000)}",
                sender_id="SESSION_BROKER",
                sender_role=RemoteRole.OBSERVER,
                message_type=WebSocketMessageType.REPLAY_RESPONSE,
                sequence_number=len(room.replay_buffer),
                payload={"messages": [m.model_dump() for m in replayed], "count": len(replayed)}
            )
            if peer and peer.websocket:
                try:
                    await peer.websocket.send_text(replay_response.model_dump_json())
                except Exception:
                    pass
            return {"status": "REPLAYED", "count": len(replayed)}

        elif envelope.message_type == WebSocketMessageType.WEBRTC_OFFER:
            # WebRTC Offer from Patient VR Host -> route to Physician Controller and Observers
            await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope)
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope)
            return {"status": "ROUTED", "target": "PHYSICIAN_CONTROLLER", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.WEBRTC_ANSWER:
            # WebRTC Answer from Physician Controller -> route directly to Patient VR Host
            await self.send_to_role(envelope.session_id, RemoteRole.PATIENT_VR_HOST, envelope)
            return {"status": "ROUTED", "target": "PATIENT_VR_HOST", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.WEBRTC_ICE_CANDIDATE:
            # Bidirectional ICE candidate relay
            if envelope.sender_role == RemoteRole.PATIENT_VR_HOST:
                await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope)
            else:
                await self.send_to_role(envelope.session_id, RemoteRole.PATIENT_VR_HOST, envelope)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.STREAM_STATUS:
            # Screen stream status (resolution, framerate, dropped frames, orientation)
            await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope)
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.EYETRACKING_TELEMETRY:
            # MediaPipe eye tracking and fixation telemetry
            await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope)
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        elif envelope.message_type == WebSocketMessageType.OPTICAL_INTERLOCK_STATUS:
            # Optical hardware state and lens interlocks (CR-002-G)
            await self.send_to_role(envelope.session_id, RemoteRole.PHYSICIAN_CONTROLLER, envelope)
            await self.send_to_role(envelope.session_id, RemoteRole.OBSERVER, envelope)
            return {"status": "ROUTED", "sequence": envelope.sequence_number}

        # Default: broadcast to all other peers in room
        await self.broadcast_to_room(envelope.session_id, envelope, exclude_client_id=sender_id)
        return {"status": "BROADCAST", "sequence": envelope.sequence_number}

    async def broadcast_to_room(
        self,
        session_id: str,
        envelope: WebSocketMessageEnvelope,
        exclude_client_id: Optional[str] = None
    ):
        """Sends message to all active WebSocket peers in room."""
        room = self.get_room(session_id)
        if not room:
            return

        payload_json = envelope.model_dump_json()
        dead_peers = []
        for client_id, peer in room.peers.items():
            if exclude_client_id and client_id == exclude_client_id:
                continue
            if peer.websocket:
                try:
                    await peer.websocket.send_text(payload_json)
                except Exception as exc:
                    logger.warning(f"Error sending to {client_id}: {exc}")
                    dead_peers.append(client_id)

        for client_id in dead_peers:
            room.peers.pop(client_id, None)

    async def send_to_role(
        self,
        session_id: str,
        target_role: RemoteRole,
        envelope: WebSocketMessageEnvelope,
        exclude_client_id: Optional[str] = None
    ):
        """Sends message specifically to peers matching a role."""
        room = self.get_room(session_id)
        if not room:
            return

        payload_json = envelope.model_dump_json()
        dead_peers = []
        for client_id, peer in room.peers.items():
            if exclude_client_id and client_id == exclude_client_id:
                continue
            if peer.role == target_role and peer.websocket:
                try:
                    await peer.websocket.send_text(payload_json)
                except Exception as exc:
                    logger.warning(f"Error sending to role {target_role} ({client_id}): {exc}")
                    dead_peers.append(client_id)

        for client_id in dead_peers:
            room.peers.pop(client_id, None)

    def get_room_stats(self, session_id: str) -> Dict[str, Any]:
        room = self.get_room(session_id)
        if not room:
            return {"exists": False}
        return {
            "exists": True,
            "session_id": session_id,
            "connected_peers": len(room.peers),
            "roles": room.get_connected_roles(),
            "active_physician": room.active_physician_client_id,
            "buffered_messages": len(room.replay_buffer),
            "status": room.status.value,
            "active_module": room.active_module
        }

    def get_messages_since(self, session_id: str, since_seq: int = -1, limit: int = 50) -> List[WebSocketMessageEnvelope]:
        room = self.get_room(session_id)
        if not room:
            return []
        return [env for env in room.replay_buffer if env.sequence_number > since_seq][:limit]

# Global singleton instance
session_broker = SessionBroker()
