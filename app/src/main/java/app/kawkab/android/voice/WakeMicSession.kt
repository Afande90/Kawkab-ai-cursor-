package app.kawkab.android.voice

import app.kawkab.android.domain.voice.MicUsageMeter
import java.time.LocalDate
import java.time.ZoneId

/** A real capture resource; closing it must stop recording and release the microphone. */
interface WakeCapture : AutoCloseable {
    /** Read a mono PCM frame, or return an audio error. */
    fun read(pcm: ShortArray): Int
}

/** Serializes permission to open capture with every pause, eliminating reopen-after-pause races. */
class WakeMicSession(
    private val factory: () -> WakeCapture?,
    private val usage: (Map<LocalDate, Long>) -> Unit,
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : AutoCloseable {
    private val lock = Object()
    private val meter = MicUsageMeter()
    private var allowed = false
    private var stopped = false
    private var capture: WakeCapture? = null

    /** Hardware callbacks close capture immediately, even while the worker is in a blocking read. */
    fun setAllowed(value: Boolean) = synchronized(lock) {
        allowed = value && !stopped
        if (!allowed) release()
        lock.notifyAll()
    }

    /** Paused workers sleep on a condition instead of continuously polling sensors or native models. */
    fun acquireOrWait(timeoutMs: Long): WakeCapture? = synchronized(lock) {
        if (!allowed && !stopped) lock.wait(timeoutMs)
        if (!allowed || stopped) return@synchronized null
        capture ?: factory()?.also {
            capture = it
            meter.open(wall(), elapsed())
        }
    }

    /** Prevent a frame read before a pause from waking the phone after its resource has been closed. */
    fun isCurrent(candidate: WakeCapture): Boolean = synchronized(lock) { allowed && capture === candidate }

    /** Capture time is checkpointed while open, including silence. No I/O runs under this lock. */
    fun checkpoint() = synchronized(lock) { usage(meter.checkpoint(wall(), elapsed(), zone())) }

    override fun close() = synchronized(lock) {
        stopped = true
        allowed = false
        release()
        lock.notifyAll()
    }

    private fun release() {
        val resource = capture ?: return
        capture = null
        try {
            resource.close()
        } finally {
            usage(meter.close(wall(), elapsed(), zone()))
        }
    }
}
