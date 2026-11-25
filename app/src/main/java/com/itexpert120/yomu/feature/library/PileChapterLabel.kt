package com.itexpert120.yomu.feature.library

/**
 * The pile's short "where you are" line, as the boards show it ("Chapter 17"): a numbered chapter
 * title keeps only its number part ("Chapter 124: Wrapping Up Work" → "Chapter 124"), so it never
 * trails off in the narrow column. Titles without a numbered label stay whole.
 */
internal fun pileChapterLabel(title: String): String {
    val trimmed = title.trim()
    val match = NumberedLabel.find(trimmed) ?: return trimmed
    return match.groupValues[1].trim().ifEmpty { trimmed }
}

private val NumberedLabel = Regex(
    """^((?:chapter|ch\.?|part|book|volume|vol\.?|section|episode|act)\s+[\p{L}\p{N}]+)\s*(?::|\.|-|–|—|\|)\s*\S.*$""",
    RegexOption.IGNORE_CASE,
)
