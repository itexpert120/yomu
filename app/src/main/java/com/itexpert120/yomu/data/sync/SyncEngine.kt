package com.itexpert120.yomu.data.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.database.SyncLedgerEntity
import com.itexpert120.yomu.core.database.SyncMetadataEntity
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.data.sync.crypto.SyncKeys
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
    private val database: YomuDatabase,
    private val dao: SyncDao,
    private val dataStore: DataStore<Preferences>,
    private val snapshotter: SyncSnapshotter,
    private val applier: SyncApplier,
    private val transport: SyncTransport,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var job: Job? = null
    private val pushRequests = Channel<Unit>(Channel.CONFLATED)
    private lateinit var clock: HlcClock
    private lateinit var device: String

    /** Shown to other devices in "Continue from <device>": the name the user gave this device. */
    private val deviceLabel: String by lazy {
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
    }

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
        if (!keepStatus) _status.value = SyncStatus()
    }

    /** Pushes pending changes now (e.g. when the app goes to the background). */
    fun flush() {
        if (job?.isActive == true) pushRequests.trySend(Unit)
    }

    /** Forgets everything synced on this device (ledger, cursor, offers). Local data stays. */
    suspend fun resetLocalState() = mutex.withLock { dao.reset() }

    // region Pull

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun pullLoop(keys: SyncKeys, caughtUp: CompletableDeferred<Unit>) {
        val cursor = MutableStateFlow(dao.metadata(KEY_CURSOR)?.toDoubleOrNull() ?: 0.0)
        // The subscription can end on a connection error; resubscribe from the saved cursor.
        while (true) {
            // A rebuilt connection (new generation) needs its own subscription.
            combine(cursor, transport.generation) { after, _ -> after }
                .flatMapLatest { after -> transport.pull(keys.token, after).map { after to it } }
                .collect { (after, result) ->
                    val page = result.getOrElse { error ->
                        when (ConvexSyncTransport.run { error.classify() }) {
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
                        dao.putMetadata(SyncMetadataEntity(KEY_CURSOR, last.toString()))
                        saveClock()
                        last
                    }
                    // A short page means this device has everything; the subscription stays open for more.
                    if (page.records.size < PULL_PAGE) caughtUp.complete(Unit)
                    cursor.value = next
                    if (caughtUp.isCompleted) markUpToDate()
                }
            _status.value = _status.value.copy(phase = SyncStatus.Phase.Offline)
            delay(RETRY_MILLIS)
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
        applier.refreshInstalledFonts()
        val decoded = records.mapNotNull { record ->
            val envelope = runCatching {
                SyncJson.decodeFromString(SyncEnvelope.serializer(), keys.open(record.key, record.blob ?: return@mapNotNull null).decodeToString())
            }.getOrElse {
                Log.w(TAG, "Skipping a record that doesn't decrypt", it)
                return@mapNotNull null
            }
            val hlc = Hlc.decode(record.hlc) ?: return@mapNotNull null
            val kind = SyncKind.of(envelope.name) ?: return@mapNotNull null
            Triple(envelope, hlc, kind)
        }
        // Fonts first so settings that use them resolve to installed files.
        for ((envelope, hlc, kind) in decoded.sortedBy { if (it.third == SyncKind.Font) 0 else 1 }) {
            clock.observe(hlc)
            val existing = dao.ledger(envelope.name)
            if (existing != null && existing.hlc() >= hlc) continue
            val id = envelope.name.substringAfter('/')
            val applied = runCatching {
                applier.apply(kind, id, envelope.payload, existing == null, envelope.device, hlc)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Couldn't apply ${kind.key}", error)
                continue
            }
            if (applied.awaitingBook && applied.sha256 != null) {
                dao.putMetadata(SyncMetadataEntity(PENDING_BOOK_PREFIX + applied.sha256, applied.sha256))
            }
            val stamp = if (applied.dirty) clock.tick() else hlc
            dao.upsertLedger(
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
            SyncKind.entries.forEach { diff(it) }
        }
        pushRequests.trySend(Unit)
        val roomChanges = database.invalidationTracker.createFlow(*TABLE_KINDS.keys.toTypedArray(), emitInitialState = false)
            .map { tables -> tables.flatMap { TABLE_KINDS[it].orEmpty() }.toSet() }
        // Settings, themes and fonts live in DataStore. The first emission is the current state.
        val prefChanges = dataStore.data.drop(1).map { setOf(SyncKind.Settings, SyncKind.Theme, SyncKind.Font) }
        val pending = mutableSetOf<SyncKind>()
        merge(roomChanges, prefChanges)
            .onEach { kinds -> synchronized(pending) { pending += kinds } }
            .debounce(DEBOUNCE_MILLIS)
            .collect {
                val kinds = synchronized(pending) { pending.toSet().also { pending.clear() } }
                if (kinds.isEmpty()) return@collect
                mutex.withLock {
                    if (SyncKind.Progress in kinds) adoptNewBooks()
                    kinds.forEach { diff(it) }
                }
                pushRequests.trySend(Unit)
            }
    }

    /** Applies records that arrived before their book was on this device. */
    private suspend fun adoptNewBooks() {
        val pending = dao.metadataWithPrefix(PENDING_BOOK_PREFIX)
        if (pending.isEmpty()) return
        val local = snapshotter.localBooks().entries.associate { (id, sha) -> sha to id }
        applier.refreshInstalledFonts()
        pending.forEach { marker ->
            val bookId = local[marker.value] ?: return@forEach
            applier.onBookAdded(marker.value, bookId)
            dao.deleteMetadata(marker.key)
        }
    }

    /** Stamps local changes of [kind] (relative to the ledger) and marks them for pushing. */
    private suspend fun diff(kind: SyncKind) {
        val local = snapshotter.snapshot(kind).associateBy { it.name }
        val ledger = dao.ledgerOfKind(kind.key).associateBy { it.recordKey }
        val changed = mutableListOf<SyncLedgerEntity>()
        for (record in local.values) {
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
            val localShas = if (kind.bookBound) snapshotter.localBooks().values.toSet() else emptySet()
            val pendingFonts = if (kind == SyncKind.Font) applier.pendingFonts() else emptySet()
            for (row in ledger.values) {
                if (row.deleted || row.recordKey in local) continue
                // A book removed from this device keeps its synced data everywhere else.
                if (kind.bookBound && row.bookSha256 !in localShas) continue
                if (row.entityId in pendingFonts) continue
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
            dao.upsertLedger(changed)
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
                    applier.retryPendingFonts()
                    continue
                }
                if (sent == 0) break
            }
            markUpToDate()
        }
    }

    /** Pushes one batch of dirty records; returns how many were sent. */
    private suspend fun pushOnce(keys: SyncKeys): Int {
        val rows = mutex.withLock { dao.dirty(PUSH_BATCH) }
        if (rows.isEmpty()) return 0
        _status.value = _status.value.copy(phase = SyncStatus.Phase.Syncing)
        val wire = rows.map { row ->
            val envelope = SyncEnvelope(
                name = row.recordKey,
                payload = row.payloadJson,
                device = if (row.kind == SyncKind.Progress.key) deviceLabel else null,
            )
            val serverKey = keys.serverKey(row.recordKey)
            val blob = keys.seal(serverKey, SyncJson.encodeToString(SyncEnvelope.serializer(), envelope).encodeToByteArray())
            WireRecord(serverKey, blob, row.hlc().encode())
        }
        transport.push(keys.token, wire)
        mutex.withLock {
            rows.forEach { dao.markPushed(it.recordKey, it.physicalTime, it.logicalCounter, it.originDeviceId) }
        }
        return rows.size
    }

    // endregion

    private suspend fun loadClock() {
        device = dao.metadata(KEY_DEVICE) ?: UUID.randomUUID().toString().replace("-", "").take(12).also {
            dao.putMetadata(SyncMetadataEntity(KEY_DEVICE, it))
        }
        clock = HlcClock(device, dao.metadata(KEY_CLOCK)?.let(Hlc::decode))
    }

    private suspend fun saveClock() {
        clock.latest?.let { dao.putMetadata(SyncMetadataEntity(KEY_CLOCK, it.encode())) }
    }

    private fun markUpToDate() {
        _status.value = SyncStatus(SyncStatus.Phase.UpToDate, lastSyncedAt = System.currentTimeMillis())
    }

    private fun LocalRecord.toLedger(stamp: Hlc) = SyncLedgerEntity(
        recordKey = name,
        bookSha256 = sha256,
        kind = kind.key,
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

        /** Which record kinds each table feeds. */
        private val TABLE_KINDS = mapOf(
            "books" to setOf(SyncKind.Meta, SyncKind.Progress, SyncKind.Shelf),
            "book_collections" to setOf(SyncKind.Shelf),
            "collections" to setOf(SyncKind.Collection),
            "chapter_reads" to setOf(SyncKind.Chapter),
            "chapter_progress" to setOf(SyncKind.Chapter),
            "bookmarks" to setOf(SyncKind.Bookmark),
            "highlights" to setOf(SyncKind.Highlight),
            "reader_settings" to setOf(SyncKind.BookSettings),
            "reading_sessions" to setOf(SyncKind.Session),
        )
    }
}
