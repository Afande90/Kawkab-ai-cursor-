package app.knotwork.design.screens.settings.backup

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
 * Roborazzi baseline for Settings → Privacy → *Backup & restore* and its *Back up* route: the screen, the password
 * form (empty, flagged, ready, 200 %), the work (key derivation, files), the saved result (also at 200 %, where the
 * counts fall into one column) and two failures — light, and dark for every state the user reads a result in.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class BackupSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backup_home_light() = snapshot("home", dark = false) { BackupContent(BackupViewState(), BackupCallbacks()) }

    @Test
    fun backup_home_dark() = snapshot("home", dark = true) { BackupContent(BackupViewState(), BackupCallbacks()) }

    @Test
    fun backup_form_empty_light() = snapshot("form_empty", dark = false) { RouteScreen(BackupForm()) }

    @Test
    fun backup_form_errors_light() = snapshot("form_errors", dark = false) { RouteScreen(FLAGGED) }

    @Test
    fun backup_form_errors_dark() = snapshot("form_errors", dark = true) { RouteScreen(FLAGGED) }

    @Test
    fun backup_form_ready_light() = snapshot("form_ready", dark = false) { RouteScreen(READY) }

    @Test
    fun backup_form_font_scale_2x_light() = snapshot("form_font_scale_2x", dark = false, fontScale = 2f) {
        RouteScreen(FLAGGED)
    }

    @Test
    fun backup_working_password_light() = snapshot("working_password", dark = false) {
        RouteScreen(BackupWorking(FILE_NAME, BackupWorkStep.PASSWORD))
    }

    @Test
    fun backup_working_files_light() = snapshot("working_files", dark = false) { RouteScreen(WORKING_FILES) }

    @Test
    fun backup_working_files_dark() = snapshot("working_files", dark = true) { RouteScreen(WORKING_FILES) }

    @Test
    fun backup_saved_light() = snapshot("saved", dark = false) { RouteScreen(SAVED) }

    @Test
    fun backup_saved_dark() = snapshot("saved", dark = true) { RouteScreen(SAVED) }

    @Test
    fun backup_saved_font_scale_2x_light() = snapshot("saved_font_scale_2x", dark = false, fontScale = 2f) {
        RouteScreen(SAVED)
    }

    @Test
    fun backup_failed_no_space_light() = snapshot("failed_no_space", dark = false) { RouteScreen(NO_SPACE) }

    @Test
    fun backup_failed_no_space_dark() = snapshot("failed_no_space", dark = true) { RouteScreen(NO_SPACE) }

    @Test
    fun backup_failed_write_light() = snapshot("failed_write", dark = false) {
        RouteScreen(BackupFailed(BackupFailReason.WRITE_FAILED))
    }

    @Composable
    private fun RouteScreen(route: BackupRoute) = BackupContent(BackupViewState(route = route), BackupCallbacks())

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
            filePath = "src/test/snapshots/settings_backup_${name}_$themeTag.png",
        )
    }

    private companion object {
        const val FILE_NAME = "knotwork-backup-2026-10-13.knotwork"

        /** A short first password left, and a second that differs: both errors at once. */
        val FLAGGED = BackupForm(
            password = "brass",
            confirmation = "brass lantern",
            passwordTooShort = true,
            confirmationMismatch = true,
        )

        /** Both fields pass and the secrets box is ticked: the button is live. */
        val READY = BackupForm(
            password = "brass lantern otter quiet",
            confirmation = "brass lantern otter quiet",
            includeSecrets = true,
            canContinue = true,
        )

        val WORKING_FILES = BackupWorking(
            fileName = FILE_NAME,
            step = BackupWorkStep.FILES,
            bytesDone = 412_000_000,
            bytesTotal = 1_030_000_000,
        )

        val SAVED = BackupSaved(
            fileName = FILE_NAME,
            sizeBytes = 1_030_000_000,
            chats = 128,
            memories = 3_412,
            pipelines = 14,
            files = 57,
            keysIncluded = true,
            skippedFiles = 2,
        )

        /** The provider refused the delete: the screen names the file to delete. */
        val NO_SPACE = BackupFailed(
            reason = BackupFailReason.NO_SPACE,
            neededBytes = 1_030_000_000,
            fileDeleted = false,
            fileName = FILE_NAME,
        )
    }
}
