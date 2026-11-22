package com.itexpert120.yomu.core.reader

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.ReaderSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
    // Selections this returns true for are phrases, for which the selection menu hides "Look up".
    val isLookupPhrase: (String) -> Boolean = { false },
)

/** Result of opening a publication, including cache data generated for a cache miss or repair. */
data class ReaderOpenResult(
    val session: ReaderSession,
    val publicationCache: ReaderPublicationCache,
)

/** Atomic presentation state for the navigator. */
sealed interface ReaderRenderState {
    data object Opening : ReaderRenderState

    data class Transitioning(val forward: Boolean) : ReaderRenderState

    data class Ready(val href: String?) : ReaderRenderState
}

/** An action picked from the text-selection menu, with the selected text and its position. */
data class ReaderSelection(
    val action: Action,
    val text: String,
    val locatorJson: String,
) {
    enum class Action { Copy, Highlight, LookUp, SearchWeb, ReadAloud, Share }
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
    // Other TOC sections that start at the same position as [chapterId] and so share its progress:
    // several fragment links into one resource whose anchor positions are unknown (for example a
    // volume's "Image Gallery / Characters / Artworks / Locations" back matter in a single file).
    val sharedChapterIds: List<String> = emptyList(),
    // A preceding logical section whose end was just crossed through continuous reading.
    val completedChapterId: String? = null,
    // Sections sharing [completedChapterId]'s start; they complete along with it.
    val completedSharedChapterIds: List<String> = emptyList(),
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

    /**
     * Opens a book's publication ahead of a likely [open] (the resume book, a book whose details
     * are showing), so that open skips parsing. One book at a time: a newer prepare replaces an
     * older one, and an [open] of the same file takes the prepared book (or waits for it).
     */
    fun prepare(filePath: String, publicationCache: ReaderPublicationCache?) = Unit

    /** Drops a prepared book, e.g. under memory pressure. */
    fun releasePrepared() = Unit

    /** Starts the rendering engine's one-time start-up early, off the critical path. Main thread. */
    fun warmUp() = Unit

    /** Reads the book's table of contents without starting a reading session. */
    suspend fun tableOfContents(filePath: String): List<ReaderTocItem>?

    companion object {
        /**
         * Lets [activity] be re-created (rotation, process death) while a book is open. Call before
         * `super.onCreate`: the engine's reading view can't be restored from saved state, so the
         * guard lets the framework instantiate it and then discards it before it can resume; the
         * reader hosts a fresh one.
         */
        fun installRestoreGuard(activity: FragmentActivity) {
            EntryPointAccessors.fromApplication(activity.applicationContext, ReaderRestoreGuardEntryPoint::class.java)
                .restoreGuard()
                .install(activity)
        }
    }
}

/** The engine's activity re-creation guard; see [ReaderEngine.installRestoreGuard]. */
fun interface ReaderRestoreGuard {
    fun install(activity: FragmentActivity)
}

// The guard runs before the activity is injected, so it is looked up from the application.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderRestoreGuardEntryPoint {
    fun restoreGuard(): ReaderRestoreGuard
}

/** The reading view, as Compose hosts it. */
interface ReaderNavigator {
    /**
     * Shows the reading view in [container], through the activity's [fragmentManager]. Doesn't block
     * the first frame: the view is added once the container is attached, and a view restored from
     * a previous configuration is replaced.
     */
    fun host(container: FragmentContainerView, fragmentManager: FragmentManager)

    /** Whether the page runs under the system bars (immersive) or inset below them. */
    fun setImmersive(immersive: Boolean)

    /** Removes the reading view from its container (the host is going away). */
    fun unhost()
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
     * Emits an action the user picked from the text-selection menu, with the selection. The engine
     * finishes the selection action mode itself; what each action does is up to the caller.
     */
    val selections: SharedFlow<ReaderSelection>

    /**
     * Emits a footnote's content (HTML) when the user taps a footnote reference, so the UI can show
     * it in a popup instead of the engine jumping to the note at the bottom of the resource.
     */
    val footnotes: SharedFlow<String>

    /** Emits the id of an on-page highlight the user tapped, so the UI can edit/delete it. */
    val highlightTaps: SharedFlow<String>

    /** Applies reading preferences (layout/theme/font/size). Safe to call before/after hosting. */
    fun applySettings(settings: ReaderSettings)

    /**
     * Verifies that the hosted navigator is still rendering [currentLocator] after the app returns
     * to the foreground. A stale position is restored; a blank/unrecoverable navigator is rebuilt.
     */
    fun onForegroundResumed()

    // Chapter navigation, driven by the custom chrome. Page turns are the engine's own tap zones.
    fun nextChapter()
    fun previousChapter()

    /** Jumps to a specific locator (e.g. a TOC entry's stored position). */
    fun goToLocator(locatorJson: String)

    /**
     * The current position in a form that survives a different screen size, font, or layout: the
     * first visible element (selector + surrounding text) with the chapter progression as fallback.
     * Used to resume on other devices. Null when the navigator can't tell yet.
     */
    suspend fun portableLocator(): String? = null

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

    fun close()
}
