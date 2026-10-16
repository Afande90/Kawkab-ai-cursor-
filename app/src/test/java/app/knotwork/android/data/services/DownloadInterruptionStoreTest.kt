package app.knotwork.android.data.services

import android.content.Context
import app.knotwork.android.domain.models.ModelDownloadFailure
import app.knotwork.android.domain.repositories.InterruptedDownload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers the record of why a model download stopped: it is kept until cleared, one per file, read
 * back across instances (the worker writes it, a screen opened later reads it), and a record this
 * build cannot read is left out rather than failing the screen.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadInterruptionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `given a stop recorded when observed then it comes back with its address, reason and sentence`() = runTest {
        store().record("m.litertlm", URL, ModelDownloadFailure.PAUSED_TIME_LIMIT, "paused")

        assertEquals(
            listOf(InterruptedDownload("m.litertlm", URL, ModelDownloadFailure.PAUSED_TIME_LIMIT, "paused")),
            store().observe().first(),
        )
    }

    @Test
    fun `given a screen observing when the worker records a stop then the screen hears of it`() = runTest {
        // The watched case: the record is written while the screen's collector is subscribed.
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async(Dispatchers.IO) {
            store().observe().onEach { subscribed.complete(Unit) }.take(2).toList()
        }
        subscribed.await()

        store().record("m.litertlm", URL, ModelDownloadFailure.CHECKSUM_MISMATCH, "did not match")

        assertEquals(
            listOf(
                emptyList(),
                listOf(InterruptedDownload("m.litertlm", URL, ModelDownloadFailure.CHECKSUM_MISMATCH, "did not match")),
            ),
            emissions.await(),
        )
    }

    @Test
    fun `given a second stop of the same file when recorded then it replaces the first`() = runTest {
        val store = store()
        store.record("m.litertlm", URL, ModelDownloadFailure.PAUSED_TIME_LIMIT, "paused")

        store.record("m.litertlm", URL, ModelDownloadFailure.CHECKSUM_MISMATCH, "did not match")

        assertEquals(
            listOf(InterruptedDownload("m.litertlm", URL, ModelDownloadFailure.CHECKSUM_MISMATCH, "did not match")),
            store.observe().first(),
        )
    }

    @Test
    fun `given a recorded stop when cleared then nothing is left`() = runTest {
        val store = store()
        store.record("m.litertlm", URL, ModelDownloadFailure.PAUSED_TIME_LIMIT, "paused")

        store.clear("m.litertlm")

        assertEquals(emptyList<InterruptedDownload>(), store.observe().first())
    }

    @Test
    fun `given stops of two files when observed then the oldest comes first`() = runTest {
        val store = store()
        store.record("b.litertlm", URL, ModelDownloadFailure.PAUSED_TIME_LIMIT, "b")
        now += 1
        store.record("a.litertlm", URL, ModelDownloadFailure.CHECKSUM_MISMATCH, "a")

        assertEquals(listOf("b.litertlm", "a.litertlm"), store.observe().first().map { it.fileName })
    }

    @Test
    fun `given a record this build cannot read when observed then it is left out`() = runTest {
        context.getSharedPreferences(DownloadInterruptionStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putString("broken.litertlm", "{not json")
            .putString("later.litertlm", """{"url":"$URL","failure":"FROM_A_LATER_BUILD","message":"x","at":1}""")
            .commit()
        store().record("m.litertlm", URL, ModelDownloadFailure.PAUSED_TIME_LIMIT, "paused")

        assertEquals(listOf("m.litertlm"), store().observe().first().map { it.fileName })
    }

    /** The wall clock the stores read; a test moves it. */
    private var now = 1_000L

    private fun store() = DownloadInterruptionStore(
        preferences = {
            context.getSharedPreferences(DownloadInterruptionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        },
        clock = { now },
    )

    private companion object {
        const val URL = "https://huggingface.co/o/r/resolve/main/m.litertlm"
    }
}
