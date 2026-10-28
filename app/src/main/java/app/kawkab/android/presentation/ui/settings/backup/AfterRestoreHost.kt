package app.kawkab.android.presentation.ui.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kawkab.design.screens.settings.backup.AfterRestoreCallbacks
import app.kawkab.design.screens.settings.backup.AfterRestoreSheet

/**
 * The after-restore sheet over the chat, while there is a restore report to show ([AfterRestoreViewModel]). Placed
 * beside the chat screen in its navigation destination, not inside it: the chat screen stays drawable without Hilt.
 *
 * Shown only while the destination is resumed. A back gesture from a screen above the chat (Discover, opened from
 * this sheet) draws the chat underneath while it runs, started but not resumed; a sheet shown then would take the
 * gesture over, and the back would snap to the screen the user was leaving.
 *
 * @param onOpenDiscover Opens Discover, for a model whose source the backup did not record.
 * @param viewModel The report and the downloads started from it.
 */
@Composable
fun AfterRestoreHost(onOpenDiscover: () -> Unit, viewModel: AfterRestoreViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }
    state?.let {
        AfterRestoreSheet(
            state = it,
            callbacks = AfterRestoreCallbacks(
                onDownload = viewModel::onDownload,
                onCancelDownload = viewModel::onCancelDownload,
                onDiscover = onOpenDiscover,
                onDone = viewModel::onDone,
            ),
        )
    }
}
