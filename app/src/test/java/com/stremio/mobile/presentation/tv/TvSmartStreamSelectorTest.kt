package com.stremio.mobile.presentation.tv

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSmartStreamSelectorTest {
    @Test fun `preferred 1080p beats higher and lower qualities`() {
        val result = TvSmartStreamSelector.select(listOf(option("4k", "2160p", StreamSourceKind.Direct), option("1080", "1080p", StreamSourceKind.Torrent), option("720", "720p", StreamSourceKind.Direct)), "1080p")
        assertEquals("1080", result.selected?.semanticKey)
    }

    @Test fun `highest conventional quality wins without preference`() {
        assertEquals("4k", TvSmartStreamSelector.select(listOf(option("720", "720p"), option("4k", "2160p"))).selected?.semanticKey)
    }

    @Test fun `unknown quality remains eligible`() {
        val result = TvSmartStreamSelector.select(listOf(option("mystery", null)))
        assertEquals("mystery", result.selected?.semanticKey)
        assertEquals("unknown", result.ranked.first().quality)
    }

    @Test fun `explicit parsed quality takes precedence over conflicting title tokens`() {
        val option = option("explicit", "1080p", StreamSourceKind.Direct).copy(name = "2160p release filename")
        val result = TvSmartStreamSelector.select(listOf(option), "1080p")
        assertEquals("1080p", result.ranked.single().quality)
        assertTrue(result.ranked.single().reasons.contains("preferred-quality"))
    }

    @Test fun `direct wins when quality is comparable`() {
        val result = TvSmartStreamSelector.select(listOf(option("torrent", "1080p", StreamSourceKind.Torrent, seeds = "150"), option("direct", "1080p", StreamSourceKind.Direct)))
        assertEquals("direct", result.selected?.semanticKey)
    }

    @Test fun `better resolution torrent beats low resolution direct`() {
        val result = TvSmartStreamSelector.select(listOf(option("direct", "480p", StreamSourceKind.Direct), option("torrent", "1080p", StreamSourceKind.Torrent, seeds = "150")))
        assertEquals("torrent", result.selected?.semanticKey)
    }

    @Test fun `healthy 2160p torrent beats 1080p direct without a preference`() {
        assertEquals("torrent", TvSmartStreamSelector.select(listOf(
            option("direct", "1080p", StreamSourceKind.Direct),
            option("torrent", "2160p", StreamSourceKind.Torrent, seeds = "100"),
        )).selected?.semanticKey)
    }

    @Test fun `reasonable 2160p torrent beats 1080p direct without a preference`() {
        assertEquals("torrent", TvSmartStreamSelector.select(listOf(
            option("direct", "1080p", StreamSourceKind.Direct),
            option("torrent", "2160p", StreamSourceKind.Torrent, seeds = "20"),
        )).selected?.semanticKey)
    }

    @Test fun `weak 2160p torrent loses to 1080p direct without a preference`() {
        assertEquals("direct", TvSmartStreamSelector.select(listOf(
            option("direct", "1080p", StreamSourceKind.Direct),
            option("torrent", "2160p", StreamSourceKind.Torrent, seeds = "2"),
        )).selected?.semanticKey)
    }

    @Test fun `dead 2160p torrent loses to 1080p direct without a preference`() {
        assertEquals("direct", TvSmartStreamSelector.select(listOf(
            option("direct", "1080p", StreamSourceKind.Direct),
            option("torrent", "2160p", StreamSourceKind.Torrent, seeds = "0"),
        )).selected?.semanticKey)
    }

    @Test fun `explicit 2160p preference keeps weak 2160p torrent ahead`() {
        assertEquals("torrent", TvSmartStreamSelector.select(listOf(
            option("direct", "1080p", StreamSourceKind.Direct),
            option("torrent", "2160p", StreamSourceKind.Torrent, seeds = "2"),
        ), "2160p").selected?.semanticKey)
    }

    @Test fun `preferred 1080p direct wins comparable source alternatives`() {
        assertEquals("direct", TvSmartStreamSelector.select(listOf(
            option("torrent", "1080p", StreamSourceKind.Torrent, seeds = "150"),
            option("direct", "1080p", StreamSourceKind.Direct),
            option("higher", "2160p", StreamSourceKind.Direct),
        ), "1080p").selected?.semanticKey)
    }

    @Test fun `provider title does not determine rank`() {
        val first = option("stable-a", "1080p", StreamSourceKind.Torrent, seeds = "50", provider = "Zeta")
        val second = option("stable-b", "1080p", StreamSourceKind.Torrent, seeds = "20", provider = "Alpha")
        assertEquals("stable-a", TvSmartStreamSelector.select(listOf(first, second)).selected?.semanticKey)
    }

    @Test fun `healthy seeds beat zero and buckets are sensible`() {
        val options = listOf(option("zero", "1080p", StreamSourceKind.Torrent, seeds = "0"), option("hundred", "1080p", StreamSourceKind.Torrent, seeds = "809"), option("fifty", "1080p", StreamSourceKind.Torrent, seeds = "55"))
        val result = TvSmartStreamSelector.select(options)
        assertEquals("hundred", result.selected?.semanticKey)
        assertEquals(listOf("100+", "50+", "0"), result.ranked.map { it.seedsBucket })
    }

    @Test fun `unknown seed count remains eligible`() {
        assertEquals("unknown", TvSmartStreamSelector.select(listOf(option("u", "1080p", StreamSourceKind.Torrent))).ranked.single().seedsBucket)
    }

    @Test fun `size is a tie breaker only`() {
        val smaller = option("small", "1080p", StreamSourceKind.Direct, size = "700 MB")
        val larger = option("large", "1080p", StreamSourceKind.Direct, size = "2 GB")
        assertEquals("small", TvSmartStreamSelector.select(listOf(larger, smaller)).selected?.semanticKey)
        assertEquals("4k", TvSmartStreamSelector.select(listOf(smaller, option("4k", "2160p", StreamSourceKind.Direct, size = "20 GB"))).selected?.semanticKey)
    }

    @Test fun `input and provider arrival order do not affect winner`() {
        val a = option("a", "1080p", StreamSourceKind.Torrent, seeds = "150")
        val b = option("b", "1080p", StreamSourceKind.Direct)
        val c = option("c", "720p", StreamSourceKind.Direct)
        assertEquals(TvSmartStreamSelector.select(listOf(a, b, c)).selected?.semanticKey, TvSmartStreamSelector.select(listOf(c, b, a)).selected?.semanticKey)
        // Simulates the same providers completing in reverse order: rank the final union, not first emission.
        val arrivalsOne = listOf(a) + listOf(b, c)
        val arrivalsTwo = listOf(c) + listOf(b, a)
        assertEquals(TvSmartStreamSelector.select(arrivalsOne).selected?.semanticKey, TvSmartStreamSelector.select(arrivalsTwo).selected?.semanticKey)
    }

    @Test fun `exact ties use stable semantic key`() {
        val a = option("alpha", "1080p", StreamSourceKind.Direct)
        val z = option("zeta", "1080p", StreamSourceKind.Direct)
        assertEquals("alpha", TvSmartStreamSelector.select(listOf(z, a)).selected?.semanticKey)
    }

    @Test fun `semantic duplicates are ranked once`() {
        val duplicateA = option("same", "720p")
        val duplicateB = option("same", "1080p")
        val ranked = TvSmartStreamSelector.select(listOf(duplicateA, duplicateB)).ranked
        assertEquals(1, ranked.size)
    }

    @Test fun `selection window waits settle completes immediately and hard stops`() {
        assertEquals(false, tvSmartShouldFinish(false, 500, true))
        assertEquals(true, tvSmartShouldFinish(true, 100, true))
        assertEquals(true, tvSmartShouldFinish(false, 1_300, true))
        assertEquals(true, tvSmartShouldFinish(false, 5_000, false))
    }

    @Test fun `stale target result is ignored`() {
        val target = TvStreamTarget("series", "show", "Show", videoId = "one", guessStreamPath = false)
        val stale = target.copy(videoId = "two")
        assertTrue(tvSmartResultApplies(target, target, true))
        assertEquals(false, tvSmartResultApplies(stale, target, true))
        assertEquals(false, tvSmartResultApplies(target, target, false))
    }

    @Test fun `empty and usable candidate states`() {
        assertNull(TvSmartStreamSelector.select(emptyList()).selected)
        assertEquals("usable", TvSmartStreamSelector.select(listOf(option("usable", null))).selected?.semanticKey)
    }

    private fun option(key: String, quality: String?, kind: StreamSourceKind = StreamSourceKind.Other, seeds: String? = null,
                       size: String? = null, provider: String = "Provider") = StreamOption(
        key = key, semanticKey = key, name = "${quality.orEmpty()} stream", description = null, addonTitle = provider,
        quality = quality, core = coreStream(provider), seeds = seeds, size = size, sourceKind = kind,
    )

    private fun coreStream(provider: String) = CoreStream(
        stream = Stream(name = "fixture", behaviorHints = com.stremio.core.types.resource.StreamBehaviorHints(false), deepLinks = com.stremio.core.types.resource.StreamDeepLinks(player = "", externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink()), source = Stream.Source.Url(Stream.Url("https://example.invalid/video"))),
        streamRequest = ResourceRequest(provider, ResourcePath("stream", "movie", "movie")), metaRequest = null, addonTitle = provider,
    )
}
