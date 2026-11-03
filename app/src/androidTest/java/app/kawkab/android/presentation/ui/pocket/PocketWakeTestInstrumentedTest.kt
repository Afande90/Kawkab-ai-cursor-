package app.kawkab.android.presentation.ui.pocket

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.kawkab.design.device.DeviceState
import app.kawkab.design.device.KawkabDevice
import app.kawkab.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test

/** The wake test explains its distance and target, and cannot start before owner enrollment. */
class PocketWakeTestInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unregisteredOwnerCannotStartATest() {
        compose.setContent {
            KnotworkTheme {
                KawkabDevice(DeviceState(), lcdContent = { PocketWakeTest(false, 0) {} })
            }
        }
        compose.onNodeWithText("ENROLL YOUR VOICE FIRST").assertIsDisplayed()
        compose.onNodeWithText("TRY 1").assertDoesNotExist()
    }

    @Test fun freshTestShowsDistanceTargetAndPrivateLog() {
        compose.setContent {
            KnotworkTheme {
                KawkabDevice(DeviceState(), lcdContent = { PocketWakeTest(true, 0) {} })
            }
        }
        compose.onNodeWithText("AT ARM'S LENGTH — SAY KAWKAB").assertIsDisplayed()
        compose.onNodeWithText("FRESH TRIES 0/10 · TARGET 9/10").assertIsDisplayed()
        compose.onNodeWithText("TRY 1").assertIsDisplayed()
        compose.onNodeWithText("> SCORES ONLY. AUDIO IS NOT SAVED.").assertIsDisplayed()
    }
}
