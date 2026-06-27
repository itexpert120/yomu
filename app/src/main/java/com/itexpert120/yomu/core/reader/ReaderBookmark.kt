package com.itexpert120.yomu.core.reader

/**
 * A Yomu-owned reading-position bookmark. Anchored to a [locatorJson] (the engine-native position),
 * exactly like [ReaderHighlight] — no engine types leak. [progression] is the whole-book progression
 * (0..1), used for ordering and current-page detection.
 */
data class ReaderBookmark(
    val id: String,
    val locatorJson: String,
    val href: String?,
    val chapterTitle: String?,
    val progression: Double,
    val createdAt: Long,
)
