package com.stremio.mobile.presentation.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.stremio.core.types.resource.Video
import com.stremio.core.types.resource.VideoDeepLinks
import com.stremio.mobile.player.PlayerEngine
import org.junit.Test

class TvValidationFixturesTest {
    private val fixtures = TvValidationFixtures.FIXTURES

    @Test fun `fixture sequence maps unique targets to A B C and is stable for repeated targets`() {
        val sequence = TvFixtureSequence(fixtures, debugBuild = true)
        sequence.begin()
        assertEquals("A", sequence.fixtureFor(target("one"))?.id)
        assertEquals("A", sequence.fixtureFor(target("one"))?.id)
        assertNull(sequence.fixtureFor(target("two")))
        assertEquals("B", sequence.fixtureFor(target("b", TvValidationFixtures.mediaId("B")))?.id)
        assertEquals("C", sequence.fixtureFor(target("c", TvValidationFixtures.mediaId("C")))?.id)
        sequence.end()
        assertNull(sequence.fixtureFor(target("five")))
    }

    @Test fun `explicit restart assigns A to selected target and clears prior sequence`() {
        val sequence = TvFixtureSequence(fixtures, debugBuild = true)
        val original = target("original")
        sequence.begin(original)
        assertEquals("A", sequence.fixtureFor(original)?.id)
        val b = target("b", TvValidationFixtures.mediaId("B"))
        assertEquals("B", sequence.fixtureFor(b)?.id)
        assertEquals("C", sequence.fixtureFor(target("c", TvValidationFixtures.mediaId("C")))?.id)

        val selectedNormalEpisode = target("selected-again")
        sequence.begin(selectedNormalEpisode)
        assertEquals("A", sequence.fixtureFor(selectedNormalEpisode)?.id)
        assertNull(sequence.fixtureFor(target("unrelated-after-restart")))
    }

    @Test fun `hold after first visual is debug-only explicitly armed one-shot for fixture B`() {
        val sequence = TvFixtureSequence(fixtures, debugBuild = true)
        val a = target("a")
        sequence.begin(a)
        val b = target("b", TvValidationFixtures.mediaId("B"))
        val c = target("c", TvValidationFixtures.mediaId("C"))
        sequence.fixtureFor(b)
        sequence.fixtureFor(c)

        assertFalse(sequence.consumeHoldAfterFirstVisual(b))
        assertTrue(sequence.armHoldAfterFirstVisual())
        assertFalse(sequence.consumeHoldAfterFirstVisual(a))
        assertTrue(sequence.consumeHoldAfterFirstVisual(b))
        assertFalse(sequence.consumeHoldAfterFirstVisual(b))

        assertTrue(sequence.armHoldAfterFirstVisual())
        sequence.begin(a)
        assertFalse(sequence.consumeHoldAfterFirstVisual(b))
        assertFalse(TvFixtureSequence(fixtures, debugBuild = false).apply { begin(a) }.armHoldAfterFirstVisual())
    }

    @Test fun `release behavior cannot activate fixture source substitution`() {
        val releaseSequence = TvFixtureSequence(fixtures, debugBuild = false)
        releaseSequence.begin()
        assertNull(releaseSequence.fixtureFor(target("episode-a")))
        assertFalse(tvFixtureEnabled(debugBuild = false, active = true))
        assertFalse(tvFixtureEnabled(debugBuild = true, active = false))
    }

    @Test fun `all three fixture identities have separate bundled resources`() {
        assertEquals(listOf("A", "B", "C"), fixtures.map { it.id })
        assertEquals(3, fixtures.map { it.rawResourceName }.distinct().size)
        assertTrue(fixtures.all { it.rawResourceName.startsWith("tv_fixture_") })
    }

    @Test fun `core provider delegates and fixture metadata is deterministic and terminal after C`() {
        val expected = Video(
            id = "core-next", title = "Core next", upcoming = false, watched = false, currentVideo = false,
            deepLinks = VideoDeepLinks(
                metaDetailsVideos = "", metaDetailsStreams = "",
                externalPlayer = VideoDeepLinks.ExternalPlayerLink(),
            ),
        )
        var reads = 0
        val core = CoreTvNextVideoProvider { reads++; expected }
        assertEquals(expected, core.getNextVideo(target("normal"), "attempt"))
        assertEquals(1, reads)

        val sequence = TvFixtureSequence(fixtures, debugBuild = true).apply { begin() }
        val a = target("a")
        val aFixture = sequence.fixtureFor(a)
        assertEquals("A", aFixture?.id)
        val nextA = sequence.nextVideoForKnownTarget(a)
        assertEquals(TvValidationFixtures.mediaId("B"), nextA?.video?.id)
        assertEquals(nextA, sequence.nextVideoForKnownTarget(a)) // retries/recomposition are stable

        val b = target("b", TvValidationFixtures.mediaId("B"))
        assertEquals("B", sequence.fixtureFor(b)?.id)
        assertEquals(TvValidationFixtures.mediaId("C"), sequence.nextVideoForKnownTarget(b)?.video?.id)
        val c = target("c", TvValidationFixtures.mediaId("C"))
        assertEquals("C", sequence.fixtureFor(c)?.id)
        assertNull(sequence.nextVideoForKnownTarget(c)?.video)
        assertNull(sequence.nextVideoForKnownTarget(target("unrelated")))
        assertEquals(3, listOf(a.videoId, b.videoId, c.videoId).distinct().size)
    }

