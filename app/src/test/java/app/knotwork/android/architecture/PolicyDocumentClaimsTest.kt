package app.knotwork.android.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sentences of the privacy policy, the threat model and the bundled FAQ that the
 * code once contradicted, pinned to what the code does now.
 *
 * Each one was measured false against the tree before it was rewritten: a reader who
 * followed it would have looked in the wrong place or trusted a guarantee the app
 * does not give. The assertions look for the wrong sentence and for the fact that
 * replaced it, so a later edit cannot quietly restore the first.
 *
 * `PRIVACY.md` and `SECURITY.md` are declared inputs of the test task; the FAQ is
 * read from its bundled copy under `src/main/assets`, which recompiles the resources.
 */
class PolicyDocumentClaimsTest {

    private val privacy = document("PRIVACY.md")
    private val security = document("SECURITY.md")
    private val faq = document("app/src/main/assets/docs/faq.md")

    @Test
    fun `given a node whose engine is a cloud provider then the privacy policy counts it as a cloud path`() {
        // Routers, conditions, decompositions, evaluations, tools and skills can run
        // on a cloud provider without a Cloud node in the pipeline.
        assertFalse(privacy.contains("a pipeline without a cloud node never contacts a cloud provider"))
        assertTrue(privacy.contains("whose engine is set to a cloud provider"))
    }

    @Test
    fun `given a failed run in the trigger journal then no document says the export holds none of a run's text`() {
        // The trigger journal export carries a failed run's error message.
        assertFalse(privacy.contains("does not contain the content of your runs"))
        assertFalse(security.contains("not the content of the runs they describe"))
        assertFalse(faq.contains("no message a run was given, no answer it produced."))
        listOf(privacy, security, faq).forEach { assertTrue(it.contains("error message")) }
    }

    @Test
    fun `given the network paths the app has then crash reporting is not called the only one`() {
        assertFalse(security.contains("the only path that can transmit anything off-device"))
    }

    @Test
    fun `given the foss build's dependencies then no document says it carries no Firebase code`() {
        // MediaPipe brings Google's data-transport libraries and Firebase's encoders
        // into both flavours; foss removes the components that could send anything.
        assertFalse(security.contains("It ships no Firebase/Google dependency"))
        assertFalse(faq.contains("zero proprietary dependencies, built for F-Droid: no Firebase,"))
        assertTrue(security.contains("the components that could send anything"))
    }

    @Test
    fun `given what Crashlytics attaches then the documents name its installation identifier`() {
        assertTrue(privacy.contains("installation identifier"))
        assertTrue(security.contains("installation identifier"))
    }

    @Test
    fun `given attachments stay on the device then the privacy policy never sends one to a cloud step`() {
        // An image is delivered only to an on-device LITE_RT node; CloudLlmNodeExecutor
        // ignores the delivery channel, and a voice message is transcribed on the device.
        assertFalse(privacy.contains("and any attachments it consumes"))
        assertFalse(privacy.contains("unless a cloud node in your pipeline consumes them"))
        assertTrue(privacy.contains("never sent to a cloud provider"))
    }

    @Test
    fun `given the browser editor parses the same untrusted format then the threat model names it`() {
        assertTrue(security.contains("BrowserEditorImportParityGuard"))
    }

    private fun document(path: String): String {
        val working = File("").absoluteFile
        val root = if (File(working, "PRIVACY.md").isFile) working else working.parentFile
        return File(root, path).readText().replace(Regex("""\s+"""), " ")
    }
}
