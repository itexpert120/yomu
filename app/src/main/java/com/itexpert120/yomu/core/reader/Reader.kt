package com.itexpert120.yomu.core.reader

import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Yomu-owned reading position (wraps the engine's native locator JSON). */
data class ReaderLocator(
    val locatorJson: String,
    val totalProgression: Double?,
    val chapterTitle: String?,
    // Resource href of the current position; matches [ReaderTocItem.id] so the TOC can track reads.
    val href: String?,
)

/**
 * A single, flattened table-of-contents entry. [id] is a stable per-book key (the entry's href)
 * used to persist read-state. [depth] is the nesting level in the source TOC (0 = top level) so the
 * UI can indent without holding the tree. [locatorJson] is the position to open the reader at, or
 * null when the entry has no resolvable target.
 */
data class ReaderTocItem(
    val id: String,
    val title: String,
    val locatorJson: String?,
    val depth: Int,
)

/** Opens books for reading. Implemented by the Readium adapter; no engine types leak out. */
interface ReaderEngine {
    suspend fun open(
        filePath: String,
        initialLocatorJson: String?,
        initialSettings: ReaderSettings = ReaderSettings(),
    ): ReaderSession?

    /** Reads the book's table of contents without starting a reading session. */
    suspend fun tableOfContents(filePath: String): List<ReaderTocItem>
}

/**
 * A live reading session. Exposes the navigator as an androidx [FragmentFactory] + class name so
 * the Compose host can place it in a container without referencing engine types. The session
 * observes the navigator once hosted ([onFragmentHosted]).
 */
interface ReaderSession {
    val title: String
    val currentLocator: StateFlow<ReaderLocator?>

    /** Emits when the user taps the centre of the page (used to open the controls sheet). */
    val centerTaps: SharedFlow<Unit>

    val fragmentFactory: FragmentFactory
    val fragmentClassName: String

    fun onFragmentHosted(fragmentManager: FragmentManager, tag: String)

    /** Applies reading preferences (layout/theme/font/size). Safe to call before/after hosting. */
    fun applySettings(settings: ReaderSettings)

    // Navigation, driven by the custom chrome (tap zones, slider arrows, progress slider).
    fun goForward()
    fun goBackward()
    fun nextChapter()
    fun previousChapter()
    fun goToProgression(totalProgression: Double)

    fun close()
}
