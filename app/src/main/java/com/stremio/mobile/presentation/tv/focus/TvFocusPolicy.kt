package com.stremio.mobile.presentation.tv.focus

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf

/** Request identity differentiates same-named catalogs; ordinal is only a final tie-breaker for duplicates. */
fun shelfFocusKeys(shelves: List<CatalogShelf>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return shelves.map { shelf ->
        val request = shelf.seeAllRequest?.toString().orEmpty()
        val base = listOf(shelf.type.orEmpty(), shelf.title.trim(), request).joinToString("|")
        val occurrence = counts.getOrDefault(base, 0)
        counts[base] = occurrence + 1
        "$base#$occurrence"
    }
}

data class FocusShelf(val key: String, val items: List<CatalogItem>, val isLoading: Boolean)

/** Resolves return focus after catalog data settles. Shelf ordering is intentionally irrelevant. */
fun resolveFocusLocation(
    requested: TvFocusLocation?, shelves: List<FocusShelf>, remembered: Map<String, String>,
): TvFocusLocation? {
    val populated = shelves.filter { it.items.isNotEmpty() }
    if (populated.isEmpty()) return null
    val requestedShelf = shelves.firstOrNull { it.key == requested?.shelfKey }
    if (requestedShelf != null && requestedShelf.items.isEmpty() && requestedShelf.isLoading) return null
    val shelf = populated.firstOrNull { it.key == requested?.shelfKey }
        ?: nearestShelf(requested, shelves, populated)
    val content = requested?.takeIf { it.shelfKey == shelf.key }?.contentKey
        ?: remembered[shelf.key]
    val key = content?.takeIf { candidate -> shelf.items.any { contentFocusKey(it.type, it.id) == candidate } }
        ?: contentFocusKey(shelf.items.first().type, shelf.items.first().id)
    return TvFocusLocation(shelf.key, key, shelves.indexOfFirst { it.key == shelf.key })
}

private fun nearestShelf(requested: TvFocusLocation?, all: List<FocusShelf>, populated: List<FocusShelf>): FocusShelf {
    val oldIndex = requested?.shelfIndex?.coerceIn(0, (all.size - 1).coerceAtLeast(0))
        ?: all.indexOfFirst { it.key == requested?.shelfKey }.let { if (it < 0) 0 else it }
    return populated.minBy { shelf -> kotlin.math.abs(all.indexOfFirst { it.key == shelf.key } - oldIndex) }
}
