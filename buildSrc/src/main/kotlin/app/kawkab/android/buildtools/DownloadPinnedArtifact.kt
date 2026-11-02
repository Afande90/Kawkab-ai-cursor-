package app.kawkab.android.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Downloads a public build input and verifies its pinned bytes before exposing it to Android. */
@DisableCachingByDefault(because = "The upstream artifact is already immutable and locally up-to-date checked")
abstract class DownloadPinnedArtifact : DefaultTask() {
    /** Immutable upstream release artifact URL. */
    @get:Input abstract val url: Property<String>

    /** Expected SHA-256; a changed upstream file fails the build. */
    @get:Input abstract val sha256: Property<String>

    /** Build-directory output, never a tracked binary. */
    @get:OutputFile abstract val outputFile: RegularFileProperty

    /** Download to a temporary file so an interrupted build cannot package incomplete bytes. */
    @TaskAction
    fun download() {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val temporary = File(target.parentFile, target.name + ".part")
        try {
            val connection = URI(url.get()).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            connection.getInputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            val hash = MessageDigest.getInstance("SHA-256")
            temporary.inputStream().use { input ->
                val buffer = ByteArray(65_536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            check(hash.digest().joinToString("") { "%02x".format(it) } == sha256.get()) {
                "Checksum mismatch for ${target.name}"
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
