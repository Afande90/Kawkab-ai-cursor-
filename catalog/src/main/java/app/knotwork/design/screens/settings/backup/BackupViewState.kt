package app.knotwork.design.screens.settings.backup

import androidx.compose.runtime.Immutable

/**
 * What the Settings → Privacy → *Backup & restore* screen shows: the screen itself, or one of its full-screen
 * routes over it.
 *
 * @property route The route in front, or `null` for the screen itself.
 * @property restoreAvailable Whether the screen offers *Restore from file…*.
 */
@Immutable
data class BackupViewState(val route: BackupRoute? = null, val restoreAvailable: Boolean = false)

/** A full-screen route of the *Backup & restore* screen; its state changes in place. */
sealed interface BackupRoute

/**
 * *Back up*: the password, twice, and whether secrets go in.
 *
 * @property password The password typed so far.
 * @property confirmation The second typing.
 * @property passwordTooShort Whether the first field shows *Too short*.
 * @property confirmationMismatch Whether the second field shows *Doesn't match*.
 * @property includeSecrets Whether API keys and sign-ins go in.
 * @property canContinue Whether *Choose where to save* is enabled: both fields pass.
 */
@Immutable
data class BackupForm(
    val password: String = "",
    val confirmation: String = "",
    val passwordTooShort: Boolean = false,
    val confirmationMismatch: Boolean = false,
    val includeSecrets: Boolean = false,
    val canContinue: Boolean = false,
) : BackupRoute

/**
 * *Backing up*: the file being written, the step and the bytes.
 *
 * @property fileName The chosen document's name, when the provider gives one.
 * @property step The step running now.
 * @property bytesDone Content bytes written so far.
 * @property bytesTotal Content bytes to write; 0 while the size is not known yet.
 */
@Immutable
data class BackupWorking(
    val fileName: String?,
    val step: BackupWorkStep,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
) : BackupRoute

/** The steps of a backup, in order. */
enum class BackupWorkStep {
    /** Deriving the keys from the password: about a second, with no measurable progress. */
    PASSWORD,

    /** The database snapshot and the settings. */
    DATABASE,

    /** The workspace files and the image attachments. */
    FILES,
}

/**
 * *Backup saved*.
 *
 * @property fileName The document's name, when the provider gives one.
 * @property sizeBytes The size of the written file.
 * @property chats Chats in the backup.
 * @property memories Memory entries in the backup.
 * @property pipelines Pipelines in the backup.
 * @property files Workspace files and image attachments in the backup.
 * @property keysIncluded Whether API keys and sign-ins are in it.
 * @property skippedFiles Files left out because their names cannot be stored in a backup.
 */
@Immutable
data class BackupSaved(
    val fileName: String?,
    val sizeBytes: Long,
    val chats: Int,
    val memories: Int,
    val pipelines: Int,
    val files: Int,
    val keysIncluded: Boolean,
    val skippedFiles: Int = 0,
) : BackupRoute

/**
 * *Backup not saved*.
 *
 * @property reason Why.
 * @property neededBytes With [BackupFailReason.NO_SPACE], about how much room the file needs.
 * @property fileDeleted Whether the unfinished file was deleted.
 * @property fileName The unfinished file's name, for the user to delete it when [fileDeleted] is `false`.
 */
@Immutable
data class BackupFailed(
    val reason: BackupFailReason,
    val neededBytes: Long = 0,
    val fileDeleted: Boolean = true,
    val fileName: String? = null,
) : BackupRoute

/** Why a backup was not saved. */
enum class BackupFailReason {
    /** The place the user picked ran out of space. */
    NO_SPACE,

    /** The place the user picked stopped accepting the file. */
    WRITE_FAILED,

    /** This phone has no room for the database snapshot the backup makes on the way. */
    PHONE_FULL,

    /** The app's own data could not be read. */
    READ_FAILED,
}

/**
 * What the *Backup & restore* screen and its routes ask the app to do.
 *
 * @property onBack Leave the screen.
 * @property onStartBackup Open *Back up*.
 * @property onStartRestore Open *Restore*.
 * @property onPasswordChange The first password field changed.
 * @property onPasswordFocusLost The first password field lost focus.
 * @property onConfirmationChange The second password field changed.
 * @property onConfirmationFocusLost The second password field lost focus.
 * @property onToggleSecrets The secrets checkbox was toggled.
 * @property onChooseWhere *Choose where to save* was tapped.
 * @property onCancel *Cancel* was tapped while working.
 * @property onDone *Done* was tapped on the result.
 * @property onTryAgain *Try again* was tapped after a failure.
 * @property onCloseRoute The route's *Close* was tapped.
 */
@Immutable
data class BackupCallbacks(
    val onBack: () -> Unit = {},
    val onStartBackup: () -> Unit = {},
    val onStartRestore: () -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onPasswordFocusLost: () -> Unit = {},
    val onConfirmationChange: (String) -> Unit = {},
    val onConfirmationFocusLost: () -> Unit = {},
    val onToggleSecrets: () -> Unit = {},
    val onChooseWhere: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onCloseRoute: () -> Unit = {},
)
