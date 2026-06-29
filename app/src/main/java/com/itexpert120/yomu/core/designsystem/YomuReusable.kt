package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

@Composable
fun YomuDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (expanded) {
        Popup(
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ) + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                ),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                ) + fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                ),
            ) {
                Box(modifier = modifier) {
                    content()
                }
            }
        }
    }
}

@Composable
fun YomuDropdownMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.sm))
            .background(if (selected) YomuTheme.colors.accentSoft else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) YomuTheme.colors.accent else YomuTheme.colors.textPrimary,
            style = YomuTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "\u2713",
                color = YomuTheme.colors.accent,
                style = YomuTheme.type.body,
            )
        }
    }
}

@Composable
fun YomuDropdownMenuContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(YomuTheme.radius.md))
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.panel)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md))
            .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
fun YomuPillFilter(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.pill))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        Text(text = value, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.caption)
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = YomuTheme.colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun YomuCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .yomuPressable(onClick = onClick)
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(YomuTheme.colors.textPrimary),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun YomuCircleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = YomuTheme.colors.appBackground,
) {
    YomuCircleIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A custom, on-brand drag grip for [YomuBottomSheet]s. */
@Composable
fun YomuSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .background(YomuTheme.colors.border),
        )
    }
}

/**
 * Draggable bottom sheet that slides up (Material modal sheet, restyled with Yomu colors + a
 * custom drag handle). Programmatic dismissals animate the slide-down before tearing it out of
 * composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    // Sheets with their own scroll container (e.g. a LazyColumn) should pass false.
    scrollable: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    if (!visible) return
    // Open at the height the content needs rather than a half-expanded stop; tall content is then
    // capped to a reasonable height and scrolls internally instead of filling the whole screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun animatedDismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    // Cap tall sheets at 60% of the actual window height (containerSize, not Configuration).
    val maxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.60f).toDp()
    }
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = YomuTheme.colors.panel,
        contentColor = YomuTheme.colors.textPrimary,
        dragHandle = { YomuSheetDragHandle() },
        // Draw the panel edge-to-edge under the (transparent) gesture nav bar instead of reserving it
        // as bottom inset — otherwise the panel stops above the bar, leaving a coloured band. The nav
        // clearance is moved onto the content below so text/controls still sit above the bar.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrollable) {
                        Modifier
                            .heightIn(max = maxHeight)
                            // Fade content into the panel at whichever edge has more to scroll, so a
                            // tall sheet doesn't hard-cut under the drag handle or at its bottom.
                            .yomuScrollEdgeShadow(
                                color = YomuTheme.colors.panel,
                                top = scrollState.canScrollBackward,
                                bottom = scrollState.canScrollForward,
                            )
                            .verticalScroll(scrollState)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content(::animatedDismiss)
        }
    }
}

@Composable
fun <T> YomuOptionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    label: (T) -> String = { (it as Enum<*>).name.replaceFirstChar { c -> c.titlecase() } },
) where T : Enum<T> {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        Text(
            text = title,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.title,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        options.forEach { option ->
            val isSelected = option == selectedOption
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .yomuPressable(
                        onClick = {
                            onSelect(option)
                            dismiss()
                        },
                    )
                    .clip(RoundedCornerShape(YomuTheme.radius.md))
                    .background(if (isSelected) YomuTheme.colors.accentSoft else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) YomuTheme.colors.accent else YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.body,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = YomuTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .yomuPressable(onClick = dismiss)
                .clip(RoundedCornerShape(YomuTheme.radius.md))
                .background(YomuTheme.colors.surface)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }
    }
}
