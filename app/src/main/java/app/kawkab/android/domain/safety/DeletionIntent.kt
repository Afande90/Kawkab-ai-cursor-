package app.kawkab.android.domain.safety

import org.json.JSONObject

/** Delete controls require explicit speaker-verified yes, including through a generic accessibility tap. */
object DeletionIntent {
    private val words =
        Regex("(?i)(^|[^\\p{L}])(delete|remove|erase|discard|uninstall)([^\\p{L}]|$)|حذف|مسح|إزالة|ازالة")

    /** Actual target labels and ids are inspected again inside the accessibility service. */
    fun target(text: String?, viewId: String?): Boolean = words.containsMatchIn("${text.orEmpty()} ${viewId.orEmpty()}")

    /** Model-supplied arguments can request deletion but cannot supply consent. */
    fun requiresYes(tool: String, arguments: String): Boolean {
        if (tool == "delete_file") return true
        if (tool != "submit_tap") return false
        val json = runCatching { JSONObject(arguments) }.getOrNull() ?: return false
        return target(json.optString("text"), json.optString("view_id"))
    }
}

/** A synchronous action-scoped permit; no global yes can leak into a later macro or another thread. */
object DeletionConsent {
    private val permitted = ThreadLocal.withInitial { false }

    /** True only while a context carrying consumed owner evidence executes a synchronous tap. */
    fun allowed(): Boolean = permitted.get() == true

    /** The gate, not model arguments, supplies the context boolean. It is always cleared on return. */
    fun <T> withOwnerConsent(verified: Boolean, action: () -> T): T {
        val previous = permitted.get()
        permitted.set(verified)
        return try {
            action()
        } finally {
            permitted.set(previous)
        }
    }
}
