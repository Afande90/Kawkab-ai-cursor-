package app.kawkab.android.voice

import app.kawkab.android.domain.voice.WakeAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Private diagnostics survive restart, exclude audio, and tolerate concurrent process-style writers. */
@RunWith(RobolectricTestRunner::class)
class WakeScoreLogTest {
    @Test fun `score-only records survive reopening without being stored in backed-up files`() {
        val context = RuntimeEnvironment.getApplication()
        val match = WakeAssessment(personal = 0.95, speaker = 0.8, templates = 10)
        WakeScoreLog(context).record(WakeScoreLog.Source.TEST, match, 123L)
        assertEquals(listOf(match), WakeScoreLog(context).recent())
        assertFalse(File(context.filesDir, "wake_scores").exists())
        val text = File(context.noBackupFilesDir, "wake_scores").listFiles()!!.single().readText()
        val row = org.json.JSONObject(text)
        assertEquals(
            setOf(
                "time_ms", "source", "personal", "generic", "generic_threshold",
                "sherpa", "speaker", "templates", "outcome",
            ),
            row.keys().asSequence().toSet(),
        )
        assertEquals("TEST", row.getString("source"))
    }

    @Test fun `simultaneous writers keep every decision and unknown speaker stays null`() {
        val context = RuntimeEnvironment.getApplication()
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(20) {
                workers.submit { WakeScoreLog(context).record(WakeScoreLog.Source.BACKGROUND, WakeAssessment()) }
            }
            workers.shutdown()
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
            assertEquals(20, WakeScoreLog(context).recent(30).size)
            assertTrue(WakeScoreLog(context).recent(30).all { it.speaker == null && !it.accepted })
        } finally {
            workers.shutdownNow()
        }
    }
}
