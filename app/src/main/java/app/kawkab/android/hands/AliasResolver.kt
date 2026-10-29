package app.kawkab.android.hands

/*
 * Copyright (c) 2026 OpenDroid Contributors
 * Licensed under the Apache License, Version 2.0.
 * Adapted for Kawkab from yashab-cyber/opendroid @ c08175cb815344da038f92c80e301929b28d79e8.
 * Phrase tables for finance, food, transport, and social actions are not included.
 */

/**
 * Maps a short phrase to one action hint. Exact text wins. A compound sentence
 * ("open WhatsApp and send…") is left unmatched so a planner can see both parts.
 * Otherwise the longest phrase that occurs in the text wins.
 */
object AliasResolver {
    /**
     * One resolved phrase.
     *
     * @property action Canonical action name.
     * @property params Arguments the phrase already fixed.
     */
    data class ActionHint(val action: String, val params: Map<String, String>)

    private val aliases: Map<String, ActionHint> = mapOf(
        "flashlight" to ActionHint("TOGGLE_FLASHLIGHT", mapOf("state" to "toggle")),
        "torch" to ActionHint("TOGGLE_FLASHLIGHT", mapOf("state" to "toggle")),
        "turn on flashlight" to ActionHint("TOGGLE_FLASHLIGHT", mapOf("state" to "on")),
        "turn off flashlight" to ActionHint("TOGGLE_FLASHLIGHT", mapOf("state" to "off")),
        "screenshot" to ActionHint("read_screen", emptyMap()),
        "read my screen" to ActionHint("read_screen", emptyMap()),
        "read screen" to ActionHint("read_screen", emptyMap()),
        "call" to ActionHint("call_phone", emptyMap()),
        "phone" to ActionHint("call_phone", emptyMap()),
        "text" to ActionHint("send_sms", emptyMap()),
        "set an alarm" to ActionHint("set_alarm", emptyMap()),
        "set a timer" to ActionHint("set_timer", emptyMap()),
        "open" to ActionHint("open_app", emptyMap()),
    )

    private val compoundIntentWords = listOf(" and ", " then ", " after that ")
    private val filler = Regex(
        """\b(the|a|an|please|could you|please turn|turn the|open the|close the)\b""",
    )
    private val alarmPhrases = listOf(
        "set an alarm",
        "set a alarm",
        "set alarm",
        "wake me up at",
        "wake me at",
        "wake me up",
        "wake me",
    )
    private val timerPhrases = listOf(
        "set a timer for",
        "set a timer",
        "set timer for",
        "timer for",
        "timer",
    )

    /**
     * Returns the hint for [input], or null when the phrase is compound or unknown.
     */
    fun resolve(input: String): ActionHint? {
        val lower = input.lowercase().trim()
        val cleaned = cleanInput(input)
        aliases[cleaned]?.let { return it }
        if (compoundIntentWords.any { lower.contains(it) }) return null
        return longest(cleaned, lower)
    }

    /**
     * True when [input] is asking for an alarm.
     */
    fun isAlarmRequest(input: String): Boolean {
        val lower = input.lowercase()
        return alarmPhrases.any { lower.contains(it) }
    }

    /**
     * The time words left after the alarm phrase is removed.
     */
    fun extractAlarmTime(input: String): String? {
        var cleaned = input.lowercase().trim()
        alarmPhrases.sortedByDescending { it.length }.forEach { phrase ->
            cleaned = cleaned.replace(phrase, "")
        }
        cleaned = cleaned.replace("for", "").replace("at", "").trim()
        return cleaned.ifEmpty { null }
    }

    /**
     * True when [input] is asking for a timer.
     */
    fun isTimerRequest(input: String): Boolean {
        val lower = input.lowercase()
        return timerPhrases.any { lower.contains(it) }
    }

    /**
     * Seconds parsed from a timer phrase, or null.
     */
    fun extractTimerDuration(input: String): Int? {
        var cleaned = input.lowercase().trim()
        timerPhrases.sortedByDescending { it.length }.forEach { phrase ->
            cleaned = cleaned.replace(phrase, " ")
        }
        return DurationParser.parseToSeconds(cleaned)
    }

    private fun longest(cleaned: String, lower: String): ActionHint? = aliases.entries
        .filter { (key, _) -> cleaned.contains(key) || lower.contains(key) }
        .maxByOrNull { it.key.length }
        ?.value

    private fun cleanInput(input: String): String = input.lowercase()
        .replace(Regex("""[.,/#!$%^&*;:{}=\-_`~()?+!]"""), " ")
        .replace(filler, "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
