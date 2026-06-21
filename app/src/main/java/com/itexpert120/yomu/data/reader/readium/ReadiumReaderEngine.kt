package com.itexpert120.yomu.data.reader.readium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderTextAlign
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderLocator
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
import org.json.JSONObject
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
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
        return ReadiumReaderSession(context, publication, initialLocatorJson, initialSettings)
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

    private val _centerTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val centerTaps: SharedFlow<Unit> = _centerTaps.asSharedFlow()

    private val _lookUpRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val lookUpRequests: SharedFlow<String> = _lookUpRequests.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var navigator: EpubNavigatorFragment? = null

    // Latest engine locator, used for reading-order (chapter) navigation.
    private var lastLocator: Locator? = null

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
        override fun onExternalLinkActivated(url: AbsoluteUrl) {}
    }

    override val fragmentFactory: FragmentFactory =
        navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = initialSettings.toPreferences(),
            listener = listener,
            configuration = EpubNavigatorFragment.Configuration {
                // In scroll mode a horizontal swipe jumps a whole resource (chapter), which users
                // hit accidentally while scrolling vertically. Disable it: chapters are advanced via
                // the next-chapter button / controls sheet / TOC (programmatic, unaffected). This is
                // the engine-level guard the InputListener.onDrag swallow couldn't provide. Paged
                // mode is untouched — the flag only gates scroll-mode swipes.
                disablePageTurnsWhileScrolling = true

                // Customize the text-selection menu. Readium replaces the WebView's native menu with
                // this callback entirely (it drops Chromium's Copy/Share/Read-aloud), so we rebuild
                // the standard actions ourselves — Copy, Read aloud (TTS), Share — and slot "Look up"
                // in. "Look up" hands the selected text to the dictionary flow; this also replaces the
                // old floating look-up bar.
                selectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        menu ?: return false
                        menu.add(Menu.NONE, MENU_COPY, 0, android.R.string.copy)
                        menu.add(Menu.NONE, MENU_LOOK_UP, 1, "Look up")
                        menu.add(Menu.NONE, MENU_SPEAK, 2, "Read aloud")
                        menu.add(Menu.NONE, MENU_SHARE, 3, "Share")
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        val id = item?.itemId ?: return false
                        if (id !in setOf(MENU_COPY, MENU_LOOK_UP, MENU_SPEAK, MENU_SHARE)) {
                            return false
                        }
                        scope.launch {
                            val selection = (navigator as? SelectableNavigator)?.currentSelection()
                            val text = selection?.locator?.text?.highlight?.trim()
                            if (!text.isNullOrBlank()) {
                                when (id) {
                                    MENU_COPY -> copySelection(text)
                                    MENU_SPEAK -> speakSelection(text)
                                    MENU_SHARE -> shareSelection(text)
                                    MENU_LOOK_UP -> _lookUpRequests.tryEmit(text)
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
        scope.launch {
            nav.currentLocator.collect { locator ->
                lastLocator = locator
                val hrefStr = locator.href.toString()
                val order = publication.readingOrder
                val index = order.indexOfFirst { it.url().toString() == hrefStr }
                _currentLocator.value = ReaderLocator(
                    locatorJson = locator.toJSON().toString(),
                    totalProgression = locator.locations.totalProgression,
                    chapterTitle = locator.title,
                    href = hrefStr,
                    chapterProgression = locator.locations.progression,
                    hasPreviousChapter = index > 0,
                    hasNextChapter = index in 0 until order.lastIndex,
                )
            }
        }
        // Tap-zone navigation: in PAGED mode, the left/right thirds turn pages; the centre opens the
        // controls sheet (any mode). In SCROLL mode edge taps do nothing — the user scrolls. We
        // deliberately don't handle drags, so there's no swipe/drag-to-change-chapter. Unhandled
        // taps return false so Readium can still activate in-page links.
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val view = nav.view ?: return false
                if (view.width <= 0) return false
                val x = event.point.x / view.width
                if (currentSettings.tapNavigation && currentSettings.layout == ReaderLayout.Paged) {
                    if (x <= TAP_LEFT) {
                        goBackward(); return true
                    }
                    if (x >= TAP_RIGHT) {
                        goForward(); return true
                    }
                }
                if (x > TAP_LEFT && x < TAP_RIGHT && currentSettings.centerTapOpensSheet) {
                    _centerTaps.tryEmit(Unit)
                    return true
                }
                return false
            }
        })
    }

    override fun applySettings(settings: ReaderSettings) {
        pendingSettings = settings
        currentSettings = settings
        navigator?.submitPreferences(settings.toPreferences())
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

    override fun goToLocator(locatorJson: String) {
        val locator =
            runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull() ?: return
        scope.launch { navigator?.go(locator, animated = false) }
    }

    override fun close() {
        scope.cancel()
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
    }
}
