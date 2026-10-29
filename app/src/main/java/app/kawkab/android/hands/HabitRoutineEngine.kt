package app.kawkab.android.hands

import javax.inject.Inject
import javax.inject.Singleton

/*
 * Copyright (c) 2026 OpenDroid Contributors
 * Licensed under the Apache License, Version 2.0.
 * Adapted for Kawkab from yashab-cyber/opendroid @ c08175cb815344da038f92c80e301929b28d79e8.
 * Room and the personal knowledge graph are not ported. Detection keeps an in-memory
 * event list and suggests a routine; it never runs one by itself.
 */

/**
 * Something the owner did, kept so a repeated sequence can be suggested.
 *
 * @property actionName Tool or app action.
 * @property timestampMs When it happened, unix millis.
 */
data class HabitEvent(val actionName: String, val timestampMs: Long)

/**
 * A suggested routine. Approving it makes a macro. Playing that macro still confirms outbound steps.
 *
 * @property name Short label.
 * @property actions The repeated actions, in first-seen order.
 * @property repetitions How many sessions showed this sequence.
 */
data class DetectedRoutine(val name: String, val actions: List<String>, val repetitions: Int)

/**
 * Groups recent actions into half-hour sessions and suggests a sequence that
 * comes back at least [detect]'s minimum number of times in the same part of the day.
 */
@Singleton
class HabitRoutineEngine @Inject constructor() {
    private val events = mutableListOf<HabitEvent>()

    /** Remembers one action. */
    fun recordAction(actionName: String, timestampMs: Long) {
        events += HabitEvent(actionName.trim(), timestampMs)
    }

    /**
     * Routines seen at least [minRepetitions] times. A session is two or more
     * actions inside [sessionWindowMs]. Sequences with one action are ignored.
     */
    fun detect(
        minRepetitions: Int = DEFAULT_REPETITIONS,
        sessionWindowMs: Long = SESSION_WINDOW_MS,
    ): List<DetectedRoutine> {
        val sessions = sessions(events.sortedBy { it.timestampMs }, sessionWindowMs)
        val counts = mutableMapOf<String, MutableList<String>>()
        sessions.forEach { actions ->
            val key = actions.joinToString(">")
            val bucket = counts.getOrPut(key) { mutableListOf() }
            actions.forEach { action -> if (action !in bucket) bucket += action }
        }
        return counts.mapNotNull { (key, actions) ->
            val repetitions = sessions.count { it.joinToString(">") == key }
            if (repetitions < minRepetitions || actions.size < 2) {
                null
            } else {
                DetectedRoutine(name = actions.joinToString(" then "), actions = actions, repetitions = repetitions)
            }
        }
    }

    private fun sessions(sorted: List<HabitEvent>, windowMs: Long): List<List<String>> {
        val found = mutableListOf<List<String>>()
        var current = mutableListOf<HabitEvent>()
        sorted.forEach { event ->
            val previous = current.lastOrNull()
            val gap = if (previous == null) 0L else event.timestampMs - previous.timestampMs
            if (previous != null && gap > windowMs) {
                flush(current, found)
                current = mutableListOf(event)
            } else {
                current += event
            }
        }
        flush(current, found)
        return found
    }

    private fun flush(current: List<HabitEvent>, found: MutableList<List<String>>) {
        if (current.size < 2) return
        val actions = current.map { it.actionName }.distinct()
        if (actions.size >= 2) found += actions
    }

    private companion object {
        const val DEFAULT_REPETITIONS = 3
        const val SESSION_WINDOW_MS = 1_800_000L
    }
}
