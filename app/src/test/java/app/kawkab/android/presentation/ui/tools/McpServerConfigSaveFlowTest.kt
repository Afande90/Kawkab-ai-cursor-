package app.kawkab.android.presentation.ui.tools

import androidx.lifecycle.SavedStateHandle
import app.kawkab.android.data.mcp.McpClient
import app.kawkab.android.data.mcp.McpClientFactory
import app.kawkab.android.data.mcp.McpConnectionPool
import app.kawkab.android.data.repositories.McpServerRepositoryImpl
import app.kawkab.android.domain.models.AgentTool
import app.kawkab.android.domain.models.McpAuth
import app.kawkab.android.domain.models.McpConnectionStatus
import app.kawkab.android.domain.models.McpServerConfig
import app.kawkab.android.domain.models.UpdateMcpServerResult
import app.kawkab.android.domain.repositories.SettingsRepository
import app.kawkab.android.domain.repositories.ToolRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.TestTimeSource

/**
 * Saving the MCP server form, as the Tools screen beneath it sees it: the real [ToolsViewModel],
 * [McpServerConfigViewModel] and [McpServerRepositoryImpl] over one connection pool, with only the
 * MCP client and the settings faked.
 *
 * The form used to disconnect a server before saving it, and only the Tools screen reconnects — when
 * the server's config changed. A save that changed nothing, or one refused for a URL another server
 * holds, left the row on "Connecting…" until *Refresh*. These tests hold every outcome of a save to a
 * row that ends up connected with the config that is stored.
 *
 * The settings list re-emits on every change, an unchanged one included, as `ToolSettingsStore` does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpServerConfigSaveFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    private val serverA = McpServerConfig(url = "https://a.example", name = "A", auth = McpAuth.Bearer(token = "tok"))
    private val serverB = McpServerConfig(url = "https://b.example", name = "B")

    /** The stored server list; a replaying shared flow, so an unchanged list is emitted again. */
    private val servers = MutableSharedFlow<List<McpServerConfig>>(replay = 1)

    private val settings: SettingsRepository = mockk(relaxed = true)
    private val client: McpClient = mockk(relaxed = true)
    private val repository = McpServerRepositoryImpl(
        pool = McpConnectionPool(
            clientFactory = mockk<McpClientFactory> { every { create() } returns client },
            networkSettings = settings,
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        servers.tryEmit(listOf(serverA, serverB))
        every { settings.mcpServers } returns servers
        every { settings.approvedCleartextOrigins } returns MutableStateFlow(emptySet())
        every { settings.disabledAppFunctions } returns MutableStateFlow(emptySet())
        every { settings.disabledMcpTools } returns MutableStateFlow(emptySet())
        every { settings.allowedHttpDomains } returns MutableStateFlow(emptyList())
        every { settings.toolRiskOverrides } returns MutableStateFlow(emptyMap())
        coEvery { settings.updateMcpServer(any(), any()) } coAnswers {
            val originalUrl = firstArg<String>()
            val updated = secondArg<McpServerConfig>()
            val current = servers.first()
            if (current.any { it.url == updated.url && it.url != originalUrl }) {
                UpdateMcpServerResult.UrlCollision(collidingUrl = updated.url, collidingDisplayName = null)
            } else {
                servers.emit(current.map { if (it.url == originalUrl) updated else it })
                UpdateMcpServerResult.Success
            }
        }
        coEvery { client.getTools() } returns listOf(AgentTool(name = "t", description = "d", parameters = "{}"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a connected server when its form is saved unchanged then its row stays connected`() =
        runTest(testDispatcher) {
            // Given
            val tools = connectedToolsScreen()
            val form = editForm(serverA.url)

            // When
            form.onSubmit()
            advanceUntilIdle()

            // Then
            assertEquals(McpServerConfigViewModel.Event.Saved, form.events.value)
            assertEquals(McpConnectionStatus.Connected, tools.statusOf(serverA.url))
        }

    @Test
    fun `given a connected server when an edit is refused for another server's URL then its row stays connected`() =
        runTest(testDispatcher) {
            // Given
            val tools = connectedToolsScreen()
            val form = editForm(serverB.url)
            form.onUrlChange(serverA.url)

            // When
            form.onSubmit()
            advanceUntilIdle()

            // Then
            assertNull(form.events.value)
            assertEquals(McpConnectionStatus.Connected, tools.statusOf(serverB.url))
        }

    @Test
    fun `given a connected server when its token is changed and saved then it reconnects with the new token`() =
        runTest(testDispatcher) {
            // Given
            val tools = connectedToolsScreen()
            val form = editForm(serverA.url)
            form.onBearerTokenChange("tok2")

            // When
            form.onSubmit()
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) {
                client.connect(match { it.url == serverA.url && it.auth == McpAuth.Bearer(token = "tok2") })
            }
            assertEquals(McpConnectionStatus.Connected, tools.statusOf(serverA.url))
        }

    /** The Tools screen with both servers connected, as it stands under the form. */
    private fun TestScope.connectedToolsScreen(): ToolsViewModel {
        val tools = ToolsViewModel(
            toolSettings = settings,
            toolRepository = mockk<ToolRepository>(relaxed = true),
            mcpServerRepository = repository,
        )
        advanceUntilIdle()
        assertEquals(McpConnectionStatus.Connected, tools.statusOf(serverA.url))
        assertEquals(McpConnectionStatus.Connected, tools.statusOf(serverB.url))
        return tools
    }

    /** The form opened on [url], pre-filled from the stored list. */
    private fun TestScope.editForm(url: String): McpServerConfigViewModel {
        val form = McpServerConfigViewModel(
            savedStateHandle = SavedStateHandle(mapOf(McpServerConfigViewModel.EXTRA_ORIGINAL_URL to url)),
            toolSettings = settings,
            networkSettings = settings,
            connectionChecker = mockk(relaxed = true),
            timeSource = TestTimeSource(),
        )
        advanceUntilIdle()
        return form
    }

    private fun ToolsViewModel.statusOf(url: String): McpConnectionStatus =
        uiState.value.mcpServers.single { it.url == url }.status
}
