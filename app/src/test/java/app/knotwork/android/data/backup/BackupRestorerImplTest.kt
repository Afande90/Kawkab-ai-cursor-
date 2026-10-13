package app.knotwork.android.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.knotwork.android.data.local.crypto.FakeAeadCipher
import app.knotwork.android.data.local.crypto.KeystoreBackedPrefsStore
import app.knotwork.android.domain.backup.BackupCounts
import app.knotwork.android.domain.backup.BackupFormat
import app.knotwork.android.domain.backup.BackupFormatException
import app.knotwork.android.domain.backup.BackupHeader
import app.knotwork.android.domain.backup.BackupKdfParams
import app.knotwork.android.domain.backup.BackupManifest
import app.knotwork.android.domain.backup.BackupManifestJson
import app.knotwork.android.domain.backup.BackupModelEntry
import app.knotwork.android.domain.backup.BackupReadFailure
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.RestoreFailure
import app.knotwork.android.domain.backup.RestoreReport
import app.knotwork.android.domain.models.DbPassphraseUnavailableException
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.services.DownloadedModelFile
import app.knotwork.android.domain.services.DownloadedModelFiles
import app.knotwork.android.domain.services.TaskScheduler
import app.knotwork.android.domain.services.TriggerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * [BackupRestorerImpl] with [RestoreStager] and [RestoreFinisher] over real archives, a real DataStore and a
 * Keystore-backed store on a fake cipher; the database step is a recording fake (the real one is tested on a
 * device). Staging checks everything and changes nothing outside the staging area; committing stops this
 * phone's background work; the start completes the rest, by policy.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRestorerImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val password = "correct horse battery staple"
    private val fastKdf = BackupKdfParams(memoryKiB = 64, iterations = 1, parallelism = 1)
    private val derivation = Argon2idBackupKeyDerivation { Long.MAX_VALUE }
    private val phoneKey = ByteArray(BackupFormat.KEY_LENGTH) { 9 }

    private lateinit var layout: RestoreLayout
    private lateinit var stagedEntries: RestoreStagedEntries
    private lateinit var dataStoreFile: File
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            migrations = listOf(RestoredPreferencesMigration(layout)),
            scope = dataStoreScope,
            produceFile = { dataStoreFile },
        )
    }

    private val preparer = RecordingPreparer()
    private val apiKeys = RecordingOwner(BackedUpSecrets.API_KEYS_STORE)
    private val settingsSecrets = RecordingOwner(BackedUpSecrets.SETTINGS_SECRETS_STORE)
    private val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    private val triggerScheduler = mockk<TriggerScheduler>(relaxed = true)
    private val triggerRepository = mockk<TriggerRepository> {
        every { observeActiveTriggers() } returns flowOf(listOf(trigger("t1"), trigger("t2")))
    }
    private val models = mockk<LocalModelRepository>(relaxed = true)
    private val modelFiles = mockk<DownloadedModelFiles>()
    private var usableBytes = Long.MAX_VALUE

    @Before
    fun setUp() {
        val app = temp.newFolder("app")
        layout = RestoreLayout(
            root = File(app, "files/restore"),
            liveDatabase = File(app, "databases/agent_database.db"),
            liveWorkspace = File(app, "files/agent_workspace"),
            liveAttachments = File(app, "files/attachments"),
        )
        dataStoreFile = File(app, "files/datastore/agent_preferences.preferences_pb")
        stagedEntries = RestoreStagedEntries(
            KeystoreBackedPrefsStore(context, "restore_staging_test", "test.restore", FakeAeadCipher()),
        )
        coEvery { models.getAllModels() } returns flowOf(
            listOf(
                model(1, "gemma.litertlm", "/old/phone/gemma.litertlm"),
                model(2, "qwen.litertlm", "/old/qwen.litertlm"),
            ),
        )
        coEvery { modelFiles.list() } returns listOf(DownloadedModelFile("gemma.litertlm", "/here/gemma.litertlm", 10))
    }

    @After
    fun tearDown() = runTest {
        dataStoreScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
    }

    // --- Staging ---

    @Test
    fun `given a valid archive when staged then everything is checked into staging and nothing else changes`() =
        runTest {
            // When
            val staged = restorer(testScheduler).stage(input(archive()), password.toCharArray()) { }

            // Then
            assertEquals("0.12.0", staged.info.appVersionName)
            assertEquals(manifest(), staged.manifest)
            assertArrayEquals(DATABASE_BYTES, layout.stagedDatabase.readBytes())
            assertTrue(layout.stagedPreferences.isFile)
            assertEquals(manifest(), BackupManifestJson.decode(layout.stagedManifest.readText()))
            assertEquals("plan", File(layout.stagedWorkspace, "notes/plan.md").readText())
            assertEquals("photo", File(layout.stagedAttachments, "1.jpg").readText())
            assertEquals(
                mapOf(BackedUpSecrets.API_KEYS_STORE to mapOf("openai_model" to "gpt-5")),
                stagedEntries.entries(RestoreStagedEntries.Kind.PROVIDERS),
            )
            assertTrue(stagedEntries.has(RestoreStagedEntries.Kind.SECRETS))
            assertFalse("staging is not committing", layout.isCommitted)
            assertFalse("the app's own database is untouched", layout.liveDatabase.exists())
        }

    @Test
    fun `given a valid archive when staged then the database is prepared with both keys, wiped afterwards`() = runTest {
        // When
        restorer(testScheduler).stage(input(archive()), password.toCharArray()) { }

        // Then
        assertEquals(1, preparer.calls.size)
        val call = preparer.calls.single()
        assertArrayEquals(ARCHIVE_DB_KEY, call.archiveKeyAtCall)
        assertArrayEquals(phoneKey, call.phoneKeyAtCall)
        assertTrue("the archive's key is wiped", call.archiveKey.all { it == 0.toByte() })
        assertTrue("this phone's key is wiped", call.phoneKey.all { it == 0.toByte() })
    }

    @Test
    fun `given the wrong password when staged then it is refused and nothing is staged`() = runTest {
        // When
        val failure = runCatchingFormat {
            restorer(testScheduler).stage(input(archive()), "not the password".toCharArray()) { }
        }

        // Then
        assertEquals(BackupReadFailure.WRONG_PASSWORD, failure.failure)
        assertNothingStaged()
    }

    @Test
    fun `given an archive from a newer schema when staged then it is refused with the version that wrote it`() =
        runTest {
            // When
            val failure = runCatchingRestore {
                restorer(testScheduler).stage(input(archive(schemaVersion = 99)), password.toCharArray()) { }
            }

            // Then
            assertEquals(RestoreFailure.NEWER_VERSION, failure.failure)
            assertEquals("0.12.0", failure.writerVersionName)
            assertNothingStaged()
        }

    @Test
    fun `given too little free space when staged then it is refused before unpacking, with the bytes needed`() =
        runTest {
            // Given
            usableBytes = 1_000

            // When
            val failure = runCatchingRestore {
                restorer(testScheduler).stage(input(archive()), password.toCharArray()) { }
            }

            // Then
            assertEquals(RestoreFailure.NO_SPACE, failure.failure)
            assertTrue(failure.neededBytes > 1_000)
            assertNothingStaged()
        }

    @Test
    fun `given an archive without a required entry when staged then it is damaged and nothing is left`() = runTest {
        // When
        val failure = runCatchingFormat {
            restorer(testScheduler).stage(
                input(archive(omit = setOf(BackupFormat.PROVIDERS_ENTRY))),
                password.toCharArray(),
            ) { }
        }

        // Then
        assertEquals(BackupReadFailure.DAMAGED, failure.failure)
        assertNothingStaged()
    }

    @Test
    fun `given an entry this build does not know when staged then it is skipped`() = runTest {
        // When
        restorer(testScheduler).stage(
            input(archive(extra = listOf("future/feature.bin" to byteArrayOf(1, 2, 3)))),
            password.toCharArray(),
        ) { }

        // Then
        assertFalse(File(layout.staging, "future").exists())
        assertTrue(layout.stagedManifest.isFile)
    }

    @Test
    fun `given this phone's database key cannot be read when staged then the restore says so`() = runTest {
        // When
        val failure = runCatchingRestore {
            restorer(testScheduler, phoneKey = {
                throw DbPassphraseUnavailableException(DbPassphraseUnavailableException.Reason.DECRYPTION_FAILED)
            }).stage(input(archive()), password.toCharArray()) { }
        }

        // Then
        assertEquals(RestoreFailure.PHONE_KEY_UNAVAILABLE, failure.failure)
        assertNothingStaged()
    }

    @Test
    fun `given a database that fails its checks when staged then the failure is passed on and nothing is left`() =
        runTest {
            // Given
            preparer.failure = BackupRestoreException(RestoreFailure.DATABASE_UNUSABLE, "pages")

            // When
            val failure = runCatchingRestore {
                restorer(testScheduler).stage(input(archive()), password.toCharArray()) { }
            }

            // Then
            assertEquals(RestoreFailure.DATABASE_UNUSABLE, failure.failure)
            assertNothingStaged()
        }

    @Test
    fun `given an archive the exporter wrote when staged then it is accepted`() = runTest {
        // Given — the real writer of the format, with its own entries and order.
        val out = ByteArrayOutputStream()
        exporter(testScheduler).export(out, password.toCharArray(), includeSecrets = true) { }

        // When
        val staged = restorer(testScheduler).stage(input(out.toByteArray()), password.toCharArray()) { }

        // Then
        assertEquals(1, staged.manifest.counts.chats)
        assertArrayEquals(DATABASE_BYTES, layout.stagedDatabase.readBytes())
        assertTrue(stagedEntries.has(RestoreStagedEntries.Kind.SECRETS))
    }

    // --- Committing ---

    @Test
    fun `given a staged restore when committed then the marker is written before background work is stopped`() =
        runTest {
            // Given
            val restorer = restorer(testScheduler)
            restorer.stage(input(archive()), password.toCharArray()) { }
            var committedWhenStopping = false
            coEvery { taskScheduler.cancelAllBackgroundRuns() } answers { committedWhenStopping = layout.isCommitted }

            // When
            restorer.commit(applySecrets = true)

            // Then — past the marker the restore completes whatever happens to the process.
            assertTrue(committedWhenStopping)
            assertEquals(RestoreLayout.CommitMarker(applySecrets = true, backgroundStopped = true), layout.readCommit())
            coVerifyOrder {
                taskScheduler.cancelAllBackgroundRuns()
                triggerScheduler.stopWatching(listOf("t1", "t2"))
            }
        }

    @Test
    fun `given background work cannot be stopped when committed then the restore is committed all the same`() =
        runTest {
            // Given
            val restorer = restorer(testScheduler)
            restorer.stage(input(archive()), password.toCharArray()) { }
            coEvery { taskScheduler.cancelAllBackgroundRuns() } throws IllegalStateException("runtime unavailable")

            // When
            restorer.commit(applySecrets = false)

            // Then — the start cancels the queued runs instead.
            assertEquals(
                RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = false),
                layout.readCommit(),
            )
        }

    @Test(expected = IllegalStateException::class)
    fun `given nothing staged when committed then it is refused`() = runTest {
        restorer(testScheduler).commit(applySecrets = false)
    }

    @Test
    fun `given a committed restore when another is staged or it is discarded then it stays, but a wipe erases it`() =
        runTest {
            // Given
            val restorer = restorer(testScheduler)
            restorer.stage(input(archive()), password.toCharArray()) { }
            restorer.commit(applySecrets = false)

            // Then
            val refused = try {
                restorer.stage(input(archive()), password.toCharArray()) { }
                null
            } catch (e: IllegalStateException) {
                e
            }
            assertTrue("a second restore is not staged over a committed one", refused != null)
            assertFalse(restorer.discard())
            assertTrue(layout.isCommitted)
            assertTrue(restorer.erase())
            assertFalse(layout.staging.exists())
        }

    @Test
    fun `given a staged restore when discarded then the staging area and the kept entries are gone`() = runTest {
        // Given
        val restorer = restorer(testScheduler)
        restorer.stage(input(archive()), password.toCharArray()) { }

        // When
        val discarded = restorer.discard()

        // Then
        assertTrue(discarded)
        assertNothingStaged()
    }

    // --- Finishing at the start ---

    @Test
    fun `given a committed restore when the app starts then settings, entries, models and the report follow`() =
        runTest {
            // Given — this phone's settings, then the restore staged, committed and its files moved.
            seedPhoneSettings()
            val restorer = restorer(testScheduler)
            restorer.stage(input(archive()), password.toCharArray()) { }
            restorer.commit(applySecrets = true)
            PendingRestoreApplier.applyIfCommitted(layout)

            // When
            restorer.finishPending()

            // Then — settings by policy.
            val settings = dataStore.data.first()
            assertEquals(0.2f, settings[floatPreferencesKey("temperature")])
            assertEquals("Be brief.", settings[stringPreferencesKey("system_prompt_prefix")])
            assertEquals("GPU", settings[stringPreferencesKey("local_model_backend")])
            // Entries through their owners, secrets included.
            assertEquals(
                listOf(mapOf("openai_model" to "gpt-5"), mapOf("openai_api_key" to "sk-test")),
                apiKeys.restored,
            )
            assertEquals(listOf(mapOf("hugging_face_token" to "hf_x")), settingsSecrets.restored)
            // The registry points at this phone's file; the missing model is reported.
            coVerify { models.updateModel(model(1, "gemma.litertlm", "/here/gemma.litertlm")) }
            assertEquals(
                RestoreReport(
                    modelsToDownload = listOf(BackupModelEntry("qwen.litertlm", 2_000, null, "https://q")),
                    scheduledTasksLeftBehind = 2,
                    secretsApplied = true,
                ),
                restorer.report(),
            )
            // Nothing staged is left; the background work was already stopped at the commit.
            assertFalse(layout.staging.exists())
            assertFalse(stagedEntries.has(RestoreStagedEntries.Kind.SECRETS))
            coVerify(exactly = 1) { taskScheduler.cancelAllBackgroundRuns() }
        }

    @Test
    fun `given secrets were not chosen when the app starts then only the provider settings are written`() = runTest {
        // Given
        val restorer = restorer(testScheduler)
        restorer.stage(input(archive()), password.toCharArray()) { }
        restorer.commit(applySecrets = false)
        PendingRestoreApplier.applyIfCommitted(layout)

        // When
        restorer.finishPending()

        // Then
        assertEquals(listOf(mapOf("openai_model" to "gpt-5")), apiKeys.restored)
        assertTrue(settingsSecrets.restored.isEmpty())
        assertFalse(restorer.report()!!.secretsApplied)
    }

    @Test
    fun `given the process ended before background work was stopped when the app starts then the runs are cancelled`() =
        runTest {
            // Given
            val restorer = restorer(testScheduler)
            restorer.stage(input(archive()), password.toCharArray()) { }
            layout.writeCommit(RestoreLayout.CommitMarker(applySecrets = false, backgroundStopped = false))
            PendingRestoreApplier.applyIfCommitted(layout)

            // When
            restorer.finishPending()

            // Then
            coVerify(exactly = 1) { taskScheduler.cancelAllBackgroundRuns() }
        }

    @Test
    fun `given a committed restore whose files are not all moved when the app starts then nothing is finished`() =
        runTest {
            // Given — the start could not move the files.
            val restorer = restorer(testScheduler)
            restorer.stage(input(archive()), password.toCharArray()) { }
            restorer.commit(applySecrets = true)

            // When
            restorer.finishPending()

            // Then
            assertTrue(layout.stagedDatabase.exists())
            assertTrue(stagedEntries.has(RestoreStagedEntries.Kind.SECRETS))
            assertTrue(apiKeys.restored.isEmpty())
        }

    @Test
    fun `given a restore staged but never committed when the app starts then it is removed`() = runTest {
        // Given
        val restorer = restorer(testScheduler)
        restorer.stage(input(archive()), password.toCharArray()) { }

        // When
        restorer.finishPending()

        // Then
        assertNothingStaged()
    }

    @Test
    fun `given a report when dismissed then there is none`() = runTest {
        // Given
        RestoreReportJson.write(
            layout.report,
            RestoreReport(emptyList(), scheduledTasksLeftBehind = 0, secretsApplied = false),
        )
        val restorer = restorer(testScheduler)

        // When
        restorer.dismissReport()

        // Then
        assertNull(restorer.report())
    }

    // --- Helpers ---

    private fun restorer(
        scheduler: TestCoroutineScheduler,
        phoneKey: () -> ByteArray = { this.phoneKey.copyOf() },
    ): BackupRestorerImpl {
        val io = UnconfinedTestDispatcher(scheduler)
        return BackupRestorerImpl(
            layout = layout,
            stager = RestoreStager(layout, derivation, preparer, stagedEntries, phoneKey) { usableBytes },
            finisher = RestoreFinisher(
                layout,
                stagedEntries,
                setOf(apiKeys, settingsSecrets),
                dataStore,
                models,
                modelFiles,
                taskScheduler,
            ),
            taskScheduler = taskScheduler,
            triggerRepository = triggerRepository,
            triggerScheduler = triggerScheduler,
            ioDispatcher = io,
        )
    }

    private fun exporter(scheduler: TestCoroutineScheduler): BackupExporterImpl {
        val workspace = temp.newFolder("export-workspace").also { File(it, "a.md").writeText("a") }
        val attachments = temp.newFolder("export-attachments")
        val snapshotDir = temp.newFolder("export-snapshot")
        val source = object : BackupContentSource {
            override suspend fun collect(includeSecrets: Boolean, snapshotFile: File, createdAtEpochMillis: Long) =
                BackupContent(
                    snapshotFile = snapshotFile.also { it.writeBytes(DATABASE_BYTES) },
                    snapshotKey = ARCHIVE_DB_KEY.copyOf(),
                    preferences = PreferencesBackupJson.encode(mutablePreferencesOf()).toByteArray(),
                    providers = PROVIDERS.toByteArray(),
                    secrets = SECRETS.toByteArray(),
                    workspace = BackupFileSource({ workspace }, { attachments }).workspace(),
                    attachments = BackupFileSource({ workspace }, { attachments }).attachments(),
                    manifest = manifest().copy(createdAtEpochMillis = createdAtEpochMillis, secretsIncluded = true),
                )
        }
        return BackupExporterImpl(
            source = source,
            keyDerivation = derivation,
            snapshotDirectory = { snapshotDir },
            kdfParams = fastKdf,
            chunkSize = BackupFormat.MIN_CHUNK_SIZE,
            now = { CREATED_AT },
            ioDispatcher = UnconfinedTestDispatcher(scheduler),
        )
    }

    private fun archive(
        schemaVersion: Int = 70,
        omit: Set<String> = emptySet(),
        extra: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val entries = listOf(
            BackupFormat.MANIFEST_ENTRY to BackupManifestJson.encode(manifest()).toByteArray(),
            BackupFormat.DATABASE_KEY_ENTRY to ARCHIVE_DB_KEY,
            BackupFormat.PREFERENCES_ENTRY to PreferencesBackupJson.encode(
                mutablePreferencesOf(
                    floatPreferencesKey("temperature") to 0.2f,
                    stringPreferencesKey("system_prompt_prefix") to "Be brief.",
                    stringPreferencesKey("local_model_backend") to "NPU",
                ),
            ).toByteArray(),
            BackupFormat.PROVIDERS_ENTRY to PROVIDERS.toByteArray(),
            BackupFormat.SECRETS_ENTRY to SECRETS.toByteArray(),
            BackupFormat.DATABASE_ENTRY to DATABASE_BYTES,
            BackupFormat.WORKSPACE_PREFIX + "notes/plan.md" to "plan".toByteArray(),
            BackupFormat.ATTACHMENTS_PREFIX + "1.jpg" to "photo".toByteArray(),
        ).filterNot { it.first in omit } + extra
        val salt = Random(1).nextBytes(BackupFormat.SALT_LENGTH)
        val header = BackupHeader(
            formatVersion = BackupFormat.FORMAT_VERSION,
            appVersionName = "0.12.0",
            appVersionCode = 17,
            // Written by the other distribution: an archive restores into either.
            flavor = "foss",
            schemaVersion = schemaVersion,
            createdAtEpochMillis = CREATED_AT,
            contentBytes = entries.sumOf { it.second.size.toLong() },
            kdfParams = fastKdf,
            salt = salt,
            chunkSize = BackupFormat.MIN_CHUNK_SIZE,
        )
        val bytes = ByteArrayOutputStream()
        BackupArchiveWriter(bytes, header, derivation.deriveKeys(password.toCharArray(), salt, fastKdf)).use { writer ->
            entries.forEach { (name, content) -> writer.addEntry(name, compress = true) { it.write(content) } }
            writer.finish()
        }
        return bytes.toByteArray()
    }

    private fun manifest() = BackupManifest(
        createdAtEpochMillis = CREATED_AT,
        schemaVersion = 70,
        secretsIncluded = true,
        counts = BackupCounts(chats = 1, memories = 2, pipelines = 3, workspaceFiles = 1, attachments = 1),
        scheduledTasks = 2,
        models = listOf(BackupModelEntry("gemma.litertlm", 1_000, null, null)),
    )

    private fun model(id: Long, name: String, path: String) = LocalModel(
        id = id,
        name = name,
        path = path,
        size = id * 1_000,
        isActive = id == 1L,
        sourceUrl = if (id == 2L) "https://q" else null,
    )

    private fun trigger(id: String): Trigger = mockk { every { this@mockk.id } returns id }

    private suspend fun seedPhoneSettings() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { dataStoreFile })
        store.edit {
            it[stringPreferencesKey("local_model_backend")] = "GPU"
            it[floatPreferencesKey("temperature")] = 0.9f
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
    }

    private fun input(bytes: ByteArray) = ByteArrayInputStream(bytes)

    private fun assertNothingStaged() {
        assertFalse("the staging area is removed", layout.staging.exists())
        assertFalse("no entries are kept", stagedEntries.has(RestoreStagedEntries.Kind.PROVIDERS))
    }

    private suspend fun runCatchingFormat(block: suspend () -> Unit): BackupFormatException = try {
        block()
        throw AssertionError("the archive was read")
    } catch (e: BackupFormatException) {
        e
    }

    private suspend fun runCatchingRestore(block: suspend () -> Unit): BackupRestoreException = try {
        block()
        throw AssertionError("the archive was restored")
    } catch (e: BackupRestoreException) {
        e
    }

    /** Records what it was given and how the keys looked then; succeeds or fails as told. */
    private class RecordingPreparer : StagedDatabasePreparer {
        class Call(val archiveKey: ByteArray, val phoneKey: ByteArray) {
            val archiveKeyAtCall: ByteArray = archiveKey.copyOf()
            val phoneKeyAtCall: ByteArray = phoneKey.copyOf()
        }

        val calls = mutableListOf<Call>()
        var failure: BackupRestoreException? = null

        override fun prepare(file: File, archiveKey: ByteArray, phoneKey: ByteArray): Int {
            calls += Call(archiveKey, phoneKey)
            failure?.let { throw it }
            return 70
        }
    }

    /** An owner that records what it is asked to restore. */
    private class RecordingOwner(override val storeName: String) : BackedUpSecrets {
        val restored = mutableListOf<Map<String, String>>()

        override suspend fun alwaysEntries(): Map<String, String> = emptyMap()

        override suspend fun secretEntries(): Map<String, String> = emptyMap()

        override suspend fun restore(entries: Map<String, String>): Int {
            restored += entries
            return entries.size
        }
    }

    private companion object {
        const val CREATED_AT = 1_791_849_600_000L
        val DATABASE_BYTES = Random(5).nextBytes(10_000)
        val ARCHIVE_DB_KEY = ByteArray(BackupFormat.KEY_LENGTH) { 7 }
        val PROVIDERS = JSONObject()
            .put("entriesVersion", 1)
            .put("stores", JSONObject().put(BackedUpSecrets.API_KEYS_STORE, JSONObject().put("openai_model", "gpt-5")))
            .toString()
        val SECRETS = JSONObject()
            .put("entriesVersion", 1)
            .put(
                "stores",
                JSONObject()
                    .put(BackedUpSecrets.API_KEYS_STORE, JSONObject().put("openai_api_key", "sk-test"))
                    .put(BackedUpSecrets.SETTINGS_SECRETS_STORE, JSONObject().put("hugging_face_token", "hf_x")),
            )
            .toString()
    }
}
