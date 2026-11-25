package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.reader.ReaderPageTarget
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Reading-time estimates. Text is measured as the engines measure the book: KB of its chapter files
 * (markup included). The reader's speed comes from their own reading of this book (time logged in
 * Stats against how far into the book they are) once there is enough of it to go on; until then,
 * and whenever that figure is implausible (a jump far ahead, a reread), an average reader's.
 */
internal object ReadingTime {
    const val DEFAULT_WORDS_PER_MINUTE = 250

    // About six characters a word with its space, plus the markup around them in a chapter file.
    const val BYTES_PER_WORD = 9.0

    val defaultKbPerMinute: Double = DEFAULT_WORDS_PER_MINUTE * BYTES_PER_WORD / 1024.0

    // Enough reading to trust: a quarter of an hour and some 6,000 words.
    const val MIN_MEASURED_SECONDS = 15 * 60L
    const val MIN_MEASURED_KB = 50.0

    // A measured speed outside this many times the average is taken for a jump or a reread.
    private const val PLAUSIBLE_FACTOR = 3.0

    /** The reader's speed in KB a minute, from [readingSeconds] spent to reach [kbRead] into the book. */
    fun kbPerMinute(readingSeconds: Long?, kbRead: Double?): Double {
        if (readingSeconds == null || kbRead == null) return defaultKbPerMinute
        if (readingSeconds < MIN_MEASURED_SECONDS || kbRead < MIN_MEASURED_KB) return defaultKbPerMinute
        val measured = kbRead / (readingSeconds / 60.0)
        val plausible = defaultKbPerMinute / PLAUSIBLE_FACTOR..defaultKbPerMinute * PLAUSIBLE_FACTOR
        return if (measured in plausible) measured else defaultKbPerMinute
    }

    /** Whole minutes to read [kbLeft] at [kbPerMinute] (rounded up; 0 when nothing is left). */
    fun minutesLeft(kbLeft: Double?, kbPerMinute: Double): Int? {
        if (kbLeft == null || kbPerMinute <= 0.0) return null
        if (kbLeft <= 0.0) return 0
        return ceil(kbLeft / kbPerMinute).toInt()
    }

    /** "6 min", "1 h", "2 h 10 min" (minutes rounded to fives past an hour). */
    fun duration(minutes: Int): String {
        if (minutes < 60) return "$minutes min"
        val rounded = (minutes / 5.0).roundToInt() * 5
        val hours = rounded / 60
        val rest = rounded % 60
        return if (rest == 0) "$hours h" else "$hours h $rest min"
    }

    /** The controls panel's "6 min left in chapter"; null without an estimate. */
    fun chapterLabel(minutes: Int?): String? = when (minutes) {
        null -> null
        0, 1 -> "Less than a minute left in chapter"
        else -> "${duration(minutes)} left in chapter"
    }

    /** The footer's shorter forms: "6 min left" (chapter) and "3 h 10 min in book". */
    fun footerChapterLabel(minutes: Int?): String? = when (minutes) {
        null -> null
        0, 1 -> "<1 min left"
        else -> "${duration(minutes)} left"
    }

    fun footerBookLabel(minutes: Int?): String? = when (minutes) {
        null -> null
        0, 1 -> "<1 min in book"
        else -> "${duration(minutes)} in book"
    }
}

/** "p. 112", or "pp. 112–113" across a spread; null without a page. */
internal fun printedPageLabel(pageLabel: String?): String? {
    val label = pageLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if ('–' in label) "pp. $label" else "p. $label"
}

/**
 * The controls panel's caption, as the boards write it: the time left in the chapter, then the
 * printed pages ("6 min left in chapter · pages 112–113"). Without a time estimate, [fallback]
 * (pages or percent of the chapter left) stands in for it.
 */
internal fun controlsCaption(timeLeft: String?, pageLabel: String?, fallback: String?): String? {
    val lead = timeLeft ?: fallback
    val page = pageLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { if ('–' in it) "pages $it" else "page $it" }
    return when {
        lead != null && page != null -> "$lead · $page"
        else -> lead ?: page
    }
}

/**
 * The page-list entry for what the reader typed: the page of that label (any case, so "xii" finds
 * "XII"), else, for a number, the last numbered page at or before it. Null when nothing fits.
 */
internal fun findPrintedPage(pages: List<ReaderPageTarget>, query: String): ReaderPageTarget? {
    val wanted = query.trim()
    if (wanted.isEmpty()) return null
    pages.firstOrNull { it.label.equals(wanted, ignoreCase = true) }?.let { return it }
    val number = wanted.toIntOrNull() ?: return null
    return pages.filter { (it.label.toIntOrNull() ?: Int.MAX_VALUE) <= number }.maxByOrNull { it.label.toInt() }
}
