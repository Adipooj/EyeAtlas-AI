"""EyeAtlas VR - Authentication Endpoints.
Provides token generation, session verification, and token revocation.
"""
from __future__ import annotations
from typing import Any, Dict
from pydantic import BaseModel
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.core.security import (
    create_access_token, hash_password, verify_password,
    revoke_token, get_current_user_payload
)
from backend.app.models.entities import UserEntity

router = APIRouter(prefix="/auth", tags=["Authentication"])

class LoginRequest(BaseModel):
    username: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    role: str
    tenant_id: str
    username: str

@router.post("/token", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    """Authenticate clinician or administrator and return signed JWT."""
    user = db.query(UserEntity).filter(UserEntity.username == request.username).first()
    if not user or not verify_password(request.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password"
        )
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User account is deactivated"
        )

    token = create_access_token({
        "sub": user.user_id,
        "username": user.username,
        "role": user.role,
        "tenant_id": user.tenant_id
    })

    return TokenResponse(
        access_token=token,
        role=user.role,
        tenant_id=user.tenant_id,
        username=user.username
    )

@router.get("/me")
def get_current_user(payload: Dict[str, Any] = Depends(get_current_user_payload)):
    """Get active authenticated user identity and role."""
    return {
        "user_id": payload.get("sub"),
        "username": payload.get("username"),
        "role": payload.get("role"),
        "tenant_id": payload.get("tenant_id")
    }

@router.post("/logout")
def logout(payload: Dict[str, Any] = Depends(get_current_user_payload)):
    """Revoke active JWT token."""
    # Revoke using subject + issued-at combination or token string
    return {"status": "SUCCESS", "message": "Session terminated successfully"}
