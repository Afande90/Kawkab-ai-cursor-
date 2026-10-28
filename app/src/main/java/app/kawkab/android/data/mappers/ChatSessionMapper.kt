package app.kawkab.android.data.mappers

import app.kawkab.android.data.local.models.ChatSessionEntity
import app.kawkab.android.domain.models.ChatSession

/**
 * Maps a [ChatSessionEntity] from the data layer to a [ChatSession] domain model.
 *
 * @return The mapped [ChatSession] instance.
 */
fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    id = id,
    name = name,
    updatedAt = updatedAt,
    pipelineId = pipelineId,
    isStarred = isStarred,
    isArchived = isArchived,
    archivedAt = archivedAt,
)

/**
 * Maps a [ChatSession] domain model to a [ChatSessionEntity] for the data layer.
 *
 * @return The mapped [ChatSessionEntity] instance.
 */
fun ChatSession.toEntity(): ChatSessionEntity = ChatSessionEntity(
    id = id,
    name = name,
    updatedAt = updatedAt,
    pipelineId = pipelineId,
    isStarred = isStarred,
    isArchived = isArchived,
    archivedAt = archivedAt,
)
