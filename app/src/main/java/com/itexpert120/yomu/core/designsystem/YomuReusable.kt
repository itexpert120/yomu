package com.itexpert120.yomu.core.designsystem

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = YomuTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun YomuDropdownMenuContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun YomuPillFilter(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = MaterialTheme.shapes.large,
    )
}

enum class YomuIconButtonEmphasis {
    Primary,
    Quiet,
}

@Composable
fun YomuCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: YomuIconButtonEmphasis = YomuIconButtonEmphasis.Quiet,
    icon: @Composable () -> Unit,
) {
    when (emphasis) {
        YomuIconButtonEmphasis.Primary -> androidx.compose.material3.FilledIconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            icon()
        }

        YomuIconButtonEmphasis.Quiet -> androidx.compose.material3.FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            icon()
        }
    }
}

@Composable
fun YomuCircleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    emphasis: YomuIconButtonEmphasis = YomuIconButtonEmphasis.Quiet,
    tint: Color = when (emphasis) {
        YomuIconButtonEmphasis.Primary -> YomuTheme.colors.appBackground
        YomuIconButtonEmphasis.Quiet -> YomuTheme.colors.textPrimary
    },
) {
    when (emphasis) {
        YomuIconButtonEmphasis.Primary -> androidx.compose.material3.FilledIconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }

        YomuIconButtonEmphasis.Quiet -> androidx.compose.material3.FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

/** Material 3 drag handle compatibility wrapper for [YomuBottomSheet]s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuSheetDragHandle() {
    BottomSheetDefaults.DragHandle(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
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
    // Expanded tablets can keep reader navigation visible as a side sheet instead of a centered
    // dialog. Other sheets retain the centered dialog treatment.
    wideAsSideSheet: Boolean = false,
    // Optional lower bound for focused sheets such as in-book search.
    minHeight: Dp = 0.dp,
    // Override the standard 20–24 dp horizontal gutter for full-width list surfaces.
    horizontalContentPadding: Dp? = null,
    // Some controls contain their own visual hierarchy and do not need a fading scroll edge.
    showScrollEdgeShadow: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    if (!visible) return
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidth = with(density) { windowSize.width.toDp() }
    if (YomuWidthClass.fromWidth(windowWidth).isWide) {
        val maxHeight = with(density) { windowSize.height.toDp() - 48.dp }
        val scrollState = rememberScrollState()
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                    .padding(if (wideAsSideSheet) 0.dp else 24.dp),
                contentAlignment = if (wideAsSideSheet) Alignment.CenterEnd else Alignment.Center,
            ) {
                // The wide layout fills the Dialog window, so platform outside-click handling never
                // sees a click as being outside. Make the visible scrim dismiss explicitly instead.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(onDismiss) {
                            detectTapGestures(onTap = { onDismiss() })
                        },
                )
                Column(
                    modifier = Modifier
                        .then(
                            if (wideAsSideSheet) {
                                Modifier
                                    .fillMaxHeight()
                                    .widthIn(min = 320.dp, max = 440.dp)
                                    .padding(vertical = 16.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth(0.86f)
                                    .widthIn(max = 560.dp)
                                    .heightIn(
                                        min = minHeight.coerceAtMost(maxHeight),
                                        max = maxHeight,
                                    )
                            },
                        )
                        .shadow(18.dp, MaterialTheme.shapes.extraLarge)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.shapes.extraLarge,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.extraLarge,
                        )
                        // Keep taps on empty panel space from reaching the dismiss scrim behind it.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        }
                        .then(
                            if (scrollable) {
                                Modifier.verticalScroll(scrollState)
                            } else {
                                Modifier
                            },
                        )
                        .padding(
                            horizontal = horizontalContentPadding ?: 24.dp,
                            vertical = 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    content(onDismiss)
                }
            }
        }
        return
    }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { YomuSheetDragHandle() },
        // Draw the panel edge-to-edge under the (transparent) gesture nav bar instead of reserving it
        // as bottom inset — otherwise the panel stops above the bar, leaving a coloured band. The nav
        // clearance is moved onto the content below so text/controls still sit above the bar.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        // ModalBottomSheet hosts its content in its own Dialog window, which by default resizes
        // (adjustResize) when the IME opens. That shrinks the window below our 60%-of-screen max
        // height, so the sheet fills whatever's left near the top instead of staying put — reads as
        // the whole sheet jumping up. Pan mode keeps the window's size fixed and only shifts content
        // enough to keep the focused field visible, so a focused input scrolls into view without the
        // sheet itself resizing/relocating.
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrollable) {
                        Modifier
                            .heightIn(
                                min = minHeight.coerceAtMost(maxHeight),
                                max = maxHeight,
                            )
                            .then(
                                if (showScrollEdgeShadow) {
                                    // Fade content into the panel at whichever edge has more to scroll, so a
                                    // tall sheet doesn't hard-cut under the drag handle or at its bottom.
                                    Modifier.yomuScrollEdgeShadow(
                                        color = YomuTheme.colors.panel,
                                        top = scrollState.canScrollBackward,
                                        bottom = scrollState.canScrollForward,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .verticalScroll(scrollState)
                    } else {
                        Modifier.heightIn(min = minHeight.coerceAtMost(maxHeight))
                    },
                )
                .padding(horizontal = horizontalContentPadding ?: 20.dp)
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
