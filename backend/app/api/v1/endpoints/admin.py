"""EyeAtlas VR - Administrative & Tenant Management Endpoints.
Restricted exclusively to ADMINISTRATOR and TENANT_ADMIN roles.
"""
from __future__ import annotations
import uuid
from typing import Any, Dict, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.security import require_roles
from backend.app.models.entities import TenantEntity, UserEntity, AuditEventEntity
from backend.app.services.audit_service import query_audit_events

router = APIRouter(prefix="/admin", tags=["Administration"])

class CreateTenantRequest(BaseModel):
    tenant_id: str
    name: str
    tier: str = "TIER_2_VISION_CENTER"

class TenantResponse(BaseModel):
    tenant_id: str
    name: str
    tier: str
    is_active: bool

@router.get("/tenants", response_model=List[TenantResponse])
def list_tenants(
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("ADMINISTRATOR"))
):
    """List all registered system tenants (Global Administrator only)."""
    tenants = db.query(TenantEntity).all()
    return [
        TenantResponse(
            tenant_id=t.tenant_id,
            name=t.name,
            tier=t.tier,
            is_active=t.is_active
        )
        for t in tenants
    ]

@router.post("/tenants", response_model=TenantResponse, status_code=status.HTTP_201_CREATED)
def create_tenant(
    request: CreateTenantRequest,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("ADMINISTRATOR"))
):
    """Register a new clinical site / tenant organization."""
    existing = db.query(TenantEntity).filter(TenantEntity.tenant_id == request.tenant_id).first()
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Tenant ID already registered")

    tenant = TenantEntity(
        tenant_id=request.tenant_id,
        name=request.name,
        tier=request.tier,
        is_active=True
    )
    db.add(tenant)
    db.commit()
    db.refresh(tenant)
    return TenantResponse(
        tenant_id=tenant.tenant_id,
        name=tenant.name,
        tier=tenant.tier,
        is_active=tenant.is_active
    )

@router.get("/audit-logs")
def get_audit_trail(
    limit: int = 100,
    db: Session = Depends(get_db),
    user: Dict[str, Any] = Depends(require_roles("ADMINISTRATOR", "TENANT_ADMIN", "CLINICIAN"))
):
    """Retrieve immutable audit events for the active tenant."""
    tenant_id = user["tenant_id"]
    events = query_audit_events(db=db, tenant_id=tenant_id, limit=limit)
    return [
        {
            "eventId": ev.event_id,
            "tenantId": ev.tenant_id,
            "actor": ev.actor,
            "deviceId": ev.device_id,
            "action": ev.action,
            "timestampNs": ev.timestamp_ns,
            "beforeHash": ev.before_hash,
            "afterHash": ev.after_hash,
            "reason": ev.reason
        }
        for ev in events
    ]
