package app.knotwork.android.domain.backup

/**
 * The rule every entry name inside an archive obeys, checked by the writer before it writes an entry
 * and by the reader before it hands one out.
 *
 * A name is a relative path of `/`-separated segments that cannot leave the directory it is unpacked
 * into: no leading `/`, no `\`, no empty, `.` or `..` segment, no control character, at most
 * [MAX_LENGTH] characters. Entries are files only — a directory is implied by the names under it.
 * A reader still resolves every name against its target directory before writing (canonicalise, then
 * compare): the rule keeps a crafted archive out at the first step, not the only one.
 */
object BackupEntryName {

    /** The longest entry name, in characters. */
    const val MAX_LENGTH: Int = 1024

    private const val SEPARATOR = '/'
    private const val FIRST_PRINTABLE = ' '
    private const val DELETE = '\u007f'

    /**
     * Whether [name] may name an entry.
     *
     * @param name The candidate entry name.
     * @return `true` when [name] obeys the rule above.
     */
    fun isValid(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_LENGTH) return false
        if (name.any { it < FIRST_PRINTABLE || it == DELETE || it == '\\' }) return false
        return name.split(SEPARATOR).none { it.isEmpty() || it == "." || it == ".." }
    }
}
