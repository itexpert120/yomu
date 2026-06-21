package com.itexpert120.yomu.data.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up word definitions from the free, key-less Free Dictionary API. Uses a plain
 * [HttpURLConnection] off the main thread so no networking library is pulled in just for this.
 */
@Singleton
class DictionaryRepository @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(word: String): DictionaryResult = withContext(Dispatchers.IO) {
        val term = word.trim()
        if (term.isEmpty()) return@withContext DictionaryResult.NotFound
        val encoded = URLEncoder.encode(term, "UTF-8")
        val connection = runCatching {
            (URL("$ENDPOINT$encoded").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
        }.getOrElse { return@withContext DictionaryResult.Error }

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val entries = runCatching {
                        json.decodeFromString(ListSerializer(DictionaryEntry.serializer()), body)
                    }.getOrNull()
                    if (entries.isNullOrEmpty()) DictionaryResult.NotFound else DictionaryResult.Found(
                        entries
                    )
                }

                HttpURLConnection.HTTP_NOT_FOUND -> DictionaryResult.NotFound
                else -> {
                    // Drain the error stream so the socket can be reused / released cleanly.
                    runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }
                    DictionaryResult.Error
                }
            }
        } catch (e: Exception) {
            DictionaryResult.Error
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.dictionaryapi.dev/api/v2/entries/en/"
        const val TIMEOUT_MS = 8_000
    }
}
