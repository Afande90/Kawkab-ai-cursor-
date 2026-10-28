package app.kawkab.android.presentation.ui.discover

import app.kawkab.android.domain.models.DiscoverableModelDetail
import app.kawkab.android.domain.repositories.InterruptedDownload

/** Loading branch of the Discover detail screen. */
enum class DiscoverDetailStatus {
    /** Fetching the repository detail. */
    Loading,

    /** Detail loaded. */
    Loaded,

    /** Network/parse failure. */
    Error,
}

/**
 * Immutable UI state of the model-discovery detail screen.
 *
 * @property status which loading branch to render.
 * @property detail the loaded repository detail, or `null` until loaded.
 * @property progress per-file download percentage (0..100) for in-flight installs.
 * @property installed file names installed during this screen's lifetime,
 *   layered over the [DiscoverableModelDetail.files] `isInstalled` flags so a
 *   just-finished install flips to the "Installed" badge without a refetch.
 * @property pendingLicenseFileName file awaiting license confirmation; non-null
 *   shows the confirm dialog.
 * @property tokenInput current Hugging Face token text (for gated repos).
 * @property tokenRevealed whether the token is shown in clear text.
 * @property interruption the next download of one of this repository's files that stopped for a
 *   reason not shown yet — a pause at the background time limit or a file that failed its
 *   checksum; the screen shows it once and reports it shown. `null` when there is none.
 */
data class DiscoverDetailUiState(
    val status: DiscoverDetailStatus = DiscoverDetailStatus.Loading,
    val detail: DiscoverableModelDetail? = null,
    val progress: Map<String, Int> = emptyMap(),
    val installed: Set<String> = emptySet(),
    val pendingLicenseFileName: String? = null,
    val tokenInput: String = "",
    val tokenRevealed: Boolean = false,
    val interruption: InterruptedDownload? = null,
)

/** One-shot result of an install attempt, surfaced to the screen as a snackbar. */
sealed interface DiscoverInstallEvent {
    /** Install completed; [fileName] is now on disk. */
    data class Success(val fileName: String) : DiscoverInstallEvent

    /**
     * Install failed.
     *
     * @property reason Why, as far as the screen words it differently.
     */
    data class Failed(val reason: FailureReason) : DiscoverInstallEvent

    /** Why an install failed, for the sentence the screen shows. */
    enum class FailureReason {

        /**
         * An access-gated refusal (HTTP 401/403) — the screen nudges the user toward
         * accepting the licence and adding a token.
         */
        GATED,

        /**
         * Anything else — the screen suggests checking the connection. A pause and a
         * checksum mismatch are not reported this way: they reach the screen as an
         * [DiscoverDetailUiState.interruption], once, whether or not it was watching.
         */
        OTHER,
    }
}
