package app.kawkab.android.voice

import android.content.Context
import android.util.AtomicFile
import timber.log.Timber
import java.io.File
import java.time.LocalDate

/** Daily microphone-open milliseconds, stored locally without audio and excluded from every backup. */
class WakeMicUsage(context: Context) {
    private val directory = File(context.noBackupFilesDir, "wake_mic_usage")
    private val pending = linkedMapOf<LocalDate, Long>()

    /** Fast in-memory accounting, safe for sensor callbacks; disk I/O happens only on the wake worker. */
    @Synchronized
    fun record(slices: Map<LocalDate, Long>) {
        slices.forEach { (day, millis) -> if (millis > 0) pending[day] = (pending[day] ?: 0) + millis }
    }

    /** Persist each pending day once; failed writes remain pending for the next checkpoint. Single writer. */
    fun flush() {
        val snapshot = synchronized(this) { pending.toMap() }
        snapshot.forEach { (day, amount) ->
            if (add(day, amount)) synchronized(this) {
                val remaining = (pending[day] ?: 0) - amount
                if (remaining > 0) pending[day] = remaining else pending.remove(day)
            }
        }
    }

    /** Fresh reads work across the app and wake processes; a read never interferes with an atomic write. */
    fun dailyMillis(): Map<LocalDate, Long> = directory.listFiles().orEmpty().mapNotNull { file ->
        if (!file.name.endsWith(".ms")) return@mapNotNull null
        runCatching { LocalDate.parse(file.name.removeSuffix(".ms")) to file.readText().toLong().coerceAtLeast(0) }
            .getOrNull()
    }.toMap()

    private fun add(day: LocalDate, amount: Long): Boolean = runCatching {
        directory.mkdirs()
        val file = File(directory, "$day.ms")
        val previous = if (file.exists()) file.readText().toLong() else 0L
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write((previous + amount).toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        true
    }.getOrElse {
        Timber.w("Could not checkpoint wake microphone duration (%s)", it.javaClass.simpleName)
        false
    }
}
