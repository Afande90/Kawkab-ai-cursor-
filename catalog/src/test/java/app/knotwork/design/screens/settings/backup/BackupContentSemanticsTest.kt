package app.knotwork.design.screens.settings.backup

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the *Back up* route puts in the semantics tree for TalkBack: the working steps are one polite live region
 * whose text is the current step, so the step's name changes as the work moves on — the content change a live
 * region is announced on. What TalkBack then says is a device check; this pins the tree it reads.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupContentSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `given the work moving on when each step starts then the live region's text is that step`() {
        // Given
        val step = mutableStateOf(BackupWorkStep.PASSWORD)
        rule.setContent {
            KnotworkTheme {
                BackupContent(
                    state = BackupViewState(route = BackupWorking(fileName = "backup.knotwork", step = step.value)),
                    callbacks = BackupCallbacks(),
                )
            }
        }

        // Then — each step in turn.
        assertEquals(listOf("Securing the password"), liveRegionTexts())
        step.value = BackupWorkStep.DATABASE
        rule.waitForIdle()
        assertEquals(listOf("Chats, memory and settings"), liveRegionTexts())
        step.value = BackupWorkStep.FILES
        rule.waitForIdle()
        assertEquals(listOf("Files and images"), liveRegionTexts())
    }

    /** The text of every polite live region on screen. */
    private fun liveRegionTexts(): List<String> =
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() }
}
