package app.kawkab.android.presentation.ui.pocket

import app.kawkab.android.domain.repositories.AppStateSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Services needed by the device shell without adding presentation duties to the chat engine. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PocketServices {
    /** Speech completion starts the full three-second cancel window. */
    fun gate(): app.kawkab.android.domain.engine.executors.ToolInvocationGate

    /** Existing local image messages populate the screenshot inbox. */
    fun chats(): app.kawkab.android.domain.repositories.ChatRepository

    /** Persists successful first voice setup. */
    fun appState(): AppStateSettings
}
