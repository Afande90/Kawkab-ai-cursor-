package app.knotwork.android.presentation.ui.settings.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.BuildConfig
import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupReadFailure
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.RestoreFailure
import app.knotwork.android.domain.backup.RestoreProgress
import app.knotwork.android.domain.backup.RestoreStep
import app.knotwork.android.domain.backup.StagedRestore
import app.knotwork.android.domain.usecases.InspectBackupDocumentUseCase
import app.knotwork.android.domain.usecases.PickedBackup
import app.knotwork.android.domain.usecases.StageRestoreFromDocumentUseCase
import app.knotwork.android.domain.usecases.SummarizeRestoreUseCase
import app.knotwork.android.presentation.state.AppRestartRequest
import app.knotwork.design.R
import app.knotwork.design.components.dialogs.typedConfirmMatches
import app.knotwork.design.screens.settings.backup.RestoreCheckStep
import app.knotwork.design.screens.settings.backup.RestoreChecking
import app.knotwork.design.screens.settings.backup.RestoreCounts
import app.knotwork.design.screens.settings.backup.RestoreFile
import app.knotwork.design.screens.settings.backup.RestorePassword
import app.knotwork.design.screens.settings.backup.RestoreProblem
import app.knotwork.design.screens.settings.backup.RestoreProblemKind
import app.knotwork.design.screens.settings.backup.RestoreRoute
import app.knotwork.design.screens.settings.backup.RestoreSummary
import app.knotwork.design.screens.settings.backup.Restoring
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * Settings → Privacy → *Backup & restore* → *Restore from file…*: the picked file, its password, the check, the
 * summary, the typed confirm, and the commit after which the app restarts.
 *
 * Nothing on the phone changes before the confirm: the check stages a copy, and *Close* or *Cancel* discards it; a
 * copy left by leaving the screen is removed at the next start, as any uncommitted restore is. The password lives
 * here for the route only — kept for *Try again* after there was not enough space, dropped when the route closes,
 * never saved; the copy handed to the check is wiped after it.
 *
 * @property inspect Reads a picked file's open header, name and size.
 * @property stage Checks and stages the backup with its password.
 * @property summarize Weighs the staged backup against this phone.
 * @property restorer Commits or discards the staged restore.
 * @property restartRequest Asked once the restore is committed; the activity restarts the app.
 * @property appContext Reads the confirm keyword in the user's language.
 * @property formatDate Formats when a backup was made; the device's medium date and short time by default.
 */
