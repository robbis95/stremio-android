package com.stremio.mobile.data.model

import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.core.utils.parseStreamDescription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Library state has playback offsets in the Core state. Convert their ratio to the UI's 0..1 scale. */
fun com.stremio.core.types.library.LibraryItem.toCatalogItem(): CatalogItem {
    val duration = state.duration.toDouble()
    val progress = if (duration > 0.0) {
        (state.timeOffset.toDouble() / duration).coerceIn(0.0, 1.0).toFloat()
    } else {
        null
    }
    return CatalogItem(
        id = id,
        type = type,
        name = name,
        poster = poster,
        background = null,
        releaseInfo = null,
        imdbRating = null,
        progress = progress,
        watched = watched,
        remainingEpisodes = remainingEpisodes,
        posterShape = when (posterShape) {
            com.stremio.core.types.resource.PosterShape.POSTER -> CatalogPosterShape.Poster
            com.stremio.core.types.resource.PosterShape.LANDSCAPE -> CatalogPosterShape.Landscape
            com.stremio.core.types.resource.PosterShape.SQUARE -> CatalogPosterShape.Square
            else -> null
        },
        inLibrary = true,
        behaviorHints = CatalogBehaviorHints(
            defaultVideoId = behaviorHints.defaultVideoId,
            featuredVideoId = behaviorHints.featuredVideoId,
            hasScheduledVideos = behaviorHints.hasScheduledVideos,
        ),
    )
}

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

/** Merge full Core metadata into the already visible preview without dropping preview-only state. */
fun mergeDetailsPreview(
    preview: CatalogItem,
    full: com.stremio.core.types.resource.MetaItem,
): CatalogItem {
    // A stale or mismatched MetaDetails emission must never change the route's semantic identity.
    if (full.id != preview.id || full.type != preview.type) return preview

    return preview.copy(
        name = full.name.takeIf { it.isNotBlank() } ?: preview.name,
        poster = full.poster?.takeIf { it.isNotBlank() } ?: preview.poster,
        background = full.background?.takeIf { it.isNotBlank() } ?: preview.background,
        releaseInfo = full.releaseInfo?.takeIf { it.isNotBlank() } ?: preview.releaseInfo,
        progress = full.progress?.toFloat() ?: preview.progress,
        posterShape = when (full.posterShape) {
            com.stremio.core.types.resource.PosterShape.POSTER -> CatalogPosterShape.Poster
            com.stremio.core.types.resource.PosterShape.LANDSCAPE -> CatalogPosterShape.Landscape
            com.stremio.core.types.resource.PosterShape.SQUARE -> CatalogPosterShape.Square
            else -> preview.posterShape
        },
        logo = full.logo?.takeIf { it.isNotBlank() } ?: preview.logo,
        description = full.description?.takeIf { it.isNotBlank() } ?: preview.description,
        runtime = full.runtime?.takeIf { it.isNotBlank() } ?: preview.runtime,
        released = full.released?.toCoreTimestamp() ?: preview.released,
        links = (
            full.links.map { CatalogLink(name = it.name, category = it.category, url = it.url) } + preview.links
        ).distinctBy { "${it.category.lowercase()}\u0000${it.name.lowercase()}" },
        inLibrary = full.inLibrary,
        watched = full.watched,
        behaviorHints = CatalogBehaviorHints(
            defaultVideoId = full.behaviorHints.defaultVideoId ?: preview.behaviorHints.defaultVideoId,
            featuredVideoId = full.behaviorHints.featuredVideoId ?: preview.behaviorHints.featuredVideoId,
            hasScheduledVideos = full.behaviorHints.hasScheduledVideos,
        ),
        // These values are supplied by other presentation paths rather than MetaItem.
        imdbRating = preview.imdbRating,
        inCinema = preview.inCinema,
        remainingEpisodes = preview.remainingEpisodes,
        continueWatchingVideoId = preview.continueWatchingVideoId,
        isContinueWatching = preview.isContinueWatching,
    )
}

fun com.stremio.core.types.resource.MetaItem.toMetaDetails(
    preview: CatalogItem,
    trailerUrl: String? = null,
): MetaDetails {
    val item = mergeDetailsPreview(preview, this)
    fun linksIn(vararg categories: String) = item.links.asSequence()
        .filter { link -> categories.any { it.equals(link.category, ignoreCase = true) } }
        .map { it.name }
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    return MetaDetails(
        item = item,
        description = item.description,
        genres = linksIn("genre", "genres"),
        cast = linksIn("cast", "actor").take(8),
        director = linksIn("director", "directors").take(4),
        runtime = item.runtime,
        year = item.releaseInfo,
        trailer = trailerUrl,
        isLoading = false,
        episodes = videos.mapIndexed { index, video -> video.toEpisodeOption(index) },
    )
}

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
    originalIndex: Int = 0,
): EpisodeOption = EpisodeOption(
    videoId = id,
    season = seriesInfo?.season?.toInt() ?: 0,
    episode = seriesInfo?.episode?.toInt() ?: 0,
    title = title,
    thumbnail = thumbnail,
    releaseDate = released.toFormattedReleaseDate(),
    watched = watched,
    isCurrent = currentVideo,
    overview = overview,
    progress = progress,
    upcoming = upcoming,
    released = released?.toCoreTimestamp(),
    seriesInfo = seriesInfo?.let { EpisodeSeriesInfo(it.season, it.episode) },
    originalIndex = originalIndex,
)

