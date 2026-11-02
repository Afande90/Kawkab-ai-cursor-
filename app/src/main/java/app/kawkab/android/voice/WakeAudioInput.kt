package app.kawkab.android.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import app.kawkab.android.domain.voice.WakeWord
import java.util.concurrent.atomic.AtomicBoolean

/** The only wake recorder factory; failed initialization also releases its native audio resource. */
object WakeAudioInput {
    private const val BUFFER_FRAMES = 8
    private const val BYTES_PER_SAMPLE = 2

    /** Open only on the wake worker, after policy permits listening. */
    fun open(context: Context): WakeCapture? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        var record: AudioRecord? = null
        return try {
            val minimum = AudioRecord.getMinBufferSize(WakeWord.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, WakeWord.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, WakeWord.FRAME_SAMPLES * BUFFER_FRAMES * BYTES_PER_SAMPLE))
            record = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            Capture(audio)
        } catch (_: Exception) {
            record?.release()
            null
        }
    }

    private class Capture(private val record: AudioRecord) : WakeCapture {
        private val closed = AtomicBoolean(false)

        override fun read(pcm: ShortArray): Int = try {
            record.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
        } catch (_: IllegalStateException) {
            AudioRecord.ERROR_INVALID_OPERATION
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                runCatching { record.stop() }
            } finally {
                record.release()
            }
        }
    }
}
