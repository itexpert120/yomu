package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.feature.annotations.AnnotationTransferAction
import com.itexpert120.yomu.feature.annotations.AnnotationTransferHost
import com.itexpert120.yomu.feature.annotations.rememberAnnotationImportPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** How long a notice (e.g. the gallery-save result) stays up. */
private const val NoticeMillis = 4_000L

// The ViewModel resolves its bookId from the type-safe route via SavedStateHandle.
@Composable
fun BookDetailsRoute(
    onBack: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onOpenChapter: (String) -> Unit,
    libraryBeside: Boolean = false,
    resume: BookSheetResume? = null,
) {
    val viewModel: BookDetailsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val annotationTransfer by viewModel.annotationTransfer.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    val pickAnnotationFile = rememberAnnotationImportPicker(viewModel::onAnnotationAction)
    val onAnnotationAction: (AnnotationTransferAction) -> Unit = { action ->
        if (action == AnnotationTransferAction.PickFile) pickAnnotationFile() else viewModel.onAnnotationAction(action)
    }

    // One-shot messages (e.g. the gallery-save result) as Stack's floating notice.
    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            notice = message
            delay(NoticeMillis)
            notice = null
        }
    }
    // An export or import result, in the same notice.
    LaunchedEffect(annotationTransfer.notice) {
        val result = annotationTransfer.notice ?: return@LaunchedEffect
        notice = result.text
        delay(NoticeMillis)
        notice = null
        viewModel.onAnnotationAction(AnnotationTransferAction.DismissNotice(result.key))
    }

    Box(Modifier.fillMaxSize()) {
        BookDetailsScreen(
            book = state.book,
            bookLoaded = !state.loading,
            toc = toc,
            libraryBeside = libraryBeside,
            resume = resume,
            onBack = onBack,
            onRead = onRead,
            onEdit = onEdit,
            onRemove = {
                viewModel.remove()
                onBack()
            },
            onSaveCover = viewModel::saveCoverToGallery,
            onTocSortChange = viewModel::onTocSortChange,
            onOpenChapter = onOpenChapter,
            onSetChapterRead = viewModel::onSetChapterRead,
            onEnterChapterSelection = viewModel::onEnterChapterSelection,
            onStartChapterSelection = viewModel::onStartChapterSelection,
            onToggleChapterSelection = viewModel::onToggleChapterSelection,
            onToggleChapterBookmark = viewModel::onToggleChapterBookmark,
            onExitChapterSelection = viewModel::onExitChapterSelection,
            onSelectAllChapters = viewModel::onSelectAllChapters,
            onDeselectAllChapters = viewModel::onDeselectAllChapters,
            onMarkSelectedChapters = viewModel::onMarkSelectedChapters,
            onMarkPreviousRead = viewModel::onMarkPreviousRead,
            organize = state.organize,
            onSetStatus = viewModel::onSetStatus,
            onSetCollection = viewModel::onSetCollection,
            onCreateCollection = viewModel::onCreateCollection,
            onToggleVolume = viewModel::onToggleVolume,
            onCollapseAllVolumes = viewModel::onCollapseAllVolumes,
            onExpandAllVolumes = viewModel::onExpandAllVolumes,
            calibreHighlights = annotationTransfer.calibreAvailable,
            onAnnotationAction = onAnnotationAction,
        )
        AnnotationTransferHost(
            state = annotationTransfer,
            onAction = onAnnotationAction,
            shares = viewModel.annotationShares,
        )
        AnimatedVisibility(
            visible = notice != null,
            enter = yomuChromeEnter(),
            exit = yomuChromeExit(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .widthIn(max = 568.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // Keeps the last text while the notice slides away.
            val text = remember { mutableStateOf("") }
            notice?.let { text.value = it }
            YomuNotice(text = text.value, tone = YomuNoticeTone.Floating, modifier = Modifier.fillMaxWidth())
        }
    }
}
