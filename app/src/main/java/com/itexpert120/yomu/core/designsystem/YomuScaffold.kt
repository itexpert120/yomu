package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Custom (non-Material) top bar for detail/settings screens: optional back affordance, title,
 * optional subtitle, and trailing actions. Deliberately not a Material `TopAppBar`
 * (docs/design-language.md "Anti-Material Rules").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YomuScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    elevated: Boolean = false,
    // Lets callers fade the title in on scroll (e.g. a title-less bar that reveals the title once
    // the in-content title scrolls away).
    titleVisible: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // Lift the header onto its own plane once content scrolls beneath it; the shadow defines the
    // seam instead of the content hard-cutting at the header edge.
    val elevation by animateDpAsState(
        targetValue = if (elevated) 4.dp else 0.dp,
        label = "screenHeaderElevation",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation)
            .background(YomuTheme.colors.appBackground)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            YomuBackButton(onBack)
        }
        Column(Modifier.weight(1f)) {
            AnimatedVisibility(
                visible = titleVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column {
                    Text(
                        text = title,
                        color = YomuTheme.colors.textPrimary,
                        style = YomuTheme.type.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = YomuTheme.colors.textMuted,
                            style = YomuTheme.type.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        trailing()
    }
}

@Composable
private fun YomuBackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = YomuTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Standard detail-screen shell: app surface, a [YomuScreenHeader], and a vertically scrolling
 * content column padded for the page gutter and bottom system bar. Content arrives as a
 * [ColumnScope] so callers stack [YomuSettingGroup]s / panels directly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YomuScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()
    YomuAppSurface(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            YomuScreenHeader(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                elevated = scrollState.value > 0,
                trailing = trailing,
            )
            // Content is centered and capped so detail screens read intentionally on tablets
            // rather than stretching edge-to-edge; on phones it just fills the width.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = navBottom + 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}
