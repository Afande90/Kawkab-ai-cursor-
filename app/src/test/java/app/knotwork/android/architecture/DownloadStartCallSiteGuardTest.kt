package app.knotwork.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Census of the places that start a model download — which is also where the
 * download's foreground-time budget starts over.
 *
 * **Why.** `AndroidModelDownloadManager.downloadModel` resets
 * `DownloadForegroundBudget`. That is honest only because Android resets its own
 * daily `dataSync` count at the same moment: every caller is a user action on a
 * visible screen, so the app is on screen. A download started from the
 * background — a pipeline tool, a trigger — would reset the budget while
 * Android's count kept running, and the download could run into Android's
 * limit, which crashes the app. This test fails when a new file starts a
 * download, so the new caller is looked at before it lands.
 *
 * **What it cannot see.** It matches the call as written, `.downloadModel(`. A
 * call through a function reference or a wrapper is not matched; the one
 * wrapper that exists, the Discover install use case, has its own census below.
 */
class DownloadStartCallSiteGuardTest {

    @Test
    fun `only user actions on a screen start a model download`() {
        val callers = callersOf(DOWNLOAD_CALL)

        assertEquals(
            "a new file starts a model download. It resets the download's foreground budget, which is " +
                "honest only on a visible screen — check that the caller is a user action there, then add it.",
            DOWNLOAD_CALLERS,
            callers,
        )
    }

    @Test
    fun `the Discover install flow is driven only by its screen`() {
        val users = callersOf(INSTALL_USE_CASE) - INSTALL_USE_CASE_FILE

        assertEquals(
            "a new file uses the Discover install use case, which starts a model download",
            INSTALL_USE_CASE_USERS,
            users,
        )
    }

    @Test
    fun `the census reads the production sources it claims to`() {
        // Keeps the rules above from passing vacuously on an empty or wrong tree.
        assertTrue(
            "AndroidModelDownloadManager not found among ${ProductionSources.code.size} production files",
            ProductionSources.code.entries.any { (path, code) ->
                path.endsWith("/data/network/AndroidModelDownloadManager.kt") && "fun downloadModel(" in code
            },
        )
    }

    /** File names of the production sources whose code matches [pattern]. */
    private fun callersOf(pattern: Regex): Set<String> = ProductionSources.code
        .filterValues { pattern.containsMatchIn(it) }
        .keys
        .map { it.substringAfterLast('/') }
        .toSet()

    private companion object {
        /** A call that starts a download on the manager. */
        val DOWNLOAD_CALL = Regex("""\.downloadModel\(""")

        /** A use of the Discover install use case. */
        val INSTALL_USE_CASE = Regex("""\bInstallDiscoveredModelUseCase\b""")

        /** The file declaring the install use case. */
        const val INSTALL_USE_CASE_FILE = "InstallDiscoveredModelUseCase.kt"

        /** Every file allowed to start a download, each a user action on a screen. */
        val DOWNLOAD_CALLERS = setOf(
            // Download and retry buttons on the Models screen.
            "ModelsViewModel.kt",
            // The model step of first-run setup.
            "OnboardingViewModel.kt",
            // The Download button of the sheet over the chat after a restore.
            "AfterRestoreViewModel.kt",
            // The Discover install flow; its own users are pinned below.
            INSTALL_USE_CASE_FILE,
        )

        /** Every file allowed to drive the Discover install flow. */
        val INSTALL_USE_CASE_USERS = setOf(
            // The install button on a Discover model's detail screen.
            "DiscoverDetailViewModel.kt",
        )
    }
}
