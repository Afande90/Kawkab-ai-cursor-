package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.knotwork.android.R
import app.knotwork.android.data.network.ResumableFileDownloader
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.RegisterDownloadedModelUseCase
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Covers the download worker's policy decisions: what it does with the token,
 * what it does once the bytes are on disk, and — the part that decides whether
 * a flaky network costs the user their progress — when a failure is worth
 * another attempt.
 */
@RunWith(RobolectricTestRunner::class)
class ModelDownloadWorkerTest {

    private lateinit var context: Context
    private lateinit var downloader: ResumableFileDownloader
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var registerDownloadedModel: RegisterDownloadedModelUseCase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        downloader = mockk()
        settingsRepository = mockk()
        every { settingsRepository.huggingFaceAuthToken } returns flowOf("hf_stored")
        registerDownloadedModel = mockk(relaxed = true)
        coEvery { registerDownloadedModel(any(), any(), any(), any()) } returns 1L
    }

    @Test
    fun `given a completed download when it finishes then the model is registered with its source`() = runTest {
        val file = java.io.File(context.cacheDir, "m.bin").apply { writeText("payload") }
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Success(file.absolutePath)

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            file.absolutePath,
            (result as ListenableWorker.Result.Success).outputData.getString(ModelDownloadWorker.KEY_OUTPUT_PATH),
        )
        // Registration lives here because the transfer outlives every screen —
        // a survived download must leave a model the app knows about.
        // The address goes with it, so a restored phone can fetch the model again.
        coVerify(exactly = 1) {
            registerDownloadedModel(
                fileName = "m.bin",
                path = file.absolutePath,
                sizeBytes = "payload".length.toLong(),
                sourceUrl = "http://example.com/m.bin",
            )
        }
    }

    @Test
    fun `given no stored-auth flag when downloading then no token is read or sent`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Success("/models/m.bin")

        worker(useStoredAuth = false).doWork()

        coVerify { downloader.download(any(), any(), null, any()) }
    }

    @Test
    fun `given the stored-auth flag when downloading then the token comes from the encrypted store`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Success("/models/m.bin")

        worker(useStoredAuth = true).doWork()

        // The secret is read here, not carried through worker input — that
        // input is persisted by WorkManager in the clear.
        coVerify { downloader.download(any(), any(), "hf_stored", any()) }
    }

    @Test
    fun `given a transport failure on the first attempt when it fails then another attempt is scheduled`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Network timeout", httpCode = null)

        val result = worker().doWork()

        // Retrying resumes rather than restarts, so it costs the user nothing.
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `given an HTTP status when it fails then it gives up immediately`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Server returned code: 404", httpCode = 404)

        val result = worker().doWork()

        // The server's answer will not change on a retry — spending the user's
        // battery to hear it again is the wrong trade.
        val failure = result as ListenableWorker.Result.Failure
        assertEquals(404, failure.outputData.getInt(ModelDownloadWorker.KEY_ERROR_CODE, 0))
        assertEquals("Server returned code: 404", failure.outputData.getString(ModelDownloadWorker.KEY_ERROR))
    }

    @Test
    fun `given the attempt budget is spent when a transport failure repeats then it fails for good`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Network timeout", httpCode = null, bytesTransferred = 0L)

        val result = worker(runAttemptCount = 2).doWork()

        val failure = result as ListenableWorker.Result.Failure
        assertEquals(
            ModelDownloadWorker.NO_HTTP_CODE,
            failure.outputData.getInt(ModelDownloadWorker.KEY_ERROR_CODE, 0),
        )
    }

    @Test
    fun `given a stalled transfer past the budget when it fails then it stops retrying`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Network timeout", httpCode = null, bytesTransferred = 0L)

        val result = worker(runAttemptCount = 2).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `given a failing attempt that moved bytes when the budget is spent then it still retries`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Network timeout", httpCode = null, bytesTransferred = 4_096L)

        val result = worker(runAttemptCount = 5).doWork()

        // The system re-runs this worker every time the network constraint
        // lapses, so the attempt count is not evidence of trouble — a file that
        // keeps growing is evidence of the opposite.
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `given a request without a URL when run then it fails instead of downloading`() = runTest {
        val result = worker(url = null).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { downloader.download(any(), any(), any(), any()) }
    }

    @Test
    fun `given the foreground budget is spent when the download starts then it pauses without the foreground`() =
        runTest {
            val budget = DownloadForegroundBudget(context).apply { exhaust() }
            val foreground = RecordingForegroundUpdater()

            val result = worker(budget = budget, foreground = foreground).doWork()

            // Entering the foreground now would be refused by Android until the
            // user returns — or, worse, restart the clock that ends in a crash.
            val failure = result as ListenableWorker.Result.Failure
            assertEquals(
                context.getString(R.string.model_download_paused_time_limit),
                failure.outputData.getString(ModelDownloadWorker.KEY_ERROR),
            )
            assertEquals(0, foreground.calls)
            coVerify(exactly = 0) { downloader.download(any(), any(), any(), any()) }
        }

    @Test
    fun `given the budget runs out mid-transfer when downloading then it pauses instead of finishing`() = runTest {
        val budget = virtualBudget()
        spend(budget, DownloadForegroundBudget.BUDGET_MS - TEN_MINUTES_MS)
        coEvery { downloader.download(any(), any(), any(), any()) } coAnswers {
            delay(2 * TEN_MINUTES_MS)
            ResumableFileDownloader.Outcome.Success("/models/m.bin")
        }

        val result = worker(budget = budget).doWork()

        // The transfer is stopped between two chunks and its bytes stay in the
        // partial file, so tapping Download again continues from there.
        val failure = result as ListenableWorker.Result.Failure
        assertEquals(
            context.getString(R.string.model_download_paused_time_limit),
            failure.outputData.getString(ModelDownloadWorker.KEY_ERROR),
        )
        assertTrue(budget.isExhausted())
        coVerify(exactly = 0) { registerDownloadedModel(any(), any(), any(), any()) }
    }

    @Test
    fun `given time in the foreground when the download runs then it is booked against the budget`() = runTest {
        val budget = virtualBudget()
        var leftAtProgress = -1L
        coEvery { downloader.download(any(), any(), any(), any()) } coAnswers {
            val onProgress = arg<suspend (Int) -> Unit>(3)
            delay(TEN_MINUTES_MS)
            onProgress(50)
            leftAtProgress = budget.remainingMs()
            delay(TEN_MINUTES_MS)
            ResumableFileDownloader.Outcome.Success("/models/m.bin")
        }

        worker(budget = budget).doWork()

        // Booked as progress arrives, not only at the end: a process killed
        // mid-transfer loses at most the stretch since the last percent.
        assertEquals(DownloadForegroundBudget.BUDGET_MS - TEN_MINUTES_MS, leftAtProgress)
        assertEquals(DownloadForegroundBudget.BUDGET_MS - 2 * TEN_MINUTES_MS, budget.remainingMs())
    }

    @Test
    fun `given Android stops the download for its time limit when it is cancelled then the budget is spent`() =
        runTest {
            val budget = virtualBudget()
            coEvery { downloader.download(any(), any(), any(), any()) } coAnswers { awaitCancellation() }
            val subject = worker(budget = budget)
            val run = launch { subject.doWork() }
            runCurrent()

            // WorkManager records the reason and then cancels the work; `stop` is
            // its own (library-restricted) entry point for exactly that.
            subject.stop(WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT)
            run.cancel()
            run.join()

            // The count fell short of Android's, so the next run must pause at
            // once rather than try the foreground again.
            assertTrue(budget.isExhausted())
        }

    @Test
    fun `given the download is stopped for another reason when it is cancelled then the budget keeps its time`() =
        runTest {
            val budget = virtualBudget()
            coEvery { downloader.download(any(), any(), any(), any()) } coAnswers { awaitCancellation() }
            val subject = worker(budget = budget)
            val run = launch { subject.doWork() }
            runCurrent()
            advanceTimeBy(TEN_MINUTES_MS)

            subject.stop(WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY)
            run.cancel()
            run.join()

            assertEquals(DownloadForegroundBudget.BUDGET_MS - TEN_MINUTES_MS, budget.remainingMs())
        }

    private fun worker(
        url: String? = "http://example.com/m.bin",
        fileName: String = "m.bin",
        useStoredAuth: Boolean = false,
        runAttemptCount: Int = 0,
        budget: DownloadForegroundBudget = DownloadForegroundBudget(context),
        foreground: RecordingForegroundUpdater = RecordingForegroundUpdater(),
    ): ModelDownloadWorker = TestListenableWorkerBuilder<ModelDownloadWorker>(context)
        .setInputData(
            workDataOf(
                ModelDownloadWorker.KEY_URL to url,
                ModelDownloadWorker.KEY_FILE_NAME to fileName,
                ModelDownloadWorker.KEY_USE_STORED_AUTH to useStoredAuth,
            ),
        )
        .setRunAttemptCount(runAttemptCount)
        .setForegroundUpdater(foreground)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = ModelDownloadWorker(
                    appContext,
                    workerParameters,
                    downloader,
                    settingsRepository,
                    registerDownloadedModel,
                    budget,
                )
            },
        )
        .build()

    /**
     * A budget measured on the test's virtual clock, so a download's foreground
     * time is exactly the virtual time it spent.
     */
    private fun TestScope.virtualBudget(): DownloadForegroundBudget = DownloadForegroundBudget(
        preferences = { context.getSharedPreferences("virtual-budget", Context.MODE_PRIVATE) },
        clock = { testScheduler.currentTime },
    )

    /** Books [spentMs] of foreground time against [budget] on the virtual clock. */
    private fun TestScope.spend(budget: DownloadForegroundBudget, spentMs: Long) {
        val meter = budget.startMeter()
        advanceTimeBy(spentMs)
        meter.record()
    }

    /** Counts the worker's requests to enter or update the foreground. */
    private class RecordingForegroundUpdater : ForegroundUpdater {
        /** Requests received so far. */
        var calls = 0

        override fun setForegroundAsync(
            context: Context,
            id: UUID,
            foregroundInfo: ForegroundInfo,
        ): ListenableFuture<Void> {
            calls += 1
            return CompletedFuture
        }
    }

    /** A future that is already complete, as a granted foreground request is. */
    private object CompletedFuture : ListenableFuture<Void> {
        override fun addListener(listener: Runnable, executor: Executor) = executor.execute(listener)

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean = true

        override fun get(): Void? = null

        override fun get(timeout: Long, unit: TimeUnit): Void? = null
    }

    private companion object {
        /** Ten minutes of virtual time, in milliseconds. */
        const val TEN_MINUTES_MS = 10L * 60L * 1_000L
    }
}
