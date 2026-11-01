package app.kawkab.android.data.update

import app.kawkab.android.domain.repositories.NetworkActivityTracker
import app.kawkab.android.domain.update.LatestBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Asks GitHub for the newest Kawkab pre-release (task 25c). Only when the owner taps "Check for
 * updates" on About; never automatically (spec §0.2). It sends an anonymous request and nothing
 * about the phone or its data (PRIVACY.md §3.10).
 */
class UpdateChecker @Inject constructor(private val activity: NetworkActivityTracker) {
    /**
     * @return The newest build with an APK, or `null` when GitHub cannot be reached.
     */
    suspend fun latest(): LatestBuild? = withContext(Dispatchers.IO) {
        try {
            activity.recordOutbound()
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                LatestBuild.parse(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Afande90/Kawkab-ai-cursor-/releases?per_page=10"
        const val TIMEOUT_MS = 15_000
    }
}
