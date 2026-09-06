package com.itexpert120.yomu.data.reader.readium

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderHighlightDraft
import com.itexpert120.yomu.core.reader.ReaderLocator
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderRenderGate
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign

@Singleton
class ReadiumReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderEngine {

    private val customFontCssCache = CustomFontCssCache()

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? {
        ReaderOpenTrace.mark("reader.publication-open")
        val publication = openPublication(request.filePath) ?: return null
        ReaderOpenTrace.mark("reader.publication-opened")
        return try {
            coroutineScope {
                val cachedToc = async(Dispatchers.Default) {
                    request.publicationCache?.toc?.let { cached ->
                        publication.cachedTableOfContents(cached)
                    }
                }
                val readingOrderKeys = publication.readingOrder.map { it.normalizedResourceCacheKey() }
                val cachedWeightsInOrder = request.publicationCache?.validatedWeights(readingOrderKeys)
                val weights = if (cachedWeightsInOrder != null) {
                    async(Dispatchers.Default) {
                        cachedWeightsInOrder
                    }
                } else {
                    async { resourceWeightMap(request.filePath, publication.readingOrder) }
                        .let { deferred ->
                            async(Dispatchers.Default) {
                                val map = deferred.await()
                                readingOrderKeys.map { map[it] ?: 1 }
                            }
                        }
                }
                val tableOfContents = cachedToc.await()
                    ?: async(Dispatchers.Default) { publication.flattenedTableOfContents() }.await()
                val resourceWeights = weights.await()
                ReaderOpenTrace.mark("reader.cache-prepared")
                val tocItems = tableOfContents.map { it.item }
                val effectiveCache = ReaderPublicationCache(
                    toc = tocItems,
                    resourceWeights = readingOrderKeys.zip(resourceWeights).toMap(),
                )
                val session = ReadiumReaderSession(
                    context = context,
                    publication = publication,
                    initialLocatorJson = request.initialLocatorJson,
                    initialSettings = request.initialSettings,
                    resourceWeights = resourceWeights,
                    mappedTableOfContents = tableOfContents,
                    customFontCssCache = customFontCssCache,
                )
                ReaderOpenTrace.mark("reader.session-assembled")
                ReaderOpenResult(session, effectiveCache)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            runCatching { publication.close() }
            throw cancelled
        } catch (_: Throwable) {
            runCatching { publication.close() }
            null
        }
    }

    override suspend fun tableOfContents(filePath: String): List<ReaderTocItem>? {
        val publication = openPublication(filePath) ?: return null
        return try {
            publication.flattenedTableOfContents().map { it.item }
        } finally {
            runCatching { publication.close() }
        }
    }

    private suspend fun openPublication(filePath: String): Publication? {
        val asset = assetRetriever.retrieve(File(filePath).toUrl(isDirectory = false))
            .getOrElse { return null }
        return publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                null
            }
    }
}

/** Process-lifetime cache for one active custom font; bounded to avoid pinning large font blobs. */
private class CustomFontCssCache {
    private var key: String? = null
    private var css: String? = null

    @Synchronized
    fun get(key: String): String? = css?.takeIf { this.key == key }

