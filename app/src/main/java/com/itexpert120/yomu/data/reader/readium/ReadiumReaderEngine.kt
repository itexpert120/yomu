package com.itexpert120.yomu.data.reader.readium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
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
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.readium.r2.shared.publication.services.positions
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
import kotlin.math.roundToInt
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign

@Singleton
class ReadiumReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderEngine {

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

    override suspend fun open(
        filePath: String,
        initialLocatorJson: String?,
        initialSettings: ReaderSettings,
    ): ReaderSession? {
        val publication = openPublication(filePath) ?: return null
        return ReadiumReaderSession(
            context,
            publication,
            initialLocatorJson,
            initialSettings,
        )
    }

    override suspend fun tableOfContents(filePath: String): List<ReaderTocItem> {
        val publication = openPublication(filePath) ?: return emptyList()
        return try {
            buildList {
                flattenToc(
                    publication,
                    publication.tableOfContents,
                    depth = 0,
                    out = this,
                )
            }
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

    // Flatten the TOC tree depth-first, preserving reading order and recording nesting depth.
    private fun flattenToc(
        publication: Publication,
        links: List<Link>,
        depth: Int,
        out: MutableList<ReaderTocItem>,
    ) {
        for (link in links) {
            val title = link.title?.trim()?.takeIf { it.isNotEmpty() }
            if (title != null) {
                val locator = publication.locatorFromLink(link)
                out += ReaderTocItem(
                    // Key on the resource href (from the resolved locator) so it matches the
                    // reader's ReaderLocator.href and read-state tracks as the user reads.
                    id = locator?.href?.toString() ?: link.url().toString(),
                    title = title,
                    locatorJson = locator?.toJSON()?.toString(),
                    depth = depth,
                )
            }
            if (link.children.isNotEmpty()) {
                flattenToc(publication, link.children, depth + 1, out)
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private class ReadiumReaderSession(
    private val context: Context,
    private val publication: Publication,
    initialLocatorJson: String?,
    initialSettings: ReaderSettings,
) : ReaderSession {

    override val title: String = publication.metadata.title ?: "Reading"

    private val _currentLocator = MutableStateFlow<ReaderLocator?>(null)
    override val currentLocator: StateFlow<ReaderLocator?> = _currentLocator.asStateFlow()

    // Flips true on the navigator's first onPageLoaded (real first paint) — the loading gate.
    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    // Flips false at the start of a chapter change and true once the new resource's layout CSS (incl.
    // the immersive chapter-start top padding) has applied — so the page is revealed already padded
    // instead of jolting when the spacer pops in a few frames after first paint.
    private val _styled = MutableStateFlow(false)
    override val styled: StateFlow<Boolean> = _styled.asStateFlow()
    private val _transitionForward = MutableStateFlow(true)
    override val transitionForward: StateFlow<Boolean> = _transitionForward.asStateFlow()
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

    // Latest engine locator, used for reading-order (chapter) navigation and for restoring position
    // after a re-host (config change) so the reader doesn't snap back to where the book was opened.
    private var lastLocator: Locator? = null

    // True while restoring [lastLocator] into a freshly re-hosted fragment; the fragment's transient
    // initial-locator emissions are ignored during this window so they can't clobber saved progress.
    private var restoring = false

    // Per-resource reading-position progressions. Used as a scroll-mode fallback for "pages left";
    // paged mode uses Readium's visual page callback so the count changes on every page turn.
    private var positionsByHref: Map<String, List<Double>>? = null

    private var latestVisualPage: VisualPageState? = null

    // The resource we last injected the scroll-width CSS override into (re-injected per resource).
    private var lastStyledHref: String? = null

    // Settings requested before the navigator exists; applied once it is hosted.
    private var pendingSettings: ReaderSettings? = initialSettings

    // Latest settings, read by the tap handler (e.g. for the center-tap-opens-sheet toggle).
    private var currentSettings: ReaderSettings = initialSettings

    // Lazily-created TTS for the "Read aloud" selection action; reused across taps, shut down on close.
    private var tts: TextToSpeech? = null
    private var pendingSpeak: String? = null

    private val initialLocator: Locator? = initialLocatorJson
        ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    private val navigatorFactory = EpubNavigatorFactory(publication)

    private val listener = object : EpubNavigatorFragment.Listener {
        override fun onExternalLinkActivated(url: AbsoluteUrl) {
            // Custom-scheme links injected by the scroll-mode rubberband overscroll gesture route
            // here as external links; intercept them for chapter navigation instead of opening a
            // browser. Pull-to-previous lands at the END of the previous chapter for continuity.
            val s = url.toString()
            when {
                s.startsWith(PREV_CHAPTER_URL) -> goToPreviousResourceEnd()
                s.startsWith(NEXT_CHAPTER_URL) -> nextChapter()
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

    // Inject the scroll-width CSS fix the moment each resource finishes loading — earlier than the
    // currentLocator settle, so the page doesn't briefly flash at the narrow default width first.
    private val paginationListener = object : EpubNavigatorFragment.PaginationListener {
        override fun onPageLoaded() {
            // First real paint — release the "Opening…" gate. Idempotent (fires per resource).
            _ready.value = true
            // Readium builds a fresh WebView per resource, so every script must be re-injected here —
            // this is the one authoritative per-resource hook.
            scope.launch {
                // Apply the layout CSS (incl. the immersive chapter-start top padding) and reveal the
                // page only AFTER it lands, so the padding is part of the first visible frame instead
                // of shoving the content down a few frames later. Best-effort: reveal anyway if the
                // injection can't be confirmed, rather than leaving the page covered.
                evalWithRetry(scrollCssJsForCurrent())
                // Embed the custom font (if any) before revealing, so text paints in it directly
                // rather than flashing a fallback and re-flowing.
                applyCustomFontInline()
                revealWatchdog?.cancel()
                _styled.value = true
                // Non-visual injections can follow once the page is shown.
                injectViewportFit()
                // Overscroll used to be injected ONLY from the currentLocator path (gated on href
                // change), so a chapter that loaded without a fresh locator emission could be left
                // without the rubberband gesture; inject it here too.
                injectOverscroll()
                applyScrollbars(currentSettings)
                clearImmersiveScrollTopPadding()
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

    override val fragmentFactory: FragmentFactory =
        navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = initialSettings.toPreferences(),
            listener = listener,
            paginationListener = paginationListener,
            configuration = EpubNavigatorFragment.Configuration {
                // In scroll mode a horizontal swipe jumps a whole resource (chapter), which users
                // hit accidentally while scrolling vertically. Disable it: chapters are advanced via
                // the next-chapter button / controls sheet / TOC (programmatic, unaffected). This is
                // the engine-level guard the InputListener.onDrag swallow couldn't provide. Paged
                // mode is untouched — the flag only gates scroll-mode swipes.
                disablePageTurnsWhileScrolling = true

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
                        menu.add(Menu.NONE, MENU_SPEAK, 3, "Read aloud")
                        menu.add(Menu.NONE, MENU_SHARE, 4, "Share")
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        val id = item?.itemId ?: return false
                        if (id !in setOf(
                                MENU_COPY,
                                MENU_HIGHLIGHT,
                                MENU_LOOK_UP,
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
                                    MENU_SPEAK -> speakSelection(text)
                                    MENU_SHARE -> shareSelection(text)
                                    MENU_LOOK_UP -> _lookUpRequests.tryEmit(text)
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
                        addSource("fonts/Lora-Regular.ttf", preload = true)
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
                        addSource("fonts/Karla-Regular.ttf", preload = true)
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
                        addSource("fonts/Rubik-Regular.ttf", preload = true)
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
                        addSource("fonts/Cardo-Regular.ttf", preload = true)
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
                        addSource("fonts/Nunito-Regular.ttf", preload = true)
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
                        addSource("fonts/Merriweather-Regular.ttf", preload = true)
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
        val nav = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment ?: return
        navigator = nav
        pendingSettings?.let { nav.submitPreferences(it.toPreferences()) }
        // Index page boundaries per resource once (off the main path) for "pages left in chapter".
        if (positionsByHref == null) {
            scope.launch {
                positionsByHref = runCatching {
                    publication.positions()
                        .groupBy { it.href.toString() }
                        .mapValues { (_, locs) ->
                            locs.mapNotNull { it.locations.progression }.sorted()
                        }
                }.getOrNull()
            }
        }
        // On a re-host (config change), the fresh fragment opens at the original initialLocator. If we
        // already have a position, restore it so the reader doesn't snap back to an earlier chapter —
        // and ignore the fragment's transient emissions until then so they can't overwrite progress.
        val restoreTarget = lastLocator
        if (restoreTarget != null) {
            restoring = true
            lastStyledHref = null
        }
        scope.launch {
            nav.currentLocator.collect { locator ->
                updateCurrentLocator(locator)
            }
        }
        // Restore the latest reading position into the freshly re-hosted fragment, then resume
        // reporting locator changes (the suppression above kept the transient open-position out).
        if (restoreTarget != null) {
            scope.launch {
                runCatching { nav.go(restoreTarget, animated = false) }
                restoring = false
            }
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

        // Re-apply any highlights / search underlines requested before the navigator existed.
        if (pendingHighlights.isNotEmpty()) renderHighlights(pendingHighlights)
        if (pendingSearchDecorations.isNotEmpty()) renderSearchDecorations(pendingSearchDecorations)
    }

    override fun applyHighlights(highlights: List<ReaderHighlight>) {
        pendingHighlights = highlights
        renderHighlights(highlights)
    }

    private fun renderHighlights(highlights: List<ReaderHighlight>) {
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
        val iterator = publication.search(query) ?: return emptyList()
        searchIterator = iterator
        // Drain pages off the main thread, capped so a huge book can't produce thousands of rows.
        return withContext(Dispatchers.IO) {
            val out = ArrayList<ReaderSearchResult>()
            while (out.size < MAX_SEARCH_RESULTS) {
                // getOrNull() yields null at the end of the publication or on a read error — stop either way.
                val page = iterator.next().getOrNull() ?: break
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

    private fun updateCurrentLocator(locator: Locator, visualPage: VisualPageState? = null) {
        if (restoring) return
        visualPage?.let { latestVisualPage = it }
        lastLocator = locator
        val hrefStr = locator.href.toString()
        // Scroll mode forces body{max-width:40rem!important} in Readium CSS, which our
        // RsProperties maxLineLength can't override (it's set per-resource on body). Inject a
        // style override so scroll mode fills the width too. Re-applied per resource.
        val order = publication.readingOrder
        val index = order.indexOfFirst { it.url().toString() == hrefStr }
        val hasNext = index in 0 until order.lastIndex
        // Chapter-weighted whole-book progress: advances smoothly even through an early chapter of a
        // many-chapter book (where Readium's totalProgression barely moves).
        val bookProgress = if (index >= 0 && order.isNotEmpty()) {
            ((index + (locator.locations.progression ?: 0.0)) / order.size).coerceIn(0.0, 1.0)
        } else {
            null
        }
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
        // In scroll mode there is no visual page index, so fall back to Readium positions.
        val pagesLeft = visualPagesLeft ?: positionsByHref?.get(hrefStr)?.let { positions ->
            val prog = locator.locations.progression ?: 0.0
            positions.count { it > prog + 1e-6 }
        }
        if (hrefStr != lastStyledHref) {
            lastStyledHref = hrefStr
            scope.launch {
                injectScrollCss()
                injectViewportFit()
                clearImmersiveScrollTopPadding()
            }
            // Arm the scroll-mode rubberband chapter gesture.
            injectOverscroll()
        }
        _currentLocator.value = ReaderLocator(
            locatorJson = locator.toJSON().toString(),
            totalProgression = locator.locations.totalProgression,
            chapterTitle = locator.title,
            href = hrefStr,
            chapterProgression = locator.locations.progression,
            hasPreviousChapter = index > 0,
            hasNextChapter = hasNext,
            bookProgress = bookProgress,
            chapterPagesLeft = pagesLeft,
        )
    }
    override fun applySettings(settings: ReaderSettings) {
        pendingSettings = settings
        currentSettings = settings
        navigator?.submitPreferences(settings.toPreferences())
        // Re-toggle/re-theme the native scrollbar (it's scroll-mode only and tracks the text colour).
        applyScrollbars(settings)
        // Refresh scroll-only CSS overrides when layout or immersive mode changes.
        injectScrollCss()
        // Toggle viewport-fit=cover with immersive scroll mode so normal reading keeps status-bar space.
        injectViewportFit()
        clearImmersiveScrollTopPadding(settings)
        // Re-arm or tear down the rubberband gesture on a scroll<->paged or theme change.
        injectOverscroll()
        // Embed/clear the custom font live so switching fonts in the sheet applies without reopening.
        scope.launch { applyCustomFontInline() }
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
        val key = "${ref.family}|${ref.regularPath}|${ref.italicPath}"
        if (key == customFontKey && customFontCss != null) return customFontCss
        val css = withContext(Dispatchers.IO) { buildFontFaceCss(ref) }
        customFontKey = key
        customFontCss = css
        return css
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
        val immersiveScroll = currentSettings.immersiveChrome &&
            currentSettings.layout == ReaderLayout.Scroll
        return scrollCssJs(
            immersiveScroll = immersiveScroll,
            chapterStartPaddingPx = if (immersiveScroll) chapterStartPaddingPx() else 0,
        )
    }

    private fun injectScrollCss() {
        navigator ?: return
        injectJs(scrollCssJsForCurrent())
    }

    // Cover the page during a chapter change so it is revealed only once the new resource is re-styled
    // (see [styled]). A watchdog guarantees we never stay covered if the new page somehow never fires
    // onPageLoaded (e.g. a failed load), since a stuck cover would mean a blank reader.
    private fun beginChapterTransition(forward: Boolean = true) {
        _transitionForward.value = forward
        _styled.value = false
        revealWatchdog?.cancel()
        revealWatchdog = scope.launch {
            delay(STYLE_REVEAL_TIMEOUT_MS)
            _styled.value = true
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

    override fun nextChapter() = goToReadingOrderOffset(+1)

    override fun previousChapter() = goToReadingOrderOffset(-1)

    private fun goToReadingOrderOffset(delta: Int) {
        val hrefStr = lastLocator?.href?.toString() ?: return
        val order = publication.readingOrder
        val index = order.indexOfFirst { it.url().toString() == hrefStr }
        val target = order.getOrNull(index + delta) ?: return
        beginChapterTransition(forward = delta > 0)
        scope.launch { navigator?.go(target, animated = false) }
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
        scope.launch { navigator?.go(target, animated = false) }
    }

    override fun goToProgression(totalProgression: Double) {
        scope.launch {
            // Map the requested whole-book progression to the nearest known position.
            val positions = publication.positions()
            val target = positions.minByOrNull {
                kotlin.math.abs((it.locations.totalProgression ?: 0.0) - totalProgression)
            } ?: return@launch
            // Cover only when landing on a different resource (a same-resource jump doesn't reload, so
            // onPageLoaded wouldn't fire to lift the cover).
            if (target.href.toString() != lastLocator?.href?.toString()) {
                val forward = (target.locations.totalProgression ?: 0.0) >=
                    (lastLocator?.locations?.totalProgression ?: 0.0)
                beginChapterTransition(forward = forward)
            }
            navigator?.go(target, animated = false)
        }
    }

    override fun scrollToChapterStart() = goToChapterProgression(0.0)

    override fun scrollToChapterEnd() = goToChapterProgression(0.999)

    // Jump within the current resource by replacing only its progression, so start/end stay in-chapter.
    private fun goToChapterProgression(progression: Double) {
        val base = lastLocator ?: return
        val target = base.copy(locations = Locator.Locations(progression = progression))
        scope.launch { navigator?.go(target, animated = false) }
    }

    override fun goToLocator(locatorJson: String) {
        val locator =
            runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull() ?: return
        // Cover only on a cross-resource jump (same-resource jumps don't reload to lift the cover).
        if (locator.href.toString() != lastLocator?.href?.toString()) {
            val forward = (locator.locations.totalProgression ?: 0.0) >=
                (lastLocator?.locations?.totalProgression ?: 0.0)
            beginChapterTransition(forward = forward)
        }
        scope.launch { navigator?.go(locator, animated = false) }
    }

    override fun close() {
        scope.cancel()
        runCatching { searchIterator?.close() }
        searchIterator = null
        tts?.shutdown()
        tts = null
        runCatching { publication.close() }
    }

    private fun speakSelection(text: String) {
        val existing = tts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yomu-selection")
            return
        }
        // First use: TTS init is async, so remember the text and speak once the engine is ready.
        pendingSpeak = text
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                pendingSpeak?.let {
                    tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "yomu-selection")
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
                         offset: 0, armed: false, fired: false, svgDir: 0,
                         lastT: 0, lastOffset: 0, vel: 0, raf: 0 };
              // THRESHOLD: armed (release navigates) once the stretch passes this. MAX: asymptotic
              // ceiling. INITIAL: stretch-per-pixel near the boundary (the curve eases off from here to
              // MAX so there's no hard wall). SPRING: a lightly under-damped return (ratio ~0.9).
              var THRESHOLD = 80, MAX = 170, INITIAL = 0.6;
              var STIFFNESS = 170, DAMPING = 24;
              var SVGNS = 'http://www.w3.org/2000/svg';
              document.documentElement.style.overscrollBehaviorY = 'contain';
              var hint = document.createElement('div');
              hint.id = 'yomu-overscroll';
              hint.style.cssText = [
                'position:fixed', 'left:50%', 'z-index:2147483647', 'box-sizing:border-box',
                'width:42px', 'height:42px', 'border-radius:50%',
                'display:flex', 'align-items:center', 'justify-content:center',
                'font:600 22px system-ui,-apple-system,Roboto,sans-serif', 'line-height:1',
                'background:$fill', 'border:1px solid $border', 'color:$accent',
                'opacity:0', 'transform:translateX(-50%) scale(0.7)',
                // Transform is intentionally NOT transitioned: the pill's scale tracks the pull 1:1
                // during the drag. Only the colour/opacity ease (incl. the fade-out on release).
                'transition:opacity .18s ease, background .18s ease, color .18s ease',
                'pointer-events:none', '-webkit-tap-highlight-color:transparent'
              ].join(';');
              // Appended to <html>, NOT <body>: the body is transformed during the pull, which would
              // make a fixed element inside it page-relative (scrolling with the content) instead of
              // pinned to the viewport. As an <html> child it stays anchored to the screen edge.
              document.documentElement.appendChild(hint);
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
              function now() { return (window.performance && performance.now) ? performance.now() : Date.now(); }
              function sc() { return document.scrollingElement || document.documentElement; }
              function atTop() { return sc().scrollTop <= 0; }
              function atBottom() { return sc().scrollTop + window.innerHeight >= sc().scrollHeight - 1; }
              function hasSel() { var s = window.getSelection(); return s && s.toString().length > 0; }
              // Asymptotic resistance: linear (slope INITIAL) at the boundary, easing smoothly toward MAX
              // so a firm pull meets diminishing give instead of the old hard cap.
              function resist(raw) {
                if (raw <= 0) return 0;
                return MAX * (1 - 1 / (1 + raw * INITIAL / MAX));
              }
              function setBody(off) {
                document.body.style.transition = 'none';
                document.body.style.transform = off ? 'translateY(' + off + 'px)' : '';
                st.offset = off;
              }
              function clearTransform() {
                if (st.raf) { cancelAnimationFrame(st.raf); st.raf = 0; }
                document.body.style.transition = 'none';
                document.body.style.transform = '';
                st.offset = 0;
              }
              function fadeHint() {
                hint.style.opacity = 0;
                hint.style.background = '$fill';
                hint.style.color = '$accent';
                hint.style.transform = 'translateX(-50%) scale(0.7)';
              }
              function updateHint(pull) {
                var p = Math.min(pull / THRESHOLD, 1);
                hint.style.opacity = p;
                if (st.armed) {
                  hint.style.background = '$accent';
                  hint.style.color = '$onAccent';
                  hint.style.transform = 'translateX(-50%) scale(1)';
                } else {
                  hint.style.background = '$fill';
                  hint.style.color = '$accent';
                  hint.style.transform = 'translateX(-50%) scale(' + (0.7 + 0.3 * p) + ')';
                }
              }
              // Velocity-seeded spring back to rest: the return scales with how far/fast the finger
              // left, instead of a fixed-duration tween that snaps the same way every time.
              function springBack(fromOff, vel) {
                if (st.raf) { cancelAnimationFrame(st.raf); st.raf = 0; }
                var x = fromOff, v = vel || 0, last = now();
                document.body.style.transition = 'none';
                function frame(t) {
                  var n = t || now();
                  var dt = Math.min((n - last) / 1000, 0.032); last = n;
                  var a = (-STIFFNESS * x - DAMPING * v);
                  v += a * dt; x += v * dt;
                  if (Math.abs(x) < 0.4 && Math.abs(v) < 6) {
                    document.body.style.transform = ''; st.offset = 0; st.raf = 0; return;
                  }
                  document.body.style.transform = 'translateY(' + x + 'px)';
                  st.offset = x;
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
                if (st.raf) { cancelAnimationFrame(st.raf); st.raf = 0; }
                if (!st.enabled || e.touches.length !== 1 || hasSel()) {
                  st.dragging = false; st.engaged = false; return;
                }
                st.startY = e.touches[0].clientY;
                st.dragging = true; st.engaged = false; st.dir = 0;
                st.armed = false; st.fired = false; st.offset = 0;
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
                  st.lastT = now(); st.lastOffset = 0; st.vel = 0;
                  if (st.svgDir !== cand) { st.svgDir = cand; setArrow(cand < 0); }
                  if (cand < 0) { hint.style.top = st.topHint + 'px'; hint.style.bottom = 'auto'; }
                  else { hint.style.bottom = '18px'; hint.style.top = 'auto'; }
                }
                e.preventDefault();
                // Clamp at 0: pulling back past the boundary just relaxes the stretch — it never flips
                // direction or resumes scrolling mid-gesture, which is what used to cause the jitter.
                var raw = Math.max(0, (st.dir < 0) ? (y - st.engageY) : (st.engageY - y));
                var pull = resist(raw);
                var off = -st.dir * pull;
                var t = now();
                var dt = (t - st.lastT) / 1000;
                if (dt > 0) { st.vel = (off - st.lastOffset) / dt; st.lastT = t; st.lastOffset = off; }
                setBody(off);
                st.armed = pull >= THRESHOLD;
                updateHint(pull);
              }, { capture: true, passive: false });
              window.addEventListener('touchend', function() {
                if (!st.engaged) { fadeHint(); st.dragging = false; return; }
                // Snapshot the gesture, then clear state so nothing lingers regardless of which branch
                // runs below.
                var off = st.offset, vel = st.vel, dir = st.dir, armed = st.armed, fired = st.fired;
                st.dragging = false; st.engaged = false; st.dir = 0; st.armed = false; st.svgDir = 0;
                // Always hide the arrow on release. Before navigating this matters: the next chapter can
                // render in the same WebView document, which would otherwise leave the pill pinned at
                // the edge.
                fadeHint();
                if (dir && armed && !fired) {
                  st.fired = true;
                  clearTransform();
                  window.location.href = dir < 0 ? '$PREV_CHAPTER_URL' : '$NEXT_CHAPTER_URL';
                  return;
                }
                springBack(off, vel);
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

        // Decoration group name for user highlights.
        const val HIGHLIGHTS_GROUP = "highlights"

        // Separate decoration group + tint for in-book search hits (replaced wholesale per query).
        const val SEARCH_GROUP = "search"
        val SEARCH_TINT = 0xFFE7C75B.toInt()

        // Cap on collected search hits — bounds memory and scan time on large books.
        const val MAX_SEARCH_RESULTS = 150

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

        // Overrides Readium CSS's scroll-mode body{max-width:40rem!important} so scroll mode fills
        // the width like paged mode does. In immersive scroll mode it also neutralizes Readium's
        // permanent safe-area top padding and inserts a chapter-start spacer into the content itself.
        fun scrollCssJs(immersiveScroll: Boolean, chapterStartPaddingPx: Int): String {
            val spacerHeight = chapterStartPaddingPx.coerceAtLeast(0)
            val topPaddingFix = if (immersiveScroll) {
                """
                ':root[style*="readium-scroll-on"]{--RS__scrollPaddingTop:0px!important}',
                ':root[style*="readium-scroll-on"] body{padding-top:0!important}',
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
                $topPaddingFix
                $spacerCss
              ].filter(Boolean).join('\n');

              var body = document.body;
              if (!body) return;
              var staleNext = document.getElementById('yomu-next-chapter');
              if (staleNext && staleNext.parentNode) staleNext.parentNode.removeChild(staleNext);
              var spacer = document.getElementById(spacerId);
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
