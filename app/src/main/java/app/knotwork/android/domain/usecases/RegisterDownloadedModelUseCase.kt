package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.ModelFileHash
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.services.ModelFileHashScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a freshly-downloaded model file in the local model store.
 *
 * This lives at the *download* end rather than in a ViewModel because a
 * download now outlives the screen that started it: the user can finish
 * onboarding, leave, or lock the phone while the transfer runs. If registration
 * stayed with the observer, a download that survived would leave a
 * multi-gigabyte file on disk that the app knows nothing about — the worst of
 * both worlds.
 *
 * The write is idempotent on the file — found by name, or by path when the
 * name differs (`local_models` has no unique index, so a blind insert would
 * duplicate the row on a re-download) — and never touches the active flag:
 * installing a model is not choosing it. Idempotent under concurrency too: the lookup and the insert run
 * under one lock, because a download finishing while the start-up pass
 * ([RediscoverDownloadedModelsUseCase]) registers the same file would otherwise
 * insert it twice — both would find no row, then both would insert.
 *
 * Every registration asks for a background hashing pass: the file's SHA-256
 * identifies the model a run used. A download that hashed the file to check it
 * hands the hash in, and the pass then finds nothing left to read; a file
 * registered without one (found on disk, or one that could not be read) is
 * hashed by the pass. A re-registered file that changed on disk no longer
 * matches its stored hash, so the same pass covers it.
 *
 * @property localModelRepository The local model registry.
 * @property modelFileHashScheduler Schedules the pass that hashes the file.
 */
@Singleton
class RegisterDownloadedModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val modelFileHashScheduler: ModelFileHashScheduler,
) {

    /** Serialises lookup-then-insert across every caller in the process. */
    private val registration = Mutex()

    /**
     * Registers (or refreshes) the row for [fileName].
     *
     * @param fileName On-disk file name, the identity used for the upsert.
     * @param path Absolute path of the downloaded file.
     * @param sizeBytes Size to record. Callers that know the authoritative size
     *   (the Hub listing) pass it; otherwise the on-disk length is the truth.
     * @param sourceUrl The address the file was downloaded from, or `null` when the caller does not know
     *   it (a file found on disk). A `null` keeps the source an existing row already has.
     * @param sourceSha256 The SHA-256 the source published for the file, when the download matched it,
     *   or `null` when nothing was checked. A `null` keeps the value an existing row already has: whether
     *   it still describes the file is decided against the file's current hash, never by this field alone
     *   (`LocalModel.matchesPublishedHash`).
     * @param fileHash The file's hash with the stamp of the file at [path], when the caller has just read
     *   it, or `null` to leave it to the background pass. A `null` keeps the stored hash, which the pass
     *   replaces once it no longer describes the file.
     * @return The row id of the registered model.
     */
    suspend operator fun invoke(
        fileName: String,
        path: String,
        sizeBytes: Long,
        sourceUrl: String? = null,
        sourceSha256: String? = null,
        fileHash: ModelFileHash? = null,
    ): Long {
        val id = registration.withLock {
            // By name first; then by path, which catches the row the start-up pass
            // registered under the flattened on-disk name of a file this download
            // names by its repository path — the row takes the download's name back.
            val existing = localModelRepository.findByFileName(fileName) ?: localModelRepository.findByPath(path)
            if (existing != null) {
                localModelRepository.updateModel(
                    existing.copy(
                        name = fileName,
                        path = path,
                        size = sizeBytes,
                        sourceUrl = sourceUrl ?: existing.sourceUrl,
                        sourceSha256 = sourceSha256 ?: existing.sourceSha256,
                        fileHash = fileHash ?: existing.fileHash,
                    ),
                )
                existing.id
            } else {
                localModelRepository.insertModel(
                    LocalModel(
                        name = fileName,
                        path = path,
                        size = sizeBytes,
                        isActive = false,
                        fileHash = fileHash,
                        sourceUrl = sourceUrl,
                        sourceSha256 = sourceSha256,
                    ),
                )
            }
        }
        modelFileHashScheduler.schedule()
        return id
    }
}
