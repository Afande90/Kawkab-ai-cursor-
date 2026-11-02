package app.kawkab.android.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.kawkab.design.device.DeviceState
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference

/** Shares the physical controller between the main window and the power-button surface. */
@Composable
fun GlyphMirror(state: DeviceState) {
    val context = LocalContext.current
    val controller = remember(context) { ForegroundGlyph.get(context.applicationContext) }
    val token = remember { Any() }
    val latest = rememberUpdatedState(state)
    LifecycleResumeEffect(controller) {
        controller.claim(token, GlyphMirrorPolicy.frame(latest.value, true))
        onPauseOrDispose { controller.release(token) }
    }
    LaunchedEffect(state.signal, state.page, state.countdownProgress, state.seconds) {
        while (true) {
            controller.paint(token, GlyphMirrorPolicy.frame(latest.value, true))
            delay(REPEAT_MS)
        }
    }
    DisposableEffect(controller) { onDispose { controller.release(token) } }
}

/** One SDK connection; an old window cannot turn off the new foreground owner's lights. */
private class ForegroundGlyph(context: Context) {
    private val application = WeakReference(context.applicationContext)
    private var owner: Any? = null
    private var manager: GlyphManager? = null
    private var ready = false
    private var output = GlyphOutput(GlyphPattern.OFF)
    private var previous: GlyphOutput? = null
    private var lastPaint = 0L

    fun claim(token: Any, frame: GlyphOutput) {
        // Never load or bind the vendor service on another manufacturer's phone.
        if (!Build.MANUFACTURER.equals("Nothing", ignoreCase = true) ||
            !runCatching { Common.is24111() }.getOrDefault(false)
        ) {
            return
        }
        owner = token
        output = frame
        if (manager != null) {
            render()
            return
        }
        runCatching {
            val created = GlyphManager.getInstance(application.get() ?: return)
            manager = created
            created.init(object : GlyphManager.Callback {
                override fun onServiceConnected(name: ComponentName) {
                    if (manager !== created || owner == null) return
                    runCatching {
                        ready = created.register(Glyph.DEVICE_24111)
                        if (ready) {
                            created.openSession()
                            render()
                        }
                    }.onFailure { release(token) }
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    if (manager === created) {
                        ready = false
                        previous = null
                    }
                }
            })
        }.onFailure { release(token) }
    }

    fun paint(token: Any, frame: GlyphOutput) {
        if (owner !== token) return
        output = frame
        render()
    }

    private fun render() {
        val glyph = manager ?: return
        if (!ready || owner == null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (output == previous &&
            (output.pattern in listOf(GlyphPattern.OFF, GlyphPattern.PROGRESS) || now - lastPaint < REPEAT_MS)
        ) {
            return
        }
        runCatching {
            when (output.pattern) {
                GlyphPattern.OFF -> glyph.turnOff()
                GlyphPattern.PROGRESS -> glyph.displayProgress(
                    glyph.glyphFrameBuilder.buildChannelC().build(),
                    output.progress,
                )
                GlyphPattern.BREATHE -> glyph.animate(
                    glyph.glyphFrameBuilder.buildChannelC().buildPeriod(
                        BREATHE_MS,
                    ).buildCycles(1).buildInterval(0).build(),
                )
                GlyphPattern.ATTENTION -> glyph.animate(
                    glyph.glyphFrameBuilder.buildChannelA().buildPeriod(
                        ATTENTION_MS,
                    ).buildCycles(2).buildInterval(ATTENTION_MS).build(),
                )
            }
            previous = output
            lastPaint = now
        }.onFailure { previous = null }
    }

    fun release(token: Any) {
        if (owner !== token) return
        owner = null
        ready = false
        manager?.let { glyph ->
            runCatching { glyph.turnOff() }
            runCatching { glyph.closeSession() }
            runCatching { glyph.unInit() }
        }
        manager = null
        previous = null
    }

    companion object {
        private var instance: ForegroundGlyph? = null
        fun get(context: Context): ForegroundGlyph = instance ?: ForegroundGlyph(context).also { instance = it }
    }
}

private const val REPEAT_MS = 1_800L
private const val BREATHE_MS = 1_500
private const val ATTENTION_MS = 300
