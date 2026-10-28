package app.kawkab.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.kawkab.design.theme.KnotworkTheme

/** Light-theme preview of [ComponentsCatalogPage] with a tall artboard. */
@Preview(name = "Components — Light", showBackground = true, heightDp = 4200)
@Composable
private fun ComponentsCatalogPageLightPreview() {
    KnotworkTheme(darkTheme = false) { ComponentsCatalogPage() }
}

/** Dark-theme preview of [ComponentsCatalogPage]. */
@Preview(name = "Components — Dark", showBackground = true, heightDp = 4200)
@Composable
private fun ComponentsCatalogPageDarkPreview() {
    KnotworkTheme(darkTheme = true) { ComponentsCatalogPage() }
}
