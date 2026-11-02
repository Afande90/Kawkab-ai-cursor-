package app.kawkab.android.voice

import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android must leave segment completion to the PCM endpoint, with the documented silence fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineSpeechRequestTest {
    @Test fun semanticEndpointOwnsTheExternalAudioSession() {
        val request = OfflineSpeech.request("en-US", controlledEndpoint = true)
        assertEquals(RecognizerIntent.EXTRA_AUDIO_SOURCE, request.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        assertEquals(15000L, request.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0))
    }

    @Test fun unsupportedSegmentedProvidersKeepThe700MillisecondFallback() {
        val request = OfflineSpeech.request("en-US")
        assertFalse(request.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        assertEquals(700L, request.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0))
    }
}
