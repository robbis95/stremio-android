package com.stremio.mobile.data.model

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.MetaItemBehaviorHints
import com.stremio.core.types.resource.MetaItemDeepLinks
import com.stremio.core.types.resource.MetaItem
import com.stremio.core.types.resource.MetaItemPreview
import com.stremio.core.types.resource.PosterShape
import com.stremio.core.types.resource.Stream
import com.stremio.core.types.resource.StreamBehaviorHints
import com.stremio.core.types.resource.StreamDeepLinks
import com.stremio.core.types.resource.Video
import com.stremio.core.types.resource.VideoDeepLinks
import com.stremio.mobile.core.CoreStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePresentationMappersTest {
    @Test
    fun `library item mapping preserves its TV fields and normalizes state progress`() {
        val item = com.stremio.core.types.library.LibraryItem(
            id = "library-1",
            type = "series",
            name = "Library series",
            poster = "poster.jpg",
            posterShape = PosterShape.LANDSCAPE,
            state = com.stremio.core.types.library.LibraryItemState(
                timeOffset = 25,
                duration = 100,
                videoId = "s1:e2",
                noNotif = false,
            ),
            behaviorHints = MetaItemBehaviorHints(
                defaultVideoId = "default-video",
                featuredVideoId = "featured-video",
                hasScheduledVideos = true,
            ),
            deepLinks = MetaItemDeepLinks(),
            progress = 25.0,
            watched = true,
            notifications = 3,
            remainingEpisodes = 3,
        ).toCatalogItem()

        assertEquals("library-1", item.id)
        assertEquals("series", item.type)
        assertEquals("Library series", item.name)
        assertEquals("poster.jpg", item.poster)
        assertEquals(CatalogPosterShape.Landscape, item.posterShape)
        assertTrue(item.watched)
        assertEquals(3, item.remainingEpisodes)
        assertEquals(0.25f, item.progress!!, 0.0001f)
        assertEquals(true, item.inLibrary)
        assertEquals("default-video", item.behaviorHints.defaultVideoId)
        assertEquals("featured-video", item.behaviorHints.featuredVideoId)
        assertTrue(item.behaviorHints.hasScheduledVideos)
        assertNull(item.background)
        assertNull(item.logo)
        assertNull(item.description)
    }

    @Test
    fun `library item mapping clamps state ratio and leaves unknown progress absent`() {
        fun item(timeOffset: Long, duration: Long) = com.stremio.core.types.library.LibraryItem(
            id = "id",
            type = "movie",
            name = "Movie",
            posterShape = PosterShape.POSTER,
            state = com.stremio.core.types.library.LibraryItemState(timeOffset, duration, noNotif = false),
            behaviorHints = MetaItemBehaviorHints(hasScheduledVideos = false),
            deepLinks = MetaItemDeepLinks(),
            progress = 0.0,
            watched = false,
            notifications = 0,
        ).toCatalogItem()

        assertEquals(1f, item(150, 100).progress!!, 0f)
        assertNull(item(0, 0).progress)
    }

    @Test
    fun `rich preview maps presentation metadata and keeps existing artwork and release info`() {
        val item = preview(
            posterShape = PosterShape.LANDSCAPE,
            poster = "poster.jpg",
            background = "background.jpg",
            logo = "logo.png",
            description = "A description",
            releaseInfo = "2024",
            runtime = "1h 42m",
            released = pbandk.wkt.Timestamp(seconds = 1_700_000_000L, nanos = 123_456_789),
            links = listOf(com.stremio.core.types.resource.LinkPreview("Drama", "genre")),
            inLibrary = true,
            watched = true,
            inCinema = true,
            behaviorHints = MetaItemBehaviorHints(
                defaultVideoId = "episode-default",
                featuredVideoId = "episode-featured",
                hasScheduledVideos = true,
            ),
        ).toCatalogItem()

        assertEquals("logo.png", item.logo)
        assertEquals("A description", item.description)
        assertEquals("1h 42m", item.runtime)
        assertEquals(CatalogPosterShape.Landscape, item.posterShape)
        assertEquals(listOf(CatalogLink("Drama", "genre")), item.links)
        assertEquals(true, item.inLibrary)
        assertTrue(item.watched)
        assertTrue(item.inCinema)
        assertEquals("poster.jpg", item.poster)
        assertEquals("background.jpg", item.background)
        assertEquals("2024", item.releaseInfo)
        assertEquals(CoreTimestamp(1_700_000_000L, 123_456_789), item.released)
        assertEquals("episode-default", item.behaviorHints.defaultVideoId)
        assertEquals("episode-featured", item.behaviorHints.featuredVideoId)
        assertTrue(item.behaviorHints.hasScheduledVideos)
    }

    @Test
    fun `missing optional preview metadata maps safely`() {
        val item = preview().toCatalogItem()

        assertNull(item.poster)
        assertNull(item.background)
        assertNull(item.logo)
        assertNull(item.description)
        assertNull(item.runtime)
        assertNull(item.released)
        assertTrue(item.links.isEmpty())
        assertNull(CatalogItem("x", "movie", "x", null, null, null, null).inLibrary)
        assertEquals(CatalogPosterShape.Poster, item.posterShape)
        assertFalse(item.watched)
        assertFalse(item.inCinema)
    }

    @Test
    fun `catalog item maps back to core preview with rich metadata and behavior hints`() {
        val item = preview(
            posterShape = PosterShape.SQUARE,
            poster = "poster.jpg",
            background = "background.jpg",
            logo = "logo.png",
            description = "A description",
            releaseInfo = "2024",
            runtime = "1h 42m",
            released = pbandk.wkt.Timestamp(seconds = 1_700_000_000L, nanos = 123_456_789),
            links = listOf(com.stremio.core.types.resource.LinkPreview("Drama", "genre")),
            watched = true,
            inCinema = true,
            behaviorHints = MetaItemBehaviorHints(
                defaultVideoId = "episode-default",
                featuredVideoId = "episode-featured",
                hasScheduledVideos = true,
            ),
        ).toCatalogItem().toCoreMetaItemPreviewForLibrary()

        assertEquals(PosterShape.SQUARE, item.posterShape)
        assertEquals("poster.jpg", item.poster)
        assertEquals("background.jpg", item.background)
        assertEquals("logo.png", item.logo)
        assertEquals("A description", item.description)
        assertEquals("2024", item.releaseInfo)
        assertEquals("1h 42m", item.runtime)
        assertEquals(pbandk.wkt.Timestamp(seconds = 1_700_000_000L, nanos = 123_456_789), item.released)
        assertEquals(listOf(com.stremio.core.types.resource.LinkPreview("Drama", "genre")), item.links)
        assertEquals("episode-default", item.behaviorHints.defaultVideoId)
        assertEquals("episode-featured", item.behaviorHints.featuredVideoId)
        assertTrue(item.behaviorHints.hasScheduledVideos)
        assertTrue(item.watched)
        assertTrue(item.inCinema)
        assertTrue(item.inLibrary)
    }

    @Test
    fun `preview catalog item core round trip preserves behavior hints`() {
        val originalHints = MetaItemBehaviorHints(
            defaultVideoId = "default-id",
            featuredVideoId = "featured-id",
            hasScheduledVideos = true,
        )
        val roundTripped = preview(behaviorHints = originalHints)
            .toCatalogItem()
            .toCoreMetaItemPreviewForLibrary()

        assertEquals(originalHints.defaultVideoId, roundTripped.behaviorHints.defaultVideoId)
        assertEquals(originalHints.featuredVideoId, roundTripped.behaviorHints.featuredVideoId)
        assertEquals(originalHints.hasScheduledVideos, roundTripped.behaviorHints.hasScheduledVideos)
    }

    @Test
    fun `manually created catalog item maps to safe behavior hint defaults`() {
        val preview = CatalogItem("id", "series", "Example", null, null, null, null)
            .toCoreMetaItemPreviewForLibrary()

        assertNull(preview.behaviorHints.defaultVideoId)
        assertNull(preview.behaviorHints.featuredVideoId)
        assertFalse(preview.behaviorHints.hasScheduledVideos)
    }

    @Test
    fun `video mapping preserves core episode semantics without changing progress units`() {
        val episode = Video(
            id = "s1:e2",
            title = "Episode title",
            released = pbandk.wkt.Timestamp(seconds = 1_700_000_001L, nanos = 7),
            overview = "Episode overview",
            thumbnail = "thumb.jpg",
            seriesInfo = Video.SeriesInfo(season = 1, episode = 2),
            upcoming = true,
            watched = true,
            currentVideo = true,
            progress = 37.5,
            deepLinks = VideoDeepLinks(
                metaDetailsVideos = "videos",
                metaDetailsStreams = "streams",
                externalPlayer = VideoDeepLinks.ExternalPlayerLink(),
            ),
        ).toEpisodeOption(season = 1, episode = 2, releaseDate = "Nov 14, 2023")

        assertEquals("s1:e2", episode.videoId)
        assertEquals("Episode overview", episode.overview)
        assertEquals(37.5, episode.progress!!, 0.0)
        assertTrue(episode.upcoming)
        assertTrue(episode.watched)
        assertTrue(episode.isCurrent)
        assertEquals("Nov 14, 2023", episode.releaseDate)
        assertEquals(CoreTimestamp(1_700_000_001L, 7), episode.released)
    }

    @Test
    fun `full details fills richer metadata while preserving preview-only Continue Watching state`() {
        val preview = CatalogItem(
            id = "tt123", type = "series", name = "Preview title", poster = "preview-poster",
            background = "preview-background", releaseInfo = "2020", imdbRating = "8.1",
            progress = 0.42f, inCinema = true, watched = false, remainingEpisodes = 4,
            continueWatchingVideoId = "s2:e3", isContinueWatching = true,
            posterShape = CatalogPosterShape.Poster, logo = "preview-logo", description = "Preview description",
            runtime = "Preview runtime", inLibrary = false,
            behaviorHints = CatalogBehaviorHints("preview-default", "preview-featured", true),
        )
        val full = fullMetaItem(
            posterShape = PosterShape.LANDSCAPE,
            poster = "full-poster",
            background = "full-background",
            logo = "full-logo",
            description = "Full description",
            releaseInfo = "2024",
            runtime = "48m",
            inLibrary = true,
            watched = true,
            behaviorHints = MetaItemBehaviorHints("full-default", "full-featured", true),
            links = listOf(
                com.stremio.core.types.resource.Link("Drama", "genre", "genre://drama"),
                com.stremio.core.types.resource.Link("Actor Name", "cast", "person://actor"),
                com.stremio.core.types.resource.Link("Director Name", "director", "person://director"),
            ),
        )

        val merged = mergeDetailsPreview(preview, full)

        assertEquals("tt123", merged.id)
        assertEquals("series", merged.type)
        assertEquals("Full title", merged.name)
        assertEquals("full-poster", merged.poster)
        assertEquals("full-background", merged.background)
        assertEquals("full-logo", merged.logo)
        assertEquals("Full description", merged.description)
        assertEquals("2024", merged.releaseInfo)
        assertEquals("48m", merged.runtime)
        assertEquals(CatalogPosterShape.Landscape, merged.posterShape)
        assertEquals(
            listOf(
                CatalogLink("Drama", "genre", "genre://drama"),
                CatalogLink("Actor Name", "cast", "person://actor"),
                CatalogLink("Director Name", "director", "person://director"),
            ),
            merged.links,
        )
        assertEquals(true, merged.inLibrary)
        assertTrue(merged.watched)
        assertEquals("full-default", merged.behaviorHints.defaultVideoId)
        assertEquals("full-featured", merged.behaviorHints.featuredVideoId)
        assertTrue(merged.behaviorHints.hasScheduledVideos)
        assertEquals(0.42f, merged.progress!!, 0f)
        assertEquals(4, merged.remainingEpisodes)
        assertEquals("s2:e3", merged.continueWatchingVideoId)
        assertTrue(merged.isContinueWatching)
        assertEquals("8.1", merged.imdbRating)
        assertTrue(merged.inCinema)
        val details = full.toMetaDetails(preview, trailerUrl = "https://trailer")
        assertEquals(listOf("Drama"), details.genres)
        assertEquals(listOf("Actor Name"), details.cast)
        assertEquals(listOf("Director Name"), details.director)
        assertEquals("https://trailer", details.trailer)
    }

    @Test
    fun `missing full metadata retains preview fields and matching identity`() {
        val preview = CatalogItem(
            id = "tt123", type = "movie", name = "Preview", poster = "p.jpg", background = "b.jpg",
            releaseInfo = "2022", imdbRating = "7.2", posterShape = CatalogPosterShape.Square,
            logo = "logo.svg", description = "Summary", runtime = "2h", links = listOf(CatalogLink("Sci-Fi", "genre")),
            inLibrary = true, behaviorHints = CatalogBehaviorHints("default", "featured", true),
        )
        val merged = mergeDetailsPreview(
            preview,
            fullMetaItem(
                type = "movie",
                posterShape = PosterShape.POSTER,
                poster = null,
                background = null,
                logo = null,
                description = null,
                releaseInfo = null,
                runtime = null,
                behaviorHints = MetaItemBehaviorHints(hasScheduledVideos = false),
            ),
        )

        assertEquals("Full title", merged.name)
        assertEquals("p.jpg", merged.poster)
        assertEquals("b.jpg", merged.background)
        assertEquals("2022", merged.releaseInfo)
        assertEquals(CatalogPosterShape.Poster, merged.posterShape)
        assertEquals("logo.svg", merged.logo)
        assertEquals("Summary", merged.description)
        assertEquals("2h", merged.runtime)
        assertEquals(listOf(CatalogLink("Sci-Fi", "genre")), merged.links)
        assertEquals("default", merged.behaviorHints.defaultVideoId)
        assertEquals("featured", merged.behaviorHints.featuredVideoId)
        assertFalse(merged.behaviorHints.hasScheduledVideos)
        assertEquals("7.2", merged.imdbRating)
    }

    @Test
    fun `partial full links enrich while retaining preview-only categories`() {
        val preview = CatalogItem(
            id = "tt123", type = "movie", name = "Preview", poster = null, background = null,
            releaseInfo = null, imdbRating = null,
            links = listOf(CatalogLink("Drama", "genre"), CatalogLink("Director Name", "director")),
        )
        val merged = mergeDetailsPreview(
            preview,
            fullMetaItem(
                type = "movie",
                links = listOf(com.stremio.core.types.resource.Link("Actor Name", "cast", "person://actor")),
            ),
        )
        assertEquals(setOf("Drama", "Director Name", "Actor Name"), merged.links.map { it.name }.toSet())
        assertEquals("person://actor", merged.links.single { it.name == "Actor Name" }.url)
    }

    @Test
    fun `mismatched full item cannot replace preview identity or presentation`() {
        val preview = CatalogItem("tt123", "movie", "Requested", null, null, null, null)
        val merged = mergeDetailsPreview(preview, fullMetaItem(id = "different", type = "series"))
        assertEquals(preview, merged)
    }

    @Test
    fun `stream mapping preserves selected behavior hints`() {
        val request = ResourceRequest(base = "https://addon", path = ResourcePath(resource = "stream", type = "movie", id = "id"))
        val stream = Stream(
            name = "1080p",
            behaviorHints = StreamBehaviorHints(
                notWebReady = true,
                bingeGroup = "provider:show",
                filename = "episode.mkv",
                videoSize = 4_294_967_296L,
                videoHash = "abc123",
            ),
            deepLinks = StreamDeepLinks(player = "player", externalPlayer = StreamDeepLinks.ExternalPlayerLink()),
        )
        val option = CoreStream(stream, request, null, "Addon").toStreamOption(index = 3)

        assertEquals("provider:show", option.bingeGroup)
        assertTrue(option.notWebReady)
        assertEquals("episode.mkv", option.filename)
        assertEquals(4_294_967_296L, option.videoSize)
        assertEquals("abc123", option.videoHash)
    }

    private fun preview(
        posterShape: PosterShape = PosterShape.POSTER,
        poster: String? = null,
        background: String? = null,
        logo: String? = null,
        description: String? = null,
        releaseInfo: String? = null,
        runtime: String? = null,
        released: pbandk.wkt.Timestamp? = null,
        links: List<com.stremio.core.types.resource.LinkPreview> = emptyList(),
        inLibrary: Boolean = false,
        watched: Boolean = false,
        inCinema: Boolean = false,
        behaviorHints: MetaItemBehaviorHints = MetaItemBehaviorHints(hasScheduledVideos = false),
    ) = MetaItemPreview(
        id = "tt123",
        type = "movie",
        name = "Example",
        posterShape = posterShape,
        poster = poster,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        runtime = runtime,
        released = released,
        links = links,
        behaviorHints = behaviorHints,
        deepLinks = MetaItemDeepLinks(),
        inLibrary = inLibrary,
        watched = watched,
        inCinema = inCinema,
    )

    private fun fullMetaItem(
        id: String = "tt123",
        type: String = "series",
        posterShape: PosterShape = PosterShape.LANDSCAPE,
        poster: String? = "full-poster",
        background: String? = "full-background",
        logo: String? = "full-logo",
        description: String? = "Full description",
        releaseInfo: String? = "2024",
        runtime: String? = "48m",
        inLibrary: Boolean = true,
        watched: Boolean = true,
        behaviorHints: MetaItemBehaviorHints = MetaItemBehaviorHints("full-default", "full-featured", true),
        links: List<com.stremio.core.types.resource.Link> = emptyList(),
    ) = MetaItem(
        id = id,
        type = type,
        name = "Full title",
        posterShape = posterShape,
        poster = poster,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        runtime = runtime,
        links = links,
        behaviorHints = behaviorHints,
        deepLinks = MetaItemDeepLinks(),
        inLibrary = inLibrary,
        watched = watched,
        receiveNotifications = false,
    )
}
