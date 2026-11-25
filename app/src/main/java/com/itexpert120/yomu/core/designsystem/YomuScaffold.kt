@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/** Keep app chrome stationary while the outgoing reader still hides the system bars. */
@Composable
internal fun yomuStableSystemBarInsets(): WindowInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

/**
 * Stack's screen title bar, on the desk. A top-level screen ([onBack] null) sets its title in
 * Display 32/38 at the 24dp gutter with its actions to the right; a child screen gets a 48dp back
 * button followed by the title (17/600). [subtitle] adds a muted line. The bar never collapses: it
 * stays put and shows Stack's scroll-edge rule under itself while content is scrolled beneath it
 * ([scrolledUnder], or a [scrollBehavior]'s content offset).
 */
@Composable
fun YomuScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    // Extra inset on each side so the title lines up with width-capped, centred content below.
    contentGutter: Dp = 0.dp,
    scrolledUnder: (() -> Boolean)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val stack = YomuTheme.stack
    if (scrollBehavior != null) {
        // A Stack bar doesn't collapse; a zero limit keeps the behaviour's connection from
        // consuming scroll while it still tracks how far content has moved under the bar.
        SideEffect { scrollBehavior.state.heightOffsetLimit = 0f }
    }
    Column(
        modifier = modifier
            .zIndex(1f)
            .fillMaxWidth()
            .background(stack.desk)
            .then(
                when {
                    scrolledUnder != null -> Modifier.yomuPinnedHeaderRule(scrolledUnder)
                    scrollBehavior != null -> Modifier.yomuPinnedHeaderRule { scrollBehavior.state.contentOffset < -1f }
                    else -> Modifier
                },
            )
            .windowInsetsPadding(yomuStableSystemBarInsets().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(horizontal = contentGutter),
    ) {
        if (onBack == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = StackType.Display,
                    color = stack.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                CompositionLocalProvider(LocalContentColor provides stack.ink) { trailing() }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = StackType.Value,
                    color = stack.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides stack.ink) {
                    YomuBackButton(onClick = onBack)
                    Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                        Text(
                            text = title,
                            style = StackType.SheetTitle,
                            color = stack.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(text = subtitle, style = StackType.Value, color = stack.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    trailing()
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * The screen title text on its own (for custom bars): 600 weight with Stack's tightened tracking, at
 * whatever size the surrounding style sets.
 */
@Composable
fun YomuScreenTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text = text,
        modifier = modifier,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.019).em,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The 48dp back button (arrow, ink). */
@Composable
fun YomuBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    YomuIconButton(icon = StackIcons.ArrowBack, contentDescription = "Back", onClick = onClick, modifier = modifier)
}

/** Standard detail-screen shell with a Material 3 top app bar and capped tablet content. */
@Composable
fun YomuScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    // Stack's scroll-edge rule under the header while content is scrolled beneath it.
    showScrollEdgeShadow: Boolean = true,
    // Reading-width by default so forms and prose keep a comfortable line length on tablets;
    // visual dashboards can pass YomuContentMaxWidth.
    maxContentWidth: Dp = YomuReadableMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    YomuAppSurface(modifier = modifier) {
        // Measured (not window) width, so a navigation rail beside this screen is accounted for.
        val windowMargin = YomuWidthClass.fromWidth(maxWidth).margin
        // The bar's own title inset is 24dp; shift it to the centred column's leading edge.
        val headerGutter = ((maxWidth - maxContentWidth) / 2 - 24.dp).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                contentGutter = headerGutter,
                // Stack's scroll-edge rule under the pinned header; the bottom edge is open.
                scrolledUnder = if (showScrollEdgeShadow) ({ scrollState.canScrollBackward }) else ({ false }),
                trailing = trailing,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Lift content above the keyboard; a focused field then scrolls itself into view.
                    .imePadding()
                    .verticalScroll(scrollState),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth + windowMargin * 2)
                        .fillMaxWidth()
                        .padding(horizontal = windowMargin)
                        // Insets are consumption-aware: while the IME is up it already covers the
                        // nav bar, so this collapses instead of double-padding.
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                        .padding(top = YomuTheme.space.xxs, bottom = YomuTheme.space.lg),
                    verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
                    content = content,
                )
            }
        }
    }
}
