package app.kawkab.android.presentation.ui.chat.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tasks 23h and 23j: a turn with no progress ends instead of hanging. */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnWatchdogTest {

    @Test
    fun `a disabled watchdog never fires`() {
        val scope = TestScope()
        var fired = 0
        TurnWatchdog(scope, 0L, MutableStateFlow(true), { 0L }, { fired += 1 }).start()
        scope.advanceTimeBy(10 * 60_000L)
        assertEquals(0, fired)
        scope.cancel()
    }

    @Test
    fun `an idle chat is not a hang`() {
        val scope = TestScope()
        var fired = 0
        TurnWatchdog(scope, 50L, MutableStateFlow(false), { 0L }, { fired += 1 }).start()
        scope.runCurrent()
        scope.advanceTimeBy(5_000L)
        assertEquals(0, fired)
        scope.cancel()
    }

    @Test
    fun `the default limit is one minute`() {
        assertEquals(60_000L, TurnWatchdogConfig.Default().timeoutMs)
        assertEquals(0L, TurnWatchdogConfig.Disabled.timeoutMs)
    }
}
