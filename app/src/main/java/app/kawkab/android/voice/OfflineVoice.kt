package app.kawkab.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.kawkab.android.domain.text.ReplyLanguage
import java.util.Locale

/**
 * Android text-to-speech with offline voices, in the reply's language (task 23l).
 *
 * @property context Any context.
 */
class OfflineVoice(context: Context) {
    private var ready = false
    private var onDone: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    onDone?.invoke()
                }

                @Deprecated("Deprecated in the platform; still called on older phones.")
                override fun onError(utteranceId: String?) {
                    onDone?.invoke()
                }
            },
        )
    }

    /**
     * Speaks [text]; [done] runs when it ends or cannot be spoken.
     *
     * @param text What to say.
     * @param language The reply's language.
     * @param done Called on the TTS thread when speech ends.
     */
    fun speak(text: String, language: ReplyLanguage, done: () -> Unit) {
        onDone = done
        if (!ready) {
            done()
            return
        }
        tts.language = localeFor(language)
        // Offline voices only: a network voice would send the reply off the phone.
        tts.voices?.firstOrNull { it.locale.language == tts.language?.language && !it.isNetworkConnectionRequired }
            ?.let { tts.voice = it }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    /** Stops speaking at once (barge-in or the stop tap). */
    fun stop() {
        onDone = null
        tts.stop()
    }

    /** Releases the engine. */
    fun shutdown() {
        stop()
        tts.shutdown()
    }

    /** The voice locale for a reply language. */
    companion object {
        private const val UTTERANCE = "kawkab-reply"

        /**
         * @param language The reply's language.
         * @return Arabic for Sudanese and MSA, English otherwise.
         */
        fun localeFor(language: ReplyLanguage): Locale = when (language) {
            ReplyLanguage.SUDANESE, ReplyLanguage.MSA -> Locale.forLanguageTag("ar")
            ReplyLanguage.ENGLISH -> Locale.US
        }
    }
}
