package app.knotwork.android.presentation.ui.settings.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.R
import app.knotwork.android.domain.backup.BackupRestorer
import app.knotwork.android.domain.backup.RestoreReport
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.presentation.state.TransientMessageRelay
import app.knotwork.design.screens.settings.backup.AfterRestoreModel
import app.knotwork.design.screens.settings.backup.AfterRestoreState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * What the app tells the user the first time it opens after a restore: a sheet over the chat with the models to
 * download again, the keys to enter again — when the backup did not bring them and none is set on this phone — and
 * the tasks that were not carried; or, when nothing is left to do, the snackbar *Restored*.
 *
 * The report is read once and dismissed on *Done*; until then the sheet shows each time the chat does. A download
 * started here is the download worker's, as on the Models screen: it goes on after the sheet closes, and the
 * worker registers the file. One runs at a time.
 *
 * @property restorer Reads and dismisses the report of the restore applied last.
 * @property downloads Starts, follows and cancels a model's download.
 * @property relay Shows *Restored* in the app's snackbar.
 * @property appContext Reads the snackbar's text.
 * @property formatDate Formats when the backup was made; the device's medium date and short time by default.
 */
@HiltViewModel
class AfterRestoreViewModel internal constructor(
    private val restorer: BackupRestorer,
    private val downloads: ModelDownloadManager,
    private val relay: TransientMessageRelay,
    private val appContext: Context,
    private val formatDate: (Long) -> String,
) : ViewModel() {

    /** Formats dates for the device's locale and time zone. */
    @Inject
    constructor(
        restorer: BackupRestorer,
        downloads: ModelDownloadManager,
        relay: TransientMessageRelay,
        @ApplicationContext appContext: Context,
    ) : this(restorer, downloads, relay, appContext, ::deviceDate)

    private val _state = MutableStateFlow<AfterRestoreState?>(null)

    /** The sheet, or `null` when there is none to show. */
    val state: StateFlow<AfterRestoreState?> = _state.asStateFlow()

    private var report: RestoreReport? = null
    private var downloaded = emptySet<String>()
    private var running: Pair<String, Int>? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    /**
     * *Download* on a model whose source the backup recorded.
     *
     * @param name The model's file name.
     */
    fun onDownload(name: String) {
        val entry = report?.modelsToDownload?.firstOrNull { it.name == name } ?: return
        val url = entry.sourceUrl ?: return
        if (downloadJob?.isActive == true) return
        running = name to 0
        render()
        downloadJob = downloads.downloadModel(url, name)
            .onEach { state ->
                when (state) {
                    DownloadState.Pending -> running = name to 0
                    is DownloadState.Downloading -> running = name to state.progress
                    is DownloadState.Success -> {
                        running = null
                        downloaded = downloaded + name
                    }
                    is DownloadState.Error -> running = null
                }
                render()
            }
            // A download cancelled from its notification ends with no last state: the row is free again.
            .onCompletion {
                if (running?.first == name) {
                    running = null
                    render()
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Cancels the running download of a model.
     *
     * @param name The model's file name.
     */
    fun onCancelDownload(name: String) {
        if (running?.first != name) return
        downloads.cancelDownload(name)
        downloadJob?.cancel()
        downloadJob = null
        running = null
        render()
    }

    /** *Done*, or the sheet dismissed: the report is not shown again. A running download goes on. */
    fun onDone() {
        _state.value = null
        report = null
        viewModelScope.launch { restorer.dismissReport() }
    }

    private suspend fun load() {
        val loaded = try {
            restorer.report()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An unreadable report is not worth the chat: nothing is shown.
            Timber.w(e, "The restore report could not be read")
            null
        } ?: return
        val leftToDo = loaded.modelsToDownload.isNotEmpty() ||
            keysToEnter(loaded) ||
            loaded.scheduledTasksLeftBehind > 0
        if (!leftToDo) {
            relay.post(appContext.getString(R.string.backup_snack_restored))
            restorer.dismissReport()
            return
        }
        report = loaded
        render()
    }

    private fun render() {
        val current = report ?: return
        _state.value = AfterRestoreState(
            madeOnLabel = current.backupCreatedAtEpochMillis?.let(formatDate),
            models = current.modelsToDownload
                .filterNot { it.name in downloaded }
                .map { model ->
                    AfterRestoreModel(
                        name = model.name,
                        sizeBytes = model.sizeBytes,
                        sourceKnown = model.sourceUrl != null,
                        progress = running?.takeIf { it.first == model.name }?.second,
                        canDownload = running == null,
                    )
                },
            enterKeysAgain = keysToEnter(current),
            tasksLeftBehind = current.scheduledTasksLeftBehind > 0,
        )
    }

    private companion object {
        /** Keys are left to enter only when the backup carried none and this phone has none of its own. */
        fun keysToEnter(report: RestoreReport): Boolean = !report.secretsApplied && !report.secretsOnPhone

        fun deviceDate(epochMillis: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
    }
}
