package app.kawkab.android.domain.text

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/**
 * A time of day the way the owner reads it (task 23i): "6:00 AM" in English, "٦:٠٠ ص" in Arabic,
 * never "6:0".
 */
object ClockText {
    /**
     * @param hour 0 to 23.
     * @param minute 0 to 59.
     * @param locale The owner's language.
     * @return The time with minutes padded, AM/PM, and the locale's digits.
     */
    fun format(hour: Int, minute: Int, locale: Locale): String {
        val formatter = DateTimeFormatter.ofPattern(PATTERN, locale).withDecimalStyle(DecimalStyle.of(locale))
        return LocalTime.of(hour.coerceIn(0, LAST_HOUR), minute.coerceIn(0, LAST_MINUTE)).format(formatter)
    }

    private const val PATTERN = "h:mm a"
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
}
