package app.kawkab.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for "switch HOME → SHOTS → GROWTH → HOME" (task 23d).
 *
 * No model is loaded: tab switching must not depend on model state. The gate is a median frame
 * time under 16 ms on the CI emulator, read from this benchmark's JSON by the workflow.
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMacrobenchmarkApi::class)
    @Test
    fun switchTabs() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Ignore(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.skipOnboarding()
            check(device.wait(Until.hasObject(By.text(HOME)), WAIT_MS))
        },
    ) {
        for (tab in listOf(SHOTS, GROWTH, HOME)) {
            requireNotNull(device.wait(Until.findObject(By.text(tab)), WAIT_MS)) { "Missing device key: $tab" }.click()
            device.waitForIdle()
        }
    }

    private fun UiDevice.skipOnboarding() {
        findObject(By.text(SKIP))?.click() ?: return
        waitForIdle()
        findObject(By.text(CONTINUE))?.click()
        waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "app.kawkab.android.debug"
        const val ITERATIONS = 5
        const val WAIT_MS = 10_000L
        const val HOME = "HOME"
        const val SHOTS = "SHOTS"
        const val GROWTH = "GROWTH"
        const val SKIP = "Skip"
        const val CONTINUE = "Continue"
    }
}
