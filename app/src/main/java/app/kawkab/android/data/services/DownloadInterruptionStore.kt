package app.kawkab.android.data.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.kawkab.android.domain.models.ModelDownloadFailure
import app.kawkab.android.domain.repositories.InterruptedDownload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the model downloads that stopped without a model for a reason the user has to be
 * told, until a screen has told them.
 *
 * A download outlives the screen that started it, and a pause at the background time limit comes
 * after hours out of sight — so the reason must outlive the worker that reported it, and the app
 * process too. WorkManager keeps a finished work's output for a while, but may prune it whenever
 * it likes; this store keeps a record until a screen shows it, the download is started again, or
 * it is cancelled.
 *
 * One record per file name, in a plain preferences file of its own: the file name, the address
 * it downloads from (what starting it again needs, query string included), why it stopped, the
 * sentence that says so and when. WorkManager keeps the same address in the clear in its own
 * record of the download; this copy lasts longer — until a screen shows it, or the download is
 * started again or cancelled.
 *
 * @param preferences Opens the preferences file; a parameter only so a test can supply its own.
 * @param clock Wall-clock time in epoch milliseconds, which orders the records; a parameter only
 *   so a test can set it.
 */
@Singleton
class DownloadInterruptionStore internal constructor(
    private val preferences: () -> SharedPreferences,
    private val clock: () -> Long,
) {

    /**
     * Production constructor: the app's own preferences file for interruptions.
     *
     * Kept separate from the primary constructor because Hilt rejects a class with two
     * `@Inject` constructors.
     *
     * @param context Application context the preferences file is opened from.
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        preferences = { context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) },
        clock = System::currentTimeMillis,
    )

    /**
     * Records why the download of [fileName] stopped, replacing an earlier record for the file.
     *
     * @param fileName Local file name the download was started with.
     * @param url The address it downloads from.
     * @param failure Why it stopped.
     * @param message The sentence the download reported it with.
     */
    fun record(fileName: String, url: String, failure: ModelDownloadFailure, message: String) {
        val value = JSONObject()
            .put(FIELD_URL, url)
            .put(FIELD_FAILURE, failure.name)
            .put(FIELD_MESSAGE, message)
            .put(FIELD_AT, clock())
            .toString()
        // Written at once, not later: the caller is the download worker, off the main thread, and
        // the failure it returns next may be followed by the process ending before a later write.
        preferences().edit(commit = true) { putString(fileName, value) }
    }

    /**
     * Removes the record for [fileName], if any.
     *
     * @param fileName Local file name the download was started with.
     */
    fun clear(fileName: String) {
        preferences().edit { remove(fileName) }
    }

    /**
     * The recorded interruptions, now and after every change, oldest first.
     *
     * @return A [Flow] of the records; a record that cannot be read is left out.
     */
    fun observe(): Flow<List<InterruptedDownload>> = callbackFlow {
        val prefs = preferences()
        // Held here: SharedPreferences keeps its listeners weakly, and only this flow refers to it.
        // It sends a signal, not a list: every list is read in turn below, so a change landing
        // between the first read and the first send cannot be overtaken by the older list.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .conflate()
        .map { read(preferences()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /** Every readable record in [prefs], oldest first. */
    private fun read(prefs: SharedPreferences): List<InterruptedDownload> = prefs.all
        .mapNotNull { (fileName, value) -> (value as? String)?.let { parse(fileName, it) } }
        .sortedBy { it.second }
        .map { it.first }

    /** One record with the time it was made, or `null` when it is not one this build can read. */
    private fun parse(fileName: String, value: String): Pair<InterruptedDownload, Long>? = try {
        val json = JSONObject(value)
        val failure = ModelDownloadFailure.entries.firstOrNull { it.name == json.optString(FIELD_FAILURE) }
        val url = json.optString(FIELD_URL)
        if (failure == null || url.isEmpty()) {
            null
        } else {
            InterruptedDownload(
                fileName = fileName,
                url = url,
                failure = failure,
                message = json.optString(FIELD_MESSAGE),
            ) to json.optLong(FIELD_AT)
        }
    } catch (e: JSONException) {
        // Type only: on Android a JSONException message quotes its input, and that holds an address.
        Timber.w("Skipping an unreadable download interruption record (%s)", e.javaClass.simpleName)
        null
    }

    internal companion object {
        /** The preferences file the records live in. */
        const val PREFERENCES_NAME = "model_download_interruptions"

        private const val FIELD_URL = "url"
        private const val FIELD_FAILURE = "failure"
        private const val FIELD_MESSAGE = "message"
        private const val FIELD_AT = "at"
    }
}
