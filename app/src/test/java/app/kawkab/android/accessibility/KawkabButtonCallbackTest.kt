package app.kawkab.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Task 23b: the floating accessibility button opens the panel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KawkabButtonCallbackTest {

    @Test
    fun `a tap on the accessibility button opens the panel`() {
        var opened = 0
        val callback = KawkabButtonCallback { opened += 1 }

        callback.onClicked(null)

        assertEquals(1, opened)
    }

    @Test
    fun `the service asks Android for the accessibility button`() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        assertTrue(config.contains("flagRequestAccessibilityButton"))
        val service = File("src/main/java/app/kawkab/android/accessibility/KawkabAccessibilityService.kt").readText()
        assertTrue(service.contains("registerAccessibilityButtonCallback"))
    }
}
