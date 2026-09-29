package com.stremio.mobile.presentation.tv

import com.stremio.core.models.LibraryWithFilters
import com.stremio.mobile.data.model.CatalogItem

/** Small immutable TV view of Core Library state; the Core model stays in repository/ViewModel. */
internal data class TvLibraryUiState(
    val selectedRequest: LibraryWithFilters.LibraryRequest? = null,
    val logicalSelection: LibrarySelectionIdentity? = null,
    val filterGroups: List<TvLibraryFilterGroup> = emptyList(),
    val items: List<CatalogItem> = emptyList(),
    val nextPageRequest: LibraryWithFilters.LibraryRequest? = null,
    val isLoading: Boolean = true,
)

internal fun LibraryWithFilters.toTvLibraryUiState(
    items: List<CatalogItem>,
    loading: Boolean = false,
): TvLibraryUiState {
    val selected = selected?.request
    return TvLibraryUiState(
        selectedRequest = selected,
        logicalSelection = selected?.selectionIdentity(),
        filterGroups = toTvLibraryFilterGroups(),
        items = items,
        nextPageRequest = selectable.nextPage?.request,
        isLoading = loading || selected == null,
    )
}
