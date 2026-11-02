package app.kawkab.android.voice

import android.content.Context
import app.kawkab.android.domain.voice.WakeFeatures
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/** Learned generic head plus the owner's ten few-shot keyword templates; sherpa remains a second opinion. */
class PersonalWakeDetector(private val context: Context) {
    private var version = Long.MIN_VALUE
    private var templates = emptyList<DoubleArray>()
    private val head = runCatching {
        JSONObject(context.assets.open("wake/custom_detector.json").bufferedReader().use { it.readText() })
            .also { require(it.getString("format") == "pocket-kws-v1") }
    }.getOrNull()

    /** Called on the wake worker, never on the UI thread. Speaker verification is still required after a hit. */
    fun hit(samples: FloatArray): Boolean {
        refresh()
        if (templates.isEmpty()) return false
        val features = WakeFeatures.encode(samples)
        val personal = templates.any { WakeFeatures.similarity(features, it) >= PERSONAL_SIMILARITY }
        return personal || generic(features)
    }

    private fun generic(features: DoubleArray): Boolean {
        val model = head ?: return false
        return runCatching {
            val weights = model.getJSONArray("weights")
            if (weights.length() != features.size) return false
            val score = features.indices.sumOf { weights.getDouble(it) * features[it] } + model.getDouble("bias")
            val scaled = (score / model.getDouble("temperature")).coerceIn(-LOGIT_CAP, LOGIT_CAP)
            1 / (1 + exp(-scaled)) >= model.getDouble("threshold")
        }.getOrDefault(false)
    }

    private fun refresh() {
        val directory = File(context.noBackupFilesDir, "owner_voice")
        val current = File(directory, "profile.json").lastModified()
        if (version == current) return
        version = current
        templates = if (current == 0L) {
            emptyList()
        } else {
            (0 until WAKE_CLIPS).mapNotNull { index ->
                runCatching {
                    val bytes = File(directory, "sample-$index.pcm").readBytes()
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    WakeFeatures.encode(FloatArray(bytes.size / 2) { buffer.short / PCM_SCALE })
                }.getOrNull()
            }
        }
    }

    private companion object {
        const val WAKE_CLIPS = 10
        const val PERSONAL_SIMILARITY = 0.90
        const val PCM_SCALE = 32768f
        const val LOGIT_CAP = 40.0
    }
}
