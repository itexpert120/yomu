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
import javax.inject.Inject
import javax.inject.Singleton

/** Persists library view preferences (sort/group/view/columns/cover fit) via DataStore. */
@Singleton
class LibraryPrefsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<LibraryPreferences> = dataStore.data.map { prefs ->
        LibraryPreferences(
            sortMode = prefs[KeySort].toEnum(SortMode.entries, SortMode.Recent),
            groupMode = prefs[KeyGroup].toEnum(GroupMode.entries, GroupMode.None),
            viewMode = prefs[KeyView].toEnum(LibraryViewMode.entries, LibraryViewMode.Grid),
            gridColumns = (prefs[KeyColumns] ?: LibraryPreferences().gridColumns)
                .coerceColumns(),
            coverCrop = prefs[KeyCoverCrop] ?: true,
        )
    }

    suspend fun setSortMode(mode: SortMode) = edit(KeySort, mode.name)
    suspend fun setGroupMode(mode: GroupMode) = edit(KeyGroup, mode.name)
    suspend fun setViewMode(mode: LibraryViewMode) = edit(KeyView, mode.name)

    suspend fun setGridColumns(columns: Int) {
        dataStore.edit { it[KeyColumns] = columns.coerceColumns() }
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

private fun <E : Enum<E>> String?.toEnum(entries: List<E>, default: E): E = this?.let { name -> entries.firstOrNull { it.name == name } } ?: default

/** Keep Auto (0) as-is; clamp any explicit count into the supported range. */
private fun Int.coerceColumns(): Int = if (this <= LibraryPreferences.AUTO_COLUMNS) {
    LibraryPreferences.AUTO_COLUMNS
} else {
    coerceIn(LibraryPreferences.MIN_COLUMNS, LibraryPreferences.MAX_COLUMNS)
}
