package app.kawkab.design.device

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kawkab.design.KnotworkRoborazziOptions
import app.kawkab.design.a11y.FixedKnotworkA11y
import app.kawkab.design.a11y.LocalKnotworkA11y
import app.kawkab.design.theme.KnotworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One screenshot for every owner-approved LCD screen; fixed motion and fixture data. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xhdpi")
class PocketDeviceSnapshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun f1Idle() = capture(DeviceState(date = "MON 02 NOV", battery = "84%", result = "ALARM 06:00"))

    @Test fun f2Listening() =
        capture(DeviceState(page = DevicePage.LISTENING, signal = DeviceSignal.LISTENING, heard = "call Abdalla"))

    @Test fun f3Which() = capture(
        DeviceState(
            page = DevicePage.WHICH,
            signal = DeviceSignal.ASKING,
            name = "ABDALLA",
            choices = listOf("Abdalla Mubadala", "Wad Abdalla"),
        ),
    )

    @Test fun f4Calling() = capture(
        DeviceState(
            page = DevicePage.CALLING,
            signal = DeviceSignal.ACTING,
            name = "ABDALLA MUBADALA",
            number = "+249 ... 6117",
            usual = true,
        ),
    )

    @Test fun f5Talk() = capture(
        DeviceState(
            page = DevicePage.TALK,
            heard = "ذكرني يوم الثلاثاء",
            reply = "حاضر، يوم الثلاثاء الساعة تسعة",
            result = "REMINDER SET TUE 09:00",
        ),
    )

    @Test fun f6Shots() = capture(
        DeviceState(
            page = DevicePage.SHOTS,
            shots = listOf(
                DeviceShot("Interview", "JOB"),
                DeviceShot("Electricity", "BILL"),
                DeviceShot("Khartoum", "PLACE"),
                DeviceShot("What is this?", "OTHER", true),
            ),
        ),
    )

    @Test fun f7Growth() = capture(
        DeviceState(
            page = DevicePage.GROWTH,
            learned = listOf(DeviceLearning("abdalla", "Abdalla means Abdalla Mubadala")),
            accuracy = 86,
            trend = listOf(42, 65, 72, 86),
        ),
    )

    @Test fun f8Enrollment() = capture(
        DeviceState(page = DevicePage.ENROLL, signal = DeviceSignal.LISTENING, enrolled = 6),
    )

    private fun capture(state: DeviceState) {
        compose.setContent {
            KnotworkTheme {
                CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                    KawkabDevice(state)
                }
            }
        }
        compose.onNodeWithText("HOME").assertIsDisplayed()
        compose.onNode(hasText("SHOTS") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("GROWTH").assertIsDisplayed()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/pocket_${state.page.name.lowercase()}.png",
            roborazziOptions = KnotworkRoborazziOptions,
        )
    }
}
