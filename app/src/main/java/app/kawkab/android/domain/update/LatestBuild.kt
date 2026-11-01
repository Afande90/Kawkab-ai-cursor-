package app.kawkab.android.domain.update

import org.json.JSONArray
import org.json.JSONException

/**
 * The newest pre-release on the repository (task 25c).
 *
 * @property tag For example `build-20261101-abc1234`.
 * @property apkUrl Direct download link of its `.apk`.
 */
data class LatestBuild(val tag: String, val apkUrl: String) {
    /**
     * Whether this build is a different commit from the one installed.
     *
     * @param installedSha The installed build's commit, full or short.
     * @return `true` when the tag names another commit.
     */
    fun isNewerThan(installedSha: String): Boolean {
        val short = tag.substringAfterLast('-')
        return installedSha.isBlank() || !installedSha.startsWith(short)
    }

    /** Reading GitHub's answer. */
    companion object {
        private const val APK = ".apk"

        /**
         * @param releasesJson GitHub's `GET /repos/{owner}/{repo}/releases` answer.
         * @return The newest release that carries an `.apk`, or `null`.
         */
        fun parse(releasesJson: String): LatestBuild? = try {
            val releases = JSONArray(releasesJson)
            (0 until releases.length()).asSequence()
                .map { releases.getJSONObject(it) }
                .mapNotNull { release ->
                    val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                    val apk = (0 until assets.length()).asSequence()
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(APK) }
                        ?: return@mapNotNull null
                    LatestBuild(release.optString("tag_name"), apk.optString("browser_download_url"))
                }
                .firstOrNull()
        } catch (_: JSONException) {
            null
        }
    }
}
