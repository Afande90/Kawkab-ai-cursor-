package app.kawkab.android.voice

import android.content.Context
import android.util.AtomicFile
import app.kawkab.android.domain.voice.OwnerVoicePolicy
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Offline sherpa-onnx speaker verification. Recordings and embeddings are excluded from every backup. */
class OwnerVoice(private val context: Context) {
    private val directory get() = File(context.noBackupFilesDir, "owner_voice")
    private val profile get() = File(directory, "profile.json")

    /** True only after all ten wake repetitions and both sentences were accepted. */
    fun enrolled(): Boolean = readProfile() != null

    /** Extracts a speaker embedding off the main thread; callers contain native failures. */
    fun embedding(samples: FloatArray): FloatArray {
        require(OwnerVoicePolicy.usable(samples)) { "Speak clearly for at least one second." }
        val extractor = SpeakerEmbeddingExtractor(
            context.assets,
            SpeakerEmbeddingExtractorConfig(model = "voice/speaker.onnx", numThreads = 1, provider = "cpu"),
        )
        return try {
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, RATE)
                stream.inputFinished()
                check(extractor.isReady(stream)) { "Not enough speech. Please try again." }
                extractor.compute(stream)
            } finally {
                stream.release()
            }
        } finally {
            extractor.release()
        }
    }

    /** Every wake and every final voice command passes this check; unknown voices are ignored. */
    fun accepts(samples: FloatArray): Boolean = runCatching {
        val owner = readProfile() ?: return false
        OwnerVoicePolicy.matches(embedding(samples), owner)
    }.getOrDefault(false)

    /** Commit a complete, consistent enrollment atomically; an interrupted enrollment keeps the old one. */
    fun enroll(recordings: List<FloatArray>) {
        require(OwnerVoicePolicy.enrollmentComplete(recordings.size)) { "Record ten wake words and two sentences." }
        val vectors = recordings.map(::embedding)
        val first = vectors.first()
        require(vectors.all { OwnerVoicePolicy.matches(it, first) }) { "Use the same voice for every recording." }
        val centroid = FloatArray(first.size) { i -> vectors.map { it[i] }.average().toFloat() }
        require(
            vectors.all {
                OwnerVoicePolicy.matches(it, centroid)
            },
        ) { "Please redo the enrollment in a quiet place." }
        directory.mkdirs()
        recordings.forEachIndexed { i, pcm ->
            File(directory, "sample-$i.pcm").outputStream().use { output ->
                pcm.forEach { value ->
                    val sample = (value.coerceIn(-1f, 1f) * PCM_SCALE).toInt()
                    output.write(sample and BYTE_MASK)
                    output.write((sample shr BYTE_BITS) and BYTE_MASK)
                }
            }
        }
        val json = JSONObject().put("version", 1).put("samples", recordings.size)
            .put("embedding", JSONArray(centroid.map { it.toDouble() }))
        val atomic = AtomicFile(profile)
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray())
            atomic.finishWrite(stream)
        } catch (
            error: Exception,
        ) {
            atomic.failWrite(stream)
            throw error
        }
    }

    /** Forget is called only after a fresh verified spoken yes. */
    fun forget() {
        directory.deleteRecursively()
    }

    private fun readProfile(): FloatArray? = runCatching {
        val json = JSONObject(AtomicFile(profile).readFully().toString(Charsets.UTF_8))
        if (!OwnerVoicePolicy.enrollmentComplete(json.getInt("samples"))) return null
        val values = json.getJSONArray("embedding")
        FloatArray(values.length()) { values.getDouble(it).toFloat() }
    }.getOrNull()

    private companion object {
        const val RATE = 16_000
        const val PCM_SCALE = 32767
        const val BYTE_MASK = 255
        const val BYTE_BITS = 8
    }
}
