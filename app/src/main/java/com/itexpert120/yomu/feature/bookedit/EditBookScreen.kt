package com.itexpert120.yomu.feature.bookedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import java.io.File

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
    YomuScreenScaffold(
        title = "Edit details",
        onBack = onBack,
        trailing = {
            // The page's single primary action lives in the app bar so it stays reachable above
            // the keyboard.
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                enabled = state.editable && state.title.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Save")
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EditCover(coverImagePath = state.coverImagePath, modifier = Modifier.width(96.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Cover",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
                YomuButton(
                    text = "Change cover",
                    enabled = state.editable,
                    onClick = onChangeCover,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
                Text(
                    text = "Editing changes only the stored details, not the EPUB file.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        YomuTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = "Title",
            capitalization = KeyboardCapitalization.Words,
            isError = state.title.isBlank(),
            supportingText = if (state.title.isBlank()) "A title is required" else null,
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
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun EditCover(coverImagePath: String?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    if (coverImagePath != null) {
        AsyncImage(
            model = File(coverImagePath),
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(1f / 1.6f)
                .clip(shape)
                .background(YomuTheme.colors.surfaceRaised),
        )
    } else {
        Box(
            modifier = modifier
                .aspectRatio(1f / 1.6f)
                .clip(shape)
                .background(YomuTheme.colors.surfaceRaised)
                .border(1.dp, YomuTheme.colors.border, shape)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No cover",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }
    }
}
