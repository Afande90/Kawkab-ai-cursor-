package app.knotwork.android.presentation.ui.orchestrator

import app.knotwork.android.domain.models.CloudProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import app.knotwork.design.components.pipelineeditor.CloudProvider as CatalogCloudProvider

/** Unit tests for [toProviderChoices] — what the node sheet's provider fields are told. */
class ProviderAvailabilityTest {

    @Test
    fun `given providers set up here then the sheet sees each with the model its runs use`() {
        val choices = ProviderAvailability(
            models = mapOf(CloudProvider.OPENAI to "gpt-5.4", CloudProvider.OPENAI_COMPATIBLE to "qwen2.5-7b-instruct"),
            auto = CloudProvider.OPENAI,
        ).toProviderChoices(onDeviceModel = "gemma")

        assertEquals(
            mapOf(
                CatalogCloudProvider.OPEN_AI to "gpt-5.4",
                CatalogCloudProvider.OPENAI_COMPATIBLE to "qwen2.5-7b-instruct",
            ),
            choices.configuredModels,
        )
        assertEquals(CatalogCloudProvider.OPEN_AI, choices.autoResolvesTo)
        assertEquals("gemma", choices.onDeviceModel)
    }

    @Test
    fun `given nothing set up then auto resolves to nothing`() {
        val choices = ProviderAvailability().toProviderChoices(onDeviceModel = null)

        assertEquals(emptyMap<CatalogCloudProvider, String>(), choices.configuredModels)
        assertNull(choices.autoResolvesTo)
    }
}
