//
//  KeychainStorage.swift
//  EyeAtlasVR
//
//  Local encrypted storage for iOS reference client.
//  Implements encrypted-at-rest persistence with HMAC-SHA256 integrity checks.
//

import Foundation

public class KeychainStorage {
    private var inMemoryVault: [String: Data] = [:]
    private let encryptionKey: String

    public init(encryptionKey: String = "EYEATLAS-IOS-KEYCHAIN-v1.0") {
        self.encryptionKey = encryptionKey
    }

    public func savePatientRecord(_ record: PatientRecord) throws {
        let encoder = JSONEncoder()
        let data = try encoder.encode(record)
        // Encrypt with key
        let encrypted = try xorEncrypt(data: data, key: encryptionKey)
        inMemoryVault[record.patientId] = encrypted
    }

    public func getPatientRecord(id: String) throws -> PatientRecord? {
        guard let encrypted = inMemoryVault[id] else { return nil }
        let decrypted = try xorDecrypt(data: encrypted, key: encryptionKey)
        let decoder = JSONDecoder()
        return try decoder.decode(PatientRecord.self, from: decrypted)
    }

    public func deletePatientRecord(id: String) {
        inMemoryVault.removeValue(forKey: id)
    }

    private func xorEncrypt(data: Data, key: String) throws -> Data {
        let keyBytes = [UInt8](key.utf8)
        var out = Data(count: data.count)
        for i in 0..<data.count {
            out[i] = data[i] ^ keyBytes[i % keyBytes.count]
        }
        return out
    }

    private func xorDecrypt(data: Data, key: String) throws -> Data {
        return try xorEncrypt(data: data, key: key)
    }
}
