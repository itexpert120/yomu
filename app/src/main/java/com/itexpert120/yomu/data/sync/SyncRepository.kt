package com.itexpert120.yomu.data.sync

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.data.sync.crypto.SyncKeys
import com.itexpert120.yomu.data.sync.crypto.SyncPhrase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of entering a phrase on a new device. */
sealed interface JoinResult {
    data object Joined : JoinResult

    /** The words are valid but no device has turned sync on with them. */
    data object NotFound : JoinResult
    data object UpdateRequired : JoinResult
    data class Failed(val error: Throwable) : JoinResult
}

/**
 * Account-less sync (docs/sync.md). The 12-word phrase is the only credential; this is the one
 * entry point the UI and reader use.
 */
@Singleton
class SyncRepository @Inject internal constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val engine: SyncEngine,
    private val secrets: SyncSecretStore,
    private val transport: SyncTransport,
    private val dao: SyncDao,
) {
    private val lifecycle = Mutex()

    @Volatile
    private var codec: SyncPhrase.Codec? = null

    /** False in builds without a sync deployment configured. */
    val available: Boolean get() = transport.available

    val enabled: Flow<Boolean> = secrets.present
    val status: StateFlow<SyncStatus> = engine.status

    init {
        scope.launch {
            // Another device deleted the group: this one turns sync off too (data stays local).
            engine.status.collect { if (it.phase == SyncStatus.Phase.GroupDeleted) turnOff(keepStatus = true) }
        }
    }

    private var pauseJob: Job? = null

    /**
     * Syncs while the app is in the foreground, if a phrase is stored. Call once, on the main thread,
     * at app start. In the background the process is soon frozen by the system, so a live
     * connection there only burns battery on reconnect attempts: pending changes are pushed when the
     * app leaves the foreground, then the connection closes until it comes back.
     */
    fun start() {
        if (!available) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = resume()

                override fun onStop(owner: LifecycleOwner) = pause()
            },
        )
    }

    private fun resume() {
        pauseJob?.cancel()
        pauseJob = null
        scope.launch {
            lifecycle.withLock { secrets.read()?.let { engine.start(SyncKeys(it)) } }
        }
    }

    private fun pause() {
        pauseJob?.cancel()
        pauseJob = scope.launch {
            lifecycle.withLock {
                engine.pause(timeoutMs = BACKGROUND_DRAIN_MS)
                transport.disconnect()
            }
        }
    }

    /** Creates a new group from fresh words and starts syncing this device's library into it. */
    suspend fun turnOn(): Result<SyncPhrase> = lifecycle.withLock {
        runCatching {
            transport.checkCompatible()
            val phrase = codec().generate()
            val keys = SyncKeys(phrase.entropy)
            transport.create(keys.token)
            begin(phrase, keys)
            phrase
        }
    }

    fun parse(input: String): SyncPhrase.ParseResult = codecOrNull()?.parse(input) ?: SyncPhrase.ParseResult.WrongWordCount(0)

    fun suggestions(prefix: String): List<String> = codecOrNull()?.suggestions(prefix).orEmpty()

    /** Joins the group the words belong to. Never creates one, so a mistyped phrase can't. */
    suspend fun join(phrase: SyncPhrase): JoinResult = lifecycle.withLock {
        try {
            transport.checkCompatible()
            val keys = SyncKeys(phrase.entropy)
            if (!transport.exists(keys.token)) return@withLock JoinResult.NotFound
            begin(phrase, keys)
            JoinResult.Joined
        } catch (_: SyncUpdateRequiredException) {
            JoinResult.UpdateRequired
        } catch (error: Exception) {
            JoinResult.Failed(error)
        }
    }

    /** The words for this device's group, to show again or pair another device. */
    suspend fun phrase(): SyncPhrase? = secrets.read()?.let { codec().fromEntropy(it) }

    /** Stops syncing on this device only. Its local library and the other devices are untouched. */
    suspend fun turnOff() = turnOff(keepStatus = false)

    /** Deletes the synced copy from the server; every device then stops syncing. Local data stays. */
    suspend fun deleteEverywhere(): Result<Unit> = runCatching {
        val entropy = secrets.read() ?: return@runCatching
        transport.destroy(SyncKeys(entropy).token)
        turnOff()
    }

    /** A newer position from another device for this book, if one is waiting. */
    fun progressOffer(bookId: BookId): Flow<ProgressOffer?> = flow {
        val sha = dao.shaForBook(bookId.value) ?: return@flow emit(null)
        emitAll(
            dao.observeMetadataWithPrefix(SyncApplier.OFFER_PREFIX + sha).map { rows ->
                rows.firstOrNull()?.let { runCatching { SyncJson.decodeFromString(ProgressOffer.serializer(), it.value) }.getOrNull() }
            },
        )
    }

    /** Forgets the offer, whether it was taken (the reader jumped) or dismissed. */
    suspend fun clearOffer(bookId: BookId) {
        dao.shaForBook(bookId.value)?.let { dao.deleteMetadata(SyncApplier.OFFER_PREFIX + it) }
    }

    private suspend fun turnOff(keepStatus: Boolean) = lifecycle.withLock {
        engine.stop(keepStatus)
        secrets.clear()
        engine.resetLocalState()
    }

    private suspend fun begin(phrase: SyncPhrase, keys: SyncKeys) {
        engine.stop(keepStatus = false)
        // A new group starts from a clean ledger so nothing from a previous group leaks into it.
        engine.resetLocalState()
        secrets.write(phrase.entropy)
        engine.start(keys)
    }

    private suspend fun codec(): SyncPhrase.Codec = codec ?: withContext(Dispatchers.IO) {
        SyncPhrase.Codec(
            context.assets.open(WORDLIST).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() },
        ).also { codec = it }
    }

    private fun codecOrNull(): SyncPhrase.Codec? = codec

    /** Loads the wordlist ahead of the join screen so parsing and suggestions are synchronous. */
    suspend fun prepare() {
        codec()
    }

    private companion object {
        const val WORDLIST = "bip39_english.txt"

        // Long enough to push a reading position over a slow link; short enough to beat the freezer.
        const val BACKGROUND_DRAIN_MS = 10_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds
    abstract fun bindTransport(impl: ConvexSyncTransport): SyncTransport
}
