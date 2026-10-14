package app.knotwork.android.presentation.ui.settings.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.android.R
import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.design.components.misc.KnotworkSnackbarHost
import app.knotwork.design.screens.settings.backup.BackupCallbacks
import app.knotwork.design.screens.settings.backup.BackupContent

/**
 * Settings → Privacy → *Backup & restore*: the screen and its *Back up* route, over [BackupViewModel].
 *
 * Owns the system *Create document* picker (`application/octet-stream`, under the name the view model suggests)
 * and the snackbar a cancelled backup leaves. Back inside a route closes it — or cancels the work.
 *
 * @param onBack Leaves the screen.
 * @param viewModel The screen's state.
 */
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancelNotice by viewModel.cancelNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)) {
        viewModel.onDocumentPicked(it?.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.PickDocument -> picker.launch(event.suggestedName)
            }
        }
    }
    // Resolved in composition: the snackbar names the file the user has to delete when the provider refused. The
    // notice is cleared only once shown, so a rotation in between shows it again rather than never.
    cancelNotice?.let { notice ->
        val text = if (notice.fileDeleted) {
            stringResource(R.string.backup_snack_cancelled)
        } else {
            stringResource(R.string.backup_snack_cancelled_left, notice.fileName)
        }
        LaunchedEffect(notice) {
            snackbarHostState.showSnackbar(text)
            viewModel.onCancelNoticeShown()
        }
    }
    BackHandler(enabled = state.route != null) { viewModel.onBack() }

    BackupContent(
        state = state,
        callbacks = BackupCallbacks(
            onBack = onBack,
            onStartBackup = viewModel::startBackup,
            onPasswordChange = viewModel::onPasswordChange,
            onPasswordFocusLost = viewModel::onPasswordFocusLost,
            onConfirmationChange = viewModel::onConfirmationChange,
            onConfirmationFocusLost = viewModel::onConfirmationFocusLost,
            onToggleSecrets = viewModel::onToggleSecrets,
            onChooseWhere = viewModel::onChooseWhere,
            onCancel = viewModel::onCancel,
            onDone = viewModel::closeRoute,
            onTryAgain = viewModel::onTryAgain,
            onCloseRoute = viewModel::closeRoute,
        ),
        snackbarHost = { KnotworkSnackbarHost(hostState = snackbarHostState) },
    )
}
