package com.itexpert120.yomu.core.reader

/**
 * One spoken unit of the book: usually a sentence, as the page splits it.
 *
 * @property id stable within its chapter for as long as the chapter is loaded; the reader engine
 *   uses it to find the segment's text in the page again ([ReadAloudSource.highlight]).
 * @property text exactly the string the TTS engine is given and the string every character range
 *   is measured against (UTF-16 code units, which are Kotlin/Java and JavaScript string indices
 *   alike). Never blank. Kept under [MAX_LENGTH] by the page, which splits run-on sentences at a
 *   clause or word boundary.
 * @property language BCP-47 tag (`xml:lang`/`lang` of the block, else the book's), or null for the
 *   chapter's language.
 * @property locator where the segment starts; also what the reader saves as the reading position
 *   while listening.
 */
data class ReadAloudSegment(
    val id: String,
    val text: String,
    val language: String?,
    val locator: ReaderLocator,
) {
    companion object {
        const val MAX_LENGTH = 1_000
    }
}

/**
 * One section (reading-order resource) of segments, in reading order.
 *
 * @property key identifies the section to the source (its href, or index); opaque to the player.
 * @property startIndex the first segment at or after the position the chapter was asked from
 *   (0 when a whole chapter was asked for).
 */
data class ReadAloudChapter(
    val key: String,
    val title: String?,
    val language: String?,
    val segments: List<ReadAloudSegment>,
    val startIndex: Int = 0,
)

/**
 * Feeds whole-book read-aloud: the reader engine's side of the contract, implemented over the open
 * reading page. All calls come from the main thread; any of them may throw to report that the page
 * is gone, which ends read-aloud with an error.
 *
 * Expected page glue (foliate engine; `yomu-reader.js`), all synchronous JS returning JSON:
 *
 * - `yomuReadAloudSegments(cfi)` → the section containing `cfi` (or the current position when
 *   `cfi` is null), split with foliate's `tts.js`/`Intl.Segmenter` into sentences:
 *   `{"section": 12, "href": "OEBPS/ch3.xhtml", "title": "Chapter 3", "lang": "en",
 *     "start": 4, "segments": [{"id": "12:0", "text": "…", "cfi": "epubcfi(…)", "lang": null}, …]}`.
 *   `start` is the index of the first segment whose range ends after `cfi`. Ids are
 *   `"<section>:<n>"` so they stay unique across chapters. Image-only sections give `[]`.
 * - `yomuReadAloudSection(index)` → the same shape for a whole section (`start` 0), built from the
 *   section's document without displaying it; `null` past either end of the book. Used for
 *   [nextChapter]/[previousChapter], so read-aloud can run ahead of what is on screen.
 * - `yomuReadAloudHighlight(id, start, end)` → draws the read-aloud mark on segment `id` (and the
 *   word `start..end`, UTF-16 offsets into the segment's `text`, when both are ≥ 0) and turns the
 *   page / scrolls so the segment stays on screen. When `id`'s section isn't displayed, the engine
 *   first navigates to the segment's locator (CFI), then highlights. Returns `true` when drawn.
 * - `yomuReadAloudClear()` → removes the mark.
 *
 * Text rule: the page builds `text` from the DOM range's text with whitespace collapsed, and keeps
 * its own map from those offsets back to DOM positions for the word highlight.
 */
interface ReadAloudSource {
    /** The chapter containing [start] (the current position when null), or null if there is none. */
    suspend fun segmentsFrom(start: ReaderLocator?): ReadAloudChapter?

    /** The whole chapter after [chapter] in reading order, or null at the end of the book. */
    suspend fun nextChapter(chapter: ReadAloudChapter): ReadAloudChapter?

    /** The whole chapter before [chapter] in reading order, or null at the start of the book. */
    suspend fun previousChapter(chapter: ReadAloudChapter): ReadAloudChapter?

    /**
     * Marks [segment] as being read and keeps it on screen, navigating to it when needed.
     * [range] is the word being spoken (UTF-16 indices into [ReadAloudSegment.text]; pass
     * `range.first` and `range.last + 1` as the page's `start`/`end`), when the TTS engine reports
     * words.
     */
    suspend fun highlight(segment: ReadAloudSegment, range: IntRange?)

    suspend fun clearHighlight()
}
