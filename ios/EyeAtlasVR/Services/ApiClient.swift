//
//  ApiClient.swift
//  EyeAtlasVR
//
//  Asynchronous REST client connecting iOS to the FastAPI service layer (/api/v1).
//

import Foundation

public class EyeAtlasApiClient {
    public var baseUrl: String
    public var authToken: String?
    public var tenantId: String
    private let session: URLSession

    public init(baseUrl: String = "http://127.0.0.1:8000/api/v1", tenantId: String = "TENANT-APEX", session: URLSession = .shared) {
        self.baseUrl = baseUrl
        self.tenantId = tenantId
        self.session = session
    }

    private func createRequest(path: String, method: String = "GET") -> URLRequest {
        let url = URL(string: "\(baseUrl)\(path)")!
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(tenantId, forHTTPHeaderField: "X-Tenant-ID")
        if let token = authToken {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return req
    }

    public func checkHealth() async throws -> [String: Any] {
        let req = createRequest(path: "/health")
        let (data, response) = try await session.data(for: req)
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        return try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
    }

    public func listPatients() async throws -> [PatientRecord] {
        let req = createRequest(path: "/patients")
        let (data, response) = try await session.data(for: req)
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode([PatientRecord].self, from: data)
    }

    public func getReport(sessionId: String) async throws -> ClinicalReport {
        let req = createRequest(path: "/reports/\(sessionId)")
        let (data, response) = try await session.data(for: req)
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(ClinicalReport.self, from: data)
    }
}
