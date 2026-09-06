package com.itexpert120.yomu.core.reader

import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Immutable publication metadata that can be reused when opening a book. */
@Serializable
data class ReaderPublicationCache(
    val toc: List<ReaderTocItem> = emptyList(),
    val resourceWeights: Map<String, Int> = emptyMap(),
) {
    /** Validates and orders cached weights against the opened publication's reading order. */
    fun validatedWeights(readingOrderKeys: List<String>): List<Int>? {
        if (readingOrderKeys.any { resourceWeights[it]?.let { weight -> weight > 0 } != true }) {
            return null
        }
        return readingOrderKeys.map { resourceWeights.getValue(it) }
    }
}

/** Request used to open a publication without exposing Readium types across the reader boundary. */
data class ReaderOpenRequest(
    val filePath: String,
    val initialLocatorJson: String?,
    val initialSettings: ReaderSettings,
    val publicationCache: ReaderPublicationCache? = null,
)

/** Result of opening a publication, including cache data generated for a cache miss or repair. */
data class ReaderOpenResult(
    val session: ReaderSession,
    val publicationCache: ReaderPublicationCache,
)

/** Atomic presentation state for the navigator. */
sealed interface ReaderRenderState {
    data object Opening : ReaderRenderState
    data object Failed : ReaderRenderState

    data class Transitioning(val forward: Boolean) : ReaderRenderState

    data class Ready(val href: String?) : ReaderRenderState
}

/** Yomu-owned reading position (wraps the engine's native locator JSON). */
data class ReaderLocator(
    val locatorJson: String,
    val totalProgression: Double?,
    val chapterTitle: String?,
    // Resource href of the current position; matches [ReaderTocItem.id] so the TOC can track reads.
    val href: String?,
    // Progression within the current resource/chapter (0..1), for chapter-boundary detection.
    val chapterProgression: Double? = null,
    // Whether a previous/next resource (chapter) exists in reading order.
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    // Chapter-weighted whole-book progress (chapterIndex + chapterProgression) / chapterCount, for a
    // smoothly-advancing "% through book" display. Unlike the engine's totalProgression it never sits
    // near 0 through a whole early chapter of a many-chapter book.
    val bookProgress: Double? = null,
    // Visual pages remaining in paged mode; null in scroll mode.
    val chapterPagesLeft: Int? = null,
    // Stable logical TOC section containing [href]. A section may span several reading-order
    // resources, unlike [href], which always identifies the currently rendered resource.
    val chapterId: String? = null,
    // A preceding logical section whose end was just crossed through continuous reading.
    val completedChapterId: String? = null,
    // True only when the actual final page/end of the publication has been reached.
    val completed: Boolean = false,
)

/**
 * A flattened TOC entry. [id] identifies a logical section, including its fragment when present;
 * [resourceHref] identifies the containing resource and can be shared by several sections.
 * Read-state uses [id]. [depth] is the nesting level in the source TOC (0 = top level) so the
 * UI can indent without holding the tree. [locatorJson] is the position to open the reader at, or
 * null when the entry has no resolvable target.
 */
@Serializable
data class ReaderTocItem(
    val id: String,
    val title: String,
    val locatorJson: String?,
    val depth: Int,
    // Raw resource href for matching old caches and locators. [id] additionally includes a fragment
    // when needed so two TOC sections in one XHTML resource remain independently addressable.
    val resourceHref: String = id,
)

/**
 * A single in-book search hit. [match] is the matched substring; [before]/[after] are the
 * surrounding text for display context. [locatorJson] is the position to jump to. No engine types
 * leak out.
 */
data class ReaderSearchResult(
    val locatorJson: String,
    val before: String,
    val match: String,
    val after: String,
    val chapterTitle: String?,
)

/** Opens books for reading. Implemented by the Readium adapter; no engine types leak out. */
interface ReaderEngine {
    suspend fun open(request: ReaderOpenRequest): ReaderOpenResult?

