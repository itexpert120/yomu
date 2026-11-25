package com.itexpert120.yomu.data.reader.foliate

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewStartUpConfig
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
import com.itexpert120.yomu.reader.web.SelectionAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
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
 * Not yet ported (no-ops for now): in-book search, footnote popups, and the scroll-mode chapter
 * rubberband.
 */
@Singleton
class FoliateReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readium: ReadiumReaderEngine,
    private val crashLog: CrashLog,
) : ReaderEngine {

    private val main = Handler(Looper.getMainLooper())
    private val prepareScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One reader page loaded ahead of a tap (main thread only), and one book read ahead.
    private var warmReader: FoliateReader? = null
    private var webViewStarted = false
    private var warming = false
    private val prepareLock = Any()
    private var prepared: Pair<String, Deferred<Prepared?>>? = null

    private class Prepared(val book: FoliateBook, val cache: ReaderPublicationCache)

    init {
        // Leaving the app (or memory pressure) gives the warm page and the prepared book back.
        context.registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) releasePrepared()
                }

                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = releasePrepared()
            },
        )
    }

    override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? {
        ReaderOpenTrace.mark("reader.foliate-open")
        val ready = takePrepared(request.filePath)?.let { runCatching { it.await() }.getOrNull() }
        if (ready != null) ReaderOpenTrace.mark("reader.publication-prepared")
        val loaded = ready
            ?: withContext(Dispatchers.IO) { load(request.filePath, request.publicationCache) }
            ?: return null
        val book = loaded.book
        val cache = loaded.cache
        ReaderOpenTrace.mark("reader.session-assembled")
        val session = FoliateReaderSession(
            book = book,
            tableOfContents = cache.toc,
            initialLocatorJson = request.initialLocatorJson,
            initialSettings = request.initialSettings,
            crashLog = crashLog,
            isLookupPhrase = request.isLookupPhrase,
            newReader = ::takeWarmReader,
        )
        return ReaderOpenResult(session, cache)
    }

    private suspend fun load(filePath: String, publicationCache: ReaderPublicationCache?): Prepared? {
        val file = File(filePath).takeIf { it.isFile } ?: return null
        val cache = publicationCache?.takeIf { it.toc.isNotEmpty() }
            ?: readium.publicationCache(filePath)
            ?: ReaderPublicationCache()
        val book = runCatching { FoliateBook.read(file, cache) }
            .onFailure { Log.w(TAG, "Couldn't read ${file.name}", it) }
            .getOrNull() ?: return null
        return Prepared(book, cache)
    }

    override fun prepare(filePath: String, publicationCache: ReaderPublicationCache?) {
        synchronized(prepareLock) {
            if (prepared?.first == filePath) return
            prepared?.second?.cancel()
            prepared = filePath to prepareScope.async { load(filePath, publicationCache) }
        }
        main.post { preparedFile = File(filePath).also(::loadIntoWarmReader) }
    }

    // The book the next tap is likely to open; the warm page loads it too. Main thread.
    private var preparedFile: File? = null

    private fun loadIntoWarmReader(file: File) {
        val warm = warmReader ?: return
        when (warm.bookFile) {
            null -> warm.preload(file)
            file -> Unit
            // It holds another book: replace it with an empty page for the new one.
            else -> {
                warm.close()
                warmReader = newWarmReader()
            }
        }
    }

    private fun takePrepared(filePath: String): Deferred<Prepared?>? = synchronized(prepareLock) {
        prepared?.takeIf { it.first == filePath }?.second?.also { prepared = null }
    }

    override fun releasePrepared() {
        synchronized(prepareLock) {
            prepared?.second?.cancel()
            prepared = null
        }
        main.post {
            warmReader?.close()
            warmReader = null
        }
    }

    /**
     * Starts Chromium off the main thread, then — when the main thread is idle — builds a reader
     * page and loads its scripts, so the next book opens into a page that is already waiting.
     */
    @androidx.annotation.OptIn(WebViewCompat.ExperimentalAsyncStartUp::class)
    override fun warmUp() {
        if (warmReader != null || warming) return
        warming = true
        val buildWhenIdle = {
            main.post {
                Looper.myQueue().addIdleHandler {
                    warming = false
                    if (warmReader == null) warmReader = newWarmReader()
                    preparedFile?.let(::loadIntoWarmReader)
                    false
                }
            }
        }
        if (webViewStarted) {
            buildWhenIdle()
            return
        }
        runCatching {
            val config = WebViewStartUpConfig.Builder(Dispatchers.Default.asExecutor())
                .setShouldRunUiThreadStartUpTasks(true)
                .build()
            WebViewCompat.startUpWebView(
                context,
                config,
                WebViewCompat.WebViewStartUpCallback {
                    webViewStarted = true
                    buildWhenIdle()
                },
            )
        }.onFailure {
            webViewStarted = true
            buildWhenIdle()
        }
    }

    private fun newWarmReader(): FoliateReader? = runCatching {
        ReaderOpenTrace.mark("reader.foliate-warm-page")
        FoliateReader(context).also { reader ->
            preparedFile?.takeIf { it.isFile }?.let(reader::preload)
            reader.onRendererGone = {
                if (warmReader === reader) {
                    warmReader = null
                    reader.close()
                }
            }
        }
    }.onFailure { Log.w(TAG, "Couldn't build a warm reader page", it) }.getOrNull()

    /** The warm page for [file] if there is one (handing it over), else a fresh reader. Main thread. */
    private fun takeWarmReader(activity: Activity, file: File): FoliateReader {
        val warm = warmReader
        warmReader = null
        if (warm != null && warm.bookFile != null && warm.bookFile != file) {
            warm.close()
        } else if (warm != null) {
            ReaderOpenTrace.mark("reader.foliate-warm-page-taken")
            warm.attachTo(activity)
            return warm
        }
        return FoliateReader(activity)
    }

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
    private val isLookupPhrase: (String) -> Boolean,
    private val newReader: (Activity, File) -> FoliateReader,
) : ReaderSession {

    override val title: String = book.title ?: "Reading"

    private val _currentLocator = MutableStateFlow<ReaderLocator?>(null)
    override val currentLocator: StateFlow<ReaderLocator?> = _currentLocator.asStateFlow()

    private val _renderState = MutableStateFlow<ReaderRenderState>(ReaderRenderState.Opening)
    override val renderState: StateFlow<ReaderRenderState> = _renderState.asStateFlow()

    private val _centerTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val centerTaps: SharedFlow<Unit> = _centerTaps.asSharedFlow()

    private val _selections = MutableSharedFlow<ReaderSelection>(extraBufferCapacity = 1)
    override val selections: SharedFlow<ReaderSelection> = _selections.asSharedFlow()

    private val _highlightTaps = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val highlightTaps: SharedFlow<String> = _highlightTaps.asSharedFlow()

    // Not ported yet: never emits.
    override val footnotes: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()

    // The latest highlight set, drawn once the page shows (and again on a rebuilt page).
    private var highlights: List<ReaderHighlight> = emptyList()

    private var settings = initialSettings
    private var reader: FoliateReader? = null
    private var readerActivity: Activity? = null
    private var hostContainer: FragmentContainerView? = null
    private var hostFragmentManager: FragmentManager? = null
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

    // The reader screen lays the page out around its own chrome in both modes.
    override fun setImmersive(immersive: Boolean) = Unit

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
        val created = newReader(activity, book.file)
        // The book itself loads now; only showing it waits for the page's size.
        created.preload(book.file)
        created.onEvent = ::onPageEvent
        created.onRendererGone = ::onRendererGone
        created.isLookupPhrase = isLookupPhrase
        created.onSelection = ::onSelection
        created.userFonts = userFonts(settings)
        // The reader screen already reserves room for its bars around this view. Padding it again for
        // the system bars would resize the page as they hide just after it opens, and the book would
        // re-lay out under the reader (a visible jump from the restored page).
        // Lay the book out at the page's real size: start once the view has been laid out.
        created.view.doOnLayout {
            if (reader === created) {
                created.open(book.file, JSONObject().put("start", target).put("settings", settingsJson(settings)).toString())
            }
        }
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
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "page $name $json")
        when (name) {
            "first-page" -> {
                ReaderOpenTrace.mark("reader.first-page")
                _renderState.value = ReaderRenderState.Ready(_currentLocator.value?.href)
                drawHighlights()
            }
            "highlight-tap" -> JSONObject(json).optString("id").takeIf { it.isNotEmpty() }?.let(_highlightTaps::tryEmit)
            "relocate" -> onRelocate(JSONObject(json))
            "center-tap" -> _centerTaps.tryEmit(Unit)
            "script", "book", "view-open", "section-loaded" -> ReaderOpenTrace.mark("reader.foliate-$name")
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
            .put("animated", ValueAnimator.areAnimatorsEnabled())
    }

    private fun css(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    // endregion

    override fun onForegroundResumed() = Unit

    // region Selection and highlights

    private fun onSelection(action: SelectionAction, selection: JSONObject) {
        val index = selection.optInt("index", -1)
        val href = book.readingOrder.getOrNull(index) ?: return
        val text = selection.optString("text").trim()
        if (text.isEmpty()) return
        val locations = JSONObject().put("cfi", selection.optString("cfi"))
        _currentLocator.value?.takeIf { it.href == href }?.chapterProgression?.let { locations.put("progression", it) }
        val locator = JSONObject()
            .put("href", href)
            .put("type", book.mediaTypes[index])
            .put("locations", locations)
            .put(
                "text",
                JSONObject()
                    .put("before", selection.optString("before"))
                    .put("highlight", text)
                    .put("after", selection.optString("after")),
            )
        val readerAction = when (action) {
            SelectionAction.Copy -> ReaderSelection.Action.Copy
            SelectionAction.Highlight -> ReaderSelection.Action.Highlight
            SelectionAction.LookUp -> ReaderSelection.Action.LookUp
            SelectionAction.SearchWeb -> ReaderSelection.Action.SearchWeb
            SelectionAction.ReadAloud -> ReaderSelection.Action.ReadAloud
            SelectionAction.Share -> ReaderSelection.Action.Share
        }
        _selections.tryEmit(ReaderSelection(readerAction, text, locator.toString()))
    }

    override fun applyHighlights(highlights: List<ReaderHighlight>) {
        this.highlights = highlights
        if (_renderState.value is ReaderRenderState.Ready) drawHighlights()
    }

    // Only highlights made in this engine carry a CFI to draw; Readium's are positioned by its
    // own text search and aren't drawn here.
    private fun drawHighlights() {
        val reader = reader ?: return
        val list = org.json.JSONArray()
        for (highlight in highlights) {
            val cfi = runCatching { JSONObject(highlight.locatorJson).optJSONObject("locations")?.optString("cfi") }
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            list.put(JSONObject().put("id", highlight.id).put("cfi", cfi).put("color", "#%06X".format(highlight.colorArgb and 0xFFFFFF)))
        }
        reader.setHighlights(list.toString())
    }

    // endregion

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
