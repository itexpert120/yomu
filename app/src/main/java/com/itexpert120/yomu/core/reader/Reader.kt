package com.itexpert120.yomu.core.reader

import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
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
    suspend fun open(filePath: String, initialLocatorJson: String?): ReaderSession?

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

    /** Emits when the user taps the reading area (used to toggle chrome). */
    val tapEvents: SharedFlow<Unit>

    val fragmentFactory: FragmentFactory
    val fragmentClassName: String

    fun onFragmentHosted(fragmentManager: FragmentManager, tag: String)
    fun close()
}
