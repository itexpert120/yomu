package com.itexpert120.yomu

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuLabeledIconAction
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YomuDesignSystemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labeledIconActionExposesAccessibleButtonSemantics() {
        composeRule.setContent {
            YomuDesignTheme {
                YomuLabeledIconAction(
                    label = "Search",
                    icon = StackIcons.Search,
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Search")
            .assertHasClickAction()
            .assertIsEnabled()
    }
}
