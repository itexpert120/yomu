package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.SyncLedgerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** [SyncStore] in memory; [books] stands in for the library (local id to content hash). */
internal class InMemorySyncStore : SyncStore {
    val ledgerRows = mutableMapOf<String, SyncLedgerEntity>()
    private val meta = MutableStateFlow<Map<String, String>>(emptyMap())
    val books = mutableMapOf<String, String>()

    override suspend fun ledger(key: String) = ledgerRows[key]
    override suspend fun ledgerOfKind(kind: String) = ledgerRows.values.filter { it.kind == kind }
    override suspend fun liveLedgerForBook(sha256: String) = ledgerRows.values.filter { it.bookSha256 == sha256 && !it.deleted }
    override suspend fun dirty(limit: Int) = ledgerRows.values.filter { it.dirty }.sortedWith(compareBy({ it.physicalTime }, { it.logicalCounter })).take(limit)
    override suspend fun dirtyCount() = ledgerRows.values.count { it.dirty }
    override suspend fun upsertLedger(rows: List<SyncLedgerEntity>) = rows.forEach { ledgerRows[it.recordKey] = it }

    override suspend fun markPushed(key: String, physical: Long, counter: Long, device: String) {
        val row = ledgerRows[key] ?: return
        if (row.physicalTime == physical && row.logicalCounter == counter && row.originDeviceId == device) ledgerRows[key] = row.copy(dirty = false)
    }

    fun seedMetadata(key: String, value: String) = meta.update { it + (key to value) }

    override suspend fun metadata(key: String) = meta.value[key]
    override suspend fun putMetadata(key: String, value: String) = meta.update { it + (key to value) }
    override suspend fun deleteMetadata(key: String) = meta.update { it - key }
    override suspend fun metadataWithPrefix(prefix: String) = meta.value.filterKeys { it.startsWith(prefix) }
    override fun observeMetadataWithPrefix(prefix: String): Flow<Map<String, String>> = meta.map { all -> all.filterKeys { it.startsWith(prefix) } }

    override suspend fun reset() {
        ledgerRows.clear()
        meta.value = emptyMap()
    }

    override suspend fun localBooks(): Map<String, String> = books.toMap()
    override suspend fun bookIdFor(sha256: String) = books.entries.firstOrNull { it.value == sha256 }?.key
    override suspend fun shaForBook(bookId: String) = books[bookId]
}
