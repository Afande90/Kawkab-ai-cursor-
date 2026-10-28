package app.kawkab.android.domain.constants

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ModelDiscoveryConstants.isHuggingFaceUrl]: an address on the Hub itself over `https` — never a
 * look-alike host, a CDN link or cleartext — including one built from a file name a strict URI
 * parser would reject.
 */
class ModelDiscoveryConstantsTest {

    @Test
    fun `given addresses when checked then only https huggingface co addresses are on the Hub`() {
        val answers = mapOf(
            "https://huggingface.co/org/repo/resolve/main/model.litertlm" to true,
            "HTTPS://HuggingFace.co/org/repo/resolve/main/model.litertlm" to true,
            "https://huggingface.co/org/repo/resolve/main/my model.litertlm" to true,
            ModelDiscoveryConstants.resolveUrl("org/repo", "model.litertlm") to true,
            "http://huggingface.co/org/repo/resolve/main/model.litertlm" to false,
            "https://cdn-lfs.hf.co/repos/ab/cd/model.litertlm?X-Amz-Signature=x" to false,
            "https://cas-bridge.xethub.hf.co/xet-bridge-us/model" to false,
            "https://huggingface.co.example.com/org/repo" to false,
            "https://example.com/huggingface.co/model.litertlm" to false,
            "https://hf.co/org/repo" to false,
            "https://huggingface.com/org/repo" to false,
            "not a url" to false,
            "" to false,
        )

        answers.forEach { (url, expected) ->
            assertEquals(url, expected, ModelDiscoveryConstants.isHuggingFaceUrl(url))
        }
    }
}
