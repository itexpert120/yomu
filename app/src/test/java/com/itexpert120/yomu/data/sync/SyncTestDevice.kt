package com.itexpert120.yomu.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.builtins.serializer

/** A record kind used by engine tests; payloads are plain strings. */
internal enum class TestKind(val key: String, val bookBound: Boolean) {
    Note("note", bookBound = false),

    /** Book-bound; the record id is the book's hash. */
    BookNote("bookNote", bookBound = true),
}

/** One device in a sync test: its own store, local data and engine, on a shared server. */
internal class SyncTestDevice(label: String, deviceId: String, transport: SyncTransport, scope: CoroutineScope) {
    val store = InMemorySyncStore()
    private val changes = TestLocalChangeSource()
    private val kinds = TestKind.entries.associateWith { MapKind(it, changes) }
    private val registry = RecordKinds(kinds.values.toSet(), store)
    val engine = SyncEngine(store, registry, changes, transport, { label }, scope)

    init {
        // A fixed device id keeps HLC tie-breaks deterministic.
        store.seedMetadata("device", deviceId)
    }

    /** A book arriving on this device, like an import invalidating the books table. */
    fun addBook(sha256: String) {
        store.books["book-$sha256"] = sha256
        TestKind.entries.filter { it.bookBound }.forEach { changes.changed(it.key) }
    }

    fun removeBook(sha256: String) {
        store.books.remove("book-$sha256")
    }

    /** A local edit; [timeHint] dates it as pre-sync data. */
    fun put(kind: TestKind, id: String, value: String, timeHint: Long = 1) = kinds.getValue(kind).put(id, value, timeHint)

    fun delete(kind: TestKind, id: String) = kinds.getValue(kind).delete(id)

    fun value(kind: TestKind, id: String): String? = kinds.getValue(kind).value(id)
}

internal class TestLocalChangeSource : LocalChangeSource {
    private val flow = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)
    override val changes: Flow<Set<String>> = flow

    fun changed(key: String) {
        flow.tryEmit(setOf(key))
    }
}

/** Local data as a map of plain records. Every write reports a change, as Room would. */
private class MapKind(private val spec: TestKind, private val changes: TestLocalChangeSource) : RecordKind<String> {
    private val records = mutableMapOf<String, LocalRecord>()
    override val key = spec.key
    override val bookBound = spec.bookBound
    override val payload = String.serializer()

    fun put(id: String, value: String, timeHint: Long) {
        records[id] = record(id, if (bookBound) id else null, value, timeHint)
        changes.changed(key)
    }

    fun delete(id: String) {
        records.remove(id)
        changes.changed(key)
    }

    fun value(id: String) = records[id]?.let { SyncJson.decodeFromString(payload, it.payload) }

    override suspend fun snapshot() = records.values.toList()

    override suspend fun apply(id: String, payload: String?, ctx: ApplyContext): Applied {
        if (payload == null) delete(id) else put(id, payload, 1)
        return stored(payload)
    }
}
