package com.itexpert120.yomu.app.devgallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.data.collections.Membership
import com.itexpert120.yomu.data.collections.OrganizeCollection
import com.itexpert120.yomu.data.collections.StatusChoice
import com.itexpert120.yomu.feature.collections.OrganizeSheet
import com.itexpert120.yomu.feature.library.LibraryOptionsSheet
import com.itexpert120.yomu.feature.settings.ThemeSheet

// Sheets as side sheets on tablets (YomuBottomSheet from medium widths).

@Composable
private fun SideSheetPreviewFrame(mode: YomuThemeMode, sheet: @Composable () -> Unit) = YomuDesignTheme(themeMode = mode) {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk)) { sheet() }
}

@Preview(name = "Arrange side sheet", widthDp = 1280, heightDp = 800)
@Composable
private fun ArrangeSideSheetPreview() = SideSheetPreviewFrame(YomuThemeMode.Light) {
    LibraryOptionsSheet(
        visible = true,
        sortMode = SortMode.Recent,
        viewMode = LibraryViewMode.ComfortableGrid,
        portraitColumns = 0,
        landscapeColumns = 5,
        onSortModeChange = {},
        onViewModeChange = {},
        onPortraitColumnsChange = {},
        onLandscapeColumnsChange = {},
        onDismiss = {},
    )
}

@Preview(name = "Organize side sheet", widthDp = 840, heightDp = 1000)
@Composable
private fun OrganizeSideSheetPreview() = SideSheetPreviewFrame(YomuThemeMode.Dark) {
    OrganizeSheet(
        visible = true,
        title = "Organize",
        subtitle = "The Wind in the Willows",
        status = StatusChoice.Auto,
        collections = listOf(
            OrganizeCollection("1", "Classics", Membership.All),
            OrganizeCollection("2", "Bedtime", Membership.None),
            OrganizeCollection("3", "Summer 2026", Membership.Some),
        ),
        onSetStatus = {},
        onSetCollection = { _, _ -> },
        onCreateCollection = {},
        onDismiss = {},
    )
}

@Preview(name = "Theme side sheet", widthDp = 1280, heightDp = 800)
@Composable
private fun ThemeSideSheetPreview() = SideSheetPreviewFrame(YomuThemeMode.Light) {
    ThemeSheet(
        visible = true,
        selectedTheme = ThemePreference.System,
        oledDark = false,
        onSelectTheme = {},
        onToggleOled = {},
        onDismiss = {},
    )
}
