package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.database.SyncLedgerEntity
import com.itexpert120.yomu.core.database.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What sync keeps on this device: the ledger (last known state of every record), the key-value
 * sync metadata, and which books are here. Room in the app; in memory in tests.
 */
internal interface SyncStore {
    suspend fun ledger(key: String): SyncLedgerEntity?
    suspend fun ledgerOfKind(kind: String): List<SyncLedgerEntity>
    suspend fun liveLedgerForBook(sha256: String): List<SyncLedgerEntity>

    /** Dirty rows, oldest stamp first. */
    suspend fun dirty(limit: Int): List<SyncLedgerEntity>
    suspend fun dirtyCount(): Int
    suspend fun upsertLedger(rows: List<SyncLedgerEntity>)

    /** Clears the dirty flag only if the row still carries the pushed stamp. */
    suspend fun markPushed(key: String, physical: Long, counter: Long, device: String)

    suspend fun metadata(key: String): String?
    suspend fun putMetadata(key: String, value: String)
    suspend fun deleteMetadata(key: String)

    /** Rows whose key starts with [prefix], as key to value. */
    suspend fun metadataWithPrefix(prefix: String): Map<String, String>
    fun observeMetadataWithPrefix(prefix: String): Flow<Map<String, String>>

    /** Forgets the ledger and all metadata. */
    suspend fun reset()

    /** Content hashes of the books on this device, by local id. */
    suspend fun localBooks(): Map<String, String>
    suspend fun bookIdFor(sha256: String): String?
    suspend fun shaForBook(bookId: String): String?
}

@Singleton
internal class RoomSyncStore @Inject constructor(private val dao: SyncDao) : SyncStore {
    override suspend fun ledger(key: String) = dao.ledger(key)
    override suspend fun ledgerOfKind(kind: String) = dao.ledgerOfKind(kind)
    override suspend fun liveLedgerForBook(sha256: String) = dao.liveLedgerForBook(sha256)
    override suspend fun dirty(limit: Int) = dao.dirty(limit)
    override suspend fun dirtyCount() = dao.dirtyCount()
    override suspend fun upsertLedger(rows: List<SyncLedgerEntity>) = dao.upsertLedger(rows)
    override suspend fun markPushed(key: String, physical: Long, counter: Long, device: String) = dao.markPushed(key, physical, counter, device)
    override suspend fun metadata(key: String) = dao.metadata(key)
    override suspend fun putMetadata(key: String, value: String) = dao.putMetadata(SyncMetadataEntity(key, value))
    override suspend fun deleteMetadata(key: String) = dao.deleteMetadata(key)
    override suspend fun metadataWithPrefix(prefix: String) = dao.metadataWithPrefix(prefix).associate { it.key to it.value }
    override fun observeMetadataWithPrefix(prefix: String) = dao.observeMetadataWithPrefix(prefix).map { rows -> rows.associate { it.key to it.value } }
    override suspend fun reset() = dao.reset()
    override suspend fun localBooks() = dao.books().associate { it.id to it.sha256 }
    override suspend fun bookIdFor(sha256: String) = dao.bookByHash(sha256)?.id
    override suspend fun shaForBook(bookId: String) = dao.shaForBook(bookId)
}
