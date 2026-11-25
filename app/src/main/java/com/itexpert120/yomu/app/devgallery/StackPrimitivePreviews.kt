package com.itexpert120.yomu.app.devgallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuNavigationRail
import com.itexpert120.yomu.core.designsystem.YomuNavigationRailItem
import com.itexpert120.yomu.core.designsystem.YomuPressIndication
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuSheetTabsHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

// The Stack review round's primitives: the edge-to-edge phone sheet with its one-line header,
// the tabs-as-header variant, the segmented choice, the press highlight, and the rail on the desk.

@Composable
private fun Desk(mode: YomuThemeMode, content: @Composable () -> Unit) = YomuDesignTheme(themeMode = mode) {
    Box(Modifier.fillMaxSize().background(YomuTheme.stack.desk)) { content() }
}

@Composable
private fun ArrangeLikeSheet() {
    YomuBottomSheet(visible = true, onDismiss = {}) { dismiss ->
        YomuSheetHeader(title = "Arrange", subtitle = "Not shown", onClose = dismiss)
        YomuSectionLabel("Sort by")
        YomuConnectedChoiceGroup(
            options = listOf("Recent", "Title", "Author", "Unread"),
            selected = "Recent",
            label = { it },
            onSelect = {},
        )
        YomuSectionLabel("Theme")
        YomuConnectedChoiceGroup(
            options = listOf("Light", "Dark", "Auto"),
            selected = "Auto",
            label = { it },
            onSelect = {},
            height = 38.dp,
        )
    }
}

@Preview(name = "Phone sheet, light", widthDp = 390, heightDp = 844)
@Composable
private fun PhoneSheetLightPreview() = Desk(YomuThemeMode.Light) { ArrangeLikeSheet() }

@Preview(name = "Phone sheet, dark", widthDp = 390, heightDp = 844)
@Composable
private fun PhoneSheetDarkPreview() = Desk(YomuThemeMode.Dark) { ArrangeLikeSheet() }

@Preview(name = "Side sheet, light", widthDp = 1280, heightDp = 800)
@Composable
private fun SideSheetLightPreview() = Desk(YomuThemeMode.Light) { ArrangeLikeSheet() }

@Preview(name = "Tabs as header", widthDp = 390, heightDp = 844)
@Composable
private fun TabsHeaderPreview() = Desk(YomuThemeMode.Light) {
    YomuBottomSheet(visible = true, onDismiss = {}, scrollable = false) { dismiss ->
        YomuSheetTabsHeader(
            tabs = listOf("Contents", "Bookmarks", "Highlights"),
            selectedIndex = 1,
            onSelect = {},
            onClose = dismiss,
        )
        repeat(6) { Text("Chapter ${it + 1}", style = StackType.Body, modifier = Modifier.padding(vertical = 14.dp)) }
    }
}

@Preview(name = "Press highlight", widthDp = 390, heightDp = 260)
@Composable
private fun PressHighlightPreview() = Desk(YomuThemeMode.Light) {
    val held = remember { MutableInteractionSource() }
    LaunchedEffect(held) { held.emit(PressInteraction.Press(Offset.Zero)) }
    val heldRound = remember { MutableInteractionSource() }
    LaunchedEffect(heldRound) { heldRound.emit(PressInteraction.Press(Offset.Zero)) }
    Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Text(
            "A held row",
            style = StackType.Body,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = held, indication = YomuPressIndication(), onClick = {})
                .padding(vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                "Pill",
                style = StackType.Label,
                modifier = Modifier
                    .clickable(interactionSource = heldRound, indication = YomuPressIndication(CircleShape), onClick = {})
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Preview(name = "Rail on the desk, dark", widthDp = 400, heightDp = 500)
@Composable
private fun RailPreview() = Desk(YomuThemeMode.Dark) {
    YomuNavigationRail(Modifier.fillMaxHeight()) {
        YomuNavigationRailItem(selected = true, onClick = {}, icon = StackIcons.ImportContacts, selectedIcon = StackIcons.ImportContactsFilled, label = "Library")
        YomuNavigationRailItem(selected = false, onClick = {}, icon = StackIcons.Monitoring, label = "Statistics")
        YomuNavigationRailItem(selected = false, onClick = {}, icon = StackIcons.Tune, label = "Settings")
    }
}
