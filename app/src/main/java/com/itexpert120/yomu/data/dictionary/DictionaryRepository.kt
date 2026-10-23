package com.itexpert120.yomu.data.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up word definitions from the free, key-less freedictionaryapi.com API. Uses a plain
 * [HttpURLConnection] off the main thread so no networking library is pulled in just for this.
 */
@Singleton
class DictionaryRepository @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Looks up [word] in [language] (ISO 639 code, or "all" for every language). When the word is
     * only an inflection (e.g. "muttered" → "simple past … of mutter"), the base word's entry is
     * returned instead, with the inflection kept as [DictionaryResult.Found.inflectionOf] context.
     */
    suspend fun lookup(word: String, language: String = "en"): DictionaryResult {
        var direct = fetch(word, language)
        // Possessives ("Dantès's") have no entries of their own; retry without the "'s".
        val bare = word.trim().removeSuffix("'s")
        if (direct is DictionaryResult.NotFound && bare.length < word.trim().length && bare.isNotEmpty()) {
            direct = fetch(bare, language)
        }
        val found = direct as? DictionaryResult.Found ?: return direct
        val (lemma, note) = found.response.inflectionTarget() ?: return direct
        val partOfSpeech = found.response.entries.firstOrNull()?.partOfSpeech.orEmpty()
        if (lemma.equals(word.trim(), ignoreCase = true)) return direct
        val base = fetch(lemma, language) as? DictionaryResult.Found ?: return direct
        return base.copy(inflectionOf = InflectionNote(word = word.trim(), description = note, partOfSpeech = partOfSpeech))
    }

    private suspend fun fetch(word: String, language: String): DictionaryResult = withContext(Dispatchers.IO) {
        val term = word.trim()
        if (term.isEmpty()) return@withContext DictionaryResult.NotFound
        val encoded = URLEncoder.encode(term, "UTF-8")
        val connection = runCatching {
            (URL("$ENDPOINT$language/$encoded").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
        }.getOrElse { return@withContext DictionaryResult.Error }

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = runCatching {
                        json.decodeFromString(DictionaryResponse.serializer(), body)
                    }.getOrNull()
                    if (response == null || response.entries.isEmpty()) {
                        DictionaryResult.NotFound
                    } else {
                        DictionaryResult.Found(response)
                    }
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

    /**
     * When every sense is a Wiktionary "form of" sense, returns the base word they all point at and
     * the description without the trailing "of <base>" (e.g. "simple past and past participle").
     */
    private fun DictionaryResponse.inflectionTarget(): Pair<String, String>? {
        val senses = entries.flatMap { it.senses }
        if (senses.isEmpty() || senses.any { sense -> sense.tags.none { it == "form of" } }) return null
        val definition = senses.first().definition.trim().trimEnd('.')
        val target = definition.substringAfterLast(" of ", "").trim()
        if (target.isEmpty() || target.contains(' ')) return null
        return target to definition.substringBeforeLast(" of ").trim()
    }

    private companion object {
        const val ENDPOINT = "https://freedictionaryapi.com/api/v1/entries/"
        const val TIMEOUT_MS = 8_000
    }
}
