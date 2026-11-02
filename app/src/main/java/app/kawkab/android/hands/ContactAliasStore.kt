package app.kawkab.android.hands

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which contact the owner meant by a name (task 34b, vision rule 2). After he picks
 * "Abdalla Mubadala" for "abdalla", the next "call abdalla" goes straight to him; Kawkab never
 * asks again for that name. The Growth screen lists the names and can delete each one.
 */
interface ContactAliasStore {
    /** @return The remembered contact for [spoken], or `null`. */
    fun get(spoken: String): ContactMatch?

    /** Remembers that [spoken] means [contact]. */
    fun remember(spoken: String, contact: ContactMatch)

    /** @return Every remembered name, by its normalised spelling. */
    fun all(): Map<String, ContactMatch>

    /** Forgets [spoken]. */
    fun forget(spoken: String)

    /** Defaults. */
    companion object {
        /** Remembers nothing; for tests and previews. */
        val NONE: ContactAliasStore = object : ContactAliasStore {
            override fun get(spoken: String): ContactMatch? = null
            override fun remember(spoken: String, contact: ContactMatch) = Unit
            override fun all(): Map<String, ContactMatch> = emptyMap()
            override fun forget(spoken: String) = Unit
        }

        /**
         * The spelling a name is remembered under: lower case, single spaces, without a leading
         * "the" or "ال".
         *
         * @param spoken What the owner said.
         * @return The key.
         */
        fun key(spoken: String): String = ContactMatcher.tokens(spoken)
            .filterNot { it == "the" }
            .joinToString(" ")
    }
}

/** A small private preferences file, kept out of backups like the other voice settings. */
@Singleton
class AndroidContactAliases @Inject constructor(@ApplicationContext private val context: Context) : ContactAliasStore {
    override fun get(spoken: String): ContactMatch? = all()[ContactAliasStore.key(spoken)]

    override fun remember(spoken: String, contact: ContactMatch) {
        val key = ContactAliasStore.key(spoken)
        if (key.isBlank()) return
        prefs().edit {
            putString(
                key,
                JSONObject().put(
                    "name",
                    contact.name,
                ).put("phone", contact.phone).put("label", contact.label).toString(),
            )
        }
    }

    override fun all(): Map<String, ContactMatch> = prefs().all.mapNotNull { (key, value) ->
        val json = runCatching { JSONObject(value as String) }.getOrNull() ?: return@mapNotNull null
        key to ContactMatch(json.optString("name"), json.optString("phone"), json.optString("label"))
    }.toMap()

    override fun forget(spoken: String) {
        prefs().edit { remove(ContactAliasStore.key(spoken)) }
    }

    private fun prefs(): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private companion object {
        const val FILE = "contact_aliases"
    }
}
