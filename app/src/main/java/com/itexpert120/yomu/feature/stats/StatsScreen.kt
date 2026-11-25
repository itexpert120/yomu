@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.app.navigation.LocalYomuNavigationBarShown
import com.itexpert120.yomu.core.designsystem.StackCompactHeader
import com.itexpert120.yomu.core.designsystem.StackCompactHeaderHeight
import com.itexpert120.yomu.core.designsystem.StackHeaderFadeDistance
import com.itexpert120.yomu.core.designsystem.StackTitleRow
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackPinnedEdgeFade
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeRule
import com.itexpert120.yomu.core.model.BookReadingTime
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import java.time.LocalDate
import java.time.ZoneId

/**
 * [onOpenLibrary] is the "no reading yet" state's way out; the state shows no button without it.
 */
@Composable
fun StatsRoute(onBack: (() -> Unit)?, onOpenLibrary: (() -> Unit)? = null) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.Week) }
    StatsScreen(
        state = state,
        period = period,
        onPeriodChange = { period = it },
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenLibrary = onOpenLibrary,
    )
}

private enum class StatsPhase { Loading, Error, Empty, Content }

private val StatsUiState.phase: StatsPhase
    get() = when {
        isLoading -> StatsPhase.Loading
        error != null -> StatsPhase.Error
        !stats.hasActivity && bookTimes.isEmpty() && history.isEmpty() -> StatsPhase.Empty
        else -> StatsPhase.Content
    }

/**
 * Stack's statistics: the title, the one figure for the chosen period and its chart lie on the
 * desk; every other figure, time by book and recent history are on a white sheet. On a phone the
 * sheet scrolls up over the desk and a compact line of the figure pins at the top; from expanded
 * width the dashboard stays on the desk and the books and history become a sheet on the right.
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit = {},
    onOpenLibrary: (() -> Unit)? = null,
) {
    YomuAppSurface {
        val widthClass = YomuWidthClass.fromWidth(maxWidth)
        // Stats is information-dense, so it stays single-pane until expanded width.
        val wide = widthClass.isExpanded
        val gutter = if (widthClass == YomuWidthClass.Compact) YomuTheme.space.lg else YomuTheme.space.xl
        val viewport = maxHeight
        val fade = fadeTransition()
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fade },
            label = "statsPhase",
        ) { phase ->
            when (phase) {
                StatsPhase.Loading -> if (wide) {
                    WideSkeleton(period, onPeriodChange, onBack)
                } else {
                    PhoneSkeleton(period, onPeriodChange, onBack, gutter)
                }

                StatsPhase.Error -> StatsStatePane(onBack, gutter) {
                    YomuEmptyState(
                        icon = StackIcons.CloudOff,
                        title = "Couldn’t load your reading history",
                        body = "Your books and progress are safe. Try again in a moment.",
                        actions = { YomuButton(text = "Try again", onClick = onRetry) },
                    )
                }

                StatsPhase.Empty -> StatsStatePane(onBack, gutter) {
                    YomuEmptyState(
                        icon = StackIcons.HourglassEmpty,
                        title = "No reading time yet",
                        body = "Minutes, streaks and how long each book takes will show up here once you start reading.",
                        actions = onOpenLibrary?.let { open -> { YomuButton(text = "Open your library", onClick = open) } },
                    )
                }

                StatsPhase.Content -> if (wide) {
                    WideStats(state, period, onPeriodChange, onBack)
                } else {
                    PhoneStats(state, period, onPeriodChange, onBack, gutter, viewport)
                }
            }
        }
    }
}

@Composable
private fun fadeTransition(): ContentTransform {
    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    return remember(enter, exit) { fadeIn(enter) togetherWith fadeOut(exit) }
}

private val PinnedRowHeight = 48.dp

/**
 * The statistics title: the shared top-level title row with the period choice as its action, so
 * "Statistics" sits exactly where "Library" and "Settings" do. With [onBack] (a pushed page) it
 * keeps the dashboard's own row with a back button.
 */
