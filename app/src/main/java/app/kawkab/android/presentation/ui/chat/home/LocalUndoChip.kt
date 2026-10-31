package app.kawkab.android.presentation.ui.chat.home

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.kawkab.android.R
import app.kawkab.android.hands.LocalUndo
import kotlinx.coroutines.delay

/**
 * The Undo chip shown for 10 seconds after a local action (task 23i).
 *
 * @param undo The undo store.
 * @param onUndone Receives the result sentence after a tap.
 */
@Composable
fun LocalUndoChip(undo: LocalUndo, onUndone: (String) -> Unit, modifier: Modifier = Modifier) {
    val offer by undo.state.collectAsState()
    val current = offer ?: return
    LaunchedEffect(current) {
        delay(LocalUndo.WINDOW_MS)
        undo.expire(current)
    }
    AssistChip(
        onClick = { undo.undo(System.currentTimeMillis())?.let(onUndone) },
        label = { Text(stringResource(R.string.local_undo)) },
        modifier = modifier.testTag(LOCAL_UNDO_TAG),
    )
}

/** Test tag of the Undo chip. */
const val LOCAL_UNDO_TAG = "local_undo"
