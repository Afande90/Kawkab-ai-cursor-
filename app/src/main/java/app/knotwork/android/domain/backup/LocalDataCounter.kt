package app.knotwork.android.domain.backup

/**
 * Counts what this phone holds now, in the terms a backup's manifest uses — what a restore would replace.
 */
interface LocalDataCounter {

    /**
     * Counts this phone's chats, memories, pipelines, workspace files and image attachments.
     *
     * @return The counts, as [BackupManifest.counts] would record them for a backup taken now.
     */
    suspend fun count(): BackupCounts
}
