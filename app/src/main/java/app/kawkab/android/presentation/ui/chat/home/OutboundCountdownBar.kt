package app.kawkab.android.presentation.ui.chat.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kawkab.android.R
import app.kawkab.android.domain.text.ReplyLanguage
import app.kawkab.android.domain.voice.VoiceAnswers
import app.kawkab.android.presentation.ui.common.resolve
import app.kawkab.android.voice.OfflineSpeech
import app.kawkab.android.voice.OfflineVoice
import kotlinx.coroutines.delay

/**
 * An announced call or message (vision rule 1): "Calling Abdalla Mubadala · 3" with STOP. It runs by
 * itself when the count ends; STOP, or saying "stop", cancels it.
 *
 * @param pending The call waiting out its window.
 * @param onStop Cancels it.
 */
@Composable
fun OutboundCountdownBar(pending: HitlPending, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runsAt = pending.runsAtMs ?: return
    var now by remember(pending.requestId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending.requestId) {
        while (now < runsAt) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val seconds = ((runsAt - now + MS_PER_SECOND - 1) / MS_PER_SECOND).coerceAtLeast(0)
    val sentence = context.resolve(
        ActionSentence.announcement(pending.toolName, ActionSentence.arguments(pending.arguments)),
    )
    CountdownSpeech(pending.requestId, sentence, runsAt, onStop)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color(ORANGE))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(COUNTDOWN_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.countdown_line, sentence, seconds),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onStop, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Text(stringResource(R.string.countdown_stop), color = Color.White)
        }
    }
}

@Composable
private fun CountdownSpeech(requestId: String, sentence: String, runsAt: Long, onStop: () -> Unit) {
    val context = LocalContext.current
    val stop by rememberUpdatedState(onStop)
    DisposableEffect(requestId) {
        val voice = OfflineVoice(context)
        val speech = OfflineSpeech(context)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var active = true
        val listener = object : OfflineSpeech.Listener {
            private fun cancel(text: String) {
                if (active && VoiceAnswers.answer(text) == VoiceAnswers.Answer.NO) {
                    active = false
                    voice.stop()
                    speech.stop()
                    stop()
                }
            }

            override fun onText(text: String) {
                cancel(text)
                onNothing(0)
            }

            override fun onPartialText(text: String) = cancel(text)

            override fun onNothing(code: Int) {
                if (active && System.currentTimeMillis() < runsAt) {
                    main.postDelayed({
                        if (active) runCatching {
                            speech.listen(OfflineSpeech.languageFor(java.util.Locale.getDefault()), this)
                        }
                    }, RETRY_MS)
                }
            }
        }
        voice.speak(sentence, ReplyLanguage.of(sentence)) { }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { speech.listen(OfflineSpeech.languageFor(java.util.Locale.getDefault()), listener) }
        }
        onDispose {
            active = false
            main.removeCallbacksAndMessages(null)
            speech.stop()
            voice.shutdown()
        }
    }
}

private const val ORANGE: Long = 0xFFE2611B
private const val TICK_MS = 100L
private const val MS_PER_SECOND = 1_000L

/** Test tag of the countdown bar. */
const val COUNTDOWN_TAG: String = "outbound_countdown"

private const val RETRY_MS = 200L
