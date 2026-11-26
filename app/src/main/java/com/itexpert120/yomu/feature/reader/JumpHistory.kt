package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.reader.ReaderLocator
import kotlin.math.roundToInt

/**
 * "Back to where you were": the places the reader jumped away from (contents, search, a bookmark or
 * highlight, the position slider, a link in the text), newest last, so Back can be taken more than
 * once. The offer ends once the reader reads on from where a jump landed: [READ_ON_MOVES] moves of
 * the position, each at least [MOVE_SPACING_MS] after the last (a scroll reports many positions in a
 * burst; a page turn reports one).
 *
 * Fed positions as they're reported; knows nothing about the engine.
 */
internal class JumpHistory(
    private val maxEntries: Int = MAX_ENTRIES,
    private val readOnMoves: Int = READ_ON_MOVES,
    private val moveSpacingMillis: Long = MOVE_SPACING_MS,
) {
    private class Entry(val origin: ReaderLocator, val coalesce: Boolean)

    private val entries = ArrayDeque<Entry>()

    // A jump (or a step back) is under way: the next position reported is where it landed.
    private var awaitingArrival = false
    private var lastLocatorJson: String? = null
    private var lastMoveAt = 0L
    private var moves = 0

    /** Where Back goes, or null when nothing is on offer. */
    val back: ReaderLocator? get() = entries.lastOrNull()?.origin

    /**
     * The reader is jumping away from [origin]. A [coalesce]d jump (a slider seek) made straight after
     * another, before reading on, keeps the first one's origin: scrubbing twice goes back once.
     */
    fun recordJump(origin: ReaderLocator, coalesce: Boolean = false) {
        val top = entries.lastOrNull()
        val scrubbing = coalesce && top?.coalesce == true && moves == 0
        if (!scrubbing && top?.origin?.locatorJson != origin.locatorJson) {
            entries.addLast(Entry(origin, coalesce))
            while (entries.size > maxEntries) entries.removeFirst()
        }
        awaitingArrival = true
        moves = 0
    }

    /** Takes the way back: the newest origin, which the reader is now returning to. */
    fun pop(): ReaderLocator? {
        val entry = entries.removeLastOrNull() ?: return null
        awaitingArrival = entries.isNotEmpty()
        moves = 0
        return entry.origin
    }

    fun clear() {
        entries.clear()
        awaitingArrival = false
        moves = 0
    }

    /**
     * A new position, at [nowMillis]. True when this ended the offer: the reader has read on from
     * where the jump landed.
     */
    fun onLocator(locator: ReaderLocator, nowMillis: Long): Boolean {
        val changed = locator.locatorJson != lastLocatorJson
        lastLocatorJson = locator.locatorJson
        if (entries.isEmpty()) return false
        if (awaitingArrival) {
            awaitingArrival = false
            lastMoveAt = nowMillis
            return false
        }
        if (!changed || nowMillis - lastMoveAt < moveSpacingMillis) return false
        lastMoveAt = nowMillis
        if (++moves < readOnMoves) return false
        clear()
        return true
    }

    companion object {
        const val MAX_ENTRIES = 10
        const val READ_ON_MOVES = 3
        const val MOVE_SPACING_MS = 700L
    }
}

/**
 * The Back chip's label for returning to [origin]: its printed page when the book has them ("Back
 * to p. 112"), else its chapter ("Back to Chapter 3"); a place no chapter names (front matter the
 * engine can't title) by how far into the book it is ("Back to the beginning", "Back to 42%").
 */
internal fun jumpBackLabel(origin: ReaderLocator, chapterTitle: String?): String {
    val page = origin.pageLabel?.substringBefore('–')?.trim()?.takeIf { it.isNotEmpty() }
    val chapter = (origin.chapterTitle ?: chapterTitle)?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        page != null -> "Back to p. $page"
        chapter != null -> "Back to $chapter"
        else -> {
            val percent = ((origin.bookProgress ?: 0.0) * 100).roundToInt()
            if (percent < 1) "Back to the beginning" else "Back to $percent%"
        }
    }
}
