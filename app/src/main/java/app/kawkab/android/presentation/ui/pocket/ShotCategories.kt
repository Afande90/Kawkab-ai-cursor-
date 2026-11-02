package app.kawkab.android.presentation.ui.pocket

import android.content.Context
import androidx.core.content.edit

/** Only owner-supplied labels are remembered; image bytes remain in the existing attachment store. */
internal class ShotCategories(context: Context) {
    private val prefs = context.getSharedPreferences("shot_categories", Context.MODE_PRIVATE)
    fun get(path: String): String? = prefs.getString(path, null)
    fun remember(path: String, category: String) {
        prefs.edit { putString(path, category) }
    }
    fun forget(path: String) {
        prefs.edit { remove(path) }
    }
    fun all(): Map<String, String> = prefs.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    fun answer(text: String): String? = when (text.trim().lowercase()) {
        "job", "work", "شغل", "وظيفة" -> "JOB"
        "bill", "receipt", "فاتورة", "حساب" -> "BILL"
        "place", "location", "مكان", "موقع" -> "PLACE"
        else -> null
    }
}
