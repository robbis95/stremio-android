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

    @Test fun `player initial focus is explicit for starting playing and error states`() {
        assertEquals(TvPlayerFocusTarget.PlayerSurface, tvPlayerInitialFocusTarget(TvPlaybackStage.Resolving, false))
        assertEquals(TvPlayerFocusTarget.PlayPause, tvPlayerInitialFocusTarget(TvPlaybackStage.Playing, true))
        assertEquals(TvPlayerFocusTarget.Retry, tvPlayerInitialFocusTarget(TvPlaybackStage.Error, false))
    }

    @Test fun `timing exposes resolution load and visual deltas in milliseconds`() {
        val timing = TvPlaybackTiming(
            userSourceActivatedNanos = 5_000_000,
            userPlayActivatedNanos = 0,
            discoveryStartedNanos = 0,
            firstCandidateNanos = 2_000_000,
            candidateSnapshotNanos = 3_000_000,
            selectorReturnedNanos = 4_000_000,
            serverStartRequestedNanos = 5_000_000,
            serverReadyNanos = 10_000_000,
            resolutionStartedNanos = 2_000_000,
            playableSourceResolvedNanos = 12_000_000,
            playerLoadStartedNanos = 13_000_000,
            playerLoadReturnedNanos = 20_000_000,
            firstVisualSignalNanos = 51_000_000,
        )
        assertEquals(10L, timing.resolutionLatencyMs)
        assertEquals(7L, timing.playerLoadCallLatencyMs)
        assertEquals(46L, timing.ttffMs)
        assertEquals(51L, timing.userActivationToFirstVisualMs)
        assertEquals(39L, timing.postResolveToFirstVisualMs)
        assertEquals(5L, timing.serverStartupMs)
        assertEquals(38L, timing.playerPrepareToFirstVisualMs)
        assertEquals(4L, timing.discoveryMs)
        assertEquals(2L, timing.smartSettleMs)
        assertNull(TvPlaybackTiming().ttffMs)
    }

    @Test fun `startup summary reports original activation through first visual without source data`() {
        val attempt = TvPlaybackAttempt.create(target, option("safe"), PlayerEngine.EXO, 10_000_000)
        val trace = TvStartupTrace(
            userActivatedNanos = 1_000_000,
            discoveryStartedNanos = 1_000_000,
            firstCandidateNanos = 2_000_000,
            candidateSnapshotNanos = 3_000_000,
            selectorReturnedNanos = 4_000_000,
            candidateCount = 56,
            attemptCount = 2,
            winningRank = 2,
        )
        val summary = safeTvStartupSummary(
            trace,
            attempt,
            TvPlaybackTiming(
                userPlayActivatedNanos = 1_000_000,
                resolutionStartedNanos = 10_000_000,
                playableSourceResolvedNanos = 390_000_000,
                playerLoadStartedNanos = 400_000_000,
                firstVisualSignalNanos = 1_310_000_000,
            ),
        )
        assertTrue(summary.contains("rank=2"))
        assertTrue(summary.contains("smartCandidates=56"))
        assertTrue(summary.contains("fallbackAttempts=2"))
        assertTrue(summary.contains("totalMs=1309"))
        listOf("https://", "magnet:", "token=").forEach { assertFalse(summary.contains(it, ignoreCase = true)) }
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

    @Test fun `failed source resolution releases only the retained player owned by that reuse attempt`() {
        assertTrue(shouldReleaseRetainedPlayerAfterTvFailure("attempt-b", "attempt-b"))
        assertFalse(shouldReleaseRetainedPlayerAfterTvFailure("attempt-b", "stale-attempt"))
        assertFalse(shouldReleaseRetainedPlayerAfterTvFailure(null, "cold-attempt"))
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

    @Test fun `next prompt follows configured final window and hides after seeking out`() {
        assertFalse(tvNextEpisodePromptVisible(800_000, 1_000_000, 30_000, true, false))
        assertTrue(tvNextEpisodePromptVisible(975_000, 1_000_000, 30_000, true, false))
        assertFalse(tvNextEpisodePromptVisible(975_000, 1_000_000, 30_000, false, false))
        assertFalse(tvNextEpisodePromptVisible(975_000, 1_000_000, 30_000, true, true))
        assertFalse(tvNextEpisodePromptVisible(975_000, 1_000_000, 0, true, false))
        assertFalse(tvNextEpisodePromptVisible(800_000, 1_000_000, 30_000, true, true))
    }

    @Test fun `dismissal persists for current attempt and stale next state is rejected`() {
        val current = TvNextEpisodeState(playbackAttemptId = "attempt-2", videoId = "episode-7")
        val dismissed = tvNextEpisodeDismiss(current)
        assertTrue(dismissed.dismissed)
        assertFalse(dismissed.promptVisible)
        assertTrue(isCurrentTvNextEpisode(dismissed, "attempt-2"))
        assertFalse(isCurrentTvNextEpisode(dismissed, "attempt-1"))
        assertFalse(TvNextEpisodeState(playbackAttemptId = "attempt-3", videoId = "episode-8").dismissed)
    }

    @Test fun `ended advances only when binge watching and a next video exist`() {
        assertTrue(tvShouldAutoAdvanceEnded(true, true, true))
        assertFalse(tvShouldAutoAdvanceEnded(true, true, false))
        assertFalse(tvShouldAutoAdvanceEnded(true, false, true))
        assertFalse(tvShouldAutoAdvanceEnded(false, true, true))
    }

    @Test fun `transition can be started only once`() {
        val available = TvNextEpisodeState(playbackAttemptId = "attempt", videoId = "next")
        val started = tvNextEpisodeTransitionStarted(available)!!
        assertEquals(TvNextEpisodeTransition.Loading, started.transition)
        assertNull(tvNextEpisodeTransitionStarted(started))
        assertNull(tvNextEpisodeTransitionStarted(TvNextEpisodeState()))
    }

    @Test fun `next video target preserves series identity and uses exact video id`() {
        val series = TvStreamTarget("series", "show-id", "Show", videoId = "old", episodeLabel = "S01E05", guessStreamPath = false)
        val next = nextEpisodeTarget(series, "core-video-6", "S01E06 · The Next Chapter")
        assertEquals("series", next.contentType)
        assertEquals("show-id", next.contentId)
        assertEquals("Show", next.contentName)
        assertEquals("core-video-6", next.videoId)
        assertEquals("S01E06 · The Next Chapter", next.episodeLabel)
        assertFalse(next.guessStreamPath)
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
