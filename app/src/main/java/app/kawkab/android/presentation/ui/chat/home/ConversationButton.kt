package app.kawkab.android.presentation.ui.chat.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.kawkab.android.R
import app.kawkab.android.domain.voice.ConversationLoop
import app.kawkab.android.voice.ConversationDriver
import app.kawkab.android.voice.OfflineSpeech
import app.kawkab.android.voice.OfflineVoice
import app.kawkab.design.components.chat.ChatContent
import app.kawkab.design.components.chat.ChatRole
import app.kawkab.design.screens.chat.ChatHomeViewState
import app.kawkab.design.screens.chat.ChatHomeVisualState

/**
 * Conversation mode (task 23l): tap once to talk; Kawkab listens offline, sends what was said,
 * speaks the reply with an offline voice, and listens again. Tap again to stop. A tap card
 * stops the loop: voice never confirms an outbound action.
 *
 * @param viewState The chat, read for replies and cards.
 * @param send Sends a transcript as the owner's message.
 */
@Composable
fun ConversationButton(viewState: ChatHomeViewState, send: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(ConversationLoop.Phase.IDLE) }
    var driver by remember { mutableStateOf<ConversationDriver?>(null) }
    fun driver(): ConversationDriver = driver ?: ConversationDriver(
        speech = OfflineSpeech(context),
        voice = OfflineVoice(context),
        send = send,
        onPhase = { phase = it },
    ).also { driver = it }
    DisposableEffect(Unit) { onDispose { driver?.release() } }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) driver().tap()
    }
    // A finished turn hands its reply to the loop; a card stops it.
    LaunchedEffect(viewState.visualState, viewState.messages.lastOrNull()?.id) {
        val current = driver ?: return@LaunchedEffect
        if (current.phase != ConversationLoop.Phase.WAITING) return@LaunchedEffect
        when (viewState.visualState) {
            ChatHomeVisualState.HitlConfirm, ChatHomeVisualState.Clarification -> current.waitsForTap()
            ChatHomeVisualState.Generating, ChatHomeVisualState.Loading -> Unit
            else -> current.replied(lastReply(viewState))
        }
    }
    AssistChip(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted ||
                phase != ConversationLoop.Phase.IDLE
            ) {
                driver().tap()
            } else {
                mic.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        label = {
            Text(
                stringResource(
                    when (phase) {
                        ConversationLoop.Phase.IDLE -> R.string.conversation_talk
                        ConversationLoop.Phase.LISTENING -> R.string.conversation_listening
                        ConversationLoop.Phase.WAITING -> R.string.conversation_thinking
                        ConversationLoop.Phase.SPEAKING -> R.string.conversation_speaking
                    },
                ),
            )
        },
        modifier = modifier.testTag(CONVERSATION_TAG),
    )
}

private fun lastReply(viewState: ChatHomeViewState): String {
    val row = viewState.messages.lastOrNull { it.role == ChatRole.Assistant } ?: return ""
    return when (val content = row.content) {
        is ChatContent.Markdown -> content.source
        is ChatContent.Text -> content.text
        else -> ""
    }
}

/** Test tag of the talk button. */
const val CONVERSATION_TAG = "conversation_mode"
