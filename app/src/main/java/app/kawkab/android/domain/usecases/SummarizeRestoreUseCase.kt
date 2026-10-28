package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.backup.BackupCounts
import app.kawkab.android.domain.backup.BackupManifest
import app.kawkab.android.domain.backup.BackupModelEntry
import app.kawkab.android.domain.backup.LocalDataCounter
import app.kawkab.android.domain.models.ModelFileStatus
import app.kawkab.android.domain.repositories.LocalModelRepository
import javax.inject.Inject

/**
 * What the *Ready to restore* summary weighs: what this phone holds now, which a restore replaces, and which of
 * the backup's models this phone would have to download again.
 *
 * A model counts as here when this phone has a model of the same file name whose file is on disk — the restore
 * points each model at a file of that name in this phone's downloads.
 *
 * @property counter Counts this phone's data.
 * @property localModels This phone's installed models.
 */
class SummarizeRestoreUseCase @Inject constructor(
    private val counter: LocalDataCounter,
    private val localModels: LocalModelRepository,
) {

    /**
     * Summarises a restore of [manifest] onto this phone.
     *
     * @param manifest What the staged backup holds.
     * @return This phone's counts and the backup's models it lacks.
     */
    suspend operator fun invoke(manifest: BackupManifest): RestoreSummaryData = RestoreSummaryData(
        phoneCounts = counter.count(),
        modelsToDownload = manifest.models.filterNot { isHere(it) },
    )

    private suspend fun isHere(model: BackupModelEntry): Boolean {
        val local = localModels.findByFileName(model.name) ?: return false
        return localModels.fileStatus(local.path) != ModelFileStatus.Missing
    }
}

/**
 * A restore weighed against this phone.
 *
 * @property phoneCounts What this phone holds now — what *Will be lost* lists.
 * @property modelsToDownload The backup's models whose file this phone does not have.
 */
data class RestoreSummaryData(val phoneCounts: BackupCounts, val modelsToDownload: List<BackupModelEntry>)
