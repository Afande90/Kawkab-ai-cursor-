package app.kawkab.design.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kawkab.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Task 23: Phone access is a Basic row on the settings hub, and it opens the screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsHubPhoneAccessTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the phone access row shows its progress and opens the screen`() {
        var opened = 0
        composeTestRule.setContent {
            KnotworkTheme {
                SettingsHubContent(
                    state = SettingsPreview.hubDefault().copy(phoneAccessSummary = "5 of 8 on"),
                    callbacks = SettingsCallbacks(onOpenPhoneAccess = { opened += 1 }),
                )
            }
        }
        composeTestRule.onNodeWithTag(PHONE_ACCESS_ROW_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("5 of 8 on").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }
}