    @Synchronized
    fun put(key: String, css: String) {
        if (css.length <= 16 * 1024 * 1024) {
            this.key = key
            this.css = css
        } else {
            this.key = null
            this.css = null
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private class ReadiumReaderSession(
    private val context: Context,
    private val publication: Publication,
    initialLocatorJson: String?,
    initialSettings: ReaderSettings,
    private val resourceWeights: List<Int>,
    mappedTableOfContents: List<ReadiumTocEntry>,
    private val customFontCssCache: CustomFontCssCache,
) : ReaderSession {

    override val title: String = publication.metadata.title ?: "Reading"
    override val tableOfContents: List<ReaderTocItem> = mappedTableOfContents.map { it.item }

    private val _currentLocator = MutableStateFlow<ReaderLocator?>(null)
    override val currentLocator: StateFlow<ReaderLocator?> = _currentLocator.asStateFlow()

    // The page remains covered until its target resource and all layout-affecting styling are ready.
    private val renderGate = ReaderRenderGate()
    private val _renderState = renderGate.state
    override val renderState: StateFlow<ReaderRenderState> = renderGate.state
    private var revealWatchdog: Job? = null

    // Cached @font-face CSS (base64 data URLs) for the active custom font, keyed by its family+paths so
    // the (largish) base64 is built once per font rather than per chapter. Readium can only serve
    // fonts from bundled assets, so custom fonts are embedded inline instead.
    private var customFontKey: String? = null
    private var customFontCss: String? = null

    private val _centerTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val centerTaps: SharedFlow<Unit> = _centerTaps.asSharedFlow()

    private val _lookUpRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val lookUpRequests: SharedFlow<String> = _lookUpRequests.asSharedFlow()

    private val _footnotes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val footnotes: SharedFlow<String> = _footnotes.asSharedFlow()

    private val _highlightRequests = MutableSharedFlow<ReaderHighlightDraft>(extraBufferCapacity = 1)
    override val highlightRequests: SharedFlow<ReaderHighlightDraft> =
        _highlightRequests.asSharedFlow()

    private val _highlightTaps = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val highlightTaps: SharedFlow<String> = _highlightTaps.asSharedFlow()

    // Latest highlight set; remembered so it can be re-applied once the navigator is hosted.
    private var pendingHighlights: List<ReaderHighlight> = emptyList()

    // Active search cursor (closed when a new query starts or the session closes), plus the latest
    // search hits, remembered so the underlines can be re-applied once the navigator is hosted.
    private var searchIterator: SearchIterator? = null
    private var pendingSearchDecorations: List<ReaderSearchResult> = emptyList()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var navigator: EpubNavigatorFragment? = null
    private var locatorCollectionJob: Job? = null
    private var scrollProgressJob: Job? = null
    private var restorationJob: Job? = null
    private var styleJob: Job? = null
    private val renderGeneration: Long get() = renderGate.generation
    private var restorationGeneration = 0L
    private var hostedFragmentManager: FragmentManager? = null
    private var hostedFragmentTag: String? = null

    private val initialLocator: Locator? = initialLocatorJson
        ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    // Latest engine locator, used for reading-order (chapter) navigation and for restoring position
    // after a re-host (config change) so the reader doesn't snap back to where the book was opened.
    private var lastLocator: Locator? = initialLocator

    // True while restoring [lastLocator] into a freshly re-hosted fragment; the fragment's transient
    // initial-locator emissions are ignored during this window so they can't clobber saved progress.
    private var restoring = false

    private var latestVisualPage: VisualPageState? = null
    private var pendingCompletedChapterId: String? = null

    // Latest settings, read by the tap handler (e.g. for the center-tap-opens-sheet toggle).
    private var currentSettings: ReaderSettings = initialSettings

    // Lazily-created TTS for the "Read aloud" selection action; reused across taps, shut down on close.
    private var tts: TextToSpeech? = null
    private var pendingSpeak: String? = null

    private val navigatorFactory = EpubNavigatorFactory(publication)

    private data class LogicalSection(
        val id: String,
        val title: String,
        val locator: Locator,
        val orderIndex: Int,
        val startProgression: Double,
    )

    private val logicalSections: List<LogicalSection> = mappedTableOfContents
        .mapNotNull { entry ->
            val locator = entry.locator ?: return@mapNotNull null
            if (entry.readingOrderIndex < 0) return@mapNotNull null
            LogicalSection(
                id = entry.item.id,
                title = entry.item.title,
                locator = locator,
                orderIndex = entry.readingOrderIndex,
                startProgression = entry.startProgression,
            )
        }
        .sortedWith(compareBy<LogicalSection> { it.orderIndex }.thenBy { it.startProgression })

    private val listener = object : EpubNavigatorFragment.Listener {
        override fun onExternalLinkActivated(url: AbsoluteUrl) {
            // Custom-scheme links injected by the scroll-mode rubberband overscroll gesture route
            // here as external links; intercept them for chapter navigation instead of opening a
            // browser. Pull-to-previous lands at the END of the previous chapter for continuity.
            val s = url.toString()
            when {
                s.startsWith(PREV_CHAPTER_URL) -> goToPreviousResourceEnd()
                s.startsWith(NEXT_CHAPTER_URL) -> goToNextResourceStart()
            }
        }

        // Tapping a footnote reference: surface the note's content in a popup instead of letting the
        // navigator jump to it at the bottom of the resource. Returning false cancels that jump.
        override fun shouldFollowInternalLink(
            link: Link,
            context: HyperlinkNavigator.LinkContext?,
        ): Boolean {
            val footnote = context as? HyperlinkNavigator.FootnoteContext ?: return true
            _footnotes.tryEmit(footnote.noteContent)
            return false
        }
    }

    // Readium creates a fresh WebView for each resource. One generation-aware finalizer owns all
    // critical styling and is the only place allowed to reveal that resource.
    private val paginationListener = object : EpubNavigatorFragment.PaginationListener {
        override fun onPageLoaded() {
            ReaderOpenTrace.mark("reader.target-resource-loaded")
            // Initial opening has no prior resource transition to finalize. Chapter changes are
            // finalized from updateCurrentLocator instead: Readium invokes this callback before it
            // publishes the new locator, and starting both passes can mutate the WebView twice.
            if (_renderState.value is ReaderRenderState.Opening) {
                scheduleCriticalStyling()
            }
        }

        override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
            updateCurrentLocator(
                locator,
                visualPage = VisualPageState(
                    href = locator.href.toString(),
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                ),
            )
        }
    }

    private fun shouldPreloadBundledFont(family: String): Boolean = currentSettings.customFont == null && currentSettings.font.cssFamily == family

    private suspend fun criticalBootstrapJs(): String {
        val customFontCss = currentSettings.customFont?.let { ensureCustomFontCss(it) }
        val fontMutation = customFontCss?.let(::customFontInjectJs) ?: CUSTOM_FONT_CLEAR_JS
        return listOf(
            viewportFitJs(
                enabled = currentSettings.immersiveChrome && currentSettings.layout == ReaderLayout.Scroll,
            ),
            scrollCssJsForCurrent(),
            readerPaletteJs(currentSettings),
            fontMutation,
        ).joinToString("\n")
    }

    private fun scheduleCriticalStyling() {
        styleJob?.cancel()
        val generation = renderGeneration
        val expectedHref = lastLocator?.href?.toString()
        val expectedNavigator = navigator ?: return
        styleJob = scope.launch {
            ReaderOpenTrace.mark("reader.critical-bootstrap-start")
            applyScrollbars(currentSettings)
            // All layout-affecting mutations go through one idempotent bootstrap. This prevents
            // locator, settings, and page-loaded callbacks from racing one another on first paint.
            val ready = renderGate.reveal(
                generation,
                expectedHref,
                isCurrent = { navigator === expectedNavigator && expectedHref == lastLocator?.href?.toString() },
                style = {
                    val styled = evalWithRetry(criticalBootstrapJs())
                    if (styled) clearImmersiveScrollTopPadding()
                    styled
                },
                content = { awaitRenderableContent(expectedNavigator) },
                preDraw = { awaitNextPreDraw(expectedNavigator) },
            )
            if (!ready) return@launch
            revealWatchdog?.cancel()
            ReaderOpenTrace.mark("reader.pre-draw")
            ReaderOpenTrace.mark("reader.final-reveal")
            // Gesture work is deliberately after the first stable frame.
            injectOverscroll()
        }
    }

    override val fragmentFactory: FragmentFactory
        get() = navigatorFactory.createFragmentFactory(
            initialLocator = lastLocator ?: initialLocator,
            readingOrder = publication.readingOrder,
            initialPreferences = currentSettings.toPreferences(),
            listener = listener,
            paginationListener = paginationListener,
            configuration = EpubNavigatorFragment.Configuration {
                // In scroll mode a horizontal swipe jumps a whole resource (chapter), which users
                // hit accidentally while scrolling vertically. Disable it: chapters are advanced via
                // the next-chapter button / controls sheet / TOC (programmatic, unaffected). This is
                // the engine-level guard the InputListener.onDrag swallow couldn't provide. Paged
                // mode is untouched — the flag only gates scroll-mode swipes.
                disablePageTurnsWhileScrolling = true
                // Avoid a native inset pass on the first immersive layout; the host still consumes
                // insets so a later live preference change can be applied safely.
                shouldApplyInsetsPadding = !currentSettings.immersiveChrome

                // Readium caps the text column at an "optimal line length" and centres it, which
                // leaves huge side margins on a wide tablet. Raise the cap far past any screen so the
                // column fills the available width; the page margins (pageMargins) remain the gutter.
                readiumCssRsProperties = RsProperties(maxLineLength = Length.Rem(120.0))

                // Customize the text-selection menu. Readium replaces the WebView's native menu with
                // this callback entirely (it drops Chromium's Copy/Share/Read-aloud), so we rebuild
                // the standard actions ourselves — Copy, Read aloud (TTS), Share — and slot "Look up"
                // in. "Look up" hands the selected text to the dictionary flow; this also replaces the
                // old floating look-up bar.
                selectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        menu ?: return false
                        menu.add(Menu.NONE, MENU_COPY, 0, android.R.string.copy)
                        menu.add(Menu.NONE, MENU_HIGHLIGHT, 1, "Highlight")
                        menu.add(Menu.NONE, MENU_LOOK_UP, 2, "Look up")
                        menu.add(Menu.NONE, MENU_SEARCH_WEB, 3, "Search web")
                        menu.add(Menu.NONE, MENU_SPEAK, 4, "Read aloud")
                        menu.add(Menu.NONE, MENU_SHARE, 5, "Share")
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        val id = item?.itemId ?: return false
                        if (id !in setOf(
                                MENU_COPY,
                                MENU_HIGHLIGHT,
                                MENU_LOOK_UP,
                                MENU_SEARCH_WEB,
                                MENU_SPEAK,
                                MENU_SHARE,
                            )
                        ) {
                            return false
                        }
                        scope.launch {
                            val selection = (navigator as? SelectableNavigator)?.currentSelection()
                            val locator = selection?.locator
                            val text = locator?.text?.highlight?.trim()
                            if (!text.isNullOrBlank()) {
                                when (id) {
                                    MENU_COPY -> copySelection(text)
                                    MENU_SPEAK -> speak(text)
                                    MENU_SHARE -> shareSelection(text)
                                    MENU_LOOK_UP -> _lookUpRequests.tryEmit(text)
                                    MENU_SEARCH_WEB -> searchWeb(text)
                                    MENU_HIGHLIGHT -> _highlightRequests.tryEmit(
                                        ReaderHighlightDraft(
                                            locatorJson = locator.toJSON().toString(),
                                            text = text,
                                        ),
                                    )
                                }
                            }
                            mode?.finish()
                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {}
                }

                // Serve the bundled .ttf files from assets/fonts/ to the EPUB engine.
                servedAssets += "fonts/.*"

                // Variable fonts: one upright + one italic face, each spanning the full weight axis.
                addFontFamilyDeclaration(FontFamily("Lora")) {
                    addFontFace {
                        addSource("fonts/Lora-Regular.ttf", preload = shouldPreloadBundledFont("Lora"))
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(100..900)
                    }
                    addFontFace {
                        addSource("fonts/Lora-Italic.ttf")
                        setFontStyle(FontStyle.ITALIC)
                        setFontWeight(100..900)
                    }
                }
                addFontFamilyDeclaration(FontFamily("Karla")) {
                    addFontFace {
                        addSource("fonts/Karla-Regular.ttf", preload = shouldPreloadBundledFont("Karla"))
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(100..900)
                    }
                    addFontFace {
                        addSource("fonts/Karla-Italic.ttf")
                        setFontStyle(FontStyle.ITALIC)
                        setFontWeight(100..900)
                    }
                }
                addFontFamilyDeclaration(FontFamily("Rubik")) {
                    addFontFace {
                        addSource("fonts/Rubik-Regular.ttf", preload = shouldPreloadBundledFont("Rubik"))
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(100..900)
                    }
                    addFontFace {
                        addSource("fonts/Rubik-Italic.ttf")
                        setFontStyle(FontStyle.ITALIC)
                        setFontWeight(100..900)
                    }
                }
                // Cardo ships as static faces: a regular, a bold, and an italic.
                addFontFamilyDeclaration(FontFamily("Cardo")) {
                    addFontFace {
                        addSource("fonts/Cardo-Regular.ttf", preload = shouldPreloadBundledFont("Cardo"))
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(FontWeight.NORMAL)
                    }
                    addFontFace {
                        addSource("fonts/Cardo-Bold.ttf")
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(FontWeight.BOLD)
                    }
                    addFontFace {
                        addSource("fonts/Cardo-Italic.ttf")
                        setFontStyle(FontStyle.ITALIC)
                        setFontWeight(FontWeight.NORMAL)
                    }
                }
                addFontFamilyDeclaration(FontFamily("Nunito")) {
                    addFontFace {
                        addSource("fonts/Nunito-Regular.ttf", preload = shouldPreloadBundledFont("Nunito"))
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(100..900)
                    }
                    addFontFace {
                        addSource("fonts/Nunito-Italic.ttf")
                        setFontStyle(FontStyle.ITALIC)
                        setFontWeight(100..900)
                    }
                }
                addFontFamilyDeclaration(FontFamily("Merriweather")) {
                    addFontFace {
                        addSource("fonts/Merriweather-Regular.ttf", preload = shouldPreloadBundledFont("Merriweather"))
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(100..900)
                    }
                    addFontFace {
                        addSource("fonts/Merriweather-Italic.ttf")
                        setFontStyle(FontStyle.ITALIC)
                        setFontWeight(100..900)
                    }
                }
            },
        )

    override val fragmentClassName: String = EpubNavigatorFragment::class.java.name

    override fun onFragmentHosted(fragmentManager: FragmentManager, tag: String) {
        ReaderOpenTrace.mark("reader.fragment-attached")
        val rehost = navigator != null
        if (rehost) {
            beginChapterTransition()
        }
        val nav = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment ?: return
        restorationGeneration++
        restorationJob?.cancel()
        restorationJob = null
        restoring = false
        navigator = nav
        hostedFragmentManager = fragmentManager
        hostedFragmentTag = tag
        locatorCollectionJob?.cancel()
        locatorCollectionJob = scope.launch {
            nav.currentLocator.collect { locator ->
                updateCurrentLocator(locator)
            }
        }
        // A restored/configuration-hosted navigator can emit its locator before the pagination
        // callback is wired. Use that first locator as a second, generation-safe trigger; it does
        // not navigate, it only lets the single bootstrap wait for the actual page.
        scope.launch {
            nav.currentLocator.first()
            if (_renderState.value !is ReaderRenderState.Ready) scheduleCriticalStyling()
        }
        // Tap zones: in PAGED mode the left/right thirds turn pages and the centre toggles the
        // controls bar. In SCROLL mode there are no page-turn zones, so a tap *anywhere* toggles the
        // bar. Unhandled taps return false so Readium can still activate in-page links/footnotes.
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val view = nav.view ?: return false
                if (view.width <= 0) return false
                val x = event.point.x / view.width
                val paged = currentSettings.layout == ReaderLayout.Paged
                if (currentSettings.tapNavigation && paged) {
                    if (x <= TAP_LEFT) {
                        goBackward()
                        return true
                    }
                    if (x >= TAP_RIGHT) {
                        goForward()
                        return true
                    }
                }
                // Centre third (paged) or anywhere (scroll) toggles the chapter-controls bar.
                if (!paged || (x > TAP_LEFT && x < TAP_RIGHT)) {
                    _centerTaps.tryEmit(Unit)
                    return true
                }
                return false
            }
        })

