package app.kawkab.android.domain.safety

import app.kawkab.android.domain.models.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Task 23d: the screen text is read lazily and throttled, and the full denylist check still
 * runs immediately before every action.
 */
class LazyScreenCheckTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the first event checks at once and later events wait for the 500 ms gap`() {
        val schedule = TextInspectionSchedule()
        assertEquals(0L, schedule.onEvent(nowMs = 1_000))
        schedule.onRun(nowMs = 1_000)
        assertEquals(400L, schedule.onEvent(nowMs = 1_100))
    }

    @Test
    fun `events while a check is pending fold into it`() {
        val schedule = TextInspectionSchedule()
        schedule.onEvent(nowMs = 0)
        schedule.onRun(nowMs = 0)
        assertEquals(500L, schedule.onEvent(nowMs = 0))
        assertNull(schedule.onEvent(nowMs = 10))
        assertNull(schedule.onEvent(nowMs = 20))
        schedule.onRun(nowMs = 500)
        assertEquals(0L, schedule.onEvent(nowMs = 1_200))
    }

    @Test
    fun `an action reads the full screen even when the per-event snapshot has no text`() {
        val watch = ScreenWatch()
        watch.latest = ScreenSnapshot("com.example.shop", secure = false, passwordField = false, visibleText = "")
        var reads = 0
        watch.fullReader = {
            reads += 1
            ScreenSnapshot("com.example.shop", secure = false, passwordField = false, visibleText = "Pay now")
        }
        val runner = ActionRunner(ActionDenylist.fromText(""), FileActivityLog(log()), watch, clock = { 1L })
        var ran = false

        val result = runner.run("open", "open_app", ToolRisk.READ_ONLY, "tool", confirmed = false) {
            ran = true
            "opened"
        }

        assertTrue(result.startsWith("Blocked"))
        assertEquals(false, ran)
        assertEquals(1, reads)
        assertEquals("Pay now", watch.latest.visibleText)
    }

    @Test
    fun `without the service the last snapshot is checked`() {
        val watch = ScreenWatch()
        val idle = watch.forAction()
        assertEquals(ScreenSnapshot.idle(), idle)
    }

    @Test
    fun `the service listens only to window changes with a 300 ms timeout`() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        val types = Regex("accessibilityEventTypes=\"([^\"]+)\"").find(config)!!.groupValues[1]
        assertEquals(setOf("typeWindowStateChanged", "typeWindowContentChanged"), types.split('|').toSet())
        val timeout = Regex("notificationTimeout=\"(\\d+)\"").find(config)!!.groupValues[1].toInt()
        assertTrue("notificationTimeout is $timeout", timeout >= 300)
    }

    @Test
    fun `the per-event path does not walk the node tree`() {
        val source = File("src/main/java/app/kawkab/android/accessibility/KawkabAccessibilityService.kt").readText()
        val onEvent = source.substringAfter(
            "override fun onAccessibilityEvent",
        ).substringBefore("override fun onInterrupt")
        assertTrue(!onEvent.contains("describe(") && !onEvent.contains("fullSnapshot()"))
        assertTrue(onEvent.contains("postDelayed"))
    }

    private fun log(): File = File(folder.root, "activity.jsonl")
}
