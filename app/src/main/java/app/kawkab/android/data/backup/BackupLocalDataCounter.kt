package app.kawkab.android.data.backup

import app.kawkab.android.di.IoDispatcher
import app.kawkab.android.domain.backup.BackupCounts
import app.kawkab.android.domain.backup.LocalDataCounter
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.domain.repositories.MemoryRepository
import app.kawkab.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [LocalDataCounter] over the same sources an export reads, so a restore's *Will be lost* counts what a backup
 * taken now would hold.
 *
 * @property chatRepository Counts the chats.
 * @property memoryRepository Counts the memories.
 * @property pipelineRepository Counts the pipelines.
 * @property files Lists the workspace's files and the image attachments.
 * @property ioDispatcher Runs the counting: the file listings walk directories.
 */
internal class BackupLocalDataCounter @Inject constructor(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val pipelineRepository: PipelineRepository,
    private val files: BackupFileSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LocalDataCounter {

    override suspend fun count(): BackupCounts = withContext(ioDispatcher) {
        BackupCounts(
            chats = chatRepository.getAllSessions().size,
            memories = memoryRepository.countMemories(),
            pipelines = pipelineRepository.getAllPipelines().first().size,
            workspaceFiles = files.workspace().entries.size,
            attachments = files.attachments().entries.size,
        )
    }
}
