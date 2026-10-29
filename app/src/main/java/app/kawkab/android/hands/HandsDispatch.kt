package app.kawkab.android.hands

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one recorded step on [PhoneHands]. Unknown steps are reported and not guessed.
 */
@Singleton
class HandsDispatch @Inject constructor(private val hands: PhoneHands) {
    /**
     * Performs [step]. The caller has already passed the denylist and the tap gate.
     */
    fun perform(step: MacroStep): String = when (step.tool) {
        "call_phone" -> hands.placeCall(step.params["number"].orEmpty())
        "send_sms" -> hands.composeSms(step.params["number"].orEmpty(), step.params["body"].orEmpty())
        "open_app" -> hands.openApp(step.params["name"].orEmpty())
        "set_alarm" -> hands.setAlarm(
            step.params["hour"]?.toIntOrNull() ?: 0,
            step.params["minute"]?.toIntOrNull() ?: 0,
            step.params["label"].orEmpty(),
        )
        "set_timer" -> hands.setTimer(step.params["seconds"]?.toIntOrNull() ?: 0)
        "read_screen" -> hands.readScreen()
        else -> "Step '${step.tool}' is not a hands action."
    }
}
