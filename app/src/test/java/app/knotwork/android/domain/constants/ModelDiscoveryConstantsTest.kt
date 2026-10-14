package app.knotwork.android.domain.constants

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ModelDiscoveryConstants.isHuggingFaceUrl]: Hugging Face itself over `https`, the one address the
 * downloader sends a saved token to — never a look-alike host, a CDN link or cleartext.
 */
class ModelDiscoveryConstantsTest {

    @Test
    fun `given addresses when checked then only https huggingface co is Hugging Face`() {
        val answers = mapOf(
            "https://huggingface.co/org/repo/resolve/main/model.litertlm" to true,
            "HTTPS://HuggingFace.co/org/repo/resolve/main/model.litertlm" to true,
            "https://huggingface.co" to true,
            "http://huggingface.co/org/repo/resolve/main/model.litertlm" to false,
            "https://cdn-lfs.hf.co/repos/ab/cd/model.litertlm?X-Amz-Signature=x" to false,
            "https://cas-bridge.xethub.hf.co/xet-bridge-us/model" to false,
            "https://huggingface.co.example.com/org/repo" to false,
            "https://example.com/huggingface.co/model.litertlm" to false,
            "https://hf.co/org/repo" to false,
            "not a url" to false,
            "https://exa mple.com/x" to false,
            "" to false,
        )

        answers.forEach { (url, expected) ->
            assertEquals(url, expected, ModelDiscoveryConstants.isHuggingFaceUrl(url))
        }
    }
}
