package com.itexpert120.yomu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists library view preferences (sort/view/columns/cover fit) via DataStore. */
@Singleton
class LibraryPrefsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<LibraryPreferences> = dataStore.data.map { prefs ->
        val legacyColumns = prefs[KeyColumns]?.coerceColumns()
        LibraryPreferences(
            sortMode = prefs[KeySort].toEnum(SortMode.entries, SortMode.Recent),
            viewMode = prefs[KeyView].toLibraryViewMode(),
            portraitGridColumns = resolveLibraryColumns(
                value = prefs[KeyPortraitColumns],
                legacy = legacyColumns,
                fallback = LibraryPreferences().portraitGridColumns,
            ),
            landscapeGridColumns = resolveLibraryColumns(
                value = prefs[KeyLandscapeColumns],
                legacy = legacyColumns,
                fallback = LibraryPreferences().landscapeGridColumns,
            ),
            coverCrop = prefs[KeyCoverCrop] ?: true,
            lastTab = prefs[KeyLastTab] ?: LibraryPreferences().lastTab,
            tabOrder = prefs[KeyTabOrder]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            hiddenTabs = prefs[KeyHiddenTabs]?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty(),
        )
    }

    suspend fun setSortMode(mode: SortMode) = edit(KeySort, mode.name)
    suspend fun setViewMode(mode: LibraryViewMode) = edit(KeyView, mode.name)

    suspend fun setPortraitGridColumns(columns: Int) {
        dataStore.edit { it[KeyPortraitColumns] = columns.coerceColumns() }
    }

    suspend fun setLandscapeGridColumns(columns: Int) {
        dataStore.edit { it[KeyLandscapeColumns] = columns.coerceColumns() }
    }

    /** Compatibility helper for callers that still want to apply one density to both orientations. */
    suspend fun setGridColumns(columns: Int) {
        val value = columns.coerceColumns()
        dataStore.edit {
            it[KeyPortraitColumns] = value
            it[KeyLandscapeColumns] = value
        }
    }

    suspend fun setLastTab(key: String) = edit(KeyLastTab, key)

    suspend fun setTabOrder(keys: List<String>) = edit(KeyTabOrder, keys.joinToString("\n"))

    suspend fun setTabHidden(key: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KeyHiddenTabs]?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            prefs[KeyHiddenTabs] = (if (hidden) current + key else current - key).joinToString("\n")
        }
    }

    suspend fun setCoverCrop(crop: Boolean) {
        dataStore.edit { it[KeyCoverCrop] = crop }
    }

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val KeySort = stringPreferencesKey("library_sort")
        val KeyView = stringPreferencesKey("library_view")
        val KeyColumns = intPreferencesKey("library_columns")
        val KeyPortraitColumns = intPreferencesKey("library_columns_portrait")
        val KeyLandscapeColumns = intPreferencesKey("library_columns_landscape")
        val KeyCoverCrop = booleanPreferencesKey("library_cover_crop")
        val KeyLastTab = stringPreferencesKey("library_last_tab")
        val KeyTabOrder = stringPreferencesKey("library_tab_order")
        val KeyHiddenTabs = stringPreferencesKey("library_hidden_tabs")
    }
}

private fun <E : Enum<E>> String?.toEnum(entries: List<E>, default: E): E = this?.let { name -> entries.firstOrNull { it.name == name } } ?: default

internal fun String?.toLibraryViewMode(): LibraryViewMode = when (this) {
    // The old persisted Grid value is intentionally treated as the richer default presentation.
    "Grid" -> LibraryViewMode.ComfortableGrid
    else -> toEnum(LibraryViewMode.entries, LibraryViewMode.ComfortableGrid)
}

/** Keep Automatic (0) as-is; clamp any explicit count into the supported range. */
internal fun Int.coerceColumns(): Int = if (this <= LibraryPreferences.AUTO_COLUMNS) {
    LibraryPreferences.AUTO_COLUMNS
} else {
    coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS)
}

internal fun resolveLibraryColumns(value: Int?, legacy: Int?, fallback: Int): Int = (value ?: legacy ?: fallback).coerceColumns()
