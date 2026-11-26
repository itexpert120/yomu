package com.itexpert120.yomu.app.shortcuts

/**
 * A launcher quick action (long-press on the app icon): the static ones in `res/xml/shortcuts.xml`
 * and the dynamic "Continue <book>" ([ContinueReadingShortcut]). Each arrives at MainActivity as an
 * intent with its own action ([parse]); the nav host then opens the reader, or the library's search
 * or import, or Statistics.
 */
sealed interface AppShortcut {
    val action: String

    /** Opens the reader on [bookId] where it was left. */
    data class Continue(val bookId: String) : AppShortcut {
        override val action get() = ACTION_CONTINUE
    }

    data object SearchLibrary : AppShortcut {
        override val action get() = ACTION_SEARCH
    }

    data object ImportBooks : AppShortcut {
        override val action get() = ACTION_IMPORT
    }

    data object Statistics : AppShortcut {
        override val action get() = ACTION_STATISTICS
    }

    /** One string (action, then the book for Continue), for saved state. */
    fun encode(): String = if (this is Continue) "$action|$bookId" else action

    companion object {
        // Also named in res/xml/shortcuts.xml.
        const val ACTION_CONTINUE = "com.itexpert120.yomu.shortcut.CONTINUE"
        const val ACTION_SEARCH = "com.itexpert120.yomu.shortcut.SEARCH"
        const val ACTION_IMPORT = "com.itexpert120.yomu.shortcut.IMPORT"
        const val ACTION_STATISTICS = "com.itexpert120.yomu.shortcut.STATISTICS"
        const val EXTRA_BOOK_ID = "bookId"

        /** The shortcut an intent's [action] (and [bookId] extra) asks for; null for any other intent. */
        fun parse(action: String?, bookId: String?): AppShortcut? = when (action) {
            ACTION_CONTINUE -> bookId?.takeIf { it.isNotBlank() }?.let(::Continue)
            ACTION_SEARCH -> SearchLibrary
            ACTION_IMPORT -> ImportBooks
            ACTION_STATISTICS -> Statistics
            else -> null
        }

        fun decode(encoded: String): AppShortcut? = parse(encoded.substringBefore('|'), encoded.substringAfter('|', ""))
    }
}
