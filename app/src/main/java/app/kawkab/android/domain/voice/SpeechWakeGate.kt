package app.kawkab.android.domain.voice

/** Personal keyword features and speaker verification are never evaluated outside VAD speech windows. */
class SpeechWakeGate(private val personal: (FloatArray) -> Boolean, private val speaker: (FloatArray) -> Boolean) {
    private var tick = 0

    /** A sherpa hit is a second opinion; every accepted keyword still needs the owner's speaker match. */
    fun feed(speechSegment: Boolean, keyword: Boolean, samples: () -> FloatArray): Boolean {
        if (!speechSegment) {
            tick = 0
            return false
        }
        tick = (tick + 1) % PERSONAL_FRAME_INTERVAL
        if (!keyword && tick != 0) return false
        val speech = samples()
        return (keyword || personal(speech)) && speaker(speech)
    }

    private companion object {
        const val PERSONAL_FRAME_INTERVAL = 8
    }
}
