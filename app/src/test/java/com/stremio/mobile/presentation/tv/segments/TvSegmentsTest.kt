package com.stremio.mobile.presentation.tv.segments

import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.presentation.tv.TvPlaybackAttempt
import com.stremio.mobile.presentation.tv.TvPlaybackUiState
import com.stremio.mobile.presentation.tv.TvStreamTarget
import com.stremio.mobile.presentation.tv.tvSegmentsForAttempt
import org.junit.Assert.*
import org.junit.Test

class TvSegmentsTest {
    private val query = TvSegmentQuery("series", "show", "ep1", 1, 1, 1_000_000, "Torrent", "stream-1")
    private fun candidate(type: TvSegmentType, start: Long, end: Long, confidence: TvSegmentConfidence = TvSegmentConfidence.High, provider: String = "core") =
        TvSegmentCandidate(type, start, end, provider, confidence)

    @Test fun `Core intro maps without scaling and outro maps to credits`() {
        val candidates = coreTvSegmentCandidates(90_000, 120_000, 1_800_000, 1_300_000, 1_500_000)
        assertEquals(listOf(TvSegmentType.Intro, TvSegmentType.Credits), candidates.map { it.type })
        assertEquals(90_000L, candidates[0].startMs)
        assertEquals(120_000L, candidates[0].endMs)
        assertEquals(1_500_000L, candidates[1].endMs)
        assertEquals(TvSegmentConfidence.High, candidates[0].providerConfidence)
    }

    @Test fun `Core invalid bounds duration and missing data produce no candidate`() {
        assertTrue(coreTvSegmentCandidates(-1, 20, 100, null, 900).isEmpty())
        assertTrue(coreTvSegmentCandidates(20, 20, 100, null, 900).isEmpty())
        assertTrue(coreTvSegmentCandidates(20, 30, 0, null, 900).isEmpty())
        assertEquals(listOf(TvSegmentType.Intro), coreTvSegmentCandidates(20, 30, null, 900, 900).map { it.type })
        assertTrue(coreTvSegmentCandidates(null, null, null, null, 900).isEmpty())
        assertTrue(coreTvSegmentCandidates(20, 30, null, 200, 0).isEmpty())
    }

    @Test fun `resolver keeps only high confidence valid candidates and sorts deterministically`() {
        val result = TvSegmentResolver.resolve(listOf(
            candidate(TvSegmentType.Credits, 800, 1_000),
            candidate(TvSegmentType.Intro, 100, 200, TvSegmentConfidence.Medium),
            candidate(TvSegmentType.Intro, 100, 200),
            candidate(TvSegmentType.Preview, -5, 10),
            candidate(TvSegmentType.Recap, 10, 1_000_001),
            candidate(TvSegmentType.Intro, 200, 200),
        ), query)
        assertEquals(listOf(TvSegmentType.Intro, TvSegmentType.Credits), result.segments.map { it.type })
    }

    @Test fun `resolver deduplicates candidates and drops unsafe overlaps`() {
        val duplicate = candidate(TvSegmentType.Intro, 100, 200)
        assertEquals(1, TvSegmentResolver.resolve(listOf(duplicate, duplicate), query).segments.size)
        val conflict = TvSegmentResolver.resolve(listOf(duplicate, candidate(TvSegmentType.Credits, 150, 500)), query)
        assertTrue(conflict.segments.isEmpty())
    }

    @Test fun `cache uses identity duration and stream fingerprint and evicts oldest`() {
        val cache = TvSegmentCache(2)
        val segments = listOf(TvResolvedSegment(TvSegmentType.Intro, 1, 2, TvSegmentConfidence.High, setOf("core"), "test"))
        cache.put(query.cacheKey, segments)
        assertEquals(segments, cache.get(query.cacheKey))
        assertNull(cache.get(query.copy(contentId = "other").cacheKey))
        assertNull(cache.get(query.copy(durationMs = query.durationMs + 1).cacheKey))
        assertNull(cache.get(query.copy(semanticStreamIdentity = "other").cacheKey))
        val fingerprinted = query.copy(videoHash = "hash")
        cache.put(fingerprinted.cacheKey, segments)
        assertNull(cache.get(query.copy(videoHash = "other").cacheKey))
        cache.put(query.copy(contentId = "third").cacheKey, segments)
        assertEquals(2, cache.size())
        assertNull(cache.get(query.cacheKey))
    }

    @Test fun `coordinator returns cache hit for same resolved query`() {
        var loads = 0
        val provider = object : TvSegmentProvider {
            override val id = "core"
            override fun load(query: TvSegmentQuery): List<TvSegmentCandidate> {
                loads++
                return listOf(candidate(TvSegmentType.Intro, 100, 200))
            }
        }
        val coordinator = TvSegmentCoordinator(listOf(provider))
        assertFalse(coordinator.resolve(query).cacheHit)
        assertTrue(coordinator.resolve(query).cacheHit)
        assertEquals(1, loads)
    }

    @Test fun `contextual action chooses active intro and credits policy`() {
        val intro = resolved(TvSegmentType.Intro, 100, 200)
        val credits = resolved(TvSegmentType.Credits, 800, 1_000)
        assertEquals(TvContextualPlaybackAction.SkipSegment(intro), tvContextualPlaybackAction(listOf(intro), 150, false))
        assertNull(tvContextualPlaybackAction(listOf(intro), 99, false))
        assertEquals(TvContextualPlaybackAction.SkipSegment(credits), tvContextualPlaybackAction(listOf(credits), 850, false))
        assertEquals(TvContextualPlaybackAction.NextEpisode, tvContextualPlaybackAction(listOf(credits), 850, true))
        assertEquals(TvContextualPlaybackAction.NextEpisode, tvContextualPlaybackAction(emptyList(), 500, true))
        assertNull(tvContextualPlaybackAction(emptyList(), 500, false))
    }

    @Test fun `new attempt clears segments and stale prior result cannot cross Exo reuse`() {
        val attemptA = attempt("A")
        val attemptB = attempt("B")
        val aSegment = resolved(TvSegmentType.Intro, 100, 200)
        val aState = TvPlaybackUiState(attempt = attemptA, resolvedSegments = listOf(aSegment))
        val bState = TvPlaybackUiState(attempt = attemptB)
        assertTrue(bState.resolvedSegments.isEmpty())
        assertSame(bState, tvSegmentsForAttempt(bState, "A", listOf(aSegment)))
        assertEquals(emptyList<TvResolvedSegment>(), tvSegmentsForAttempt(bState, "B", emptyList()).resolvedSegments)
        assertEquals(listOf(aSegment), aState.resolvedSegments)
        // Retry and reused-Exo transitions both install a fresh attempt id; old callbacks are rejected identically.
        assertTrue(tvSegmentsForAttempt(TvPlaybackUiState(attempt = attempt("retry-B")), "B", listOf(aSegment)).resolvedSegments.isEmpty())
    }

    private fun resolved(type: TvSegmentType, start: Long, end: Long) =
        TvResolvedSegment(type, start, end, TvSegmentConfidence.High, setOf("core"), "test")

    private fun attempt(id: String) = TvPlaybackAttempt(
        attemptId = id,
        target = TvStreamTarget("series", "show", "Show", "ep1", "S01 E01", null, false),
        semanticStreamKey = "stream-1",
        sourceKind = StreamSourceKind.Torrent,
        providerTitle = "provider",
        quality = null,
        proxyHeadersPresent = false,
        serverRequired = false,
        requestedEngine = PlayerEngine.EXO,
        startedAtNanos = 1,
    )
}
