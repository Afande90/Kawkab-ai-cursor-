package app.kawkab.android.presentation.ui.phoneaccess

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.phoneaccess.PhoneAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Task 23 on the emulator: with permissions off, the chat banner shows and opens Phone access. */
@RunWith(AndroidJUnit4::class)
class PhoneAccessBannerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theBannerShowsWhileSomethingIsOffAndOpensPhoneAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val off = PhoneAccessStatus.offCount(AndroidPhoneAccess.lines(context))
        assertTrue("a fresh emulator install has permissions off", off > 0)
        var opened = 0
        composeTestRule.setContent { PhoneAccessBanner(onOpen = { opened += 1 }) }
        composeTestRule.onNodeWithTag(PHONE_ACCESS_BANNER_TAG).assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun phoneAccessListsEightItems() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(8, AndroidPhoneAccess.lines(context).size)
    }
}