@Composable
private fun StatsTitle(
    onBack: (() -> Unit)?,
    period: StatsPeriod?,
    onPeriodChange: (StatsPeriod) -> Unit,
    gutter: Dp,
    // The board ends the period choice 16dp in on a phone.
    endPadding: Dp = if (gutter > 0.dp) 16.dp else 0.dp,
    collapse: (() -> Float)? = null,
) {
    if (onBack != null) {
        StatsTitleRow(onBack = onBack, period = period, onPeriodChange = onPeriodChange, startPadding = gutter)
    } else {
        StackTitleRow(
            title = "Statistics",
            gutter = gutter,
            endPadding = endPadding,
            collapse = collapse,
            actions = period?.let { { PeriodPicker(selected = it, onSelect = onPeriodChange) } },
        )
    }
}

@Composable
private fun PhoneStats(
    state: StatsUiState,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    onBack: (() -> Unit)?,
    gutter: Dp,
    viewport: Dp,
    scroll: ScrollState = rememberScrollState(),
) {
    val stack = YomuTheme.stack
    val density = LocalDensity.current
    var deskHeight by remember { mutableIntStateOf(0) }
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navBottom = if (LocalYomuNavigationBarShown.current) {
        0.dp
    } else {
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    }
    val sheetRadius = with(density) { 32.dp.toPx() }
    val headerBottom = with(density) { (statusTop + StackCompactHeaderHeight).toPx() }
    val pinnedBottom = with(density) { (statusTop + StackCompactHeaderHeight + PinnedRowHeight).toPx() }
    val headerFade = with(density) { StackHeaderFadeDistance.toPx() }
    val edgeRamp = with(density) { 28.dp.toPx() }
    // Where the sheet's top edge is on screen; read only in layer and draw blocks, so scrolling
    // never recomposes the dashboard.
    val sheetTop = { (deskHeight - scroll.value).toFloat() }
    // The corners square off over the last stretch before the sheet reaches the compact header.
    val cornerRadius = { sheetRadius * ((sheetTop() - headerBottom) / sheetRadius).coerceIn(0f, 1f) }
    // The compact header comes in as the large title scrolls up under it.
    val headerProgress = { (scroll.value / headerFade).coerceIn(0f, 1f) }

    val pager = rememberStatsPeriodPager(state.trend, period)
    // How far the sheet has arrived under the pinned period line (0 -> 1 over its first 28dp).
    val sheetArrived = { ((pinnedBottom - sheetTop()) / edgeRamp).coerceIn(0f, 1f) }
    Box(
        Modifier
            .fillMaxSize()
            // The sheet's colour behind the scrolling column, so the edge fade below dissolves the
            // sheet's content into the sheet (not into the desk).
            .drawBehind { drawRect(stack.sheet, topLeft = Offset(0f, (sheetTop() + cornerRadius()).coerceAtLeast(0f))) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // Content dissolves under the compact header, then under the period line once the
                // sheet has come up to it.
                .stackPinnedEdgeFade(
                    edge = { headerBottom + (pinnedBottom - headerBottom) * sheetArrived() },
                    strength = headerProgress,
                )
                .verticalScroll(scroll),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { deskHeight = it.height }
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
            ) {
                StatsTitle(onBack = onBack, period = period, onPeriodChange = onPeriodChange, gutter = gutter, collapse = headerProgress)
                PeriodOverview(pager, wide = false, gutter = gutter)
                Spacer(Modifier.height(YomuTheme.space.lg))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    // Tall enough to reach the pinned line, so the sheet can always scroll up to it.
                    .heightIn(min = viewport - statusTop - StackCompactHeaderHeight - PinnedRowHeight)
                    .graphicsLayer {
                        val radius = cornerRadius()
                        shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                        clip = true
                    }
                    .background(stack.sheet)
                    .padding(horizontal = gutter)
                    .padding(top = 22.dp, bottom = navBottom + YomuTheme.space.lg),
            ) {
                FactsRow(state.stats)
                SectionHeading("Reading details", Modifier.padding(top = 26.dp))
                ReadingDetailsGrid(state.stats, columns = 2)
                BookTimeSection(state.bookTimes, Modifier.padding(top = 26.dp))
                HistorySection(
                    history = state.history,
                    historicalDetailIncomplete = state.stats.historicalSessionDetailIncomplete,
                    modifier = Modifier.padding(top = 26.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth()) {
            StackCompactHeader(
                title = "Statistics",
                progress = headerProgress,
                gutter = gutter,
                endPadding = 16.dp,
                actions = if (onBack == null) {
                    { PeriodPicker(selected = period, onSelect = onPeriodChange, compact = true) }
                } else {
                    null
                },
            )
            PinnedSummary(
                text = pager.current?.summary?.compact.orEmpty(),
                gutter = gutter,
                sheetTop = { sheetTop() - headerBottom },
                cornerRadius = cornerRadius,
            )
        }
    }
}

/**
 * The period's compact figure, pinned under the compact header once the sheet has covered the
 * desk. It paints the sheet only where the sheet already is (same corners), so the sheet's content
 * slides under it without a band appearing over the desk; its text fades in as the sheet arrives.
 * [sheetTop] is in this row's own coordinates.
 */
@Composable
private fun PinnedSummary(
    text: String,
    gutter: Dp,
    sheetTop: () -> Float,
    cornerRadius: () -> Float,
) {
    val stack = YomuTheme.stack
    Box(
        Modifier
            .fillMaxWidth()
            .height(PinnedRowHeight)
            .clipToBounds()
            .drawBehind {
                val top = sheetTop()
                if (top < size.height) {
                    val radius = cornerRadius()
                    drawRoundRect(
                        color = stack.sheet,
                        topLeft = Offset(0f, top.coerceAtLeast(0f)),
                        size = Size(size.width, size.height + radius * 2),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
            .clearAndSetSemantics { },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = gutter)
                .graphicsLayer {
                    // Clear until the sheet covers half the line, fully there once it reaches its top.
                    val half = size.height / 2f
                    alpha = ((half - sheetTop()) / half.coerceAtLeast(1f)).coerceIn(0f, 1f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = StackType.Heading,
                color = stack.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Expanded and wider: the dashboard on the desk, books and history as a sheet on the right. */
@Composable
private fun WideStats(
    state: StatsUiState,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    onBack: (() -> Unit)?,
) {
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val pager = rememberStatsPeriodPager(state.trend, period)
    val scroll = rememberScrollState()
    val headerFade = with(LocalDensity.current) { StackHeaderFadeDistance.toPx() }
    val headerProgress = { (scroll.value / headerFade).coerceIn(0f, 1f) }
    WidePanes(
        dashboardScroll = scroll,
        dashboardHeader = {
            StackCompactHeader(
                title = "Statistics",
                progress = headerProgress,
                gutter = YomuTheme.space.xl,
                endPadding = YomuTheme.space.xl,
                actions = if (onBack == null) {
                    { PeriodPicker(selected = period, onSelect = onPeriodChange, compact = true) }
                } else {
                    null
                },
            )
        },
        dashboard = {
            StatsTitle(onBack = onBack, period = period, onPeriodChange = onPeriodChange, gutter = 0.dp, collapse = headerProgress)
            PeriodOverview(pager, wide = true, modifier = Modifier.padding(top = 12.dp))
            FactCards(state.stats, Modifier.padding(top = 28.dp))
            SectionHeading("Reading details", Modifier.padding(top = 32.dp, bottom = YomuTheme.space.sm))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(YomuTheme.stack.sheet)
                    .padding(horizontal = 20.dp, vertical = YomuTheme.space.xxs),
            ) {
                ReadingDetailsGrid(state.stats, columns = 4)
            }
            Spacer(Modifier.height(navBottom + YomuTheme.space.xl))
        },
        side = {
            BookTimeSection(state.bookTimes)
            HistorySection(
                history = state.history,
                historicalDetailIncomplete = state.stats.historicalSessionDetailIncomplete,
                modifier = Modifier.padding(top = 28.dp),
                thumbWidth = 28.dp,
            )
            Spacer(Modifier.height(navBottom + YomuTheme.space.lg))
        },
    )
}

private val SideSheetShape: Shape = RoundedCornerShape(topStart = 32.dp)

@Composable
private fun WidePanes(
    dashboard: @Composable () -> Unit,
    side: @Composable () -> Unit,
    // Null keeps the dashboard still (the loading skeleton).
    dashboardScroll: ScrollState? = null,
    // Pinned over the dashboard pane (the compact header), drawn above its scrolling content.
    dashboardHeader: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideWidth = (maxWidth * 0.36f).coerceIn(360.dp, 440.dp)
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .then(if (dashboardScroll != null) Modifier.verticalScroll(dashboardScroll) else Modifier)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                        .padding(horizontal = YomuTheme.space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) { dashboard() }
                }
                dashboardHeader?.invoke()
            }
            val sideScroll = rememberScrollState()
            Column(
                Modifier
                    .width(sideWidth)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .clip(SideSheetShape)
                    .background(YomuTheme.stack.sheet)
                    // Rows dissolve at the sheet's top edge once they scroll under it.
                    .yomuScrollEdgeRule(top = sideScroll.canScrollBackward)
                    .verticalScroll(sideScroll)
                    .padding(horizontal = 28.dp)
                    .padding(top = YomuTheme.space.lg),
            ) { side() }
        }
    }
}

/** Error and "no reading yet": the title on the desk, then the one empty state. */
@Composable
private fun StatsStatePane(onBack: (() -> Unit)?, gutter: Dp, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        StatsTitle(onBack = onBack, period = null, onPeriodChange = {}, gutter = gutter)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 120.dp, start = gutter - YomuTheme.space.lg, end = gutter - YomuTheme.space.lg),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = 480.dp)) { content() }
        }
    }
}

