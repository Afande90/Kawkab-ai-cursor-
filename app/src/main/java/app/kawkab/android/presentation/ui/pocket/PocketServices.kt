package app.kawkab.android.presentation.ui.pocket

import app.kawkab.android.domain.repositories.AppStateSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Services needed by the device shell without adding presentation duties to the chat engine. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PocketServices {
    /** Persists successful first voice setup. */
    fun appState(): AppStateSettings
}