        // Tapping an on-page highlight surfaces its id so the UI can open the edit/delete popup.
        (nav as? DecorableNavigator)?.addDecorationListener(
            HIGHLIGHTS_GROUP,
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(
                    event: DecorableNavigator.OnActivatedEvent,
                ): Boolean {
                    if (event.group != HIGHLIGHTS_GROUP) return false
                    _highlightTaps.tryEmit(event.decoration.id)
                    return true
                }
            },
        )

        // Re-apply non-critical decorations after the first stable page, never during opening.
        if (pendingHighlights.isNotEmpty() || pendingSearchDecorations.isNotEmpty()) {
            scope.launch {
                renderState.filter { it is ReaderRenderState.Ready }.first()
                renderHighlights(pendingHighlights)
                renderSearchDecorations(pendingSearchDecorations)
            }
        }
    }

    override fun applyHighlights(highlights: List<ReaderHighlight>) {
        pendingHighlights = highlights
        renderHighlights(highlights)
    }

    private fun renderHighlights(highlights: List<ReaderHighlight>) {
        if (renderState.value !is ReaderRenderState.Ready) return
        val nav = navigator as? DecorableNavigator ?: return
        val decorations = highlights.mapNotNull { highlight ->
            val locator = runCatching {
                Locator.fromJSON(JSONObject(highlight.locatorJson))
            }.getOrNull() ?: return@mapNotNull null
            Decoration(
                id = highlight.id,
                locator = locator,
                style = Decoration.Style.Highlight(tint = highlight.colorArgb),
            )
        }
        scope.launch { nav.applyDecorations(decorations, HIGHLIGHTS_GROUP) }
    }

    override suspend fun search(query: String): List<ReaderSearchResult> {
        if (query.isBlank()) return emptyList()
        runCatching { searchIterator?.close() }
        searchIterator = null
        return withContext(Dispatchers.IO) {
            val iterator = publication.search(query) ?: return@withContext emptyList()
            searchIterator = iterator
            val out = ArrayList<ReaderSearchResult>()
            try {
                while (out.size < MAX_SEARCH_RESULTS) {
                    val page = iterator.next().getOrElse { error("Publication search failed: $it") } ?: break
                    for (loc in page.locators) {
                        out += ReaderSearchResult(
                            locatorJson = loc.toJSON().toString(),
                            before = loc.text.before.orEmpty(),
                            match = loc.text.highlight.orEmpty(),
                            after = loc.text.after.orEmpty(),
                            chapterTitle = loc.title,
                        )
                        if (out.size >= MAX_SEARCH_RESULTS) break
                    }
                }
                out
            } finally {
                runCatching { iterator.close() }
                if (searchIterator === iterator) searchIterator = null
            }
        }
    }

    override fun applySearchDecorations(results: List<ReaderSearchResult>) {
        pendingSearchDecorations = results
        renderSearchDecorations(results)
    }

    override fun clearSearch() {
        pendingSearchDecorations = emptyList()
        val nav = navigator as? DecorableNavigator ?: return
        scope.launch { nav.applyDecorations(emptyList(), SEARCH_GROUP) }
    }

    private fun renderSearchDecorations(results: List<ReaderSearchResult>) {
        if (renderState.value !is ReaderRenderState.Ready) return
        val nav = navigator as? DecorableNavigator ?: return
        val decorations = results.mapIndexedNotNull { index, result ->
            val locator = runCatching {
                Locator.fromJSON(JSONObject(result.locatorJson))
            }.getOrNull() ?: return@mapIndexedNotNull null
            Decoration(
                id = "search-$index",
                locator = locator,
                style = Decoration.Style.Underline(tint = SEARCH_TINT),
            )
        }
        scope.launch { nav.applyDecorations(decorations, SEARCH_GROUP) }
    }

    private data class VisualPageState(
        val href: String,
        val pageIndex: Int,
        val totalPages: Int,
    )

    private data class ScrollProgressState(
        val href: String,
        val progression: Double,
    )

    private var latestScrollProgress: ScrollProgressState? = null

    private data class ProgressMetrics(
        val bookProgress: Double,
        val chapter: LogicalSection?,
        val chapterProgress: Double?,
        val chapterIndex: Int,
        val completed: Boolean,
    )

    private fun updateCurrentLocator(
        locator: Locator,
        visualPage: VisualPageState? = null,
        measuredScrollProgress: Double? = null,
        queryScrollProgress: Boolean = true,
    ) {
        if (restoring) return
        visualPage?.let { latestVisualPage = it }
        val previousHref = lastLocator?.href?.toString()
        val hrefStr = locator.href.toString()
        val resourceChanged = previousHref != null && previousHref != hrefStr
        val transitionAlreadyStarted = _renderState.value is ReaderRenderState.Transitioning
        if (
            resourceChanged &&
            !transitionAlreadyStarted &&
            currentSettings.layout == ReaderLayout.Scroll
        ) {
            // Links and navigator-driven chapter changes do not always pass through one of Yomu's
            // explicit navigation helpers. Scroll mode still needs its layout cover for those
            // moves. In paged mode this locator arrives after Readium has already displayed the
            // adjacent spine item; covering it here would make the new chapter appear twice.
            val order = publication.readingOrder
            val previousIndex = order.indexOfFirst { it.url().toString() == previousHref }
            val currentIndex = order.indexOfFirst { it.url().toString() == hrefStr }
            beginChapterTransition(
                forward = currentIndex >= previousIndex,
            )
        }
        lastLocator = locator
        if (latestScrollProgress?.href != hrefStr) latestScrollProgress = null
        measuredScrollProgress?.let {
            latestScrollProgress = ScrollProgressState(hrefStr, it.coerceIn(0.0, 1.0))
        }
        if (currentSettings.layout == ReaderLayout.Scroll && queryScrollProgress) {
            requestScrollProgress(locator)
        }
        val order = publication.readingOrder
        val index = order.indexOfFirst { it.url().toString() == hrefStr }
        val metrics = progressMetrics(locator, visualPage ?: latestVisualPage)
        val crossedChapterId = completedSectionOnSequentialCrossing(
            pendingChapterId = pendingCompletedChapterId,
            currentChapterId = metrics.chapter?.id,
            previousHref = previousHref,
            currentHref = hrefStr,
        )
        if (crossedChapterId != null) pendingCompletedChapterId = null
        val visualPagesLeft = latestVisualPage
            ?.takeIf {
                currentSettings.layout == ReaderLayout.Paged &&
                    it.href == hrefStr &&
                    it.totalPages > 0
            }
            ?.let {
                val pageIndex = it.pageIndex.coerceIn(0, it.totalPages - 1)
                (it.totalPages - pageIndex - 1).coerceAtLeast(0)
            }
        _currentLocator.value = ReaderLocator(
            locatorJson = locator.toJSON().toString(),
            totalProgression = locator.locations.totalProgression,
            chapterTitle = metrics.chapter?.title ?: locator.title,
            href = hrefStr,
            chapterProgression = metrics.chapterProgress,
            hasPreviousChapter = metrics.chapterIndex > 0,
            hasNextChapter = metrics.chapterIndex in 0 until logicalSections.lastIndex,
            bookProgress = metrics.bookProgress,
            chapterPagesLeft = visualPagesLeft,
            chapterId = metrics.chapter?.id,
            completedChapterId = crossedChapterId,
            completed = metrics.completed,
        )
        if (resourceChanged) {
            // Readium publishes the canonical locator after loading the resource. Finalize styling
            // here exactly once so the new chapter is not reflowed by two competing bootstraps.
            scheduleCriticalStyling()
        }
    }

    private fun progressMetrics(locator: Locator, visualPage: VisualPageState?): ProgressMetrics {
        val order = publication.readingOrder
        val href = locator.href.toString()
        val resourceIndex = order.indexOfFirst { it.url().toString() == href }
        if (resourceIndex < 0 || order.isEmpty()) {
            return ProgressMetrics(0.0, null, null, -1, false)
        }
        val visualPageProgress = visualPage
            ?.takeIf { it.href == href && it.totalPages > 0 }
            ?.let { (it.pageIndex + 1).toDouble() / it.totalPages }
        val resourceProgress = resolveResourceProgress(
            locatorProgress = locator.locations.progression,
            visualPageProgress = visualPageProgress,
            measuredScrollProgress = latestScrollProgress
                ?.takeIf { it.href == href }
                ?.progression,
            useVisualPageProgress = currentSettings.layout == ReaderLayout.Paged,
        )
        val weights = resourceWeights.takeIf { it.size == order.size }
            ?: List(order.size) { 1 }
        val logical = calculateLogicalProgress(
            resourceIndex = resourceIndex,
            resourceProgress = resourceProgress,
            locatorProgress = locator.locations.progression ?: 0.0,
            resourceWeights = weights,
            sections = logicalSections.map { SectionBoundary(it.orderIndex, it.startProgression) },
        )
        val chapterIndex = logical.sectionIndex
        val chapter = logicalSections.getOrNull(chapterIndex)
        return ProgressMetrics(
            bookProgress = logical.bookProgress,
            chapter = chapter,
            chapterProgress = logical.sectionProgress,
            chapterIndex = chapterIndex,
            completed = logical.completed,
        )
    }
    override fun applySettings(settings: ReaderSettings) {
        if (settings == currentSettings) return
        val previous = currentSettings
        val publicationPresentationChanged = previous.publicationPresentationDiffersFrom(settings)
        val criticalStylingChanged =
            publicationPresentationChanged || previous.immersiveChrome != settings.immersiveChrome
        currentSettings = settings
        if (settings.layout != ReaderLayout.Scroll) {
            scrollProgressJob?.cancel()
            latestScrollProgress = null
        }
        val nav = navigator
        if (nav != null) {
            when {
                criticalStylingChanged -> {
                    beginChapterTransition()
                    if (publicationPresentationChanged) {
                        nav.submitPreferences(settings.toPreferences())
                    }
                    scheduleCriticalStyling()
                }

                previous.showScrollbar != settings.showScrollbar -> applyScrollbars(settings)
            }
        }
    }

    private fun ReaderSettings.publicationPresentationDiffersFrom(other: ReaderSettings): Boolean = layout != other.layout ||
        colorPalette != other.colorPalette ||
        font != other.font ||
        customFont != other.customFont ||
        fontScale != other.fontScale ||
        lineHeight != other.lineHeight ||
        pageMargins != other.pageMargins ||
        paragraphSpacing != other.paragraphSpacing ||
        textAlign != other.textAlign

    override fun refreshImmersiveLayout() {
        clearImmersiveScrollTopPadding()
    }

    override fun onForegroundResumed() {
        clearImmersiveScrollTopPadding()
        val nav = navigator ?: return
        val target = lastLocator ?: return
        startRestoration(
            nav = nav,
            target = target,
            recreateOnFailure = true,
            verifyCurrentFirst = true,
        )
    }

    // Inject the active custom font's @font-face (or clear it when switching back to a bundled font)
    // into the current resource. The family is set via EpubPreferences.fontFamily; this just provides
    // the glyphs. Builds the base64 CSS once per font (cached) on first use.
    private suspend fun applyCustomFontInline() {
        navigator ?: return
        val ref = currentSettings.customFont
        if (ref == null) {
            customFontKey = null
            customFontCss = null
            evalWithRetry(CUSTOM_FONT_CLEAR_JS)
            return
        }
        val css = ensureCustomFontCss(ref) ?: return
        evalWithRetry(customFontInjectJs(css))
    }

    private suspend fun ensureCustomFontCss(ref: CustomFontRef): String? {
        val key = buildCustomFontCacheKey(ref)
        if (key == customFontKey && customFontCss != null) return customFontCss
        customFontCssCache.get(key)?.let {
            customFontKey = key
            customFontCss = it
            return it
        }
        val css = withContext(Dispatchers.IO) { buildFontFaceCss(ref) }
        customFontKey = key
        customFontCss = css
        css?.let { customFontCssCache.put(key, it) }
        return css
    }

    private fun buildCustomFontCacheKey(ref: CustomFontRef): String = buildString {
        append(ref.family)
        listOf(ref.regularPath, ref.italicPath).forEach { path ->
            append('|')
            if (path == null) {
                append("null")
            } else {
                val file = File(path)
                append(path).append(':').append(file.length()).append(':').append(file.lastModified())
            }
        }
    }

    private fun buildFontFaceCss(ref: CustomFontRef): String? {
        val regular = runCatching { File(ref.regularPath).readBytes() }.getOrNull() ?: return null
        val regularB64 = Base64.encodeToString(regular, Base64.NO_WRAP)
        val sb = StringBuilder()
        // Declared at weight 400; the browser synthesizes bold for headings (no bold file is fetched).
        sb.append(
            "@font-face{font-family:'${ref.family}';font-style:normal;font-weight:400;" +
                "src:url(data:font/woff2;base64,$regularB64) format('woff2');}",
        )
        ref.italicPath
            ?.let { runCatching { File(it).readBytes() }.getOrNull() }
            ?.let { italic ->
                val italicB64 = Base64.encodeToString(italic, Base64.NO_WRAP)
                sb.append(
                    "@font-face{font-family:'${ref.family}';font-style:italic;font-weight:400;" +
                        "src:url(data:font/woff2;base64,$italicB64) format('woff2');}",
                )
            }
        return sb.toString()
    }

    private fun customFontInjectJs(css: String): String = """
        (function() {
          var id = 'yomu-custom-font';
          var s = document.getElementById(id);
          if (!s) { s = document.createElement('style'); s.id = id; (document.head || document.documentElement).appendChild(s); }
          s.textContent = "$css";
        })();
    """.trimIndent()

    // Readium's evaluateJavascript targets only the currently-visible reflowable page fragment and
    // silently returns Kotlin null when that fragment isn't current yet (a race against page load).
    // Fire-and-forget therefore drops injections permanently for a resource. Retry a few times with a
    // short backoff until the script actually executes (any non-null result, even "null"/undefined,
    // means it ran). All injected scripts are idempotent, so a retry that lands twice is harmless.
    private fun injectJs(js: String) {
        scope.launch { evalWithRetry(js) }
    }

    // Evaluate [js] in the current resource, retrying through the load race. Returns true once it
    // actually executed (any non-null result, even "null"/undefined), false if it never landed.
    private suspend fun evalWithRetry(js: String): Boolean {
        repeat(JS_INJECT_ATTEMPTS) { attempt ->
            val nav = navigator ?: return false
            val result = runCatching { nav.evaluateJavascript(js) }.getOrNull()
            if (result != null) return true
            delay(JS_INJECT_RETRY_DELAY_MS * (attempt + 1))
        }
        return false
    }

    private fun scrollCssJsForCurrent(): String {
        val scrollMode = currentSettings.layout == ReaderLayout.Scroll
        val immersiveScroll = currentSettings.immersiveChrome && scrollMode
        val chapterPaddingPx = if (immersiveScroll) chapterStartPaddingPx() else 0
        return scrollCssJs(
            immersiveScroll = immersiveScroll,
            chapterStartPaddingPx = chapterPaddingPx,
            chapterEndPaddingPx = chapterPaddingPx,
        )
    }

    // Cover the page during a chapter change so it is revealed only once the new resource is re-styled.
    private fun beginChapterTransition(forward: Boolean = true) {
        renderGate.transition(forward)
        styleJob?.cancel()
        revealWatchdog?.cancel()
        val generation = renderGeneration
        revealWatchdog = scope.launch {
            delay(STYLE_REVEAL_TIMEOUT_MS)
            renderGate.fail(generation)
        }
    }

    private fun chapterStartPaddingPx(): Int {
        val topInset = topSystemInsetCssPx()
        val fallbackTop = CHAPTER_START_FALLBACK_PADDING_DP.roundToInt()
        val breathingRoom = CHAPTER_START_EXTRA_PADDING_DP.roundToInt()
        return maxOf(topInset, fallbackTop) + breathingRoom
    }

    private fun rubberbandTopOffsetPx(): Int {
        val margin = RUBBERBAND_EDGE_MARGIN_DP.roundToInt()
        if (!currentSettings.immersiveChrome) return margin
        val topInset = topSystemInsetCssPx()
        val fallbackTop = CHAPTER_START_FALLBACK_PADDING_DP.roundToInt()
        return maxOf(topInset, fallbackTop) + margin
    }

    private fun topSystemInsetCssPx(): Int {
        // Android window insets are physical view pixels; injected WebView CSS wants CSS pixels.
        val root = navigator?.view
        val density = root?.resources?.displayMetrics?.density
            ?: context.resources.displayMetrics.density
        val statusTop = root?.let {
            ViewCompat.getRootWindowInsets(it)
                ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
                ?.top
        } ?: 0
        val cutoutTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root?.rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
        } else {
            0
        }
        return (maxOf(statusTop, cutoutTop) / density).roundToInt()
    }

    private fun injectViewportFit() {
        navigator ?: return
        val js = viewportFitJs(
            enabled = currentSettings.immersiveChrome && currentSettings.layout == ReaderLayout.Scroll,
        )
        injectJs(js)
    }

    private suspend fun injectViewportFitAwaited() {
        if (navigator == null) return
        evalWithRetry(
            viewportFitJs(
                enabled = currentSettings.immersiveChrome && currentSettings.layout == ReaderLayout.Scroll,
            ),
        )
    }

    private fun clearImmersiveScrollTopPadding(settings: ReaderSettings = currentSettings) {
        if (!settings.immersiveChrome || settings.layout != ReaderLayout.Scroll) return
        val root = navigator?.view ?: return
        fun clear() {
            forEachWebView(root) { wv ->
                val parent = wv.parent as? View ?: return@forEachWebView
                if (parent.paddingTop != 0) {
                    parent.setPadding(parent.paddingLeft, 0, parent.paddingRight, parent.paddingBottom)
                }
            }
        }
        clear()
        root.post { clear() }
    }

    // Re-enable the WebView's native vertical scrollbar in scroll mode (Readium force-disables it),
    // themed to the reading text colour on API 29+. The CSS route can't work — scroll mode is a
    // native WebView scroll, not a CSS-overflow element. android.webkit.WebView is not a Readium
    // type, so walking the navigator's view tree keeps the engine boundary intact.
    private fun applyScrollbars(settings: ReaderSettings) {
        val root = navigator?.view ?: return
        val scroll = settings.layout == ReaderLayout.Scroll
        forEachWebView(root) { wv ->
            wv.isVerticalScrollBarEnabled = scroll && settings.showScrollbar
            wv.isHorizontalScrollBarEnabled = false
            wv.isScrollbarFadingEnabled = true
            wv.scrollBarFadeDuration = 600
            wv.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val c = settings.textArgb.toInt()
                wv.verticalScrollbarThumbDrawable = GradientDrawable().apply {
                    setColor(
                        android.graphics.Color.argb(
                            0x66,
                            (c shr 16) and 0xFF,
                            (c shr 8) and 0xFF,
                            c and 0xFF,
                        ),
                    )
                    cornerRadius = 3f * root.resources.displayMetrics.density
                }
            }
        }
    }

    private fun forEachWebView(v: View, action: (WebView) -> Unit) {
        when (v) {
            is WebView -> action(v)
            is ViewGroup -> for (i in 0 until v.childCount) forEachWebView(v.getChildAt(i), action)
        }
    }

    // Inject (or refresh) the scroll-mode rubberband overscroll gesture into the current resource:
    // pulling past the top goes to the previous chapter, past the bottom to the next. A no-op
    // teardown in paged mode (enabled = false).
    private fun injectOverscroll() {
        navigator ?: return
        val hrefStr = lastLocator?.href?.toString()
        val order = publication.readingOrder
        val index =
            if (hrefStr != null) order.indexOfFirst { it.url().toString() == hrefStr } else -1
        val hasPrev = index > 0
        val hasNext = index in 0 until order.lastIndex
        val enabled = currentSettings.layout == ReaderLayout.Scroll
        val js = overscrollJs(enabled, hasPrev, hasNext, currentSettings, rubberbandTopOffsetPx())
        injectJs(js)
    }

    override fun goForward() {
        (navigator as? OverflowableNavigator)?.goForward()
    }

    override fun goBackward() {
        (navigator as? OverflowableNavigator)?.goBackward()
    }

    override fun nextChapter() = goToLogicalSectionOffset(+1)

    override fun previousChapter() = goToLogicalSectionOffset(-1)

    private fun goToLogicalSectionOffset(delta: Int) {
        val current = lastLocator ?: return
        val index = progressMetrics(current, latestVisualPage).chapterIndex
        val target = logicalSections.getOrNull(index + delta)?.locator ?: return
        beginChapterTransition(forward = delta > 0)
        scope.launch { navigator?.let { goChecked(it, target) } }
    }

    // Navigate to the END of the previous resource (progression ~1), so pulling down past the top of
    // a chapter reveals the text just before it — the continuous-reading "rubberband" behaviour.
    private fun goToPreviousResourceEnd() {
        val hrefStr = lastLocator?.href?.toString() ?: return
        val order = publication.readingOrder
        val index = order.indexOfFirst { it.url().toString() == hrefStr }
        val prev = order.getOrNull(index - 1) ?: return
        val base = publication.locatorFromLink(prev) ?: return
        val target = base.copy(locations = Locator.Locations(progression = 0.999))
        beginChapterTransition(forward = false)
        scope.launch { navigator?.let { goChecked(it, target) } }
    }

    // Scroll overscroll represents continuous reading, so advance to the immediately following
    // spine resource. A logical TOC chapter can span several resources (for example chapter005 and
    // chapter006 in the Yen Press test book); using nextChapter() here would skip its actual text.
    private fun goToNextResourceStart() {
        val hrefStr = lastLocator?.href?.toString() ?: return
        val order = publication.readingOrder
        val index = order.indexOfFirst { it.url().toString() == hrefStr }
        val next = order.getOrNull(index + 1) ?: return
        val target = publication.locatorFromLink(next) ?: return
        val currentSection = lastLocator
            ?.let { logicalSections.getOrNull(progressMetrics(it, latestVisualPage).chapterIndex) }
        val targetSection = logicalSections.getOrNull(progressMetrics(target, null).chapterIndex)
        val completedChapterId = currentSection?.id
            ?.takeIf { targetSection?.id != it }
        beginChapterTransition(forward = true)
        scope.launch {
            val nav = navigator ?: return@launch
            pendingCompletedChapterId = completedChapterId
            if (!goChecked(nav, target)) pendingCompletedChapterId = null
        }
    }

    override fun goToProgression(totalProgression: Double) {
        scope.launch {
            val order = publication.readingOrder
            val weights = resourceWeights.takeIf { it.size == order.size }
                ?: List(order.size) { 1 }
            val weightedTarget = weightedProgressTarget(totalProgression, weights)
                ?: return@launch
            val target = order.getOrNull(weightedTarget.resourceIndex)
                ?.let { publication.locatorFromLink(it) }
                ?.let { locator ->
                    locator.copy(
                        locations = locator.locations.copy(
                            progression = weightedTarget.resourceProgress,
                        ),
                    )
                } ?: return@launch
            // Cover only when landing on a different resource (a same-resource jump doesn't reload, so
            // onPageLoaded wouldn't fire to lift the cover).
            if (target.href.toString() != lastLocator?.href?.toString()) {
                val forward = totalProgression >=
                    (lastLocator?.let { progressMetrics(it, latestVisualPage).bookProgress } ?: 0.0)
                beginChapterTransition(forward = forward)
            }
            navigator?.let { goChecked(it, target) }
        }
    }

    override fun goToLocator(locatorJson: String) {
        val locator =
            runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull() ?: return
        // Cover only on a cross-resource jump (same-resource jumps don't reload to lift the cover).
        if (locator.href.toString() != lastLocator?.href?.toString()) {
            val forward = progressMetrics(locator, null).bookProgress >=
                (lastLocator?.let { progressMetrics(it, latestVisualPage).bookProgress } ?: 0.0)
            beginChapterTransition(forward = forward)
        }
        scope.launch { navigator?.let { goChecked(it, locator) } }
    }

    /**
     * Runs a locator jump and handles Readium's Boolean failure signal. A failed jump immediately
     * lifts the transition cover, then confirms a return to the last valid locator. If even that
     * recovery fails, rebuilding the hosted navigator is safer than leaving a blank WebView.
     */
    private suspend fun goChecked(
        nav: EpubNavigatorFragment,
        target: Locator,
        recoverOnFailure: Boolean = true,
    ): Boolean {
        val fallback = lastLocator
        val succeeded = runCatching { nav.go(target, animated = false) }.getOrDefault(false)
        if (succeeded) return true

        clearChapterTransitionCover()
        if (recoverOnFailure && fallback != null && !target.matchesRestoreTarget(fallback)) {
            val fallbackStarted = runCatching { nav.go(fallback, animated = false) }.getOrDefault(false)
            val recovered = fallbackStarted &&
                withTimeoutOrNull(RESTORE_SETTLE_TIMEOUT_MS) {
                    nav.currentLocator.filter { it.matchesRestoreTarget(fallback) }.first()
                } != null
            if (!recovered) scheduleNavigatorRecreation(nav)
        }
        return false
    }

    private fun startRestoration(
        nav: EpubNavigatorFragment,
        target: Locator,
        recreateOnFailure: Boolean,
        verifyCurrentFirst: Boolean = false,
    ) {
        val generation = ++restorationGeneration
        restorationJob?.cancel()
        restoring = true
        restorationJob = scope.launch {
            var confirmed: Locator? = null
            var healthy = false
            try {
                if (verifyCurrentFirst) {
                    delay(RESUME_CHECK_DELAY_MS)
                    val current = runCatching { nav.currentLocator.first() }.getOrNull()
                    if (current != null && current.matchesRestoreTarget(target) && awaitRenderableContent(nav)) {
                        confirmed = current
                        healthy = true
                    }
                }
                if (!healthy) {
                    val started = goChecked(nav, target, recoverOnFailure = false)
                    if (started) {
                        confirmed = withTimeoutOrNull(RESTORE_SETTLE_TIMEOUT_MS) {
                            nav.currentLocator.filter { it.matchesRestoreTarget(target) }.first()
                        }
                        healthy = confirmed != null && awaitRenderableContent(nav)
                    }
                }
            } finally {
                if (generation == restorationGeneration && navigator === nav) {
                    restoring = false
                    confirmed?.let { updateCurrentLocator(it) }
                    if (!healthy) {
                        clearChapterTransitionCover()
                        if (recreateOnFailure) scheduleNavigatorRecreation(nav)
                    }
                }
            }
        }
    }

    private suspend fun awaitRenderableContent(nav: EpubNavigatorFragment): Boolean {
        repeat(NAVIGATOR_CONTENT_CHECK_ATTEMPTS) { attempt ->
            if (navigator !== nav || nav.view == null || !nav.isAdded) return false
            val result = runCatching { nav.evaluateJavascript(NAVIGATOR_CONTENT_CHECK_JS) }.getOrNull()
            if (result?.trim('"') == "true") return true
            delay(NAVIGATOR_CONTENT_CHECK_DELAY_MS * (attempt + 1))
        }
        return false
    }

    /** Waits for the next navigator pre-draw so the cover is removed only after WebView layout. */
    private suspend fun awaitNextPreDraw(nav: EpubNavigatorFragment): Boolean {
        val view = nav.view ?: return false
        if (!view.isAttachedToWindow) return false
        return suspendCancellableCoroutine { continuation ->
            val observer = view.viewTreeObserver
            lateinit var listener: ViewTreeObserver.OnPreDrawListener
            listener = ViewTreeObserver.OnPreDrawListener {
                if (observer.isAlive) observer.removeOnPreDrawListener(listener)
                if (continuation.isActive) continuation.resume(Unit)
                true
            }
            observer.addOnPreDrawListener(listener)
            continuation.invokeOnCancellation {
                if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            }
        }.let { true }
    }

    private fun scheduleNavigatorRecreation(expected: EpubNavigatorFragment) {
        scope.launch {
            if (navigator !== expected) return@launch
            val fragmentManager = hostedFragmentManager ?: return@launch
            val tag = hostedFragmentTag ?: return@launch
            if (fragmentManager.isDestroyed) return@launch
            val stale = fragmentManager.findFragmentByTag(tag) ?: return@launch
            val containerId = stale.id.takeIf { it != 0 } ?: return@launch
            beginChapterTransition()
            runCatching {
                fragmentManager.beginTransaction()
                    .remove(stale)
                    .commitNowAllowingStateLoss()
                val replacement = fragmentFactory.instantiate(context.classLoader, fragmentClassName)
                fragmentManager.fragmentFactory = fragmentFactory
                fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(containerId, replacement, tag)
                    .commitNowAllowingStateLoss()
                onFragmentHosted(fragmentManager, tag)
            }.onFailure { clearChapterTransitionCover() }
        }
    }

    private fun clearChapterTransitionCover() {
        beginChapterTransition()
        scheduleCriticalStyling()
    }

    override fun close() {
        restorationGeneration++
        restorationJob?.cancel()
        restorationJob = null
        locatorCollectionJob?.cancel()
        locatorCollectionJob = null
        scrollProgressJob?.cancel()
        scrollProgressJob = null
        styleJob?.cancel()
        styleJob = null
        hostedFragmentManager = null
        hostedFragmentTag = null
        scope.cancel()
        runCatching { searchIterator?.close() }
        searchIterator = null
        tts?.shutdown()
        tts = null
        runCatching { publication.close() }
    }

    private fun requestScrollProgress(locator: Locator) {
        val nav = navigator ?: return
        val href = locator.href.toString()
        scrollProgressJob?.cancel()
        scrollProgressJob = scope.launch {
            delay(SCROLL_PROGRESS_DEBOUNCE_MS)
            if (navigator !== nav || currentSettings.layout != ReaderLayout.Scroll) return@launch
            val result = runCatching { nav.evaluateJavascript(SCROLL_PROGRESS_JS) }.getOrNull()
            val progression = result?.trim('"')?.toDoubleOrNull() ?: return@launch
            if (lastLocator?.href?.toString() != href) return@launch
            updateCurrentLocator(
                locator = locator,
                measuredScrollProgress = progression,
                queryScrollProgress = false,
            )
        }
    }

    private fun Locator.matchesRestoreTarget(target: Locator): Boolean {
        if (href != target.href) return false
        val actual = locations.progression ?: locations.totalProgression
        val expected = target.locations.progression ?: target.locations.totalProgression
        return actual == null || expected == null || kotlin.math.abs(actual - expected) < 0.01
    }

    override fun speak(text: String) {
        val existing = tts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yomu-reader")
            return
        }
        // First use: TTS init is async, so remember the text and speak once the engine is ready.
        pendingSpeak = text
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                pendingSpeak?.let {
                    tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "yomu-reader")
                }
            }
            pendingSpeak = null
        }
    }

    private fun copySelection(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Selection", text))
    }

    private fun shareSelection(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    private fun searchWeb(text: String) {
        val uri = Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("q", text)
            .build()
        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .also { it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            customTab.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            // The device has no activity capable of handling web links.
        }
    }

    // Builds the injected JS for the scroll-mode rubberband chapter gesture. Idempotent per document
    // (guarded by window.__yomuOverscroll); re-running just updates enabled/hasPrev/hasNext. A
    // capture-phase touch listener only takes over (preventDefault) while over-pulling at the very
    // top/bottom, so normal scrolling, taps, links and text selection are untouched. Colours derive
    // from the active reading theme so the hint pill matches the page.
    private fun overscrollJs(
        enabled: Boolean,
        hasPrev: Boolean,
        hasNext: Boolean,
        settings: ReaderSettings,
        topHintOffsetPx: Int,
    ): String {
        val text = settings.textArgb.toInt()
        val tr = (text shr 16) and 0xFF
        val tg = (text shr 8) and 0xFF
        val tb = text and 0xFF
        val pageBg = settings.backgroundArgb.toInt()
        val br = (pageBg shr 16) and 0xFF
        val bgG = (pageBg shr 8) and 0xFF
        val bb = pageBg and 0xFF
        val fill = "rgba($tr,$tg,$tb,0.12)"
        val border = "rgba($tr,$tg,$tb,0.30)"
        val accent = "rgb($tr,$tg,$tb)"
        val onAccent = "rgb($br,$bgG,$bb)"
        val topHintOffset = topHintOffsetPx.coerceAtLeast(0)
        return """
            (function() {
              if (window.__yomuOverscroll) {
                window.__yomuOverscroll.update($enabled, $hasPrev, $hasNext, $topHintOffset);
                return;
              }
              var st = { enabled: $enabled, hasPrev: $hasPrev, hasNext: $hasNext, topHint: $topHintOffset,
                         startY: 0, dragging: false, engaged: false, dir: 0, engageY: 0,
                         armed: false, fired: false, svgDir: 0, raf: 0,
                         targetOffset: 0, renderedOffset: 0, hintScale: 0.7 };
              // THRESHOLD: armed (release navigates) once the stretch passes this. MAX: asymptotic
              // ceiling. INITIAL: stretch-per-pixel near the boundary (the curve eases off from here to
              // MAX so there's no hard wall).
              var THRESHOLD = 80, MAX = 170, INITIAL = 0.6;
              var STIFFNESS = 190, DAMPING = 28;
              var SVGNS = 'http://www.w3.org/2000/svg';
              var body = document.body;
              var baseTransform = body ? body.style.transform : '';
              var baseWillChange = body ? body.style.willChange : '';
              var hideTimer = 0;
              // 'contain' only stops scroll-chaining to the parent — Chromium/Android WebView still
              // plays its own native overscroll glow/rubber-band content-stretch at the boundary, which
              // fires the instant the finger pulls past the edge (before our touchmove handler below
              // even gets to preventDefault it). 'none' suppresses that native effect so the fixed
              // hint is the only gesture feedback. Set on both html and body since Android WebView
              // can attach its overscroll effect
              // to either depending on which one ends up as the scrolling element.
              document.documentElement.style.overscrollBehaviorY = 'none';
              if (document.body) document.body.style.overscrollBehaviorY = 'none';
              var hint = document.createElement('div');
              hint.id = 'yomu-overscroll';
              hint.setAttribute('data-yomu-hidden', 'true');
              hint.setAttribute('aria-hidden', 'true');
              hint.style.cssText = [
                'position:fixed', 'left:50%', 'z-index:2147483647', 'box-sizing:border-box',
                'width:42px', 'height:42px', 'border-radius:50%',
                'display:none', 'align-items:center', 'justify-content:center',
                'font:600 22px system-ui,-apple-system,Roboto,sans-serif', 'line-height:1',
                'background:$fill', 'border:1px solid $border', 'color:$accent',
                'opacity:0', 'transform:translateX(-50%) scale(0.7)',
                // Transform is intentionally NOT transitioned: the pill's scale tracks the pull 1:1
                // during the drag. Only the colour/opacity ease (incl. the fade-out on release).
                'transition:opacity .18s ease, background .18s ease, color .18s ease',
                'pointer-events:none', '-webkit-tap-highlight-color:transparent'
              ].join(';');
              // A fixed element is valid inside EPUB XHTML body and stays outside normal flow. Adding
              // it directly under <html> made WebView include it in root overflow calculations.
              if (body) body.appendChild(hint);
              // Build the arrow with createElementNS so it renders in EPUB XHTML documents (where an
              // innerHTML SVG string would land in the wrong namespace and not draw). currentColor
              // inherits the pill's colour so it flips with the armed state.
              function setArrow(up) {
                var svg = document.createElementNS(SVGNS, 'svg');
                svg.setAttribute('width', '22'); svg.setAttribute('height', '22');
                svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('fill', 'none');
                svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '2.4');
                svg.setAttribute('stroke-linecap', 'round'); svg.setAttribute('stroke-linejoin', 'round');
                var p1 = document.createElementNS(SVGNS, 'path');
                p1.setAttribute('d', up ? 'M12 19V5' : 'M12 5v14');
                var p2 = document.createElementNS(SVGNS, 'path');
                p2.setAttribute('d', up ? 'M6 11l6-6 6 6' : 'M6 13l6 6 6-6');
                svg.appendChild(p1); svg.appendChild(p2);
                while (hint.firstChild) hint.removeChild(hint.firstChild);
                hint.appendChild(svg);
              }
              function sc() { return document.scrollingElement || document.documentElement; }
              function maxScroll() { var e = sc(); return Math.max(0, e.scrollHeight - e.clientHeight); }
              function atTop() { return sc().scrollTop <= 0; }
              function atBottom() { return sc().scrollTop >= maxScroll() - 1; }
              function hasSel() { var s = window.getSelection(); return s && s.toString().length > 0; }
              // Asymptotic resistance: linear (slope INITIAL) at the boundary, easing smoothly toward MAX
              // so a firm pull meets diminishing give instead of the old hard cap.
              function resist(raw) {
                if (raw <= 0) return 0;
                return MAX * (1 - 1 / (1 + raw * INITIAL / MAX));
              }
              function bodyTransform(off) {
                var base = baseTransform && baseTransform !== 'none' ? baseTransform + ' ' : '';
                return base + 'translate3d(0,' + off + 'px,0)';
              }
              function renderHintTransform() {
                hint.style.transform = 'translate3d(-50%,' + (-st.renderedOffset) + 'px,0) scale(' + st.hintScale + ')';
              }
              function renderBody() {
                st.raf = 0;
                st.renderedOffset = st.targetOffset;
                if (body) {
                  body.style.willChange = 'transform';
                  body.style.transform = bodyTransform(st.renderedOffset);
                }
                renderHintTransform();
              }
              function setBody(off) {
                st.targetOffset = off;
                if (!st.raf) st.raf = requestAnimationFrame(renderBody);
              }
              function clearTransform() {
                if (st.raf) { cancelAnimationFrame(st.raf); st.raf = 0; }
                st.targetOffset = 0; st.renderedOffset = 0;
                if (body) {
                  body.style.transform = baseTransform;
                  body.style.willChange = baseWillChange;
                }
                renderHintTransform();
              }
              function showHint() {
                if (hideTimer) { window.clearTimeout(hideTimer); hideTimer = 0; }
                hint.setAttribute('data-yomu-hidden', 'false');
                hint.setAttribute('aria-hidden', 'false');
                hint.style.setProperty('display', 'flex', 'important');
              }
              function fadeHint() {
                hint.style.opacity = 0;
                hint.style.background = '$fill';
                hint.style.color = '$accent';
                st.hintScale = 0.7;
                renderHintTransform();
                if (hideTimer) window.clearTimeout(hideTimer);
                hideTimer = window.setTimeout(function() {
                  hint.setAttribute('data-yomu-hidden', 'true');
                  hint.setAttribute('aria-hidden', 'true');
                  hint.style.setProperty('display', 'none', 'important');
                  hideTimer = 0;
                }, 180);
              }
              function updateHint(pull) {
                showHint();
                var p = Math.min(pull / THRESHOLD, 1);
                hint.style.opacity = p;
                if (st.armed) {
                  hint.style.background = '$accent';
                  hint.style.color = '$onAccent';
                  st.hintScale = 1;
                } else {
                  hint.style.background = '$fill';
                  hint.style.color = '$accent';
                  st.hintScale = 0.7 + 0.3 * p;
                }
                renderHintTransform();
              }
              function springBack() {
                if (st.raf) { cancelAnimationFrame(st.raf); st.raf = 0; }
                var x = st.renderedOffset, v = 0;
                var last = (window.performance && performance.now) ? performance.now() : Date.now();
                function frame(t) {
                  var now = t || Date.now();
                  var dt = Math.min((now - last) / 1000, 0.032); last = now;
                  var a = -STIFFNESS * x - DAMPING * v;
                  v += a * dt; x += v * dt;
                  if (Math.abs(x) < 0.35 && Math.abs(v) < 5) { clearTransform(); return; }
                  st.renderedOffset = x; st.targetOffset = x;
                  if (body) body.style.transform = bodyTransform(x);
                  renderHintTransform();
                  st.raf = requestAnimationFrame(frame);
                }
                st.raf = requestAnimationFrame(frame);
              }
              // Full teardown (used when the gesture is disabled or a fresh touch starts).
              function reset() {
                clearTransform();
                fadeHint();
                st.dragging = false; st.engaged = false; st.dir = 0; st.armed = false; st.svgDir = 0;
              }
              window.addEventListener('touchstart', function(e) {
                if (st.raf || st.renderedOffset) clearTransform();
                if (!st.enabled || e.touches.length !== 1 || hasSel()) {
                  st.dragging = false; st.engaged = false; return;
                }
                st.startY = e.touches[0].clientY;
                st.dragging = true; st.engaged = false; st.dir = 0;
                st.armed = false; st.fired = false;
              }, { capture: true, passive: true });
              window.addEventListener('touchmove', function(e) {
                if (!st.dragging || !st.enabled) return;
                var y = e.touches[0].clientY;
                if (!st.engaged) {
                  // Stay out of the way until the finger reaches a chapter edge and pulls past it; only
                  // then take over. Anchoring at this moment (engageY) means the stretch starts from
                  // zero — no jump as the native scroll slack at the chapter end hands off to us.
                  var dy = y - st.startY;
                  var cand = (dy > 0 && atTop() && st.hasPrev) ? -1
                           : (dy < 0 && atBottom() && st.hasNext) ? 1 : 0;
                  if (cand === 0) return;
                  st.engaged = true; st.dir = cand; st.engageY = y;
                  showHint();
                  if (st.svgDir !== cand) { st.svgDir = cand; setArrow(cand < 0); }
                  if (cand < 0) { hint.style.top = st.topHint + 'px'; hint.style.bottom = 'auto'; }
                  else { hint.style.bottom = '18px'; hint.style.top = 'auto'; }
                }
                e.preventDefault();
                // Clamp at 0: pulling back past the boundary just relaxes the stretch — it never flips
                // direction or resumes scrolling mid-gesture, which is what used to cause the jitter.
                var raw = Math.max(0, (st.dir < 0) ? (y - st.engageY) : (st.engageY - y));
                var pull = resist(raw);
                setBody(-st.dir * pull);
                st.armed = pull >= THRESHOLD;
                updateHint(pull);
              }, { capture: true, passive: false });
              window.addEventListener('touchend', function() {
                if (!st.engaged) { fadeHint(); st.dragging = false; return; }
                // Snapshot the gesture, then clear state so nothing lingers regardless of which branch
                // runs below.
                var dir = st.dir, armed = st.armed, fired = st.fired;
                st.dragging = false; st.engaged = false; st.dir = 0; st.armed = false; st.svgDir = 0;
                // Always hide the arrow on release. Before navigating this matters: the next chapter can
                // render in the same WebView document, which would otherwise leave the pill pinned at
                // the edge.
                fadeHint();
                if (dir && armed && !fired) {
                  st.fired = true;
                  window.location.href = dir < 0 ? '$PREV_CHAPTER_URL' : '$NEXT_CHAPTER_URL';
                  // Let Yomu's opaque chapter-change cover appear before cleaning the old page.
                  window.setTimeout(clearTransform, 180);
                  return;
                }
                springBack();
              }, { capture: true, passive: true });
              window.addEventListener('touchcancel', function() {
                if (!st.engaged) { reset(); return; }
                st.dragging = false; st.engaged = false; st.dir = 0; st.armed = false; st.svgDir = 0;
                fadeHint();
                springBack();
              }, { capture: true, passive: true });
              window.__yomuOverscroll = {
                update: function(en, hp, hn, topHint) {
                  st.enabled = en; st.hasPrev = hp; st.hasNext = hn; st.topHint = topHint;
                  if (!en) reset();
                }
              };
            })();
        """.trimIndent()
    }

    // Layout + size + theme colours (explicit bg/text so it matches the chrome) + the bundled font.
    private fun ReaderSettings.toPreferences(): EpubPreferences = EpubPreferences(
        scroll = layout == ReaderLayout.Scroll,
        fontSize = fontScale.toDouble(),
        // The family name must match a registered declaration (bundled fonts) OR a custom @font-face
        // we inject inline (custom fonts) — see applyCustomFontInline().
        fontFamily = FontFamily(customFont?.family ?: font.cssFamily),
        // Advanced typography (active because publisherStyles is disabled). null = engine default.
        lineHeight = lineHeight?.toDouble(),
        pageMargins = pageMargins?.toDouble(),
        paragraphSpacing = paragraphSpacing?.toDouble(),
        textAlign = when (textAlign) {
            ReaderTextAlign.Default -> null
            ReaderTextAlign.Left -> ReadiumTextAlign.LEFT
            ReaderTextAlign.Justify -> ReadiumTextAlign.JUSTIFY
        },
        // Base appearance picks sensible defaults (links etc.), but the explicit bg/text colours
        // win so the page exactly matches the Yomu chrome (no status-bar seam). A theme's own bg
        // would otherwise override them, which is what caused the mismatch.
        theme = if (isLightBackground) Theme.LIGHT else Theme.DARK,
        backgroundColor = ReadiumColor(backgroundArgb.toInt()),
        textColor = ReadiumColor(textArgb.toInt()),
        publisherStyles = false,
    )

    private companion object {
        // Tap-zone boundaries as fractions of the page width.
        const val TAP_LEFT = 0.33f
        const val TAP_RIGHT = 0.67f

        // Menu item ids for the actions we rebuild in the text-selection menu.
        const val MENU_COPY = 1
        const val MENU_LOOK_UP = 2
        const val MENU_SPEAK = 3
        const val MENU_SHARE = 4
        const val MENU_HIGHLIGHT = 5
        const val MENU_SEARCH_WEB = 6

        // Decoration group name for user highlights.
        const val HIGHLIGHTS_GROUP = "highlights"

        // Separate decoration group + tint for in-book search hits (replaced wholesale per query).
        const val SEARCH_GROUP = "search"
        val SEARCH_TINT = 0xFFE7C75B.toInt()

        // Cap on collected search hits — bounds memory and scan time on large books.
        const val MAX_SEARCH_RESULTS = 150

        // Confirmation is the primary completion signal; timeout is only a safety escape hatch for
        // malformed publications or a navigator that never emits after go().
        const val RESTORE_SETTLE_TIMEOUT_MS = 5_000L
        const val RESUME_CHECK_DELAY_MS = 300L
        const val NAVIGATOR_CONTENT_CHECK_ATTEMPTS = 6
        const val NAVIGATOR_CONTENT_CHECK_DELAY_MS = 150L
        const val SCROLL_PROGRESS_DEBOUNCE_MS = 48L
        const val NAVIGATOR_CONTENT_CHECK_JS =
            "(function(){var b=document&&document.body;" +
                "return !!(b&&(b.childElementCount>0||(b.textContent||'').trim().length>0));})()"
        const val SCROLL_PROGRESS_JS =
            "(function(){" +
                "var e=document.scrollingElement||document.documentElement||document.body;" +
                "if(!e)return null;" +
                "var top=Math.max(window.pageYOffset||0,e.scrollTop||0);" +
                "var max=Math.max(0,e.scrollHeight-e.clientHeight);" +
                "return max<=1?1:Math.max(0,Math.min(1,top/max));" +
                "})()"

        // Custom-scheme URLs behind the scroll-mode rubberband overscroll gesture (intercepted in
        // onExternalLinkActivated).
        const val NEXT_CHAPTER_URL = "yomu://next-chapter"
        const val PREV_CHAPTER_URL = "yomu://prev-chapter"

        // Removes the injected custom-font @font-face when switching back to a bundled font.
        val CUSTOM_FONT_CLEAR_JS =
            "(function(){var s=document.getElementById('yomu-custom-font');" +
                "if(s&&s.parentNode)s.parentNode.removeChild(s);})();"

        // Retry budget for evaluateJavascript: it silently no-ops until the resource's page fragment
        // is current. ~4 tries over a rising backoff (80,160,240,320ms) covers a slow paint.
        const val JS_INJECT_ATTEMPTS = 4
        const val JS_INJECT_RETRY_DELAY_MS = 80L

        // Safety cap: reveal a transitioning chapter even if its onPageLoaded never arrives, so a
        // failed/stalled load can't leave the reader stuck behind the transition cover.
        const val STYLE_REVEAL_TIMEOUT_MS = 3000L

        const val CHAPTER_START_FALLBACK_PADDING_DP = 20f
        const val CHAPTER_START_EXTRA_PADDING_DP = 4f
        const val RUBBERBAND_EDGE_MARGIN_DP = 18f

        fun readerPaletteJs(settings: ReaderSettings): String {
            val styleId = "yomu-reader-palette"
            val palette = settings.colorPalette
            val text = palette.textArgb.toCssRgb()
            val secondary = palette.secondaryTextArgb.toCssRgb()
            val selection = palette.selectionArgb.toCssRgb()
            val link = palette.linkArgb.toCssRgb()
            val border = palette.borderArgb.toCssRgb()
            val css = """
                ::selection { background: $selection !important; color: $text !important; }
                a, a:link, a:visited { color: $link !important; }
                small, figcaption, caption, aside, .footnote, .endnote { color: $secondary !important; }
                hr, table, th, td, blockquote { border-color: $border !important; }
            """.trimIndent()
            return """
                (function() {
                  var s = document.getElementById('$styleId');
                  if (!s) {
                    s = document.createElement('style');
                    s.id = '$styleId';
                    (document.head || document.documentElement).appendChild(s);
                  }
                  s.textContent = ${JSONObject.quote(css)};
                })();
            """.trimIndent()
        }

        private fun Long.toCssRgb(): String = "#" + (this and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()

        // Overrides Readium CSS's scroll-mode body{max-width:40rem!important} so scroll mode fills
        // the width like paged mode does. In immersive scroll mode it also neutralizes Readium's
        // permanent safe-area top padding and inserts a chapter-start spacer into the content itself.
        fun scrollCssJs(
            immersiveScroll: Boolean,
            chapterStartPaddingPx: Int,
            chapterEndPaddingPx: Int,
        ): String {
            val spacerHeight = chapterStartPaddingPx.coerceAtLeast(0)
            val endSpacerHeight = chapterEndPaddingPx.coerceAtLeast(0)
            val topPaddingFix = if (immersiveScroll) {
                """
                ':root[style*="readium-scroll-on"]{--RS__scrollPaddingTop:0px!important;--RS__scrollPaddingBottom:0px!important}',
                ':root[style*="readium-scroll-on"] body{padding-top:0!important;padding-bottom:0!important}',
                """.trimIndent()
            } else {
                ""
            }
            val spacerCss = if (immersiveScroll && spacerHeight > 0) {
                "':root[style*=\"readium-scroll-on\"] #yomu-chapter-start-padding{display:block!important;height:${spacerHeight}px!important;min-height:${spacerHeight}px!important;margin:0!important;padding:0!important;border:0!important;pointer-events:none!important;}'"
            } else {
                "''"
            }
            return """
            (function() {
              var styleId = 'yomu-scroll-css';
              var spacerId = 'yomu-chapter-start-padding';
              var endSpacerId = 'yomu-chapter-end-padding';
              var s = document.getElementById(styleId);
              if (!s) {
                s = document.createElement('style');
                s.id = styleId;
                (document.head || document.documentElement).appendChild(s);
              }
              s.textContent = [
                ':root[style*="readium-scroll-on"] body{max-width:none!important}',
                // Short chapters that don't fill the viewport leave the page background covering only
                // the text height — so the area below reads as empty/odd and the overscroll
                // (rubberband) bottom detection sits mid-screen. Force the page to at least fill the
                // viewport so the reading background covers it and bottom-of-chapter is the screen edge.
                ':root[style*="readium-scroll-on"] body{min-height:100vh!important}',
                // Opacity alone leaves the fixed overscroll SVG rendered in WebView's scroll-height
                // accounting. Collapse it completely while idle in every scroll presentation.
                ':root[style*="readium-scroll-on"] #yomu-overscroll[data-yomu-hidden="true"]{display:none!important;width:0!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important;overflow:hidden!important}',
                $topPaddingFix
                $spacerCss
              ].filter(Boolean).join('\n');

              var body = document.body;
              if (!body) return;
              var staleNext = document.getElementById('yomu-next-chapter');
              if (staleNext && staleNext.parentNode) staleNext.parentNode.removeChild(staleNext);
              var spacer = document.getElementById(spacerId);
              var endSpacer = document.getElementById(endSpacerId);
              if ($immersiveScroll && $spacerHeight > 0) {
                if (!spacer) {
                  spacer = document.createElementNS(body.namespaceURI || 'http://www.w3.org/1999/xhtml', 'div');
                  spacer.id = spacerId;
                  spacer.setAttribute('aria-hidden', 'true');
                }
                spacer.style.cssText = 'display:block;height:${spacerHeight}px;min-height:${spacerHeight}px;margin:0;padding:0;border:0;pointer-events:none;';
                if (body.firstChild !== spacer) body.insertBefore(spacer, body.firstChild);
              } else if (spacer && spacer.parentNode) {
                spacer.parentNode.removeChild(spacer);
              }
              if ($endSpacerHeight > 0) {
                if (!endSpacer) {
                  endSpacer = document.createElementNS(body.namespaceURI || 'http://www.w3.org/1999/xhtml', 'div');
                  endSpacer.id = endSpacerId;
                  endSpacer.setAttribute('aria-hidden', 'true');
                }
                endSpacer.style.cssText = 'display:block;height:${endSpacerHeight}px;min-height:${endSpacerHeight}px;margin:0;padding:0;border:0;pointer-events:none;';
                if (body.lastChild !== endSpacer) body.appendChild(endSpacer);
              } else if (endSpacer && endSpacer.parentNode) {
                endSpacer.parentNode.removeChild(endSpacer);
              }
            })();
            """.trimIndent()
        }

        fun viewportFitJs(enabled: Boolean): String = """
            (function() {
              var m = document.querySelector('meta[name="viewport"]');
              if (!m) {
                if (!$enabled) return;
                m = document.createElement('meta');
                m.setAttribute('name', 'viewport');
                (document.head || document.documentElement).appendChild(m);
              }
              var c = m.getAttribute('content') || '';
              var parts = c.split(',').map(function(part) { return part.trim(); })
                .filter(function(part) { return part && part.indexOf('viewport-fit') !== 0; });
              if ($enabled) parts.push('viewport-fit=cover');
              m.setAttribute('content', parts.join(', '));
            })();
        """.trimIndent()
    }
}