// --- Loading: skeleton blocks shaped like what's coming (track on the desk, raised on the sheet) ---

@Composable
private fun Modifier.skeleton(onSheet: Boolean, shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val stack = YomuTheme.stack
    return clip(shape).background(if (onSheet) stack.raised else stack.track.copy(alpha = 0.6f))
}

@Composable
private fun DeskSkeleton(wide: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .padding(top = if (wide) 30.dp else 26.dp)
                .size(width = if (wide) 300.dp else 230.dp, height = if (wide) 60.dp else 44.dp)
                .skeleton(onSheet = false),
        )
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(width = 170.dp, height = 14.dp)
                .skeleton(onSheet = false),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
                .height(if (wide) 156.dp else 94.dp),
            horizontalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(0.6f, 0.4f, 0.8f, 0.5f, 0.3f, 0.9f, 0.7f).forEach { fraction ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .skeleton(onSheet = false),
                )
            }
        }
    }
}

@Composable
private fun SheetRowsSkeleton(rows: Int, thumbWidth: Dp) {
    Box(
        Modifier
            .size(width = 120.dp, height = 16.dp)
            .skeleton(onSheet = true),
    )
    repeat(rows) {
        Row(
            Modifier.padding(top = YomuTheme.space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(width = thumbWidth, height = thumbWidth * 1.5f)
                    .skeleton(onSheet = true, shape = ThumbShape),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(14.dp)
                    .skeleton(onSheet = true),
            )
        }
    }
}

