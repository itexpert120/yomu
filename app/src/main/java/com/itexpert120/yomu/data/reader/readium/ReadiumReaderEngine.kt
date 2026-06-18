package com.itexpert120.yomu.data.reader.readium

import android.content.Context
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.Theme
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

    override suspend fun open(filePath: String, initialLocatorJson: String?): ReaderSession? {
        val publication = openPublication(filePath) ?: return null
        return ReadiumReaderSession(publication, initialLocatorJson)
    }

    override suspend fun tableOfContents(filePath: String): List<ReaderTocItem> {
        val publication = openPublication(filePath) ?: return emptyList()
        return try {
            buildList { flattenToc(publication, publication.tableOfContents, depth = 0, out = this) }
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
    private val publication: Publication,
    initialLocatorJson: String?,
) : ReaderSession {

    override val title: String = publication.metadata.title ?: "Reading"

    private val _currentLocator = MutableStateFlow<ReaderLocator?>(null)
    override val currentLocator: StateFlow<ReaderLocator?> = _currentLocator.asStateFlow()

    private val _centerTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val centerTaps: SharedFlow<Unit> = _centerTaps.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var navigator: EpubNavigatorFragment? = null
    // Latest engine locator, used for reading-order (chapter) navigation.
    private var lastLocator: Locator? = null
    // Settings requested before the navigator exists; applied once it is hosted.
    private var pendingSettings: ReaderSettings? = null

    private val initialLocator: Locator? = initialLocatorJson
        ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    private val navigatorFactory = EpubNavigatorFactory(publication)

    private val listener = object : EpubNavigatorFragment.Listener {
        override fun onExternalLinkActivated(url: AbsoluteUrl) {}
    }

    override val fragmentFactory: FragmentFactory =
        navigatorFactory.createFragmentFactory(initialLocator = initialLocator, listener = listener)

    override val fragmentClassName: String = EpubNavigatorFragment::class.java.name

    override fun onFragmentHosted(fragmentManager: FragmentManager, tag: String) {
        val nav = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment ?: return
        navigator = nav
        pendingSettings?.let { nav.submitPreferences(it.toPreferences()) }
        scope.launch {
            nav.currentLocator.collect { locator ->
                lastLocator = locator
                _currentLocator.value = ReaderLocator(
                    locatorJson = locator.toJSON().toString(),
                    totalProgression = locator.locations.totalProgression,
                    chapterTitle = locator.title,
                    href = locator.href.toString(),
                )
            }
        }
        // Only the centre is handled (opens the controls sheet); edge taps fall through so Readium's
        // default navigation (swipes) keeps working — no custom navigation tap zones.
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val view = nav.view ?: return false
                if (view.width <= 0 || view.height <= 0) return false
                val x = event.point.x / view.width
                val y = event.point.y / view.height
                val center = x in CENTER_MIN..CENTER_MAX && y in CENTER_MIN..CENTER_MAX
                return if (center) {
                    _centerTaps.tryEmit(Unit)
                    true
                } else {
                    false
                }
            }
        })
    }

    override fun applySettings(settings: ReaderSettings) {
        pendingSettings = settings
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

    override fun close() {
        scope.cancel()
        runCatching { publication.close() }
    }

    // Layout + size + theme colours (explicit bg/text so it matches the chrome); custom fonts land in P2.
    private fun ReaderSettings.toPreferences(): EpubPreferences = EpubPreferences(
        scroll = layout == ReaderLayout.Scroll,
        fontSize = fontScale.toDouble(),
        // Base appearance picks sensible defaults (links etc.), but the explicit bg/text colours
        // win so the page exactly matches the Yomu chrome (no status-bar seam). A theme's own bg
        // would otherwise override them, which is what caused the mismatch.
        theme = if (isLightBackground) Theme.LIGHT else Theme.DARK,
        backgroundColor = ReadiumColor(backgroundArgb.toInt()),
        textColor = ReadiumColor(textArgb.toInt()),
        publisherStyles = false,
    )

    private companion object {
        const val CENTER_MIN = 0.25f
        const val CENTER_MAX = 0.75f
    }
}
