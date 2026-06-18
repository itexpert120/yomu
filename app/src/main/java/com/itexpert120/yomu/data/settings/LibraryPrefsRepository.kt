package com.itexpert120.yomu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists library view preferences (sort/group/view/columns/cover fit) via DataStore. */
class LibraryPrefsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<LibraryPreferences> = dataStore.data.map { prefs ->
        LibraryPreferences(
            sortMode = prefs[KeySort].toEnum(SortMode.entries, SortMode.Recent),
            groupMode = prefs[KeyGroup].toEnum(GroupMode.entries, GroupMode.None),
            viewMode = prefs[KeyView].toEnum(LibraryViewMode.entries, LibraryViewMode.Grid),
            gridColumns = (prefs[KeyColumns] ?: LibraryPreferences().gridColumns)
                .coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS),
            coverCrop = prefs[KeyCoverCrop] ?: true,
        )
    }

    suspend fun setSortMode(mode: SortMode) = edit(KeySort, mode.name)
    suspend fun setGroupMode(mode: GroupMode) = edit(KeyGroup, mode.name)
    suspend fun setViewMode(mode: LibraryViewMode) = edit(KeyView, mode.name)

    suspend fun setGridColumns(columns: Int) {
        val clamped = columns.coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS)
        dataStore.edit { it[KeyColumns] = clamped }
    }

    suspend fun setCoverCrop(crop: Boolean) {
        dataStore.edit { it[KeyCoverCrop] = crop }
    }

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val KeySort = stringPreferencesKey("library_sort")
        val KeyGroup = stringPreferencesKey("library_group")
        val KeyView = stringPreferencesKey("library_view")
        val KeyColumns = intPreferencesKey("library_columns")
        val KeyCoverCrop = booleanPreferencesKey("library_cover_crop")
    }
}

private fun <E : Enum<E>> String?.toEnum(entries: List<E>, default: E): E =
    this?.let { name -> entries.firstOrNull { it.name == name } } ?: default
