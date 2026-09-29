package com.stremio.mobile.presentation.tv

import com.stremio.core.models.CatalogWithFilters
import com.stremio.core.models.LoadablePage
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.data.model.CatalogShelf

/** Narrow, immutable TV projection of Core Discover; requests remain Core-owned values. */
internal data class TvDiscoverUiState(
    val selectedRequest: ResourceRequest? = null,
    val resolvedRequest: ResourceRequest? = null,
    val title: String = "Discover",
    val shelf: CatalogShelf = CatalogShelf(title = "Discover", isLoading = true),
    val filterGroups: List<TvDiscoverFilterGroup<ResourceRequest>> = emptyList(),
    val nextPageRequest: ResourceRequest? = null,
)

internal fun CatalogWithFilters.toTvDiscoverFilterGroups(): List<TvDiscoverFilterGroup<ResourceRequest>> {
    val typeGroups = discoverTypeInputs(
        selectable.types.map { type ->
            DiscoverFilterOptionInput(humanizeDiscoverType(type.type), type.selected, type.request)
        },
    )
    val catalogGroups = discoverCatalogInputs(
        selectable.catalogs.map { catalog ->
            DiscoverFilterOptionInput(catalog.name, catalog.selected, catalog.request)
        },
    )
    val extraGroups = selectable.extra.mapNotNull { extra ->
        discoverExtraInput(extra.name, extra.options.map { option -> Triple(option.value, option.selected, option.request) })
    }
    return mapDiscoverFilterGroups(typeGroups, catalogGroups, extraGroups)
}

internal fun CatalogWithFilters.toTvDiscoverUiState(
    shelf: CatalogShelf,
    title: String,
    selectedRequest: ResourceRequest?,
): TvDiscoverUiState {
    val pageError = catalog.pages.firstNotNullOfOrNull { page ->
        (page.content as? LoadablePage.Content.Error)?.value?.message
    }
    val loading = catalog.pages.any { page ->
        page.content == null || page.content is LoadablePage.Content.Loading
    }
    val resolvedShelf = shelf.copy(
        title = title,
        isLoading = loading,
        error = pageError,
        seeAllRequest = selectedRequest,
    )
    return TvDiscoverUiState(
        selectedRequest = selectedRequest ?: this.selected?.request,
        resolvedRequest = this.selected?.request,
        title = title,
        shelf = resolvedShelf,
        filterGroups = toTvDiscoverFilterGroups(),
        nextPageRequest = selectable.nextPage?.request,
    )
}
