package app.kawkab.android.presentation.ui.pocket

import app.kawkab.android.domain.repositories.AppStateSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Services needed by the device shell without adding presentation duties to the chat engine. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PocketServices {
    /** Automatic command/chat and act/ask verdicts at the owner-selected confidence threshold. */
    fun judge(): app.kawkab.android.domain.decision.TypedJudge

    /** Typed coordinator, independent of the language model's spoken answer. */
    fun decisions(): app.kawkab.android.domain.decision.DecisionCoordinator

    /** A conversational turn cannot authorize phone tools. */
    fun intents(): app.kawkab.android.domain.decision.DecisionIntents

    /** One-use verified voice handoff from the power-button panel. */
    fun entries(): app.kawkab.android.presentation.state.ChatEntryRequestRelay

    /** Ambiguous installed apps use spoken choices shared with the system assistant. */
    fun apps(): app.kawkab.android.hands.AppChoice

    /** Speech completion starts the full three-second cancel window. */
    fun gate(): app.kawkab.android.domain.engine.executors.ToolInvocationGate

    /** Existing local image messages populate the screenshot inbox. */
    fun chats(): app.kawkab.android.domain.repositories.ChatRepository

    /** Persists successful first voice setup. */
    fun appState(): AppStateSettings
}
