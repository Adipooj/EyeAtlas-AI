"""EyeAtlas VR - API v1 Router Aggregator."""
from __future__ import annotations
from fastapi import APIRouter
from backend.app.api.v1.endpoints import (
    auth, patients, sessions, sync, reports, admin, health, remote_sessions
)

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(patients.router)
api_router.include_router(sessions.router)
api_router.include_router(remote_sessions.router)
api_router.include_router(sync.router)
api_router.include_router(reports.router)
api_router.include_router(admin.router)
api_router.include_router(health.router)
