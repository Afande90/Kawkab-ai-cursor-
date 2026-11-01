package app.kawkab.android.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.kawkab.android.domain.voice.ConversationLoop
import java.util.Locale

/**
 * Android's speech recognizer, offline only (tasks 23c and 23l). Audio never goes to a cloud
 * recognizer: the on-device recognizer is used when the phone has one, and every request asks
 * for offline recognition.
 *
 * @property context Any context; the recognizer runs on the main thread.
 */
class OfflineSpeech(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    /** What one listen produced. */
    interface Listener {
        /** The owner started speaking. */
        fun onSpeechStart() = Unit

        /** The final transcript. */
        fun onText(text: String)

        /** No speech, no match, or an error; [code] is a `SpeechRecognizer.ERROR_*` value. */
        fun onNothing(code: Int)
    }

    /** Whether any recognizer exists on this phone. */
    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Listens once.
     *
     * @param languageTag For example "ar-SD" or "en-US".
     * @param listener Receives the result.
     */
    fun listen(languageTag: String, listener: Listener) {
        stop()
        val created = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = created
        created.setRecognitionListener(Bridge(listener))
        created.startListening(request(languageTag))
    }

    /** Stops listening and releases the recognizer. */
    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private class Bridge(private val listener: Listener) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = listener.onSpeechStart()
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) = listener.onNothing(error)
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) listener.onNothing(SpeechRecognizer.ERROR_NO_MATCH) else listener.onText(text)
        }
    }

    /** Language choice and the request. */
    companion object {
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
