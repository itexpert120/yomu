package com.itexpert120.yomu.app.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.PersistableBundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.itexpert120.yomu.MainActivity
import com.itexpert120.yomu.R
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.feature.library.pileBooks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/** The book "Continue" opens: what the shortcut shows. Equal targets never republish it. */
internal data class ContinueTarget(val bookId: String, val title: String, val coverPath: String?)

/**
 * The book the launcher's "Continue" shortcut opens: the front of the library's pile, i.e. the most
 * recently opened book in progress (not finished). Null without one.
 */
internal fun continueTarget(books: List<Book>): ContinueTarget? = books.pileBooks().firstOrNull()
    ?.let { ContinueTarget(it.id.value, it.title, it.coverImagePath) }

/** [title] in at most [room] characters, cut at a word where it can be and ended with "…". */
internal fun fitTitle(title: String, room: Int): String {
    val trimmed = title.trim()
    if (trimmed.length <= room) return trimmed
    if (room <= 1) return "…"
    val cut = trimmed.take(room - 1)
    val atWord = cut.substringBeforeLast(' ', "").takeIf { it.length >= cut.length / 2 } ?: cut
    return atWord.trimEnd(' ', ',', ':', ';', '.', '-') + "…"
}

/**
 * Keeps the dynamic "Continue <book>" launcher shortcut on the [continueTarget]: published when that
 * book changes (another book opened, its title or cover edited), removed when there is none (the
 * book finished or deleted). Page turns change only the book's progress, so they never reach the
 * ShortcutManager; nor does a cold start that finds the shortcut already current.
 */
@Singleton
class ContinueReadingShortcut @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            books.observeBooks()
                .map(::continueTarget)
                .distinctUntilChanged()
                // A reader closing can write a few rows in a row; publish what they settle on.
                .debounce(SETTLE_MS)
                .collect { target ->
                    try {
                        publish(target)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        // Rate limited, user locked, launcher gone: the next change tries again.
                        Log.w(TAG, "Couldn't update the Continue shortcut", e)
                    }
                }
        }
    }

    private fun publish(target: ContinueTarget?) {
        if (target == null) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID))
            return
        }
        val current = ShortcutManagerCompat.getDynamicShortcuts(context).firstOrNull { it.id == SHORTCUT_ID }
        if (current?.extras?.let(::targetOf) == target) return
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_continue_short))
            .setLongLabel(longLabel(target.title))
            .setIcon(coverIcon(target.coverPath) ?: IconCompat.createWithResource(context, R.drawable.ic_shortcut_continue))
            .setIntent(
                Intent(AppShortcut.ACTION_CONTINUE)
                    .setClassName(context, MainActivity::class.java.name)
                    .putExtra(AppShortcut.EXTRA_BOOK_ID, target.bookId),
            )
            .setExtras(extrasOf(target))
            .setRank(0)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    // Launchers fall back to the short label ("Continue") when the long one doesn't fit, so a long
    // title is shortened to keep the label within the recommended length.
    private fun longLabel(title: String): String {
        val room = LONG_LABEL_MAX - context.getString(R.string.shortcut_continue_long, "").length
        return context.getString(R.string.shortcut_continue_long, fitTitle(title, room))
    }

    /**
     * The cover on the launcher's icon background, inside the adaptive icon's safe circle, with the
     * covers' small rounded corners. Null when there is no readable cover.
     */
    private fun coverIcon(path: String?): IconCompat? {
        val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
        val size = min(ShortcutManagerCompat.getIconMaxWidth(context), MAX_ICON_PX).coerceAtLeast(MIN_ICON_PX)
        val coverHeight = (size * COVER_HEIGHT_SHARE).roundToInt()
        val cover = decodeCover(file, coverHeight) ?: return null
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(ContextCompat.getColor(context, R.color.yomu_launcher_background))
        val coverWidth = coverHeight * cover.width / cover.height.toFloat()
        val bounds = RectF((size - coverWidth) / 2f, (size - coverHeight) / 2f, (size + coverWidth) / 2f, (size + coverHeight) / 2f)
        val radius = size * CORNER_SHARE
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(cover, Rect(0, 0, cover.width, cover.height), bounds, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        cover.recycle()
        return IconCompat.createWithAdaptiveBitmap(icon)
    }

    private fun decodeCover(file: File, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= height) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun extrasOf(target: ContinueTarget) = PersistableBundle().apply {
        putString(EXTRA_ID, target.bookId)
        putString(EXTRA_TITLE, target.title)
        putString(EXTRA_COVER, target.coverPath)
    }

    private fun targetOf(extras: PersistableBundle): ContinueTarget? {
        val id = extras.getString(EXTRA_ID) ?: return null
        return ContinueTarget(id, extras.getString(EXTRA_TITLE).orEmpty(), extras.getString(EXTRA_COVER))
    }

    private companion object {
        const val TAG = "ContinueShortcut"
        const val SHORTCUT_ID = "continue"
        const val SETTLE_MS = 1_000L
        const val LONG_LABEL_MAX = 25
        const val EXTRA_ID = "bookId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COVER = "cover"

        // An adaptive icon is 108 units with a 72-unit visible area; a 2:3 cover this tall fits
        // inside the circle launchers mask it to.
        const val COVER_HEIGHT_SHARE = 0.55f
        const val CORNER_SHARE = 0.02f
        const val MAX_ICON_PX = 432
        const val MIN_ICON_PX = 108
    }
}
