package app.kawkab.android.presentation.ui.orchestrator

import app.kawkab.android.domain.models.CloudProvider
import app.kawkab.android.presentation.ui.pipeline.editor.config.CloudProviderMapper
import app.kawkab.design.components.pipelineeditor.ProviderChoices

/**
 * Which providers are set up on this device, for the node sheet's *Provider* and *Engine* fields.
 *
 * @property models Every provider set up here, with the model id a run of it uses — the default
 *   when none is chosen or the choice is no longer offered. A provider absent from the map is not
 *   set up here; a node may still choose it, since a pipeline can run on another device.
 * @property auto What a node set to *Auto* runs on here, or `null` when nothing qualifies.
 */
data class ProviderAvailability(val models: Map<CloudProvider, String> = emptyMap(), val auto: CloudProvider? = null)

/**
 * The node sheet's view of [ProviderAvailability], in the catalog's provider vocabulary.
 *
 * @param onDeviceModel The on-device model's name, for the *Engine* field's first option.
 * @return The choices the sheet's provider fields read.
 */
internal fun ProviderAvailability.toProviderChoices(onDeviceModel: String?): ProviderChoices = ProviderChoices(
    configuredModels = models.entries.associate { (provider, model) ->
        CloudProviderMapper.toCatalog(provider) to model
    },
    autoResolvesTo = auto?.let(CloudProviderMapper::toCatalog),
    onDeviceModel = onDeviceModel,
)
