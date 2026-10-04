package com.stremio.mobile.presentation.tv

import android.content.ContextWrapper
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSmartFallbackTest {
    private val target = TvStreamTarget("series", "show", "Show", videoId = "episode-1", guessStreamPath = false)
    private val candidates = listOf(option("A"), option("B"), option("C"), option("D"), option("E"), option("F"))

    @Test fun `startup failure advances to next ranked candidate`() {
        val session = startedSession()
        val next = next(session, "attempt-A")
        assertNotNull(next)
        assertEquals(1, next)
        assertEquals("B", session.rankedCandidates[next!!].semanticKey)
    }

    @Test fun `multiple startup failures advance in snapshot order to success`() {
        var session = startedSession()
        val order = mutableListOf("A")
        listOf("attempt-A", "attempt-B").forEachIndexed { index, failedId ->
            val next = next(session, failedId)!!
            session = session.start(next, "attempt-${('B' + index)}")!!
            order += session.rankedCandidates[next].semanticKey
        }
        assertEquals(listOf("A", "B", "C"), order)
        assertEquals(listOf("A", "B", "C"), session.rankedCandidates.take(3).map { it.semanticKey })
    }

    @Test fun `failed candidates are attempted once and chain ends at limit`() {
        var session = startedSession()
        val attempted = mutableListOf("A")
        var currentId = "attempt-A"
        while (true) {
            val index = next(session, currentId) ?: break
            currentId = "attempt-${index + 1}"
            session = session.start(index, currentId)!!
            attempted += session.rankedCandidates[index].semanticKey
        }
        assertEquals(5, attempted.size)
        assertEquals(attempted.size, attempted.toSet().size)
        assertNull(next(session, currentId))
    }

    @Test fun `first visual commits session and later error cannot advance`() {
        val session = startedSession().commit("attempt-A")
        assertNull(next(session, "attempt-A", firstVisual = true))
        assertNull(next(session, "attempt-A"))
    }

    @Test fun `manual playback cannot advance automatically`() {
        assertNull(next(startedSession(), "attempt-A", smartPlay = false))
    }

    @Test fun `late failure from previous attempt is ignored`() {
        val session = startedSession().start(1, "attempt-B")!!
        assertNull(next(session, "attempt-A"))
        assertEquals(2, next(session, "attempt-B"))
    }

    @Test fun `Back cancellation prevents delayed fallback`() {
        val session = startedSession().cancel()
        assertNull(next(session, "attempt-A"))
    }

    @Test fun `automatic attempts stop at five including first candidate`() {
        var session = startedSession()
        repeat(4) { index ->
            val next = next(session, session.currentAttemptId!!)!!
            session = session.start(next, "attempt-${index + 2}")!!
        }
        assertEquals(5, session.attemptedSemanticKeys.size)
        assertNull(next(session, session.currentAttemptId!!))
    }

    @Test fun `failure does not reorder ranked snapshot`() {
        val snapshot = candidates.map { it.semanticKey }
        var session = startedSession()
        val next = next(session, "attempt-A")!!
        session = session.start(next, "attempt-B")!!
        assertEquals(snapshot, session.rankedCandidates.map { it.semanticKey })
    }

    @Test fun `real Tramvai source can live in ranked fallback session`() {
        val fixtures = TvValidationFixtures(ContextWrapper(null), com.stremio.mobile.data.repository.PlayableSourceResolver {
            error("This session test does not resolve sources")
        }, debugBuild = true)
        val torrent = requireNotNull(fixtures.rawTorrentOption(target))
        val session = TvSmartPlaybackSession(target, listOf(option("A"), torrent, option("C")))
            .start(0, "attempt-A")!!

        assertEquals(StreamSourceKind.Torrent, session.rankedCandidates[1].sourceKind)
        assertTrue(session.rankedCandidates[1].core.stream.source is Stream.Source.Tramvai)
        assertEquals(1, next(session, "attempt-A"))
        assertEquals(torrent.semanticKey, session.rankedCandidates[next(session, "attempt-A")!!].semanticKey)
    }

    @Test fun `new target invalidates previous session`() {
        val otherTarget = target.copy(videoId = "episode-2")
        val session = startedSession()
        assertNull(tvSmartFallbackNextCandidateIndex(
            session, "attempt-A", "attempt-A", otherTarget.semanticTargetKey,
            firstVisualObserved = false, smartPlay = true,
        ))
    }

    private fun startedSession(): TvSmartPlaybackSession = TvSmartPlaybackSession(target, candidates)
        .start(0, "attempt-A")!!

    private fun next(session: TvSmartPlaybackSession, failedId: String, firstVisual: Boolean = false, smartPlay: Boolean = true): Int? =
        tvSmartFallbackNextCandidateIndex(
            session = session,
            failedAttemptId = failedId,
            currentAttemptId = failedId,
            currentTargetKey = target.semanticTargetKey,
            firstVisualObserved = firstVisual,
            smartPlay = smartPlay,
        )

    private fun option(key: String) = StreamOption(
        key = key,
        semanticKey = key,
        name = key,
        description = null,
        addonTitle = "fixture",
        quality = "1080p",
        core = CoreStream(
            stream = Stream(
                name = key,
                behaviorHints = com.stremio.core.types.resource.StreamBehaviorHints(false),
                deepLinks = com.stremio.core.types.resource.StreamDeepLinks(player = "", externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink()),
                source = Stream.Source.Url(Stream.Url("https://example.invalid/$key")),
            ),
            streamRequest = ResourceRequest("fixture", ResourcePath("stream", "series", "show")),
            metaRequest = null,
            addonTitle = "fixture",
        ),
        sourceKind = StreamSourceKind.Direct,
    )
}
