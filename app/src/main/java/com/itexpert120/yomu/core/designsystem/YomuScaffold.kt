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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Material 3 top app bar compatibility wrapper for detail and settings screens. */
@Composable
fun YomuScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        modifier = modifier.zIndex(1f),
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = YomuTheme.type.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = trailing,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    YomuAppSurface(modifier = modifier) {
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                trailing = trailing,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (showScrollEdgeShadow) {
                            Modifier.yomuScrollEdgeShadow(
                                color = MaterialTheme.colorScheme.background,
                                bottom = scrollState.canScrollForward,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .verticalScroll(scrollState),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = YomuContentMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = YomuTheme.space.md)
                        .padding(top = YomuTheme.space.xxs, bottom = navBottom + YomuTheme.space.lg),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}
