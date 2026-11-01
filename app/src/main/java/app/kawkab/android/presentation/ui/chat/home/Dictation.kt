package app.kawkab.android.presentation.ui.chat.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.SpeechRecognizer
import app.kawkab.android.R
import app.kawkab.android.voice.OfflineSpeech
import java.util.Locale

/**
 * The composer mic before the M4 voice stack (task 23c): Android's recognizer, offline only, and
 * the transcript goes into the input box so the owner can correct it before sending. It works
 * whatever brain is active, because no model transcribes.
 *
 * @property context Any context.
 * @property insert Puts the transcript into the input box.
 * @property hint Shows a one-line message, with an optional action label and action.
 */
class Dictation(
    private val context: Context,
    private val insert: (String) -> Unit,
    private val hint: (message: String, actionLabel: String?, action: (() -> Unit)?) -> Unit,
) {
    private val speech = OfflineSpeech(context)

    /** Listens once. With no recognizer on the phone, it says so in one line instead. */
    fun start() {
        if (!speech.available()) {
            hint(context.getString(R.string.dictation_no_recognizer), null, null)
            return
        }
        val language = OfflineSpeech.languageFor(Locale.getDefault())
        speech.listen(
            language,
            object : OfflineSpeech.Listener {
                override fun onText(text: String) {
                    insert(text)
                    speech.stop()
                }

                override fun onNothing(code: Int) {
                    speech.stop()
                    when (DictationOutcome.of(code)) {
                        DictationOutcome.LANGUAGE_MISSING -> hint(
                            context.getString(R.string.dictation_language_missing),
                            context.getString(R.string.dictation_download),
                        ) { downloadLanguage(language) }
                        DictationOutcome.NOTHING_HEARD -> hint(
                            context.getString(R.string.dictation_nothing),
                            null,
                            null,
                        )
                        DictationOutcome.FAILED -> hint(context.getString(R.string.dictation_failed), null, null)
                    }
                }
            },
        )
    }

    /** Stops listening. */
    fun stop() = speech.stop()

    /** Offers the offline language download: the on-device recognizer's own, else the voice settings. */
    private fun downloadLanguage(language: String) {
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer.triggerModelDownload(OfflineSpeech.request(language))
            recognizer.destroy()
            return
        }
        runCatching {
            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** What an empty recognizer result means for the owner. */
enum class DictationOutcome {
    /** The offline model for this language is not on the phone. */
    LANGUAGE_MISSING,

    /** Silence, or nothing matched. */
    NOTHING_HEARD,

    /** Anything else. */
    FAILED,
    ;

    /** Mapping from the recognizer's error codes. */
    companion object {
        /**
         * @param code A `SpeechRecognizer.ERROR_*` value.
         * @return The outcome.
         */
        fun of(code: Int): DictationOutcome = when (code) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> LANGUAGE_MISSING
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NOTHING_HEARD
            else -> FAILED
        }
    }
}
