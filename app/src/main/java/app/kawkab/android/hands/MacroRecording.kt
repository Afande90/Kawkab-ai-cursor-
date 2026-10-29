package app.kawkab.android.hands

import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.safety.ActionRunner

/*
 * Copyright (c) 2026 OpenDroid Contributors
 * Licensed under the Apache License, Version 2.0.
 * Adapted for Kawkab from yashab-cyber/opendroid @ c08175cb815344da038f92c80e301929b28d79e8.
 */

/**
 * One recorded step.
 *
 * @property tool Tool name to run again.
 * @property params Arguments captured with the step.
 */
data class MacroStep(val tool: String, val params: Map<String, String>)

/**
 * A macro is a finished run that can be played again. Replay still stops for a tap
 * on every outbound step. Nothing in here marks a step as always allowed.
 *
 * @property id Stable id.
 * @property name What the owner called it.
 * @property steps The successful steps, in order.
 */
data class Macro(val id: String, val name: String, val steps: List<MacroStep>)

/**
 * One history row that might become a macro step.
 *
 * @property tool Tool that ran.
 * @property params Arguments.
 * @property success False when the row must not be recorded.
 * @property order Position in the original run.
 */
data class HistoryRow(val tool: String, val params: Map<String, String>, val success: Boolean, val order: Int)

/**
 * Turns a fully successful history into macro steps. A single failure drops the whole run.
 */
object MacroRecording {
    /**
     * Steps in [rows] order, or null when the run is empty or any row failed.
     */
    fun stepsFromCompleted(rows: List<HistoryRow>): List<MacroStep>? {
        if (rows.isEmpty() || rows.any { !it.success }) return null
        val steps = rows.sortedBy { it.order }.map { row ->
            MacroStep(tool = row.tool.trim(), params = row.params)
        }
        if (steps.isEmpty() || steps.any { it.tool.isBlank() }) return null
        return steps
    }
}

/**
 * Plays a macro or a skill. Each step goes through [ActionRunner], so an outbound
 * step with [confirmed] false does not run.
 */
class StepReplay(private val risks: (String) -> ToolRisk, private val runner: ActionRunner) {
    /**
     * Runs [steps] in order. Stops at the first step the runner refuses.
     * [entryPoint] is `macro`, `skill`, or `routine`.
     */
    fun play(steps: List<MacroStep>, confirmed: Boolean, entryPoint: String, perform: (MacroStep) -> String): String {
        if (steps.isEmpty()) return "Nothing to replay."
        for (step in steps) {
            var ran = false
            val outcome = runner.run(
                asked = "",
                ran = step.tool,
                risk = risks(step.tool),
                entryPoint = entryPoint,
                confirmed = confirmed,
                perform = {
                    ran = true
                    perform(step)
                },
            )
            if (!ran) return outcome
        }
        return "Replayed ${steps.size} steps."
    }
}
