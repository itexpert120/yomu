package com.itexpert120.yomu.data.reader.foliate

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderLocator
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSelection
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.diagnostics.CrashLog
import com.itexpert120.yomu.data.reader.ReadingPosition
import com.itexpert120.yomu.data.reader.readium.ReadiumReaderEngine
import com.itexpert120.yomu.reader.epub.EpubBook
import com.itexpert120.yomu.reader.web.FoliateReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Reads EPUBs with foliate-js in a WebView ([FoliateReader]), turning pages natively.
 *
 * Positions cross the boundary in the same locator JSON the Readium engine writes (href +
 * progression, plus foliate's CFI under `locations.cfi`), so stored positions, bookmarks, TOC
 * targets and chapter read state keep working with either engine. The TOC and its ids still come
 * from Readium's parser (import-time cache, or [ReadiumReaderEngine.publicationCache] on a miss), so
 * chapter read marks stay keyed the same.
 *
 * Not yet ported (no-ops for now): highlights, in-book search, the text-selection menu, footnote
 * popups, and the scroll-mode chapter rubberband.
 */
@Singleton
class FoliateReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readium: ReadiumReaderEngine,
    private val crashLog: CrashLog,
) : ReaderEngine {

    override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? = withContext(Dispatchers.IO) {
        ReaderOpenTrace.mark("reader.foliate-open")
        val file = File(request.filePath).takeIf { it.isFile } ?: return@withContext null
        val cache = request.publicationCache?.takeIf { it.toc.isNotEmpty() }
            ?: readium.publicationCache(request.filePath)
            ?: ReaderPublicationCache()
        val book = runCatching { FoliateBook.read(file, cache) }
            .onFailure { Log.w(TAG, "Couldn't read ${file.name}", it) }
            .getOrNull() ?: return@withContext null
        ReaderOpenTrace.mark("reader.session-assembled")
        val session = FoliateReaderSession(
            book = book,
            tableOfContents = cache.toc,
            initialLocatorJson = request.initialLocatorJson,
            initialSettings = request.initialSettings,
            crashLog = crashLog,
        )
        ReaderOpenResult(session, cache)
    }

    // WebView's one-time start-up is the same whichever engine draws the page.
    override fun warmUp() = readium.warmUp()

    override suspend fun tableOfContents(filePath: String): List<ReaderTocItem>? = readium.tableOfContents(filePath)

    private companion object {
        const val TAG = "FoliateEngine"
    }
}

/** What the session needs to know about a book, read straight from the EPUB's package. */
internal class FoliateBook(
    val file: File,
    val title: String?,
    /** Spine items' paths inside the EPUB, in foliate's section order. */
    val paths: List<String>,
    val mediaTypes: List<String>,
    /** The same items as locator hrefs, written exactly as the Readium engine writes them. */
    val readingOrder: List<String>,
    val weights: List<Int>,
) {
    private val indexByPath: Map<String, Int> = HashMap<String, Int>().apply {
        paths.forEachIndexed { index, path -> putIfAbsent(path, index) }
    }

    /** The section a locator href points into, or -1. */
    fun indexOfHref(href: String?): Int {
        val path = href?.substringBefore('#')?.substringBefore('?')?.removePrefix("/") ?: return -1
        return indexByPath[path] ?: indexByPath[Uri.decode(path)] ?: -1
    }

    companion object {
        fun read(file: File, cache: ReaderPublicationCache): FoliateBook {
            val (title, spine) = EpubBook.open(file).use { it.title to it.spine }
            // Prefer the exact href strings Readium produced (TOC targets and its resource keys) so
            // positions written by either engine compare equal; encode the rest the same way.
            val known = HashMap<String, String>()
            cache.toc.forEach { known.putIfAbsent(Uri.decode(it.resourceHref.removePrefix("/")), it.resourceHref) }
            cache.resourceWeights.keys.forEach { known.putIfAbsent(Uri.decode(it.removePrefix("/")), it) }
            val weights = ZipFile(file).use { zip ->
                spine.map { item ->
                    val size = zip.getEntry(item.path)?.size ?: 0L
                    ceil(size.coerceAtLeast(1L) / 1024.0).toInt().coerceAtLeast(1)
                }
            }
            return FoliateBook(
                file = file,
                title = title,
                paths = spine.map { it.path },
                mediaTypes = spine.map { it.mediaType },
                readingOrder = spine.map { known[it.path] ?: Uri.encode(it.path, "/") },
                weights = weights,
            )
        }
    }
}

