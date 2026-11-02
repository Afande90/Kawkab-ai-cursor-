package app.kawkab.android.data.repositories

import androidx.room.Room
import app.kawkab.android.data.local.AppDatabase
import app.kawkab.android.data.local.Converters
import app.kawkab.android.data.local.EmbeddingBlobCodec
import app.kawkab.android.data.local.dao.MemoryDao
import app.kawkab.android.data.local.models.MemoryChunkEntity
import app.kawkab.android.domain.models.MemorySource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Real Room compaction preserves source history and reduces the active retrieval set. */
@RunWith(RobolectricTestRunner::class)
class MemoryConsolidationPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MemoryDao
    private lateinit var repository: MemoryRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.memoryDao()
        repository = MemoryRepositoryImpl(dao, Converters(), database.memoryHistoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seed(id: Long, text: String) {
        dao.insertMemory(
            MemoryChunkEntity(
                id = id,
                text = text,
                embedding = EmbeddingBlobCodec.encode(floatArrayOf(1f, 0f)),
                timestamp = id,
                source = MemorySource.Manual,
            ),
        )
    }

    @Test
    fun `given consolidated originals when replaced then history remains and only current chunks are retrieved`() = runTest {
        seed(1L, "fact one")
        seed(2L, "fact two")
        seed(3L, "fact three")
        seed(4L, "unrelated fact")

        repository.replaceWithConsolidated(
            text = "merged fact",
            embedding = floatArrayOf(0.6f, 0.8f),
            originalIds = listOf(1L, 2L, 3L),
        )

        val stored = repository.getAllMemories()
        assertEquals(5, stored.size)
        assertEquals(setOf(1L, 2L, 3L), stored.filter { "status:past" in it.tags }.map { it.id }.toSet())
        val active = repository.findSimilarMemories(floatArrayOf(1f, 0f), null).map { it.first }
        assertEquals(setOf("unrelated fact", "merged fact"), active.map { it.text }.toSet())
        val summary = stored.single { it.text == "merged fact" }
        assertEquals(MemorySource.Compaction(originalChunkIds = listOf(1L, 2L, 3L)), summary.source)
        assertEquals(5, dao.countMemories())
    }

    @Test
    fun `given an empty original list when replaced then the summary is stored and nothing is deleted`() = runTest {
        seed(1L, "fact one")

        repository.replaceWithConsolidated(
            text = "standalone summary",
            embedding = floatArrayOf(0.6f, 0.8f),
            originalIds = emptyList(),
        )

        assertEquals(2, dao.countMemories())
        assertEquals(setOf("fact one", "standalone summary"), repository.getAllMemories().map { it.text }.toSet())
    }
}
