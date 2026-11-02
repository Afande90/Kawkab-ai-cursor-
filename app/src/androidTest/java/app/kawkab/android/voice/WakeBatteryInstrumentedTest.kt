package app.kawkab.android.voice

import android.Manifest
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.voice.WakeListeningConfig
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.domain.voice.WakeListeningPolicy
import app.kawkab.android.domain.voice.WakeListeningState
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

/** A real recorder disappears from Android's active capture list for every wake pause rule. */
class WakeBatteryInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyPauseReleasesTheActualAudioRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        compose.setContent { Text("Wake microphone lifecycle test") }
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        val config = WakeListeningConfig(sleepEnabled = true)
        val ready = WakeListeningState(batteryPercent = 80)
        val cases = listOf(
            Triple(config, ready.copy(covered = true), LocalTime.NOON),
            Triple(config, ready.copy(faceDown = true), LocalTime.NOON),
            Triple(config, ready.copy(batteryPercent = 19), LocalTime.NOON),
            Triple(config, ready.copy(inCall = true), LocalTime.NOON),
            Triple(config, ready, LocalTime.MIDNIGHT),
            Triple(config.copy(mode = WakeListeningMode.CHARGING), ready, LocalTime.NOON),
            Triple(config.copy(mode = WakeListeningMode.OFF), ready, LocalTime.NOON),
        )
        for ((settings, state, time) in cases) {
            val before = audio.activeRecordingConfigurations.count {
                it.clientAudioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
            WakeMicSession({ WakeAudioInput.open(context) }, {}).use { session ->
                session.setAllowed(true)
                assertNotNull("real microphone must open", session.acquireOrWait(1))
                compose.waitUntil(timeoutMillis = WAIT_MS) {
                    audio.activeRecordingConfigurations.any {
                        it.clientAudioSource ==
                            MediaRecorder.AudioSource.VOICE_RECOGNITION
                    }
                }
                session.setAllowed(WakeListeningPolicy.pauseReason(settings, state, time) == null)
                compose.waitUntil(timeoutMillis = WAIT_MS) {
                    audio.activeRecordingConfigurations.none {
                        it.clientAudioSource ==
                            MediaRecorder.AudioSource.VOICE_RECOGNITION
                    }
                }
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
