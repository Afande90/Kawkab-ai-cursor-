package app.kawkab.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kawkab.design.components.buttons.KnotworkButtonsCatalogContent
import app.kawkab.design.components.chat.ChatCatalogContent
import app.kawkab.design.components.chips.KnotworkChipsCatalogContent
import app.kawkab.design.components.console.ConsoleCatalogContent
import app.kawkab.design.components.lists.KnotworkListsCatalogContent
import app.kawkab.design.components.misc.KnotworkMiscCatalogContent
import app.kawkab.design.components.pipelineeditor.PipelineEditorCatalogContent
import app.kawkab.design.theme.KnotworkTheme
import app.kawkab.design.tokens.KnotworkTextStyles

/**
 * Catalog page surfacing every base component shipped in
 * `app.kawkab.design.components.*` in a single scrollable column.
 *
 * Sections (top → bottom): Buttons, Chips & pills, List rows, Misc. Each
 * section reuses its category-level `*CatalogContent` composable so the
 * preview / snapshot story is consistent — categories own the canonical
 * variant set; this page composes them.
 *
 * Mirrors `FoundationsCatalogPage` and `IconCatalogPage` so the design
 * system has a uniform browsing experience across foundations / icons /
 * components. Renders inside the parent [KnotworkTheme]; callers (preview
 * / test) pin `darkTheme` deterministically.
 */
@Composable
fun ComponentsCatalogPage() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp6),
        ) {
            item { SectionHeader(text = "Buttons") }
            item { KnotworkButtonsCatalogContent() }
            item { SectionHeader(text = "Chips & pills") }
            item { KnotworkChipsCatalogContent() }
            item { SectionHeader(text = "List rows") }
            item { KnotworkListsCatalogContent() }
            item { SectionHeader(text = "Misc") }
            item { KnotworkMiscCatalogContent() }
            item { SectionHeader(text = "Chat") }
            item { ChatCatalogContent() }
            item { SectionHeader(text = "Console") }
            item { ConsoleCatalogContent() }
            item { SectionHeader(text = "Pipeline editor") }
            item { PipelineEditorCatalogContent() }
        }
    }
}

/** Section title rendered above each component category. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = KnotworkTextStyles.TitleLg,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = KnotworkTheme.spacing.sp2),
    )
}
