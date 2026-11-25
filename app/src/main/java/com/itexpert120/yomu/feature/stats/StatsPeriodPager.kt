package com.itexpert120.yomu.feature.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import kotlinx.coroutines.launch

/**
 * Which week or month the dashboard shows: a pager over [pages] (oldest first), opening on the
 * current one. Swiping the figure and chart or the arrows beside them moves through past periods.
 */
@Stable
internal class StatsPeriodPager(
    val period: StatsPeriod,
    private val trend: () -> StatsTrend,
    val pagerState: PagerState,
) {
    val pages: List<StatsPage> get() = trend().pages(period)

    /** The page that is (mostly) on screen; follows a swipe past its halfway point. */
    val current: StatsPage? get() = pages.getOrNull(pagerState.currentPage)
}

@Composable
internal fun rememberStatsPeriodPager(trend: StatsTrend, period: StatsPeriod): StatsPeriodPager {
    val latest = rememberUpdatedState(trend)
    val count = trend.pages(period).size
    // A new pager per period: switching Week / Month opens on the current one.
    val pagerState = key(period) {
        rememberPagerState(initialPage = (count - 1).coerceAtLeast(0)) { latest.value.pages(period).size }
    }
    // When a new week or month begins while the latest is shown, follow it there.
    var knownCount by remember(pagerState) { mutableIntStateOf(count) }
    LaunchedEffect(pagerState, count) {
        val wasLatest = pagerState.currentPage >= knownCount - 1
        if (count > knownCount && wasLatest) pagerState.scrollToPage(count - 1)
        knownCount = count
    }
    return remember(period, pagerState) { StatsPeriodPager(period, { latest.value }, pagerState) }
}

/**
 * The period's title with arrows to the previous and next period, then the one figure and its
 * chart. Title, figure and chart page together with the finger; the arrows stay put. Switching
 * Week / Month cross-fades. [gutter] is the screen's side margin: pages span the full width and
 * slide out past it, so a page is never cut off at the margin.
 */
@Composable
internal fun PeriodOverview(
    pager: StatsPeriodPager,
    wide: Boolean,
    modifier: Modifier = Modifier,
    gutter: Dp = 0.dp,
) {
    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = pager,
        contentKey = { it.period },
        transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
        label = "statsPeriod",
        modifier = modifier,
    ) { shown ->
        Box(Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = shown.pagerState,
                contentPadding = PaddingValues(horizontal = gutter),
                // The neighbouring page starts just past the screen edge, so nothing peeks in.
                pageSpacing = gutter,
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                val page = shown.pages.getOrNull(index)
                if (page != null) PeriodPage(page = page, period = shown.period, wide = wide)
            }
            PeriodArrows(
                pager = shown,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = (gutter - 12.dp).coerceAtLeast(0.dp)),
            )
        }
    }
}

private val PeriodTitleHeight = 48.dp

@Composable
private fun PeriodPage(page: StatsPage, period: StatsPeriod, wide: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.height(PeriodTitleHeight), contentAlignment = Alignment.CenterStart) {
            Text(
                text = page.title,
                style = StackType.Label,
                color = YomuTheme.stack.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Room for the arrows, which sit over the end of this line.
                modifier = Modifier
                    .padding(end = 104.dp)
                    .semantics { heading() },
            )
        }
        HeroFigure(summary = page.summary, wide = wide, modifier = Modifier.padding(top = if (wide) 4.dp else 0.dp))
        TrendChart(
            bars = page.bars,
            heading = page.chartHeading,
            dense = period == StatsPeriod.Month,
            wide = wide,
            modifier = Modifier.padding(top = if (wide) 28.dp else 20.dp),
        )
    }
}

/** Back to the previous period, on to the next; each fades out at its end of the range. */
@Composable
private fun PeriodArrows(pager: StatsPeriodPager, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = pager.pagerState
    val canGoBack by remember(state) { derivedStateOf { state.currentPage > 0 } }
    val canGoOn by remember(state) { derivedStateOf { state.currentPage < state.pageCount - 1 } }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val animate = yomuAnimationsEnabled()
    val noun = if (pager.period == StatsPeriod.Week) "week" else "month"
    fun go(page: Int) {
        scope.launch {
            if (animate) state.animateScrollToPage(page, animationSpec = spec) else state.scrollToPage(page)
        }
    }
    Row(modifier.height(PeriodTitleHeight), verticalAlignment = Alignment.CenterVertically) {
        PeriodArrow(
            enabled = canGoBack,
            onClick = { go(state.currentPage - 1) },
            description = "Previous $noun",
            next = false,
        )
        PeriodArrow(
            enabled = canGoOn,
            onClick = { go(state.currentPage + 1) },
            description = "Next $noun",
            next = true,
        )
    }
}

@Composable
private fun PeriodArrow(enabled: Boolean, onClick: () -> Unit, description: String, next: Boolean) {
    val stack = YomuTheme.stack
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
        Icon(
            imageVector = if (next) StackIcons.KeyboardArrowRight else StackIcons.KeyboardArrowLeft,
            contentDescription = description,
            tint = if (enabled) stack.ink else stack.faint,
            modifier = Modifier.size(24.dp),
        )
    }
}
