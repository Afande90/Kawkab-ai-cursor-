package app.kawkab.android.assistant

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** A voice interaction window has no Activity to own its Compose lifecycle. */
internal class PocketSessionOwner :
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun show() {
        registry.currentState = Lifecycle.State.RESUMED
    }
    fun hide() {
        registry.currentState = Lifecycle.State.CREATED
    }
    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
