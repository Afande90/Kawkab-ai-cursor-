package app.kawkab.android.assistant

import android.content.Intent
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import dagger.hilt.android.AndroidEntryPoint

/**
 * Satisfies the assistant service contract without sending speech off the phone.
 */
@AndroidEntryPoint
class KawkabRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        try {
            listener.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (ignored: RemoteException) {
            // The caller is already gone, so there is no speech session to finish.
        }
    }

    override fun onCancel(listener: Callback) = Unit

    override fun onStopListening(listener: Callback) = Unit
}
