package com.itexpert120.yomu.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** One device in a sync test: its own store, local data and engine, on a shared server. */
internal class SyncTestDevice(label: String, deviceId: String, transport: SyncTransport, scope: CoroutineScope) {
    val store = InMemorySyncStore()
    private val changes = TestLocalChangeSource()
    private val local = MapLocalRecords(changes)
    val engine = SyncEngine(store, local, changes, transport, { label }, scope)

    init {
        // A fixed device id keeps HLC tie-breaks deterministic.
        store.seedMetadata("device", deviceId)
    }

    fun addBook(sha256: String) {
        store.books["book-$sha256"] = sha256
    }

    fun removeBook(sha256: String) {
        store.books.remove("book-$sha256")
    }

    /** A local edit; [timeHint] dates it as pre-sync data. */
    fun put(kind: SyncKind, id: String, payload: String, timeHint: Long = 1) = local.put(kind, id, payload, timeHint)

    fun delete(kind: SyncKind, id: String) = local.delete(kind, id)

    fun value(kind: SyncKind, id: String): String? = local.value(kind, id)
}

internal class TestLocalChangeSource : LocalChangeSource {
    private val flow = MutableSharedFlow<Set<SyncKind>>(extraBufferCapacity = 64)
    override val changes: Flow<Set<SyncKind>> = flow

    fun changed(kind: SyncKind) {
        flow.tryEmit(setOf(kind))
    }
}

/** Local data as plain records; book-bound ids are the book's hash. Every write reports a change. */
private class MapLocalRecords(private val changes: TestLocalChangeSource) : LocalRecords {
    private val records = mutableMapOf<String, LocalRecord>()

    fun put(kind: SyncKind, id: String, payload: String, timeHint: Long) {
        records[kind.name(id)] = LocalRecord(kind, id, if (kind.bookBound) id else null, payload, timeHint)
        changes.changed(kind)
    }

    fun delete(kind: SyncKind, id: String) {
        records.remove(kind.name(id))
        changes.changed(kind)
    }

    fun value(kind: SyncKind, id: String) = records[kind.name(id)]?.payload

    override suspend fun snapshot(kind: SyncKind) = records.values.filter { it.kind == kind }

    override suspend fun apply(kind: SyncKind, id: String, payload: String?, firstContact: Boolean, device: String?, hlc: Hlc): Applied {
        if (payload == null) delete(kind, id) else put(kind, id, payload, 1)
        return Applied(payload, sha256 = if (kind.bookBound) id else null)
    }

    override suspend fun onBookAdded(sha256: String, bookId: String) = Unit
    override suspend fun beforeApply() = Unit
    override suspend fun keptWithoutLocalCopy(kind: SyncKind) = emptySet<String>()
    override suspend fun retryPending() = Unit
}
