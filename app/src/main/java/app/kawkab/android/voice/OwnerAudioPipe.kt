package app.kawkab.android.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import app.kawkab.android.domain.voice.TurnCompletionModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** One PCM stream feeds both Android's offline recognizer and the speaker verifier. Never stored. */
class OwnerAudioPipe(context: Context, turnModel: TurnCompletionModel? = null, onTurnEnd: () -> Unit = {}) :
    AutoCloseable {
    private val descriptors = ParcelFileDescriptor.createPipe()
    val source: ParcelFileDescriptor get() = descriptors[0]
    private val sink = ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1])
    private val bytes = ByteArrayOutputStream()
    private val closed = AtomicBoolean(false)
    private val turnEnd = turnModel?.let { model ->
        AudioTurnEnd(context.applicationContext.assets, model, ::samples) {
            finishInput()
            onTurnEnd()
        }
    }

    @Volatile private var running = false
    private val recorder: AudioRecord
    private var thread: Thread? = null

    init {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, BUFFER_BYTES),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED)
    }

    /** Starts capture only for the foreground listening session. */
    fun start() {
        running = true
        thread = Thread({
            val buffer = ByteArray(BUFFER_BYTES)
            try {
                turnEnd?.prepare()
                if (!running) return@Thread
                recorder.startRecording()
                turnEnd?.start()
                while (running && bytes.size() < MAX_BYTES) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count <= 0) break
                    synchronized(bytes) { bytes.write(buffer, 0, count) }
                    turnEnd?.accept(decode(buffer, count))
                    sink.write(buffer, 0, count)
                }
            } catch (_: Exception) {
                // Closing the input lets Android report its normal recognition error.
            } finally {
                running = false
                turnEnd?.finishCapture()
                runCatching { sink.close() }
            }
        }, "owner-audio").apply {
            isDaemon = true
            start()
        }
    }

    private fun finishInput() {
        running = false
        runCatching { recorder.stop() }
        runCatching { sink.close() }
    }

    /** A copy of exactly the audio supplied to recognition, for the independent speaker decision. */
    fun samples(): FloatArray {
        val pcm = synchronized(bytes) { bytes.toByteArray() }
        return decode(pcm, pcm.size)
    }

    private fun decode(pcm: ByteArray, count: Int): FloatArray = FloatArray(count / SAMPLE_BYTES) { i ->
        val sample = (pcm[i * SAMPLE_BYTES].toInt() and BYTE_MASK) or
            (pcm[i * SAMPLE_BYTES + 1].toInt() shl BYTE_BITS)
        sample.toShort() / PCM_SCALE
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        turnEnd?.close()
        running = false
        runCatching { recorder.stop() }
        runCatching { sink.close() }
        runCatching { source.close() }
        thread?.join(JOIN_MS)
        recorder.release()
    }

    companion object {
        const val RATE = 16_000
        private const val BUFFER_BYTES = 2048
        private const val MAX_BYTES = RATE * 2 * 15
        private const val SAMPLE_BYTES = 2
        private const val BYTE_MASK = 255
        private const val BYTE_BITS = 8
        private const val PCM_SCALE = 32768f
        private const val JOIN_MS = 250L
    }
}
