package com.itexpert120.yomu.widget

import android.content.Context
import android.content.Intent
import com.itexpert120.yomu.MainActivity

/**
 * Contract for the home-screen widgets to deep-link into the app. Tapping the "Continue reading"
 * widget launches [MainActivity] with [EXTRA_OPEN_BOOK_ID]; the activity reads it and routes the
 * nav host straight into the reader for that book. This mirrors how an external "Open with" intent
 * is handled — a launch intent the app inspects on start.
 */
object WidgetDeepLink {

    /** String extra carrying the book id the widget wants opened in the reader. */
    const val EXTRA_OPEN_BOOK_ID = "com.itexpert120.yomu.widget.OPEN_BOOK_ID"

    /** A launch intent that opens [bookId] in the reader (or just the library when null). */
    fun launchIntent(context: Context, bookId: String?): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (bookId != null) putExtra(EXTRA_OPEN_BOOK_ID, bookId)
        }
}
