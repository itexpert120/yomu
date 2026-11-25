@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable

/*
 * Stack's settings list, shared by Settings, Sync, About and the reading-defaults pages: muted
 * 13/600 section labels, 56dp rows with a muted 24dp leading icon, 15 text, a 13 muted value and a
 * chevron, separated by hairline rules. Rows sit directly on a sheet (no cards, no containers).
 */

/** The value / supporting-line style of a settings row. */
internal val SettingsCaptionStyle: TextStyle = StackType.Caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp)

/** Title of a pushed settings page and of its sheets (17/600). */
internal val SettingsPageTitleStyle: TextStyle = StackType.Heading.copy(fontSize = 17.sp, lineHeight = 22.sp)

private val ValueStyle: TextStyle = StackType.Label.copy(fontWeight = FontWeight.Normal)

/** A group's name above its rows: 13/600 muted, sentence case. */
@Composable
internal fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier, top: Dp = 20.dp) {
    Text(
        text = text,
        style = StackType.Label,
        color = YomuTheme.stack.muted,
        modifier = modifier
            .padding(top = top, bottom = 2.dp)
            .semantics { heading() },
    )
}

/**
 * One settings row. [onClick] makes the whole row a button; [value] and [chevron] point onward,
 * [trailing] holds a control. [divider] draws the rule under the row (off for a group's last row).
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    chevron: Boolean = false,
    divider: Boolean = true,
    role: Role = Role.Button,
    // Titles that should read stronger (e.g. the selected item in a tablet list).
    emphasized: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val ruleColor = stack.rule
    val titleColor = when {
        destructive -> stack.danger
        else -> stack.ink
    }
    val iconColor = if (destructive) stack.danger else stack.muted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.yomuPressable(onClick = onClick, enabled = enabled, role = role) else Modifier)
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val dim = if (enabled) Modifier else Modifier.alpha(DisabledAlpha)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = dim.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f).then(dim)) {
            Text(
                text = title,
                style = if (emphasized) StackType.Body.copy(fontWeight = FontWeight.SemiBold) else StackType.Body,
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = SettingsCaptionStyle,
                    color = stack.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(text = value, style = ValueStyle, color = stack.muted, maxLines = 1, modifier = dim)
        }
        trailing?.invoke(this)
        if (chevron) {
            Icon(StackIcons.KeyboardArrowRight, contentDescription = null, tint = stack.faint, modifier = dim)
        }
    }
}

/** A row whose whole surface flips a switch. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        subtitle = subtitle,
        enabled = enabled,
        divider = divider,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
    ) {
        YomuTogglePill(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A short explanatory paragraph on a settings page (14.5 body colour). */
@Composable
internal fun SettingsParagraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = StackType.BodySmall,
        color = YomuTheme.stack.body,
        modifier = modifier,
    )
}

/** A raised, rounded inset carrying an icon and a short message (status or summary). */
@Composable
internal fun SettingsInset(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(stack.raised, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = stack.muted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), content = content)
    }
}

/**
 * A page pushed from Settings or About: a back button and 17/600 title on [background], then
 * [content] in a scrolling column with the 24dp gutter, capped to a readable width.
 */
@Composable
internal fun SettingsSubpage(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    background: Color = YomuTheme.stack.sheet,
    contentPadding: Dp = 24.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        if (onBack != null || title != null) {
            SettingsTopRow(title = title, onBack = onBack)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = YomuReadableMaxWidth + contentPadding * 2)
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}

/** Back button + page title, as on every pushed settings page. */
@Composable
internal fun SettingsTopRow(title: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(StackIcons.ArrowBack, contentDescription = "Back", tint = YomuTheme.stack.ink)
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = SettingsPageTitleStyle,
                color = YomuTheme.stack.ink,
                modifier = Modifier
                    .padding(start = if (onBack != null) 4.dp else 12.dp)
                    .semantics { heading() },
            )
        }
    }
}

private const val DisabledAlpha = 0.45f
