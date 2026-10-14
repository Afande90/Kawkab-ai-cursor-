package app.knotwork.android.data.backup

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.knotwork.android.domain.repositories.AppStateSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
        every { openOutputStream(any(), "wt") } returns ByteArrayOutputStream()
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
        every { resolver.openOutputStream(any(), "wt") } throws SecurityException("revoked")

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
    fun `given a stream its caller never got when the document is discarded then the stream is closed`() = runTest {
        // Given
        val stream = mockk<OutputStream>(relaxed = true)
        every { resolver.openOutputStream(any(), "wt") } returns stream
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
    fun `given an existing file picked to overwrite when opened then it is opened truncating`() = runTest {
        documents().openForWrite(DOCUMENT)

        verify { resolver.openOutputStream(Uri.parse(DOCUMENT), "wt") }
    }

    @Test
    fun `given a provider without a truncating mode when opened then it is opened for plain writing`() = runTest {
        every { resolver.openOutputStream(any(), "wt") } throws IllegalArgumentException("bad mode")
        every { resolver.openOutputStream(any(), "w") } returns ByteArrayOutputStream()

        documents().openForWrite(DOCUMENT)

        verify { resolver.openOutputStream(Uri.parse(DOCUMENT), "w") }
    }

    @Test
    fun `given a provider that rejects the truncating mode as a missing file when opened then it writes plainly`() =
        runTest {
            // What `openOutputStream` documents for a mode the provider does not accept.
            every { resolver.openOutputStream(any(), "wt") } throws FileNotFoundException("mode")
            every { resolver.openOutputStream(any(), "w") } returns ByteArrayOutputStream()

            documents().openForWrite(DOCUMENT)

            verify { resolver.openOutputStream(Uri.parse(DOCUMENT), "w") }
        }

    @Test
    fun `given a provider that cannot truncate at all when opened then it writes plainly`() = runTest {
        every { resolver.openOutputStream(any(), "wt") } throws UnsupportedOperationException("no truncate")
        every { resolver.openOutputStream(any(), "w") } returns ByteArrayOutputStream()

        documents().openForWrite(DOCUMENT)

        verify { resolver.openOutputStream(Uri.parse(DOCUMENT), "w") }
    }

    @Test
    fun `given a provider that refuses the plain mode too when opened then it is an IO failure`() = runTest {
        every { resolver.openOutputStream(any(), "wt") } throws IllegalArgumentException("bad mode")
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
    fun `given the document a backup is writing when discarded if empty then it is left to the backup`() = runTest {
        // Given — still empty: the key is being derived.
        sizeIs(0L)
        val documents = documents()
        documents.openForWrite(OTHER_DOCUMENT)

        // When
        val deleted = documents.discardIfEmpty(OTHER_DOCUMENT)

        // Then
        assertFalse(deleted)
        verify(exactly = 0) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
    }

    @Test
    fun `given a provider that fails in its own way when deleting then it says not deleted, not a crash`() = runTest {
        every { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) } throws
            RuntimeException("provider died")

        assertFalse(documents().discard(DOCUMENT))
    }

    @Test
    fun `given a record that cannot be cleared when a document is deleted then the deletion still stands`() = runTest {
        val documents = documents()
        documents.openForWrite(DOCUMENT)
        coEvery { appState.setUnfinishedBackupDocument(null) } throws IOException("datastore")

        assertTrue(documents.discard(DOCUMENT))
    }

    @Test
    fun `given a record that cannot be cleared when a backup is finished then the caller hears of it`() = runTest {
        val documents = documents()
        documents.openForWrite(DOCUMENT)
        coEvery { appState.setUnfinishedBackupDocument(null) } throws IOException("datastore")

        val failure = try {
            documents.finish(DOCUMENT)
            null
        } catch (e: IOException) {
            e
        }

        assertTrue("a complete backup still marked unfinished must not pass quietly", failure != null)
    }

    @Test
    fun `given an empty stray document when discarded if empty then it is deleted`() = runTest {
        sizeIs(0L)

        assertTrue(documents().discardIfEmpty(OTHER_DOCUMENT))
        verify { DocumentsContract.deleteDocument(any<ContentResolver>(), Uri.parse(OTHER_DOCUMENT)) }
    }

    @Test
    fun `given an existing file picked to overwrite when discarded if empty then it stays`() = runTest {
        sizeIs(4_096L)

        assertFalse(documents().discardIfEmpty(OTHER_DOCUMENT))
        verify(exactly = 0) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
    }

    @Test
    fun `given a provider that does not tell the size when discarded if empty then the file stays`() = runTest {
        sizeIs(null)

        assertFalse(documents().discardIfEmpty(OTHER_DOCUMENT))
        verify(exactly = 0) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
    }

    @Test
    fun `given start-up maintenance reading the record when the backup finishes then the complete file stays`() =
        runTest {
            // Given — a backup about to finish, and the clean-up (an activity recreated) reading the record first.
            val reading = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            var reads = 0
            every { appState.unfinishedBackupDocument } answers {
                if (reads++ == 0) {
                    flow<String?> {
                        reading.complete(Unit)
                        gate.await()
                        emit(DOCUMENT)
                    }
                } else {
                    unfinished
                }
            }
            val documents = documents()
            documents.openForWrite(DOCUMENT)

            // When — the backup finishes while the clean-up holds what it read.
            val cleanUp = launch { documents.discardLeftBehind() }
            reading.await()
            val finishing = launch { documents.finish(DOCUMENT) }
            testScheduler.runCurrent()
            gate.complete(Unit)
            cleanUp.join()
            finishing.join()

            // Then — the clean-up saw it still being written; the finish then cleared the record.
            verify(exactly = 0) { DocumentsContract.deleteDocument(any<ContentResolver>(), any()) }
            assertNull(unfinished.value)
        }

    private fun sizeIs(size: Long?) {
        val cursor = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
            every { isNull(0) } returns (size == null)
            every { getLong(0) } returns (size ?: 0L)
        }
        every { resolver.query(any(), arrayOf(OpenableColumns.SIZE), any(), any(), any()) } returns cursor
    }

    private fun TestScope.documents() = SafBackupDocuments(resolver, appState, StandardTestDispatcher(testScheduler))

    private companion object {
        const val DOCUMENT = "content://com.android.externalstorage.documents/document/primary%3Abackup.knotwork"
        const val OTHER_DOCUMENT = "content://com.android.externalstorage.documents/document/primary%3Aother.knotwork"
        const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
