package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogBehaviorHints
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.tv.focus.FocusShelf
import com.stremio.mobile.presentation.tv.focus.shelfFocusKeys

internal const val TV_CONTINUE_WATCHING_SHELF_KEY = "tv:continue-watching"

internal enum class TvHeroArtworkTreatment {
    RichArtwork,
    ContainedPoster,
    Neutral,
}

internal fun heroArtworkTreatment(item: CatalogItem?): TvHeroArtworkTreatment = when {
    item == null -> TvHeroArtworkTreatment.Neutral
    !item.background.isNullOrBlank() -> TvHeroArtworkTreatment.RichArtwork
    !item.poster.isNullOrBlank() && item.posterShape == com.stremio.mobile.data.model.CatalogPosterShape.Landscape ->
        TvHeroArtworkTreatment.RichArtwork
    !item.poster.isNullOrBlank() -> TvHeroArtworkTreatment.ContainedPoster
    else -> TvHeroArtworkTreatment.Neutral
}

/** Higher scores describe real preview fields that work especially well in the Home hero. */
internal fun heroPresentationQuality(item: CatalogItem): Int = when {
    !item.background.isNullOrBlank() -> 4
    !item.poster.isNullOrBlank() && item.posterShape == com.stremio.mobile.data.model.CatalogPosterShape.Landscape -> 3
    !item.logo.isNullOrBlank() -> 2
    !item.description.isNullOrBlank() -> 1
    else -> 0
}

internal sealed interface TvHomeSection {
    val lazyKey: String

    data class Hero(val initialItem: CatalogItem?) : TvHomeSection {
        override val lazyKey: String = "tv:home-hero"
    }

    data class Shelf(
        val semanticKey: String,
        val focusIndex: Int,
        val title: String,
        val items: List<CatalogItem>,
        val isLoading: Boolean,
        val error: String?,
        val isContinueWatching: Boolean,
        val boardShelfIndex: Int?,
    ) : TvHomeSection {
        override val lazyKey: String = "tv:home-shelf:$semanticKey"
    }
}

internal data class TvHomePresentation(
    val sections: List<TvHomeSection>,
    val focusShelves: List<FocusShelf>,
    val enrichedContinueWatching: CatalogShelf,
) {
    fun shelf(semanticKey: String): TvHomeSection.Shelf? =
        sections.filterIsInstance<TvHomeSection.Shelf>().firstOrNull { it.semanticKey == semanticKey }

    fun lazyIndexForShelf(semanticKey: String): Int =
        sections.indexOfFirst { it is TvHomeSection.Shelf && it.semanticKey == semanticKey }

    fun lazyIndexForBoardShelf(boardShelfIndex: Int): Int =
        sections.indexOfFirst { it is TvHomeSection.Shelf && it.boardShelfIndex == boardShelfIndex }
}

internal fun enrichContinueWatching(
    continueWatching: CatalogShelf,
    boardShelves: List<CatalogShelf>,
): CatalogShelf {
    val boardItems = boardShelves.asSequence().flatMap { it.items.asSequence() }
        .associateBy { item -> "${item.type}:${item.id}" }
    val enriched = continueWatching.items.map { item ->
        val rich = boardItems["${item.type}:${item.id}"] ?: return@map item
        item.copy(
            poster = item.poster ?: rich.poster,
            background = item.background ?: rich.background,
            releaseInfo = item.releaseInfo ?: rich.releaseInfo,
            posterShape = item.posterShape ?: rich.posterShape,
            logo = item.logo ?: rich.logo,
            description = item.description ?: rich.description,
            runtime = item.runtime ?: rich.runtime,
            released = item.released ?: rich.released,
            links = item.links.ifEmpty { rich.links },
            inLibrary = item.inLibrary ?: rich.inLibrary,
            behaviorHints = CatalogBehaviorHints(
                defaultVideoId = item.behaviorHints.defaultVideoId ?: rich.behaviorHints.defaultVideoId,
                featuredVideoId = item.behaviorHints.featuredVideoId ?: rich.behaviorHints.featuredVideoId,
                hasScheduledVideos = item.behaviorHints.hasScheduledVideos || rich.behaviorHints.hasScheduledVideos,
            ),
        )
    }
    return continueWatching.copy(items = enriched)
}

internal fun initialHeroCandidate(
    continueWatching: CatalogShelf,
    boardShelves: List<CatalogShelf>,
): CatalogItem? {
    val enrichedContinueWatching = enrichContinueWatching(continueWatching, boardShelves)
    fun bestHeroCapable(items: Sequence<CatalogItem>): CatalogItem? = items
        .filter { it.name.isNotBlank() && heroPresentationQuality(it) > 0 }
        .maxByOrNull(::heroPresentationQuality)

    bestHeroCapable(enrichedContinueWatching.items.asSequence())?.let { return it }
    bestHeroCapable(boardShelves.asSequence().flatMap { it.items.asSequence() })?.let { return it }

    enrichedContinueWatching.items.firstOrNull { item ->
        item.name.isNotBlank() && listOf(item.poster, item.releaseInfo, item.runtime)
            .any { !it.isNullOrBlank() }
    }?.let { return it }
    return boardShelves.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.name.isNotBlank() }
}

internal fun buildTvHomePresentation(
    continueWatching: CatalogShelf,
    boardShelves: List<CatalogShelf>,
): TvHomePresentation {
    val enrichedContinueWatching = enrichContinueWatching(continueWatching, boardShelves)
    val homeShelves = buildList {
        if (enrichedContinueWatching.items.isNotEmpty()) {
            add(
                TvHomeSection.Shelf(
                    semanticKey = TV_CONTINUE_WATCHING_SHELF_KEY,
                    focusIndex = 0,
                    title = enrichedContinueWatching.title,
                    items = enrichedContinueWatching.items,
                    isLoading = false,
                    error = null,
                    isContinueWatching = true,
                    boardShelfIndex = null,
                ),
            )
        }
        val boardKeys = shelfFocusKeys(boardShelves)
        boardShelves.forEachIndexed { boardIndex, shelf ->
            add(
                TvHomeSection.Shelf(
                    semanticKey = boardKeys[boardIndex],
                    focusIndex = size,
                    title = shelf.title,
                    items = shelf.items,
                    isLoading = shelf.isLoading,
                    error = shelf.error,
                    isContinueWatching = false,
                    boardShelfIndex = boardIndex,
                ),
            )
        }
    }
    val hero = TvHomeSection.Hero(initialHeroCandidate(enrichedContinueWatching, boardShelves))
    val sections = listOf(hero) + homeShelves
    val focusShelves = homeShelves.map { shelf -> FocusShelf(shelf.semanticKey, shelf.items, shelf.isLoading) }
    return TvHomePresentation(sections, focusShelves, enrichedContinueWatching)
}
