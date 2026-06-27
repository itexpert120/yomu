package com.itexpert120.yomu.data.reader.readium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
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
                    out = this
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
            // Custom-scheme links injected into the page — the end-of-chapter "Next chapter" button
            // and the scroll-mode rubberband overscroll gesture — route here as external links;
            // intercept them for chapter navigation instead of opening a browser. Pull-to-previous
            // lands at the END of the previous chapter for a continuous-reading feel.
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
            scope.launch {
                runCatching { navigator?.evaluateJavascript(SCROLL_WIDTH_FIX_JS) }
                applyScrollbars(currentSettings)
            }
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
                if (restoring) return@collect
                lastLocator = locator
                val hrefStr = locator.href.toString()
                // Scroll mode forces body{max-width:40rem!important} in Readium CSS, which our
                // RsProperties maxLineLength can't override (it's set per-resource on body). Inject a
                // style override so scroll mode fills the width too. Re-applied per resource.
                val order = publication.readingOrder
                val index = order.indexOfFirst { it.url().toString() == hrefStr }
                val hasNext = index in 0 until order.lastIndex
                if (hrefStr != lastStyledHref) {
                    lastStyledHref = hrefStr
                    scope.launch { runCatching { nav.evaluateJavascript(SCROLL_WIDTH_FIX_JS) } }
                    // Append the end-of-chapter "Next chapter" button + arm the rubberband gesture.
                    injectNextChapterButton()
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
                )
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
                        goBackward(); return true
                    }
                    if (x >= TAP_RIGHT) {
                        goForward(); return true
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

    override fun applySettings(settings: ReaderSettings) {
        pendingSettings = settings
        currentSettings = settings
        navigator?.submitPreferences(settings.toPreferences())
        // Re-style the injected button so its colours track a live theme/accent change.
        injectNextChapterButton()
        // Re-toggle/re-theme the native scrollbar (it's scroll-mode only and tracks the text colour).
        applyScrollbars(settings)
        // Re-arm or tear down the rubberband gesture on a scroll<->paged or theme change.
        injectOverscroll()
    }

    // Re-enable the WebView's native vertical scrollbar in scroll mode (Readium force-disables it),
    // themed to the reading text colour on API 29+. The CSS route can't work — scroll mode is a
    // native WebView scroll, not a CSS-overflow element. android.webkit.WebView is not a Readium
    // type, so walking the navigator's view tree keeps the engine boundary intact.
    private fun applyScrollbars(settings: ReaderSettings) {
        val root = navigator?.view ?: return
        val scroll = settings.layout == ReaderLayout.Scroll
        forEachWebView(root) { wv ->
            wv.isVerticalScrollBarEnabled = scroll
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

    // True when the current resource has a following resource in reading order.
    private fun currentHasNext(): Boolean {
        val hrefStr = lastLocator?.href?.toString() ?: return false
        val order = publication.readingOrder
        val index = order.indexOfFirst { it.url().toString() == hrefStr }
        return index in 0 until order.lastIndex
    }

    // Inject (or refresh) the end-of-chapter "Next chapter" button into the current resource, styled
    // from the active theme + accent. Removes it on the final chapter (nothing to advance to).
    private fun injectNextChapterButton() {
        val nav = navigator ?: return
        val js = nextChapterButtonJs(currentHasNext(), currentSettings)
        scope.launch { runCatching { nav.evaluateJavascript(js) } }
    }

    // Inject (or refresh) the scroll-mode rubberband overscroll gesture into the current resource:
    // pulling past the top goes to the previous chapter, past the bottom to the next. A no-op
    // teardown in paged mode (enabled = false).
    private fun injectOverscroll() {
        val nav = navigator ?: return
        val hrefStr = lastLocator?.href?.toString()
        val order = publication.readingOrder
        val index =
            if (hrefStr != null) order.indexOfFirst { it.url().toString() == hrefStr } else -1
        val hasPrev = index > 0
        val hasNext = index in 0 until order.lastIndex
        val enabled = currentSettings.layout == ReaderLayout.Scroll
        val js = overscrollJs(enabled, hasPrev, hasNext, currentSettings)
        scope.launch { runCatching { nav.evaluateJavascript(js) } }
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
        scope.launch { navigator?.go(target, animated = false) }
    }

    override fun goToProgression(totalProgression: Double) {
        scope.launch {
            // Map the requested whole-book progression to the nearest known position.
            val positions = publication.positions()
            val target = positions.minByOrNull {
                kotlin.math.abs((it.locations.totalProgression ?: 0.0) - totalProgression)
            } ?: return@launch
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

    // Builds the JS that appends a "Next chapter" pill to the end of the resource body. Colours are
    // derived from the active reading theme (the page's own text colour) — never the app accent — so
    // the button always harmonises with the page: a soft filled background, a defined border, and a
    // text-coloured label. System font, fixed px size. The button is an anchor to a custom scheme;
    // its tap is caught in onExternalLinkActivated. Removed on the final chapter.
    private fun nextChapterButtonJs(hasNext: Boolean, settings: ReaderSettings): String {
        val text = settings.textArgb.toInt()
        val r = (text shr 16) and 0xFF
        val g = (text shr 8) and 0xFF
        val b = text and 0xFF
        val fill = "rgba($r,$g,$b,0.10)"
        val border = "rgba($r,$g,$b,0.30)"
        val label = "rgb($r,$g,$b)"
        return """
            (function() {
              var id = 'yomu-next-chapter';
              var prev = document.getElementById(id);
              if (prev) prev.remove();
              if (!$hasNext) return;
              var a = document.createElement('a');
              a.id = id;
              a.href = '$NEXT_CHAPTER_URL';
              a.textContent = 'Next chapter';
              // Fixed px (not em/rem) so the button keeps its size regardless of reading font scale.
              a.style.cssText = [
                'display:block',
                'box-sizing:border-box',
                'width:fit-content',
                'max-width:100%',
                'margin:2.75em auto 1.75em',
                'padding:14px 28px',
                'border:1px solid $border',
                'border-radius:14px',
                'background:$fill',
                'color:$label',
                'font-family:system-ui,-apple-system,Roboto,sans-serif',
                'font-size:16px',
                'font-weight:600',
                'line-height:1',
                'letter-spacing:0.01em',
                'text-align:center',
                'text-decoration:none',
                'cursor:pointer',
                '-webkit-tap-highlight-color:transparent'
              ].join(';');
              document.body.appendChild(a);
            })();
        """.trimIndent()
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
    ): String {
        val text = settings.textArgb.toInt()
        val r = (text shr 16) and 0xFF
        val g = (text shr 8) and 0xFF
        val b = text and 0xFF
        val fill = "rgba($r,$g,$b,0.10)"
        val border = "rgba($r,$g,$b,0.30)"
        val label = "rgb($r,$g,$b)"
        return """
            (function() {
              if (window.__yomuOverscroll) {
                window.__yomuOverscroll.update($enabled, $hasPrev, $hasNext);
                return;
              }
              var st = { enabled: $enabled, hasPrev: $hasPrev, hasNext: $hasNext,
                         startY: 0, dragging: false, dir: 0, armed: false, fired: false };
              var THRESHOLD = 96, MAX = 160, DAMP = 0.45;
              document.documentElement.style.overscrollBehaviorY = 'contain';
              var hint = document.createElement('div');
              hint.id = 'yomu-overscroll';
              hint.style.cssText = [
                'position:fixed', 'left:50%', 'transform:translateX(-50%)', 'z-index:2147483647',
                'padding:8px 18px', 'border-radius:999px',
                'font:600 14px system-ui,-apple-system,Roboto,sans-serif',
                'background:$fill', 'border:1px solid $border', 'color:$label',
                'opacity:0', 'transition:opacity .15s', 'pointer-events:none',
                '-webkit-tap-highlight-color:transparent'
              ].join(';');
              document.body.appendChild(hint);
              function sc() { return document.scrollingElement || document.documentElement; }
              function atTop() { return sc().scrollTop <= 0; }
              function atBottom() { return sc().scrollTop + window.innerHeight >= sc().scrollHeight - 1; }
              function hasSel() { var s = window.getSelection(); return s && s.toString().length > 0; }
              function reset() {
                document.body.style.transition = 'transform .22s ease-out';
                document.body.style.transform = '';
                hint.style.opacity = 0;
                st.dragging = false; st.dir = 0; st.armed = false;
              }
              window.addEventListener('touchstart', function(e) {
                if (!st.enabled || e.touches.length !== 1 || hasSel()) { st.dragging = false; return; }
                st.startY = e.touches[0].clientY;
                st.dragging = true; st.dir = 0; st.armed = false; st.fired = false;
              }, { capture: true, passive: true });
              window.addEventListener('touchmove', function(e) {
                if (!st.dragging || !st.enabled) return;
                var dy = e.touches[0].clientY - st.startY;
                var dir = (dy > 0 && atTop() && st.hasPrev) ? -1
                        : (dy < 0 && atBottom() && st.hasNext) ? 1 : 0;
                if (dir === 0) { if (st.dir) reset(); return; }
                st.dir = dir;
                e.preventDefault();
                var pull = Math.min(Math.abs(dy) * DAMP, MAX);
                document.body.style.transition = 'none';
                document.body.style.transform = 'translateY(' + (dir < 0 ? pull : -pull) + 'px)';
                st.armed = pull >= THRESHOLD;
                hint.textContent = dir < 0
                  ? (st.armed ? 'Release for previous chapter' : 'Pull for previous chapter')
                  : (st.armed ? 'Release for next chapter' : 'Pull for next chapter');
                if (dir < 0) { hint.style.top = '16px'; hint.style.bottom = 'auto'; }
                else { hint.style.bottom = '16px'; hint.style.top = 'auto'; }
                hint.style.opacity = Math.min(pull / THRESHOLD, 1);
              }, { capture: true, passive: false });
              window.addEventListener('touchend', function() {
                if (st.dir && st.armed && !st.fired) {
                  st.fired = true;
                  window.location.href = st.dir < 0 ? '$PREV_CHAPTER_URL' : '$NEXT_CHAPTER_URL';
                }
                reset();
              }, { capture: true, passive: true });
              window.__yomuOverscroll = {
                update: function(en, hp, hn) {
                  st.enabled = en; st.hasPrev = hp; st.hasNext = hn;
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
        // The family name must match a declaration registered on the navigator factory above.
        fontFamily = FontFamily(font.cssFamily),
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

        // Custom-scheme URLs behind the injected end-of-chapter "Next chapter" button and the
        // scroll-mode rubberband overscroll gesture (intercepted in onExternalLinkActivated).
        const val NEXT_CHAPTER_URL = "yomu://next-chapter"
        const val PREV_CHAPTER_URL = "yomu://prev-chapter"

        // Overrides Readium CSS's scroll-mode body{max-width:40rem!important} so scroll mode fills
        // the width like paged mode does. No-op in paged mode (the selector only matches scroll).
        val SCROLL_WIDTH_FIX_JS = """
            (function() {
              var id = 'yomu-scroll-width';
              if (document.getElementById(id)) return;
              var s = document.createElement('style');
              s.id = id;
              s.textContent = ':root[style*="readium-scroll-on"] body{max-width:none!important}';
              (document.head || document.documentElement).appendChild(s);
            })();
        """.trimIndent()
    }
}
