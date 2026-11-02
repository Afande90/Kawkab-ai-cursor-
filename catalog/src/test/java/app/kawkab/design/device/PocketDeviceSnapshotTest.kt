package app.kawkab.design.device

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kawkab.design.KnotworkRoborazziOptions
import app.kawkab.design.a11y.FixedKnotworkA11y
import app.kawkab.design.a11y.LocalKnotworkA11y
import app.kawkab.design.theme.KnotworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            heard = "call Abdalla Mubadala",
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
        val panel = compose.onNodeWithTag("lcd_panel").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithTag("lcd_bottom").getUnclippedBoundsInRoot()
        assertEquals("LCD height", 400f, panel.bottom.value - panel.top.value, 0.5f)
        assertEquals("final block is pinned to the LCD bottom", panel.bottom.value - 16f, bottom.bottom.value, 0.5f)
        if (state.page == DevicePage.CALLING) assertCalling(panel.right.value, panel.bottom.value)
        compose.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/pocket_${state.page.name.lowercase()}.png",
            roborazziOptions = KnotworkRoborazziOptions,
        )
    }
    private fun assertCalling(panelRight: Float, panelBottom: Float) {
        compose.onNodeWithText("HEARD: CALL ABDALLA MUBADALA").assertIsDisplayed()
        compose.onNodeWithText("MOB ..6117  (YOUR USUAL)").assertIsDisplayed()
        compose.onNodeWithText("[■■■□□□□□□]").assertIsDisplayed()
        val digit = compose.onNodeWithTag("countdown_digit").getUnclippedBoundsInRoot()
        val progress = compose.onNodeWithTag("calling_progress").getUnclippedBoundsInRoot()
        val hint = compose.onNodeWithTag("calling_hint").getUnclippedBoundsInRoot()
        val key = compose.onNodeWithTag("talk_key").getUnclippedBoundsInRoot()
        val shadow = compose.onNodeWithTag("talk_shadow").getUnclippedBoundsInRoot()
        assertEquals(panelRight - 16f, digit.right.value, 0.5f)
        assertEquals(panelBottom - 16f, progress.bottom.value, 0.5f)
        assertTrue("hint must be outside the LCD", hint.top.value > panelBottom)
        assertEquals(124f, key.right.value - key.left.value, 0.5f)
        assertEquals(124f, key.bottom.value - key.top.value, 0.5f)
        assertEquals(8f, shadow.top.value - key.top.value, 0.5f)
        assertStyle("calling_heard", 22f)
        assertStyle("calling_title", 46f)
        assertStyle("calling_name", 32f)
        assertStyle("calling_number", 22f)
        assertStyle("calling_progress", 26f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("countdown_digit").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertEquals(150.sp, layouts.single().layoutInput.style.fontSize)
        assertEquals(127.5.sp, layouts.single().layoutInput.style.lineHeight)
    }

    private fun assertStyle(tag: String, size: Float) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(size.sp, layouts.single().layoutInput.style.fontSize)
    }
}