@Composable
private fun PhoneSkeleton(
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    onBack: (() -> Unit)?,
    gutter: Dp,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .clearAndSetSemantics { },
    ) {
        StatsTitle(onBack = onBack, period = period, onPeriodChange = onPeriodChange, gutter = gutter)
        DeskSkeleton(wide = false, Modifier.padding(horizontal = gutter))
        Column(
            Modifier
                .padding(top = YomuTheme.space.lg)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(YomuTheme.stack.sheet)
                .padding(horizontal = gutter, vertical = 26.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
                listOf(0.7f, 0.4f, 0.5f).forEach { fraction ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(26.dp)
                                .skeleton(onSheet = true),
                        )
                        Box(
                            Modifier
                                .padding(top = YomuTheme.space.xs)
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .skeleton(onSheet = true),
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            SheetRowsSkeleton(rows = 2, thumbWidth = 44.dp)
        }
    }
}

@Composable
private fun WideSkeleton(
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    onBack: (() -> Unit)?,
) {
    Box(Modifier.clearAndSetSemantics { }) {
        WidePanes(
            dashboard = {
                StatsTitle(onBack = onBack, period = period, onPeriodChange = onPeriodChange, gutter = 0.dp)
                DeskSkeleton(wide = true)
                Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.md)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(88.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(YomuTheme.stack.sheet),
                        )
                    }
                }
            },
            side = { SheetRowsSkeleton(rows = 4, thumbWidth = 44.dp) },
        )
    }
}

