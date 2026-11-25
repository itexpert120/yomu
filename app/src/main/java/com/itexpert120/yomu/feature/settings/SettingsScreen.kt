package com.itexpert120.yomu.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.core.designsystem.StackCompactHeader
import com.itexpert120.yomu.core.designsystem.StackCompactHeaderHeight
import com.itexpert120.yomu.core.designsystem.StackHeaderFadeDistance
import com.itexpert120.yomu.core.designsystem.StackTitleRow
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackPinnedEdgeFade
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.feature.sync.SyncStatusCard
import com.itexpert120.yomu.feature.sync.SyncUiState

/** The panes of the tablet list-detail layout; on phones each opens as its own page. */
internal enum class SettingsPane { Sync, Appearance, ReadingDefaults, Fonts, About }

/** Everything the Settings list shows; the Route fills it from the app, reader and sync state. */
internal data class SettingsUiState(
    val selectedTheme: ThemePreference = ThemePreference.System,
    val oledDark: Boolean = false,
    val readerSettings: ReaderSettings = ReaderSettings(),
    val installedFontCount: Int = 0,
    val sync: SyncUiState = SyncUiState(),
)

/** What the Settings list asks for; navigation targets are resolved by the Route. */
internal class SettingsActions(
    val onOpenPane: (SettingsPane) -> Unit,
    val onSelectTheme: (ThemePreference) -> Unit,
    val onToggleOled: (Boolean) -> Unit,
    val onSendFeedback: () -> Unit,
)

private val ReaderThemeMode.stackName: String
    get() = when (this) {
        ReaderThemeMode.Light -> "Paper"
        ReaderThemeMode.Dark -> "Night"
        ReaderThemeMode.Sepia -> "Sepia"
        ReaderThemeMode.Black -> "Black"
        ReaderThemeMode.Custom -> "Custom"
    }

/** "Lora · 100% · Paper": the font, size and page theme every book starts with. */
internal fun ReaderSettings.defaultsSummary(): String = listOf(
    customFont?.family ?: font.displayName,
    "${(fontScale * 100).toInt()}%",
    theme.stackName,
).joinToString(" · ")

private fun fontsSummary(count: Int) = "$count installed"

private val VersionText = "Open Reader ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}"

