package app.kawkab.design.screens.phoneaccess

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
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

/** Task 23: the Phone access cards, as screenshot goldens, and their structure. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1600dp-xhdpi")
class PhoneAccessContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun phoneAccess_mixed_light() = snapshot(name = "mixed", dark = false) {
        PhoneAccessContent(PhoneAccessPreview.mixed(), {}, {})
    }

    @Test
    fun phoneAccess_mixed_dark() = snapshot(name = "mixed", dark = true) {
        PhoneAccessContent(PhoneAccessPreview.mixed(), {}, {})
    }

    @Test
    fun phoneAccess_shows_the_progress_line_and_all_eight_cards() {
        composeTestRule.setContent { KnotworkTheme { PhoneAccessContent(PhoneAccessPreview.mixed(), {}, {}) } }
        composeTestRule.onNodeWithTag(PROGRESS_TAG).assertIsDisplayed()
        PhoneAccessKind.entries.forEach { kind ->
            composeTestRule.onNodeWithTag("$CARD_TAG_PREFIX${kind.name}").performScrollTo().assertIsDisplayed()
        }
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                    content()
                }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            roborazziOptions = KnotworkRoborazziOptions,
            filePath = "src/test/snapshots/phone_access_${name}_$themeTag.png",
        )
    }
}

internal object PhoneAccessPreview {
    fun mixed(): PhoneAccessViewState {
        val on =
            setOf(
                PhoneAccessKind.Contacts,
                PhoneAccessKind.Phone,
                PhoneAccessKind.Calendar,
                PhoneAccessKind.Notifications,
                PhoneAccessKind.Microphone,
            )
        return PhoneAccessViewState(
            title = "Phone access",
            progress = "5 of 8 on",
            cards = PhoneAccessKind.entries.map { kind ->
                PhoneAccessCard(
                    kind = kind,
                    title = kind.name,
                    reason = "Why Kawkab needs this, in one line.",
                    on = kind in on,
                    statusLabel = if (kind in on) "On" else "Off",
                    buttonLabel = if (kind in on) "Review in settings" else "Allow",
                    note = if (kind ==
                        PhoneAccessKind.Assistant
                    ) {
                        "Also set the power button to Digital assistant."
                    } else {
                        null
                    },
                )
            },
            doneLabel = "Continue",
        )
    }
}
