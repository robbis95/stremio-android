package com.stremio.mobile.core

import com.stremio.core.models.LoadableConvertedStream
import com.stremio.core.models.Player
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.presentation.tv.TvPlaybackAttempt
import com.stremio.mobile.presentation.tv.TvStreamTarget
import com.stremio.mobile.presentation.tv.safeTvPlaybackTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PlaybackResolutionTest {
    private val request = ResourceRequest("addon", ResourcePath("stream", "series", "video-7"))
    private val metaRequest = ResourceRequest("meta-addon", ResourcePath("meta", "series", "show"))

    @Test fun `raw immediate fallback is disabled for proxied URL plain HTTP and torrent`() {
        assertFalse(mayUseRawStreamFallback(url("https://source/video", proxy = true)))
        assertFalse(mayUseRawStreamFallback(url("http://source/video")))
        assertFalse(mayUseRawStreamFallback(torrent()))
    }

    @Test fun `local server policy classifies Core conversion sources`() {
        assertTrue(streamRequiresLocalServer(torrent()))
        assertTrue(streamRequiresLocalServer(url("https://source/video", proxy = true)))
        assertTrue(streamRequiresLocalServer(url("ftp://source/video")))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.YouTube(Stream.YouTube("yt")), behaviorHints = hints(), deepLinks = links())))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.Rar(Stream.Rar()), behaviorHints = hints(), deepLinks = links())))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.Zip(Stream.Zip()), behaviorHints = hints(), deepLinks = links())))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.Zip7(Stream.Zip7()), behaviorHints = hints(), deepLinks = links())))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.Tgz(Stream.Tgz()), behaviorHints = hints(), deepLinks = links())))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.Tar(Stream.Tar()), behaviorHints = hints(), deepLinks = links())))
        assertTrue(streamRequiresLocalServer(Stream(source = Stream.Source.Nzb(Stream.Nzb()), behaviorHints = hints(), deepLinks = links())))
        assertFalse(streamRequiresLocalServer(url("https://source/video")))
        assertFalse(streamRequiresLocalServer(url("http://source/video")))
    }

    @Test fun `selected matcher requires exact stream and both request identities`() {
        val expected = selected(url("https://source/video"))
        val ready = Player(
            selected = expected,
            stream = LoadableConvertedStream(LoadableConvertedStream.Content.Ready(converted("https://source/video"))),
        )
        assertTrue(matchingPlayerStream(ready, expected) is MatchingPlayerStream.Ready)
        assertTrue(
            matchingPlayerStream(
                ready.copy(selected = expected.copy(stream = expected.stream.copy(deepLinks = links("derived-link")))),
                expected,
            ) is MatchingPlayerStream.Ready,
        )

        val previous = ready.copy(selected = selected(url("https://old/video")))
        assertEquals(MatchingPlayerStream.Stale, matchingPlayerStream(previous, expected))
        assertEquals(
            MatchingPlayerStream.Stale,
            matchingPlayerStream(ready.copy(selected = expected.copy(streamRequest = request.copy(base = "wrong"))), expected),
        )
        assertEquals(
            MatchingPlayerStream.Stale,
            matchingPlayerStream(ready.copy(selected = expected.copy(metaRequest = metaRequest.copy(base = "wrong"))), expected),
        )
    }

    @Test fun `loading and previous ready state remain unresolved`() {
        val expected = selected(url("https://source/video"))
        val prior = Player(selected = selected(url("https://old/video")), stream = ready("http://127.0.0.1/old"))
        assertNull(resolvedCoreSource(matchingPlayerStream(prior, expected), expected.stream))
        val loading = Player(selected = expected, stream = LoadableConvertedStream(LoadableConvertedStream.Content.Loading(com.stremio.core.models.common.Loading())))
        assertEquals(MatchingPlayerStream.Loading, matchingPlayerStream(loading, expected))
    }

    @Test fun `synchronous dispatch update is observed after subscription`() = runBlocking {
        var subscribed = false
        var current = "previous-ready"
        val observed = raceSafeDispatchObservations(
            subscribe = { onChange ->
                subscribed = true
                AutoCloseable { }
            },
            dispatch = {
                assertTrue("listener must be registered before dispatch", subscribed)
                current = "requested-converted-ready"
            },
            readCurrent = { current },
        ).first()

        assertEquals("requested-converted-ready", observed)
    }

    @Test fun `converted Ready uses Core streaming endpoint and marks conversion`() {
        val original = torrent()
        val expected = selected(original)
        val convertedTorrent = torrent().copy(
            deepLinks = links("http://127.0.0.1:11470/hash/4?tr=one&f=Show"),
        )
        val result = resolvedCoreSource(MatchingPlayerStream.Ready(convertedTorrent), original)!!
        assertEquals("http://127.0.0.1:11470/hash/4?tr=one&f=Show", result.playableUri)
        assertEquals("core-converted", result.resolutionKind)
        assertEquals("Torrent", result.convertedSourceKind)
        assertTrue(result.usedCoreConversion)
        assertTrue(result.usedStreamingServer)
        assertEquals(original, expected.stream) // original tracker/file metadata is passed to Core unchanged
    }

    @Test fun `converted error is typed and never falls back to raw stream`() {
        val original = torrent()
        val expected = selected(original)
        val player = Player(
            selected = expected,
            stream = LoadableConvertedStream(LoadableConvertedStream.Content.Error(com.stremio.core.models.common.Error("private detail"))),
        )
        val matching = matchingPlayerStream(player, expected)
        assertEquals(MatchingPlayerStream.Error, matching)
        val error = try {
            resolvedCoreSource(matching, original)
            null
        } catch (failure: PlaybackResolutionException) { failure }
        assertEquals(PlaybackResolutionFailure.CoreConversionError, error?.category)
        assertFalse(mayUseRawStreamFallback(original))
    }

    @Test fun `timeout category is deterministic and safe`() {
        val failure = coreResolutionTimeoutFailure()
        assertEquals(PlaybackResolutionFailure.CoreResolutionTimeout, failure.category)
        assertEquals("CoreResolutionTimeout", failure.message)
    }

    @Test fun `trace includes categories without endpoint header token or magnet data`() {
        val option = StreamOption(
            key = "key", semanticKey = "semantic", name = "Torrent", description = null,
            addonTitle = "Torrentio", quality = "1080p", core = CoreStream(torrent(), request, metaRequest, "Torrentio"),
            sourceKind = StreamSourceKind.Torrent,
        )
        val target = TvStreamTarget.nonEpisodic(CatalogItem("show", "series", "Show", null, null, null, null))
        val attempt = TvPlaybackAttempt.create(target, option, PlayerEngine.EXO, 1)
        val trace = safeTvPlaybackTrace(
            attempt, "resolved", server = "ready", resolution = "core-converted", convertedSource = "Torrent",
        )
        assertTrue(trace.contains("proxyHeaders=no"))
        assertTrue(trace.contains("serverRequired=yes"))
        assertTrue(trace.contains("server=ready"))
        assertTrue(trace.contains("resolution=core-converted"))
        assertTrue(trace.contains("convertedSource=Torrent"))
        listOf("http://", "magnet:", "Authorization", "secret-token", "private detail").forEach {
            assertFalse("trace leaked $it", trace.contains(it, ignoreCase = true))
        }
    }

    private fun selected(stream: Stream) = Player.Selected(stream, request, metaRequest)

    private fun ready(url: String) = LoadableConvertedStream(LoadableConvertedStream.Content.Ready(converted(url)))

    private fun converted(url: String) = Stream(
        source = Stream.Source.Url(Stream.Url("https://placeholder.invalid")),
        behaviorHints = hints(),
        deepLinks = links(url),
    )

    private fun url(value: String, proxy: Boolean = false) = Stream(
        source = Stream.Source.Url(Stream.Url(value)),
        behaviorHints = if (proxy) hints().copy(proxyHeaders = com.stremio.core.types.resource.StreamProxyHeaders(request = mapOf("Authorization" to "secret-token"))) else hints(),
        deepLinks = links(),
    )

    private fun torrent() = Stream(
        source = Stream.Source.Tramvai(Stream.Tramvai("private-hash", 4, listOf("tracker-secret"), listOf("Show S02E07"))),
        behaviorHints = hints(), deepLinks = links(),
    )

    private fun hints() = com.stremio.core.types.resource.StreamBehaviorHints(false)

    private fun links(streaming: String? = null) = com.stremio.core.types.resource.StreamDeepLinks(
        player = "", externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink(streaming = streaming),
    )
}
