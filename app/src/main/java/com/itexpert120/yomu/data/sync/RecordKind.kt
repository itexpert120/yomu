package com.itexpert120.yomu.data.sync

import kotlinx.serialization.KSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How one kind of synced data travels: its wire key, the payload it encodes, how this device's
 * state becomes records ([snapshot]) and how an incoming record becomes local state ([apply]).
 *
 * The invariant every kind keeps: applying a record and snapshotting again yields the payload
 * [apply] reported, or each applied change would echo back as a local edit.
 */
internal interface RecordKind<P : Any> {
    /** The wire key, `<key>/<id>` names a record. Never change it: other versions read it. */
    val key: String

    /** Records belong to one book, matched across devices by the EPUB's content hash. */
    val bookBound: Boolean

    /** Never tombstoned: a missing local record means "nothing to send", not "deleted". */
    val neverDeleted: Boolean get() = false

    /** A record for a book this device doesn't have applies anyway instead of waiting for it. */
    val appliesWithoutBook: Boolean get() = false

    /** Records carry the sender's device name (shown in "Continue from <device>"). */
    val carriesDeviceLabel: Boolean get() = false

    /** Room tables whose changes can change this kind's snapshot. */
    val watchedTables: Set<String> get() = emptySet()

    /** Whether DataStore writes can change this kind's snapshot. */
    val watchesPreferences: Boolean get() = false

    /** Lower applies first within a batch. */
    val applyOrder: Int get() = 1

    val payload: KSerializer<P>

    suspend fun snapshot(): List<LocalRecord>

    /** The book a record belongs to, if [bookBound]. */
    fun bookSha(id: String, payload: P?): String? = if (bookBound) id else null

    /** [payload] null = deleted. */
    suspend fun apply(id: String, payload: P?, ctx: ApplyContext): Applied

    /** A book was just imported here (before its waiting records are applied). */
    suspend fun onBookAdded(sha256: String, bookId: String) = Unit

    /** Retries work that failed earlier (e.g. offline downloads). */
    suspend fun retryPending() = Unit

    fun encode(value: P): String = SyncJson.encodeToString(payload, value)

    fun record(id: String, sha256: String?, value: P, timeHint: Long) = LocalRecord(key, id, sha256, encode(value), timeHint)

    /** The ledger outcome when [value] is now the local state, unmerged. */
    fun stored(value: P?) = Applied(value?.let(::encode))
}

/**
 * [bookId]: the local book for a book-bound record, if it is on this device. [firstContact]: this
 * device has never reconciled the record, so local data may predate sync and gets merged.
 * [device] labels progress offers.
 */
internal data class ApplyContext(val bookId: String?, val firstContact: Boolean, val device: String?, val hlc: Hlc)

/**
 * What the ledger should record after applying: the payload now true locally, whether to push it
 * back (a first-contact merge), and whether it waits for a book this device doesn't have yet.
 */
internal data class Applied(
    val ledgerPayload: String?,
    val dirty: Boolean = false,
    val sha256: String? = null,
    val awaitingBook: Boolean = false,
)

/** Every synced kind, by wire key. Callers serialise applies (SyncEngine's mutex). */
@Singleton
internal class RecordKinds @Inject constructor(
    kinds: Set<@JvmSuppressWildcards RecordKind<*>>,
    private val store: SyncStore,
) {
    val all: List<RecordKind<*>> = kinds.sortedBy { it.key }
    private val byKey = all.associateBy { it.key }

    init {
        require(byKey.size == all.size) { "Duplicate record kind keys" }
    }

    fun byKey(key: String): RecordKind<*>? = byKey[key]

    /** The kind of record `<key>/<id>`, or null for a kind this build doesn't know. */
    fun of(recordName: String): RecordKind<*>? = byKey[recordName.substringBefore('/')]

    /** Decodes and applies one record; one for a book not on this device waits for it. */
    suspend fun apply(kind: RecordKind<*>, id: String, payload: String?, firstContact: Boolean, device: String?, hlc: Hlc): Applied = applyTyped(kind, id, payload, firstContact, device, hlc)

    private suspend fun <P : Any> applyTyped(kind: RecordKind<P>, id: String, raw: String?, firstContact: Boolean, device: String?, hlc: Hlc): Applied {
        val payload = raw?.let { SyncJson.decodeFromString(kind.payload, it) }
        val sha = kind.bookSha(id, payload)
        val bookId = sha?.let { store.bookIdFor(it) }
        if (kind.bookBound && !kind.appliesWithoutBook && raw != null && bookId == null) {
            return Applied(raw, sha256 = sha, awaitingBook = true)
        }
        return kind.apply(id, payload, ApplyContext(bookId, firstContact, device, hlc)).copy(sha256 = sha)
    }

    /** Applies everything already known for a book that was just imported on this device. */
    suspend fun onBookAdded(sha256: String, bookId: String) {
        all.forEach { it.onBookAdded(sha256, bookId) }
        store.liveLedgerForBook(sha256)
            .sortedBy { it.kind }
            .forEach { row ->
                val kind = byKey[row.kind]?.takeUnless { it.appliesWithoutBook } ?: return@forEach
                val hlc = Hlc(row.physicalTime, row.logicalCounter.toInt(), row.originDeviceId)
                apply(kind, row.entityId, row.payloadJson, firstContact = false, device = null, hlc = hlc)
            }
    }

    suspend fun retryPending() = all.forEach { it.retryPending() }
}
