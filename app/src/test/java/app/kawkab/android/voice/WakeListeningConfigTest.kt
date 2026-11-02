package app.kawkab.android.voice

import android.content.Intent
import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakeListeningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Voice settings persist and cross the wake-process boundary as a complete immutable snapshot. */
@RunWith(RobolectricTestRunner::class)
class WakeListeningConfigTest {
    @Test fun `smart is selected by default but setup has not yet enabled the microphone`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(WakeListeningMode.SMART, WakeWordSwitch.config(context).mode)
        assertFalse(WakeWordSwitch.isEnabled(context))
    }

    @Test fun `an existing explicit off setting migrates to off rather than silently enabling smart`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("wake_word", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", false).commit()
        assertEquals(WakeListeningMode.OFF, WakeWordSwitch.config(context).mode)
        assertFalse(WakeWordSwitch.isEnabled(context))
    }

    @Test fun `a failed wake service is stopped as well as disabled so retry gets fresh capture`() {
        val context = RuntimeEnvironment.getApplication()
        context.startService(Intent(context, WakeWordService::class.java))
        WakeWordSwitch.set(context, true)
        WakeWordSwitch.failed(context, "microphone unavailable")
        assertFalse(WakeWordSwitch.isEnabled(context))
        assertEquals(WakeListeningMode.OFF, WakeWordSwitch.config(context).mode)
        assertEquals("microphone unavailable", WakeWordSwitch.failure(context))
        assertEquals(
            WakeWordService::class.java.name,
            requireNotNull(shadowOf(context).nextStoppedService).component?.className,
        )
    }

    @Test fun `sleep hours can be saved before enrollment without enabling capture`() {
        val context = RuntimeEnvironment.getApplication()
        val config = WakeListeningConfig(sleepEnabled = true)
        WakeWordSwitch.configure(context, config, activate = false)
        assertEquals(config, WakeWordSwitch.config(context))
        assertFalse(WakeWordSwitch.isEnabled(context))
    }

    @Test fun `updating listening mode preserves wake counts and sends current sleep hours`() {
        val context = RuntimeEnvironment.getApplication()
        WakeWordSwitch.set(context, true)
        WakeWordSwitch.countWake(context)
        val config = WakeListeningConfig(WakeListeningMode.CHARGING, true, 23 * 60, 6 * 60)
        WakeWordSwitch.configure(context, config)
        assertEquals(config, WakeWordSwitch.config(context))
        assertEquals(1, WakeWordSwitch.stats(context)!!.wakes)
        val intent = WakeWordSwitch.attachConfig(context, Intent())
        WakeWordSwitch.configure(context, config.copy(mode = WakeListeningMode.OFF))
        assertFalse(WakeWordSwitch.isEnabled(context))
        assertEquals(config, WakeWordSwitch.readConfig(context, intent))
        WakeWordSwitch.set(context, true)
        assertTrue(WakeWordSwitch.isEnabled(context))
        assertEquals(WakeListeningMode.SMART, WakeWordSwitch.config(context).mode)
    }
}
