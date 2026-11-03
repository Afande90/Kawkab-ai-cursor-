package app.kawkab.android.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The explicit test cannot acquire its mic until the service's pause callback has finished. */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WakePauseHandoffTest {
    @Test fun `ordered pause acknowledges only after the worker receiver releases the microphone`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        var released = false
        val worker = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                released = true
            }
        }
        ContextCompat.registerReceiver(
            context,
            worker,
            IntentFilter("app.kawkab.android.WAKE_PAUSE"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            val waiting = async {
                WakeWordService.pauseAndAwait(context)
                released
            }
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue("acknowledgement must be delivered", waiting.isCompleted)
            assertTrue("worker closes before acknowledgement", waiting.await())
        } finally {
            context.unregisterReceiver(worker)
        }
    }

    @Test fun `pause completes when background listening is off`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val waiting = async { WakeWordService.pauseAndAwait(context) }
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertTrue(waiting.isCompleted)
        waiting.await()
    }
}
