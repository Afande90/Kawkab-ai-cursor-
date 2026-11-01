package app.kawkab.android.accessibility

import android.accessibilityservice.AccessibilityButtonController

/**
 * The floating accessibility button (task 23b): a tap opens the Kawkab assistant panel over the
 * current app, the same panel as a long-press of power, or a new chat when Kawkab is not the
 * assistant. Android exposes no long-press on this button.
 *
 * @property open Opens the panel.
 */
class KawkabButtonCallback(private val open: () -> Unit) : AccessibilityButtonController.AccessibilityButtonCallback() {
    override fun onClicked(controller: AccessibilityButtonController?) = open()
}
