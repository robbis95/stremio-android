package com.stremio.mobile.data.model

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.MetaItemBehaviorHints
import com.stremio.core.types.resource.MetaItemDeepLinks
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
        behaviorHints = MetaItemBehaviorHints(hasScheduledVideos = false),
        deepLinks = MetaItemDeepLinks(),
        inLibrary = inLibrary,
        watched = watched,
        inCinema = inCinema,
    )
}
