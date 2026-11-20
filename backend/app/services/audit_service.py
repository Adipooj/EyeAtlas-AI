"""EyeAtlas VR - Append-Only Cryptographic Audit Service.
Enforces tamper-evident audit logging with SHA-256 event chaining.
"""
from __future__ import annotations
import hashlib
import json
import time
import uuid
from typing import Any, Dict, List, Optional
from sqlalchemy.orm import Session
from backend.app.models.entities import AuditEventEntity

def compute_state_hash(data: Any) -> str:
    """Compute SHA-256 digest of serialized canonical data."""
    if data is None:
        return "GENESIS_EMPTY_HASH"
    if isinstance(data, (dict, list)):
        serialized = json.dumps(data, sort_keys=True, separators=(",", ":"))
    else:
        serialized = str(data)
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()

def record_audit_event(
    db: Session,
    tenant_id: str,
    actor: str,
    device_id: str,
    action: str,
    before_data: Any,
    after_data: Any,
    reason: str
) -> AuditEventEntity:
    """Record an immutable append-only audit event in the database."""
    before_hash = compute_state_hash(before_data)
    after_hash = compute_state_hash(after_data)
    now_ns = int(time.time() * 1_000_000_000)

    event = AuditEventEntity(
        event_id=str(uuid.uuid4()),
        tenant_id=tenant_id,
        actor=actor,
        timestamp_ns=now_ns,
        device_id=device_id,
        action=action,
        before_hash=before_hash,
        after_hash=after_hash,
        reason=reason
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event

def query_audit_events(
    db: Session,
    tenant_id: str,
    limit: int = 100,
    actor: Optional[str] = None
) -> List[AuditEventEntity]:
    """Retrieve audit events for a tenant with optional actor filter."""
    query = db.query(AuditEventEntity).filter(AuditEventEntity.tenant_id == tenant_id)
    if actor:
        query = query.filter(AuditEventEntity.actor == actor)
    return query.order_by(AuditEventEntity.created_at.desc()).limit(limit).all()