/** Compatibility adapter for the existing mobile Streams sheet call shape. */
fun com.stremio.core.types.resource.Video.toEpisodeOption(
    season: Int,
    episode: Int,
    releaseDate: String?,
): EpisodeOption = toEpisodeOption().copy(season = season, episode = episode, releaseDate = releaseDate)

private fun pbandk.wkt.Timestamp?.toFormattedReleaseDate(): String? {
    val timestamp = this ?: return null
    if (timestamp.seconds <= 0L) return null
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(timestamp.seconds * 1000L))
}

fun CoreStream.toStreamOption(index: Int): StreamOption {
    val rawDescription = stream.description?.takeIf { it.isNotBlank() } ?: stream.thumbnail
    val parsed = parseStreamDescription(rawDescription)
    val quality = listOfNotNull(stream.name, stream.behaviorHints.filename, rawDescription)
        .asSequence()
        .mapNotNull(::explicitDisplayQuality)
        .firstOrNull()
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
        semanticKey = semanticStreamKey(this),
        sourceKind = stream.source.toPresentationSourceKind(),
    )
}

private fun explicitDisplayQuality(value: String): String? {
    val match = Regex("(?<![A-Za-z0-9])(?:2160p|4k|1080p|720p|480p)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        .find(value)?.value ?: return null
    return if (match.equals("4k", ignoreCase = true)) "4K" else match.lowercase()
}

/** Stable source identity. URLs are reduced to host/path evidence and only the digest is retained. */
internal fun semanticStreamKey(source: CoreStream): String {
    val stream = source.stream
    val hints = stream.behaviorHints
    val sourceEvidence = when (val variant = stream.source) {
        is com.stremio.core.types.resource.Stream.Source.Tramvai -> "torrent:${variant.value.infoHash}:${variant.value.fileIdx}"
        is com.stremio.core.types.resource.Stream.Source.Url -> {
            val endpoint = runCatching { java.net.URI(variant.value.url) }.getOrNull()
            "url:${endpoint?.host.orEmpty()}:${endpoint?.path.orEmpty()}"
        }
        is com.stremio.core.types.resource.Stream.Source.YouTube -> "youtube:${variant.value.ytId}"
        is com.stremio.core.types.resource.Stream.Source.External -> "external:${variant.value.externalUrl}:${variant.value.androidTvUrl}"
        is com.stremio.core.types.resource.Stream.Source.PlayerFrame -> "player-frame:${variant.value.playerFrameUrl}"
        is com.stremio.core.types.resource.Stream.Source.Rar -> "rar:${variant.value.rarUrls.joinToString()}"
        is com.stremio.core.types.resource.Stream.Source.Zip -> "zip:${variant.value.zipUrls.joinToString()}"
        is com.stremio.core.types.resource.Stream.Source.Zip7 -> "zip7:${variant.value.zip7Urls.joinToString()}"
        is com.stremio.core.types.resource.Stream.Source.Tgz -> "tgz:${variant.value.tgzUrls.joinToString()}"
        is com.stremio.core.types.resource.Stream.Source.Tar -> "tar:${variant.value.tarUrls.joinToString()}"
        is com.stremio.core.types.resource.Stream.Source.Nzb -> "nzb:${variant.value.nzbUrls.joinToString()}"
        null -> "unknown"
    }
    val request = source.streamRequest
    val evidence = listOf(
        request.base, request.path.resource, request.path.type, request.path.id,
        sourceEvidence, hints.videoHash, hints.filename, hints.videoSize,
        stream.name, stream.description, stream.thumbnail,
    ).joinToString("\u0000")
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(evidence.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "stream:$digest"
}

private fun com.stremio.core.types.resource.Stream.Source<*>?.toPresentationSourceKind(): StreamSourceKind = when (this) {
    is com.stremio.core.types.resource.Stream.Source.Url -> StreamSourceKind.Direct
    is com.stremio.core.types.resource.Stream.Source.Tramvai -> StreamSourceKind.Torrent
    is com.stremio.core.types.resource.Stream.Source.External,
    is com.stremio.core.types.resource.Stream.Source.PlayerFrame -> StreamSourceKind.External
    is com.stremio.core.types.resource.Stream.Source.YouTube -> StreamSourceKind.YouTube
    is com.stremio.core.types.resource.Stream.Source.Rar,
    is com.stremio.core.types.resource.Stream.Source.Zip,
    is com.stremio.core.types.resource.Stream.Source.Zip7,
    is com.stremio.core.types.resource.Stream.Source.Tgz,
    is com.stremio.core.types.resource.Stream.Source.Tar,
    is com.stremio.core.types.resource.Stream.Source.Nzb -> StreamSourceKind.Archive
    null -> StreamSourceKind.Other
}

private fun pbandk.wkt.Timestamp.toCoreTimestamp() = CoreTimestamp(seconds = seconds, nanos = nanos)
