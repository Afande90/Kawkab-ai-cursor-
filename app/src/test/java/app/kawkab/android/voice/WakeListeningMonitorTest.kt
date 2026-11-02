package app.kawkab.android.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakePauseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Real Android battery broadcasts and audio-mode callbacks reach the capture policy. */
@RunWith(RobolectricTestRunner::class)
class WakeListeningMonitorTest {
    @Test fun `battery broadcasts pause and charging resumes without opening the app`() {
        val context = RuntimeEnvironment.getApplication<Application>()
        var reason: WakePauseReason? = null
        WakeListeningMonitor(context, { false }, { reason = it }).use { monitor ->
            monitor.configure(WakeListeningConfig())
            context.sendBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 19)
                .putExtra(BatteryManager.EXTRA_SCALE, 100).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(WakePauseReason.LOW_BATTERY, reason)
            context.sendBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 19)
                .putExtra(BatteryManager.EXTRA_SCALE, 100).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull(reason)
        }
    }

    @Test fun `audio mode pauses cellular and communication calls without a phone-state permission`() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var reason: WakePauseReason? = null
        WakeListeningMonitor(context, { false }, { reason = it }).use { monitor ->
            monitor.configure(WakeListeningConfig(mode = WakeListeningMode.ALWAYS))
            for (mode in listOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)) {
                audio.mode = mode
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(WakePauseReason.CALL, reason)
            }
            audio.mode = AudioManager.MODE_NORMAL
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull(reason)
        }
    }
}
