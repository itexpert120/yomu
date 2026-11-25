package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.itexpert120.yomu.feature.annotations.AnnotationNotice
import kotlinx.coroutines.delay

/** How long Stack's notices stay up. */
private const val NoticeMillis = 4_000L

/**
 * "Highlight deleted · Undo" by the thumb, for 4 seconds; Undo puts the same
 * row back. [onTimeout] gets the notice's key so a newer deletion's notice is never the one closed.
 */
@Composable
internal fun AnnotationUndoNotice(undo: AnnotationUndo?, onUndo: (Long) -> Unit, onTimeout: (Long) -> Unit) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(undo?.key) {
        val key = undo?.key ?: return@LaunchedEffect
        delay(NoticeMillis)
        currentOnTimeout(key)
    }
    ReaderFloatingNotice(value = undo, placement = ReaderNoticePlacement.Bottom) { shown ->
        ReaderNoticeCard(text = shown.message) {
            ReaderNoticeAction("Undo", onClick = { onUndo(shown.key) })
        }
    }
}

/** The result of an export or import of highlights ("Imported 12 highlights."), by the thumb. */
@Composable
internal fun AnnotationTransferNotice(notice: AnnotationNotice?, onTimeout: (Long) -> Unit) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(notice?.key) {
        val key = notice?.key ?: return@LaunchedEffect
        delay(NoticeMillis)
        currentOnTimeout(key)
    }
    ReaderFloatingNotice(value = notice, placement = ReaderNoticePlacement.Bottom) { shown ->
        ReaderNoticeCard(text = shown.text)
    }
}

/** The look-up sheet's requests as reader actions. */
internal fun wordLookupActions(onAction: (ReaderAction) -> Unit) = WordLookupActions(
    onPronounce = { onAction(ReaderAction.Pronounce(it)) },
    onLookUpWord = { onAction(ReaderAction.LookUpWord(it)) },
    onBack = { onAction(ReaderAction.LookupBack) },
    onRetry = { onAction(ReaderAction.RetryLookup) },
    onSelectTab = { onAction(ReaderAction.SelectLookupTab(it)) },
    onLookUpInEnglish = { onAction(ReaderAction.LookUpInEnglish) },
    onRetryWikipedia = { onAction(ReaderAction.RetryWikipedia) },
    onTranslate = { onAction(ReaderAction.Translate(it)) },
    onSearchInBook = { onAction(ReaderAction.SearchInBook(it)) },
)
