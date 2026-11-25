package com.itexpert120.yomu.feature.bookedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuPressable
import java.io.File

/** The edit sheet starts this far below the status bar, stacked on the book. */
private val SheetTopGap = 18.dp

/** Width of the cover column when the cover sits beside the fields (expanded and up). */
private val CoverColumnWidth = 220.dp

/**
 * Edit details as a Stack sheet stacked on the book: close and Save (the ink pill) in its bar, the
 * cover with Change cover, then the fields. Save waits for a title; while saving, the fields lock
 * and the button says so.
 */
@Composable
fun EditBookScreen(
    state: EditBookUiState,
    onTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onChangeCover: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val stack = YomuTheme.stack
    val focusManager = LocalFocusManager.current
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // The desk under the stacked sheets, dimmed to 80% like everything behind a sheet.
            .background(stack.desk)
            .background(Color.Black.copy(alpha = 0.2f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val coverBeside = YomuWidthClass.fromWidth(maxWidth).isExpanded
        Column(
            modifier = Modifier
                .padding(top = statusTop + SheetTopGap)
                .widthIn(max = if (coverBeside) YomuReadableMaxWidth + CoverColumnWidth else YomuReadableMaxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            EditBar(
                state = state,
                onBack = onBack,
                onSave = {
                    focusManager.clearFocus()
                    onSave()
                },
            )
            when {
                !state.loaded -> EditSkeleton()

                !state.available -> Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
                    YomuEmptyState(
                        icon = StackIcons.Error,
                        title = "Can't edit this book",
                        body = state.error ?: "It's still being added, or its file couldn't be read. Try again in a moment.",
                        actions = { YomuButton(text = "Go back", onClick = onBack) },
                    )
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = navBottom + 32.dp)
                        // Saving: the form locks and steps back while the button says what's happening.
                        .alpha(if (state.busy) 0.55f else 1f),
                ) {
                    if (coverBeside) {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(Modifier.width(CoverColumnWidth), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                EditCover(state.coverImagePath, Modifier.fillMaxWidth())
                                CoverControls(state.editable, onChangeCover)
                            }
                            Column(Modifier.weight(1f)) { EditFields(state, onTitleChange, onSubtitleChange, onAuthorChange, onDescriptionChange) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            EditCover(state.coverImagePath, Modifier.width(96.dp))
                            Column(Modifier.weight(1f)) { CoverControls(state.editable, onChangeCover) }
                        }
                        Spacer(Modifier.height(2.dp))
                        EditFields(state, onTitleChange, onSubtitleChange, onAuthorChange, onDescriptionChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBar(state: EditBookUiState, onBack: () -> Unit, onSave: () -> Unit) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(start = 8.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .yomuPressable(onClick = onBack, enabled = !state.busy)
                .clip(CircleShape)
                .semantics { contentDescription = if (state.available) "Discard and close" else "Close" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(StackIcons.Close, contentDescription = null, tint = if (state.busy) stack.faint else stack.ink)
        }
        Text(
            text = "Edit details",
            style = MaterialTheme.typography.titleLarge,
            color = stack.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (state.available) SaveButton(busy = state.busy, enabled = state.editable && state.title.isNotBlank(), onClick = onSave)
    }
}

/** Save as a small ink pill; disabled it greys out, saving it says so over a thin moving line. */
@Composable
private fun SaveButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    val active = enabled || busy
    Box(
        modifier = Modifier
            .height(40.dp)
            .yomuPressable(onClick = onClick, enabled = enabled)
            .clip(CircleShape)
            .background(if (active) stack.ink else stack.track),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (busy) "Saving…" else "Save",
            style = StackType.Label.copy(fontSize = 14.sp),
            color = if (active) stack.onInk else stack.faint,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        if (busy) {
            LinearProgressIndicator(
                // Sized to the button, not the bar: it must not widen the pill.
                modifier = Modifier.matchParentSize().wrapContentHeight(Alignment.Bottom).height(2.dp),
                color = stack.onInk,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }
    }
}

@Composable
private fun CoverControls(editable: Boolean, onChangeCover: () -> Unit) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .height(44.dp)
            .yomuPressable(onClick = onChangeCover, enabled = editable)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, stack.track, CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(StackIcons.Photo, contentDescription = null, tint = if (editable) stack.ink else stack.faint, modifier = Modifier.size(20.dp))
        Text("Change cover", style = StackType.Label.copy(fontSize = 14.sp), color = if (editable) stack.ink else stack.faint)
    }
    Text(
        text = "Changes how the book looks in Open Reader. The file itself isn't touched.",
        style = StackType.Caption,
        color = stack.muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The save-failure notice and the fields, each with its label. */
@Composable
private fun ColumnScope.EditFields(
    state: EditBookUiState,
    onTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    state.error?.let { message ->
        YomuNotice(
            text = message,
            icon = StackIcons.Error,
            tone = YomuNoticeTone.Error,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )
    }
    // Never flag the title before the book's details have arrived.
    val titleMissing = state.loaded && state.title.isBlank()
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        YomuTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = "Title",
            capitalization = KeyboardCapitalization.Words,
            isError = titleMissing,
            supportingText = if (titleMissing) "Every book needs a title." else null,
        )
        YomuTextField(
            value = state.subtitle,
            onValueChange = onSubtitleChange,
            label = "Subtitle",
            placeholder = "Optional",
            capitalization = KeyboardCapitalization.Words,
        )
        YomuTextField(
            value = state.author,
            onValueChange = onAuthorChange,
            label = "Author",
            capitalization = KeyboardCapitalization.Words,
        )
        YomuTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = "Description",
            placeholder = "Optional",
            singleLine = false,
            minLines = 4,
        )
    }
}

@Composable
private fun EditCover(coverImagePath: String?, modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    Box(
        modifier = modifier
            .aspectRatio(YomuBookCoverAspectRatio)
            .stackCoverShadow(YomuShapeTokens.cover)
            .clip(YomuShapeTokens.cover)
            .background(stack.raised),
        contentAlignment = Alignment.Center,
    ) {
        if (coverImagePath != null) {
            AsyncImage(
                model = File(coverImagePath),
                contentDescription = "Current cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "No cover",
                modifier = Modifier.padding(8.dp),
                color = stack.muted,
                style = StackType.Caption,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Loading: the form's shape in raised blocks. */
@Composable
private fun EditSkeleton() {
    val raised = YomuTheme.stack.raised
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(96.dp).aspectRatio(YomuBookCoverAspectRatio).clip(RoundedCornerShape(4.dp)).background(raised))
            Box(Modifier.width(150.dp).height(44.dp).clip(CircleShape).background(raised))
        }
        repeat(3) { Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(raised)) }
    }
}

private val PreviewState = EditBookUiState(
    loaded = true,
    available = true,
    title = "The Wind in the Willows",
    author = "Kenneth Grahame",
    description = "Mole leaves his spring-cleaning for the river, and with the Water Rat, Badger and the irrepressible Toad finds a life of boats, picnics and motor-cars.",
)

@Preview(name = "Edit light", widthDp = 390, heightDp = 844)
@Composable
private fun EditBookPhonePreview() {
    EditBookPreview(PreviewState, dark = false)
}

@Preview(name = "Edit dark, title missing", widthDp = 390, heightDp = 844)
@Composable
private fun EditBookMissingTitlePreview() {
    EditBookPreview(PreviewState.copy(title = ""), dark = true)
}

@Preview(name = "Edit saving", widthDp = 390, heightDp = 844)
@Composable
private fun EditBookSavingPreview() {
    EditBookPreview(PreviewState.copy(busy = true, subtitle = "Illustrated edition"), dark = false)
}

@Preview(name = "Edit expanded", widthDp = 1100, heightDp = 800)
@Composable
private fun EditBookExpandedPreview() {
    EditBookPreview(PreviewState.copy(error = "Couldn't save this change. Your draft is available to retry."), dark = false)
}

@Preview(name = "Edit unavailable", widthDp = 390, heightDp = 844)
@Composable
private fun EditBookUnavailablePreview() {
    EditBookPreview(EditBookUiState(loaded = true), dark = true)
}

@Composable
private fun EditBookPreview(state: EditBookUiState, dark: Boolean) {
    YomuDesignTheme(darkTheme = dark) {
        EditBookScreen(
            state = state,
            onTitleChange = {},
            onSubtitleChange = {},
            onAuthorChange = {},
            onDescriptionChange = {},
            onChangeCover = {},
            onSave = {},
            onBack = {},
        )
    }
}