/**
 * Stack's Settings on a phone: the title and sync status lie on the desk; the grouped list is a
 * sheet over them. Appearance (theme inline, pure black), Reading, then help, About and the
 * version. Scrolled, the title gives way to the compact header and the list slides under it.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    scroll: ScrollState = rememberScrollState(),
) {
    val stack = YomuTheme.stack
    val density = LocalDensity.current
    var deskHeight by remember { mutableIntStateOf(0) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerBottom = with(density) { (statusTop + StackCompactHeaderHeight).toPx() }
    val headerFade = with(density) { StackHeaderFadeDistance.toPx() }
    val sheetRadius = with(density) { 32.dp.toPx() }
    // All read in layer/draw blocks, so scrolling never recomposes the list.
    val headerProgress = { (scroll.value / headerFade).coerceIn(0f, 1f) }
    val sheetTop = { (deskHeight - scroll.value).toFloat() }
    Box(
        Modifier
            .fillMaxSize()
            // Desk above the sheet, sheet below it, behind the scrolling column: what the edge fade
            // dissolves content into under the compact header.
            .drawBehind {
                drawRect(stack.desk)
                drawRect(stack.sheet, topLeft = Offset(0f, sheetTop().coerceAtLeast(0f)))
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .stackPinnedEdgeFade(edge = { headerBottom }, strength = headerProgress)
                .background(stack.sheet)
                .verticalScroll(scroll),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { deskHeight = it.height }
                    .background(stack.desk)
                    .statusBarsPadding(),
            ) {
                StackTitleRow(title = "Settings", collapse = headerProgress)
                SyncStatusCard(
                    state = state.sync,
                    onClick = { actions.onOpenPane(SettingsPane.Sync) },
                    // The board's card sits 18dp under the 38dp title (8 under the shared 48dp row) and
                    // the sheet 36dp under the card, so the card stands alone on the desk.
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(stack.desk)
                    .graphicsLayer {
                        // The corners square off as the sheet slides under the compact header.
                        val radius = sheetRadius * ((sheetTop() - headerBottom) / sheetRadius).coerceIn(0f, 1f)
                        shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                        clip = true
                    }
                    .background(stack.sheet),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = YomuReadableMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp, bottom = 24.dp),
                ) {
                    SettingsSectionLabel("Appearance")
                    ThemeChoiceRow(selected = state.selectedTheme, onSelect = actions.onSelectTheme)
                    PureBlackRow(state, actions, divider = false)

                    SettingsSectionLabel("Reading")
                    SettingsRow(
                        title = "Reading defaults",
                        subtitle = state.readerSettings.defaultsSummary(),
                        icon = StackIcons.AutoStories,
                        chevron = true,
                        onClick = { actions.onOpenPane(SettingsPane.ReadingDefaults) },
                    )
                    SettingsRow(
                        title = "Fonts",
                        icon = StackIcons.MatchCase,
                        value = fontsSummary(state.installedFontCount),
                        chevron = true,
                        divider = false,
                        onClick = { actions.onOpenPane(SettingsPane.Fonts) },
                    )

                    SettingsSectionLabel("Help")
                    SettingsRow(
                        title = "Send feedback",
                        icon = StackIcons.ChatBubble,
                        chevron = true,
                        onClick = actions.onSendFeedback,
                    )
                    SettingsRow(
                        title = "About Open Reader",
                        icon = StackIcons.Info,
                        chevron = true,
                        divider = false,
                        onClick = { actions.onOpenPane(SettingsPane.About) },
                    )
                    Text(
                        text = VersionText,
                        style = StackType.Caption,
                        color = stack.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                    )
                }
            }
        }
        StackCompactHeader(title = "Settings", progress = headerProgress)
    }
}

/** Theme as an inline choice on its row: Light / Dark / Auto as one compact segmented control. */
@Composable
private fun ThemeChoiceRow(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    SettingsRow(title = "Theme", icon = StackIcons.Contrast) {
        YomuConnectedChoiceGroup(
            options = ThemeOrder,
            selected = selected,
            label = { it.stackLabel },
            onSelect = onSelect,
            height = 38.dp,
            modifier = Modifier.width(ThemeChoiceWidth),
        )
    }
}

/** The board's segmented choice: three 13/600 words with 12dp sides in a 3dp track. */
private val ThemeChoiceWidth = 192.dp

@Composable
private fun PureBlackRow(state: SettingsUiState, actions: SettingsActions, divider: Boolean) {
    val allowed = state.selectedTheme.allowsPureBlack()
    SettingsSwitchRow(
        title = "Pure black at night",
        subtitle = if (allowed) "For OLED screens" else "Available with the Dark or Auto theme",
        icon = StackIcons.DarkMode,
        checked = state.oledDark && allowed,
        enabled = allowed,
        onCheckedChange = actions.onToggleOled,
        divider = divider,
    )
}

/**
 * Settings from the two-pane width: the list on the desk (title, sync status, sections), the
 * chosen section on a sheet beside it. Send feedback acts in place rather than opening a pane.
 */
