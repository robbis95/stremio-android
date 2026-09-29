package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.focus.contentFocusKey

internal data class DiscoverSelectableRequest<T>(val selected: Boolean, val request: T)

/** Selects only requests Core says are available; list order is the deterministic final fallback. */
internal fun <T> preferredDiscoverRequest(
    coreSelectedRequest: T?,
    types: List<DiscoverSelectableRequest<T>>,
    catalogs: List<DiscoverSelectableRequest<T>>,
): T? = coreSelectedRequest
    ?: types.firstOrNull { it.selected }?.request
    ?: catalogs.firstOrNull { it.selected }?.request
    ?: types.firstOrNull()?.request
    ?: catalogs.firstOrNull()?.request

internal enum class TvDiscoverFilterKind { Type, Catalog, Extra }

internal data class DiscoverFilterOptionInput<T>(
    val label: String,
    val selected: Boolean,
    val request: T,
)

internal data class DiscoverFilterGroupInput<T>(
    val name: String,
    val options: List<DiscoverFilterOptionInput<T>>,
)

internal data class TvDiscoverFilterOption<T>(
    val key: String,
    val label: String,
    val selected: Boolean,
    val request: T,
)

internal data class TvDiscoverFilterGroup<T>(
    val key: String,
    val label: String,
    val kind: TvDiscoverFilterKind,
    val options: List<TvDiscoverFilterOption<T>>,
)

/** Pure presentation mapping shared by Core-backed TV filters and their unit tests. */
internal fun <T> mapDiscoverFilterGroups(
    types: List<DiscoverFilterGroupInput<T>>,
    catalogs: List<DiscoverFilterGroupInput<T>>,
    extras: List<DiscoverFilterGroupInput<T>>,
): List<TvDiscoverFilterGroup<T>> = buildList {
    fun addGroups(inputs: List<DiscoverFilterGroupInput<T>>, kind: TvDiscoverFilterKind) {
        inputs.forEach { group ->
            val name = if (kind == TvDiscoverFilterKind.Catalog) group.name else humanizeDiscoverLabel(group.name)
            val options = group.options.mapIndexed { index, option ->
                TvDiscoverFilterOption(
                    key = "${kind.name.lowercase()}:$name:${option.label}:$index",
                    label = option.label,
                    selected = option.selected,
                    request = option.request,
                )
            }
            if (name.isNotBlank() && options.isNotEmpty()) {
                add(TvDiscoverFilterGroup("${kind.name.lowercase()}:$name", name, kind, options))
            }
        }
    }
    addGroups(types, TvDiscoverFilterKind.Type)
    addGroups(catalogs, TvDiscoverFilterKind.Catalog)
    addGroups(extras, TvDiscoverFilterKind.Extra)
}

internal fun humanizeDiscoverType(value: String): String = when (value.trim().lowercase()) {
    "movie" -> "Movie"
    "series" -> "Series"
    else -> humanizeDiscoverLabel(value)
}

internal fun humanizeDiscoverLabel(value: String): String = value
    .trim()
    .replace('_', ' ')
    .replace('-', ' ')
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase() } }

internal fun <T> discoverTypeInputs(values: List<DiscoverFilterOptionInput<T>>): List<DiscoverFilterGroupInput<T>> =
    if (values.isEmpty()) emptyList() else listOf(DiscoverFilterGroupInput("Type", values))

internal fun <T> discoverCatalogInputs(values: List<DiscoverFilterOptionInput<T>>): List<DiscoverFilterGroupInput<T>> =
    if (values.isEmpty()) emptyList() else listOf(DiscoverFilterGroupInput("Catalog", values))

/** A null/blank option is shown as All only when Core explicitly supplied that option. */
internal fun <T> discoverExtraInput(
    name: String,
    values: List<Triple<String?, Boolean, T>>,
): DiscoverFilterGroupInput<T>? {
    if (name.isBlank() || values.isEmpty()) return null
    return DiscoverFilterGroupInput(
        name,
        values.map { (value, selected, request) ->
            DiscoverFilterOptionInput(value?.takeIf(String::isNotBlank) ?: "All", selected, request)
        },
    )
}

internal fun discoverContentKeys(items: List<CatalogItem>): List<String> =
    items.map { contentFocusKey(it.type, it.id) }

/** Keep semantic focus when possible; otherwise use a clamped prior index as a deterministic fallback. */
internal fun resolveDiscoverFocusKey(
    availableKeys: List<String>,
    previousKey: String?,
    previousIndex: Int?,
): String? = previousKey?.takeIf(availableKeys::contains)
    ?: availableKeys.getOrNull(previousIndex?.coerceIn(0, (availableKeys.size - 1).coerceAtLeast(0)) ?: 0)

internal class DiscoverPageRequestTracker {
    private val requested = mutableSetOf<String>()
    fun tryMark(identity: String?): Boolean = identity != null && requested.add(identity)
}

internal fun discoverGridVerticalTarget(index: Int, itemCount: Int, columns: Int, direction: Int): Int? {
    if (index !in 0 until itemCount || columns <= 0 || direction !in -1..1 || direction == 0) return null
    val row = index / columns + direction
    if (row < 0) return null
    val target = row * columns + index % columns
    return target.takeIf { it < itemCount } ?: if (direction > 0) (itemCount - 1).takeIf { it / columns == row } else null
}
