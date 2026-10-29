package app.kawkab.android.domain.phoneaccess

/**
 * Status lines for the phone-access screen. A missing answer is off, never hidden.
 */
object PhoneAccessStatus {
    /**
     * The accessibility service class. It is not renamed.
     */
    const val ACCESSIBILITY_SERVICE: String = "app.kawkab.android.accessibility.KawkabAccessibilityService"

    /**
     * One line per item, in enum order.
     */
    fun lines(on: Map<PhoneAccessItem, Boolean>): List<PhoneAccessLine> =
        PhoneAccessItem.entries.map { item -> PhoneAccessLine(item, on[item] == true) }

    /**
     * The runtime permission for [item], or null when the item is a system setting.
     */
    fun permission(item: PhoneAccessItem): String? = when (item) {
        PhoneAccessItem.CONTACTS -> MissingPermission.READ_CONTACTS
        PhoneAccessItem.PHONE -> MissingPermission.CALL_PHONE
        PhoneAccessItem.CALENDAR -> MissingPermission.WRITE_CALENDAR
        PhoneAccessItem.NOTIFICATIONS -> MissingPermission.POST_NOTIFICATIONS
        PhoneAccessItem.MICROPHONE -> MissingPermission.RECORD_AUDIO
        PhoneAccessItem.ACCESSIBILITY, PhoneAccessItem.BATTERY -> null
    }

    /**
     * True when [enabledServices] names [serviceClass].
     */
    fun serviceOn(enabledServices: String?, serviceClass: String): Boolean =
        enabledServices.orEmpty().split(':').any { entry -> entry.substringAfter('/') == serviceClass }
}

/**
 * One thing the phone-access screen asks the owner to allow.
 */
enum class PhoneAccessItem {
    /** Reading contacts, so a name can become a number. */
    CONTACTS,

    /** Placing a call. */
    PHONE,

    /** Adding a calendar event. */
    CALENDAR,

    /** Showing a notification. */
    NOTIFICATIONS,

    /** Using the microphone. */
    MICROPHONE,

    /** The accessibility service that taps and reads the screen. */
    ACCESSIBILITY,

    /** Staying running when the phone is idle. */
    BATTERY,
}

/**
 * Whether one item is on.
 *
 * @property item Which permission or setting.
 * @property on True when the phone has already granted it.
 */
data class PhoneAccessLine(val item: PhoneAccessItem, val on: Boolean)

/**
 * A command that cannot run until the owner grants [permission].
 *
 * @property permission The Android permission still missing.
 * @property tool The hands tool that needs it.
 */
data class MissingPermissionCard(val permission: String, val tool: String) {
    /**
     * The sentence shown instead of failing with nothing.
     */
    fun sentence(): String = "${MissingPermission.PREFIX}$permission. Open Phone access to grant it."
}

/**
 * The card a command shows when a permission it needs is off.
 */
object MissingPermission {
    /**
     * Opening of [MissingPermissionCard.sentence]. A later screen looks for it.
     */
    const val PREFIX: String = "Missing permission "

    /** Runtime permission for placing a call. */
    const val CALL_PHONE: String = "android.permission.CALL_PHONE"

    /** Runtime permission for reading contacts. */
    const val READ_CONTACTS: String = "android.permission.READ_CONTACTS"

    /** Runtime permission for writing a calendar event. */
    const val WRITE_CALENDAR: String = "android.permission.WRITE_CALENDAR"

    /** Runtime permission for notifications. */
    const val POST_NOTIFICATIONS: String = "android.permission.POST_NOTIFICATIONS"

    /** Runtime permission for the microphone. */
    const val RECORD_AUDIO: String = "android.permission.RECORD_AUDIO"

    private const val MIN_DIGITS: Int = 6

    /**
     * The first missing permission for [tool], or null when the tool needs nothing more.
     * A spoken number does not need the contacts list.
     */
    fun card(tool: String, granted: Set<String>, spoken: String? = null): MissingPermissionCard? {
        val required = when (tool) {
            "call_phone" -> if (isNumber(spoken)) listOf(CALL_PHONE) else listOf(CALL_PHONE, READ_CONTACTS)
            "send_sms" -> if (isNumber(spoken)) emptyList() else listOf(READ_CONTACTS)
            "add_calendar_event" -> listOf(WRITE_CALENDAR)
            else -> return null
        }
        val missing = required.firstOrNull { it !in granted } ?: return null
        return MissingPermissionCard(missing, tool)
    }

    /**
     * The permission named by a [MissingPermissionCard.sentence], or null when [text] is not one.
     */
    fun permissionIn(text: String): String? {
        if (!text.startsWith(PREFIX)) return null
        return listOf(CALL_PHONE, READ_CONTACTS, WRITE_CALENDAR, POST_NOTIFICATIONS, RECORD_AUDIO)
            .firstOrNull { name -> text.contains(name) }
    }

    private fun isNumber(spoken: String?): Boolean {
        val trimmed = spoken?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        val digits = trimmed.count { it.isDigit() }
        return digits >= MIN_DIGITS && trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
    }
}
