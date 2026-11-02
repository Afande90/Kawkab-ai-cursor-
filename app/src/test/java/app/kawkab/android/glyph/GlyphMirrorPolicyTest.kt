package app.kawkab.android.glyph

import app.kawkab.design.device.DevicePage
import app.kawkab.design.device.DeviceSignal
import app.kawkab.design.device.DeviceState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Physical output must stop when hidden and track the same cancellation progress as the LCD. */
class GlyphMirrorPolicyTest {
    @Test fun backgroundAndIdleAreOff() {
        for (signal in DeviceSignal.entries) {
            assertEquals(GlyphPattern.OFF, GlyphMirrorPolicy.frame(DeviceState(signal = signal), false).pattern)
        }
        assertEquals(GlyphPattern.OFF, GlyphMirrorPolicy.frame(DeviceState(), true).pattern)
    }

    @Test fun listeningSpeakingAndAskingHaveDistinctPatterns() {
        for (signal in listOf(DeviceSignal.LISTENING, DeviceSignal.SPEAKING)) {
            assertEquals(GlyphPattern.BREATHE, GlyphMirrorPolicy.frame(DeviceState(signal = signal), true).pattern)
        }
        assertEquals(
            GlyphPattern.ATTENTION,
            GlyphMirrorPolicy.frame(DeviceState(signal = DeviceSignal.ASKING), true).pattern,
        )
    }

    @Test fun countdownTracksElapsedFractionAndClamps() {
        for ((fraction, percentage) in listOf(-1f to 0, 0f to 0, 0.5f to 50, 1f to 100, 2f to 100)) {
            val frame = GlyphMirrorPolicy.frame(
                DeviceState(page = DevicePage.CALLING, signal = DeviceSignal.ACTING, countdownProgress = fraction),
                true,
            )
            assertEquals(GlyphPattern.PROGRESS, frame.pattern)
            assertEquals(percentage, frame.progress)
        }
    }

    @Test fun invalidProgressCannotCrashTheSession() {
        val frame = GlyphMirrorPolicy.frame(
            DeviceState(page = DevicePage.CALLING, signal = DeviceSignal.ACTING, countdownProgress = Float.NaN),
            true,
        )
        assertEquals(0, frame.progress)
    }
}
