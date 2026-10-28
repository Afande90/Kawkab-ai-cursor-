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
import app.knotwork.design.screens.settings.backup.RestoreCallbacks

/**
 * Settings → Privacy → *Backup & restore*: the screen, its *Back up* route over [BackupViewModel] and its *Restore*
 * route over [RestoreViewModel]. Only one route is open at a time; the restore's is the one in front when open.
 *
 * Owns the system pickers — *Create document* (`application/octet-stream`, under the name the backup suggests)
 * and *Open document* (any type: the file's header decides) — and the snackbar a cancelled backup leaves. The
 * restart after a committed restore is the activity's, not this screen's: the screen may be left while the commit
 * finishes. Back inside a route closes it — or cancels the work.
 *
 * @param onBack Leaves the screen.
 * @param viewModel The *Back up* route.
 * @param restoreViewModel The *Restore* route.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
    restoreViewModel: RestoreViewModel = hiltViewModel(),
) {
    val backupState by viewModel.state.collectAsStateWithLifecycle()
    val restoreRoute by restoreViewModel.route.collectAsStateWithLifecycle()
    val cancelNotice by viewModel.cancelNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val createPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),
    ) { viewModel.onDocumentPicked(it?.toString()) }
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        restoreViewModel.onDocumentPicked(it?.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.PickDocument -> createPicker.launch(event.suggestedName)
            }
        }
    }
    LaunchedEffect(restoreViewModel) {
        restoreViewModel.events.collect { event ->
            when (event) {
                RestoreEvent.PickDocument -> openPicker.launch(arrayOf(ANY_TYPE))
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
    val restoring = restoreRoute != null
    val state = backupState.copy(route = restoreRoute ?: backupState.route, restoreAvailable = true)
    BackHandler(enabled = state.route != null) {
        if (restoring) restoreViewModel.onBack() else viewModel.onBack()
    }

    BackupContent(
        state = state,
        callbacks = BackupCallbacks(
            onBack = onBack,
            onStartBackup = viewModel::startBackup,
            onStartRestore = restoreViewModel::startRestore,
            onPasswordChange = viewModel::onPasswordChange,
            onPasswordFocusLost = viewModel::onPasswordFocusLost,
            onConfirmationChange = viewModel::onConfirmationChange,
            onConfirmationFocusLost = viewModel::onConfirmationFocusLost,
            onToggleSecrets = viewModel::onToggleSecrets,
            onChooseWhere = viewModel::onChooseWhere,
            onCancel = viewModel::onCancel,
            onDone = viewModel::closeRoute,
            onTryAgain = viewModel::onTryAgain,
            onCloseRoute = { if (restoring) restoreViewModel.closeRoute() else viewModel.closeRoute() },
            restore = RestoreCallbacks(
                onChooseAnother = restoreViewModel::startRestore,
                onPasswordChange = restoreViewModel::onPasswordChange,
                onCheck = restoreViewModel::onCheck,
                onTryAgain = restoreViewModel::onTryAgain,
                onCancelCheck = restoreViewModel::onCancelCheck,
                onReplace = restoreViewModel::onReplace,
                onConfirmInputChange = restoreViewModel::onConfirmInputChange,
                onConfirmReplace = restoreViewModel::onConfirmReplace,
                onConfirmCancel = restoreViewModel::onConfirmCancel,
            ),
        ),
        snackbarHost = { KnotworkSnackbarHost(hostState = snackbarHostState) },
    )
}

/** Any type: a backup's provider may name or type it differently; its header decides. */
private const val ANY_TYPE = "*/*"
