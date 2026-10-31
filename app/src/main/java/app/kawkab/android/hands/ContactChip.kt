package app.kawkab.android.hands

/**
 * How a contact choice reads on a chip: the name, the last four digits and the phone label,
 * never the whole number (task 23j).
 */
object ContactChip {
    /**
     * @param match The contact.
     * @return For example "Abdalla Mubadala ·5678 Mobile".
     */
    fun text(match: ContactMatch): String =
        listOf(match.name, "·" + ContactMatcher.lastDigits(match.phone), match.label)
            .filter { it.isNotBlank() && it != "·" }
            .joinToString(" ")
}
