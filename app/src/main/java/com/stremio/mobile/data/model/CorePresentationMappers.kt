package com.stremio.mobile.data.model

import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.core.utils.parseStreamDescription

fun com.stremio.core.types.resource.MetaItemPreview.toCatalogItem(
    imdbRating: String? = null,
): CatalogItem = CatalogItem(
    id = id,
    type = type,
    name = name,
    poster = poster,
    background = background,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    inCinema = inCinema,
    watched = watched,
    posterShape = when (posterShape) {
        com.stremio.core.types.resource.PosterShape.POSTER -> CatalogPosterShape.Poster
        com.stremio.core.types.resource.PosterShape.LANDSCAPE -> CatalogPosterShape.Landscape
        com.stremio.core.types.resource.PosterShape.SQUARE -> CatalogPosterShape.Square
        else -> null
    },
    logo = logo,
    description = description,
    runtime = runtime,
    released = released?.toCoreTimestamp(),
    links = links.map { CatalogLink(name = it.name, category = it.category) },
    inLibrary = inLibrary,
    behaviorHints = CatalogBehaviorHints(
        defaultVideoId = behaviorHints.defaultVideoId,
        featuredVideoId = behaviorHints.featuredVideoId,
        hasScheduledVideos = behaviorHints.hasScheduledVideos,
    ),
)

fun CatalogItem.toCoreMetaItemPreviewForLibrary(): com.stremio.core.types.resource.MetaItemPreview =
    com.stremio.core.types.resource.MetaItemPreview(
        id = id,
        type = type,
        name = name,
        posterShape = when (posterShape) {
            CatalogPosterShape.Landscape -> com.stremio.core.types.resource.PosterShape.LANDSCAPE
            CatalogPosterShape.Square -> com.stremio.core.types.resource.PosterShape.SQUARE
            else -> com.stremio.core.types.resource.PosterShape.POSTER
        },
        poster = poster,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        runtime = runtime,
        released = released?.let { pbandk.wkt.Timestamp(seconds = it.seconds, nanos = it.nanos) },
        links = links.map { link ->
            com.stremio.core.types.resource.LinkPreview(name = link.name, category = link.category)
        },
        behaviorHints = com.stremio.core.types.resource.MetaItemBehaviorHints(
            defaultVideoId = behaviorHints.defaultVideoId,
            featuredVideoId = behaviorHints.featuredVideoId,
            hasScheduledVideos = behaviorHints.hasScheduledVideos,
        ),
        // AddToLibrary has no request-scoped deep-link data; retain the existing empty fallback.
        deepLinks = com.stremio.core.types.resource.MetaItemDeepLinks(),
        inLibrary = true,
        watched = watched,
        inCinema = inCinema,
    )

fun com.stremio.core.types.resource.Video.toEpisodeOption(
    season: Int,
    episode: Int,
    releaseDate: String?,
): EpisodeOption = EpisodeOption(
    videoId = id,
    season = season,
    episode = episode,
    title = title,
    thumbnail = thumbnail,
    releaseDate = releaseDate,
    watched = watched,
    isCurrent = currentVideo,
    overview = overview,
    progress = progress,
    upcoming = upcoming,
    released = released?.toCoreTimestamp(),
)

fun CoreStream.toStreamOption(index: Int): StreamOption {
    val rawDescription = stream.description?.takeIf { it.isNotBlank() } ?: stream.thumbnail
    val parsed = parseStreamDescription(rawDescription)
    val quality = stream.name?.let { name ->
        listOf("2160p", "4k", "1080p", "720p", "480p")
            .firstOrNull { name.contains(it, ignoreCase = true) }
    }
    return StreamOption(
        key = "$index-$addonTitle-${stream.name ?: ""}-${rawDescription ?: ""}",
        name = stream.name?.takeIf { it.isNotBlank() } ?: addonTitle,
        description = rawDescription,
        addonTitle = addonTitle,
        quality = quality,
        core = this,
        seeds = parsed.seeds,
        size = parsed.size,
        origin = parsed.origin,
        cleanDescription = parsed.cleanDescription,
        bingeGroup = stream.behaviorHints.bingeGroup,
        notWebReady = stream.behaviorHints.notWebReady,
        filename = stream.behaviorHints.filename,
        videoSize = stream.behaviorHints.videoSize,
        videoHash = stream.behaviorHints.videoHash,
    )
}

private fun pbandk.wkt.Timestamp.toCoreTimestamp() = CoreTimestamp(seconds = seconds, nanos = nanos)
