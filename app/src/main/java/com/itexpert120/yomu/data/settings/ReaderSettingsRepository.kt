package com.itexpert120.yomu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ReaderSettingsEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reader preferences with a global default (DataStore) and optional per-book overrides (Room). When
 * a book has an override it fully supersedes the global default; the resolver merges the two.
 */
@Singleton
class ReaderSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dao: BookDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val global: Flow<ReaderSettings> = dataStore.data.map { prefs ->
        prefs[KeyGlobal]?.let { decode(it) } ?: ReaderSettings()
    }

    /** Whether [bookId] currently carries its own override. */
    fun hasOverride(id: BookId): Flow<Boolean> =
        dao.observeReaderSettings(id.value).map { it != null }

    /** The settings actually applied to [bookId]: its override if present, else the global default. */
    fun effective(id: BookId): Flow<ReaderSettings> =
        combine(global, dao.observeReaderSettings(id.value)) { global, override ->
            override?.json?.let { decode(it) } ?: global
        }

    suspend fun setGlobal(settings: ReaderSettings) {
        dataStore.edit { it[KeyGlobal] = json.encodeToString(settings) }
    }

    /** Writes a full per-book override (per-book-on-edit behaviour). */
    suspend fun setForBook(id: BookId, settings: ReaderSettings) {
        dao.upsertReaderSettings(ReaderSettingsEntity(id.value, json.encodeToString(settings)))
    }

    /** Drops the override so the book follows the global default again. */
    suspend fun clearForBook(id: BookId) {
        dao.deleteReaderSettings(id.value)
    }

    private fun decode(raw: String): ReaderSettings =
        runCatching { json.decodeFromString<ReaderSettings>(raw) }.getOrDefault(ReaderSettings())

    private companion object {
        val KeyGlobal = stringPreferencesKey("reader_settings_global")
    }
}
