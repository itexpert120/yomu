package com.itexpert120.yomu.core.designsystem

import android.view.WindowManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Material 3 drag handle compatibility wrapper for [YomuBottomSheet]s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomuSheetDragHandle() {
    BottomSheetDefaults.DragHandle(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * The scroll state of the [YomuBottomSheet] around this content, while the sheet scrolls its
 * content itself; the sheet's [YomuSheetHeader] uses it to stay pinned at the sheet's top.
 */
internal val LocalYomuSheetScroll = staticCompositionLocalOf<ScrollState?> { null }

/**
 * Stack's modal sheet. On phones it runs the full width of the window, flush to its bottom edge
 * and drawn behind the gesture bar (its content is padded clear of it): sheet-coloured, top radius
 * 32, no drag handle (the [YomuSheetHeader] title row carries the close ×). It rises on the default
 * spatial spring while the screen behind dims to 80% (black at 20%, no blur). From medium widths it
 * is a side sheet at the end edge instead (≤[wideMaxWidth], leading corners 32).
 *
 * Every way out — the ×, back, a tap on the dim, a drag toward its edge (from the title row, or a
 * pull past the top of its content), the `dismiss` its content is handed — slides it off its edge
 * on the same spring before [onDismiss] runs. The system back gesture moves it with the finger
 * toward its edge, then completes or springs back ([YomuSheetMotion]). Turning [visible] off
 * removes it at once (its content may already be gone). Content taller than 60% of the window
 * scrolls ([scrollable]; pass false when the content has its own scroll container), under a
 * pinned header with Stack's scroll-edge rule. Previews draw it in place.
 */
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
    // Override the standard 24 dp horizontal gutter for full-width list surfaces.
    horizontalContentPadding: Dp? = null,
    // Whether the pinned header shows Stack's scroll-edge rule while content is scrolled under it.
    showScrollEdgeShadow: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    if (!visible) return
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val side = YomuWidthClass.fromWidth(with(density) { windowSize.width.toDp() }).isWide
    val motion = rememberYomuSheetMotion(onGone = onDismiss)
    val dismiss: () -> Unit = remember(motion) { { motion.dismiss() } }
    val scrollState = rememberScrollState()
    val gutter = horizontalContentPadding ?: YomuTheme.space.lg
    val sideGutter = horizontalContentPadding ?: SideSheetGutter
    SheetWindow(motion, dim = !side) {
        CompositionLocalProvider(
            LocalYomuSheetScroll provides scrollState.takeIf { scrollable },
            LocalYomuSheetRule provides showScrollEdgeShadow,
            LocalYomuSheetPresentation provides if (side) YomuSheetPresentation.Side else YomuSheetPresentation.Sheet,
        ) {
            if (side) {
                SideSheetPanel(motion, scrollable, scrollState, wideMaxWidth, sideGutter) { content(dismiss) }
            } else {
                BottomSheetPanel(motion, scrollable, scrollState, minHeight, gutter) { content(dismiss) }
            }
        }
    }
}

/** Whether a sheet's pinned header draws the scroll-edge rule. */
internal val LocalYomuSheetRule = staticCompositionLocalOf { true }

/**
 * The space a sheet keeps clear at its bottom (the gesture bar plus breathing room). A sheet that
 * scrolls its own content (`scrollable = false`) runs that content down to its very edge, so its
 * scroll container adds this as bottom content padding: rows scroll behind the gesture bar instead
 * of stopping at a hard edge above it.
 */
val LocalYomuSheetBottomInset = compositionLocalOf { 0.dp }

/**
 * The window a sheet lives in: a full-screen, edge-to-edge dialog window with no platform dim or
 * window animation of its own. The dim ([dim]: bottom sheets; a side sheet leaves the page
 * undimmed, as on Stack's tablet board) is drawn here, following the sheet
 * ([YomuSheetMotion.travel]); a tap outside the sheet dismisses it. Back (button or gesture) goes
 * to the sheet.
 */
@Composable
private fun SheetWindow(
    motion: YomuSheetMotion,
    dim: Boolean,
    panel: @Composable BoxScope.() -> Unit,
) {
    val frame: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { if (dim) drawRect(Color.Black.copy(alpha = YomuScrim.alpha * (1f - motion.travel).coerceIn(0f, 1f))) }
                    .pointerInput(motion) { detectTapGestures(onTap = { motion.dismiss() }) }
                    .semantics {
                        contentDescription = "Close sheet"
                        onClick {
                            motion.dismiss()
                            true
                        }
                    },
            )
            panel()
        }
    }
    if (LocalInspectionMode.current) {
        // Previews can't show a Dialog window: draw the dim and the sheet in place.
        frame()
        return
    }
    Dialog(
        onDismissRequest = { motion.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                // The sheet draws its own dim and moves itself; the window only hosts it.
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0f)
                setWindowAnimations(0)
                // The window is edge-to-edge, so the keyboard arrives as IME insets (resize mode makes
                // sure they are dispatched); the sheet pads itself above it.
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        YomuSheetBackHandler(motion)
        frame()
    }
}

