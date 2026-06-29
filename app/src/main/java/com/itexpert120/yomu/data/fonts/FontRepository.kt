package com.itexpert120.yomu.data.fonts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.model.CuratedFont
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.storage.FileStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs custom reading fonts from Google Fonts. Fetches the family's CSS from the key-less Google
 * Fonts API, downloads the (small) latin-subset woff2 for the regular + italic faces into app-private
 * storage, and records them in a DataStore registry. Plain [HttpURLConnection] off the main thread,
 * matching the dictionary's no-extra-networking-library approach.
 *
 * The downloaded files are embedded as @font-face data URLs by the reader engine (Readium can only
 * serve fonts from bundled assets, not app-private files), so the engine never needs the file served.
 */
@Singleton
class FontRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val fileStorage: FileStorage,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CustomFontRef.serializer())

    @Volatile
    private var catalogCache: List<CuratedFont>? = null

    /**
     * The full Google Fonts catalog (family + category), bundled as assets/google_fonts.txt so search
     * works offline. Parsed once and cached; only the actual font download needs the network.
     */
    suspend fun catalog(): List<CuratedFont> {
        catalogCache?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching {
                context.assets.open("google_fonts.txt").bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        val parts = line.split('\t')
                        val family = parts.getOrNull(0)?.trim().orEmpty()
                        if (family.isEmpty()) {
                            null
                        } else {
                            CuratedFont(family, parts.getOrNull(1)?.trim().orEmpty())
                        }
                    }.toList()
                }
            }.getOrDefault(emptyList())
            catalogCache = parsed
            parsed
        }
    }

    /** Custom fonts the user has installed, newest last. */
    val installed: Flow<List<CustomFontRef>> = dataStore.data.map { prefs ->
        prefs[KeyInstalled]?.let { decode(it) } ?: emptyList()
    }

    /**
     * Downloads [family] from Google Fonts and registers it. Idempotent: re-installing replaces the
     * existing entry. Returns the installed font, or failure if the family couldn't be fetched.
     */
    suspend fun install(family: String): Result<CustomFontRef> = withContext(Dispatchers.IO) {
        runCatching {
            val css = fetchCss(family)
            val faces = parseLatinFaces(css)
            val regularUrl = faces[FontStyle.NORMAL]
                ?: faces.values.firstOrNull()
                ?: error("No usable font face for \"$family\"")
            val slug = family.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val regularPath = fileStorage.saveFont("$slug-regular.woff2", httpGetBytes(regularUrl))
            val italicPath = faces[FontStyle.ITALIC]?.let { url ->
                runCatching { fileStorage.saveFont("$slug-italic.woff2", httpGetBytes(url)) }.getOrNull()
            }
            val ref = CustomFontRef(family = family, regularPath = regularPath, italicPath = italicPath)
            putInstalled(ref)
            ref
        }
    }

    /** Removes an installed custom font and deletes its files. */
    suspend fun remove(family: String) {
        dataStore.edit { prefs ->
            val current = prefs[KeyInstalled]?.let { decode(it) } ?: emptyList()
            current.firstOrNull { it.family == family }?.let {
                fileStorage.deleteFont(it.regularPath)
                it.italicPath?.let { p -> fileStorage.deleteFont(p) }
            }
            prefs[KeyInstalled] = json.encodeToString(serializer, current.filterNot { it.family == family })
        }
    }

    private suspend fun putInstalled(ref: CustomFontRef) {
        dataStore.edit { prefs ->
            val current = prefs[KeyInstalled]?.let { decode(it) } ?: emptyList()
            prefs[KeyInstalled] =
                json.encodeToString(serializer, current.filterNot { it.family == ref.family } + ref)
        }
    }

    private fun fetchCss(family: String): String {
        val encoded = URLEncoder.encode(family, "UTF-8")
        val url = "$CSS_ENDPOINT?family=$encoded:ital@0;1&display=swap"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // A desktop browser UA so Google serves compact woff2 (vs legacy ttf for unknown agents).
            setRequestProperty("User-Agent", DESKTOP_UA)
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }
                error("Google Fonts returned ${connection.responseCode} for \"$family\"")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun httpGetBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", DESKTOP_UA)
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Font download failed (${connection.responseCode})")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private enum class FontStyle { NORMAL, ITALIC }

    /**
     * Picks the latin-subset woff2 URL for each style from a Google Fonts css2 response. Each style
     * has several subset blocks (latin, latin-ext, cyrillic, …); the latin one is identified by its
     * unicode-range covering basic latin (U+0000-00FF) and is all we need for the reading UI.
     */
    private fun parseLatinFaces(css: String): Map<FontStyle, String> {
        val result = mutableMapOf<FontStyle, String>()
        val latinStyles = mutableSetOf<FontStyle>()
        FONT_FACE.findAll(css).forEach { match ->
            val block = match.groupValues[1]
            val style = if (STYLE_ITALIC.containsMatchIn(block)) FontStyle.ITALIC else FontStyle.NORMAL
            val src = SRC_URL.find(block)?.groupValues?.get(1) ?: return@forEach
            val isLatin = UNICODE_RANGE.find(block)?.groupValues?.get(1)?.contains("U+0000") == true
            // Latin block wins for a style; otherwise keep the first block seen as a fallback.
            when {
                isLatin -> {
                    result[style] = src
                    latinStyles += style
                }
                style !in latinStyles && style !in result -> result[style] = src
            }
        }
        return result
    }

    private fun decode(raw: String): List<CustomFontRef> = runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    private companion object {
        const val CSS_ENDPOINT = "https://fonts.googleapis.com/css2"
        const val TIMEOUT_MS = 12_000
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val FONT_FACE = Regex("@font-face\\s*\\{([^}]*)\\}")
        val STYLE_ITALIC = Regex("font-style:\\s*italic")
        val UNICODE_RANGE = Regex("unicode-range:\\s*([^;]*)")
        val SRC_URL = Regex("src:\\s*url\\((https://[^)]+)\\)")

        val KeyInstalled = stringPreferencesKey("installed_custom_fonts")
    }
}
