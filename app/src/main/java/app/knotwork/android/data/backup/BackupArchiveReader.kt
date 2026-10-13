package app.knotwork.android.data.backup

import app.knotwork.android.domain.backup.BackupEntryName
import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupKeys
import app.knotwork.android.domain.backup.BackupReadFailure
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Reads the entries of an archive one by one, after its header has been read and while every chunk is
 * authenticated by [BackupDecryptingInputStream].
 *
 * An entry's bytes are authenticated before they are handed out, but the archive as a whole is
 * authenticated only when [nextEntry] has returned `null`: until then a later chunk may still turn out
 * damaged, so a caller writes entries to a staging place and replaces nothing before the end. The first
 * entry must be [BackupFormat.MANIFEST_ENTRY] — authenticated content that holds no entries, or starts
 * with another, is damaged; so is a name that breaks [BackupEntryName], a directory entry or a repeated
 * name.
 *
 * @param source The archive, positioned at its first chunk (just after the header). Closed by [close].
 * @param parsed The header read from [source].
 * @param keys The keys derived from the password the user entered.
 * @throws BackupFormatException On construction with [BackupReadFailure.WRONG_PASSWORD] when the keys do
 *   not authenticate the header; while reading, as [BackupDecryptingInputStream] does, and with
 *   [BackupReadFailure.DAMAGED] for content that is not a valid entry list.
 */
internal class BackupArchiveReader(source: InputStream, parsed: ParsedBackupHeader, keys: BackupKeys) : Closeable {

    private val decrypting = BackupDecryptingInputStream(source, parsed, keys)
    private val zip = ZipInputStream(decrypting)
    private val names = HashSet<String>()

    /**
     * Moves to the next entry.
     *
     * @return The entry, whose [BackupArchiveEntry.content] is valid until the next call; or `null`
     *   after the last one, once the rest of the file has been authenticated and its end checked.
     */
    fun nextEntry(): BackupArchiveEntry? {
        val entry = try {
            zip.nextEntry
        } catch (e: ZipException) {
            damaged("not a valid entry list: ${e.javaClass.simpleName}")
        }
        if (entry == null) {
            if (names.isEmpty()) damaged("no manifest entry")
            decrypting.readToEnd()
            return null
        }
        accept(entry.name, entry.isDirectory)
        val content = object : FilterInputStream(zip) {
            override fun read(): Int = entryRead { super.read() }

            override fun read(b: ByteArray, off: Int, len: Int): Int = entryRead { super.read(b, off, len) }

            override fun close() {
                // The archive stays open for the next entry.
            }
        }
        return BackupArchiveEntry(entry.name, content)
    }

    private fun accept(name: String, isDirectory: Boolean) {
        if (names.isEmpty() && name != BackupFormat.MANIFEST_ENTRY) damaged("the first entry is not the manifest")
        if (isDirectory || !BackupEntryName.isValid(name)) damaged("entry name breaks the rule")
        if (!names.add(name)) damaged("entry name repeats")
    }

    private inline fun entryRead(read: () -> Int): Int = try {
        read()
    } catch (e: ZipException) {
        damaged("entry content is not valid: ${e.javaClass.simpleName}")
    }

    private fun damaged(reason: String): Nothing = throw BackupFormatException(BackupReadFailure.DAMAGED, reason)

    override fun close() {
        zip.close()
    }
}

/**
 * One entry of an archive being read.
 *
 * @property name The entry's name, already checked against [BackupEntryName].
 * @property content The entry's bytes; closing it does not close the archive.
 */
internal class BackupArchiveEntry(val name: String, val content: InputStream)