private class FoliateReaderSession(
    private val book: FoliateBook,
    override val tableOfContents: List<ReaderTocItem>,
    initialLocatorJson: String?,
    initialSettings: ReaderSettings,
    private val crashLog: CrashLog,
) : ReaderSession {

    override val title: String = book.title ?: "Reading"

    private val _currentLocator = MutableStateFlow<ReaderLocator?>(null)
    override val currentLocator: StateFlow<ReaderLocator?> = _currentLocator.asStateFlow()

    private val _renderState = MutableStateFlow<ReaderRenderState>(ReaderRenderState.Opening)
    override val renderState: StateFlow<ReaderRenderState> = _renderState.asStateFlow()

    private val _centerTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val centerTaps: SharedFlow<Unit> = _centerTaps.asSharedFlow()

    // Not ported yet: never emit.
    override val selections: SharedFlow<ReaderSelection> = MutableSharedFlow<ReaderSelection>().asSharedFlow()
    override val footnotes: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()
    override val highlightTaps: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()

    private var settings = initialSettings
    private var reader: FoliateReader? = null
    private var readerActivity: Activity? = null
    private var hostContainer: FragmentContainerView? = null
    private var hostFragmentManager: FragmentManager? = null
    private var immersive = false
    private var closed = false

    // Where to (re)open the page: the start position, then wherever the reader last was.
    private var target: JSONObject = initialLocatorJson?.let(::targetFor) ?: JSONObject().put("index", 0).put("fraction", 0)

    private val position = ReadingPosition(
        sections = tableOfContents.mapNotNull { item ->
            val json = item.locatorJson ?: return@mapNotNull null
            val locator = runCatching { JSONObject(json) }.getOrNull() ?: return@mapNotNull null
            val index = book.indexOfHref(locator.optString("href"))
            if (index < 0) return@mapNotNull null
            val progression = locator.optJSONObject("locations")?.optDouble("progression")?.takeUnless { it.isNaN() }
            ReadingPosition.Section(
                id = item.id,
                title = item.title,
                resourceIndex = index,
                startProgression = progression ?: 0.0,
                target = json,
            )
        },
        readingOrder = book.readingOrder,
        resourceWeights = book.weights,
        layout = initialSettings.layout,
        initialHref = target.optInt("index", -1).let { book.readingOrder.getOrNull(it) },
        initialProgression = target.optDouble("fraction").takeUnless { it.isNaN() },
    )

    // region Hosting

    override fun host(container: FragmentContainerView, fragmentManager: FragmentManager) {
        hostContainer = container
        hostFragmentManager = fragmentManager
        container.doOnAttach {
            if (closed || hostContainer !== container) return@doOnAttach
            val activity = container.context.findActivity() ?: return@doOnAttach
            val current = reader?.takeIf { readerActivity === activity } ?: createReader(activity)
            FoliateHostFragment.pendingView = current.view
            val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
            fragmentManager.findFragmentByTag(HOST_TAG)?.let(transaction::remove)
            transaction.add(container.id, FoliateHostFragment::class.java, null, HOST_TAG).commitAllowingStateLoss()
        }
    }

    override fun setImmersive(immersive: Boolean) {
        if (this.immersive == immersive) return
        this.immersive = immersive
        reader?.view?.let(ViewCompat::requestApplyInsets)
    }

    override fun unhost() {
        val fragmentManager = hostFragmentManager ?: return
        fragmentManager.findFragmentByTag(HOST_TAG)?.let {
            fragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
        }
        hostContainer = null
        hostFragmentManager = null
    }

    private fun createReader(activity: Activity): FoliateReader {
        reader?.close()
        _renderState.value = ReaderRenderState.Opening
        ReaderOpenTrace.mark("reader.foliate-webview")
        val created = FoliateReader(activity, book.file, onEvent = ::onPageEvent, onRendererGone = ::onRendererGone)
        created.userFonts = userFonts(settings)
        created.pageTurns = settings.layout == ReaderLayout.Paged
        // Not immersive, the page sits between the system bars, as Readium's did.
        ViewCompat.setOnApplyWindowInsetsListener(created.view) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (immersive) view.setPadding(0, 0, 0, 0) else view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        created.open(JSONObject().put("start", target).put("settings", settingsJson(settings)).toString())
        reader = created
        readerActivity = activity
        return created
    }

    private fun onRendererGone() {
        if (closed) return
        crashLog.note("Reader: WebView renderer was killed; rebuilt the page view")
        reader?.close()
        reader = null
        val container = hostContainer
        val fragmentManager = hostFragmentManager
        if (container != null && fragmentManager != null) host(container, fragmentManager)
    }

    // endregion

    // region Page events

    private fun onPageEvent(name: String, json: String) {
        when (name) {
            "first-page" -> {
                ReaderOpenTrace.mark("reader.first-page")
                _renderState.value = ReaderRenderState.Ready(_currentLocator.value?.href)
            }
            "relocate" -> onRelocate(JSONObject(json))
            "center-tap" -> _centerTaps.tryEmit(Unit)
            "error" -> Log.w(TAG, "page: $json")
        }
    }

    private fun onRelocate(event: JSONObject) {
        val index = event.optInt("index", -1)
        val href = book.readingOrder.getOrNull(index) ?: return
        val fraction = event.optDouble("fraction", 0.0).takeUnless { it.isNaN() } ?: 0.0
        val total = event.optDouble("total").takeUnless { it.isNaN() }
        val cfi = event.optString("cfi").takeIf { it.isNotEmpty() && it != "null" }
        val title = event.optString("title").takeIf { it.isNotEmpty() && it != "null" }
        if (event.optBoolean("scrolled")) {
            position.onScrollMeasured(href, fraction)
        } else {
            position.onPage(href, event.optInt("page"), event.optInt("pages"))
        }
        val snapshot = position.onLocator(href = href, progression = fraction, totalProgression = total, title = title)
        val locations = JSONObject().put("progression", fraction)
        total?.let { locations.put("totalProgression", it) }
        cfi?.let { locations.put("cfi", it) }
        val locator = JSONObject()
            .put("href", href)
            .put("type", book.mediaTypes[index])
            .apply { (snapshot.chapterTitle ?: title)?.let { put("title", it) } }
            .put("locations", locations)
        target = JSONObject().put("index", index).apply { if (cfi != null) put("cfi", cfi) else put("fraction", fraction) }
        val readerLocator = snapshot.toReaderLocator(locator.toString())
        if (readerLocator != _currentLocator.value) _currentLocator.value = readerLocator
    }

    // endregion

    // region Navigation

    override fun nextChapter() = goToSection(+1)

    override fun previousChapter() = goToSection(-1)

    private fun goToSection(delta: Int) {
        val locatorJson = position.sectionTarget(delta)
        if (locatorJson != null) return goToLocator(locatorJson)
        // No logical section that way: fall back to the adjacent spine item.
        val index = target.optInt("index", -1) + delta
        if (index in book.paths.indices) go(JSONObject().put("index", index).put("fraction", 0))
    }

    override fun goToLocator(locatorJson: String) {
        targetFor(locatorJson)?.let(::go)
    }

    private fun go(next: JSONObject) {
        target = next
        reader?.goTo(next.toString())
    }

    /** A foliate target for a stored locator: its CFI when it has one, else href + fragment/progression. */
    private fun targetFor(locatorJson: String): JSONObject? {
        val locator = runCatching { JSONObject(locatorJson) }.getOrNull() ?: return null
        val index = book.indexOfHref(locator.optString("href"))
        if (index < 0) return null
        val locations = locator.optJSONObject("locations")
        val cfi = locations?.optString("cfi")?.takeIf { it.isNotEmpty() }
        val fragment = locations?.optJSONArray("fragments")?.optString(0)?.takeIf { it.isNotEmpty() }
        val progression = locations?.optDouble("progression")?.takeUnless { it.isNaN() }
        return JSONObject().put("index", index).apply {
            when {
                cfi != null -> put("cfi", cfi)
                fragment != null -> put("href", "${book.paths[index]}#$fragment")
                else -> put("fraction", progression ?: 0.0)
            }
        }
    }

    // endregion

    // region Settings

    override fun applySettings(settings: ReaderSettings) {
        if (settings == this.settings) return
        this.settings = settings
        position.layout = settings.layout
        reader?.let {
            it.userFonts = userFonts(settings)
            it.pageTurns = settings.layout == ReaderLayout.Paged
            it.applySettings(settingsJson(settings).toString())
        }
    }

    private fun userFonts(settings: ReaderSettings): Map<String, File> {
        val custom = settings.customFont ?: return emptyMap()
        return buildMap {
            put("regular", File(custom.regularPath))
            custom.italicPath?.let { put("italic", File(it)) }
        }
    }

    private fun settingsJson(settings: ReaderSettings): JSONObject {
        val palette = settings.colorPalette
        val custom = settings.customFont
        val font = if (custom != null) {
            JSONObject()
                .put("family", custom.family)
                .put(
                    "faces",
                    org.json.JSONArray().apply {
                        put(JSONObject().put("src", "/userfonts/regular"))
                        if (custom.italicPath != null) put(JSONObject().put("src", "/userfonts/italic").put("style", "italic"))
                    },
                )
        } else {
            val family = settings.font.cssFamily
            JSONObject()
                .put("family", family)
                .put(
                    "faces",
                    org.json.JSONArray().apply {
                        put(JSONObject().put("src", "/assets/fonts/$family-Regular.ttf"))
                        put(JSONObject().put("src", "/assets/fonts/$family-Italic.ttf").put("style", "italic"))
                        if (family == "Cardo") put(JSONObject().put("src", "/assets/fonts/Cardo-Bold.ttf").put("weight", "bold"))
                    },
                )
        }
        return JSONObject()
            .put("flow", if (settings.layout == ReaderLayout.Scroll) "scrolled" else "paginated")
            .put(
                "colors",
                JSONObject()
                    .put("page", css(palette.backgroundArgb))
                    .put("ink", css(palette.textArgb))
                    .put("link", css(palette.linkArgb))
                    .put("dark", !settings.isLightBackground),
            )
            .put("font", font)
            .put("fontScale", settings.fontScale.toDouble())
            .apply {
                settings.lineHeight?.let { put("lineHeight", it.toDouble()) }
                settings.paragraphSpacing?.let { put("paragraphSpacing", it.toDouble()) }
            }
            // Readium's page-margin factor (1 = its default gutter) as foliate's gap.
            .put("gap", "${"%.2f".format(java.util.Locale.US, 5.0 * (settings.pageMargins ?: 1f))}%")
            .put(
                "align",
                when (settings.textAlign) {
                    ReaderTextAlign.Default -> "publisher"
                    ReaderTextAlign.Left -> "start"
                    ReaderTextAlign.Justify -> "justify"
                },
            )
            .put("tapTurns", settings.tapNavigation)
    }

    private fun css(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    // endregion

    override fun onForegroundResumed() = Unit

    // Not ported yet.
    override fun applyHighlights(highlights: List<ReaderHighlight>) = Unit

    override suspend fun search(query: String): List<ReaderSearchResult> = emptyList()

    override fun applySearchDecorations(results: List<ReaderSearchResult>) = Unit

    override fun clearSearch() = Unit

    override fun close() {
        closed = true
        unhost()
        reader?.close()
        reader = null
        readerActivity = null
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private companion object {
        const val TAG = "FoliateSession"
        const val HOST_TAG = "foliate-reader"
    }
}
