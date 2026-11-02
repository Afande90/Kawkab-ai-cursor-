package app.kawkab.android.presentation.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.kawkab.android.presentation.state.ChatEntryRequestRelay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pocket keys switch pages; Advanced routes never accumulate above the home anchor. */
class AppShellNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deviceKeysSwitchToTargetPage() {
        composeTestRule.setContent {
            PocketNavigationTestHost(rememberNavController(), ChatEntryRequestRelay())
        }
        composeTestRule.onNodeWithText("chat-home-body").assertIsDisplayed()
        composeTestRule.onNodeWithText("SHOTS").performClick()
        composeTestRule.onNodeWithText("shots-body").assertIsDisplayed()
        composeTestRule.onNodeWithText("SHOTS").assertIsSelected()
        composeTestRule.onNodeWithText("GROWTH").performClick()
        composeTestRule.onNodeWithText("growth-body").assertIsDisplayed()
        composeTestRule.onNodeWithText("GROWTH").assertIsSelected()
        composeTestRule.onNodeWithText("HOME").performClick()
        composeTestRule.onNodeWithText("chat-home-body").assertIsDisplayed()
        composeTestRule.onNodeWithText("HOME").assertIsSelected()
    }

    /** Reproduces removal of splash: it must never remain the popUpTo anchor. */
    @Test
    fun pageAndAdvancedSwitchesKeepBackStackBoundedAfterSplash() {
        lateinit var nav: NavHostController
        composeTestRule.setContent {
            nav = rememberNavController()
            PocketNavigationTestHost(nav, ChatEntryRequestRelay(), splash = true)
        }
        repeat(3) {
            composeTestRule.onNodeWithText("SHOTS").performClick()
            composeTestRule.onNodeWithText("GROWTH").performClick()
            composeTestRule.onNodeWithText("HOME").performClick()
            composeTestRule.onNodeWithText("⋮").performClick()
            composeTestRule.onNodeWithText("ADVANCED").performClick()
            composeTestRule.onNodeWithText("more-body").assertIsDisplayed()
            composeTestRule.runOnIdle {
                assertEquals(listOf(NavRoutes.CHAT_TAB, NavRoutes.MORE), nav.tabRoutesOnStack())
            }
            composeTestRule.onNodeWithText("HOME").performClick()
            composeTestRule.onNodeWithText("chat-home-body").assertIsDisplayed()
            composeTestRule.runOnIdle {
                assertEquals(listOf(NavRoutes.CHAT_TAB), nav.tabRoutesOnStack())
            }
        }
    }

    private fun NavHostController.tabRoutesOnStack(): List<String> {
        val tabRoutes = TAB_DESTINATIONS.map { it.route }.toSet()
        return currentBackStack.value.mapNotNull { it.destination.route }.filter { it in tabRoutes }
    }
}
