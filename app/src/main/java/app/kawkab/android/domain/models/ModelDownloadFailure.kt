package app.kawkab.android.domain.models

/**
 * Why a model download ended without installing a model, where a screen has to say more than
 * "it failed".
 *
 * A download's message is free text — an HTTP status, a transport error, or a sentence the
 * download worker wrote — so a screen that words the outcome itself branches on this instead of
 * parsing the text.
 */
enum class ModelDownloadFailure {

    /**
     * The file arrived, but its SHA-256 differs from the one its source published for it. The
     * file was deleted and no model was registered: what arrived is not the file the source
     * offers.
     */
    CHECKSUM_MISMATCH,

    /**
     * The download stopped at the foreground time it may spend in the background, short of
     * Android's daily limit. The bytes so far are kept; starting it again continues from them.
     */
    PAUSED_TIME_LIMIT,

    /** Anything else: an HTTP status, a transport error, or a file that could not be stored. */
    OTHER,
}
