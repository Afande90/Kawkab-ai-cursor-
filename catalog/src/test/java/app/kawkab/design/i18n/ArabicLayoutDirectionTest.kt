package app.kawkab.design.i18n

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kawkab.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Reset rule 5b: the interface stays left to right even on an Arabic phone.
 */
@RunWith(AndroidJUnit4::class)
class ArabicLayoutDirectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(sdk = [36], qualifiers = "ar")
    fun `an Arabic phone still has an English left to right interface`() {
        composeTestRule.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.setLocale(Locale.forLanguageTag("ar"))
            configuration.setLayoutDirection(Locale.forLanguageTag("ar"))
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                KnotworkTheme {
                    assertEquals(LayoutDirection.Ltr, LocalLayoutDirection.current)
                }
            }
        }
    }
}
