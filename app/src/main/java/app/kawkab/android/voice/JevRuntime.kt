package app.kawkab.android.voice

/** Verdict-slot readout using Llamatik's exact native runtime, separate from generated speech. */
object JevRuntime {
    /** Optional native capability: failed loading must ask, never fall back to invented confidence. */
    val available: Boolean = runCatching {
        System.loadLibrary("kawkab_decision")
        true
    }.getOrDefault(false)

    /** Blocking CPU inference; the caller runs this off the UI thread. */
    external fun scores(path: String, state: String, question: String, options: Array<String>): DoubleArray

    /** Release the cached judge before loading a language or image model. */
    external fun release()
}