@Composable
internal fun TabletSettingsLayout(
    state: SettingsUiState,
    selectedPane: SettingsPane,
    actions: SettingsActions,
    detail: @Composable (SettingsPane) -> Unit,
) {
    val stack = YomuTheme.stack
    Row(
        Modifier
            .fillMaxSize()
            .background(stack.desk)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .width(360.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                .padding(bottom = 24.dp),
        ) {
            // The shared title row, so "Settings" sits where "Library" and "Statistics" do (32dp in).
            StackTitleRow(title = "Settings", gutter = 8.dp, modifier = Modifier.padding(bottom = 18.dp))
            SyncStatusCard(
                state = state.sync,
                onClick = { actions.onOpenPane(SettingsPane.Sync) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val allowed = state.selectedTheme.allowsPureBlack()
            PaneItem(
                title = "Appearance",
                subtitle = state.selectedTheme.stackLabel + " · pure black " + if (state.oledDark && allowed) "on" else "off",
                icon = StackIcons.Contrast,
                selected = selectedPane == SettingsPane.Appearance,
                onClick = { actions.onOpenPane(SettingsPane.Appearance) },
            )
            PaneItem(
                title = "Reading defaults",
                subtitle = state.readerSettings.defaultsSummary(),
                icon = StackIcons.AutoStories,
                selected = selectedPane == SettingsPane.ReadingDefaults,
                onClick = { actions.onOpenPane(SettingsPane.ReadingDefaults) },
            )
            PaneItem(
                title = "Fonts",
                subtitle = fontsSummary(state.installedFontCount),
                icon = StackIcons.MatchCase,
                selected = selectedPane == SettingsPane.Fonts,
                onClick = { actions.onOpenPane(SettingsPane.Fonts) },
            )
            PaneItem(
                title = "Send feedback",
                subtitle = null,
                icon = StackIcons.ChatBubble,
                selected = false,
                onClick = actions.onSendFeedback,
            )
            PaneItem(
                title = "About Open Reader",
                subtitle = BuildConfig.VERSION_NAME,
                icon = StackIcons.Info,
                selected = selectedPane == SettingsPane.About,
                onClick = { actions.onOpenPane(SettingsPane.About) },
            )
            Text(
                text = VersionText,
                style = StackType.Caption,
                color = stack.faint,
                modifier = Modifier.padding(start = 16.dp, top = 18.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 32.dp))
                .background(stack.sheet),
        ) {
            AnimatedContent(
                targetState = selectedPane,
                transitionSpec = { yomuContentSwap(forward = targetState.ordinal > initialState.ordinal) },
                label = "settingsPane",
            ) { pane -> detail(pane) }
        }
    }
}

/** A row of the tablet list: 64dp, rounded, the open pane on a sheet-white fill. */
@Composable
private fun PaneItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) stack.sheet else stack.desk)
            .yomuPressable(onClick = onClick, shape = RoundedCornerShape(16.dp))
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) stack.ink else stack.muted, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (selected) StackType.Body.copy(fontWeight = FontWeight.SemiBold) else StackType.Body,
                color = stack.ink,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = StackType.Label.copy(fontWeight = FontWeight.Normal),
                    color = stack.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(StackIcons.KeyboardArrowRight, contentDescription = null, tint = stack.muted)
    }
}

/** The Appearance pane of the tablet layout: the theme choices inline instead of in a sheet. */
@Composable
internal fun AppearancePane(
    state: SettingsUiState,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
) {
    SettingsSubpage(title = null, onBack = null, contentPadding = 32.dp) {
        Text(
            text = "Appearance",
            style = StackType.Title,
            color = YomuTheme.stack.ink,
            modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
        )
        AppearanceControls(
            selectedTheme = state.selectedTheme,
            oledDark = state.oledDark,
            onSelectTheme = onSelectTheme,
            onToggleOled = onToggleOled,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

private val PreviewActions = SettingsActions({}, {}, {}, {})

private val PreviewState = SettingsUiState(
    selectedTheme = ThemePreference.System,
    readerSettings = ReaderSettings(theme = ReaderThemeMode.Light),
    installedFontCount = 7,
    sync = SyncUiState(enabled = true),
)

@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun SettingsLightPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) { SettingsScreen(PreviewState, PreviewActions) }
}

@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun SettingsDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
        SettingsScreen(PreviewState.copy(selectedTheme = ThemePreference.Light, sync = SyncUiState()), PreviewActions)
    }
}

@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun SettingsBlackPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Oled) { SettingsScreen(PreviewState.copy(oledDark = true), PreviewActions) }
}

@Preview(name = "Scrolled", widthDp = 390, heightDp = 560)
@Composable
private fun SettingsScrolledPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        SettingsScreen(PreviewState, PreviewActions, rememberScrollState(420))
    }
}

@Preview(name = "Scrolled dark", widthDp = 390, heightDp = 560)
@Composable
private fun SettingsScrolledDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
        SettingsScreen(PreviewState, PreviewActions, rememberScrollState(420))
    }
}

@Preview(widthDp = 1200, heightDp = 800)
@Composable
private fun TabletSettingsPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        TabletSettingsLayout(PreviewState, SettingsPane.Appearance, PreviewActions) {
            AppearancePane(PreviewState, onSelectTheme = {}, onToggleOled = {})
        }
    }
}

@Preview(widthDp = 1200, heightDp = 800)
@Composable
private fun TabletSettingsDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
        TabletSettingsLayout(PreviewState, SettingsPane.Appearance, PreviewActions) {
            AppearancePane(PreviewState, onSelectTheme = {}, onToggleOled = {})
        }
    }
}
