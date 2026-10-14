package app.knotwork.android.data.backup

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import app.knotwork.android.domain.repositories.AppStateSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * [SafBackupDocuments]: a document is remembered as unfinished from the moment it is opened until it is finished
 * or deleted; a provider's refusal is a `false`, never a crash; and the start-up clean-up never deletes the
 * document a backup in this process is still writing.
 */
@RunWith(RobolectricTestRunner::class)
class SafBackupDocumentsTest {

    private val unfinished = MutableStateFlow<String?>(null)
    private val appState = mockk<AppStateSettings> {
        every { unfinishedBackupDocument } returns unfinished
        coEvery { setUnfinishedBackupDocument(any()) } coAnswers { unfinished.value = firstArg() }
    }
    private val resolver = mockk<ContentResolver>(relaxed = true) {
        every { openOutputStream(any(), "w") } returns ByteArrayOutputStream()
    }

    @Before
    fun setUp() {
        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun `given a document when opened then it is remembered and its grant kept, until it is finished`() = runTest {
        // Given
        val documents = documents()

        // When
        documents.openForWrite(DOCUMENT)

        // Then
        assertEquals(DOCUMENT, unfinished.value)
        verify { resolver.takePersistableUriPermission(Uri.parse(DOCUMENT), GRANT) }
        documents.finish(DOCUMENT)
        assertNull(unfinished.value)
        verify { resolver.releasePersistableUriPermission(Uri.parse(DOCUMENT), GRANT) }
    }

    @Test
    fun `given a provider that refuses the stream when opened then it is an IO failure`() = runTest {
        every { resolver.openOutputStream(any(), "w") } throws SecurityException("revoked")

        val failure = try {
            documents().openForWrite(DOCUMENT)
            null
        } catch (e: IOException) {
            e
        }

        assertTrue(failure != null)
    }

    @Test
    fun `given a provider that cannot delete when discarded then it says so and forgets the document`() = runTest {
        // Given
        every { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) } throws
            UnsupportedOperationException("no delete")
        val documents = documents()
        documents.openForWrite(DOCUMENT)

        // When
        val deleted = documents.discard(DOCUMENT)

        // Then
        assertFalse(deleted)
        assertNull(unfinished.value)
    }

    @Test
    fun `given a document already gone when discarded again then it counts as deleted`() = runTest {
        every { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) } throws FileNotFoundException("gone")

        assertTrue(documents().discard(DOCUMENT))
    }

    @Test
    fun `given a document an earlier process left when the app starts then it is deleted`() = runTest {
        // Given — a fresh process: nothing is being written here.
        unfinished.value = DOCUMENT

        // When
        val clean = documents().discardLeftBehind()

        // Then
        assertTrue(clean)
        verify { DocumentsContract.deleteDocument(any<ContentResolver>(), Uri.parse(DOCUMENT)) }
        assertNull(unfinished.value)
    }

    @Test
    fun `given a backup still writing in this process when the start-up clean-up runs then the document stays`() =
        runTest {
            // Given — the activity recreated while the backup runs.
            val documents = documents()
            documents.openForWrite(DOCUMENT)

            // When
            documents.discardLeftBehind()

            // Then
            verify(exactly = 0) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
            assertEquals(DOCUMENT, unfinished.value)
        }

    @Test
    fun `given a document already deleted when asked again then the first answer stands, unasked`() = runTest {
        // Given
        val documents = documents()
        documents.discard(DOCUMENT)
        every { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) } throws
            IllegalArgumentException("no such document")

        // When
        val again = documents.discard(DOCUMENT)

        // Then
        assertTrue(again)
        verify(exactly = 1) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
    }

    @Test
    fun `given a document opened again after a delete when discarded then the provider is asked again`() = runTest {
        val documents = documents()
        documents.discard(DOCUMENT)

        documents.openForWrite(DOCUMENT)
        documents.discard(DOCUMENT)

        verify(exactly = 2) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
    }

    @Test
    fun `given a stream its caller never got when the document is discarded then the stream is closed`() = runTest {
        // Given
        val stream = mockk<OutputStream>(relaxed = true)
        every { resolver.openOutputStream(any(), "w") } returns stream
        val documents = documents()
        documents.openForWrite(DOCUMENT)

        // When
        documents.discard(DOCUMENT)

        // Then
        verify { stream.close() }
    }

    @Test
    fun `given a running backup when a stray document is discarded then the running one stays recorded`() = runTest {
        val documents = documents()
        documents.openForWrite(DOCUMENT)

        documents.discard(OTHER_DOCUMENT)

        assertEquals(DOCUMENT, unfinished.value)
    }

    @Test
    fun `given a leftover the provider will not delete when the app starts then it says so and lets it go`() = runTest {
        unfinished.value = DOCUMENT
        every { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) } throws
            SecurityException("no grant")

        val clean = documents().discardLeftBehind()

        assertFalse(clean)
        assertNull(unfinished.value)
    }

    @Test
    fun `given a provider that names the document when asked then its name is returned`() = runTest {
        val cursor = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
            every { getString(0) } returns "backup (1).knotwork"
        }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor

        assertEquals("backup (1).knotwork", documents().displayName(DOCUMENT))
    }

    @Test
    fun `given a provider that fails while naming when asked then there is no name, not a crash`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } throws IllegalStateException("provider died")

        assertNull(documents().displayName(DOCUMENT))
    }

    @Test
    fun `given start-up maintenance at the moment a document is recorded when it runs then that document stays`() =
        runTest {
            // Given — the clean-up runs (an activity recreated) exactly while the document is being recorded.
            lateinit var documents: SafBackupDocuments
            var cleanUpDuringRecord: Boolean? = null
            coEvery { appState.setUnfinishedBackupDocument(any()) } coAnswers {
                unfinished.value = firstArg()
                if (firstArg<String?>() != null) cleanUpDuringRecord = documents.discardLeftBehind()
            }
            documents = documents()

            // When
            documents.openForWrite(DOCUMENT)

            // Then — it already counted as being written.
            assertEquals(true, cleanUpDuringRecord)
            verify(exactly = 0) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
        }

    private fun TestScope.documents() = SafBackupDocuments(resolver, appState, StandardTestDispatcher(testScheduler))

    private companion object {
        const val DOCUMENT = "content://com.android.externalstorage.documents/document/primary%3Abackup.knotwork"
        const val OTHER_DOCUMENT = "content://com.android.externalstorage.documents/document/primary%3Aother.knotwork"
        const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
