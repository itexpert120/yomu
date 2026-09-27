package com.itexpert120.yomu.core.designsystem

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

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
    // Optional lower bound for focused sheets such as in-book search.
    minHeight: Dp = 0.dp,
    // Centered tablet dialogs share Mihon's compact maximum.
    wideMaxWidth: Dp = 460.dp,
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
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
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
                        // Do not impose a percentage minimum before the cap: on a landscape tablet
                        // that would make widthIn(max=460.dp) ineffective.
                        .widthIn(min = 280.dp, max = wideMaxWidth)
                        .heightIn(
                            min = minHeight.coerceAtMost(maxHeight),
                            max = maxHeight,
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
        // The sheet's dialog window is edge-to-edge, so the keyboard arrives as IME insets rather than
        // a window resize. Resize mode guarantees those insets are dispatched (pan mode left the
        // sheet sitting under the keyboard); the content then pads itself above the IME, lifting
        // the whole sheet with its fields still visible.
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Outermost, so the height cap below applies to the space left above the keyboard.
                .imePadding()
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
