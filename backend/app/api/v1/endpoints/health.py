"""EyeAtlas VR - Health & Readiness Endpoints."""
from __future__ import annotations
import time
from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session
from backend.app.core.config import settings
from backend.app.core.database import get_db

router = APIRouter(tags=["Health"])

@router.get("/health")
def healthcheck(db: Session = Depends(get_db)):
    """Operational health monitoring endpoint."""
    db_ok = False
    try:
        db.execute(text("SELECT 1"))
        db_ok = True
    except Exception:
        db_ok = False

    return {
        "status": "HEALTHY" if db_ok else "DEGRADED",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "databaseConnected": db_ok,
        "serverTimeNs": int(time.time() * 1_000_000_000),
        "regulatoryStatus": settings.REGULATORY_STATUS
    }