@HiltViewModel
class RestoreViewModel internal constructor(
    private val inspect: InspectBackupDocumentUseCase,
    private val stage: StageRestoreFromDocumentUseCase,
    private val summarize: SummarizeRestoreUseCase,
    private val restorer: BackupRestorer,
    private val restartRequest: AppRestartRequest,
    private val appContext: Context,
    private val formatDate: (Long) -> String,
) : ViewModel() {

    /** Formats dates for the device's locale and time zone. */
    @Inject
    constructor(
        inspect: InspectBackupDocumentUseCase,
        stage: StageRestoreFromDocumentUseCase,
        summarize: SummarizeRestoreUseCase,
        restorer: BackupRestorer,
        restartRequest: AppRestartRequest,
        @ApplicationContext appContext: Context,
    ) : this(inspect, stage, summarize, restorer, restartRequest, appContext, ::deviceDate)

    private val _route = MutableStateFlow<RestoreRoute?>(null)

    /** The *Restore* route in front, or `null` when it is closed. */
    val route: StateFlow<RestoreRoute?> = _route.asStateFlow()

    private val _events =
        MutableSharedFlow<RestoreEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One-shot requests to the screen: open the picker. */
    val events: SharedFlow<RestoreEvent> = _events.asSharedFlow()

    private var document: String? = null
    private var file: RestoreFile? = null
    private var password: String = ""
    private var staged: StagedRestore? = null
    private var job: Job? = null

    /** *Restore from file…*, or *Choose another file*: asks the screen to open the system picker. */
    fun startRestore() {
        _events.tryEmit(RestoreEvent.PickDocument)
    }

    /**
     * The picker returned.
     *
     * @param document The picked document's URI, or `null` when the user backed out — whatever was in front stays.
     */
    fun onDocumentPicked(document: String?) {
        if (document == null || job?.isActive == true || _route.value is Restoring) return
        job = viewModelScope.launch { open(document) }
    }

    /** The password field changed: a refusal clears with the edit. */
    fun onPasswordChange(value: String) {
        password = value
        _route.update { (it as? RestorePassword)?.copy(password = value, wrongPassword = false) ?: it }
    }

    /** *Check*, or *Done* on the keyboard: checks and stages the backup. */
    fun onCheck() {
        val form = _route.value as? RestorePassword ?: return
        if (!form.canCheck) return
        check()
    }

    /** *Try again* after there was not enough space: the same check, with the same password. */
    fun onTryAgain() {
        if ((_route.value as? RestoreProblem)?.kind == RestoreProblemKind.NO_SPACE) check()
    }

    /** *Cancel* while checking, or Back: stops the check, discards what it staged, and returns to the password. */
    fun onCancelCheck() {
        val running = job?.takeIf { it.isActive } ?: return
        if (_route.value !is RestoreChecking) return
        job = null
        viewModelScope.launch {
            running.cancelAndJoin()
            if (_route.value !is RestoreChecking) return@launch
            withContext(NonCancellable) { restorer.discard() }
            staged = null
            showPassword()
        }
    }

    /** *Replace my data*: opens the typed confirm. */
    fun onReplace() {
        _route.update { (it as? RestoreSummary)?.copy(confirmInput = "") ?: it }
    }

    /** The confirm dialog's field changed. */
    fun onConfirmInputChange(value: String) {
        _route.update { (it as? RestoreSummary)?.copy(confirmInput = value) ?: it }
    }

    /** The confirm dialog was dismissed: back to the summary. */
    fun onConfirmCancel() {
        _route.update { (it as? RestoreSummary)?.copy(confirmInput = null) ?: it }
    }

    /**
     * *Replace* in the confirm dialog: commits the staged restore — past this point it always completes — and asks
     * the app to restart. The typed word is checked again here, by the dialog's own rule.
     */
    fun onConfirmReplace() {
        val summary = _route.value as? RestoreSummary ?: return
        val keyword = appContext.getString(R.string.knotwork_backup_confirm_keyword)
        if (!typedConfirmMatches(input = summary.confirmInput.orEmpty(), keyword = keyword)) return
        val toCommit = staged ?: return
        _route.value = Restoring
        job = viewModelScope.launch {
            try {
                // In the same block, with no suspension between them: the commit has changed the app, so the
                // restart follows it even when the route's scope ends meanwhile.
                withContext(NonCancellable) {
                    restorer.commit(applySecrets = toCommit.manifest.secretsIncluded)
                    restartRequest.request()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The commit throws only before its marker is written: nothing changed. The staged copy goes.
                Timber.w(e, "A restore could not be committed")
                withContext(NonCancellable) { restorer.discard() }
                staged = null
                _route.value = RestoreProblem(RestoreProblemKind.NOT_STARTED, file)
            }
        }
    }

    /** The route's *Close*: discards a staged copy and drops the password. */
    fun closeRoute() {
        if (_route.value is Restoring) return
        job?.cancel()
        job = null
        val hadStaged = staged != null || _route.value is RestoreChecking
        staged = null
        document = null
        file = null
        password = ""
        _route.value = null
        if (hadStaged) viewModelScope.launch { withContext(NonCancellable) { restorer.discard() } }
    }

    /**
     * Back was pressed.
     *
     * @return `true` when the route handled it; `false` to leave the screen.
     */
    fun onBack(): Boolean = when (val route = _route.value) {
        null -> false
        is RestoreChecking -> {
            onCancelCheck()
            true
        }
        // Committed: there is no going back, and the app restarts in a moment.
        Restoring -> true
        is RestoreSummary -> {
            if (route.confirmInput != null) onConfirmCancel() else closeRoute()
            true
        }
        else -> {
            closeRoute()
            true
        }
    }

    private suspend fun open(document: String) {
        try {
            when (val picked = inspect(document)) {
                is PickedBackup.Readable -> {
                    this.document = document
                    password = ""
                    staged = null
                    file = RestoreFile(
                        name = picked.name,
                        writerVersion = picked.info.appVersionName,
                        createdLabel = formatDate(picked.info.createdAtEpochMillis),
                        sizeBytes = picked.sizeBytes,
                    )
                    showPassword()
                }
                is PickedBackup.Unreadable -> _route.value = problemOf(
                    picked.failure,
                    RestoreFile(name = picked.name, writerVersion = picked.failure.writerVersionName),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The picked file, or its provider, failed: it cannot be read, whatever the reason.
            Timber.w(e, "A picked backup could not be opened")
            _route.value = RestoreProblem(RestoreProblemKind.READ_FAILED)
        }
    }

    private fun check() {
        val target = document ?: return
        val shown = file ?: return
        if (job?.isActive == true) return
        _route.value = RestoreChecking(shown, RestoreCheckStep.PASSWORD)
        val chars = password.toCharArray()
        job = viewModelScope.launch {
            try {
                val result = stage(target, chars) { progress -> show(progress) }
                staged = result
                val summary = try {
                    summarize(result.manifest)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Checked but not weighed: the staged copy goes rather than outlive the route unseen.
                    withContext(NonCancellable) { restorer.discard() }
                    staged = null
                    throw e
                }
                _route.value = RestoreSummary(
                    fromBackup = countsOf(result.manifest.counts),
                    keysIncluded = result.manifest.secretsIncluded,
                    modelsToDownload = summary.modelsToDownload.size,
                    modelsBytes = summary.modelsToDownload.sumOf { it.sizeBytes },
                    onThisPhone = countsOf(summary.phoneCounts),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                _route.value = if (e.failure == BackupReadFailure.WRONG_PASSWORD) {
                    RestorePassword(shown, password, wrongPassword = true)
                } else {
                    problemOf(e, shown)
                }
            } catch (e: BackupRestoreException) {
                _route.value = problemOf(e, shown)
            } catch (e: Exception) {
                // The file or its provider failed mid-check, or this phone's own data could not be counted.
                Timber.w(e, "A backup could not be checked")
                _route.value = RestoreProblem(RestoreProblemKind.READ_FAILED, shown)
            } finally {
                chars.fill(' ')
            }
        }
    }

    private fun show(progress: RestoreProgress) {
        _route.update { current ->
            val checking = current as? RestoreChecking ?: return@update current
            when (progress.step) {
                RestoreStep.KEY -> checking.copy(step = RestoreCheckStep.PASSWORD)
                RestoreStep.UNPACK -> checking.copy(
                    step = RestoreCheckStep.FILE,
                    bytesDone = progress.bytesDone,
                    bytesTotal = progress.bytesTotal,
                )
                // The database is checked after the last byte: the bar stays full.
                RestoreStep.DATABASE -> checking.copy(step = RestoreCheckStep.FILE)
            }
        }
    }

    private fun showPassword() {
        val shown = file ?: return
        _route.value = RestorePassword(shown, password)
    }

    private fun problemOf(e: BackupFormatException, shown: RestoreFile): RestoreProblem = when (e.failure) {
        BackupReadFailure.NOT_A_BACKUP -> RestoreProblem(RestoreProblemKind.NOT_A_BACKUP, shown)
        BackupReadFailure.UNSUPPORTED_FORMAT_VERSION -> RestoreProblem(
            kind = RestoreProblemKind.NEWER,
            file = shown.copy(writerVersion = e.writerVersionName ?: shown.writerVersion),
            phoneVersion = BuildConfig.VERSION_NAME,
        )
        BackupReadFailure.TOO_DEMANDING, BackupReadFailure.DAMAGED -> RestoreProblem(RestoreProblemKind.DAMAGED, shown)
        BackupReadFailure.TRUNCATED -> RestoreProblem(RestoreProblemKind.INCOMPLETE, shown)
        BackupReadFailure.WRONG_PASSWORD -> RestoreProblem(RestoreProblemKind.DAMAGED, shown)
    }

    private fun problemOf(e: BackupRestoreException, shown: RestoreFile): RestoreProblem = when (e.failure) {
        RestoreFailure.NEWER_VERSION -> RestoreProblem(
            kind = RestoreProblemKind.NEWER,
            file = shown.copy(writerVersion = e.writerVersionName ?: shown.writerVersion),
            phoneVersion = BuildConfig.VERSION_NAME,
        )
        RestoreFailure.NO_SPACE ->
            RestoreProblem(RestoreProblemKind.NO_SPACE, shown, neededBytes = e.neededBytes, freeBytes = e.freeBytes)
        RestoreFailure.DATABASE_UNUSABLE -> RestoreProblem(RestoreProblemKind.DAMAGED, shown)
        RestoreFailure.PHONE_KEY_UNAVAILABLE -> RestoreProblem(RestoreProblemKind.PHONE_KEY_UNAVAILABLE, shown)
        RestoreFailure.READ_FAILED -> RestoreProblem(RestoreProblemKind.READ_FAILED, shown)
    }

    private companion object {
        fun countsOf(counts: BackupCounts) = RestoreCounts(
            chats = counts.chats,
            memories = counts.memories,
            pipelines = counts.pipelines,
            files = counts.workspaceFiles + counts.attachments,
        )

        fun deviceDate(epochMillis: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
    }
}

/** What the *Restore* route asks the screen to do once. */
sealed interface RestoreEvent {
    /** Open the system picker to choose a backup. */
    data object PickDocument : RestoreEvent
}
