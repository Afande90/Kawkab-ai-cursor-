package app.kawkab.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.kawkab.android.domain.text.ReplyLanguage
import app.kawkab.android.domain.text.SpokenSentences
import java.util.Locale

/**
 * Android text-to-speech with offline voices, in the reply's language (task 23l).
 *
 * @property context Any context.
 */
class OfflineVoice(context: Context) {
    private var ready = false
    private var initialized = false
    private var pendingSpeech: (() -> Unit)? = null
    private var speechRun = 0L
    private var onDone: (() -> Unit)? = null

    @Volatile
    private var lastUtterance: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        initialized = true
        ready = status == TextToSpeech.SUCCESS
        pendingSpeech?.also { pendingSpeech = null }?.invoke()
    }

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    // Each sentence is its own utterance; the reply is done after the last one.
                    if (utteranceId == lastUtterance) onDone?.invoke()
                }

                @Deprecated("Deprecated in the platform; still called on older phones.")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == lastUtterance) onDone?.invoke()
                }
            },
        )
    }

    /**
     * Speaks [text] sentence by sentence, so the first sentence starts at once (task 28); [done]
     * runs when the last one ends or cannot be spoken.
     *
     * @param text What to say.
     * @param language The reply's language.
     * @param done Called on the TTS thread when speech ends.
     */
    fun speak(text: String, language: ReplyLanguage, done: () -> Unit) {
        if (!initialized) {
            pendingSpeech = { speak(text, language, done) }
            return
        }
        onDone = done
        if (!ready) {
            done()
            return
        }
        tts.language = localeFor(language)
        // Offline voices only: a network voice would send the reply off the phone.
        val offline = tts.voices?.firstOrNull {
            it.locale.language == tts.language?.language && !it.isNetworkConnectionRequired
        }
        if (offline == null) {
            done()
            return
        }
        tts.voice = offline
        val sentences = SpokenSentences.split(text).ifEmpty { listOf(text) }
        val utterance = "$UTTERANCE-${++speechRun}"
        lastUtterance = "$utterance-${sentences.lastIndex}"
        sentences.forEachIndexed { index, sentence ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(sentence, mode, null, "$utterance-$index")
        }
    }

    /** Stops speaking at once (barge-in or the stop tap). */
    fun stop() {
        pendingSpeech = null
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
