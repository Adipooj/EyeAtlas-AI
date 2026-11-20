"""EyeAtlas VR - Security & Cryptographic Services.
Implements RFC 7519 compliant JWTs, PBKDF2-HMAC-SHA256 password hashing,
role-based access control (RBAC), and session revocation.
"""
from __future__ import annotations
import base64
import hashlib
import hmac
import json
import os
import time
from typing import Any, Dict, Optional
from fastapi import Depends, HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from backend.app.core.config import settings

bearer_scheme = HTTPBearer(auto_error=False)

# In-memory revocation set (populated upon logout/session revocation)
REVOKED_TOKENS: set[str] = set()

def hash_password(password: str) -> str:
    """Hash password using PBKDF2-HMAC-SHA256 with 600,000 iterations and 16-byte random salt."""
    salt = os.urandom(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 600_000)
    return f"pbkdf2:sha256:600000${base64.b64encode(salt).decode('ascii')}${base64.b64encode(dk).decode('ascii')}"

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify password against stored PBKDF2 hash using constant-time comparison."""
    try:
        header, salt_b64, hash_b64 = hashed_password.split("$")
        algorithm, sub_algo, iterations = header.split(":")
        salt = base64.b64decode(salt_b64.encode("ascii"))
        expected_hash = base64.b64decode(hash_b64.encode("ascii"))
        computed_hash = hashlib.pbkdf2_hmac(sub_algo, plain_password.encode("utf-8"), salt, int(iterations))
        return hmac.compare_digest(expected_hash, computed_hash)
    except Exception:
        return False

def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

def _b64url_decode(s: str) -> bytes:
    padding = 4 - (len(s) % 4)
    if padding != 4:
        s += "=" * padding
    return base64.urlsafe_b64decode(s.encode("ascii"))

def create_access_token(data: Dict[str, Any], expires_delta_seconds: Optional[int] = None) -> str:
    """Generate an RFC 7519 HMAC-SHA256 signed JSON Web Token."""
    now = int(time.time())
    expire = now + (expires_delta_seconds if expires_delta_seconds is not None else settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60)
    payload = {
        **data,
        "iat": now,
        "exp": expire,
        "iss": "EyeAtlas-VR-FastAPI-v1",
    }
    header = {"alg": "HS256", "typ": "JWT"}
    header_json = json.dumps(header, separators=(",", ":")).encode("utf-8")
    payload_json = json.dumps(payload, separators=(",", ":")).encode("utf-8")

    unsigned_token = f"{_b64url_encode(header_json)}.{_b64url_encode(payload_json)}"
    signature = hmac.new(settings.SECRET_KEY.encode("utf-8"), unsigned_token.encode("ascii"), hashlib.sha256).digest()
    return f"{unsigned_token}.{_b64url_encode(signature)}"

def decode_access_token(token: str) -> Dict[str, Any]:
    """Decode and cryptographically verify an RFC 7519 JWT."""
    if token in REVOKED_TOKENS:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has been revoked")

    parts = token.split(".")
    if len(parts) != 3:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Malformed JWT")

    unsigned_token = f"{parts[0]}.{parts[1]}"
    provided_sig = _b64url_decode(parts[2])
    expected_sig = hmac.new(settings.SECRET_KEY.encode("utf-8"), unsigned_token.encode("ascii"), hashlib.sha256).digest()

    if not hmac.compare_digest(provided_sig, expected_sig):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token signature")

    try:
        payload = json.loads(_b64url_decode(parts[1]).decode("utf-8"))
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token payload")

    now = int(time.time())
    if payload.get("exp", 0) < now:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has expired")

    return payload

def revoke_token(token: str) -> None:
    """Add token to revocation blacklist."""
    REVOKED_TOKENS.add(token)

def get_current_user_payload(credentials: Optional[HTTPAuthorizationCredentials] = Security(bearer_scheme)) -> Dict[str, Any]:
    """FastAPI Dependency: Authenticate and extract active user token payload."""
    if not credentials or not credentials.credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing authorization header")
    return decode_access_token(credentials.credentials)

def require_roles(*allowed_roles: str):
    """FastAPI Dependency Factory: Enforce Role-Based Access Control."""
    def role_checker(payload: Dict[str, Any] = Depends(get_current_user_payload)) -> Dict[str, Any]:
        user_role = payload.get("role", "")
        if user_role not in allowed_roles:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Access forbidden: User role '{user_role}' does not have permission (required: {allowed_roles})"
            )
        return payload
    return role_checker
