package app.kawkab.android.voice

import android.content.Context
import app.kawkab.android.domain.voice.PersonalWakeTemplates
import app.kawkab.android.domain.voice.WakeAssessment
import app.kawkab.android.domain.voice.WakeFeatures
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/** Learned generic head plus the owner's ten few-shot keyword templates; sherpa remains a second opinion. */
class PersonalWakeDetector(private val context: Context) {
    private var version = Long.MIN_VALUE
    private var templates = PersonalWakeTemplates(emptyList())
    private val head = runCatching {
        JSONObject(context.assets.open("wake/custom_detector.json").bufferedReader().use { it.readText() })
            .also { require(it.getString("format") == "pocket-kws-v1") }
    }.getOrNull()

    /** Called on the wake worker; no keyword opinion bypasses the separate owner speaker check. */
    fun score(samples: FloatArray, sherpa: Boolean = false): WakeAssessment {
        refresh()
        val features = WakeFeatures.encode(samples)
        return WakeAssessment(
            personal = templates.score(samples),
            generic = generic(features),
            genericThreshold = head?.optDouble("threshold", 1.0) ?: 1.0,
            sherpa = sherpa,
            templates = templates.count,
        )
    }

    /** Compatibility entry point; callers must still verify the speaker after a keyword hit. */
    fun hit(samples: FloatArray): Boolean = score(samples).keyword

    private fun generic(features: DoubleArray): Double {
        val model = head ?: return 0.0
        return runCatching {
            val weights = model.getJSONArray("weights")
            if (weights.length() != features.size) return 0.0
            val score = features.indices.sumOf { weights.getDouble(it) * features[it] } + model.getDouble("bias")
            val temperature = model.getDouble("temperature")
            if (!temperature.isFinite() || temperature <= 0) return 0.0
            val scaled = (score / temperature).coerceIn(-LOGIT_CAP, LOGIT_CAP)
            (1 / (1 + exp(-scaled))).takeIf { it.isFinite() } ?: 0.0
        }.getOrDefault(0.0)
    }

    private fun refresh() {
        val directory = File(context.noBackupFilesDir, "owner_voice")
        val current = File(directory, "profile.json").lastModified()
        if (version == current) return
        version = current
        templates = PersonalWakeTemplates(
            if (current == 0L) {
                emptyList()
            } else {
                (0 until WAKE_CLIPS).mapNotNull { index ->
                    runCatching {
                        val bytes = File(directory, "sample-$index.pcm").readBytes()
                        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        FloatArray(bytes.size / 2) { buffer.short / PCM_SCALE }
                    }.getOrNull()
                }
            },
        )
    }

    private companion object {
        const val WAKE_CLIPS = 10
        const val PCM_SCALE = 32768f
        const val LOGIT_CAP = 40.0
    }
}
