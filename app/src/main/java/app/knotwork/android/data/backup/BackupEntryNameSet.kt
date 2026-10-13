package app.knotwork.android.data.backup

import app.knotwork.android.domain.backup.BackupEntryName

/**
 * The entry names an archive has used so far, kept by the writer and the reader alike: a name may appear
 * once, and may not be a file and a directory at once — `files/a` and `files/a/b` cannot both be
 * unpacked, whichever comes first.
 */
internal class BackupEntryNameSet {

    private val files = HashSet<String>()
    private val directories = HashSet<String>()

    /** Whether no name has been added yet. */
    val isEmpty: Boolean
        get() = files.isEmpty()

    /**
     * Adds [name] when it fits beside the names added before.
     *
     * @param name A name that obeys [BackupEntryName].
     * @return `false` when [name] repeats, is a directory of an earlier name, or lies under an earlier name.
     */
    fun add(name: String): Boolean {
        val implied = BackupEntryName.directoriesOf(name)
        if (name in files || name in directories || implied.any { it in files }) return false
        files += name
        directories += implied
        return true
    }
}
