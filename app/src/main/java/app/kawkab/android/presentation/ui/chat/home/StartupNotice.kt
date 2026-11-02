package app.kawkab.android.presentation.ui.chat.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kawkab.android.R
import app.kawkab.android.crash.CrashLog
import app.kawkab.android.voice.WakeWordSwitch

/**
 * Says when something did not start (task 32): a safe-mode start after a crash, or a wake word that
 * failed and was turned off. Shown on the home screen; nothing is ever silent.
 */
@Composable
fun StartupNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text = when {
        CrashLog.safeMode -> stringResource(R.string.startup_safe_mode)
        else -> WakeWordSwitch.failure(context)?.let { stringResource(R.string.startup_wake_failed, it) }
    } ?: return
    Text(
        text = text,
        color = Color(TEXT),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(AMBER))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(STARTUP_NOTICE_TAG),
    )
}

private const val AMBER: Long = 0xFFF2B45A
private const val TEXT: Long = 0xFF070A14

/** Test tag of the notice. */
const val STARTUP_NOTICE_TAG: String = "startup_notice"
