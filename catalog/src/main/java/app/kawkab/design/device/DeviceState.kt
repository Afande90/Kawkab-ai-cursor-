package app.kawkab.design.device

/** The eight owner-approved Pocket LCD views. */
enum class DevicePage { IDLE, LISTENING, WHICH, CALLING, TALK, SHOTS, GROWTH, ENROLL }

/** The physical status LED and foreground Glyph mirror share this state. */
enum class DeviceSignal { STANDBY, LISTENING, SPEAKING, ASKING, ACTING }

/** A screenshot filed locally; no screenshot content is sent off the phone. */
data class DeviceShot(val title: String, val category: String, val needsAnswer: Boolean = false)

/** A visible, removable learned item. */
data class DeviceLearning(val id: String, val text: String)

/** Immutable presentation data, with no model identifiers, counters or raw tool calls. */
data class DeviceState(
    val page: DevicePage = DevicePage.IDLE,
    val signal: DeviceSignal = DeviceSignal.STANDBY,
    val date: String = "",
    val battery: String = "",
    val heard: String = "",
    val reply: String = "",
    val result: String = "",
    val name: String = "",
    val number: String = "",
    val usual: Boolean = false,
    val choices: List<String> = emptyList(),
    val seconds: Int = 3,
    val countdownProgress: Float? = null,
    val enrolled: Int = 0,
    val enrollmentTotal: Int = 12,
    val shots: List<DeviceShot> = emptyList(),
    val learned: List<DeviceLearning> = emptyList(),
    val accuracy: Int? = null,
    val trend: List<Int> = emptyList(),
)
