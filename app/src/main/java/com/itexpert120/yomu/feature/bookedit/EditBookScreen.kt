package com.itexpert120.yomu.feature.bookedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    YomuScreenScaffold(title = "Edit details", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EditCover(coverImagePath = state.coverImagePath, modifier = Modifier.width(96.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Cover",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
                YomuButton(
                    text = "Change cover",
                    onClick = onChangeCover,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
                Text(
                    text = "Editing changes only the stored details, not the EPUB file.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
        }

        YomuTextField(value = state.title, onValueChange = onTitleChange, label = "Title")
        YomuTextField(
            value = state.subtitle,
            onValueChange = onSubtitleChange,
            label = "Subtitle",
            placeholder = "Optional",
        )
        YomuTextField(value = state.author, onValueChange = onAuthorChange, label = "Author")
        YomuTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = "Description",
            placeholder = "Optional",
            singleLine = false,
            minLines = 4,
        )

        YomuButton(
            text = "Save",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            emphasis = YomuButtonEmphasis.Primary,
        )
    }
}

@Composable
private fun EditCover(coverImagePath: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(YomuTheme.radius.md)
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
