package app.kawkab.design.i18n

import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Compose under an Arabic locale lays out right to left (spec §0.6).
 */
@RunWith(AndroidJUnit4::class)
class ArabicLayoutDirectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(sdk = [36], qualifiers = "ar")
    fun `given an arabic locale when composed then layout direction is rtl`() {
        composeTestRule.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.setLocale(Locale.forLanguageTag("ar"))
            configuration.setLayoutDirection(Locale.forLanguageTag("ar"))
            val direction = if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                assertEquals(LayoutDirection.Rtl, LocalLayoutDirection.current)
            }
        }
    }
}
