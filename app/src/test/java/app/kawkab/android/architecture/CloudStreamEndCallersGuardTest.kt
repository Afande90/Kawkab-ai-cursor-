package app.kawkab.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Census of the code that turns a cloud model's stream into an answer — every caller of
 * `executeStreaming` — and the rule that each asks `CloudStreamEnd` whether the stream was cut off.
 *
 * **Why.** An OpenAI-compatible client ends a stream whose connection dropped *normally*,
 * without a finish reason, so half an answer looks like a whole one. The Cloud node checked
 * for it; a delegated task and a structured step on a cloud model did not, and passed cut
 * answers on as complete ones. A new caller that reads text deltas and drops the end frame
 * repeats that, and this test fails until it asks the shared rule.
 *
 * **What it cannot see.** It matches the call as written, `.executeStreaming(`, and the name
 * `CloudStreamEnd` anywhere in the file — not that the rule is applied to that stream.
 */
class CloudStreamEndCallersGuardTest {

    @Test
    fun `every file that reads a cloud stream asks whether it was cut off`() {
        val readers = filesMatching(STREAMING_CALL) - FORWARDERS

        val unchecked = readers.filterNot { it in filesMatching(SHARED_RULE) }

        assertEquals(
            "these files read a cloud model's stream without asking CloudStreamEnd whether it was cut off",
            emptySet<String>(),
            unchecked.toSet(),
        )
        assertEquals(
            "a file now reads a cloud model's stream: check it asks CloudStreamEnd, then add it here",
            READERS,
            readers,
        )
    }

    @Test
    fun `the census reads the production sources it claims to`() {
        // Keeps the rule above from passing vacuously on an empty or wrong tree.
        assertTrue(
            "CloudLlmNodeExecutor not found among ${ProductionSources.code.size} production files",
            ProductionSources.code.entries.any { (path, code) ->
                path.endsWith("/CloudLlmNodeExecutor.kt") && STREAMING_CALL.containsMatchIn(code)
            },
        )
    }

    /** File names of the production sources whose code matches [pattern]. */
    private fun filesMatching(pattern: Regex): Set<String> = ProductionSources.code
        .filterValues { pattern.containsMatchIn(it) }
        .keys
        .map { it.substringAfterLast('/') }
        .toSet()

    private companion object {
        /** A call that streams a cloud model's answer. */
        val STREAMING_CALL = Regex("""\.executeStreaming\(""")

        /** The shared rule. */
        val SHARED_RULE = Regex("""\bCloudStreamEnd\b""")

        /** Wrappers that hand every frame on unread; the caller they serve applies the rule. */
        val FORWARDERS = setOf(
            // The retry wrapper around every cloud client.
            "RetryingCloudLlmClient.kt",
        )

        /** Every file that turns a cloud stream into an answer. */
        val READERS = setOf(
            // A Cloud node's answer.
            "CloudLlmNodeExecutor.kt",
            // A structured step (Tool arguments, router, plan, condition) on a cloud model.
            "KoogStructuredInferenceClientFactory.kt",
            // The delegate_task tool.
            "DelegateTaskTool.kt",
        )
    }
}
