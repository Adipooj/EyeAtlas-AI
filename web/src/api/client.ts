/**
 * EyeAtlas VR - Web API Client.
 * Connects to the FastAPI shared service layer (/api/v1).
 */
import type { PatientRecord, ClinicalSession, ClinicalReport } from '../types/contracts.ts';

export class EyeAtlasApiClient {
  private baseUrl: string;
  private authToken: string | null = null;
  private tenantId: string;

  constructor(baseUrl = 'http://127.0.0.1:8000/api/v1', tenantId = 'TENANT-APEX') {
    this.baseUrl = baseUrl;
    this.tenantId = tenantId;
  }

  setToken(token: string) {
    this.authToken = token;
  }

  setTenant(tenantId: string) {
    this.tenantId = tenantId;
  }

  private getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Tenant-ID': this.tenantId
    };
    if (this.authToken) {
      headers['Authorization'] = `Bearer ${this.authToken}`;
    }
    return headers;
  }

  async checkHealth(): Promise<{ status: string; version: string; databaseConnected: boolean }> {
    const res = await fetch(`${this.baseUrl}/health`);
    if (!res.ok) throw new Error(`Health check failed: ${res.status}`);
    return res.json();
  }

  async login(username: string, password: string): Promise<{ access_token: string; role: string; tenant_id: string }> {
    const res = await fetch(`${this.baseUrl}/auth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    if (!res.ok) throw new Error(`Authentication failed: ${res.statusText}`);
    const data = await res.json();
    this.authToken = data.access_token;
    this.tenantId = data.tenant_id;
    return data;
  }

  async listPatients(): Promise<PatientRecord[]> {
    const res = await fetch(`${this.baseUrl}/patients`, {
      headers: this.getHeaders()
    });
    if (!res.ok) throw new Error(`Failed to list patients: ${res.statusText}`);
    return res.json();
  }

  async getPatient(patientId: string): Promise<PatientRecord> {
    const res = await fetch(`${this.baseUrl}/patients/${patientId}`, {
      headers: this.getHeaders()
    });
    if (!res.ok) throw new Error(`Patient not found: ${res.statusText}`);
    return res.json();
  }

  async createPatient(patient: PatientRecord): Promise<PatientRecord> {
    const res = await fetch(`${this.baseUrl}/patients`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify(patient)
    });
    if (!res.ok) throw new Error(`Failed to create patient: ${res.statusText}`);
    return res.json();
  }

  async getSessionDetail(sessionId: string): Promise<any> {
    const res = await fetch(`${this.baseUrl}/sessions/${sessionId}`, {
      headers: this.getHeaders()
    });
    if (!res.ok) throw new Error(`Session not found: ${res.statusText}`);
    return res.json();
  }

  async getReport(sessionId: string): Promise<ClinicalReport> {
    const res = await fetch(`${this.baseUrl}/reports/${sessionId}`, {
      headers: this.getHeaders()
    });
    if (!res.ok) throw new Error(`Report not found: ${res.statusText}`);
    return res.json();
  }

  async signReport(sessionId: string, clinicianNotes: string, decision: string): Promise<any> {
    const res = await fetch(`${this.baseUrl}/reports/${sessionId}/sign`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify({ clinicianNotes, decision })
    });
    if (!res.ok) throw new Error(`Failed to sign report: ${res.statusText}`);
    return res.json();
  }
}
