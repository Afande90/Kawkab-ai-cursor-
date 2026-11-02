package app.kawkab.android.domain.voice

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Counts actual capture time using a monotonic clock, apportioned to local calendar dates. */
class MicUsageMeter {
    private var previousElapsed: Long? = null
    private var previousWall = 0L

    /** Start accounting only after the recording resource has opened successfully. */
    fun open(wallMs: Long, elapsedMs: Long) {
        check(previousElapsed == null)
        previousElapsed = elapsedMs
        previousWall = wallMs
    }

    /** Returns only the new interval since the previous checkpoint; paused time never counts. */
    fun checkpoint(wallMs: Long, elapsedMs: Long, zone: ZoneId): Map<LocalDate, Long> {
        val previous = previousElapsed ?: return emptyMap()
        var remaining = (elapsedMs - previous).coerceAtLeast(0)
        var cursor = previousWall
        previousWall = wallMs
        previousElapsed = elapsedMs
        val slices = linkedMapOf<LocalDate, Long>()
        while (remaining > 0) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val midnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val duration = minOf(remaining, midnight - cursor)
            slices[date] = (slices[date] ?: 0) + duration
            cursor += duration
            remaining -= duration
        }
        return slices
    }

    /** End an open interval exactly once, including when no audio frame was successfully read. */
    fun close(wallMs: Long, elapsedMs: Long, zone: ZoneId): Map<LocalDate, Long> =
        checkpoint(wallMs, elapsedMs, zone).also { previousElapsed = null }
}