/** The phone sheet: full width, flush to the bottom edge, rising from below. */
@Composable
private fun BoxScope.BottomSheetPanel(
    motion: YomuSheetMotion,
    scrollable: Boolean,
    scrollState: ScrollState,
    minHeight: Dp,
    gutter: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Tall content is capped at 60% of the window and scrolls inside the sheet.
    val maxHeight = windowHeight * 0.6f
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Never under the status bar or a cutout, even while the reader hides the bars.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
            .padding(top = SheetTopClearance)
            .onSizeChanged { motion.extentPx = it.height.toFloat() }
            .graphicsLayer { translationY = size.height * motion.travel }
            .clip(SheetShape)
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(motion.connection)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { motion.dragBy(it) },
                onDragStopped = { velocity -> motion.release(velocity) },
            )
            // Keep taps on empty sheet space from reaching the dim behind it.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            // Above the keyboard; the sheet's surface still runs down behind it.
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
            .then(
                if (scrollable) {
                    Modifier
                        .heightIn(min = minHeight.coerceAtMost(maxHeight), max = maxHeight)
                        .verticalScroll(scrollState)
                } else {
                    Modifier.heightIn(min = minHeight.coerceAtMost(maxHeight))
                },
            )
            .padding(horizontal = gutter)
            .padding(top = YomuTheme.space.xs)
            // Clear of the gesture bar (the IME padding above already covers it while typing). A sheet
            // with its own scroll container gets this as LocalYomuSheetBottomInset instead.
            .then(
                if (scrollable) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(bottom = YomuTheme.space.md)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        val inset = if (scrollable) 0.dp else navigationBarBottom() + YomuTheme.space.md
        CompositionLocalProvider(LocalYomuSheetBottomInset provides inset) { content() }
    }
}

@Composable
private fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** From medium widths: a full-height panel at the end edge, sliding in from it. */
@Composable
private fun BoxScope.SideSheetPanel(
    motion: YomuSheetMotion,
    scrollable: Boolean,
    scrollState: ScrollState,
    maxWidth: Dp,
    sideGutter: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Pure black: a hairline separates the panel from the dimmed page.
    val rule = YomuTheme.stack.rule
    // +1 when "toward the end edge" is +x.
    val towardEdge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .widthIn(max = maxWidth.coerceAtMost(SideSheetMaxWidth))
            .fillMaxWidth()
            .onSizeChanged { motion.extentPx = it.width.toFloat() }
            .graphicsLayer { translationX = size.width * motion.travel * towardEdge }
            // No dim behind a side sheet: a soft shadow and a hairline set it off the page.
            .dropShadow(SideSheetShape, SideSheetShadow)
            .clip(SideSheetShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, rule, SideSheetShape)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> motion.dragBy(delta * towardEdge) },
                onDragStopped = { velocity -> motion.release(velocity * towardEdge) },
            )
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            // Top and end insets stay outside; the bottom one goes inside the scroll (or to the content
            // as LocalYomuSheetBottomInset), so content scrolls down to the panel's edge, not to a cut.
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(start = sideGutter, end = sideGutter, top = SideSheetTop)
            .then(
                if (scrollable) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                        .padding(bottom = YomuTheme.space.lg)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        val inset = if (scrollable) 0.dp else systemBarBottom() + YomuTheme.space.lg
        CompositionLocalProvider(LocalYomuSheetBottomInset provides inset) { content() }
    }
}

@Composable
private fun systemBarBottom(): Dp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

/** How far below the status bar a sheet at its tallest still leaves the screen showing. */
private val SheetTopClearance = 24.dp

private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/** Stack's side sheet: at most 420 wide, rounded 32 on its leading edge only. */
private val SideSheetMaxWidth = 420.dp
private val SideSheetShape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
private val SideSheetGutter = 28.dp

// The board's 44dp from the window's top edge: the status bar's 24 plus this.
private val SideSheetTop = 20.dp
private val SideSheetShadow = Shadow(radius = 32.dp, spread = (-24).dp, offset = DpOffset((-16).dp, 0.dp), color = Color(0xFF16191C).copy(alpha = 0.25f))

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
