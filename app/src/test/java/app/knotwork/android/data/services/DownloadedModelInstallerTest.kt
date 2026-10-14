package app.knotwork.android.data.services

import app.knotwork.android.data.local.StreamingModelFileHasher
import app.knotwork.android.data.network.ResumableFileDownloader
import app.knotwork.android.domain.models.ModelFileHash
import app.knotwork.android.domain.services.ModelFileHasher
import app.knotwork.android.domain.usecases.RegisterDownloadedModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Covers the one place a downloaded file becomes a model: the file is checked against the hash
 * its source published before it reaches its final name, a file that fails is deleted and never
 * registered, and a file with nothing to check against is installed as unchecked.
 */
class DownloadedModelInstallerTest {

    private lateinit var dir: File
    private lateinit var registerDownloadedModel: RegisterDownloadedModelUseCase
    private lateinit var installer: DownloadedModelInstaller

    private val sourceSha256 = slot<String?>()
    private val fileHash = slot<ModelFileHash?>()

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "installer_test_${System.nanoTime()}").apply { mkdirs() }
        registerDownloadedModel = mockk()
        coEvery {
            registerDownloadedModel(
                any(),
                any(),
                any(),
                any(),
                captureNullable(sourceSha256),
                captureNullable(fileHash),
            )
        } returns 1L
        installer = DownloadedModelInstaller(StreamingModelFileHasher(Dispatchers.Unconfined), registerDownloadedModel)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `given a file matching its published hash when installed then it is moved and registered as checked`() =
        runTest {
            val downloaded = part("weights", published = sha256Of("weights"))

            val result = installer.install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

            assertEquals(DownloadedModelInstaller.Result.Installed(target.absolutePath, verified = true), result)
            assertEquals("weights", target.readText())
            assertFalse(File(downloaded.partPath).exists())
            coVerify(exactly = 1) {
                registerDownloadedModel(
                    fileName = "m.litertlm",
                    path = target.absolutePath,
                    sizeBytes = "weights".length.toLong(),
                    sourceUrl = SOURCE,
                    sourceSha256 = any(),
                    fileHash = any(),
                )
            }
            assertEquals(sha256Of("weights"), sourceSha256.captured)
            // The hash read for the check is the file's hash: the background pass need not read it again.
            assertEquals(sha256Of("weights"), fileHash.captured?.sha256)
            assertTrue(fileHash.captured!!.describes(target.length(), target.lastModified()))
        }

    @Test
    fun `given a published hash in upper case when the file matches then it is installed as checked`() = runTest {
        val downloaded = part("weights", published = sha256Of("weights").uppercase())

        val result = installer.install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

        assertEquals(DownloadedModelInstaller.Result.Installed(target.absolutePath, verified = true), result)
        assertEquals(sha256Of("weights"), sourceSha256.captured)
    }

    @Test
    fun `given a file differing from its published hash when installed then it is deleted and never registered`() =
        runTest {
            val downloaded = part("tampered", published = sha256Of("weights"))

            val result = installer.install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

            assertEquals(
                DownloadedModelInstaller.Result.Rejected(
                    publishedSha256 = sha256Of("weights"),
                    actualSha256 = sha256Of("tampered"),
                ),
                result,
            )
            coVerify(exactly = 0) { registerDownloadedModel(any(), any(), any(), any(), any(), any()) }
            assertFalse("never under a model's name", target.exists())
            // Kept, the part would be resumed by the next attempt into the same wrong file.
            assertFalse(File(downloaded.partPath).exists())
        }

    @Test
    fun `given a file whose source published no hash when installed then it is registered as unchecked`() = runTest {
        val downloaded = part("weights", published = null)

        val result = installer.install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

        assertEquals(DownloadedModelInstaller.Result.Installed(target.absolutePath, verified = false), result)
        assertEquals("weights", target.readText())
        assertNull("nothing was checked, so no source hash is recorded", sourceSha256.captured)
        assertEquals(sha256Of("weights"), fileHash.captured?.sha256)
    }

    @Test
    fun `given a published hash when the file cannot be read then it is neither installed nor deleted`() = runTest {
        val unreadable = mockk<ModelFileHasher>()
        coEvery { unreadable.hash(any()) } returns null
        val downloaded = part("weights", published = sha256Of("weights"))

        val result = DownloadedModelInstaller(unreadable, registerDownloadedModel)
            .install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

        // Installing it unread would skip the check the source made possible; deleting it
        // would throw away a download the next attempt can still check.
        assertEquals(
            DownloadedModelInstaller.Result.Cause.UNREADABLE,
            (result as DownloadedModelInstaller.Result.NotInstalled).cause,
        )
        coVerify(exactly = 0) { registerDownloadedModel(any(), any(), any(), any(), any(), any()) }
        assertFalse(target.exists())
        assertTrue(File(downloaded.partPath).exists())
    }

    @Test
    fun `given no published hash when the file cannot be read then it is installed without a hash`() = runTest {
        val unreadable = mockk<ModelFileHasher>()
        coEvery { unreadable.hash(any()) } returns null
        val downloaded = part("weights", published = null)

        val result = DownloadedModelInstaller(unreadable, registerDownloadedModel)
            .install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

        assertEquals(DownloadedModelInstaller.Result.Installed(target.absolutePath, verified = false), result)
        assertNull(sourceSha256.captured)
        assertNull("the background pass hashes it later", fileHash.captured)
    }

    @Test
    fun `given the file cannot be moved into place when installed then nothing is registered and the part stays`() =
        runTest {
            val downloaded = part("weights", published = sha256Of("weights"))
                .copy(targetPath = File(dir, "missing-folder/m.litertlm").absolutePath)

            val result = installer.install(downloaded, fileName = "m.litertlm", sourceUrl = SOURCE)

            assertEquals(
                DownloadedModelInstaller.Result.Cause.NOT_MOVED,
                (result as DownloadedModelInstaller.Result.NotInstalled).cause,
            )
            coVerify(exactly = 0) { registerDownloadedModel(any(), any(), any(), any(), any(), any()) }
            assertTrue(File(downloaded.partPath).exists())
        }

    /** Where the installed file lands. */
    private val target: File get() = File(dir, "m.litertlm")

    /** A complete part file holding [content], as the downloader hands it over. */
    private fun part(content: String, published: String?): ResumableFileDownloader.Outcome.Downloaded {
        val part = File(dir, "m.litertlm.cafe.part").apply { writeText(content) }
        return ResumableFileDownloader.Outcome.Downloaded(
            partPath = part.absolutePath,
            targetPath = target.absolutePath,
            publishedSha256 = published,
        )
    }

    /** Lowercase hex SHA-256 of [text]'s UTF-8 bytes. */
    private fun sha256Of(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE = "https://huggingface.co/org/repo/resolve/main/m.litertlm"
    }
}
