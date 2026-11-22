package com.itexpert120.yomu.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

/**
 * The sync server in memory, with backend/convex/sync.ts semantics: last-writer-wins by HLC string,
 * a per-group sequence that every stored write bumps, live paged pulls, and typed errors.
 */
internal class InMemorySyncTransport : SyncTransport {
    private data class Group(val records: Map<String, WireRecord> = emptyMap(), val seq: Double = 0.0)

    private val groups = MutableStateFlow<Map<String, Group>>(emptyMap())

    /** What the server speaks; change to simulate an older or newer backend. */
    var protocol = SyncProtocol.VERSION
    var minClientProtocol = SyncProtocol.VERSION

    /** When false every call fails like a dropped connection. */
    var online: Boolean
        get() = _connected.value
        set(value) {
            _connected.value = value
        }

    var disconnects = 0
        private set

    override val available = true

    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected

    override val generation: StateFlow<Int> = MutableStateFlow(0)

    /** The sequence number of the latest stored write in [token]'s group. */
    fun head(token: String): Double = groups.value[token]?.seq ?: 0.0

    /** The stored record for [serverKey], or null if the server never stored one. */
    fun record(token: String, serverKey: String): WireRecord? = groups.value[token]?.records?.get(serverKey)

    override suspend fun checkCompatible() {
        requireOnline()
        if (minClientProtocol > SyncProtocol.VERSION) throw SyncUpdateRequiredException()
        if (protocol < SyncProtocol.MIN_SERVER) throw SyncServerOutdatedException()
    }

    override suspend fun create(token: String) {
        checkClient()
        groups.update { if (token in it) it else it + (token to Group()) }
    }

    override suspend fun exists(token: String): Boolean {
        checkClient()
        return token in groups.value
    }

    override suspend fun push(token: String, records: List<WireRecord>): Double {
        checkClient()
        var head = 0.0
        groups.update { all ->
            var group = all[token] ?: throw SyncGroupGoneException(IllegalStateException("unknown_group"))
            for (record in records) {
                val existing = group.records[record.key]
                if (existing != null && existing.hlc >= record.hlc) continue
                val seq = group.seq + 1
                group = Group(group.records + (record.key to record.copy(seq = seq)), seq)
            }
            head = group.seq
            all + (token to group)
        }
        return head
    }

    override fun pull(token: String, after: Double): Flow<Result<PullPage>> = combineState().map { (all, up) ->
        runCatching {
            if (!up) throw IOException("offline")
            checkClient()
            val group = all[token] ?: throw SyncGroupGoneException(IllegalStateException("unknown_group"))
            val page = group.records.values.filter { it.seq > after }.sortedBy { it.seq }.take(PAGE)
            PullPage(page, group.seq)
        }
    }.distinctUntilChanged()

    override suspend fun destroy(token: String) {
        checkClient()
        groups.update { it - token }
    }

    override fun disconnect() {
        disconnects++
    }

    private fun combineState(): Flow<Pair<Map<String, Group>, Boolean>> = combine(groups, _connected) { all, up -> all to up }

    private fun requireOnline() {
        if (!online) throw IOException("offline")
    }

    private fun checkClient() {
        requireOnline()
        if (minClientProtocol > SyncProtocol.VERSION) throw SyncUpdateRequiredException()
    }

    private companion object {
        const val PAGE = 500
    }
}
