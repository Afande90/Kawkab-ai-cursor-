package app.kawkab.android.hands

/**
 * One screen a stored map knows how to recognise.
 *
 * @property id Stable screen id inside one app.
 * @property markers Text that must appear on that screen, in English or Arabic.
 */
data class AppScreen(val id: String, val markers: List<String>)

/**
 * One stored navigation step. Replay does not ask a model what to do next.
 *
 * @property tool Hands tool to run.
 * @property args Arguments for that tool.
 * @property screenAfter Screen id that must be visible after the step.
 * @property outbound True when the step sends, calls, or posts, so it still needs a tap.
 */
data class AppMapStep(val tool: String, val args: Map<String, String>, val screenAfter: String, val outbound: Boolean)

/**
 * A common task stored as steps.
 *
 * @property id Stable id, unique across the bundled maps.
 * @property app Which app the task belongs to.
 * @property steps Steps in order.
 */
data class StoredMacro(val id: String, val app: String, val steps: List<AppMapStep>)

/**
 * Navigation for one installed app.
 *
 * @property app Short name used in the map.
 * @property packageName Public store id. The owner can change it on a device.
 * @property screens Screens the macros check.
 * @property macros Common tasks for this app.
 */
data class StoredAppMap(
    val app: String,
    val packageName: String,
    val screens: List<AppScreen>,
    val macros: List<StoredMacro>,
)

/**
 * Stored maps for Phone, Messages, Clock, Calendar, WhatsApp and Gmail.
 *
 * The package ids are the public store ids. They are not a reading of the owner's phone.
 */
object BundledAppMaps {
    private val phone = map(
        app = "Phone",
        packageName = "com.google.android.dialer",
        screens = listOf(AppScreen("dialer", listOf("Keypad", "لوحة"))),
        macros = listOf(
            StoredMacro(
                id = "phone.open",
                app = "Phone",
                steps = listOf(open("Phone", "dialer")),
            ),
            StoredMacro(
                id = "phone.call_mom",
                app = "Phone",
                steps = listOf(
                    open("Phone", "dialer"),
                    AppMapStep("call_phone", mapOf("contact" to "Mom"), "dialer", outbound = true),
                ),
            ),
        ),
    )

    private val messages = map(
        app = "Messages",
        packageName = "com.google.android.apps.messaging",
        screens = listOf(AppScreen("inbox", listOf("Messages", "الرسائل"))),
        macros = listOf(
            StoredMacro(
                id = "messages.open",
                app = "Messages",
                steps = listOf(open("Messages", "inbox")),
            ),
            StoredMacro(
                id = "messages.text_mom",
                app = "Messages",
                steps = listOf(
                    open("Messages", "inbox"),
                    AppMapStep(
                        tool = "send_sms",
                        args = mapOf("contact" to "Mom", "body" to "On my way"),
                        screenAfter = "inbox",
                        outbound = true,
                    ),
                ),
            ),
        ),
    )

    private val clock = map(
        app = "Clock",
        packageName = "com.google.android.deskclock",
        screens = listOf(AppScreen("alarms", listOf("Alarm", "منبه"))),
        macros = listOf(
            StoredMacro(
                id = "clock.open",
                app = "Clock",
                steps = listOf(open("Clock", "alarms")),
            ),
            StoredMacro(
                id = "clock.alarm_6",
                app = "Clock",
                steps = listOf(
                    open("Clock", "alarms"),
                    AppMapStep(
                        tool = "set_alarm",
                        args = mapOf("hour" to "6", "minute" to "0"),
                        screenAfter = "alarms",
                        outbound = false,
                    ),
                ),
            ),
        ),
    )

    private val calendar = map(
        app = "Calendar",
        packageName = "com.google.android.calendar",
        screens = listOf(AppScreen("agenda", listOf("Calendar", "التقويم"))),
        macros = listOf(
            StoredMacro(
                id = "calendar.open",
                app = "Calendar",
                steps = listOf(open("Calendar", "agenda")),
            ),
            StoredMacro(
                id = "calendar.dentist",
                app = "Calendar",
                steps = listOf(
                    open("Calendar", "agenda"),
                    AppMapStep(
                        tool = "add_calendar_event",
                        args = mapOf("title" to "Dentist", "hour" to "15", "minute" to "0"),
                        screenAfter = "agenda",
                        outbound = false,
                    ),
                ),
            ),
        ),
    )

    private val whatsapp = map(
        app = "WhatsApp",
        packageName = "com.whatsapp",
        screens = listOf(AppScreen("chats", listOf("WhatsApp", "واتساب"))),
        macros = listOf(
            StoredMacro(
                id = "whatsapp.open",
                app = "WhatsApp",
                steps = listOf(open("WhatsApp", "chats")),
            ),
            StoredMacro(
                id = "whatsapp.note",
                app = "WhatsApp",
                steps = listOf(
                    open("WhatsApp", "chats"),
                    AppMapStep(
                        tool = "send_message",
                        args = mapOf("package" to "com.whatsapp", "body" to "On my way"),
                        screenAfter = "chats",
                        outbound = true,
                    ),
                ),
            ),
        ),
    )

    private val gmail = map(
        app = "Gmail",
        packageName = "com.google.android.gm",
        screens = listOf(AppScreen("inbox", listOf("Gmail", "البريد"))),
        macros = listOf(
            StoredMacro(
                id = "gmail.open",
                app = "Gmail",
                steps = listOf(open("Gmail", "inbox")),
            ),
            StoredMacro(
                id = "gmail.note",
                app = "Gmail",
                steps = listOf(
                    open("Gmail", "inbox"),
                    AppMapStep(
                        tool = "send_message",
                        args = mapOf("package" to "com.google.android.gm", "body" to "On my way"),
                        screenAfter = "inbox",
                        outbound = true,
                    ),
                ),
            ),
        ),
    )

    /**
     * Every bundled map.
     */
    val maps: List<StoredAppMap> = listOf(phone, messages, clock, calendar, whatsapp, gmail)

    /**
     * The macro [id], with the map it belongs to.
     */
    fun find(id: String): Pair<StoredAppMap, StoredMacro>? = maps.firstNotNullOfOrNull { map ->
        map.macros.find { it.id == id }?.let { map to it }
    }

    private fun open(name: String, screenAfter: String): AppMapStep = AppMapStep(
        tool = "open_app",
        args = mapOf("name" to name),
        screenAfter = screenAfter,
        outbound = false,
    )

    private fun map(
        app: String,
        packageName: String,
        screens: List<AppScreen>,
        macros: List<StoredMacro>,
    ): StoredAppMap = StoredAppMap(app, packageName, screens, macros)
}
