package com.itexpert120.yomu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ReaderSettingsEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val global: Flow<ReaderSettings> = dataStore.data.map { prefs ->
        prefs[KeyGlobal]?.let { decode(it) } ?: ReaderSettings()
    }

    /** Whether [bookId] currently carries its own override. */
    fun hasOverride(id: BookId): Flow<Boolean> = dao.observeReaderSettings(id.value).map { it != null }

    /** The settings actually applied to [bookId]: its override if present, else the global default. */
    fun effective(id: BookId): Flow<ReaderSettings> = combine(global, dao.observeReaderSettings(id.value)) { global, override ->
        override?.json?.let { decode(it) } ?: global
    }

    suspend fun setGlobal(settings: ReaderSettings) {
        dataStore.edit { prefs ->
            prefs[KeyGlobal]?.takeIf { decodeOrNull(it) == null }?.let { corrupt ->
                prefs[KeyGlobalBackup] = corrupt
            }
            prefs[KeyGlobal] = json.encodeToString(settings)
        }
    }

    /** Writes a full per-book override (per-book-on-edit behaviour). */
    suspend fun setForBook(id: BookId, settings: ReaderSettings) {
        dao.getReaderSettings(id.value)?.json?.let { existing ->
            check(decodeOrNull(existing) != null) {
                "Per-book reader settings are malformed; original data was preserved"
            }
        }
        dao.upsertReaderSettings(ReaderSettingsEntity(id.value, json.encodeToString(settings)))
    }

    /** Drops the override so the book follows the global default again. */
    suspend fun clearForBook(id: BookId) {
        dao.deleteReaderSettings(id.value)
    }

    /** Clears a removed custom font from both the global default and every per-book override. */
    suspend fun clearCustomFontReferences(family: String) {
        dataStore.edit { prefs ->
            val raw = prefs[KeyGlobal] ?: return@edit
            val current = decodeOrNull(raw) ?: return@edit
            if (current.customFont?.family == family) {
                prefs[KeyGlobal] = json.encodeToString(current.copy(customFont = null))
            }
        }
        dao.getAllReaderSettings().forEach { row ->
            val current = decodeOrNull(row.json) ?: return@forEach
            if (current.customFont?.family == family) {
                dao.upsertReaderSettings(
                    ReaderSettingsEntity(row.bookId, json.encodeToString(current.copy(customFont = null))),
                )
            }
        }
    }

    // region Saved custom themes (app-global)

    private val customThemeSerializer = ListSerializer(CustomReaderTheme.serializer())

    val customThemes: Flow<List<CustomReaderTheme>> = dataStore.data.map { prefs ->
        prefs[KeyCustomThemes]?.let { decodeThemes(it) } ?: emptyList()
    }

    /** Adds a new theme or replaces an existing one with the same id. */
    suspend fun saveCustomTheme(theme: CustomReaderTheme) {
        dataStore.edit { prefs ->
            val current = prefs[KeyCustomThemes]?.let { decodeThemesOrThrow(it) } ?: emptyList()
            val updated = current.filterNot { it.id == theme.id } + theme
            prefs[KeyCustomThemes] = json.encodeToString(customThemeSerializer, updated)
        }
    }

    suspend fun deleteCustomTheme(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[KeyCustomThemes]?.let { decodeThemesOrThrow(it) } ?: emptyList()
            prefs[KeyCustomThemes] =
                json.encodeToString(customThemeSerializer, current.filterNot { it.id == id })
        }
    }

    private fun decodeThemes(raw: String): List<CustomReaderTheme> = runCatching { json.decodeFromString(customThemeSerializer, raw) }.getOrDefault(emptyList())

    private fun decodeThemesOrThrow(raw: String): List<CustomReaderTheme> = runCatching { json.decodeFromString(customThemeSerializer, raw) }
        .getOrElse { error("Saved reader themes are malformed; original data was preserved") }

    // endregion

    private fun decode(raw: String): ReaderSettings = decodeOrNull(raw) ?: ReaderSettings()

    private fun decodeOrNull(raw: String): ReaderSettings? = runCatching { json.decodeFromString<ReaderSettings>(raw) }.getOrNull()

    private companion object {
        val KeyGlobal = stringPreferencesKey("reader_settings_global")
        val KeyGlobalBackup = stringPreferencesKey("reader_settings_global_corrupt_backup")
        val KeyCustomThemes = stringPreferencesKey("reader_custom_themes")
    }
}