// --- Previews ---

private fun previewState(): StatsUiState {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val minutes = listOf(42, 25, 58, 30, 0, 64, 52)
    val days = (0 until 340).map { back ->
        val date = today.minusDays(back.toLong())
        val seconds = if (back < 7) minutes[6 - back] * 60L else ((back * 37) % 70).let { if (it < 12) 0L else it * 60L }
        DailyReading(date.toString(), seconds)
    }.reversed()
    fun at(daysBack: Long, hour: Int, minute: Int) = today.minusDays(daysBack).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    return StatsUiState(
        isLoading = false,
        stats = ReadingStats(
            totalReadingSeconds = 38 * 3600L + 12 * 60,
            currentStreakDays = 2,
            longestStreakDays = 14,
            booksInLibrary = 24,
            booksStarted = 9,
            booksFinished = 2,
            chaptersRead = 63,
            estimatedWordsRead = 412_000,
            sessionCount = 104,
            averageSessionSeconds = 22 * 60L,
            longestSessionSeconds = 112 * 60L,
            daysRead = 61,
            averageSecondsPerActiveDay = 37 * 60L,
            secondsLast7Days = 4 * 3600L + 21 * 60,
            secondsLast30Days = 16 * 3600L + 5 * 60,
        ),
        bookTimes = listOf(
            BookReadingTime("1", "The Wind in the Willows", "Kenneth Grahame", null, 11 * 3600L + 5 * 60, 31),
            BookReadingTime("2", "Mrs. Dalloway", "Virginia Woolf", null, 6 * 3600L + 52 * 60, 18),
            BookReadingTime("3", "Frankenstein", "Mary Shelley", null, 6 * 3600L + 6 * 60, 17),
            BookReadingTime("4", "The Hound of the Baskervilles", "Arthur Conan Doyle", null, 5 * 3600L + 14 * 60, 12),
            BookReadingTime("5", "Pride and Prejudice", "Jane Austen", null, 3 * 3600L + 8 * 60, 9),
            BookReadingTime("6", "Anna Karenina", "Leo Tolstoy", null, 2 * 3600L + 20 * 60, 6),
            BookReadingTime(null, "Removed book", null, null, 40 * 60L, 3),
            BookReadingTime(null, "Removed book", null, null, 12 * 60L, 1),
        ),
        history = listOf(
            ReadingSessionItem("1", "The Wind in the Willows", null, at(0, 20, 12), 32 * 60L),
            ReadingSessionItem("3", "Frankenstein", null, at(0, 7, 30), 20 * 60L),
            ReadingSessionItem("1", "The Wind in the Willows", null, at(1, 21, 40), 44 * 60L),
            ReadingSessionItem("5", "Pride and Prejudice", null, at(3, 15, 5), 21 * 60L, sessionCount = 3),
            ReadingSessionItem("1", "The Wind in the Willows", null, at(3, 10, 2), 43 * 60L),
        ),
        trend = buildStatsTrend(days),
    )
}

