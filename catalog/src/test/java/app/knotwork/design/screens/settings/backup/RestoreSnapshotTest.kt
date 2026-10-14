package app.knotwork.design.screens.settings.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.KnotworkRoborazziOptions
import app.knotwork.design.a11y.FixedKnotworkA11y
import app.knotwork.design.a11y.LocalKnotworkA11y
import app.knotwork.design.theme.KnotworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baseline for the *Restore* route of Settings → Privacy → *Backup & restore* and the sheet over the chat
 * after a restore: the password (also refused, and at 200 %), the check (both steps), every problem the user is
 * told, the summary (also with nothing on the phone, and at 200 %), *Restoring…*, and the sheet (also with a
 * download running, and at 200 %) — light, and dark for the states read most.
 *
 * The typed confirm over the summary is not captured: the shared `TypedConfirmDialog` never lets Compose go idle
 * under Robolectric (its field inside an `AlertDialog`), which is why no screen has a baseline of it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class RestoreSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun restore_password_light() = snapshot("password", dark = false) {
        RouteScreen(RestorePassword(FILE, "brass lantern"))
    }

    @Test
    fun restore_password_dark() = snapshot("password", dark = true) {
        RouteScreen(RestorePassword(FILE, "brass lantern"))
    }

    @Test
    fun restore_password_wrong_light() = snapshot("password_wrong", dark = false) {
        RouteScreen(RestorePassword(FILE, "brass lantern", wrongPassword = true))
    }

    @Test
    fun restore_password_font_scale_2x_light() = snapshot("password_font_scale_2x", dark = false, fontScale = 2f) {
        RouteScreen(RestorePassword(FILE, "brass lantern", wrongPassword = true))
    }

    @Test
    fun restore_checking_password_light() = snapshot("checking_password", dark = false) {
        RouteScreen(RestoreChecking(FILE, RestoreCheckStep.PASSWORD))
    }

    @Test
    fun restore_checking_file_light() = snapshot("checking_file", dark = false) { RouteScreen(CHECKING_FILE) }

    @Test
    fun restore_checking_file_dark() = snapshot("checking_file", dark = true) { RouteScreen(CHECKING_FILE) }

    @Test
    fun restore_problem_not_backup_light() = snapshot("problem_not_backup", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.NOT_A_BACKUP, RestoreFile("holiday-notes.txt")))
    }

    @Test
    fun restore_problem_newer_light() = snapshot("problem_newer", dark = false) {
        RouteScreen(
            RestoreProblem(
                RestoreProblemKind.NEWER,
                FILE.copy(writerVersion = "0.14.0"),
                phoneVersion = "0.12.0",
            ),
        )
    }

    @Test
    fun restore_problem_damaged_light() = snapshot("problem_damaged", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.DAMAGED, FILE))
    }

    @Test
    fun restore_problem_damaged_dark() = snapshot("problem_damaged", dark = true) {
        RouteScreen(RestoreProblem(RestoreProblemKind.DAMAGED, FILE))
    }

    @Test
    fun restore_problem_incomplete_light() = snapshot("problem_incomplete", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.INCOMPLETE, FILE))
    }

    @Test
    fun restore_problem_space_light() = snapshot("problem_space", dark = false) {
        RouteScreen(
            RestoreProblem(RestoreProblemKind.NO_SPACE, FILE, neededBytes = 2_400_000_000, freeBytes = 900_000_000),
        )
    }

    @Test
    fun restore_problem_space_ran_out_light() = snapshot("problem_space_ran_out", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.NO_SPACE, FILE))
    }

    @Test
    fun restore_problem_read_failed_light() = snapshot("problem_read_failed", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.READ_FAILED, FILE))
    }

    @Test
    fun restore_problem_phone_key_light() = snapshot("problem_phone_key", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.PHONE_KEY_UNAVAILABLE, FILE))
    }

    @Test
    fun restore_problem_not_started_light() = snapshot("problem_not_started", dark = false) {
        RouteScreen(RestoreProblem(RestoreProblemKind.NOT_STARTED, FILE))
    }

    @Test
    fun restore_summary_light() = snapshot("summary", dark = false) { RouteScreen(SUMMARY) }

    @Test
    fun restore_summary_dark() = snapshot("summary", dark = true) { RouteScreen(SUMMARY) }

    @Test
    fun restore_summary_empty_phone_light() = snapshot("summary_empty_phone", dark = false) {
        RouteScreen(SUMMARY.copy(keysIncluded = false, modelsToDownload = 0, onThisPhone = RestoreCounts(0, 0, 0, 0)))
    }

    @Test
    fun restore_summary_font_scale_2x_light() = snapshot("summary_font_scale_2x", dark = false, fontScale = 2f) {
        RouteScreen(SUMMARY)
    }

    @Test
    fun restore_restoring_light() = snapshot("restoring", dark = false) { RouteScreen(Restoring) }

    @Test
    fun restore_restoring_dark() = snapshot("restoring", dark = true) { RouteScreen(Restoring) }

    @Test
    fun restore_after_sheet_light() = snapshot("after_sheet", dark = false) { SheetScreen(AFTER) }

    @Test
    fun restore_after_sheet_dark() = snapshot("after_sheet", dark = true) { SheetScreen(AFTER) }

    @Test
    fun restore_after_sheet_downloading_light() = snapshot("after_sheet_downloading", dark = false) {
        SheetScreen(
            AFTER.copy(
                models = AFTER.models.mapIndexed { index, model ->
                    if (index == 0) model.copy(progress = 20) else model.copy(canDownload = false)
                },
            ),
        )
    }

    @Test
    fun restore_after_sheet_font_scale_2x_light() =
        snapshot("after_sheet_font_scale_2x", dark = false, fontScale = 2f) {
            SheetScreen(AFTER)
        }

    @Test
    fun restore_after_sheet_failed_light() = snapshot("after_sheet_failed", dark = false) { SheetScreen(AFTER_FAILED) }

    @Test
    fun restore_after_sheet_failed_dark() = snapshot("after_sheet_failed", dark = true) { SheetScreen(AFTER_FAILED) }

    @Test
    fun restore_after_sheet_failed_font_scale_2x_light() =
        snapshot("after_sheet_failed_font_scale_2x", dark = false, fontScale = 2f) {
            SheetScreen(AFTER_FAILED)
        }

    @Composable
    private fun RouteScreen(route: BackupRoute) =
        BackupContent(BackupViewState(route = route, restoreAvailable = true), BackupCallbacks())

    /** The sheet's content on the surface the sheet gives it — without one, the dark captures had no background. */
    @Composable
    private fun SheetScreen(state: AfterRestoreState) =
        Surface(color = MaterialTheme.colorScheme.surface) { AfterRestoreSheetContent(state, AfterRestoreCallbacks()) }

    private fun snapshot(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            KnotworkTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true, fontScale = fontScale),
                    LocalDensity provides Density(density = baseDensity.density, fontScale = fontScale),
                ) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            roborazziOptions = KnotworkRoborazziOptions,
            filePath = "src/test/snapshots/settings_restore_${name}_$themeTag.png",
        )
    }

    private companion object {
        val FILE = RestoreFile(
            name = "knotwork-backup-2026-10-12.knotwork",
            writerVersion = "0.12.0",
            createdLabel = "12 Oct 2026, 21:40",
            sizeBytes = 1_030_000_000,
        )

        val CHECKING_FILE =
            RestoreChecking(FILE, RestoreCheckStep.FILE, bytesDone = 412_000_000, bytesTotal = 1_030_000_000)

        val SUMMARY = RestoreSummary(
            fromBackup = RestoreCounts(chats = 128, memories = 3_412, pipelines = 14, files = 57),
            keysIncluded = true,
            modelsToDownload = 2,
            modelsBytes = 3_200_000_000,
            onThisPhone = RestoreCounts(chats = 12, memories = 340, pipelines = 9, files = 6),
        )

        val AFTER = AfterRestoreState(
            madeOnLabel = "12 Oct 2026, 21:40",
            models = listOf(
                AfterRestoreModel("gemma-4-E2B-it.litertlm", 2_588_147_712, sourceKnown = true),
                AfterRestoreModel("custom-finetune.litertlm", 612_000_000, sourceKnown = false),
            ),
            enterKeysAgain = true,
            tasksLeftBehind = true,
        )

        /** A gated repository refused its download — no token came with the backup — and another one failed. */
        val AFTER_FAILED = AFTER.copy(
            models = listOf(
                AfterRestoreModel(
                    "gemma-3n-E4B-it-int4.litertlm",
                    4_405_000_000,
                    sourceKnown = true,
                    failure = AfterRestoreDownloadFailure.NeedsAccess,
                ),
                AfterRestoreModel(
                    "gemma-4-E2B-it.litertlm",
                    2_588_147_712,
                    sourceKnown = true,
                    failure = AfterRestoreDownloadFailure.Failed,
                ),
                AfterRestoreModel("custom-finetune.litertlm", 612_000_000, sourceKnown = false),
            ),
        )
    }
}
