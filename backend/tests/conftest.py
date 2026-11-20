"""EyeAtlas VR Backend Test Configuration & Fixtures.
Provides in-memory SQLite test database and authenticated client fixtures.
"""
from __future__ import annotations
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from backend.app.core.database import Base, get_db
from backend.app.core.security import hash_password, create_access_token
from backend.app.main import app
from backend.app.models.entities import TenantEntity, UserEntity

# In-memory SQLite DB for isolated unit and integration testing
SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

@pytest.fixture(scope="session", autouse=True)
def setup_test_db():
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()
    try:
        # Seed test tenants
        t1 = TenantEntity(tenant_id="TENANT-APEX", name="Apex Eye Hospital", tier="TIER_1_APEX")
        t2 = TenantEntity(tenant_id="TENANT-RURAL", name="Rural Clinic", tier="TIER_2_VISION_CENTER")
        db.add_all([t1, t2])
        db.commit()

        # Seed test users
        admin = UserEntity(
            user_id="U-ADMIN",
            tenant_id="TENANT-APEX",
            username="admin",
            email="admin@eyeatlas.org",
            hashed_password=hash_password("AdminPass123!"),
            role="ADMINISTRATOR"
        )
        doc_apex = UserEntity(
            user_id="U-DOC-APEX",
            tenant_id="TENANT-APEX",
            username="doc_apex",
            email="doc_apex@eyeatlas.org",
            hashed_password=hash_password("DocPass123!"),
            role="CLINICIAN"
        )
        doc_rural = UserEntity(
            user_id="U-DOC-RURAL",
            tenant_id="TENANT-RURAL",
            username="doc_rural",
            email="doc_rural@eyeatlas.org",
            hashed_password=hash_password("RuralPass123!"),
            role="CLINICIAN"
        )
        db.add_all([admin, doc_apex, doc_rural])
        db.commit()
    finally:
        db.close()
    yield
    Base.metadata.drop_all(bind=engine)

@pytest.fixture
def db_session():
    connection = engine.connect()
    transaction = connection.begin()
    session = TestingSessionLocal(bind=connection)
    yield session
    session.close()
    transaction.rollback()
    connection.close()

@pytest.fixture
def client(db_session):
    def override_get_db():
        try:
            yield db_session
        finally:
            pass
    app.dependency_overrides[get_db] = override_get_db
    yield TestClient(app)
    app.dependency_overrides.clear()

@pytest.fixture
def admin_token():
    return create_access_token({
        "sub": "U-ADMIN",
        "username": "admin",
        "role": "ADMINISTRATOR",
        "tenant_id": "TENANT-APEX"
    })

@pytest.fixture
def clinician_apex_token():
    return create_access_token({
        "sub": "U-DOC-APEX",
        "username": "doc_apex",
        "role": "CLINICIAN",
        "tenant_id": "TENANT-APEX"
    })

@pytest.fixture
def clinician_rural_token():
    return create_access_token({
        "sub": "U-DOC-RURAL",
        "username": "doc_rural",
        "role": "CLINICIAN",
        "tenant_id": "TENANT-RURAL"
    })
