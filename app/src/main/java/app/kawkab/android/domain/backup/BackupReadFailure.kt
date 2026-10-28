package app.kawkab.android.domain.backup

import java.io.IOException

/**
 * Why an archive could not be read. Each value is something the user is told in its own words, and
 * none of them leaves anything on the phone changed.
 */
enum class BackupReadFailure {

    /** The file does not start like a backup made by Knotwork. */
    NOT_A_BACKUP,

    /** The file was written in a newer format version than this build reads. */
    UNSUPPORTED_FORMAT_VERSION,

    /** The header asks for key-derivation costs above the bounds a reader accepts. */
    TOO_DEMANDING,

    /** The password does not open the archive: the header's authentication code does not match. */
    WRONG_PASSWORD,

    /** Part of the file does not authenticate, is out of order, or follows the last chunk. */
    DAMAGED,

    /** The file ends before its last chunk: it may not have finished copying. */
    TRUNCATED,
}

/**
 * Thrown while reading an archive when it cannot be read for [failure].
 *
 * It is an [IOException] so that it travels through stream code unchanged; callers branch on
 * [failure], never on the message, which is for logs only and carries no content of the archive.
 *
 * @property failure Why the archive could not be read.
 * @property writerVersionName The version name of the build that wrote the archive, when the header
 *   got far enough to say it — set with [BackupReadFailure.UNSUPPORTED_FORMAT_VERSION], so the user is
 *   told which version to update to.
 * @param message A diagnostic for logs.
 */
class BackupFormatException(val failure: BackupReadFailure, message: String, val writerVersionName: String? = null) :
    IOException(message)
