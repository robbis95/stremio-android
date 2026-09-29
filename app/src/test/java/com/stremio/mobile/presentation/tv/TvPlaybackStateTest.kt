package com.stremio.mobile.presentation.tv

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.player.FirstVisualSignalGate
import com.stremio.mobile.player.PlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaybackStateTest {
    private val target = TvStreamTarget.nonEpisodic(CatalogItem("movie", "movie", "Film", null, null, null, null))

    @Test fun `player route returns to streams and preserves details origin`() {
        val streams = TvRouteState(TvRoute.Home, TvTopLevelRoute.Home).openDetails().openStreams()
        val player = streams.openPlayer()
        assertEquals(TvRoute.Player, player.route)
        assertEquals(TvTopLevelRoute.Home, player.detailsOrigin)
        assertEquals(TvRoute.Streams, player.closePlayer().route)
        assertEquals(TvRoute.Home, player.closePlayer().closeStreams().closeDetails().route)
    }

    @Test fun `new explicit activation and retry get new attempt ids`() {
        assertNotEquals(newTvPlaybackAttemptId(), newTvPlaybackAttemptId())
        val first = TvPlaybackAttempt.create(target, option("semantic"), PlayerEngine.EXO, 10)
        val retry = TvPlaybackAttempt.create(target, option("semantic"), PlayerEngine.EXO, 20)
        assertEquals(first.semanticStreamKey, retry.semanticStreamKey)
        assertNotEquals(first.attemptId, retry.attemptId)
    }

    @Test fun `late attempt event is rejected`() {
        val active = TvPlaybackAttempt.create(target, option("current"), PlayerEngine.EXO, 10)
        val stale = TvPlaybackAttempt.create(target, option("old"), PlayerEngine.EXO, 5)
        val state = TvPlaybackUiState(attempt = active)
        assertTrue(isCurrentTvAttempt(state, active.attemptId))
        assertFalse(isCurrentTvAttempt(state, stale.attemptId))
    }

    @Test fun `timing exposes resolution load and visual deltas in milliseconds`() {
        val timing = TvPlaybackTiming(
            userSourceActivatedNanos = 1_000_000,
            resolutionStartedNanos = 2_000_000,
            playableSourceResolvedNanos = 12_000_000,
            playerLoadStartedNanos = 13_000_000,
            playerLoadReturnedNanos = 20_000_000,
            firstVisualSignalNanos = 51_000_000,
        )
        assertEquals(10L, timing.resolutionLatencyMs)
        assertEquals(7L, timing.playerLoadCallLatencyMs)
        assertEquals(50L, timing.ttffMs)
        assertEquals(39L, timing.postResolveToFirstVisualMs)
        assertNull(TvPlaybackTiming().ttffMs)
    }

    @Test fun `trace formatting never includes a resolved uri`() {
        val trace = safeTvPlaybackTrace(
            TvPlaybackAttempt.create(target, option("safe"), PlayerEngine.EXO, 10),
            "first-visual",
            PlayerEngine.EXO,
        )
        assertFalse(trace.contains("https://secret.example/signed?token=abc"))
        assertFalse(trace.contains("magnet:"))
        assertTrue(trace.contains("source=Direct"))
    }

    @Test fun `progress commit is gated until first visual signal`() {
        assertFalse(tvProgressReportingAllowed(TvPlaybackUiState(stage = TvPlaybackStage.Preparing)))
        assertTrue(tvProgressReportingAllowed(TvPlaybackUiState(stage = TvPlaybackStage.Playing, firstVisualObserved = true)))
    }

    @Test fun `cancel during resolving makes later callback stale`() {
        val attempt = TvPlaybackAttempt.create(target, option("cancel"), PlayerEngine.EXO, 10)
        val resolving = TvPlaybackUiState(attempt = attempt, stage = TvPlaybackStage.Resolving)
        assertTrue(isCurrentTvAttempt(resolving, attempt.attemptId))
        assertFalse(isCurrentTvAttempt(TvPlaybackUiState(), attempt.attemptId))
    }

    @Test fun `selected source key remains available after player route closes`() {
        val options = listOf(option("first"), option("selected"))
        val selection = TvStreamSelectionUiState(target = target, options = options, selectedStreamKey = "selected")
        val returnedRoute = TvRouteState(TvRoute.Player, TvTopLevelRoute.Search).closePlayer()
        assertEquals(TvRoute.Streams, returnedRoute.route)
        assertEquals("selected", keepSelectionIfPresent(selection.selectedStreamKey, options))
    }

    @Test fun `requested and actual engine results distinguish fallback`() {
        assertFalse(TvEngineResult(PlayerEngine.EXO, PlayerEngine.EXO).fallbackUsed)
        assertFalse(TvEngineResult(PlayerEngine.MPV, PlayerEngine.MPV).fallbackUsed)
        assertTrue(TvEngineResult(PlayerEngine.MPV, PlayerEngine.EXO).fallbackUsed)
    }

    @Test fun `first visual signal gate emits once and resets for a new load`() {
        val gate = FirstVisualSignalGate()
        assertTrue(gate.tryEmit())
        assertFalse(gate.tryEmit())
        gate.reset()
        assertTrue(gate.tryEmit())
    }

    @Test fun `ended policy reports completion without automatic advance`() {
        assertTrue(tvPlaybackCompletionPolicy.reportEnded)
        assertFalse(tvPlaybackCompletionPolicy.autoAdvance)
    }

    @Test fun `retry keeps the exact semantic stream while replacing attempt identity`() {
        val sameOption = option("provider:stream:one")
        val initial = TvPlaybackAttempt.create(target, sameOption, PlayerEngine.MPV, 100)
        val retry = TvPlaybackAttempt.create(target, sameOption, PlayerEngine.MPV, 200)
        assertEquals(initial.semanticStreamKey, retry.semanticStreamKey)
        assertNotEquals(initial.attemptId, retry.attemptId)
    }

    @Test fun `seek target clamps and unknown duration refuses seek`() {
        assertEquals(0L, clampTvSeekTarget(-10L, 100L))
        assertEquals(100L, clampTvSeekTarget(110L, 100L))
        assertEquals(50L, clampTvSeekTarget(50L, 100L))
        assertNull(clampTvSeekTarget(10L, 0L))
    }

    private fun option(key: String) = StreamOption(
        key = key,
        semanticKey = key,
        name = "Source",
        description = null,
        addonTitle = "Provider",
        quality = "1080p",
        core = CoreStream(
            stream = Stream(
                name = "Source",
                source = Stream.Source.Url(Stream.Url("https://secret.example/signed?token=abc")),
                behaviorHints = com.stremio.core.types.resource.StreamBehaviorHints(false),
                deepLinks = com.stremio.core.types.resource.StreamDeepLinks(
                    player = "", externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink(),
                ),
            ),
            streamRequest = ResourceRequest("addon", ResourcePath("stream", "movie", "movie")),
            metaRequest = null,
            addonTitle = "Provider",
        ),
        sourceKind = StreamSourceKind.Direct,
    )
}
