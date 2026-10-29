package app.kawkab.android.hands

import app.kawkab.android.domain.phoneaccess.MissingPermission
import app.kawkab.android.domain.phoneaccess.MissingPermissionCard

/**
 * What the phone can do for the five core commands, plus a screen read and a guarded tap.
 * Android is behind this interface so tests can record the calls.
 */
interface PhoneHands {
    /** Starts an outbound call to [number]. The tool gate has already required a tap. */
    fun placeCall(number: String): String

    /** Opens the SMS compose screen for [number] with [body], then taps send when accessibility can. */
    fun composeSms(number: String, body: String): String

    /** Opens [packageName] and taps its send control after the message is on screen. */
    fun sendInApp(packageName: String, body: String): String

    /** Asks the clock app to set an alarm. */
    fun setAlarm(hour: Int, minute: Int, label: String): String

    /** Asks the clock app to start a timer of [seconds]. */
    fun setTimer(seconds: Int): String

    /** Opens the app whose label or package is [name]. */
    fun openApp(name: String): String

    /** Inserts a calendar event. */
    fun addCalendarEvent(title: String, startEpochMs: Long): String

    /** Reads the text of the current screen. */
    fun readScreen(): String

    /** Taps a node by [text] or [viewId]. [confirmed] is the outbound tap permit. */
    fun tap(text: String, viewId: String, confirmed: Boolean): String
}

/**
 * A contact the phone book returned.
 *
 * @property name Display name.
 * @property phone Number to dial or text.
 */
data class ContactMatch(val name: String, val phone: String)

/**
 * Looks up a spoken name. A number is returned as itself by [ContactLookup].
 */
interface ContactBook {
    /** Matches for [spoken], empty when nobody fits. */
    fun resolve(spoken: String): List<ContactMatch>

    /**
     * True when the book can read contacts. A missing grant is not an empty address book.
     */
    fun canRead(): Boolean = true
}

/**
 * Turns a spoken contact into one number, or a sentence when it cannot.
 */
object ContactLookup {
    private const val MIN_DIGITS = 6

    /**
     * A phone number, or null when [spoken] is ambiguous or missing.
     * [failure] receives the sentence to show the owner.
     */
    fun numberOrNull(spoken: String, book: ContactBook, failure: (String) -> Unit): String? {
        val trimmed = spoken.trim()
        val digits = trimmed.filter { it.isDigit() }
        val direct = digits.length >= MIN_DIGITS && trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        return when {
            trimmed.isEmpty() -> {
                failure("Missing contact.")
                null
            }
            direct -> trimmed.filter { it.isDigit() || it == '+' }
            !book.canRead() -> {
                failure(MissingPermissionCard(MissingPermission.READ_CONTACTS, "contacts").sentence())
                null
            }
            else -> match(trimmed, book, failure)
        }
    }

    private fun match(trimmed: String, book: ContactBook, failure: (String) -> Unit): String? {
        val matches = book.resolve(trimmed)
        return when (matches.size) {
            1 -> matches.first().phone
            0 -> {
                failure("Contact '$trimmed' was not found.")
                null
            }
            else -> {
                val choices = matches.joinToString { "${it.name} (${it.phone})" }
                failure("Which '$trimmed'? $choices")
                null
            }
        }
    }
}
