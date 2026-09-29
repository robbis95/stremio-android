package com.stremio.mobile.data.model

data class CatalogItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val releaseInfo: String?,
    val imdbRating: String?,
    val progress: Float? = null,
    val inCinema: Boolean = false,
    val watched: Boolean = false,
    val remainingEpisodes: Int? = null,
    val continueWatchingVideoId: String? = null,
    val isContinueWatching: Boolean = false,
    val posterShape: CatalogPosterShape? = null,
    val logo: String? = null,
    val description: String? = null,
    val runtime: String? = null,
    val released: CoreTimestamp? = null,
    val links: List<CatalogLink> = emptyList(),
    val inLibrary: Boolean? = null,
    val behaviorHints: CatalogBehaviorHints = CatalogBehaviorHints(),
)

enum class CatalogPosterShape { Poster, Landscape, Square }

data class CatalogLink(val name: String, val category: String, val url: String? = null)

data class CatalogBehaviorHints(
    val defaultVideoId: String? = null,
    val featuredVideoId: String? = null,
    val hasScheduledVideos: Boolean = false,
)

/** Exact protobuf timestamp components, without timezone or precision loss. */
data class CoreTimestamp(val seconds: Long, val nanos: Int)
