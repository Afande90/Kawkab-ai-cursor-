package app.knotwork.android.presentation.ui.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.backup.BackupDocuments
import app.knotwork.android.domain.backup.BackupExportFailure
import app.knotwork.android.domain.backup.BackupProgress
import app.knotwork.android.domain.backup.BackupStep
import app.knotwork.android.domain.usecases.BackUpToDocumentUseCase
import app.knotwork.android.domain.usecases.BackupToDocumentOutcome
import app.knotwork.android.domain.usecases.ExportBackupUseCase
import app.knotwork.design.screens.settings.backup.BackupFailReason
import app.knotwork.design.screens.settings.backup.BackupFailed
import app.knotwork.design.screens.settings.backup.BackupForm
import app.knotwork.design.screens.settings.backup.BackupRoute
import app.knotwork.design.screens.settings.backup.BackupSaved
import app.knotwork.design.screens.settings.backup.BackupViewState
import app.knotwork.design.screens.settings.backup.BackupWorkStep
import app.knotwork.design.screens.settings.backup.BackupWorking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Settings → Privacy → *Backup & restore*, and its *Back up* route.
 *
 * The password lives here for the route only: kept across the system picker and *Try again*, dropped on
 * *Done*, *Close* and when the screen goes (the process dying drops it too — it is never saved). The checkbox
 * for secrets starts off every time.
 *
 * Field errors follow the designer's rule: *Too short* once the first field has lost focus holding 1–11
 * characters, then live until it reaches 12; *Doesn't match* once the second field has lost focus, or as soon as
 * it is as long as the first and differs, clearing live. An empty field shows no error.
 *
 * @property backUpToDocument Writes the backup into the picked document.
 * @property documents Names and deletes the picked document.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backUpToDocument: BackUpToDocumentUseCase,
    private val documents: BackupDocuments,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupViewState())

    /** What the screen shows. */
    val state: StateFlow<BackupViewState> = _state.asStateFlow()

    private val _events =
        MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One-shot requests to the screen: open the picker, show a snackbar. */
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    private var fields = Fields()
    private var job: Job? = null
    private var document: String? = null
    private var documentName: String? = null

    /** Opens *Back up* with empty fields. */
    fun startBackup() {
        fields = Fields()
        showForm()
    }

    /** The first password field changed. */
    fun onPasswordChange(value: String) {
        fields = fields.copy(password = value)
        showForm()
    }

    /** The first password field lost focus: a short password is flagged from now on. */
    fun onPasswordFocusLost() {
        if (codePoints(fields.password) in 1 until ExportBackupUseCase.MIN_PASSWORD_LENGTH) {
            fields = fields.copy(passwordBlurred = true)
            showForm()
        }
    }

    /** The second password field changed. */
    fun onConfirmationChange(value: String) {
        fields = fields.copy(confirmation = value)
        showForm()
    }

    /** The second password field lost focus: a mismatch is flagged from now on. */
    fun onConfirmationFocusLost() {
        if (fields.confirmation.isNotEmpty()) {
            fields = fields.copy(confirmationArmed = true)
            showForm()
        }
    }

    /** The secrets checkbox was toggled. */
    fun onToggleSecrets() {
        fields = fields.copy(includeSecrets = !fields.includeSecrets)
        showForm()
    }

    /** *Choose where to save*: asks the screen to open the system picker. */
    fun onChooseWhere() {
        if (formOf(fields).canContinue) _events.tryEmit(BackupEvent.PickDocument)
    }

    /**
     * The picker returned.
     *
     * @param document The picked document's URI, or `null` when the user backed out — the form stays as it was.
     */
    fun onDocumentPicked(document: String?) {
        if (document == null || job?.isActive == true) {
            showForm()
            return
        }
        this.document = document
        job = viewModelScope.launch { backUp(document) }
    }

    /** *Cancel* while working, or Back: stops the backup, deletes the unfinished file and says whether it did. */
    fun onCancel() {
        val running = job ?: return
        val target = document ?: return
        viewModelScope.launch {
            running.cancel()
            running.join()
            val name = documentName
            // The backup deleted it on its way out; asking again says whether that worked.
            val deleted = documents.discard(target)
            closeRoute()
            _events.tryEmit(BackupEvent.Cancelled(fileDeleted = deleted, fileName = name))
        }
    }

    /** *Try again* after a failure: the picker again, the password kept. */
    fun onTryAgain() {
        showForm()
        _events.tryEmit(BackupEvent.PickDocument)
    }

    /** *Done* or *Close*: back to the screen, the password dropped. */
    fun closeRoute() {
        fields = Fields()
        document = null
        documentName = null
        _state.update { it.copy(route = null) }
    }

    /**
     * Back was pressed.
     *
     * @return `true` when a route handled it; `false` to leave the screen.
     */
    fun onBack(): Boolean = when (_state.value.route) {
        null -> false
        is BackupWorking -> {
            onCancel()
            true
        }
        else -> {
            closeRoute()
            true
        }
    }

    private suspend fun backUp(target: String) {
        val name = documents.displayName(target)
        documentName = name
        _state.update { it.copy(route = BackupWorking(fileName = name, step = BackupWorkStep.PASSWORD)) }
        val password = fields.password.toCharArray()
        try {
            val outcome = backUpToDocument(target, password, fields.includeSecrets) { progress -> show(progress, name) }
            _state.update { it.copy(route = routeOf(outcome, name)) }
            if (outcome is BackupToDocumentOutcome.Saved) fields = Fields()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "A backup failed in an unexpected way")
            _state.update {
                it.copy(
                    route = BackupFailed(
                        reason = BackupFailReason.READ_FAILED,
                        fileDeleted = documents.discard(target),
                        fileName = name,
                    ),
                )
            }
        } finally {
            password.fill(' ')
        }
    }

    private fun show(progress: BackupProgress, name: String?) {
        _state.update { current ->
            if (current.route !is BackupWorking) return@update current
            current.copy(
                route = BackupWorking(
                    fileName = name,
                    step = stepOf(progress.step),
                    bytesDone = progress.bytesDone,
                    bytesTotal = progress.bytesTotal,
                ),
            )
        }
    }

    private fun showForm() {
        _state.update { it.copy(route = formOf(fields)) }
    }

    /** What the password form holds, and what of it has been flagged. */
    private data class Fields(
        val password: String = "",
        val confirmation: String = "",
        val passwordBlurred: Boolean = false,
        val confirmationArmed: Boolean = false,
        val includeSecrets: Boolean = false,
    )

    private companion object {
        fun formOf(fields: Fields): BackupForm {
            val length = codePoints(fields.password)
            val enough = length >= ExportBackupUseCase.MIN_PASSWORD_LENGTH
            val differs = fields.confirmation.isNotEmpty() && fields.confirmation != fields.password
            return BackupForm(
                password = fields.password,
                confirmation = fields.confirmation,
                passwordTooShort = fields.passwordBlurred && length in 1 until ExportBackupUseCase.MIN_PASSWORD_LENGTH,
                confirmationMismatch = differs &&
                    (fields.confirmationArmed || codePoints(fields.confirmation) >= length),
                includeSecrets = fields.includeSecrets,
                canContinue = enough && fields.confirmation == fields.password,
            )
        }

        fun codePoints(text: String): Int = text.codePointCount(0, text.length)

        fun stepOf(step: BackupStep): BackupWorkStep = when (step) {
            BackupStep.KEY -> BackupWorkStep.PASSWORD
            BackupStep.DATABASE -> BackupWorkStep.DATABASE
            BackupStep.FILES -> BackupWorkStep.FILES
        }

        fun routeOf(outcome: BackupToDocumentOutcome, name: String?): BackupRoute = when (outcome) {
            is BackupToDocumentOutcome.Saved -> BackupSaved(
                fileName = name,
                sizeBytes = outcome.sizeBytes,
                chats = outcome.manifest.counts.chats,
                memories = outcome.manifest.counts.memories,
                pipelines = outcome.manifest.counts.pipelines,
                files = outcome.manifest.counts.workspaceFiles + outcome.manifest.counts.attachments,
                keysIncluded = outcome.manifest.secretsIncluded,
                skippedFiles = outcome.manifest.skippedFiles,
            )
            is BackupToDocumentOutcome.Failed -> BackupFailed(
                reason = when (outcome.failure) {
                    BackupExportFailure.NO_SPACE -> BackupFailReason.NO_SPACE
                    BackupExportFailure.WRITE_FAILED -> BackupFailReason.WRITE_FAILED
                    BackupExportFailure.PHONE_FULL -> BackupFailReason.PHONE_FULL
                    BackupExportFailure.READ_FAILED -> BackupFailReason.READ_FAILED
                },
                neededBytes = outcome.neededBytes,
                fileDeleted = outcome.documentDeleted,
                fileName = name,
            )
        }
    }
}

/** What the *Backup & restore* screen is asked to do once. */
sealed interface BackupEvent {
    /** Open the system picker to create the backup file. */
    data object PickDocument : BackupEvent

    /**
     * The backup was cancelled.
     *
     * @property fileDeleted Whether the unfinished file was deleted.
     * @property fileName Its name, for when the user has to delete it.
     */
    data class Cancelled(val fileDeleted: Boolean, val fileName: String?) : BackupEvent
}
