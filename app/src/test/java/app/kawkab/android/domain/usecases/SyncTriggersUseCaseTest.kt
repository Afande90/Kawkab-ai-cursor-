package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.models.Trigger
import app.kawkab.android.domain.models.TriggerCondition
import app.kawkab.android.domain.repositories.TriggerRepository
import app.kawkab.android.domain.services.TriggerScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [SyncTriggersUseCase] — the one-shot reconcile that hands the
 * current active-trigger snapshot to the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncTriggersUseCaseTest {

    @Test
    fun `given active triggers when invoked then scheduler syncs exactly that set`() = runTest {
        val active = listOf(
            Trigger("a", "A", TriggerCondition.Charging, "p1", "go", enabled = true, createdAt = 0L),
            Trigger("b", "B", TriggerCondition.IntervalSchedule(30), "p2", "go", enabled = true, createdAt = 0L),
        )
        val repository = mockk<TriggerRepository> { every { observeActiveTriggers() } returns flowOf(active) }
        val scheduler = mockk<TriggerScheduler>(relaxed = true)

        SyncTriggersUseCase(repository, scheduler)()

        verify(exactly = 1) { scheduler.syncAll(active) }
    }

    @Test
    fun `given no active triggers when invoked then scheduler syncs an empty set`() = runTest {
        val repository = mockk<TriggerRepository> { every { observeActiveTriggers() } returns flowOf(emptyList()) }
        val scheduler = mockk<TriggerScheduler>(relaxed = true)

        SyncTriggersUseCase(repository, scheduler)()

        verify(exactly = 1) { scheduler.syncAll(emptyList()) }
    }
}
