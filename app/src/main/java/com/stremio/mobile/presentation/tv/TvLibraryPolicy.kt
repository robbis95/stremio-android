package com.stremio.mobile.presentation.tv

import com.stremio.core.models.LibraryWithFilters
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.focus.contentFocusKey

internal data class LibrarySelectionIdentity(
    val type: String?,
    val sort: LibraryWithFilters.Sort,
)

internal data class TvLibraryOrderSnapshot(
    val selection: LibrarySelectionIdentity?,
    val items: List<CatalogItem>,
)

internal data class TvLibraryFilterOption(
    val key: String,
    val label: String,
    val selected: Boolean,
    val request: LibraryWithFilters.LibraryRequest,
)

internal data class TvLibraryFilterGroup(
    val key: String,
    val label: String,
    val options: List<TvLibraryFilterOption>,
)

internal fun libraryTypeLabel(type: String?): String = when (type?.trim()?.lowercase()) {
    null, "" -> "All"
    "movie" -> "Movie"
    "series" -> "Series"
    else -> humanizeDiscoverLabel(type)
}

internal fun librarySortLabel(sort: LibraryWithFilters.Sort): String = when (sort) {
    LibraryWithFilters.Sort.LAST_WATCHED -> "Last watched"
    LibraryWithFilters.Sort.NAME -> "Name A–Z"
    LibraryWithFilters.Sort.NAME_REVERSE -> "Name Z–A"
    LibraryWithFilters.Sort.TIMES_WATCHED -> "Most watched"
    LibraryWithFilters.Sort.WATCHED -> "Watched first"
    LibraryWithFilters.Sort.NOT_WATCHED -> "Unwatched first"
    else -> humanizeDiscoverLabel(sort.name ?: "Unknown")
}

internal fun LibraryWithFilters.toTvLibraryFilterGroups(): List<TvLibraryFilterGroup> = buildList {
    val typeOptions = selectable.types.mapIndexed { index, option ->
        TvLibraryFilterOption(
            key = "type:${option.type.orEmpty()}:$index",
            label = libraryTypeLabel(option.type),
            selected = option.selected,
            request = option.request,
        )
    }
    if (typeOptions.isNotEmpty()) add(TvLibraryFilterGroup("type", "Type", typeOptions))

    val sortOptions = selectable.sorts.map { option ->
        TvLibraryFilterOption(
            key = "sort:${option.sort.name}",
            label = librarySortLabel(option.sort),
            selected = option.selected,
            request = option.request,
        )
    }
    if (sortOptions.isNotEmpty()) add(TvLibraryFilterGroup("sort", "Sort", sortOptions))
}

internal fun LibraryWithFilters.LibraryRequest.selectionIdentity() =
    LibrarySelectionIdentity(type = type, sort = sort)

/**
 * Preserve the user's visible order while Core updates objects or extends the current pages.
 * A type or sort change is a new selection and adopts Core's authoritative order.
 */
internal fun stableLibraryOrder(
    previous: TvLibraryOrderSnapshot,
    selection: LibrarySelectionIdentity?,
    incoming: List<CatalogItem>,
): TvLibraryOrderSnapshot {
    if (previous.selection != selection) return TvLibraryOrderSnapshot(selection, incoming)

    val incomingByKey = incoming.associateBy { contentFocusKey(it.type, it.id) }
    val retainedKeys = previous.items
        .map { contentFocusKey(it.type, it.id) }
        .filter(incomingByKey::containsKey)
        .toMutableSet()
    val stableExisting = previous.items.mapNotNull { item ->
        incomingByKey[contentFocusKey(item.type, item.id)]
    }
    val appended = incoming.filter { retainedKeys.add(contentFocusKey(it.type, it.id)) }
    return TvLibraryOrderSnapshot(selection, stableExisting + appended)
}

/** Keep a semantic target when possible; otherwise use the closest valid prior index. */
internal fun resolveLibraryFocusKey(
    availableKeys: List<String>,
    previousKey: String?,
    previousIndex: Int,
): String? = previousKey?.takeIf(availableKeys::contains)
    ?: availableKeys.getOrNull(previousIndex.coerceIn(0, (availableKeys.size - 1).coerceAtLeast(0)))

internal fun libraryPageTriggerIndex(
    focusedIndex: Int,
    itemCount: Int,
    columns: Int,
    lastTriggerIndex: Int,
): Int? {
    if (columns <= 0 || itemCount <= 0 || focusedIndex !in 0 until itemCount) return null
    val finalRowStart = (itemCount - columns).coerceAtLeast(0)
    return focusedIndex.takeIf { it >= finalRowStart && it > lastTriggerIndex }
}

internal class LibraryPageRequestTracker {
    private val requested = mutableSetOf<String>()
    fun tryMark(identity: String?): Boolean = identity != null && requested.add(identity)
}
