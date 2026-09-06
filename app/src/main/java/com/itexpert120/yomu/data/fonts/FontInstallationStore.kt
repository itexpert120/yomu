package com.itexpert120.yomu.data.fonts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.model.CustomFontRef
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Registry publication and retirement are durable; old files remain until references are repaired. */
internal class FontInstallationStore(
    private val store: DataStore<Preferences>,
    private val replaceReferences: suspend (String, CustomFontRef?) -> Unit,
    private val deleteFile: suspend (String) -> Unit,
) {
    @Serializable private data class Retirement(val old: CustomFontRef, val replacement: CustomFontRef?)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val installedKey = stringPreferencesKey("installed_custom_fonts")
    private val pendingKey = stringPreferencesKey("pending_font_retirements")
    private fun installed(p: Preferences): List<CustomFontRef> = p[installedKey]?.let { json.decodeFromString(it) } ?: emptyList()
    private fun pending(p: Preferences): List<Retirement> = p[pendingKey]?.let { json.decodeFromString(it) } ?: emptyList()

    val installed = store.data.map { p ->
        (installed(p) + pending(p).map { it.old }).distinctBy { it.family }
    }

    suspend fun publish(ref: CustomFontRef) = mutex.withLock {
        withContext(NonCancellable) {
            recoverPending()
            store.edit { p ->
                val current = installed(p)
                val old = current.firstOrNull { it.family == ref.family }
                p[installedKey] = json.encodeToString(current.filterNot { it.family == ref.family } + ref)
                if (old != null) p[pendingKey] = json.encodeToString(pending(p) + Retirement(old, ref))
            }
            recoverPending()
        }
    }

    suspend fun owns(path: String): Boolean {
        val p = store.data.first()
        return (installed(p) + pending(p).map { it.old }).any { it.regularPath == path || it.italicPath == path }
    }

    suspend fun remove(family: String) = mutex.withLock {
        withContext(NonCancellable) {
            recoverPending()
            store.edit { p ->
                val current = installed(p)
                val old = current.firstOrNull { it.family == family } ?: return@edit
                p[pendingKey] = json.encodeToString(pending(p) + Retirement(old, null))
                p[installedKey] = json.encodeToString(current.filterNot { it.family == family })
            }
            recoverPending()
        }
    }

    suspend fun recover() = mutex.withLock { recoverPending() }

    private suspend fun recoverPending() {
        pending(store.data.first()).forEach { retirement ->
            replaceReferences(retirement.old.family, retirement.replacement)
            if (retirement.old.regularPath != retirement.replacement?.regularPath) deleteFile(retirement.old.regularPath)
            retirement.old.italicPath?.takeIf { it != retirement.replacement?.italicPath }?.let { deleteFile(it) }
            store.edit { p -> p[pendingKey] = json.encodeToString(pending(p).filterNot { it == retirement }) }
        }
    }
}
