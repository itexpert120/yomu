package com.itexpert120.yomu.feature.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The library's recent searches (device-local, newest first, at most [MAX_RECENT]): a search is
 * remembered once it was submitted or led to a book.
 */
@Singleton
class RecentLibrarySearches @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val searches: Flow<List<String>> = dataStore.data.map { prefs -> prefs[KeyRecent].decode() }

    suspend fun remember(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        dataStore.edit { prefs ->
            val kept = prefs[KeyRecent].decode().filterNot { it.equals(term, ignoreCase = true) }
            prefs[KeyRecent] = (listOf(term) + kept).take(MAX_RECENT).joinToString(SEPARATOR)
        }
    }

    suspend fun forget(query: String) {
        dataStore.edit { prefs ->
            prefs[KeyRecent] = prefs[KeyRecent].decode().filterNot { it == query }.joinToString(SEPARATOR)
        }
    }

    private fun String?.decode(): List<String> = this?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        val KeyRecent = stringPreferencesKey("library_recent_searches")
        const val SEPARATOR = "\n"
        const val MAX_RECENT = 6
    }
}
