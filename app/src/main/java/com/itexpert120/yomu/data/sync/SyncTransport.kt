package com.itexpert120.yomu.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.util.Log
import com.itexpert120.yomu.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.convex.android.MobileConvexClient
import dev.convex.android.QuerySubscriber
import dev.convex.android.WebSocketState
import dev.convex.android.WebSocketStateSubscriber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class WireRecord(val key: String, val blob: String?, val hlc: String, val seq: Double = 0.0)

@Serializable
internal data class PullPage(val records: List<WireRecord>, val head: Double)

@Serializable
internal data class PushResult(val head: Double)

@Serializable
internal data class DestroyResult(val done: Boolean)

@Serializable
internal data class ServerInfo(val protocol: Double, val minClientProtocol: Double)

/**
 * Wire-protocol compatibility with backend/convex/sync.ts. [VERSION] is what this app speaks; the
 * server refuses apps below its minimum, and this app refuses servers below [MIN_SERVER].
 */
internal object SyncProtocol {
    const val VERSION = 1
    const val MIN_SERVER = 1
}

/** The group no longer exists on the server (sync was turned off and deleted from another device). */
internal class SyncGroupGoneException(cause: Throwable) : Exception("Sync group no longer exists", cause)

/** The server requires a newer app. */
internal class SyncUpdateRequiredException(cause: Throwable? = null) : Exception("App update required to sync", cause)

/** The server is older than this app supports. */
internal class SyncServerOutdatedException : Exception("Sync server is out of date")

/** The Convex functions in backend/convex/sync.ts. Numbers travel as Double (Convex float64). */
internal interface SyncTransport {
    /** False when this build has no sync deployment configured. */
    val available: Boolean

    /** Whether the live connection to the server is up. */
    val connected: StateFlow<Boolean>

    /** Bumps whenever the connection is rebuilt; live subscriptions must be re-created then. */
    val generation: StateFlow<Int>

    /** Throws [SyncUpdateRequiredException] / [SyncServerOutdatedException] on a version mismatch. */
    suspend fun checkCompatible()
    suspend fun create(token: String)
    suspend fun exists(token: String): Boolean
    suspend fun push(token: String, records: List<WireRecord>): Double

    /** Live: re-emits whenever records after [after] change on the server. */
    fun pull(token: String, after: Double): Flow<Result<PullPage>>
    suspend fun destroy(token: String)
}

/**
 * Talks to Convex through its native client directly: one-shot `query` for checks, `subscribe`
 * only for the live pull, and plain kotlinx JSON (every value here is a string, bool or float64).
 */
