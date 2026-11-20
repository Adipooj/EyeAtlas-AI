"""EyeAtlas VR - FastAPI Application Entrypoint.
Governing Specification: MASTER_BLUEPRINT.md v1.2 | Phase X01
"""
from __future__ import annotations
import sys
from pathlib import Path

# Ensure eyeatlas-vr root is always in sys.path
root_dir = str(Path(__file__).resolve().parents[2])
if root_dir not in sys.path:
    sys.path.insert(0, root_dir)

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session
from backend.app.core.config import settings
from backend.app.core.database import engine, Base, SessionLocal
from backend.app.core.security import hash_password
from backend.app.models.entities import TenantEntity, UserEntity, PatientEntity, SessionEntity
from backend.app.api.v1.router import api_router

# Create DB tables
Base.metadata.create_all(bind=engine)

def seed_database():
    """Seed initial tenants, users, and demonstration patients on startup."""
    db: Session = SessionLocal()
    try:
        # 1. Tenants
        if not db.query(TenantEntity).filter(TenantEntity.tenant_id == "TENANT-APEX").first():
            db.add(TenantEntity(tenant_id="TENANT-APEX", name="Apex Eye Hospital", tier="TIER_1_APEX", is_active=True))
        if not db.query(TenantEntity).filter(TenantEntity.tenant_id == "TENANT-RURAL").first():
            db.add(TenantEntity(tenant_id="TENANT-RURAL", name="Rural Vision Center", tier="TIER_2_VISION_CENTER", is_active=True))
        db.commit()

        # 2. Users
        if not db.query(UserEntity).filter(UserEntity.username == "doc_apex").first():
            db.add(UserEntity(
                user_id="U-DOC-APEX",
                tenant_id="TENANT-APEX",
                username="doc_apex",
                email="doc_apex@eyeatlas.org",
                hashed_password=hash_password("DocPass123!"),
                role="CLINICIAN",
                is_active=True
            ))
        if not db.query(UserEntity).filter(UserEntity.username == "admin").first():
            db.add(UserEntity(
                user_id="U-ADMIN",
                tenant_id="TENANT-APEX",
                username="admin",
                email="admin@eyeatlas.org",
                hashed_password=hash_password("AdminPass123!"),
                role="ADMINISTRATOR",
                is_active=True
            ))
        db.commit()

        # 3. Demonstration Patients
        if not db.query(PatientEntity).filter(PatientEntity.patient_id == "P-101").first():
            db.add(PatientEntity(
                patient_id="P-101",
                tenant_id="TENANT-APEX",
                age_band="40-59",
                has_diabetes=True,
                symptoms_json='["BLURRED_VISION", "GLARE"]',
                current_correction="CURRENT_HABITUAL",
                comorbidity_flags_json='{"DIABETIC_RETINOPATHY": true, "GLAUCOMA": true}'
            ))
        if not db.query(PatientEntity).filter(PatientEntity.patient_id == "P-102").first():
            db.add(PatientEntity(
                patient_id="P-102",
                tenant_id="TENANT-APEX",
                age_band="60-79",
                has_diabetes=False,
                symptoms_json='["PERIPHERAL_SHADOWS"]',
                current_correction="UNAIDED",
                comorbidity_flags_json='{"CATARACT": true}'
            ))
        if not db.query(PatientEntity).filter(PatientEntity.patient_id == "P-103").first():
            db.add(PatientEntity(
                patient_id="P-103",
                tenant_id="TENANT-APEX",
                age_band="20-39",
                has_diabetes=False,
                symptoms_json='["DRYNESS"]',
                current_correction="BEST_CORRECTED",
                comorbidity_flags_json='{"DRY_EYE": true}'
            ))
        db.commit()

        # 4. Demonstration Session
        if not db.query(SessionEntity).filter(SessionEntity.session_id == "S-DEMO-01").first():
            import time
            db.add(SessionEntity(
                session_id="S-DEMO-01",
                patient_id="P-101",
                tenant_id="TENANT-APEX",
                patient_phone_id="DEV-PHONE-VR-01",
                clinician_phone_id="DEV-PAD-01",
                response_phone_id="DEV-PAD-01",
                response_role="RESPONSE_PAD",
                mode="CLINICAL",
                state="FUNCTIONAL_TESTS",
                consent_granted=True,
                started_at_ns=int(time.time() * 1_000_000_000)
            ))
            db.commit()
    except Exception as e:
        db.rollback()
        print(f"Database seed notice: {e}")
    finally:
        db.close()

seed_database()

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    docs_url=f"{settings.API_V1_STR}/docs",
    redoc_url=f"{settings.API_V1_STR}/redoc"
)

# CORS configuration supporting localhost, 127.0.0.1, and all local browser ports
app.add_middleware(
    CORSMiddleware,
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1)(:\d+)?$",
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix=settings.API_V1_STR)

# Mount web portal for zero-configuration unified deployment
web_dir = Path(__file__).resolve().parents[2] / "web"
if web_dir.exists():
    app.mount("/portal", StaticFiles(directory=str(web_dir), html=True), name="portal")

@app.get("/")
def root():
    return {
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "apiDocs": f"{settings.API_V1_STR}/docs",
        "regulatoryNotice": settings.REGULATORY_STATUS
    }
