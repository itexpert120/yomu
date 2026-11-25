package com.itexpert120.yomu.core.reader

/**
 * A Yomu-owned text highlight. Crosses the reader-engine boundary as plain data + a [locatorJson]
 * string, exactly like [ReaderLocator] — no engine types leak. [colorArgb] is a packed ARGB int.
 * [modifiedAt] is when the colour, style or note last changed; 0 = never edited.
 */
data class ReaderHighlight(
    val id: String,
    val locatorJson: String,
    val text: String,
    val colorArgb: Int,
    val createdAt: Long,
    val note: String? = null,
    val style: HighlightStyle = HighlightStyle.Highlight,
    val modifiedAt: Long = 0L,
) {
    /** When this highlight last changed: its last edit, else its creation. */
    val updatedAt: Long get() = maxOf(createdAt, modifiedAt)
}

/**
 * How a highlight is drawn. [key] is the stored and synced name; it matches foliate-js's Overlayer
 * drawing functions (highlight / underline / squiggly / strikethrough).
 */
enum class HighlightStyle(val key: String) {
    Highlight("highlight"),
    Underline("underline"),
    Squiggly("squiggly"),
    Strikethrough("strikethrough"),
    ;

    companion object {
        /** An unknown name (say, a style from a newer build) draws as a plain highlight. */
        fun fromKey(key: String?): HighlightStyle = entries.firstOrNull { it.key == key } ?: Highlight
    }
}
