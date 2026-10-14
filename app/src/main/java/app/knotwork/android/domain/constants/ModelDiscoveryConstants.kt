package app.knotwork.android.domain.constants

import java.net.URI
import java.net.URISyntaxException

/**
 * Cross-layer constants for the model-discovery feature (browsing the
 * `litert-community` organisation on Hugging Face and installing a model's
 * `.litertlm` bundle through the existing download manager).
 *
 * Kept in the `domain` layer (pure Kotlin) so the repository, the mapper and
 * the use cases share one source of truth for the Hub host, the curated
 * organisation, the compatible-file extension and the URL shapes. The data
 * layer composes the actual HTTP calls; this object only owns the literals.
 */
object ModelDiscoveryConstants {

    /** Hugging Face Hub host (no trailing slash). */
    const val HF_BASE_URL: String = "https://huggingface.co"

    /**
     * Curated organisation browsed by the Discover screen. The official
     * LiteRT model hub publishes ready-to-run `.litertlm` bundles here, so
     * scoping discovery to this author keeps every result engine-compatible
     * without a brittle library-tag filter.
     */
    const val HF_LITERT_AUTHOR: String = "litert-community"

    /**
     * File extension of an engine-compatible LiteRT-LM bundle. A repository
     * may publish several (per-accelerator variants); only files ending in
     * this suffix are surfaced as installable.
     */
    const val LITERTLM_EXTENSION: String = ".litertlm"

    /** Default number of repositories requested from the list endpoint. */
    const val DEFAULT_SEARCH_LIMIT: Int = 30

    /**
     * Builds the direct download URL of a file inside a repository's `main`
     * revision. This is the `resolve` URL the [app.knotwork.android.domain
     * .repositories.ModelDownloadManager] streams from.
     *
     * @param repoId fully-qualified repository id (`"author/name"`).
     * @param fileName file inside the repo (e.g. `"gemma-4-E2B-it.litertlm"`).
     */
    fun resolveUrl(repoId: String, fileName: String): String = "$HF_BASE_URL/$repoId/resolve/main/$fileName"

    /**
     * Builds the human-facing model-card URL opened from the detail screen's
     * "View on Hugging Face" affordance.
     *
     * @param repoId fully-qualified repository id (`"author/name"`).
     */
    fun modelCardUrl(repoId: String): String = "$HF_BASE_URL/$repoId"

    /**
     * Whether [url] is Hugging Face itself over `https` — the one address a saved Hugging Face
     * token is sent to (the downloader applies the same rule to every request). A screen uses it
     * to tell "accept the licence and add your token" from a refusal no token can answer: a link
     * on another host, a signed CDN link included, never carries the token.
     *
     * @param url An address as stored or entered; one that does not parse is not Hugging Face.
     * @return `true` only for `https://huggingface.co/…`, in any letter case.
     */
    fun isHuggingFaceUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme.equals(HF_SCHEME, ignoreCase = true) && uri.host.equals(HF_HOST, ignoreCase = true)
    }

    /** Scheme of [HF_BASE_URL]. */
    private const val HF_SCHEME = "https"

    /** Host of [HF_BASE_URL]. */
    private const val HF_HOST = "huggingface.co"
}
