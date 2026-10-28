package app.kawkab.android.data.backup

import app.kawkab.android.domain.backup.BackupEntryName
import app.kawkab.android.domain.backup.BackupFormat
import app.kawkab.android.domain.backup.BackupHeader
import app.kawkab.android.domain.backup.BackupKeys
import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the content of an archive: a ZIP stream of named entries, encrypted as it is written by
 * [BackupEncryptingOutputStream] (`docs/backup-format.md` § Content).
 *
 * The first entry is always [BackupFormat.MANIFEST_ENTRY]. Every entry name must obey [BackupEntryName],
 * appear once and not be a file and a directory at once ([BackupEntryNameSet]). Text is worth
 * compressing; content that is already encrypted or compressed (the database, images) is not, so each
 * entry says which.
 * Call [finish] after the last entry: only then is the chunk flagged as last written. [close] without
 * [finish] leaves a file that every reader reports as truncated.
 *
 * @param sink Where the archive goes — the document the user picked. Closed by [close].
 * @param header The archive's header.
 * @param keys The keys derived for the header's salt. The writer does not wipe them.
 */
internal class BackupArchiveWriter(sink: OutputStream, header: BackupHeader, keys: BackupKeys) : Closeable {

    private val encrypting = BackupEncryptingOutputStream(sink, header, keys)
    private val zip = ZipOutputStream(encrypting)
    private val names = BackupEntryNameSet()

    /**
     * Adds one entry.
     *
     * @param name The entry's name; must obey [BackupEntryName] and be new in this archive — and be
     *   [BackupFormat.MANIFEST_ENTRY] for the first entry.
     * @param compress `true` to deflate the content, `false` to store it as it is.
     * @param content Writes the entry's bytes to the stream it is given. Closing that stream does not
     *   close the archive.
     * @throws IllegalArgumentException When [name] breaks the rule, repeats, clashes with an earlier name as
     *   a file and a directory, or is not the manifest while the archive is still empty.
     */
    fun addEntry(name: String, compress: Boolean, content: (OutputStream) -> Unit) {
        require(!names.isEmpty || name == BackupFormat.MANIFEST_ENTRY) { "the first entry must be the manifest" }
        require(BackupEntryName.isValid(name)) { "invalid entry name" }
        require(names.add(name)) { "entry name repeats, or is a file and a directory at once" }
        zip.setLevel(if (compress) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
        zip.putNextEntry(ZipEntry(name))
        content(object : FilterOutputStream(zip) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                out.write(b, off, len)
            }

            override fun close() {
                flush()
            }
        })
        zip.closeEntry()
    }

    /** Ends the content and writes the chunk flagged as last. Nothing may be added after it. */
    fun finish() {
        zip.finish()
        encrypting.finish()
    }

    override fun close() {
        zip.close()
    }
}
