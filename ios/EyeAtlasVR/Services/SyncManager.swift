//
//  SyncManager.swift
//  EyeAtlasVR
//
//  Offline-First Synchronization Manager for iOS client.
//  Maintains offline queues, handles batch packaging, and preserves client observations.
//

import Foundation

public enum IosSyncStatus {
    case idle
    case queued
    case syncing
    case success
    case offline
    case conflictResolved
    case error
}

public class IosSyncManager {
    public var isSyncEnabled: Boolean_Alias = false
    public var clientDeviceId: String = "IPHONE-PRO-15"
    public var tenantId: String = "TENANT-APEX"
    private var queue: [String] = []
    public private(set) var status: IosSyncStatus = .idle
    public private(set) var lastSyncTimestampMs: Int64 = 0

    public init(isSyncEnabled: Bool = false) {
        self.isSyncEnabled = isSyncEnabled
    }

    public var pendingCount: Int {
        return queue.count
    }

    public func queuePatient(_ patient: PatientRecord) {
        let encoder = JSONEncoder()
        if let data = try? encoder.encode(patient), let str = String(data: data, encoding: .utf8) {
            queue.append(str)
            if status == .idle || status == .success {
                status = .queued
            }
        }
    }

    public func syncNow(mockSuccess: Bool = true, hasConflict: Bool = false) -> (status: IosSyncStatus, processed: Int) {
        guard isSyncEnabled else {
            status = .offline
            return (.offline, 0)
        }

        guard !queue.isEmpty else {
            status = .idle
            return (.idle, 0)
        }

        status = .syncing
        if mockSuccess {
            let count = queue.count
            queue.removeAll()
            lastSyncTimestampMs = Int64(Date().timeIntervalSince1970 * 1000)
            status = hasConflict ? .conflictResolved : .success
            return (status, count)
        } else {
            status = .offline
            return (.offline, 0)
        }
    }
}
public typealias Boolean_Alias = Bool
