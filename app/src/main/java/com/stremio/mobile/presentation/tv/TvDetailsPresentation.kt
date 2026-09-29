package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogPosterShape
import com.stremio.mobile.data.model.MetaDetails

internal enum class TvDetailsArtworkMode { RichBackdrop, LandscapeArtwork, ContainedPoster, TextOnly }

internal fun classifyDetailsArtwork(item: CatalogItem): TvDetailsArtworkMode = when {
    !item.background.isNullOrBlank() -> TvDetailsArtworkMode.RichBackdrop
    !item.poster.isNullOrBlank() && item.posterShape == CatalogPosterShape.Landscape -> TvDetailsArtworkMode.LandscapeArtwork
    !item.poster.isNullOrBlank() -> TvDetailsArtworkMode.ContainedPoster
    else -> TvDetailsArtworkMode.TextOnly
}

internal fun detailsMetadataLine(details: MetaDetails): String = listOfNotNull(
    details.item.releaseInfo?.takeIf(String::isNotBlank) ?: details.year?.takeIf(String::isNotBlank),
    details.runtime?.takeIf(String::isNotBlank) ?: details.item.runtime?.takeIf(String::isNotBlank),
    details.item.type.takeIf(String::isNotBlank)?.humanizeDetailsType(),
    details.item.imdbRating?.takeIf(String::isNotBlank)?.let { "IMDb $it" },
).distinctBy { it.trim().lowercase() }.joinToString("  •  ")

private fun String.humanizeDetailsType(): String =
    split(Regex("[_\\s-]+")).filter(String::isNotBlank).joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

internal fun isDetailsItemInLibrary(
    item: CatalogItem,
    libraryItems: List<CatalogItem>,
    fallback: Boolean = item.inLibrary == true,
    libraryIsAuthoritative: Boolean = true,
): Boolean = if (libraryIsAuthoritative) {
    libraryItems.any { it.id == item.id && it.type == item.type }
} else {
    fallback
}

/** The preview is the screen's content source while Core's optional full metadata is loading. */
internal fun detailsWhileLoading(preview: CatalogItem): MetaDetails = MetaDetails(item = preview, isLoading = true)

internal data class TvDetailsUiState(
    val details: MetaDetails? = null,
    val isInLibrary: Boolean = false,
    val isLibraryActionLoading: Boolean = false,
    val episodeBrowser: TvEpisodeBrowserUiState? = null,
)
