package app.kawkab.android.domain.services

import app.kawkab.android.domain.models.Trigger

/**
 * Domain port abstracting the background registration of [Trigger]s away from
 * any concrete scheduling framework.
 *
 * Introduced so the trigger use cases can decide *which* triggers should be
 * watched without importing `androidx.work`: the implementation
 * (`data/services/WorkManagerTriggerScheduler`) maps each active trigger onto a
 * constraint-gated WorkManager request that wakes
 * `data/services/TriggerWatchWorker` when the trigger's condition is satisfied.
 *
 * The mapping from a [Trigger] to the concrete background constraints
 * (charging / network type / periodic interval / time-of-day delay), the
 * unique-work key and the existing-work policy all live behind this boundary.
 */
interface TriggerScheduler {

    /**
     * Registers the background watches of [activeTriggers].
     *
     * Idempotent and edge-safe: triggers in the set are (re)registered, and the
     * shared charging watch is cancelled when no trigger in the set needs it. A
     * watch registered for a trigger **absent** from the set is not cancelled
     * here — the runtime cannot list them — and ends itself on its next wake,
     * when its trigger is gone or inactive. To stop watches at once, use
     * [stopWatching]. Used on app start and after any trigger
     * create/edit/enable/delete.
     *
     * @param activeTriggers The triggers that should currently be watched —
     *   exactly the [Trigger.isActive] subset (enabled and bound).
     */
    fun syncAll(activeTriggers: List<Trigger>)

    /**
     * Registers (or replaces) the background watch for a single active trigger.
     *
     * @param trigger The trigger to watch. Callers pass only [Trigger.isActive]
     *   triggers; passing an inactive one is treated as [cancel].
     */
    fun register(trigger: Trigger)

    /**
     * Cancels the background watch for a trigger, by id. Idempotent.
     *
     * @param triggerId The id of the trigger to stop watching.
     */
    fun cancel(triggerId: String)

    /**
     * Cancels the watches of [triggerIds] and the shared charging watch, and returns once the runtime has
     * recorded every cancellation — for a caller about to end the process.
     *
     * @param triggerIds The ids of the triggers to stop watching.
     */
    suspend fun stopWatching(triggerIds: Collection<String>)
}
