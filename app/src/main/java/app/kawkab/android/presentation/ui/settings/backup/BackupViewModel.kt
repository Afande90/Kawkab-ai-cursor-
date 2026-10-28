package app.kawkab.android.presentation.ui.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kawkab.android.domain.backup.BackupDocuments
import app.kawkab.android.domain.backup.BackupExportFailure
import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupProgress
import app.kawkab.android.domain.backup.BackupStep
import app.kawkab.android.domain.usecases.BackUpToDocumentUseCase
import app.kawkab.android.domain.usecases.BackupToDocumentOutcome
import app.kawkab.android.domain.usecases.ExportBackupUseCase
import app.kawkab.design.screens.settings.backup.BackupFailReason
import app.kawkab.design.screens.settings.backup.BackupFailed
import app.kawkab.design.screens.settings.backup.BackupForm
import app.kawkab.design.screens.settings.backup.BackupRoute
import app.kawkab.design.screens.settings.backup.BackupSaved
import app.kawkab.design.screens.settings.backup.BackupViewState
import app.kawkab.design.screens.settings.backup.BackupWorkStep
import app.kawkab.design.screens.settings.backup.BackupWorking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import java.time.LocalDate
import javax.inject.Inject

/**
 * Settings → Privacy → *Backup & restore*, and its *Back up* route.
 *
 * The password lives here for the route only: kept across the system picker and *Try again*, dropped on
 * *Done*, *Close*, a saved backup and when the screen goes (the process dying drops it too — it is never saved).
 * It is read when the picker returns, so nothing done on the screen afterwards changes what the backup is locked
 * with. The checkbox for secrets starts off every time.
 *
 * Field errors follow the designer's rule: *Too short* once the first field has lost focus holding 1–11
 * characters, then live until it reaches 12; *Doesn't match* once the second field has lost focus, or as soon as
 * it is as long as the first and differs, clearing live. An empty field shows no error.
 *
 * A document the picker returns when no backup can start with it — a second one while a backup runs or is being
 * cancelled, or one that comes back after the process was recreated and the password went with it — is deleted
 * only if it is empty, as the picker creates a new one. One holding bytes is an existing file the user chose to
 * overwrite, and stays; the document the running backup writes is never touched.
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

    /** One-shot requests to the screen: open the picker. */
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    private val _cancelNotice = MutableStateFlow<BackupCancelNotice?>(null)

    /**
     * What a cancelled backup left, until the screen has shown it ([onCancelNoticeShown]). Held as state, not
     * sent as an event: it can be the only word that a file was left behind, and a rotation must not lose it.
     */
    val cancelNotice: StateFlow<BackupCancelNotice?> = _cancelNotice.asStateFlow()

    private var fields = Fields()
    private var job: Job? = null
    private var document: String? = null
    private var documentName: String? = null
    private var suggestedName: String = suggestedFileName()

    /** Whether the backup, cancelled or broken, deleted its document on the way out; `null` before it says. */
    private var deletedOnTheWayOut: Boolean? = null

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
        if (formOf(fields).canContinue) requestDocument()
    }

    /**
     * The picker returned.
     *
     * @param document The picked document's URI, or `null` when the user backed out — the form stays as it was, and
     *   a backup already running keeps its screen.
     */
    fun onDocumentPicked(document: String?) {
        // A backup running or being cancelled keeps its screen, whatever a second picker returns.
        val busy = job?.isActive == true || _state.value.route is BackupWorking
        if (document == null) {
            if (!busy) showForm()
            return
        }
        if (busy || !formOf(fields).canContinue) {
            if (document != this.document) viewModelScope.launch { documents.discardIfEmpty(document) }
            return
        }
        this.document = document
        documentName = null
        deletedOnTheWayOut = null
        val password = fields.password.toCharArray()
        val includeSecrets = fields.includeSecrets
        // The form goes at once: from here Back and Close mean Cancel.
        _state.update { it.copy(route = BackupWorking(fileName = suggestedName, step = BackupWorkStep.PASSWORD)) }
        job = viewModelScope.launch { backUp(document, password, includeSecrets) }
    }

    /**
     * *Cancel* while working, or Back: stops the backup, deletes the unfinished file and leaves a notice saying
     * whether it did. Once only; and a backup that finished before the cancel reached it keeps its result.
     */
    fun onCancel() {
        val running = job?.takeIf { it.isActive } ?: return
        val target = document ?: return
        job = null
        viewModelScope.launch {
            running.cancelAndJoin()
            // The result reached the screen first: the backup is not unfinished, and stays.
            if (_state.value.route !is BackupWorking) return@launch
            // The backup deleted it on its way out and said how that went; one cancelled before it reached the
            // document left that to us.
            val deleted = deletedOnTheWayOut ?: documents.discard(target)
            val name = documentName ?: suggestedName
            closeRoute()
            _cancelNotice.value = BackupCancelNotice(fileDeleted = deleted, fileName = name)
        }
    }

    /** The screen showed [cancelNotice]. */
    fun onCancelNoticeShown() {
        _cancelNotice.value = null
    }

    /** *Try again* after a failure: the picker again, the password kept. */
    fun onTryAgain() {
        showForm()
        requestDocument()
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

    private fun requestDocument() {
        suggestedName = suggestedFileName()
        _events.tryEmit(BackupEvent.PickDocument(suggestedName))
    }

    private suspend fun backUp(target: String, password: CharArray, includeSecrets: Boolean) {
        try {
            // The provider may have renamed it; without a name from it, the one suggested is the best guess.
            val name = documents.displayName(target) ?: suggestedName
            documentName = name
            _state.update { current ->
                val working = current.route as? BackupWorking ?: return@update current
                current.copy(route = working.copy(fileName = name))
            }
            val outcome = backUpToDocument(
                target,
                password,
                includeSecrets,
                onUnfinishedDeleted = { deletedOnTheWayOut = it },
            ) { progress -> show(progress, name) }
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
                        fileDeleted = deletedOnTheWayOut ?: documents.discard(target),
                        fileName = documentName ?: suggestedName,
                    ),
                )
            }
        } finally {
            password.fill(' ')
        }
    }

    private fun show(progress: BackupProgress, name: String) {
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

        /** The name offered in the picker: `knotwork-backup-<date>.knotwork`. */
        fun suggestedFileName(): String = "knotwork-backup-${LocalDate.now()}.${BackupFormat.FILE_EXTENSION}"

        fun stepOf(step: BackupStep): BackupWorkStep = when (step) {
            BackupStep.KEY -> BackupWorkStep.PASSWORD
            BackupStep.DATABASE -> BackupWorkStep.DATABASE
            BackupStep.FILES -> BackupWorkStep.FILES
        }

        fun routeOf(outcome: BackupToDocumentOutcome, name: String): BackupRoute = when (outcome) {
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
    /**
     * Open the system picker to create the backup file.
     *
     * @property suggestedName The name the picker offers.
     */
    data class PickDocument(val suggestedName: String) : BackupEvent
}

/**
 * A cancelled backup, for the snackbar.
 *
 * @property fileDeleted Whether the unfinished file was deleted.
 * @property fileName Its name, for when the user has to delete it.
 */
data class BackupCancelNotice(val fileDeleted: Boolean, val fileName: String)
