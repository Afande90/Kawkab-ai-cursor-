package app.kawkab.android.crash

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import app.kawkab.android.BuildConfig
import app.kawkab.android.domain.crash.CrashRecord
import app.kawkab.android.domain.crash.CrashRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * The on-device crash log and the safe-mode start (tasks 32 and 33). Nothing is uploaded: the owner
 * copies the report from About and pastes it himself.
 *
 * Java crashes are caught by an uncaught-exception handler. Native crashes (a sherpa-onnx or
 * llama.cpp library) and ANRs never reach it, so they are read back on the next start from
 * Android's own record of how the process ended.
 */
object CrashLog {
    private const val FILE = "crash_log"
    private const val LOG = "log"
    private const val LAST_START = "last_start"
    private const val LAST_CRASH = "last_crash"
    private const val EXIT_SEEN = "exit_seen"

    @Volatile
    private var safe = false

    /** Whether this run started in safe mode: wake word and accessibility work stay off. */
    val safeMode: Boolean get() = safe

    /**
     * Called first in `Application.onCreate`: records native crashes of earlier runs, decides safe
     * mode, notes this start and installs the handler for Java crashes.
     *
     * @param context The application.
     */
    fun start(context: Context) {
        val prefs = prefs(context)
        // The wake process runs Application.onCreate too; only the app process decides safe mode.
        if (Application.getProcessName() != context.packageName) {
            installHandler(prefs, mainProcess = false)
            return
        }
        readExits(context, prefs)
        val lastStart = prefs.getLong(LAST_START, -1L).takeIf { it >= 0 }
        val lastCrash = prefs.getLong(LAST_CRASH, -1L).takeIf { it >= 0 }
        safe = CrashRules.safeMode(lastStart, lastCrash)
        prefs.edit(commit = true) { putLong(LAST_START, System.currentTimeMillis()) }
        installHandler(prefs, mainProcess = true)
    }

    private fun installHandler(prefs: SharedPreferences, mainProcess: Boolean) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                record(prefs, CrashRecord(System.currentTimeMillis(), "java", CrashRules.trace(error)), mainProcess)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** @return The crashes, newest first. */
    fun entries(context: Context): List<CrashRecord> = decode(prefs(context).getString(LOG, null))

    /** @return The text "Copy crash report" copies. */
    fun report(context: Context): String = CrashRules.report(
        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
        device = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            "${Build.MANUFACTURER} ${Build.MODEL}",
        log = entries(context),
    )

    private fun readExits(context: Context, prefs: SharedPreferences) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val seen = prefs.getLong(EXIT_SEEN, 0L)
        val exits = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, EXITS_READ) }
            .getOrDefault(emptyList())
            // The wake process too: its native crashes are the ones the owner hit (M1.8).
            .filter { it.timestamp > seen && it.processName.startsWith(context.packageName) }
        exits.sortedBy { it.timestamp }.forEach { exit ->
            val kind = when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
                ApplicationExitInfo.REASON_ANR -> "anr"
                // A Java crash was already written by the handler, with its frames.
                else -> return@forEach
            }
            val raw = runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val trace = raw?.let(CrashRules::nativeTrace)?.ifBlank { null } ?: exit.description.orEmpty()
            // Only the app process's own crash makes the next start a safe one.
            record(
                prefs,
                CrashRecord(exit.timestamp, kind, trace),
                mainProcess =
                exit.processName == context.packageName,
            )
        }
        exits.maxOfOrNull { it.timestamp }?.let { newest -> prefs.edit { putLong(EXIT_SEEN, newest) } }
    }

    private fun record(prefs: SharedPreferences, record: CrashRecord, mainProcess: Boolean) {
        val log = CrashRules.add(decode(prefs.getString(LOG, null)), record)
        // commit, not apply: the process is about to die.
        prefs.edit(commit = true) {
            putString(LOG, encode(log))
            if (mainProcess) putLong(LAST_CRASH, record.atEpochMs)
        }
    }

    private fun encode(log: List<CrashRecord>): String = JSONArray().apply {
        log.forEach { put(JSONObject().put("at", it.atEpochMs).put("kind", it.kind).put("trace", it.trace)) }
    }.toString()

    private fun decode(raw: String?): List<CrashRecord> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CrashRecord(item.getLong("at"), item.getString("kind"), item.getString("trace"))
        }
    }.getOrDefault(emptyList())

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val EXITS_READ = 10
}
