"""EyeAtlas VR - Shared Backend Configuration.
Governing Specification: MASTER_BLUEPRINT.md v1.2 | Phase X01
"""
from __future__ import annotations
import os
from pydantic import BaseModel

class Settings(BaseModel):
    API_V1_STR: str = "/api/v1"
    PROJECT_NAME: str = "EyeAtlas VR Shared Backend"
    VERSION: str = "1.0.0-X01"
    SECRET_KEY: str = os.getenv("EYEATLAS_SECRET_KEY", "eyeatlas-vr-super-secret-key-32bytes-min-len!!")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./eyeatlas_backend.sqlite")
    TENANT_HEADER_NAME: str = "X-Tenant-ID"
    REGULATORY_STATUS: str = "INVESTIGATIONAL_ONLY_NOT_FOR_COMMERCIAL_DISTRIBUTION"
    IS_RELEASE_APPROVED: bool = False

settings = Settings()
