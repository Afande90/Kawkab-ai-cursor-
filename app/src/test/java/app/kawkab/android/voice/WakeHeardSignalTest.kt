package app.kawkab.android.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Speech feedback is rate-limited, package-only and never launches an activity or manifest receiver. */
@RunWith(RobolectricTestRunner::class)
class WakeHeardSignalTest {
    @Test fun `speech hints are throttled and cannot open the app`() {
        val context = RuntimeEnvironment.getApplication()
        val signal = WakeHeardSignal(context)
        signal.heard()
        signal.heard()
        var broadcasts = shadowOf(context).broadcastIntents.filter { it.action == WakeHeardSignal.ACTION }
        assertEquals(1, broadcasts.size)
        assertEquals(context.packageName, broadcasts.single().`package`)
        assertNull(broadcasts.single().component)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_800))
        signal.heard()
        broadcasts = shadowOf(context).broadcastIntents.filter { it.action == WakeHeardSignal.ACTION }
        assertEquals(2, broadcasts.size)
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        org.junit.Assert.assertFalse(manifest.contains(WakeHeardSignal.ACTION))
    }
}
