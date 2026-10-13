package app.knotwork.android.domain.backup

/**
 * The rule every entry name inside an archive obeys, checked by the writer before it writes an entry
 * and by the reader before it hands one out.
 *
 * A name is a relative path of `/`-separated segments that cannot leave the directory it is unpacked
 * into and cannot hide a character: no leading `/`, no `\`, no empty, `.` or `..` segment, no control
 * character (C0, C1 or DEL), no bidirectional formatting character, at most [MAX_LENGTH] UTF-16 code
 * units. Entries are files only — a directory is implied by the names under it, so a name may not also
 * be one of the directories another implies ([directoriesOf]). A reader still resolves every name
 * against its target directory before writing (canonicalise, then compare): the rule keeps a crafted
 * archive out at the first step, not the only one.
 */
object BackupEntryName {

    /** The longest entry name, in UTF-16 code units. */
    const val MAX_LENGTH: Int = 1024

    private const val SEPARATOR = '/'

    /** Characters that reorder how a name is displayed without showing themselves. */
    private val BIDI_CONTROLS: Set<Char> = setOf(
        '\u061C', '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069',
    )

    /**
     * Whether [name] may name an entry.
     *
     * @param name The candidate entry name.
     * @return `true` when [name] obeys the rule above.
     */
    fun isValid(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_LENGTH) return false
        if (name.any { Character.isISOControl(it) || it in BIDI_CONTROLS || it == '\\' }) return false
        return name.split(SEPARATOR).none { it.isEmpty() || it == "." || it == ".." }
    }

    /**
     * The directories [name] implies — every leading run of its segments.
     *
     * @param name A valid entry name.
     * @return `a` and `a/b` for `a/b/c.txt`; nothing for a name without `/`.
     */
    fun directoriesOf(name: String): List<String> {
        val segments = name.split(SEPARATOR)
        return (1 until segments.size).map { segments.take(it).joinToString(SEPARATOR.toString()) }
    }
}
