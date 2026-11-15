package com.itexpert120.yomu.feature.bookedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuBadgeShape
import com.itexpert120.yomu.core.designsystem.YomuBadgeTone
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuContentMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingIndicator
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import java.io.File

/** Width of the cover pane when the cover sits beside the fields (expanded and up). */
private val CoverPaneWidth = 264.dp

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
    val focusManager = LocalFocusManager.current
    // Measured width, so a navigation rail or split screen picks the right layout.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = YomuWidthClass.fromWidth(maxWidth).isExpanded
        YomuScreenScaffold(
            title = "Edit details",
            onBack = onBack,
            maxContentWidth = if (twoPane) YomuContentMaxWidth else YomuReadableMaxWidth,
            trailing = {
                // The page's single primary action lives in the app bar so it stays reachable
                // above the keyboard. While a save or cover copy runs it gives way to a loader.
                when {
                    state.busy -> YomuLoadingIndicator(
                        modifier = Modifier
                            .padding(end = YomuTheme.space.md)
                            .size(40.dp),
                    )

                    state.available -> Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSave()
                        },
                        enabled = state.editable && state.title.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = YomuTheme.space.xs),
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Save")
                    }

                    else -> Unit
                }
            },
        ) {
            when {
                !state.loaded -> YomuLoadingState(
                    message = "Loading details…",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = YomuTheme.space.xxl),
                )

                !state.available -> YomuEmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Can't edit this book",
                    body = state.error ?: "This book is no longer available.",
                    shape = YomuBadgeShape.Burst,
                    tone = YomuBadgeTone.Error,
                    actions = {
                        YomuButton(
                            text = "Go back",
                            onClick = onBack,
                            emphasis = YomuButtonEmphasis.Secondary,
                        )
                    },
                )

                twoPane -> Row(
                    horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.width(CoverPaneWidth),
                        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                    ) {
                        EditCover(
                            coverImagePath = state.coverImagePath,
                            busy = state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CoverControls(editable = state.editable, onChangeCover = onChangeCover)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
                    ) {
                        EditFields(
                            state = state,
                            onTitleChange = onTitleChange,
                            onSubtitleChange = onSubtitleChange,
                            onAuthorChange = onAuthorChange,
                            onDescriptionChange = onDescriptionChange,
                        )
                    }
                }

                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditCover(
                            coverImagePath = state.coverImagePath,
                            busy = state.busy,
                            modifier = Modifier.width(96.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                        ) {
                            CoverControls(editable = state.editable, onChangeCover = onChangeCover)
                        }
                    }
                    EditFields(
                        state = state,
                        onTitleChange = onTitleChange,
                        onSubtitleChange = onSubtitleChange,
                        onAuthorChange = onAuthorChange,
                        onDescriptionChange = onDescriptionChange,
                    )
                }
            }
        }
    }
}

/** Cover label, the change action and the scope note; the caller's column spaces them. */
@Composable
private fun CoverControls(editable: Boolean, onChangeCover: () -> Unit) {
    YomuSectionLabel("Cover")
    YomuButton(
        text = "Change cover",
        enabled = editable,
        onClick = onChangeCover,
        emphasis = YomuButtonEmphasis.Secondary,
    )
    Text(
        text = "Editing changes only the stored details, not the EPUB file.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** The save-failure notice and the metadata fields; the caller's column spaces them. */
@Composable
private fun EditFields(
    state: EditBookUiState,
    onTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    state.error?.let { message ->
        YomuNotice(
            text = message,
            icon = Icons.Rounded.ErrorOutline,
            tone = YomuNoticeTone.Error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // Never flag the title before the book's details have arrived.
    val titleMissing = state.loaded && state.title.isBlank()
    YomuTextField(
        value = state.title,
        onValueChange = onTitleChange,
        label = "Title",
        capitalization = KeyboardCapitalization.Words,
        isError = titleMissing,
        supportingText = if (titleMissing) "A title is required" else null,
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

@Composable
private fun EditCover(coverImagePath: String?, busy: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(YomuBookCoverAspectRatio)
            .clip(YomuShapeTokens.cover)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (coverImagePath != null) {
            AsyncImage(
                model = File(coverImagePath),
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "No cover",
                modifier = Modifier.padding(YomuTheme.space.xs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        // A cover copy or save is in flight.
        if (busy) YomuLoadingIndicator(contained = true)
    }
}

@Preview(widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun EditBookPhonePreview() {
    EditBookPreview(
        EditBookUiState(loaded = true, available = true, title = "The Quiet Archive", author = "A. Reader"),
    )
}

@Preview(widthDp = 1100, heightDp = 800, showBackground = true)
@Composable
private fun EditBookExpandedPreview() {
    EditBookPreview(
        EditBookUiState(
            loaded = true,
            available = true,
            busy = true,
            error = "Couldn't save this change. Your draft is available to retry.",
            title = "",
            author = "A. Reader",
        ),
    )
}

@Preview(widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun EditBookUnavailablePreview() {
    EditBookPreview(EditBookUiState(loaded = true, error = "This book is no longer available."))
}

@Composable
private fun EditBookPreview(state: EditBookUiState) {
    YomuDesignTheme {
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
