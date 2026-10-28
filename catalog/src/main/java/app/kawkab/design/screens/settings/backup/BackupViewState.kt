package app.kawkab.design.screens.settings.backup

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

/** A full-screen route of *Restore*. */
sealed interface RestoreRoute : BackupRoute

/**
 * The picked backup, as the restore shows it.
 *
 * @property name The file's name, or `null` when its provider gives none.
 * @property writerVersion The version of Knotwork that wrote it, from its open header.
 * @property createdLabel When it was written, formatted for the user; `null` when not known.
 * @property sizeBytes The file's size, or `null` when its provider does not say.
 */
@Immutable
data class RestoreFile(
    val name: String?,
    val writerVersion: String? = null,
    val createdLabel: String? = null,
    val sizeBytes: Long? = null,
)

/**
 * *Restore*: the password the backup was made with.
 *
 * @property file The picked backup.
 * @property password The password typed so far.
 * @property wrongPassword Whether the last check found it does not open the backup; cleared by the next edit.
 */
@Immutable
data class RestorePassword(val file: RestoreFile, val password: String = "", val wrongPassword: Boolean = false) :
    RestoreRoute {
    /** Whether *Check* is enabled: something typed, and not the password just refused. */
    val canCheck: Boolean get() = password.isNotEmpty() && !wrongPassword
}

/**
 * *Checking the backup*: the password, then every part of the file.
 *
 * @property file The picked backup.
 * @property step The step running now.
 * @property bytesDone Content bytes checked so far.
 * @property bytesTotal Content bytes the backup holds; 0 while not known.
 */
@Immutable
data class RestoreChecking(
    val file: RestoreFile,
    val step: RestoreCheckStep,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
) : RestoreRoute

/** The named steps of a check, in order. */
enum class RestoreCheckStep {
    /** Deriving the keys and checking the password. */
    PASSWORD,

    /** Reading and checking every part of the file. */
    FILE,
}

/**
 * A backup that cannot be restored, and why. Nothing on the phone has changed.
 *
 * @property kind Why.
 * @property file The picked backup, when it is shown: with [RestoreProblemKind.NOT_A_BACKUP] its name, with
 *   [RestoreProblemKind.NEWER] what wrote it.
 * @property phoneVersion With [RestoreProblemKind.NEWER], this phone's version of Knotwork.
 * @property neededBytes With [RestoreProblemKind.NO_SPACE], about how much free space the restore needs; 0 when
 *   the phone ran out part-way and the need is not known.
 * @property freeBytes With [RestoreProblemKind.NO_SPACE] and a known [neededBytes], how much is free now.
 */
@Immutable
data class RestoreProblem(
    val kind: RestoreProblemKind,
    val file: RestoreFile? = null,
    val phoneVersion: String? = null,
    val neededBytes: Long = 0,
    val freeBytes: Long = 0,
) : RestoreRoute

/** Why a backup cannot be restored. */
enum class RestoreProblemKind {
    /** The file was not made by Knotwork. */
    NOT_A_BACKUP,

    /** It was made by a newer Knotwork than this phone's. */
    NEWER,

    /** Part of it cannot be read. */
    DAMAGED,

    /** It ends early. */
    INCOMPLETE,

    /** This phone has too little free space to check it. */
    NO_SPACE,

    /** The file, or this phone's storage, failed while it was read. */
    READ_FAILED,

    /** This phone's own database key cannot be read, so nothing can be restored here. */
    PHONE_KEY_UNAVAILABLE,

    /** The checked backup could not be set to apply; nothing changed, and the check would have to start over. */
    NOT_STARTED,
}

/**
 * Counts, as the restore summary shows them.
 *
 * @property chats Chats.
 * @property memories Long-term memory entries.
 * @property pipelines Pipelines.
 * @property files Workspace files and images.
 */
@Immutable
data class RestoreCounts(val chats: Int, val memories: Int, val pipelines: Int, val files: Int)

/**
 * *Ready to restore*: what comes in, and what this phone loses.
 *
 * @property fromBackup What the backup holds.
 * @property keysIncluded Whether its API keys and sign-ins come in too.
 * @property modelsToDownload How many of its models this phone would have to download again.
 * @property modelsBytes Their total size.
 * @property onThisPhone What this phone holds now, all of which is replaced.
 * @property confirmInput What is typed in the confirm dialog, or `null` while the dialog is closed.
 */
@Immutable
data class RestoreSummary(
    val fromBackup: RestoreCounts,
    val keysIncluded: Boolean,
    val modelsToDownload: Int,
    val modelsBytes: Long,
    val onThisPhone: RestoreCounts,
    val confirmInput: String? = null,
) : RestoreRoute

/** *Restoring…*: the restore is committed; the app restarts. */
data object Restoring : RestoreRoute

/**
 * What the *Restore* route asks the app to do.
 *
 * @property onChooseAnother *Choose another file* was tapped: the picker again.
 * @property onPasswordChange The password field changed.
 * @property onCheck *Check* was tapped, or *Done* on the keyboard.
 * @property onTryAgain *Try again* was tapped after there was not enough space: the same check again.
 * @property onCancelCheck *Cancel* was tapped while checking.
 * @property onReplace *Replace my data* was tapped: the confirm dialog opens.
 * @property onConfirmInputChange The confirm dialog's field changed.
 * @property onConfirmReplace *Replace* was tapped in the confirm dialog.
 * @property onConfirmCancel The confirm dialog was dismissed.
 */
@Immutable
data class RestoreCallbacks(
    val onChooseAnother: () -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onCheck: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onCancelCheck: () -> Unit = {},
    val onReplace: () -> Unit = {},
    val onConfirmInputChange: (String) -> Unit = {},
    val onConfirmReplace: () -> Unit = {},
    val onConfirmCancel: () -> Unit = {},
)

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
 * @property restore What the *Restore* route asks for.
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
    val restore: RestoreCallbacks = RestoreCallbacks(),
)
