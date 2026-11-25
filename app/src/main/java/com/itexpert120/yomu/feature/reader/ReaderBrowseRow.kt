package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The one row for every reader list (contents, bookmarks, highlights, search results), in Stack's
 * list style: full bleed with the sheet's 24dp gutter, an optional caption above the title, an
 * optional leading mark and trailing action, and a rule underneath. The row being read ([current])
 * sits on a raised, rounded inset instead.
 */
@Composable
internal fun ReaderBrowseRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    current: Boolean = false,
    // Extra start inset for nested entries (table-of-contents depth).
    indent: Dp = 0.dp,
    titleMaxLines: Int = 2,
    titleStyle: TextStyle = StackType.Body,
    divider: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val content: @Composable RowScope.() -> Unit = {
        leading?.invoke(this)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = indent, top = YomuTheme.space.sm, bottom = YomuTheme.space.sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            caption?.let {
                Text(text = it, color = stack.muted, style = StackType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = title,
                color = stack.ink,
                style = if (current) titleStyle.copy(fontWeight = FontWeight.SemiBold) else titleStyle,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke(this)
        if (trailingIcon != null && onTrailingClick != null) {
            IconButton(
                onClick = onTrailingClick,
                colors = IconButtonDefaults.iconButtonColors(contentColor = stack.muted),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(trailingIcon, contentDescription = trailingDescription, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (current) {
        Box(modifier.fillMaxWidth().padding(horizontal = YomuTheme.space.sm, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(stack.raised)
                    .yomuPressable(onClick = onClick, pressedScale = 1f)
                    .padding(start = YomuTheme.space.sm, end = if (onTrailingClick != null) 0.dp else YomuTheme.space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .yomuPressable(onClick = onClick, pressedScale = 1f)
                    .padding(start = YomuTheme.space.lg, end = if (onTrailingClick != null) YomuTheme.space.sm else YomuTheme.space.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
            if (divider) ReaderRule(Modifier.padding(start = YomuTheme.space.lg))
        }
    }
}

/**
 * The one colour swatch in the reader (highlight tints, saved page themes): a plain circle; the
 * selected one gets a check and an ink ring set off by a gap. [innerColor] adds a centre dot (a page
 * theme's text colour on its background).
 */
@Composable
internal fun ReaderSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 44.dp,
    innerColor: Color? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .size(size + if (selected) 10.dp else 0.dp)
            .then(if (selected) Modifier.border(2.dp, stack.ink, CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(color)
            .then(if (!selected && color.luminance() > 0.9f) Modifier.border(1.dp, stack.track, CircleShape) else Modifier)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            innerColor != null -> Box(
                modifier = Modifier
                    .size(size * 0.38f)
                    .clip(CircleShape)
                    .background(innerColor),
            )
            selected -> Icon(
                imageVector = StackIcons.Check,
                contentDescription = null,
                // The swatch is a data colour, so the check picks whichever ink reads on it.
                tint = if (color.luminance() > 0.4f) Color(0xFF16191C) else Color.White,
                modifier = Modifier.size(20.dp),
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

/** "Today, 9:12" · "Mon, 22:40" · "3 Nov" · "3 Nov 2024": when a bookmark or note was made. */
internal fun readerTimestamp(millis: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val daysAgo = ((startOfDay(now) - startOfDay(millis)) / DayMillis).toInt()
    val pattern = when {
        daysAgo == 0 -> "'Today', H:mm"
        daysAgo == 1 -> "'Yesterday', H:mm"
        daysAgo in 2..6 -> "EEE, H:mm"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private const val DayMillis = 24L * 60 * 60 * 1000
