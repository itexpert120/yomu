package com.itexpert120.yomu.data.sync

import android.util.Log
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.database.SyncLedgerEntity
import com.itexpert120.yomu.data.sync.crypto.SyncKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** The name other devices show for this one in "Continue from <device>". */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SyncDeviceLabel

data class SyncStatus(
    val phase: Phase = Phase.Off,
    val lastSyncedAt: Long? = null,
) {
    enum class Phase {
        Off,
        Connecting,
        Syncing,
        UpToDate,
        Offline,

        // Sync was turned off and its data deleted from another device.
        GroupDeleted,

        // The server needs a newer app: "Update Yomu to keep syncing".
        UpdateRequired,

        // The server is older than this app supports; resolves when the backend is deployed.
        ServerOutdated,
    }
}

/**
 * Runs sync while a phrase is stored: a live pull subscription applies changes from other devices,
 * and a tracker diffs local state against the ledger whenever relevant tables or preferences change,
 * then pushes what changed. Everything that touches the ledger runs under [mutex].
 */
@Singleton
internal class SyncEngine @Inject constructor(
    private val store: SyncStore,
    private val kinds: RecordKinds,
    private val localChanges: LocalChangeSource,
    private val transport: SyncTransport,
    @SyncDeviceLabel private val deviceLabel: dagger.Lazy<String>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var job: Job? = null

    // Set once the local-change tracker has loaded the clock and done its first diff.
    @Volatile
    private var tracking = false
    private val pushRequests = Channel<Unit>(Channel.CONFLATED)
    private lateinit var clock: HlcClock
    private lateinit var device: String

    fun start(keys: SyncKeys) {
        if (job?.isActive == true) return
        _status.value = _status.value.copy(phase = SyncStatus.Phase.Connecting)
        job = scope.launch {
            if (!awaitCompatible()) return@launch
            mutex.withLock { loadClock() }
            val caughtUp = CompletableDeferred<Unit>()
            launch { pullLoop(keys, caughtUp) }
            caughtUp.await()
            launch { pushLoop(keys) }
            trackLoop()
        }
    }

    /** [keepStatus] leaves a terminal phase (e.g. GroupDeleted) visible for the UI to explain. */
    suspend fun stop(keepStatus: Boolean = false) {
        job?.cancel()
        job?.join()
        job = null
        tracking = false
        if (!keepStatus) _status.value = SyncStatus()
    }

    /**
     * The app went to the background: stage local changes that are still inside the change debounce
     * (typically the reading position just saved by the reader), push them while the process is
     * still allowed to run, then stop. [timeoutMs] bounds the wait so an offline device stops anyway;
     * whatever didn't go out stays dirty and is pushed on the next start.
     */
    suspend fun pause(timeoutMs: Long) {
        if (job?.isActive != true) return
        withTimeoutOrNull(timeoutMs) {
            if (tracking) mutex.withLock { kinds.all.forEach { diff(it) } }
            pushRequests.trySend(Unit)
            while (store.dirtyCount() > 0) delay(DRAIN_POLL_MILLIS)
        }
        stop()
    }

    /** Forgets everything synced on this device (ledger, cursor, offers). Local data stays. */
    suspend fun resetLocalState() = mutex.withLock { store.reset() }

    // region Pull

    private suspend fun pullLoop(keys: SyncKeys, caughtUp: CompletableDeferred<Unit>) {
        val cursor = MutableStateFlow(store.metadata(KEY_CURSOR)?.toDoubleOrNull() ?: 0.0)
        // The subscription can end on a connection error; resubscribe from the saved cursor.
        while (true) {
            // A rebuilt connection (new generation) needs its own subscription. Any unexpected
            // transport error retries like the push loop does instead of killing the sync job.
            try {
                pullOnce(keys, cursor, caughtUp)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Pull subscription failed", error)
            }
            _status.value = _status.value.copy(phase = SyncStatus.Phase.Offline)
            delay(RETRY_MILLIS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun pullOnce(keys: SyncKeys, cursor: MutableStateFlow<Double>, caughtUp: CompletableDeferred<Unit>) {
        combine(cursor, transport.generation) { after, _ -> after }
            .flatMapLatest { after -> transport.pull(keys.token, after).map { after to it } }
            .collect { (after, result) ->
                val page = result.getOrElse { error ->
                    when (error) {
                        is SyncGroupGoneException -> groupGone()
                        is SyncUpdateRequiredException -> stopWith(SyncStatus.Phase.UpdateRequired)
                        else -> Unit
                    }
                    Log.w(TAG, "Pull failed", error)
                    _status.value = _status.value.copy(phase = SyncStatus.Phase.Offline)
                    return@collect
                }
                if (page.records.isEmpty()) {
                    caughtUp.complete(Unit)
                    markUpToDate()
                    return@collect
                }
                _status.value = _status.value.copy(phase = SyncStatus.Phase.Syncing)
                val next = mutex.withLock {
                    applyBatch(keys, page.records)
                    val last = page.records.maxOf { it.seq }.coerceAtLeast(after)
                    store.putMetadata(KEY_CURSOR, last.toString())
                    saveClock()
                    last
                }
                // A short page means this device has everything; the subscription stays open for more.
                if (page.records.size < PULL_PAGE) caughtUp.complete(Unit)
                cursor.value = next
                if (caughtUp.isCompleted) markUpToDate()
            }
    }

    /** Checks protocol versions before syncing, retrying while offline. False on a version mismatch. */
    private suspend fun awaitCompatible(): Boolean {
        while (true) {
            try {
                transport.checkCompatible()
                return true
            } catch (_: SyncUpdateRequiredException) {
                _status.value = SyncStatus(SyncStatus.Phase.UpdateRequired)
                return false
            } catch (_: SyncServerOutdatedException) {
                _status.value = SyncStatus(SyncStatus.Phase.ServerOutdated)
                return false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Compatibility check failed", error)
                _status.value = _status.value.copy(phase = SyncStatus.Phase.Offline)
                delay(RETRY_MILLIS)
            }
        }
    }

    /** Another device deleted the group: stop here; [SyncRepository] then turns sync off. */
    private fun groupGone(): Nothing = stopWith(SyncStatus.Phase.GroupDeleted)

    /** Ends this run with a terminal [phase] that the UI explains. */
    private fun stopWith(phase: SyncStatus.Phase): Nothing {
        _status.value = SyncStatus(phase)
        job?.cancel()
        throw CancellationException("Sync stopped: $phase")
    }

    private suspend fun applyBatch(keys: SyncKeys, records: List<WireRecord>) {
        val decoded = records.mapNotNull { record ->
            val envelope = runCatching {
                SyncJson.decodeFromString(SyncEnvelope.serializer(), keys.open(record.key, record.blob ?: return@mapNotNull null).decodeToString())
            }.getOrElse {
                Log.w(TAG, "Skipping a record that doesn't decrypt", it)
                return@mapNotNull null
            }
            val hlc = Hlc.decode(record.hlc) ?: return@mapNotNull null
            val kind = kinds.of(envelope.name) ?: return@mapNotNull null
            Triple(envelope, hlc, kind)
        }
        for ((envelope, hlc, kind) in decoded.sortedBy { it.third.applyOrder }) {
            clock.observe(hlc)
            val existing = store.ledger(envelope.name)
            if (existing != null && existing.hlc() >= hlc) continue
            val id = envelope.name.substringAfter('/')
            val applied = runCatching {
                kinds.apply(kind, id, envelope.payload, existing == null, envelope.device, hlc)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Couldn't apply ${kind.key}", error)
                continue
            }
            if (applied.awaitingBook && applied.sha256 != null) {
                store.putMetadata(PENDING_BOOK_PREFIX + applied.sha256, applied.sha256)
            }
            val stamp = if (applied.dirty) clock.tick() else hlc
            store.upsertLedger(
                listOf(
                    SyncLedgerEntity(
                        recordKey = envelope.name,
                        bookSha256 = applied.sha256 ?: existing?.bookSha256,
                        kind = kind.key,
                        entityId = id,
                        payloadJson = applied.ledgerPayload,
                        deleted = applied.ledgerPayload == null,
                        physicalTime = stamp.physical,
                        logicalCounter = stamp.counter.toLong(),
                        originDeviceId = stamp.device,
                        dirty = applied.dirty,
                    ),
                ),
            )
            if (applied.dirty) pushRequests.trySend(Unit)
        }
    }

    // endregion

    // region Local changes

    @OptIn(FlowPreview::class)
    private suspend fun trackLoop() {
        mutex.withLock {
            adoptNewBooks()
            kinds.all.forEach { diff(it) }
        }
        tracking = true
        pushRequests.trySend(Unit)
        val pending = mutableSetOf<String>()
        localChanges.changes
            .onEach { keys -> synchronized(pending) { pending += keys } }
            .debounce(DEBOUNCE_MILLIS)
            .collect {
                val changed = synchronized(pending) { pending.toSet().also { pending.clear() } }.mapNotNull(kinds::byKey)
                if (changed.isEmpty()) return@collect
                mutex.withLock {
                    if (changed.any { it.bookBound }) adoptNewBooks()
                    changed.forEach { diff(it) }
                }
                pushRequests.trySend(Unit)
            }
    }

    /** Applies records that arrived before their book was on this device. */
    private suspend fun adoptNewBooks() {
        val pending = store.metadataWithPrefix(PENDING_BOOK_PREFIX)
        if (pending.isEmpty()) return
        val books = store.localBooks().entries.associate { (id, sha) -> sha to id }
        pending.forEach { (key, sha) ->
            val bookId = books[sha] ?: return@forEach
            kinds.onBookAdded(sha, bookId)
            store.deleteMetadata(key)
        }
    }

    /** Stamps local changes of [kind] (relative to the ledger) and marks them for pushing. */
    private suspend fun diff(kind: RecordKind<*>) {
        val snapshot = kind.snapshot().associateBy { it.name }
        val ledger = store.ledgerOfKind(kind.key).associateBy { it.recordKey }
        val changed = mutableListOf<SyncLedgerEntity>()
        for (record in snapshot.values) {
            val row = ledger[record.name]
            val stamp = when {
                // Pre-sync data is dated by its own timestamp so the genuinely newest copy wins
                // when devices that both have history join the same group.
                row == null -> Hlc(maxOf(record.timeHint, 1), 0, device)
                row.deleted || row.payloadJson != record.payload -> clock.tick()
                else -> continue
            }
            changed += record.toLedger(stamp)
        }
        if (!kind.neverDeleted) {
            val localShas = if (kind.bookBound) store.localBooks().values.toSet() else emptySet()
            for (row in ledger.values) {
                if (row.deleted || row.recordKey in snapshot) continue
                // A book removed from this device keeps its synced data everywhere else.
                if (kind.bookBound && row.bookSha256 !in localShas) continue
                val stamp = clock.tick()
                changed += row.copy(
                    payloadJson = null,
                    deleted = true,
                    physicalTime = stamp.physical,
                    logicalCounter = stamp.counter.toLong(),
                    originDeviceId = stamp.device,
                    dirty = true,
                )
            }
        }
        if (changed.isNotEmpty()) {
            store.upsertLedger(changed)
            saveClock()
        }
    }

    // endregion

    // region Push

    private suspend fun pushLoop(keys: SyncKeys) {
        for (request in pushRequests) {
            while (true) {
                val sent = runCatching { pushOnce(keys) }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (error is SyncGroupGoneException) groupGone()
                    if (error is SyncUpdateRequiredException) stopWith(SyncStatus.Phase.UpdateRequired)
                    Log.w(TAG, "Push failed", error)
                    _status.value = _status.value.copy(phase = SyncStatus.Phase.Offline)
                    delay(RETRY_MILLIS)
                    kinds.retryPending()
                    continue
                }
                if (sent == 0) break
            }
            markUpToDate()
        }
    }

    /** Pushes one batch of dirty records; returns how many were sent. */
    private suspend fun pushOnce(keys: SyncKeys): Int {
        val rows = mutex.withLock { store.dirty(PUSH_BATCH) }
        if (rows.isEmpty()) return 0
        _status.value = _status.value.copy(phase = SyncStatus.Phase.Syncing)
        val wire = rows.map { row ->
            val envelope = SyncEnvelope(
                name = row.recordKey,
                payload = row.payloadJson,
                device = if (kinds.byKey(row.kind)?.carriesDeviceLabel == true) deviceLabel.get() else null,
            )
            val serverKey = keys.serverKey(row.recordKey)
            val blob = keys.seal(serverKey, SyncJson.encodeToString(SyncEnvelope.serializer(), envelope).encodeToByteArray())
            WireRecord(serverKey, blob, row.hlc().encode())
        }
        transport.push(keys.token, wire)
        mutex.withLock {
            rows.forEach { store.markPushed(it.recordKey, it.physicalTime, it.logicalCounter, it.originDeviceId) }
        }
        return rows.size
    }

    // endregion

    private suspend fun loadClock() {
        device = store.metadata(KEY_DEVICE) ?: UUID.randomUUID().toString().replace("-", "").take(12).also {
            store.putMetadata(KEY_DEVICE, it)
        }
        clock = HlcClock(device, store.metadata(KEY_CLOCK)?.let(Hlc::decode))
    }

    private suspend fun saveClock() {
        clock.latest?.let { store.putMetadata(KEY_CLOCK, it.encode()) }
    }

    private fun markUpToDate() {
        _status.value = SyncStatus(SyncStatus.Phase.UpToDate, lastSyncedAt = System.currentTimeMillis())
    }

    private fun LocalRecord.toLedger(stamp: Hlc) = SyncLedgerEntity(
        recordKey = name,
        bookSha256 = sha256,
        kind = kind,
        entityId = entityId,
        payloadJson = payload,
        deleted = false,
        physicalTime = stamp.physical,
        logicalCounter = stamp.counter.toLong(),
        originDeviceId = stamp.device,
        dirty = true,
    )

    private fun SyncLedgerEntity.hlc() = Hlc(physicalTime, logicalCounter.toInt(), originDeviceId)

    companion object {
        private const val TAG = "SyncEngine"
        private const val KEY_CURSOR = "cursor"
        private const val KEY_CLOCK = "clock"
        private const val KEY_DEVICE = "device"
        private const val PENDING_BOOK_PREFIX = "pendingBook/"
        private const val PULL_PAGE = 500
        private const val PUSH_BATCH = 200
        private const val DEBOUNCE_MILLIS = 1_500L
        private const val RETRY_MILLIS = 30_000L
        private const val DRAIN_POLL_MILLIS = 250L
    }
}
