package app.kawkab.android.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Separates versioned ORT cores so the Java bridge cannot replace sherpa's native dependency. */
@DisableCachingByDefault(because = "Uses a checksum-pinned Linux build tool; outputs remain locally up to date")
abstract class IsolateOrtRuntime @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    /** Original signed Maven artifact, verified before repacking. */
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputAar: RegularFileProperty

    /** Pinned tool download, ELF validation and deterministic artifact isolation. */
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val script: RegularFileProperty

    /** Local build tool cache, never committed or included in the APK. */
    @get:LocalState abstract val tools: DirectoryProperty

    /** Repacked AAR containing a uniquely named Java runtime and its bound JNI bridge. */
    @get:OutputFile abstract val outputAar: RegularFileProperty

    /** Runs for every APK/test build through preBuild and the dependency's builtBy relationship. */
    @TaskAction
    fun isolate() {
        exec.exec { spec ->
            spec.commandLine(
                "python3", script.get().asFile.absolutePath, inputAar.get().asFile.absolutePath,
                outputAar.get().asFile.absolutePath, "--tools", tools.get().asFile.absolutePath,
            )
        }
    }
}
