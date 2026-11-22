package com.itexpert120.yomu.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync's bookkeeping besides the ledger: the pull cursor, this device's id and clock, books with
 * records waiting for them, fonts still downloading, and progress offers. Stored as sync metadata
 * rows whose keys are fixed (existing installs already hold them).
 */
@Singleton
internal class SyncState @Inject constructor(private val store: SyncStore) {
    /** The server sequence number this device has pulled up to. */
    suspend fun cursor(): Double = store.metadata(CURSOR)?.toDoubleOrNull() ?: 0.0

    suspend fun setCursor(seq: Double) = store.putMetadata(CURSOR, seq.toString())

    /** This device's id within the group (the HLC tie-breaker), created on first use. */
    suspend fun device(): String = store.metadata(DEVICE) ?: UUID.randomUUID().toString().replace("-", "").take(12).also {
        store.putMetadata(DEVICE, it)
    }

    /** The latest stamp this device issued or saw. */
    suspend fun clock(): Hlc? = store.metadata(CLOCK)?.let(Hlc::decode)

    suspend fun saveClock(latest: Hlc) = store.putMetadata(CLOCK, latest.encode())

    /** Records arrived for book [sha256], which isn't on this device yet. */
    suspend fun markAwaitingBook(sha256: String) = store.putMetadata(PENDING_BOOK_PREFIX + sha256, sha256)

    suspend fun awaitingBooks(): Set<String> = store.metadataWithPrefix(PENDING_BOOK_PREFIX).values.toSet()

    /** The book's waiting records were applied. */
    suspend fun clearAwaitingBook(sha256: String) = store.deleteMetadata(PENDING_BOOK_PREFIX + sha256)

    /** Fonts this device should have but hasn't downloaded yet. */
    suspend fun pendingFonts(): Set<String> = store.metadataWithPrefix(PENDING_FONT_PREFIX).values.toSet()

    suspend fun markFontPending(family: String) = store.putMetadata(PENDING_FONT_PREFIX + family, family)

    suspend fun clearFontPending(family: String) = store.deleteMetadata(PENDING_FONT_PREFIX + family)

    /** A newer reading position from another device for book [sha256], if one is waiting. */
    fun offer(sha256: String): Flow<ProgressOffer?> = (OFFER_PREFIX + sha256).let { key ->
        store.observeMetadataWithPrefix(key).map { rows ->
            rows[key]?.let { runCatching { SyncJson.decodeFromString(ProgressOffer.serializer(), it) }.getOrNull() }
        }
    }

    suspend fun putOffer(offer: ProgressOffer) = store.putMetadata(OFFER_PREFIX + offer.sha256, SyncJson.encodeToString(ProgressOffer.serializer(), offer))

    suspend fun clearOffer(sha256: String) = store.deleteMetadata(OFFER_PREFIX + sha256)

    private companion object {
        const val CURSOR = "cursor"
        const val CLOCK = "clock"
        const val DEVICE = "device"
        const val PENDING_BOOK_PREFIX = "pendingBook/"
        const val PENDING_FONT_PREFIX = "fontPending/"
        const val OFFER_PREFIX = "offer/"
    }
}
