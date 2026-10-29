package app.kawkab.android.hands

/*
 * Copyright (c) 2026 OpenDroid Contributors
 * Licensed under the Apache License, Version 2.0.
 * Adapted for Kawkab from yashab-cyber/opendroid @ c08175cb815344da038f92c80e301929b28d79e8.
 */

/**
 * Parses a short duration phrase into seconds.
 * Accepts "5", "5s", "2 minutes", and "1h30m".
 */
object DurationParser {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val MIN_SECONDS = 1

    private val hourPattern = Regex("""(\d+)\s*(?:hours|hour|hrs|hr|h)(?=\d|\b)""")
    private val minutePattern = Regex("""(\d+)\s*(?:minutes|minute|mins|min|m)(?=\d|\b)""")
    private val secondPattern = Regex("""(\d+)\s*(?:seconds|second|secs|sec|s)(?=\d|\b)""")

    /**
     * Returns a positive number of seconds, or null when [input] is not a duration.
     */
    fun parseToSeconds(input: String): Int? {
        val normalized = input.trim().lowercase()
        if (normalized.isEmpty()) return null
        normalized.toIntOrNull()?.let { return it.coerceAtLeast(MIN_SECONDS) }
        val fromUnits = secondsFromUnits(normalized)
        if (fromUnits != null) return fromUnits
        val bare = Regex("""^(\d+)$""").find(normalized) ?: return null
        return bare.groupValues[1].toIntOrNull()?.coerceAtLeast(MIN_SECONDS)
    }

    private fun secondsFromUnits(normalized: String): Int? {
        var total = 0
        var matched = false
        hourPattern.findAll(normalized).forEach {
            total += it.groupValues[1].toInt() * SECONDS_PER_HOUR
            matched = true
        }
        minutePattern.findAll(normalized).forEach {
            total += it.groupValues[1].toInt() * SECONDS_PER_MINUTE
            matched = true
        }
        secondPattern.findAll(normalized).forEach {
            total += it.groupValues[1].toInt()
            matched = true
        }
        return if (matched) total.coerceAtLeast(MIN_SECONDS) else null
    }
}
