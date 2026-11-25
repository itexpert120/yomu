package com.itexpert120.yomu.data.encyclopedia

import com.itexpert120.yomu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** A Wikipedia article's summary card: what the look-up sheet's Wikipedia tab shows. */
data class WikipediaSummary(
    val title: String,
    val description: String?,
    val extract: String,
    val thumbnailUrl: String?,
    // The article on the web, for "Read on Wikipedia".
    val pageUrl: String,
    // The wiki's language code, e.g. "en", "fr".
    val language: String,
    // The title names several things ("may refer to"); the extract is then a list, not an answer.
    val disambiguation: Boolean = false,
    // The thumbnail's image bytes, fetched along with the summary (the app has no image network stack).
    val thumbnail: WikipediaThumbnail? = null,
)

/** Encoded image bytes; identity equality, so state holding one stays cheap to compare. */
class WikipediaThumbnail(val bytes: ByteArray)

sealed interface WikipediaResult {
    data class Found(val summary: WikipediaSummary) : WikipediaResult
    data object NotFound : WikipediaResult
    data object Error : WikipediaResult
}

/**
 * Fetches article summaries from Wikipedia's REST API (`/api/rest_v1/page/summary/{title}`) for a
 * selected word or short phrase. Called only when the person opens the Wikipedia tab. The request
 * carries the title and a generic `OpenReader/<version> (Android)` user agent, nothing else.
 */
@Singleton
class WikipediaRepository @Inject constructor() {

    suspend fun summary(text: String, language: String?): WikipediaResult = withContext(Dispatchers.IO) {
        val title = WikipediaRequest.title(text) ?: return@withContext WikipediaResult.NotFound
        val wiki = WikipediaRequest.wikiLanguage(language)
        val connection = runCatching {
            (URL(WikipediaRequest.summaryUrl(title, wiki)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
        }.getOrElse { return@withContext WikipediaResult.Error }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    when (val parsed = WikipediaParser.parse(body, wiki)) {
                        is WikipediaResult.Found -> WikipediaResult.Found(parsed.summary.copy(thumbnail = parsed.summary.thumbnailUrl?.let(::thumbnail)))
                        else -> parsed
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> WikipediaResult.NotFound
                else -> {
                    runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }
                    WikipediaResult.Error
                }
            }
        } catch (_: Exception) {
            WikipediaResult.Error
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    // Only from Wikimedia's own image host, and only a thumbnail-sized file; any failure just leaves it out.
    private fun thumbnail(url: String): WikipediaThumbnail? = runCatching {
        val parsed = URL(url)
        if (parsed.protocol != "https" || parsed.host != THUMBNAIL_HOST) return null
        val connection = parsed.openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (out.size() <= MAX_THUMBNAIL_BYTES) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
            out.toByteArray().takeIf { it.isNotEmpty() && it.size <= MAX_THUMBNAIL_BYTES }?.let(::WikipediaThumbnail)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val THUMBNAIL_HOST = "upload.wikimedia.org"
        const val MAX_THUMBNAIL_BYTES = 1_000_000
        const val TIMEOUT_MS = 8_000
        val USER_AGENT = "OpenReader/${BuildConfig.VERSION_NAME} (Android)"
    }
}

/** How a selection becomes a summary request. Pure, for tests. */
internal object WikipediaRequest {
    private val LANGUAGE = Regex("^[a-z]{2,3}$")

    /** The wiki for a BCP 47 tag ("fr-CA" → "fr"); English when unknown or malformed. */
    fun wikiLanguage(tag: String?): String {
        val primary = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
        return primary.takeIf { LANGUAGE.matches(it) } ?: "en"
    }

    /** The selection as an article title: surrounding punctuation and quotes trimmed, spaces collapsed. */
    fun title(text: String): String? = text
        .replace(Regex("\\s+"), " ")
        .trim { !it.isLetterOrDigit() }
        .takeIf { it.isNotEmpty() }

    fun summaryUrl(title: String, language: String): String {
        // Titles use underscores for spaces; anything else is percent-encoded (URLEncoder writes "+").
        val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
        return "https://$language.wikipedia.org/api/rest_v1/page/summary/$encoded"
    }
}

/** Parses a summary response; anything without an extract counts as not found. */
internal object WikipediaParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, language: String): WikipediaResult {
        val response = runCatching { json.decodeFromString(SummaryResponse.serializer(), body) }.getOrNull()
            ?: return WikipediaResult.Error
        val extract = response.extract.trim()
        if (extract.isEmpty() || response.type == "no-extract") return WikipediaResult.NotFound
        val page = response.contentUrls?.mobile?.page?.takeIf { it.startsWith("https://") }
            ?: response.contentUrls?.desktop?.page?.takeIf { it.startsWith("https://") }
            ?: "https://$language.wikipedia.org/wiki/${response.titles?.canonical ?: response.title.replace(' ', '_')}"
        return WikipediaResult.Found(
            WikipediaSummary(
                title = response.titles?.normalized?.takeIf { it.isNotBlank() } ?: response.title,
                description = response.description?.trim()?.takeIf { it.isNotEmpty() },
                extract = extract,
                thumbnailUrl = response.thumbnail?.source?.takeIf { it.startsWith("https://") },
                pageUrl = page,
                language = response.lang?.takeIf { it.isNotBlank() } ?: language,
                disambiguation = response.type == "disambiguation",
            ),
        )
    }

    @Serializable
    private data class SummaryResponse(
        val type: String = "standard",
        val title: String = "",
        val titles: Titles? = null,
        val description: String? = null,
        val extract: String = "",
        val thumbnail: Thumbnail? = null,
        @SerialName("content_urls") val contentUrls: ContentUrls? = null,
        val lang: String? = null,
    )

    @Serializable
    private data class Titles(val canonical: String? = null, val normalized: String? = null)

    @Serializable
    private data class Thumbnail(val source: String? = null)

    @Serializable
    private data class ContentUrls(val desktop: PageUrl? = null, val mobile: PageUrl? = null)

    @Serializable
    private data class PageUrl(val page: String? = null)
}
