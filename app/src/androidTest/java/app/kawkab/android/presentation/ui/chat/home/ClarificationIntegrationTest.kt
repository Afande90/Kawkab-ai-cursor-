package app.kawkab.android.presentation.ui.chat.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.kawkab.design.components.chat.ChatContent
import app.kawkab.design.components.chat.ChatMetadata
import app.kawkab.design.components.chat.ChatRole
import app.kawkab.design.components.chat.ClarificationCardModel
import app.kawkab.design.screens.chat.ChatHomeCallbacks
import app.kawkab.design.screens.chat.ChatHomeContent
import app.kawkab.design.screens.chat.ChatHomeMessageRow
import app.kawkab.design.screens.chat.ChatHomeViewState
import app.kawkab.design.screens.chat.ChatHomeVisualState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests covering the chat-home clarification card.
 */
class ClarificationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingWithOptions_rendersQuestionAndQuickReplyChips() {
        composeTestRule.setContent {
            ChatHomeContent(
                state = viewStateWithClarification(
                    question = "Which calendar should I use?",
                    quickReplies = listOf("Work", "Personal"),
                ),
                callbacks = ChatHomeCallbacks(),
            )
        }
        composeTestRule.onNodeWithText("Which calendar should I use?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText("Personal").assertIsDisplayed()
    }

    @Test
    fun quickReplyChip_click_dispatchesOnClarificationReplyWithLabel() {
        var captured: String? = null
        composeTestRule.setContent {
            ChatHomeContent(
                state = viewStateWithClarification(
                    question = "Pick a colour",
                    quickReplies = listOf("Red", "Blue"),
                ),
                callbacks = ChatHomeCallbacks(
                    onClarificationReply = { captured = it },
                ),
            )
        }
        composeTestRule.onNodeWithText("Blue").performClick()
        assertEquals("Blue", captured)
    }

    private fun viewStateWithClarification(question: String, quickReplies: List<String>): ChatHomeViewState {
        val row = ChatHomeMessageRow(
            id = "a-clar",
            role = ChatRole.Assistant,
            content = ChatContent.Clarification(
                model = ClarificationCardModel(
                    question = question,
                    quickReplies = quickReplies,
                ),
            ),
            metadata = ChatMetadata(timestamp = "09:16", model = "TestModel"),
        )
        return ChatHomeViewState(
            visualState = ChatHomeVisualState.Clarification,
            threadTitle = "Test thread",
            modelName = "TestModel",
            messages = listOf(row),
        )
    }
}