@Composable
private fun StatsPreview(state: StatsUiState, mode: YomuThemeMode = YomuThemeMode.Light, period: StatsPeriod = StatsPeriod.Week) {
    YomuDesignTheme(themeMode = mode) {
        StatsScreen(state = state, period = period, onPeriodChange = {}, onBack = null, onOpenLibrary = {})
    }
}

@Preview(name = "Phone", widthDp = 390, heightDp = 1500)
@Composable
private fun StatsPhonePreview() = StatsPreview(previewState())

@Preview(name = "Phone dark", widthDp = 390, heightDp = 1500)
@Composable
private fun StatsPhoneDarkPreview() = StatsPreview(previewState(), YomuThemeMode.Dark)

@Preview(name = "Phone month", widthDp = 390, heightDp = 844)
@Composable
private fun StatsPhoneMonthPreview() = StatsPreview(previewState(), period = StatsPeriod.Month)

@Preview(name = "Phone month, black", widthDp = 390, heightDp = 844)
@Composable
private fun StatsPhoneMonthBlackPreview() = StatsPreview(previewState(), YomuThemeMode.Oled, StatsPeriod.Month)

@Composable
private fun StatsScrolledPreview(mode: YomuThemeMode) {
    YomuDesignTheme(themeMode = mode) {
        YomuAppSurface {
            PhoneStats(
                state = previewState(),
                period = StatsPeriod.Week,
                onPeriodChange = {},
                onBack = null,
                gutter = YomuTheme.space.lg,
                viewport = maxHeight,
                scroll = rememberScrollState(620),
            )
        }
    }
}

@Preview(name = "Phone scrolled", widthDp = 390, heightDp = 844)
@Composable
private fun StatsPhoneScrolledPreview() = StatsScrolledPreview(YomuThemeMode.Light)

@Preview(name = "Phone scrolled dark", widthDp = 390, heightDp = 844)
@Composable
private fun StatsPhoneScrolledDarkPreview() = StatsScrolledPreview(YomuThemeMode.Dark)

@Preview(name = "Loading", widthDp = 390, heightDp = 844)
@Composable
private fun StatsLoadingPreview() = StatsPreview(StatsUiState())

@Preview(name = "Empty", widthDp = 390, heightDp = 844)
@Composable
private fun StatsEmptyPreview() = StatsPreview(StatsUiState(isLoading = false))

@Preview(name = "Error dark", widthDp = 390, heightDp = 844)
@Composable
private fun StatsErrorPreview() = StatsPreview(StatsUiState(isLoading = false, error = "x"), YomuThemeMode.Dark)

@Preview(name = "Sections empty", widthDp = 390, heightDp = 1100)
@Composable
private fun StatsSectionsEmptyPreview() = StatsPreview(
    previewState().copy(history = emptyList(), bookTimes = emptyList(), trend = buildStatsTrend(listOf(DailyReading(LocalDate.now().toString(), 0L)))),
)

@Preview(name = "Tablet", widthDp = 1200, heightDp = 800)
@Composable
private fun StatsTabletPreview() = StatsPreview(previewState())

@Preview(name = "Tablet dark", widthDp = 1200, heightDp = 800)
@Composable
private fun StatsTabletDarkPreview() = StatsPreview(previewState(), YomuThemeMode.Dark)

@Preview(name = "Tablet loading", widthDp = 1200, heightDp = 800)
@Composable
private fun StatsTabletLoadingPreview() = StatsPreview(StatsUiState())
