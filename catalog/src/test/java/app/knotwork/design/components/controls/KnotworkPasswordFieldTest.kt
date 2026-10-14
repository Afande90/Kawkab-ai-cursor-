package app.knotwork.design.components.controls

import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [KnotworkPasswordField]: TalkBack hears what the eye does, a revealed field stays revealed across recreation, and
 * a password manager hears a password — a new one where the caller says one is being chosen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class KnotworkPasswordFieldTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `given a masked field when the eye is tapped then its label says what the next tap does`() {
        // Given
        rule.setContent { KnotworkTheme { KnotworkPasswordField(value = "brass lantern", onValueChange = {}) } }

        // When
        rule.onNodeWithContentDescription("Show password").performClick()

        // Then
        rule.onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun `given a revealed field when the activity is recreated then it stays revealed`() {
        // Given
        val restoration = StateRestorationTester(rule)
        restoration.setContent { KnotworkTheme { KnotworkPasswordField(value = "brass lantern", onValueChange = {}) } }
        rule.onNodeWithContentDescription("Show password").performClick()

        // When
        restoration.emulateSavedInstanceStateRestore()

        // Then
        rule.onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun `given a field where a password is chosen when rendered then it is marked as a new password`() {
        rule.setContent {
            KnotworkTheme {
                KnotworkPasswordField(value = "", onValueChange = {}, autofillType = ContentType.NewPassword)
            }
        }

        assertEquals(ContentType.NewPassword, contentTypeOfTheField())
    }

    @Test
    fun `given a field with no autofill type when rendered then it keeps the text field's own password type`() {
        // A password keyboard makes the text field report a password by itself; the parameter only refines it.
        rule.setContent { KnotworkTheme { KnotworkPasswordField(value = "", onValueChange = {}) } }

        assertEquals(ContentType.Password, contentTypeOfTheField())
    }

    private fun contentTypeOfTheField(): ContentType? =
        rule.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType)
}
