package app.kawkab.android.voice

import android.content.Context
import app.kawkab.android.domain.voice.WakeAssessment
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate

/** Private score-only journal, excluded from backups; enrollment and microphone audio are never copied here. */
class WakeScoreLog(context: Context) {
    private val directory = File(context.noBackupFilesDir, "wake_scores")

    /** The origin is an enum so no transcript, contact or arbitrary text can enter the journal. */
    enum class Source {
        /** Speech windows from the battery-aware service. */
        BACKGROUND,

        /** One fresh, non-actioning arm's-length test attempt. */
        TEST,
    }

    /** Appends under an OS file lock because the foreground test and wake service use different processes. */
    fun record(source: Source, score: WakeAssessment, nowMs: Long = System.currentTimeMillis()) {
        runCatching {
            directory.mkdirs()
            val row = JSONObject().put("time_ms", nowMs).put("source", source.name)
                .put("personal", score.personal).put("generic", score.generic)
                .put("generic_threshold", score.genericThreshold).put("sherpa", score.sherpa)
                .put("speaker", score.speaker ?: JSONObject.NULL).put("templates", score.templates)
                .put("outcome", score.outcome)
            synchronized(WRITE_LOCK) {
                RandomAccessFile(File(directory, "${LocalDate.now()}.jsonl"), "rw").use { file ->
                    file.channel.lock().use {
                        file.seek(file.length())
                        file.write((row.toString() + "\n").toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }.onFailure { Timber.w("Could not save a private wake score (%s)", it.javaClass.simpleName) }
    }

    /** Fresh cross-process reads for Voice settings; all historical files remain on the phone. */
    fun recent(limit: Int = DISPLAY_LIMIT): List<WakeAssessment> = directory.listFiles().orEmpty()
        .filter { it.extension == "jsonl" }.sortedByDescending { it.name }
        .asSequence().flatMap { file ->
            runCatching {
                file.useLines { lines ->
                    val tail = ArrayDeque<String>()
                    lines.forEach { line ->
                        tail.addLast(line)
                        if (tail.size > limit) tail.removeFirst()
                    }
                    tail.reversed()
                }
            }.getOrDefault(emptyList()).asSequence()
        }.mapNotNull { line ->
            runCatching {
                val row = JSONObject(line)
                WakeAssessment(
                    row.getDouble("personal"),
                    row.getDouble("generic"),
                    row.getDouble("generic_threshold"),
                    row.getBoolean("sherpa"),
                    if (row.isNull("speaker")) null else row.getDouble("speaker"),
                    row.getInt("templates"),
                )
            }.getOrNull()
        }.take(limit.coerceAtLeast(0)).toList()

    private companion object {
        const val DISPLAY_LIMIT = 10
        val WRITE_LOCK = Any()
    }
}
