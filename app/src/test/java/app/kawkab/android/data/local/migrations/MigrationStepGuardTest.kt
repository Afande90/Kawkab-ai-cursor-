package app.kawkab.android.data.local.migrations

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.data.local.AppDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Runs every step of [ALL_MIGRATIONS] on real SQLite and validates the schema it leaves against the
 * schema Room exported for the step's target version — one test case per step, in `check`.
 *
 * **Why.** The instrumented `AppDatabaseMigrationHelperTest` validates the whole list only on the
 * emulator matrix, and checks steps one by one only where someone wrote a test for them: three
 * reachable steps (33→34, 35→36, 36→37) ran only inside the end-to-end chain, checked against the
 * final schema alone. A restored backup upgrades its database through this list on the user's phone,
 * so every step is validated here by construction: a new migration is covered the moment it is
 * registered, and fails the build if its exported schema is missing or its SQL leaves a schema that
 * differs from it. Data preservation stays with the per-migration tests — this guard checks only the
 * schema each step produces.
 *
 * **Steps without an exported schema.** Schemas are exported from version 23 on, so the steps that
 * start below it cannot be validated; they are listed in [UNEXPORTED_STEPS]. No build under this
 * application id carried a schema below 28 (the tags v0.1.0–v0.3.0 were built under another
 * application id), so no installed database can run them. The list cannot grow: a listed step whose starting schema
 * exists, or one the list names but [ALL_MIGRATIONS] does not register, fails its case.
 *
 * The exported schemas reach the JVM through the `debug` source set's assets: Robolectric reads only
 * the tested variant's merged assets, never the `test` source set's (`app/build.gradle.kts`).
 *
 * @property step The registered migration this case checks, or `null` for a baseline entry that
 *   names a step the list does not register.
 * @property startVersion The version the step upgrades from.
 * @property endVersion The version the step upgrades to.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class MigrationStepGuardTest(
    private val step: Migration?,
    private val startVersion: Int,
    private val endVersion: Int,
) {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `given a step when run on its exported start schema then it leaves its exported target schema`() {
        // Given
        val migration = step ?: throw AssertionError(
            "UNEXPORTED_STEPS lists $startVersion->$endVersion, which ALL_MIGRATIONS does not register: " +
                "remove it from the list",
        )
        if ((startVersion to endVersion) in UNEXPORTED_STEPS) {
            assertFalse(
                "a schema for version $startVersion exists now: validate $startVersion->$endVersion and remove " +
                    "it from UNEXPORTED_STEPS",
                hasExportedSchema(startVersion),
            )
            return
        }
        assertTrue(
            "no exported schema for $startVersion or $endVersion: build once so Room writes app/schemas/, " +
                "and commit the file",
            hasExportedSchema(startVersion) && hasExportedSchema(endVersion),
        )
        val name = "step-$startVersion-$endVersion.db"
        helper.createDatabase(name, startVersion).close()

        // When / Then — Room compares every table, view and index with the exported target schema.
        helper.runMigrationsAndValidate(name, endVersion, true, migration).close()
    }

    private fun hasExportedSchema(version: Int): Boolean =
        RuntimeEnvironment.getApplication().assets.list(SCHEMA_FOLDER).orEmpty().contains("$version.json")

    companion object {
        /** The asset folder Room exports [AppDatabase]'s schemas into, named after its class. */
        private val SCHEMA_FOLDER: String = AppDatabase::class.java.name

        /**
         * Steps whose starting schema was never exported, so no case can validate them. Every one starts
         * below 28, the oldest schema a build under this application id shipped.
         */
        private val UNEXPORTED_STEPS: Set<Pair<Int, Int>> = (9..22).map { it to it + 1 }.toSet()

        /**
         * One case per registered step, plus one per baseline entry the list does not register.
         *
         * @return `[migration-or-null, startVersion, endVersion]` triples, in upgrade order.
         */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}->{2}")
        fun steps(): List<Array<Any?>> {
            val registered = ALL_MIGRATIONS.map { arrayOf<Any?>(it, it.startVersion, it.endVersion) }
            val registeredSteps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
            val stale = (UNEXPORTED_STEPS - registeredSteps).map { (start, end) -> arrayOf<Any?>(null, start, end) }
            return registered + stale
        }
    }
}
