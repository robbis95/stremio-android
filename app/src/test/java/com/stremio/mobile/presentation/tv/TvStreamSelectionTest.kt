package com.stremio.mobile.presentation.tv

import com.stremio.core.models.MetaDetails
import com.stremio.core.models.LoadableStreams
import com.stremio.core.models.Streams
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.EpisodeSeriesInfo
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.model.toStreamOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TvStreamSelectionTest {
    @Test fun `next episode cannot commit before usable option is ready`() {
        val gate = TvEpisodeTransitionTracker()
        gate.prepare("old", "episode-2", "series:show:episode-2", TvNextEpisodeTrigger.Manual, 10L)

        assertNull(gate.optionReady("old", "episode-2", "series:show:episode-2", TvNextEpisodeDiscovery.Live, false, 20L))
        assertNull(gate.commit("old", "old", "episode-2", "episode-2", "series:show:episode-2", true, 30L))
        assertNull(gate.trace!!.commitNanos)
    }

    @Test fun `failed discovery does not commit`() {
        val gate = TvEpisodeTransitionTracker()
        gate.prepare("old", "episode-2", "series:show:episode-2", TvNextEpisodeTrigger.Automatic, 10L)

        // Empty/failing discovery cannot mark a usable option ready.
        assertNull(gate.optionReady("old", "episode-2", "series:show:episode-2", TvNextEpisodeDiscovery.Live, false, 20L))
        assertNull(gate.commit("old", "old", "episode-2", "episode-2", "series:show:episode-2", false, 30L))
        assertNull(gate.trace!!.commitNanos)
    }

    @Test fun `commit succeeds once and requires the current exact old attempt and video`() {
        val gate = preparedTransition(TvNextEpisodeDiscovery.Live)

        assertNull(gate.commit("old", "stale-old", "episode-2", "episode-2", "series:show:episode-2", true, 30L))
        assertNull(gate.commit("old", "old", "episode-2", "episode-3", "series:show:episode-2", true, 31L))
        assertEquals(40L, gate.commit("old", "old", "episode-2", "episode-2", "series:show:episode-2", true, 40L)!!.commitNanos)
        assertNull(gate.commit("old", "old", "episode-2", "episode-2", "series:show:episode-2", true, 50L))
    }

    @Test fun `prefetched hit and live fallback share the same commit path`() {
        listOf(TvNextEpisodeDiscovery.Prefetched, TvNextEpisodeDiscovery.Live).forEach { discovery ->
            val gate = preparedTransition(discovery)
            val committed = gate.commit("old", "old", "episode-2", "episode-2", "series:show:episode-2", true, 30L)
            assertEquals(discovery, committed!!.discovery)
            assertEquals(30L, committed.commitNanos)
        }
    }

    @Test fun `transition timing is paired to its own old and new attempts`() {
        val gate = preparedTransition(TvNextEpisodeDiscovery.Prefetched)
        gate.commit("old", "old", "episode-2", "episode-2", "series:show:episode-2", true, 30L)

        assertNull(gate.sourceActivated("old-a", "new-wrong", 40L))
        val activated = gate.sourceActivated("old", "new-a", 40_000_000L)!!
        assertEquals("old", activated.oldAttemptId)
        assertEquals("new-a", activated.newAttemptId)
        assertNull(gate.firstVisual("new-b", 60_000_000L))
        val visual = gate.firstVisual("new-a", 70_000_000L)!!
        assertEquals(30L, visual.sourceActivatedNanos?.let { (visual.firstVisualNanos!! - it) / 1_000_000L })
        assertEquals(70_000_000L, visual.firstVisualNanos)
    }

    private fun preparedTransition(discovery: TvNextEpisodeDiscovery): TvEpisodeTransitionTracker =
        TvEpisodeTransitionTracker().also { gate ->
            gate.prepare("old", "episode-2", "series:show:episode-2", TvNextEpisodeTrigger.Manual, 10L)
            gate.optionReady("old", "episode-2", "series:show:episode-2", discovery, true, 20L)
        }

    @Test fun `completed prefetch is an immediate cache hit for its attempt and exact target`() = runBlocking {
        val cache = TvStreamPrefetchCache()
        val request = cache.begin("attempt-1", "series:show:episode-2", 10L)
        request.result.complete(listOf(option("prefetched")))

        val hit = cache.find("attempt-1", "series:show:episode-2")
        assertSame(request, hit)
        assertEquals("prefetched", hit!!.result.await()!!.single().semanticKey)
        assertNull(cache.find("attempt-1", "series:show:episode-3"))
    }

    @Test fun `transition can reuse an in progress prefetch without creating another request`() = runBlocking {
        val cache = TvStreamPrefetchCache()
        val request = cache.begin("attempt-1", "series:show:episode-2", 10L)

        val reused = cache.find("attempt-1", "series:show:episode-2")
        assertSame(request, reused)
        assertFalse(reused!!.result.isCompleted)
        request.result.complete(listOf(option("late-result")))
        assertEquals("late-result", reused.result.await()!!.single().semanticKey)
    }

    @Test fun `cache miss leaves transition to live discovery fallback`() {
        val cache = TvStreamPrefetchCache()
        assertNull(cache.find("attempt-1", "series:show:episode-2"))
    }

    @Test fun `prefetch cache rejects stale attempt and invalidation`() {
        val cache = TvStreamPrefetchCache()
        val stale = cache.begin("attempt-1", "series:show:episode-2", 10L)
        cache.begin("attempt-2", "series:show:episode-2", 20L)

        assertFalse(cache.isCurrent(stale))
        assertNull(cache.find("attempt-1", "series:show:episode-2"))
        assertEquals("attempt-2", cache.find("attempt-2", "series:show:episode-2")!!.attemptId)
    }

    @Test fun `target keys use exact video identity and are stable`() {
        val item = item()
        val a = TvStreamTarget.episode(item, episode("s2e7"))!!
        val b = TvStreamTarget.episode(item, episode("s2e8"))!!
        assertNotEquals(a.semanticTargetKey, b.semanticTargetKey)
        assertEquals(a.semanticTargetKey, TvStreamTarget.episode(item, episode("s2e7"))!!.semanticTargetKey)
        assertEquals(TvStreamTarget.nonEpisodic(item("custom")), TvStreamTarget.nonEpisodic(item("custom")))
    }

    @Test fun `upcoming episode cannot create stream target`() {
        assertNull(TvStreamTarget.episode(item(), episode("s4e1", upcoming = true)))
    }

    @Test fun `selected target matching requires exact meta and stream semantics`() {
        val target = TvStreamTarget.episode(item(), episode("video-7"))
        assertTrue(tvStreamTargetMatches(details("series", "show", "video-7", false), target!!))
        assertFalse(tvStreamTargetMatches(details("series", "show", "video-8", false), target))
        assertFalse(tvStreamTargetMatches(details("series", "other", "video-7", false), target))
        val movie = TvStreamTarget.nonEpisodic(item("movie", "movie"))
        assertTrue(tvStreamTargetMatches(details("movie", "movie", null, true), movie))
        assertFalse(tvStreamTargetMatches(details("movie", "movie", null, false), movie))
    }

    @Test fun `interaction snapshot updates in place appends late data and removes missing`() {
        val old = listOf(option("a"), option("b"), option("gone"))
        val new = listOf(option("b", name = "updated"), option("a", name = "updated"), option("late"))
        val merged = stableInteractionOptions(old, new)
        assertEquals(listOf("a", "b", "late"), merged.map { it.semanticKey })
        assertEquals("updated", merged.first().name)
    }

    @Test fun `selected source remains only while its semantic identity is present`() {
        assertEquals("b", keepSelectionIfPresent("b", listOf(option("a"), option("b"))))
        assertNull(keepSelectionIfPresent("b", listOf(option("a"))))
    }

    @Test fun `stream focus falls back by semantic key then clamped index`() {
        val options = listOf(option("a"), option("b"), option("c"))
        assertEquals(2, restoredStreamIndex(options, "c", 0))
        assertEquals(1, restoredStreamIndex(options, "removed", 1))
        assertEquals(0, restoredStreamIndex(options, "removed", -5))
        assertNull(restoredStreamIndex(emptyList(), "a", 0))
    }

    @Test fun `provider filter is local and accepts only providers with ready streams`() {
        val states = listOf(
            TvStreamProviderState("a", "Addon A", TvProviderLoadStatus.Ready, 2),
            TvStreamProviderState("b", "Addon B", TvProviderLoadStatus.Error, 0),
        )
        assertNull(selectProviderLocally(states, null))
        assertEquals("a", selectProviderLocally(states, "a"))
        assertNull(selectProviderLocally(states, "b"))
        assertNull(selectProviderLocally(states, "missing"))
    }

    @Test fun `source kind and behavior hints survive StreamOption mapping`() {
        val core = coreStream(
            name = "1080p",
            source = Stream.Source.Tramvai(Stream.Tramvai("hash", 4)),
            hints = com.stremio.core.types.resource.StreamBehaviorHints(
                notWebReady = true,
                bingeGroup = "binge",
                filename = "file.mkv",
                videoHash = "video-hash",
                videoSize = 42L,
            ),
        )
        val option = core.toStreamOption(0)
        assertEquals(StreamSourceKind.Torrent, option.sourceKind)
        assertTrue(option.notWebReady)
        assertEquals("binge", option.bingeGroup)
        assertEquals("file.mkv", option.filename)
        assertEquals("video-hash", option.videoHash)
        assertEquals(42L, option.videoSize)
    }

    @Test fun `quality display parses explicit tokens from name filename then description`() {
        val fromFilename = coreStream(name = "Release", hints = com.stremio.core.types.resource.StreamBehaviorHints(
            notWebReady = false, filename = "Show.2160p.mkv",
        )).toStreamOption(0)
        val fromDescription = coreStream(name = "Release", hints = com.stremio.core.types.resource.StreamBehaviorHints(
            notWebReady = false,
        )).copy(stream = coreStream(name = "Release").stream.copy(description = "WEB-DL 1080p"))
            .toStreamOption(0)
        assertEquals("2160p", fromFilename.quality)
        assertEquals("1080p", fromDescription.quality)
        assertNull(coreStream(name = "21080p").toStreamOption(0).quality)
    }

    @Test fun `semantic stream identity ignores flat index and includes provider and torrent identity`() {
        val a = coreStream(source = Stream.Source.Tramvai(Stream.Tramvai("hash-a", 1)))
        val same = a.copy(stream = a.stream.copy(name = "Other display name"))
        val differentTorrent = a.copy(stream = a.stream.copy(source = Stream.Source.Tramvai(Stream.Tramvai("hash-b", 1))))
        val differentProvider = a.copy(streamRequest = request("other-addon"))
        assertEquals(a.toStreamOption(0).semanticKey, a.toStreamOption(91).semanticKey)
        assertFalse(a.toStreamOption(0).semanticKey == same.toStreamOption(4).semanticKey)
        assertFalse(a.toStreamOption(0).semanticKey == differentTorrent.toStreamOption(4).semanticKey)
        assertFalse(a.toStreamOption(0).semanticKey == differentProvider.toStreamOption(4).semanticKey)
    }

    @Test fun `all source variants classify without hiding unknown sources`() {
        assertEquals(StreamSourceKind.Direct, coreStream(source = Stream.Source.Url(Stream.Url("https://host/video"))).toStreamOption(0).sourceKind)
        assertEquals(StreamSourceKind.YouTube, coreStream(source = Stream.Source.YouTube(Stream.YouTube("yt"))).toStreamOption(0).sourceKind)
        assertEquals(StreamSourceKind.External, coreStream(source = Stream.Source.External(Stream.External(androidTvUrl = "app://id"))).toStreamOption(0).sourceKind)
        assertEquals(StreamSourceKind.Archive, coreStream(source = Stream.Source.Zip(Stream.Zip())).toStreamOption(0).sourceKind)
        assertEquals(StreamSourceKind.Other, coreStream(source = null).toStreamOption(0).sourceKind)
    }

    @Test fun `provider request identity is stable and separates addons`() {
        assertEquals(requestIdentity(request("one")), requestIdentity(request("one")))
        assertFalse(requestIdentity(request("one")) == requestIdentity(request("two")))
    }

    @Test fun `provider error stays isolated from ready addon streams`() {
        val error = LoadableStreams(
            title = "Broken Addon", request = request("broken"),
            content = LoadableStreams.Content.Error(com.stremio.core.models.common.Error("offline")),
        )
        val ready = LoadableStreams(
            title = "Working Addon", request = request("working"),
            content = LoadableStreams.Content.Ready(Streams(listOf(coreStream().stream))),
        )
        val emission = mapTvStreamEmission(MetaDetails(streams = listOf(error, ready)))
        assertEquals(listOf(TvProviderLoadStatus.Error, TvProviderLoadStatus.Ready), emission.providers.map { it.status })
        assertEquals(listOf(0, 1), emission.providers.map { it.readyStreamCount })
        assertEquals(1, emission.options.size)
        assertEquals("Working Addon", emission.options.single().addonTitle)
        assertFalse(emission.isLoading)
        assertEquals("offline", emission.providers.first().errorMessage)
    }

    @Test fun `pending provider is represented without blocking ready source`() {
        val pending = LoadableStreams(
            title = "Later Addon", request = request("later"),
            content = LoadableStreams.Content.Loading(com.stremio.core.models.common.Loading()),
        )
        val ready = LoadableStreams(
            title = "Fast Addon", request = request("fast"),
            content = LoadableStreams.Content.Ready(Streams(listOf(coreStream().stream))),
        )
        val emission = mapTvStreamEmission(MetaDetails(streams = listOf(ready, pending)))
        assertEquals(1, emission.options.size)
        assertTrue(emission.isLoading)
        assertEquals(TvProviderLoadStatus.Loading, emission.providers.last().status)
    }

    @Test fun `streams route round trips through details without losing origin`() {
        TvTopLevelRoute.entries.forEach { origin ->
            val originRoute = when (origin) {
                TvTopLevelRoute.Home -> TvRoute.Home
                TvTopLevelRoute.Discover -> TvRoute.Discover
                TvTopLevelRoute.Library -> TvRoute.Library
                TvTopLevelRoute.Search -> TvRoute.Search
            }
            val details = TvRouteState(originRoute, origin).openDetails().openStreams()
            assertEquals(TvRoute.Streams, details.route)
            assertEquals(origin, details.closeStreams().detailsOrigin)
            assertEquals(originRoute, details.closeStreams().closeDetails().route)
        }
    }

    @Test fun `movie target is available for any non episodic custom type`() {
        val target = TvStreamTarget.nonEpisodic(item(type = "short", id = "video"))
        assertEquals("short", target.contentType)
        assertNull(target.videoId)
        assertTrue(target.guessStreamPath)
    }

    private fun details(type: String, id: String, videoId: String?, guess: Boolean) = MetaDetails(
        selected = MetaDetails.Selected(
            metaPath = ResourcePath("meta", type, id),
            streamPath = videoId?.let { ResourcePath("stream", type, it) },
            guessStreamPath = guess,
        ),
    )

    private fun item(type: String = "series", id: String = "show") =
        CatalogItem(id, type, "Example", null, null, null, null)

    private fun episode(id: String, upcoming: Boolean = false) = EpisodeOption(
        videoId = id, season = 2, episode = 7, title = "Episode", thumbnail = null,
        releaseDate = null, watched = false, isCurrent = false, upcoming = upcoming,
        seriesInfo = EpisodeSeriesInfo(2, 7),
    )

    private fun option(key: String, name: String = key) = StreamOption(
        key = "flat-$key", semanticKey = key, name = name, description = null,
        addonTitle = "Addon", quality = null, core = coreStream(),
    )

    private fun coreStream(
        name: String? = "source",
        source: Stream.Source<*>? = Stream.Source.Url(Stream.Url("https://host/video")),
        hints: com.stremio.core.types.resource.StreamBehaviorHints = com.stremio.core.types.resource.StreamBehaviorHints(false),
    ) = CoreStream(
        stream = Stream(name = name, behaviorHints = hints, deepLinks = com.stremio.core.types.resource.StreamDeepLinks(
            player = "", externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink(),
        ), source = source),
        streamRequest = request("addon"), metaRequest = null, addonTitle = "Addon",
    )

    private fun request(base: String) = ResourceRequest(
        base = base,
        path = ResourcePath("stream", "series", "video"),
    )
}
