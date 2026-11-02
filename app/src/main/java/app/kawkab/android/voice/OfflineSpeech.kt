package app.kawkab.android.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.kawkab.android.domain.voice.ConversationLoop
import app.kawkab.android.domain.voice.SpokenOwnerAnswer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Android's speech recognizer, offline only (tasks 23c and 23l). Audio never goes to a cloud
 * recognizer: the on-device recognizer is used when the phone has one, and every request asks
 * for offline recognition.
 *
 * @property context Any context; the recognizer runs on the main thread.
 */
class OfflineSpeech(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var audio: OwnerAudioPipe? = null
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0

    /** What one listen produced. */
    interface Listener {
        /** The owner started speaking. */
        fun onSpeechStart() = Unit

        /** The final transcript. */
        fun onText(text: String)

        /** A partial transcript, used to stop a countdown without waiting for end of speech. */
        fun onPartialText(text: String) = Unit

        /** No speech, no match, or an error; [code] is a `SpeechRecognizer.ERROR_*` value. */
        fun onNothing(code: Int)
    }

    /** Whether any recognizer exists on this phone. */
    fun available(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * Listens once.
     *
     * @param languageTag For example "ar-SD" or "en-US".
     * @param listener Receives the result.
     */
    fun listen(languageTag: String, listener: Listener) {
        stop()
        WakeWordService.pause(context)
        if (!available() || !OwnerVoice(context).enrolled()) {
            listener.onNothing(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            return
        }
        val created = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = created
        val current = generation
        runCatching {
            val pipe = OwnerAudioPipe(context)
            audio = pipe
            created.setRecognitionListener(
                Bridge(object : Listener {
                    override fun onSpeechStart() = Unit
                    override fun onNothing(code: Int) {
                        if (current == generation) listener.onNothing(code)
                    }
                    override fun onPartialText(text: String) {
                        // A partial cancel also needs an owner match. No model runs on the UI thread.
                        verifyOwner(pipe.samples(), current) {
                            listener.onSpeechStart()
                            listener.onPartialText(text)
                        }
                    }
                    override fun onText(text: String) {
                        val samples = pipe.samples()
                        runCatching { pipe.close() }
                        audio = null
                        verifyOwner(samples, current) {
                            SpokenOwnerAnswer.record(text)
                            listener.onText(text)
                        }
                    }
                }),
            )
            val intent = request(languageTag).apply {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe.source)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, OwnerAudioPipe.RATE)
            }
            created.startListening(intent)
            pipe.start()
        }.onFailure { listener.onNothing(SpeechRecognizer.ERROR_AUDIO) }
    }

    /** Stops listening and releases the recognizer. */
    fun stop() {
        generation += 1
        audio?.let { runCatching { it.close() } }
        audio = null
        recognizer?.destroy()
        recognizer = null
        WakeWordService.resume(context)
    }

    private fun verifyOwner(samples: FloatArray, current: Int, accepted: () -> Unit) {
        verifier.execute {
            if (current == generation && OwnerVoice(context).accepts(samples)) {
                main.post { if (current == generation) accepted() }
            }
            // A different speaker produces no transcript, no action and no reply.
        }
    }

    private class Bridge(private val listener: Listener) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = listener.onSpeechStart()
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) = listener.onNothing(error)
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.let(listener::onPartialText)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) listener.onNothing(SpeechRecognizer.ERROR_NO_MATCH) else listener.onText(text)
        }
    }

    /** Language choice and the request. */
    companion object {
        private val verifier = Executors.newSingleThreadExecutor()

        /** Sudanese Arabic first, then Arabic, then English. */
        val LANGUAGES: List<String> = listOf("ar-SD", "ar", "en-US")

        /**
         * The language to listen in: Arabic (Sudanese first) on an Arabic phone, else English.
         *
         * @param locale The phone's language.
         * @return A BCP 47 tag.
         */
        fun languageFor(locale: Locale): String = if (locale.language == "ar") LANGUAGES.first() else LANGUAGES.last()

        /**
         * The recognizer request: offline, the given language, one final result.
         *
         * @param languageTag The language.
         * @return The intent.
         */
        fun request(languageTag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // End of turn after about 700 ms of silence (task 28). Recognizers may round it.
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                ConversationLoop.END_OF_TURN_SILENCE_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                ConversationLoop.END_OF_TURN_SILENCE_MS,
            )
        }
    }
}
