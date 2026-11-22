package com.itexpert120.yomu.data.sync

import javax.inject.Inject
import javax.inject.Singleton

/** This device's syncable state, as records: what the engine snapshots and applies into. */
internal interface LocalRecords {
    suspend fun snapshot(kind: SyncKind): List<LocalRecord>

    /** [firstContact]: no ledger row yet, so local data may predate sync and gets merged. */
    suspend fun apply(kind: SyncKind, id: String, payload: String?, firstContact: Boolean, device: String?, hlc: Hlc): Applied

    /** Applies what is already known for a book that was just imported here. */
    suspend fun onBookAdded(sha256: String, bookId: String)

    /** Called before each batch of [apply] calls. */
    suspend fun beforeApply()

    /** Ids of [kind] missing locally that must not be tombstoned (e.g. fonts still downloading). */
    suspend fun keptWithoutLocalCopy(kind: SyncKind): Set<String>

    /** Retries work that failed earlier (offline font downloads). */
    suspend fun retryPending()
}

@Singleton
internal class RoomLocalRecords @Inject constructor(
    private val snapshotter: SyncSnapshotter,
    private val applier: SyncApplier,
) : LocalRecords {
    override suspend fun snapshot(kind: SyncKind) = snapshotter.snapshot(kind)

    override suspend fun apply(kind: SyncKind, id: String, payload: String?, firstContact: Boolean, device: String?, hlc: Hlc) = applier.apply(kind, id, payload, firstContact, device, hlc)

    override suspend fun onBookAdded(sha256: String, bookId: String) {
        applier.refreshInstalledFonts()
        applier.onBookAdded(sha256, bookId)
    }

    override suspend fun beforeApply() = applier.refreshInstalledFonts()

    override suspend fun keptWithoutLocalCopy(kind: SyncKind): Set<String> = if (kind == SyncKind.Font) applier.pendingFonts() else emptySet()

    override suspend fun retryPending() = applier.retryPendingFonts()
}
