package app.kawkab.android.glyph

import app.kawkab.design.device.DevicePage
import app.kawkab.design.device.DeviceSignal
import app.kawkab.design.device.DeviceState
import kotlin.math.roundToInt

/** Phone (3a) has white Glyph LEDs: colours remain on the LCD and patterns mirror their meaning. */
enum class GlyphPattern { OFF, BREATHE, ATTENTION, PROGRESS }

/** A hardware-independent output frame. */
data class GlyphOutput(val pattern: GlyphPattern, val progress: Int = 0)

/** The foreground device surface is the sole source of physical Glyph activity. */
object GlyphMirrorPolicy {
    /** Hidden windows and idle always switch off, including a cancelled countdown. */
    fun frame(state: DeviceState, foreground: Boolean): GlyphOutput {
        if (!foreground || state.signal == DeviceSignal.STANDBY) return GlyphOutput(GlyphPattern.OFF)
        return when (state.signal) {
            DeviceSignal.LISTENING, DeviceSignal.SPEAKING -> GlyphOutput(GlyphPattern.BREATHE)
            DeviceSignal.ASKING -> GlyphOutput(GlyphPattern.ATTENTION)
            DeviceSignal.ACTING -> {
                val fraction =
                    state.countdownProgress ?: ((COUNTDOWN_SECONDS - state.seconds) / COUNTDOWN_SECONDS.toFloat())
                GlyphOutput(
                    if (state.page == DevicePage.CALLING) GlyphPattern.PROGRESS else GlyphPattern.ATTENTION,
                    if (fraction.isFinite()) (fraction.coerceIn(0f, 1f) * 100).roundToInt() else 0,
                )
            }
            DeviceSignal.STANDBY -> GlyphOutput(GlyphPattern.OFF)
        }
    }
}

private const val COUNTDOWN_SECONDS = 3
