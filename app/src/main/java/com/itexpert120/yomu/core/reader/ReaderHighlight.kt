package com.itexpert120.yomu.core.reader

/**
 * A Yomu-owned text highlight. Crosses the reader-engine boundary as plain data + a [locatorJson]
 * string, exactly like [ReaderLocator] — no engine types leak. [colorArgb] is a packed ARGB int.
 */
data class ReaderHighlight(
    val id: String,
    val locatorJson: String,
    val text: String,
    val colorArgb: Int,
    val createdAt: Long,
)