    /** Reads the book's table of contents without starting a reading session. */
    suspend fun tableOfContents(filePath: String): List<ReaderTocItem>?
}

/** Restricted navigator surface exposed to Compose for fragment hosting only. */
interface ReaderNavigator {
    val fragmentFactory: FragmentFactory
    val fragmentClassName: String

    fun onFragmentHosted(fragmentManager: FragmentManager, tag: String)
}

/**
 * A live reading session. The book-scoped reading workflow owns this interface; Compose receives
 * only its [ReaderNavigator] facet.
 */
interface ReaderSession : ReaderNavigator {
    val title: String
    val tableOfContents: List<ReaderTocItem>
    val currentLocator: StateFlow<ReaderLocator?>

    /** Atomic first-frame state; the UI reveals content only from [ReaderRenderState.Ready]. */
    val renderState: StateFlow<ReaderRenderState>

    /** Emits when the user taps the centre of the page (used to open the controls sheet). */
    val centerTaps: SharedFlow<Unit>

    /**
     * Emits the selected text when the user taps "Look up" in the native text-selection menu. The
     * engine finishes the selection action mode itself, so callers don't need to clear anything.
     */
    val lookUpRequests: SharedFlow<String>

    /**
     * Emits a footnote's content (HTML) when the user taps a footnote reference, so the UI can show
     * it in a popup instead of the engine jumping to the note at the bottom of the resource.
     */
    val footnotes: SharedFlow<String>

    /**
     * Emits a [ReaderHighlightDraft] when the user taps "Highlight" in the text-selection menu. The
     * engine finishes the selection action mode itself; the app decides colour + persistence.
     */
    val highlightRequests: SharedFlow<ReaderHighlightDraft>

    /** Emits the id of an on-page highlight the user tapped, so the UI can edit/delete it. */
    val highlightTaps: SharedFlow<String>

    /** Applies reading preferences (layout/theme/font/size). Safe to call before/after hosting. */
    fun applySettings(settings: ReaderSettings)

    /**
     * Re-asserts the immersive-mode layout without a full [applySettings] pass (no preference
     * resubmission/reflow). The Readium page fragment can set a stray native top-padding on its
     * WebView's parent view when its Fragment/WebView is reattached (e.g. after the Activity backgrounds
     * and resumes) — this padding sits outside CSS and outside window insets entirely, so it survives
     * even when the window's own edge-to-edge/inset state is completely correct. Call on foreground
     * resume to clear it.
     */
    fun refreshImmersiveLayout()

    /**
     * Verifies that the hosted navigator is still rendering [currentLocator] after the app returns
     * to the foreground. A stale position is restored; a blank/unrecoverable navigator is rebuilt.
     */
    fun onForegroundResumed()

    // Navigation, driven by the custom chrome (tap zones, slider arrows, progress slider).
    fun goForward()
    fun goBackward()
    fun nextChapter()
    fun previousChapter()
    fun goToProgression(totalProgression: Double)

    /** Jumps to a specific locator (e.g. a TOC entry's stored position). */
    fun goToLocator(locatorJson: String)

    /**
     * Renders the given highlights on the page (replacing the previous set). Safe to call before the
     * navigator is hosted — the engine re-applies them once it is.
     */
    fun applyHighlights(highlights: List<ReaderHighlight>)

    /**
     * Full-text search across the whole publication. Returns a bounded list of hits (empty when the
     * query is blank or the publication isn't searchable). Suspends while scanning; cancellable.
     */
    suspend fun search(query: String): List<ReaderSearchResult>

    /** Underlines the given search hits on the page (replacing the previous search set). */
    fun applySearchDecorations(results: List<ReaderSearchResult>)

    /** Clears the on-page search underlines. */
    fun clearSearch()

    /** Speaks reader-owned text, reusing the session's lazily initialized TTS engine. */
    fun speak(text: String)

    fun close()
}
