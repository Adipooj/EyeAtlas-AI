"""EyeAtlas VR - Local Backend Runner & Database Seeder."""
from __future__ import annotations
import argparse
import uvicorn
from backend.app.core.database import SessionLocal, engine, Base
from backend.app.core.security import hash_password
from backend.app.models.entities import TenantEntity, UserEntity

def seed_defaults():
    """Seed default tier tenants and test users if empty."""
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        # Check if default tenant exists
        t1 = db.query(TenantEntity).filter(TenantEntity.tenant_id == "TENANT-APEX-01").first()
        if not t1:
            t1 = TenantEntity(tenant_id="TENANT-APEX-01", name="Apex Tertiary Hospital", tier="TIER_1_APEX")
            t2 = TenantEntity(tenant_id="TENANT-RURAL-02", name="Rural Vision Center #4", tier="TIER_2_VISION_CENTER")
            db.add_all([t1, t2])
            db.commit()

        # Check if default admin and clinician users exist
        admin = db.query(UserEntity).filter(UserEntity.username == "admin").first()
        if not admin:
            admin_user = UserEntity(
                user_id="USER-ADMIN-01",
                tenant_id="TENANT-APEX-01",
                username="admin",
                email="admin@eyeatlas.org",
                hashed_password=hash_password("AdminSecurePass123!"),
                role="ADMINISTRATOR"
            )
            clinician_user = UserEntity(
                user_id="USER-DOC-01",
                tenant_id="TENANT-APEX-01",
                username="dr_sharma",
                email="sharma@eyeatlas.org",
                hashed_password=hash_password("ClinicianPass123!"),
                role="CLINICIAN"
            )
            rural_clinician = UserEntity(
                user_id="USER-DOC-02",
                tenant_id="TENANT-RURAL-02",
                username="dr_rural",
                email="rural@eyeatlas.org",
                hashed_password=hash_password("RuralPass123!"),
                role="CLINICIAN"
            )
            db.add_all([admin_user, clinician_user, rural_clinician])
            db.commit()
            print("Seeded default tenants and users successfully.")
    finally:
        db.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="EyeAtlas VR Backend Runner")
    parser.add_argument("--host", default="127.0.0.1", help="Host interface")
    parser.add_argument("--port", type=int, default=8008, help="Port")
    parser.add_argument("--seed", action="store_true", help="Seed default tenants and users")
    args = parser.parse_args()

    seed_defaults()
    uvicorn.run("backend.app.main:app", host=args.host, port=args.port, reload=False)
