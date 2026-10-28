package app.knotwork.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Keeps every foreground service with an Android time limit under a budget that
 * stops it before the limit runs out.
 *
 * **Why.** Since Android 14 a `shortService` foreground service may run about
 * three minutes, and since Android 15 a `dataSync` or `mediaProcessing` one six
 * hours a day while the app is out of sight. One still in the foreground once
 * its time runs out takes the app down — `dataSync` and `mediaProcessing` with a
 * crash, `shortService` with an ANR. The app's one such service — model downloads,
 * promoted by WorkManager with `dataSync` — crashed that way in the field
 * although WorkManager handles the timeout callback, and it now stops itself
 * first through `DownloadForegroundBudget`. A second time-limited type, or a
 * second `dataSync` user outside that budget, would bring the crash back
 * without a sound.
 *
 * **What it reads.** The merged-manifest expectations in `config/merged-manifest/`,
 * which `verify<Variant>ReleaseMergedManifest` pins against the real merged
 * manifest — so a type a library brings in shows up there as its permission;
 * the main manifest's `foregroundServiceType` attributes; and the production
 * sources ([ProductionSources]). The expectation files are declared Test-task
 * inputs in `app/build.gradle.kts`.
 *
 * **What it cannot see.** `shortService` needs no permission of its own, so one
 * declared only by a library is invisible here. A type passed as a raw number
 * instead of the platform's named constant is not matched.
 */
class TimeLimitedForegroundServiceGuardTest {

    @Test
    fun `the only time-limited foreground-service permission either flavour ships is dataSync`() {
        EXPECTATION_FILES.forEach { name ->
            val permissions = File(expectationDirectory(), name).readLines()
                .map { it.substringBefore('#').trim() }
                .filter { it.startsWith(PERMISSION_LINE) }
                .map { it.removePrefix(PERMISSION_LINE).substringBefore(' ') }
                .filter { it in TIME_LIMITED_PERMISSIONS }
                .toSet()

            assertEquals(
                "$name: a foreground-service type with an Android time limit was added. Give it a budget that " +
                    "stops it before the limit, as DownloadForegroundBudget does for dataSync, then list it here.",
                setOf(DATA_SYNC_PERMISSION),
                permissions,
            )
        }
    }

    @Test
    fun `the main manifest declares no time-limited type but dataSync`() {
        val manifest = File(ProductionSources.moduleDirectory(), "src/main/AndroidManifest.xml")
        val services = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
            .getElementsByTagName("service")
        val types = (0 until services.length)
            .map { (services.item(it) as Element).getAttribute("android:foregroundServiceType") }
            .flatMap { it.split('|') }
            .toSet()

        assertEquals(setOf(DATA_SYNC_TYPE), types intersect TIME_LIMITED_TYPES)
    }

    @Test
    fun `every production start of a dataSync foreground service books against the download budget`() {
        val dataSyncUsers = ProductionSources.code.filterValues { DATA_SYNC_CONSTANT.containsMatchIn(it) }
        val unbudgeted = dataSyncUsers.filterValues { BUDGET !in it }.keys.map { it.substringAfterLast('/') }

        assertEquals(
            "a file starts a dataSync foreground service without DownloadForegroundBudget; Android crashes " +
                "the app when such a service outlives its daily six hours",
            emptyList<String>(),
            unbudgeted,
        )
        // Keeps the rule above from passing vacuously on an empty or wrong tree.
        assertTrue(
            "ModelDownloadWorker not found among the dataSync users",
            dataSyncUsers.keys.any { it.endsWith("/data/services/ModelDownloadWorker.kt") },
        )
    }

    @Test
    fun `no production code starts a foreground service of another time-limited type`() {
        val users = ProductionSources.code
            .filterValues { OTHER_TIME_LIMITED_CONSTANT.containsMatchIn(it) }
            .keys
            .map { it.substringAfterLast('/') }

        assertEquals(
            "mediaProcessing and shortService have Android time limits too, and nothing here budgets them",
            emptyList<String>(),
            users,
        )
    }

    /** `config/merged-manifest/` at the repository root. */
    private fun expectationDirectory(): File =
        File(ProductionSources.moduleDirectory().parentFile, "config/merged-manifest")

    private companion object {
        /** The merged-manifest expectation of each shipping flavour. */
        val EXPECTATION_FILES = listOf("fullRelease.txt", "fossRelease.txt")

        /** Prefix of a requested-permission line in an expectation file. */
        const val PERMISSION_LINE = "uses-permission "

        /** The permission a `dataSync` foreground service requires. */
        const val DATA_SYNC_PERMISSION = "android.permission.FOREGROUND_SERVICE_DATA_SYNC"

        /** Permissions of the foreground-service types that have a time limit. */
        val TIME_LIMITED_PERMISSIONS = setOf(
            DATA_SYNC_PERMISSION,
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING",
        )

        /** The manifest name of the `dataSync` type. */
        const val DATA_SYNC_TYPE = "dataSync"

        /** Manifest names of the foreground-service types that have a time limit. */
        val TIME_LIMITED_TYPES = setOf(DATA_SYNC_TYPE, "mediaProcessing", "shortService")

        /** The platform constant that starts a `dataSync` foreground service. */
        val DATA_SYNC_CONSTANT = Regex("""\bFOREGROUND_SERVICE_TYPE_DATA_SYNC\b""")

        /** Platform constants of the other time-limited types. */
        val OTHER_TIME_LIMITED_CONSTANT =
            Regex("""\bFOREGROUND_SERVICE_TYPE_(?:MEDIA_PROCESSING|SHORT_SERVICE)\b""")

        /** The budget a `dataSync` user must book against. */
        const val BUDGET = "DownloadForegroundBudget"
    }
}
