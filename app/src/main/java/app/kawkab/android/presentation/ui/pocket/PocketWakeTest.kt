package app.kawkab.android.presentation.ui.pocket

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.kawkab.android.domain.voice.WakeAssessment
import app.kawkab.android.voice.WakeScoreLog
import app.kawkab.android.voice.WakeTestCapture
import app.kawkab.android.voice.WakeWordService
import app.kawkab.design.device.DeviceKey
import app.kawkab.design.device.LcdLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Fresh arm's-length attempts, with exactly the production scoring path and no action dispatch. */
@Composable
internal fun PocketWakeTest(enrolled: Boolean, talkRequest: Int, onRecording: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempts by remember { mutableStateOf<List<WakeAssessment>>(emptyList()) }
    var recent by remember { mutableStateOf<List<WakeAssessment>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf<WakeTestCapture?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var resumed by remember { mutableStateOf(false) }
    fun stop() {
        capture?.close()
        job?.cancel()
    }
    fun record() {
        if (!enrolled || busy || !resumed || attempts.size >= TEST_TRIES) return
        job = scope.launch {
            busy = true
            onRecording(true)
            message = "SAY KAWKAB NOW"
            val test = WakeTestCapture(context)
            capture = test
            try {
                val result = test.attempt()
                attempts = attempts + result
                message = if (result.accepted) "WOKE FOR YOUR VOICE" else "DID NOT WAKE"
                recent = withContext(Dispatchers.IO) { WakeScoreLog(context).recent() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: LinkageError) {
                message = "VOICE COULD NOT START. TRY REOPENING THE APP."
            } catch (_: Exception) {
                message = "CHECK MICROPHONE ACCESS AND TRY AGAIN."
            } finally {
                test.close()
                capture = null
                busy = false
                onRecording(false)
                resumeListening(context)
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose {
            resumed = false
            stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { stop() }
    }
    LaunchedEffect(Unit) { recent = withContext(Dispatchers.IO) { WakeScoreLog(context).recent() } }
    LaunchedEffect(talkRequest) { if (talkRequest > 0) record() }
    Column(Modifier.fillMaxWidth().height(TEST_HEIGHT_DP.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TestLine(if (enrolled) "AT ARM'S LENGTH — SAY KAWKAB" else "ENROLL YOUR VOICE FIRST")
        TestLine("FRESH TRIES ${attempts.size}/$TEST_TRIES · TARGET 9/10")
        TestLine("WOKE ${attempts.count { it.accepted }}/${attempts.size}")
        attempts.lastOrNull()?.let { result ->
            TestLine("WORD ${scoreText(result.personal)} · OWNER ${scoreText(result.speaker)}")
            TestLine("${result.outcome} · SECOND OPINION ${if (result.sherpa) "YES" else "NO"}")
        }
        if (message.isNotEmpty()) TestLine(message)
        if (enrolled && !busy && attempts.size < TEST_TRIES) {
            DeviceKey("TRY ${attempts.size + 1}", onClick = { record() })
        }
        if (busy) DeviceKey("STOP TEST", onClick = { stop() })
        if (attempts.size == TEST_TRIES) {
            TestLine(
                if (attempts.count { it.accepted } >=
                    TARGET_HITS
                ) {
                    "9/10 TARGET MET"
                } else {
                    "BELOW 9/10 — TRY A QUIETER ROOM"
                },
            )
            DeviceKey("NEW TEST", onClick = {
                attempts = emptyList()
                message = ""
            })
        }
        Spacer(Modifier.weight(1f))
        TestLine("PRIVATE SCORE LOG · ${recent.size} RECENT")
        recent.take(LOG_LINES).forEach { result ->
            TestLine(
                "${if (result.accepted) "+" else "-"} WORD ${scoreText(
                    result.personal,
                )} OWNER ${scoreText(result.speaker)}",
            )
        }
        TestLine("> SCORES ONLY. AUDIO IS NOT SAVED.")
    }
}

@Composable
private fun TestLine(text: String) {
    LcdLine(text, size = 18.sp)
}

private fun scoreText(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

private fun resumeListening(context: Context) = WakeWordService.resume(context)
private const val TEST_TRIES = 10
private const val TARGET_HITS = 9
private const val LOG_LINES = 2

private const val TEST_HEIGHT_DP = 320
