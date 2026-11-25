@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/** Keep app chrome stationary while the outgoing reader still hides the system bars. */
@Composable
internal fun yomuStableSystemBarInsets(): WindowInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

/** Material 3 top app bar compatibility wrapper for detail and settings screens. */
@Composable
fun YomuScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    // Extra inset on each side so the title lines up with width-capped, centred content below.
    contentGutter: Dp = 0.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        windowInsets = yomuStableSystemBarInsets()
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            .add(WindowInsets(left = contentGutter, right = contentGutter)),
        scrollBehavior = scrollBehavior,
        modifier = modifier.zIndex(1f),
        title = { YomuScreenTitle(title) },
        subtitle = subtitle?.let { text ->
            {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                YomuBackButton(onClick = onBack)
            }
        },
        actions = trailing,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/**
 * The Yomu screen title: every top-level and detail screen sets its app-bar title in the same
 * emphasized weight with slightly tightened tracking, at whatever size the bar is interpolating.
 */
@Composable
fun YomuScreenTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text = text,
        modifier = modifier,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Back navigation icon button with the expressive press-morph shape. */
@Composable
fun YomuBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            imageVector = StackIcons.ArrowBack,
            contentDescription = "Back",
        )
    }
}

/** Standard detail-screen shell with a Material 3 top app bar and capped tablet content. */
@Composable
fun YomuScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
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
        // The bar's own title inset is 16dp; shift it to the centred column's leading edge.
        val headerGutter = ((maxWidth - maxContentWidth) / 2 - 16.dp).coerceAtLeast(windowMargin - 16.dp)
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                contentGutter = headerGutter,
                trailing = trailing,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (showScrollEdgeShadow) {
                            Modifier.yomuScrollEdgeShadow(
                                bottom = scrollState.canScrollForward,
                            )
                        } else {
                            Modifier
                        },
                    )
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
