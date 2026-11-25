package com.itexpert120.yomu.core.designsystem

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Material 3 drag handle compatibility wrapper for [YomuBottomSheet]s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuSheetDragHandle() {
    BottomSheetDefaults.DragHandle(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Stack's modal sheet: sheet-coloured, top radius 32, no drag handle (the [YomuSheetHeader] title
 * row carries the close chevron); it rises on the sheet spring and the screen behind dims to 80%
 * (black at 20%, no blur). Dismiss by the chevron, back, or a drag down; programmatic dismissals
 * animate the slide-down before tearing it out of composition. From medium widths it is a side
 * sheet instead (see [YomuSideSheet]); confirmations stay centred as a [YomuDialog].
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
    // The side sheet's width from medium widths; Stack caps it at 420.
    wideMaxWidth: Dp = SideSheetMaxWidth,
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
        YomuSideSheet(
            onDismiss = onDismiss,
            scrollable = scrollable,
            maxWidth = wideMaxWidth,
            horizontalContentPadding = horizontalContentPadding,
            showScrollEdgeShadow = showScrollEdgeShadow,
            content = content,
        )
        return
    }
    // Open at the height the content needs rather than a half-expanded stop; tall content is then
    // capped to a reasonable height and scrolls internally instead of filling the whole screen.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
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
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = YomuScrim,
        dragHandle = null,
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
                .padding(horizontal = horizontalContentPadding ?: YomuTheme.space.lg)
                .padding(top = YomuTheme.space.xs)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = YomuTheme.space.lg),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
        ) {
            content(::animatedDismiss)
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * [YomuBottomSheet] from medium widths: a full-height sheet-coloured panel at the end edge, at most
 * 420dp wide, rounded 32 on its leading edge, sliding in on the default spatial spring over the
 * 80% dim. Dismiss by back (the panel follows the predictive swipe), the close ×, a tap on the dim,
 * or a drag toward the edge; every dismissal plays the slide out before the window goes.
 */
@Composable
private fun YomuSideSheet(
    onDismiss: () -> Unit,
    scrollable: Boolean,
    maxWidth: Dp,
    horizontalContentPadding: Dp?,
    showScrollEdgeShadow: Boolean,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val scrollState = rememberScrollState()
    // A preview has no frames to run the slide in, so it starts at rest.
    val inspection = LocalInspectionMode.current
    val panelState = remember { MutableTransitionState(inspection).apply { targetState = true } }
    val dismissPanel: () -> Unit = { panelState.targetState = false }
    if (panelState.isIdle && !panelState.currentState && !panelState.targetState) {
        LaunchedEffect(Unit) { onDismiss() }
    }
    // Pure black: a hairline separates the panel from the dimmed page.
    val oled = YomuTheme.stack.desk == Color.Black
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // +1 when "toward the end edge" is +x.
    val towardEdge = if (rtl) -1f else 1f
    val animate = yomuAnimationsEnabled()
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val backPreview = rememberYomuBackPreviewState()
    val panel: @Composable () -> Unit = {
        val scope = rememberCoroutineScope()
        // How far the panel has been dragged toward its edge, in px (never past its resting place).
        val drag = remember { Animatable(0f) }
        val flingVelocity = with(LocalDensity.current) { SideSheetFlingVelocity.toPx() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            // The panel fills the Dialog window, so platform outside-click handling never sees a
            // click as being outside. Make the visible dim dismiss explicitly instead.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { dismissPanel() })
                    },
            )
            AnimatedVisibility(
                visibleState = panelState,
                enter = if (animate) slideInHorizontally(slideSpec) { (it * towardEdge).toInt() } else EnterTransition.None,
                exit = if (animate) slideOutHorizontally(slideSpec) { (it * towardEdge).toInt() } else ExitTransition.None,
            ) {
                var panelWidth by remember { mutableIntStateOf(0) }
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = maxWidth.coerceAtMost(SideSheetMaxWidth))
                        .fillMaxWidth()
                        .onSizeChanged { panelWidth = it.width }
                        .offset { IntOffset((drag.value * towardEdge).roundToInt(), 0) }
                        .yomuBackPreview(backPreview, origin = TransformOrigin(if (rtl) 0f else 1f, 0.5f))
                        .draggable(
                            state = rememberDraggableState { delta ->
                                scope.launch { drag.snapTo((drag.value + delta * towardEdge).coerceAtLeast(0f)) }
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity ->
                                if (drag.value > panelWidth * SideSheetDismissFraction || velocity * towardEdge > flingVelocity) {
                                    dismissPanel()
                                } else {
                                    drag.animateTo(0f, settleSpec)
                                }
                            },
                        )
                        // Keep taps on empty panel space from reaching the dismiss dim behind it.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        },
                    shape = SideSheetShape,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = if (oled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.systemBars
                                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                                    .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                            )
                            .then(
                                if (scrollable) {
                                    Modifier
                                        .then(
                                            if (showScrollEdgeShadow) {
                                                Modifier.yomuScrollEdgeShadow(
                                                    top = scrollState.canScrollBackward,
                                                    bottom = scrollState.canScrollForward,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .verticalScroll(scrollState)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(
                                start = horizontalContentPadding ?: YomuTheme.space.lg,
                                end = horizontalContentPadding ?: YomuTheme.space.lg,
                                top = YomuTheme.space.sm,
                                bottom = YomuTheme.space.lg,
                            ),
                        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
                    ) {
                        CompositionLocalProvider(LocalYomuSheetPresentation provides YomuSheetPresentation.Side) {
                            content(dismissPanel)
                        }
                    }
                }
            }
        }
    }
    if (LocalInspectionMode.current) {
        // Previews can't show a Dialog window: draw the dim and the panel in place.
        Box(Modifier.fillMaxSize().background(YomuScrim)) { panel() }
        return
    }
    Dialog(
        onDismissRequest = dismissPanel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        YomuDialogWindowDim()
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        YomuBackPreviewHandler(backPreview, onBack = dismissPanel)
        panel()
    }
}

/** Stack's side sheet: at most 420 wide, rounded 32 on its leading edge only. */
private val SideSheetMaxWidth = 420.dp
private val SideSheetShape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)

/** A drag past this share of the panel's width, or a fling toward the edge, dismisses it. */
private const val SideSheetDismissFraction = 0.3f
private val SideSheetFlingVelocity = 800.dp

/** Stack's one softening: behind a sheet or dialog the screen dims to 80% (black at 20%). */
val YomuScrim: Color = Color.Black.copy(alpha = 0.2f)

/** Sets the enclosing dialog window's dim to Stack's 80% (call inside a `Dialog`'s content). */
@Composable
fun YomuDialogWindowDim() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(YomuScrim.alpha)
    }
}
