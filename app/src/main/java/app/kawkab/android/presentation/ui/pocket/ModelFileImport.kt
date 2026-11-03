package app.kawkab.android.presentation.ui.pocket

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Copies a model file the owner picked (for example from Downloads) into Kawkab's model folder. */
internal object ModelFileImport {
    /** Model formats the phone's engines load. */
    val EXTENSIONS = listOf(".litertlm", ".gguf")

    /**
     * Copies [uri] into the app's model folder under its own name and returns the copy. A partial copy
     * never replaces an existing model: bytes go to a temporary file that is renamed only when complete.
     */
    fun copy(context: Context, uri: Uri): File {
        val name = displayName(context, uri)
        require(accepts(name)) { "ONLY .litertlm OR .gguf FILES ARE MODELS." }
        val folder = requireNotNull(context.getExternalFilesDir(null)) { "PHONE STORAGE IS NOT AVAILABLE." }
        val target = File(folder, name)
        val partial = File(folder, "$name.part")
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "THAT FILE CANNOT BE OPENED." }
            input.use { source -> partial.outputStream().use { source.copyTo(it, BUFFER) } }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "COULD NOT SAVE THE MODEL." }
        } finally {
            partial.delete()
        }
        return target
    }

    /** True for a plain file name with a model extension, never a path. */
    fun accepts(name: String): Boolean =
        name.isNotBlank() && '/' !in name && EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""

    private const val BUFFER = 1 shl 20
}