@Singleton
internal class ConvexSyncTransport @Inject constructor(
    @ApplicationContext context: Context,
) : SyncTransport {
    // Per build type: debug → Convex dev deployment, release → production (see app/build.gradle.kts).
    override val available: Boolean = BuildConfig.SYNC_CONVEX_URL.isNotBlank()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _generation = MutableStateFlow(0)
    override val generation: StateFlow<Int> = _generation.asStateFlow()

    private val lock = Any()
    private var current: MobileConvexClient? = null

    // The native client doesn't recover once its socket dies with the network (it stays
    // "connecting" forever), so a fresh client is built when the network comes back.
    init {
        if (available) {
            context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // Also fires right after registration, and on every network switch: only
                        // replace a client that has had time to connect and still hasn't.
                        val stuck = SystemClock.elapsedRealtime() - clientCreatedAt > CONNECT_GRACE_MS
                        if (!_connected.value && stuck) reconnect("network available")
                    }
                },
            )
        }
    }

    @Volatile
    private var clientCreatedAt = 0L

    private val client: MobileConvexClient
        get() = synchronized(lock) {
            current ?: newClient(_generation.value).also {
                current = it
                clientCreatedAt = SystemClock.elapsedRealtime()
            }
        }

    // A closed client can still report state changes; only the current generation's count.
    private fun newClient(generation: Int) = MobileConvexClient(
        BuildConfig.SYNC_CONVEX_URL,
        "yomu-android",
        object : WebSocketStateSubscriber {
            override fun onStateChange(state: WebSocketState) {
                if (generation != _generation.value) return
                Log.d(TAG, "Connection: $state")
                _connected.value = state == WebSocketState.CONNECTED
            }
        },
    )

    /** Drops the current client so the next call builds a fresh one. */
    private fun reconnect(reason: String) {
        val stale = synchronized(lock) { current.also { current = null } } ?: return
        Log.d(TAG, "Rebuilding connection: $reason")
        _connected.value = false
        runCatching { stale.close() }
        _generation.value += 1
    }

    override suspend fun checkCompatible() {
        val info = call { decode(ServerInfo.serializer(), timed { client.query("sync:info", emptyMap()) }) }
        if (info.minClientProtocol > SyncProtocol.VERSION) throw SyncUpdateRequiredException()
        if (info.protocol < SyncProtocol.MIN_SERVER) throw SyncServerOutdatedException()
    }

    override suspend fun create(token: String) {
        call { timed { client.mutation("sync:create", args(token)) } }
    }

    override suspend fun exists(token: String): Boolean = call {
        decode(Boolean.serializer(), timed { client.query("sync:exists", args(token)) })
    }

    override suspend fun push(token: String, records: List<WireRecord>): Double = call {
        val wire = JsonArray(
            records.map {
                JsonObject(mapOf("key" to JsonPrimitive(it.key), "blob" to (it.blob?.let(::JsonPrimitive) ?: JsonNull), "hlc" to JsonPrimitive(it.hlc)))
            },
        )
        decode(PushResult.serializer(), timed { client.mutation("sync:push", args(token, "records" to wire)) }).head
    }

    override fun pull(token: String, after: Double): Flow<Result<PullPage>> = callbackFlow {
        val subscription = client.subscribe(
            "sync:pull",
            args(token, "after" to JsonPrimitive(after)),
            object : QuerySubscriber {
                override fun onUpdate(value: String) {
                    trySend(runCatching { decode(PullPage.serializer(), value) })
                }

                override fun onError(message: String, value: String?) {
                    val error = IOException(value ?: message)
                    trySend(Result.failure(error.classify() ?: error))
                }
            },
        )
        // The client may already have been rebuilt (and this one closed) by the time this ends.
        awaitClose { runCatching { subscription.cancel() } }
    }

    override suspend fun destroy(token: String) {
        call {
            // Each call deletes one batch; repeat until the group is gone.
            do {
                val done = decode(DestroyResult.serializer(), timed { client.mutation("sync:destroy", args(token)) }).done
            } while (!done)
        }
    }

    /** FFI arguments: each value as its own JSON document. */
    private fun args(token: String, vararg extra: Pair<String, JsonElement>): Map<String, String> = (
        mapOf(
            "protocol" to JsonPrimitive(SyncProtocol.VERSION.toDouble()),
            "token" to JsonPrimitive(token),
        ) + extra
        ).mapValues { it.value.toString() }

    private fun <T> decode(serializer: KSerializer<T>, json: String): T = SyncJson.decodeFromString(serializer, json)

    /**
     * The client waits indefinitely for a connection; give up after a while so an offline action
     * reports an error instead of spinning (a plain exception, so callers treat it as offline).
     */
    private suspend inline fun <T> timed(crossinline block: suspend () -> T): T = withTimeoutOrNull(CALL_TIMEOUT_MS) { block() } ?: run {
        if (!_connected.value) reconnect("call timed out while disconnected")
        throw IOException("Sync server didn't respond")
    }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (error: Exception) {
        throw error.classify() ?: error
    }

    companion object {
        private const val TAG = "SyncTransport"
        private const val CALL_TIMEOUT_MS = 15_000L
        private const val CONNECT_GRACE_MS = 10_000L

        /** Maps the backend's application errors (`unknown_group`, `client_outdated`) to typed ones. */
        fun Throwable.classify(): Exception? {
            val messages = generateSequence(this) { it.cause }.mapNotNull { it.message }.toList()
            return when {
                messages.any { "unknown_group" in it } -> SyncGroupGoneException(this)
                messages.any { "client_outdated" in it } -> SyncUpdateRequiredException(this)
                else -> null
            }
        }
    }
}
