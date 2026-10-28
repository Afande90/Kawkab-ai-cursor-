package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.ModelFileHash
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.services.ModelFileHashScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the upsert semantics of registering a downloaded file: the write runs
 * on every completed download, including re-downloads of a file the user
 * already has, so "insert once, refresh afterwards" is the whole contract.
 */
class RegisterDownloadedModelUseCaseTest {

    private val localModelRepository = mockk<LocalModelRepository>()
    private val hashScheduler = mockk<ModelFileHashScheduler>(relaxed = true)
    private val useCase = RegisterDownloadedModelUseCase(localModelRepository, hashScheduler)

    @Test
    fun `given no existing row when registering then a fresh inactive model is inserted`() = runTest {
        coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns null
        coEvery { localModelRepository.findByPath("/data/gemma.litertlm") } returns null
        val inserted = slot<LocalModel>()
        coEvery { localModelRepository.insertModel(capture(inserted)) } returns 7L

        val id = useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L)

        assertEquals(7L, id)
        assertEquals("gemma.litertlm", inserted.captured.name)
        assertEquals("/data/gemma.litertlm", inserted.captured.path)
        assertEquals(2_048L, inserted.captured.size)
        // Installing a model is not choosing it.
        assertEquals(false, inserted.captured.isActive)
        // A new file has no hash yet: the background pass computes it.
        verify(exactly = 1) { hashScheduler.schedule() }
    }

    @Test
    fun `given an existing row when registering then it is refreshed instead of duplicated`() = runTest {
        val existing = LocalModel(id = 9L, name = "gemma.litertlm", path = "/old/path", size = 0L, isActive = true)
        coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns existing
        val updated = slot<LocalModel>()
        coEvery { localModelRepository.updateModel(capture(updated)) } returns Unit

        val id = useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L)

        // `local_models` has no unique index on the name, so a blind insert
        // would leave the user with two rows for one file.
        coVerify(exactly = 0) { localModelRepository.insertModel(any()) }
        assertEquals(9L, id)
        assertEquals("/data/gemma.litertlm", updated.captured.path)
        assertEquals(2_048L, updated.captured.size)
        // An active model that gets re-downloaded stays active.
        assertEquals(true, updated.captured.isActive)
        // A re-download may have replaced the bytes: the pass re-checks the stamp.
        verify(exactly = 1) { hashScheduler.schedule() }
    }

    @Test
    fun `given a download with a source when registered then the source is recorded`() = runTest {
        // Given
        coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns null
        coEvery { localModelRepository.findByPath("/data/gemma.litertlm") } returns null
        val inserted = slot<LocalModel>()
        coEvery { localModelRepository.insertModel(capture(inserted)) } returns 7L

        // When
        useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L, sourceUrl = SOURCE)

        // Then
        assertEquals(SOURCE, inserted.captured.sourceUrl)
    }

    @Test
    fun `given a row with a source when re-registered then a missing source keeps it and a new one replaces it`() =
        runTest {
            // Given — a file found on disk at start-up re-registers without knowing its source.
            val existing = LocalModel(
                id = 9L,
                name = "gemma.litertlm",
                path = "/data/gemma.litertlm",
                size = 2_048L,
                isActive = false,
                sourceUrl = SOURCE,
            )
            coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns existing
            val updated = mutableListOf<LocalModel>()
            coEvery { localModelRepository.updateModel(capture(updated)) } returns Unit

            // When
            useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L)
            useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L, sourceUrl = "https://example.com/m")

            // Then
            assertEquals(SOURCE, updated[0].sourceUrl)
            assertEquals("https://example.com/m", updated[1].sourceUrl)
        }

    @Test
    fun `given a hashed row when re-registered then the stored hash is carried, not erased`() = runTest {
        // The stamp decides whether the hash still holds; the registration only
        // rewrites name, path and size, so a file that did not change keeps its hash.
        val hash = ModelFileHash(sha256 = "ab12", fileSizeBytes = 2_048L, fileModifiedAtMs = 99L)
        val existing =
            LocalModel(id = 9L, name = "gemma.litertlm", path = "/data/gemma.litertlm", size = 2_048L, isActive = false)
                .copy(fileHash = hash)
        coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns existing
        val updated = slot<LocalModel>()
        coEvery { localModelRepository.updateModel(capture(updated)) } returns Unit

        useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L)

        assertEquals(hash, updated.captured.fileHash)
    }

    @Test
    fun `given a checked download when registered as new then its source hash and file hash are recorded`() = runTest {
        coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns null
        coEvery { localModelRepository.findByPath("/data/gemma.litertlm") } returns null
        val inserted = slot<LocalModel>()
        coEvery { localModelRepository.insertModel(capture(inserted)) } returns 7L
        val hash = ModelFileHash(sha256 = "ab12", fileSizeBytes = 2_048L, fileModifiedAtMs = 99L)

        useCase(
            "gemma.litertlm",
            "/data/gemma.litertlm",
            sizeBytes = 2_048L,
            sourceUrl = SOURCE,
            sourceSha256 = "ab12",
            fileHash = hash,
        )

        assertEquals("ab12", inserted.captured.sourceSha256)
        assertEquals(hash, inserted.captured.fileHash)
    }

    @Test
    fun `given a checked row when re-registered then a missing source hash keeps it and a new one replaces it`() =
        runTest {
            // Whether the kept value still describes the file is decided against the file's
            // current hash (`LocalModel.matchesPublishedHash`), not here.
            val oldHash = ModelFileHash(sha256 = "ab12", fileSizeBytes = 2_048L, fileModifiedAtMs = 99L)
            val newHash = ModelFileHash(sha256 = "cd34", fileSizeBytes = 2_048L, fileModifiedAtMs = 120L)
            val existing = LocalModel(
                id = 9L,
                name = "gemma.litertlm",
                path = "/data/gemma.litertlm",
                size = 2_048L,
                isActive = false,
                fileHash = oldHash,
                sourceSha256 = "ab12",
            )
            coEvery { localModelRepository.findByFileName("gemma.litertlm") } returns existing
            val updated = mutableListOf<LocalModel>()
            coEvery { localModelRepository.updateModel(capture(updated)) } returns Unit

            useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L)
            useCase("gemma.litertlm", "/data/gemma.litertlm", 2_048L, SOURCE, sourceSha256 = "cd34", fileHash = newHash)

            assertEquals("ab12", updated[0].sourceSha256)
            assertEquals(oldHash, updated[0].fileHash)
            assertEquals("cd34", updated[1].sourceSha256)
            assertEquals(newHash, updated[1].fileHash)
        }

    @Test
    fun `given two registrations of one file at once when both run then one row is inserted`() = runTest {
        // A download finishing while the start-up rediscovery registers the same
        // file: each looked up, found nothing, and inserted.
        val rows = mutableListOf<LocalModel>()
        coEvery { localModelRepository.findByFileName(any()) } coAnswers {
            // Read, then suspend as a database call does before its result is used.
            val found = rows.firstOrNull { it.name == firstArg<String>() }
            yield()
            found
        }
        coEvery { localModelRepository.insertModel(any()) } coAnswers {
            rows += firstArg<LocalModel>()
            rows.size.toLong()
        }
        coEvery { localModelRepository.findByPath(any()) } returns null
        coEvery { localModelRepository.updateModel(any()) } returns Unit

        listOf(
            async { useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L) },
            async { useCase("gemma.litertlm", "/data/gemma.litertlm", sizeBytes = 2_048L) },
        ).awaitAll()

        assertEquals(1, rows.size)
    }

    @Test
    fun `given a row for the same file under its on-disk name when registering then it is renamed, not duplicated`() =
        runTest {
            // The start-up pass registered a sub-folder download under its flattened
            // on-disk name; a later download of the same file names it by repository path.
            val rediscovered =
                LocalModel(
                    id = 4L,
                    name = "q4_model.litertlm",
                    path = "/ext/q4_model.litertlm",
                    size = 1L,
                    isActive = false,
                )
            coEvery { localModelRepository.findByFileName("q4/model.litertlm") } returns null
            coEvery { localModelRepository.findByPath("/ext/q4_model.litertlm") } returns rediscovered
            val updated = slot<LocalModel>()
            coEvery { localModelRepository.updateModel(capture(updated)) } returns Unit

            val id = useCase("q4/model.litertlm", "/ext/q4_model.litertlm", sizeBytes = 2_048L)

            coVerify(exactly = 0) { localModelRepository.insertModel(any()) }
            assertEquals(4L, id)
            assertEquals("q4/model.litertlm", updated.captured.name)
            assertEquals(2_048L, updated.captured.size)
        }

    private companion object {
        const val SOURCE = "https://huggingface.co/litert-community/gemma/resolve/main/gemma.litertlm"
    }
}
