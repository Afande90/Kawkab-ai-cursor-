package app.kawkab.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Census of the places the app sends a broadcast.
 *
 * **Why external sending has one place.** The externally addressed broadcast is the automation
 * callback, and its address, action and one string extra are the caller's choice. So
 * the sender (`ExternalAutomationCallbackSender`) is also where the contract says no:
 * nothing while the contract is switched off, nothing whose caller-chosen parts are
 * over their ceilings. Those rules used to be expected of each caller, and the first
 * caller — answering a request parsed before the switch was ever read — skipped the
 * switch. A second `sendBroadcast` would be a second path around both rules; this
 * test makes it a decision instead of an accident.
 *
 * **The wake word's private senders.** The wake word runs in its own process so a native crash cannot
 * close the app. `WakeWordService` and `WakeRelayReceiver` talk across that boundary with
 * broadcasts that never leave the app: an explicit component, or `setPackage` to this package
 * with a not-exported receiver. The speech-onset hint is also package-only and has no
 * manifest receiver: only an already resumed Pocket LCD observes it. No caller chooses any part.
 */
class OutboundBroadcastCensusTest {

    @Test
    fun `given the production sources then only the callback sender sends a broadcast`() {
        val senders = ProductionSources.code.filterValues { BROADCAST_API.containsMatchIn(it) }.keys

        assertEquals(setOf(CALLBACK_SENDER) + WAKE_PROCESS, senders)
    }

    @Test
    fun `given the pattern then it matches every broadcast-sending call shape`() {
        // The census is only as good as its pattern: pin what it catches.
        listOf(
            "context.sendBroadcast(intent)",
            "sendOrderedBroadcast(intent, null)",
            "context.sendBroadcastAsUser(intent, user)",
            "LocalBroadcastManager.getInstance(context).sendBroadcast(intent)",
        ).forEach { assertTrue(it, BROADCAST_API.containsMatchIn(it)) }
    }

    private companion object {
        const val CALLBACK_SENDER = "main/java/app/kawkab/android/data/services/ExternalAutomationCallbackSender.kt"

        /** In-app broadcasts between the app and the wake process (M1.8). */
        val WAKE_PROCESS = setOf(
            "main/java/app/kawkab/android/voice/WakeRelayReceiver.kt",
            "main/java/app/kawkab/android/voice/WakeWordService.kt",
            "main/java/app/kawkab/android/voice/WakeHeardSignal.kt",
        )

        /** Any `send…Broadcast…(` call. */
        val BROADCAST_API = Regex("""\bsend\w*Broadcast\w*\s*\(""")
    }
}
