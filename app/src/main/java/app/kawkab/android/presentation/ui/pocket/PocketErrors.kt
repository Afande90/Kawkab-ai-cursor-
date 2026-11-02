package app.kawkab.android.presentation.ui.pocket

import java.util.Locale

/** Ordinary owner messages; diagnostic detail remains behind Advanced and crash-report copying. */
internal object PocketErrors {
    fun reply(detail: String): String {
        val text = detail.lowercase(Locale.ROOT)
        return when {
            "locked" in text -> "Unlock your phone and try again."
            listOf("model", "engine", "gguf", "litert").any { it in text } ->
                "Get a language model in Settings > Models, then try again."
            "permission" in text || "access" in text -> "Check Phone access in Settings, then try again."
            else -> "I could not finish that. Please try again."
        }
    }
}
