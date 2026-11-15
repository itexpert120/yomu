package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.rememberSelectionMorphShape

/**
 * The one row for every reader browse list (contents, bookmarks, highlights, search results): a
 * segmented list item with an optional leading mark, a title that takes the emphasized weight when
 * [current], optional supporting text, and an optional trailing icon action. Horizontal gutters come
 * from the enclosing sheet, so every list lines up with its header.
 */
@Composable
internal fun ReaderBrowseRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: YomuSettingPosition = YomuSettingPosition.Single,
    supporting: String? = null,
    current: Boolean = false,
    // Extra start inset for nested entries (table-of-contents depth).
    indent: Dp = 0.dp,
    titleMaxLines: Int = 2,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val trailing: (@Composable () -> Unit)? = if (trailingIcon != null && onTrailingClick != null) {
        {
            IconButton(onClick = onTrailingClick, shapes = IconButtonDefaults.shapes()) {
                Icon(trailingIcon, contentDescription = trailingDescription)
            }
        }
    } else {
        null
    }
    val supportingContent: (@Composable () -> Unit)? = supporting?.let { text ->
        {
            Text(
                text = text,
                modifier = Modifier.padding(start = indent),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    SegmentedListItem(
        selected = current,
        onClick = onClick,
        shapes = position.browseRowShapes(),
        modifier = modifier.fillMaxWidth(),
        leadingContent = leading,
        trailingContent = trailing,
        supportingContent = supportingContent,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        content = {
            Text(
                text = title,
                modifier = Modifier.padding(start = indent),
                style = if (current) {
                    MaterialTheme.typography.bodyLargeEmphasized
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun YomuSettingPosition.browseRowShapes(): ListItemShapes = when (this) {
    YomuSettingPosition.Single -> ListItemDefaults.segmentedShapes(index = 0, count = 1)
    YomuSettingPosition.First -> ListItemDefaults.segmentedShapes(index = 0, count = 3)
    YomuSettingPosition.Middle -> ListItemDefaults.segmentedShapes(index = 1, count = 3)
    YomuSettingPosition.Last -> ListItemDefaults.segmentedShapes(index = 2, count = 3)
}

/**
 * The one colour swatch in the reader (highlight tints, saved page themes): a circle that morphs to
 * a scalloped cookie when [selected], like the accent picker in Settings → Appearance. [innerColor]
 * adds a centre dot (a page theme's text colour on its background).
 */
@Composable
internal fun ReaderSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 40.dp,
    innerColor: Color? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = rememberSelectionMorphShape(
        selected = selected,
        unselected = MaterialShapes.Circle,
        selectedPolygon = MaterialShapes.Cookie9Sided,
    )
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .size(size)
            .clip(shape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    // Default indication: the ripple is clipped to the morphing shape.
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (innerColor != null) {
            Box(
                modifier = Modifier
                    .size(size * 0.38f)
                    .clip(CircleShape)
                    .background(innerColor),
            )
        }
    }
}

/**
 * Height cap for a list inside a reader sheet, derived from the window: a little over half the
 * window, but never so tall that the sheet's own header/controls ([reserved]) no longer fit — so
 * phone landscape and tablet dialogs both get a usable list.
 */
@Composable
internal fun readerSheetListMaxHeight(reserved: Dp): Dp {
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) {
        val windowHeight = windowHeightPx.toDp()
        minOf(windowHeight * 0.55f, windowHeight - reserved).coerceAtLeast(120.dp)
    }
}

/** Deepest table-of-contents level that still adds indent; deeper entries align with it. */
internal const val ReaderTocMaxIndentLevels = 4