    @Test fun `stale attempt cannot resolve next metadata for current attempt`() {
        var reads = 0
        val provider = TvNextVideoProvider { _, _ -> reads++; null }
        assertNull(tvNextVideoForAttempt(provider, target("b"), "old-attempt", "current-attempt"))
        assertEquals(0, reads)
        tvNextVideoForAttempt(provider, target("b"), "current-attempt", "current-attempt")
        assertEquals(1, reads)
    }

    @Test fun `fixture provider is inactive by default and terminal C does not fall through to Core`() {
        var coreReads = 0
        val coreVideo = Video(
            id = "core", title = "Core", upcoming = false, watched = false, currentVideo = false,
            deepLinks = VideoDeepLinks(metaDetailsVideos = "", metaDetailsStreams = "", externalPlayer = VideoDeepLinks.ExternalPlayerLink()),
        )
        val core = CoreTvNextVideoProvider { coreReads++; coreVideo }
        val inactive = TvFixtureSequence(fixtures, debugBuild = false).apply { begin() }
        val inactiveProvider = FixtureAwareTvNextVideoProvider(core, inactive::nextVideoForKnownTarget)
        assertEquals(coreVideo.id, inactiveProvider.getNextVideo(target("ordinary"), "attempt")?.id)
        assertEquals(1, coreReads)

        val active = TvFixtureSequence(fixtures, debugBuild = true).apply { begin() }
        val a = target("a")
        active.fixtureFor(a)
        val provider = FixtureAwareTvNextVideoProvider(core, active::nextVideoForKnownTarget)
        assertEquals(TvValidationFixtures.mediaId("B"), provider.getNextVideo(a, "attempt")?.id)
        val c = target("c", TvValidationFixtures.mediaId("C"))
        active.fixtureFor(c)
        assertNull(provider.getNextVideo(c, "attempt"))
        assertEquals(1, coreReads)
        assertEquals("core", provider.getNextVideo(target("unrelated"), "attempt")?.id)
        assertEquals(2, coreReads)
    }

    @Test fun `hardware mismatch override only applies to explicitly armed debug fixture B and C`() {
        assertFalse(tvFixtureHardwareOverride(debugBuild = false, active = true, armed = true, mediaId = TvValidationFixtures.mediaId("B")))
        assertFalse(tvFixtureHardwareOverride(debugBuild = true, active = false, armed = true, mediaId = TvValidationFixtures.mediaId("B")))
        assertFalse(tvFixtureHardwareOverride(debugBuild = true, active = true, armed = false, mediaId = TvValidationFixtures.mediaId("B")))
        assertFalse(tvFixtureHardwareOverride(debugBuild = true, active = true, armed = true, mediaId = "normal-media"))
        assertTrue(tvFixtureHardwareOverride(debugBuild = true, active = true, armed = true, mediaId = TvValidationFixtures.mediaId("B")))
        assertTrue(tvFixtureHardwareOverride(debugBuild = true, active = true, armed = true, mediaId = TvValidationFixtures.mediaId("C")))
    }

    @Test fun `MPV request override is inactive until explicitly armed for an active debug sequence`() {
        val override = TvFixtureMpvEngineOverride(debugBuild = true)
        assertEquals(PlayerEngine.EXO, override.requestedEngineFor(TvValidationFixtures.mediaId("B"), PlayerEngine.EXO, fixturesActive = true))
        assertFalse(override.arm(fixturesActive = false))
        assertEquals(PlayerEngine.EXO, override.requestedEngineFor(TvValidationFixtures.mediaId("B"), PlayerEngine.EXO, fixturesActive = true))
        assertTrue(override.arm(fixturesActive = true))
    }

    @Test fun `MPV request override is release inert and fixture B only`() {
        val release = TvFixtureMpvEngineOverride(debugBuild = false)
        assertFalse(release.arm(fixturesActive = true))
        assertEquals(PlayerEngine.EXO, release.requestedEngineFor(TvValidationFixtures.mediaId("B"), PlayerEngine.EXO, fixturesActive = true))

        val debug = TvFixtureMpvEngineOverride(debugBuild = true)
        assertTrue(debug.arm(fixturesActive = true))
        assertEquals(PlayerEngine.EXO, debug.requestedEngineFor(TvValidationFixtures.mediaId("A"), PlayerEngine.EXO, fixturesActive = true))
        assertEquals(PlayerEngine.EXO, debug.requestedEngineFor(TvValidationFixtures.mediaId("C"), PlayerEngine.EXO, fixturesActive = true))
        assertEquals(PlayerEngine.MPV, debug.requestedEngineFor(TvValidationFixtures.mediaId("B"), PlayerEngine.EXO, fixturesActive = true))
        assertEquals(PlayerEngine.EXO, debug.requestedEngineFor(TvValidationFixtures.mediaId("B"), PlayerEngine.EXO, fixturesActive = true))
    }

    @Test fun `fresh fixture sequence reset clears armed MPV request override`() {
        val override = TvFixtureMpvEngineOverride(debugBuild = true)
        assertTrue(override.arm(fixturesActive = true))
        override.reset()
        assertEquals(PlayerEngine.EXO, override.requestedEngineFor(TvValidationFixtures.mediaId("B"), PlayerEngine.EXO, fixturesActive = true))
    }

    private fun target(id: String, videoId: String? = "catalog-$id") = TvStreamTarget(
        contentType = "series", contentId = "show", contentName = "Show", videoId = videoId,
        episodeLabel = "S01E1", guessStreamPath = false,
    )

}
