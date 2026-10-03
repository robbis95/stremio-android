package com.stremio.mobile.presentation.tv

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `preferred 2160p cannot exceed a 1080p display`() {
        val device = DevicePlaybackCapabilities(maxDisplayWidth = 1920, maxDisplayHeight = 1080, supports2160pOutput = false)
        val result = TvSmartStreamSelector.select(listOf(option("4k", "2160p", StreamSourceKind.Direct), option("1080", "1080p", StreamSourceKind.Direct)), "2160p", device)
        assertEquals("1080", result.selected?.semanticKey)
        assertEquals(1, result.ranked.size)
    }

    @Test fun `preferred 2160p wins on a 4k display with a verified decoder`() {
        val supported = VideoDecoderCapability(CapabilitySupport.Supported, CapabilitySupport.Supported, CapabilitySupport.Supported)
        val device = DevicePlaybackCapabilities(supports2160pOutput = true, avc = supported)
        val result = TvSmartStreamSelector.select(listOf(
            option("1080", "1080p", StreamSourceKind.Direct).copy(name = "1080p AVC"),
            option("4k", "2160p", StreamSourceKind.Direct).copy(name = "2160p AVC"),
        ), "2160p", device)
        assertEquals("4k", result.selected?.semanticKey)
        assertEquals(Compatibility.Compatible, result.ranked.first().compatibility)
    }

    @Test fun `compatible 4k retains preference on a strong measured network`() {
        val supported = VideoDecoderCapability(CapabilitySupport.Supported, CapabilitySupport.Supported, CapabilitySupport.Supported)
        val device = DevicePlaybackCapabilities(supports2160pOutput = true, avc = supported)
        val network = NetworkPlaybackProfile(estimatedThroughputBps = 100_000_000, estimateSource = NetworkEstimateSource.Media3Measured, confidence = NetworkEstimateConfidence.High)
        val streams = listOf(
            option("1080", "1080p", StreamSourceKind.Direct).copy(name = "1080p AVC", videoSize = 100_000_000),
            option("4k", "2160p", StreamSourceKind.Direct).copy(name = "2160p AVC", videoSize = 300_000_000),
        )
        val result = TvSmartStreamSelector.select(streams, "2160p", device, network, durationSeconds = 100)
        assertEquals("4k", result.selected?.semanticKey)
        assertEquals(NetworkSustainability.Comfortable, result.ranked.first().network)
    }

    @Test fun `physical supported modes can establish 4k despite current 1080 mode`() {
        val (current, max) = physicalDisplaySnapshotForModes(intArrayOf(1920, 1080), listOf(intArrayOf(1920, 1080), intArrayOf(3840, 2160)))
        assertEquals("1920x1080", "${current?.get(0)}x${current?.get(1)}")
        assertEquals("3840x2160", "${max?.get(0)}x${max?.get(1)}")
    }

    @Test fun `known unsupported codec loses to supported codec despite preferred quality`() {
        val unsupported = VideoDecoderCapability(CapabilitySupport.Unsupported, CapabilitySupport.Unsupported, CapabilitySupport.Unsupported)
        val supported = VideoDecoderCapability(CapabilitySupport.Supported, CapabilitySupport.Supported, CapabilitySupport.Supported)
        val device = DevicePlaybackCapabilities(supports2160pOutput = true, hevc = unsupported, avc = supported)
        val hevc = option("hevc", "2160p", StreamSourceKind.Direct).copy(name = "2160p HEVC")
        val avc = option("avc", "2160p", StreamSourceKind.Direct).copy(name = "2160p AVC")
        assertEquals("avc", TvSmartStreamSelector.select(listOf(hevc, avc), "2160p", device).selected?.semanticKey)
    }

    @Test fun `unknown codec metadata stays eligible`() {
        val unsupported = VideoDecoderCapability(CapabilitySupport.Unsupported, CapabilitySupport.Unsupported, CapabilitySupport.Unsupported)
        val result = TvSmartStreamSelector.select(listOf(option("unknown", "2160p").copy(name = "2160p release")), device = DevicePlaybackCapabilities(supports2160pOutput = true, avc = unsupported))
        assertEquals("unknown", result.selected?.semanticKey)
        assertTrue(result.ranked.single().compatibility != Compatibility.Incompatible)
    }

    @Test fun `HDR capability is a conservative compatibility signal`() {
        val dv = option("dv", "2160p").copy(name = "2160p Dolby Vision AVC")
        val avc = VideoDecoderCapability(CapabilitySupport.Supported, CapabilitySupport.Supported, CapabilitySupport.Supported)
        val device = DevicePlaybackCapabilities(supports2160pOutput = true, avc = avc, dolbyVision = CapabilitySupport.Unsupported)
        assertEquals(Compatibility.Incompatible, compatibility(parseStreamVideoMetadata(dv), device))
        assertEquals(Compatibility.Compatible, compatibility(parseStreamVideoMetadata(dv), device.copy(dolbyVision = CapabilitySupport.Supported)))
    }

    @Test fun `measured network estimate outranks low confidence Android link estimate`() {
        val measured = NetworkPlaybackProfile(estimatedThroughputBps = 20_000_000, estimateSource = NetworkEstimateSource.Media3Measured, confidence = NetworkEstimateConfidence.High)
        val android = measured.copy(estimatedThroughputBps = 80_000_000, estimateSource = NetworkEstimateSource.AndroidLinkEstimate, confidence = NetworkEstimateConfidence.Low)
        val streams = listOf(option("1080", "1080p").copy(videoSize = 100_000_000), option("4k", "2160p").copy(videoSize = 200_000_000))
        val result = TvSmartStreamSelector.select(streams, "2160p", device = DevicePlaybackCapabilities(), network = measured, durationSeconds = 100)
        val linkResult = TvSmartStreamSelector.select(streams, "2160p", device = DevicePlaybackCapabilities(), network = android, durationSeconds = 100)
        assertEquals("1080", result.selected?.semanticKey)
        assertEquals("4k", linkResult.selected?.semanticKey)
        assertEquals(NetworkEstimateSource.Media3Measured, measured.estimateSource)
        assertEquals(NetworkEstimateSource.AndroidLinkEstimate, android.estimateSource)
    }

    @Test fun `network headroom classifies comfortable borderline and unsustainable`() {
        val profile = NetworkPlaybackProfile(estimatedThroughputBps = 100_000_000, estimateSource = NetworkEstimateSource.Media3Measured)
        assertEquals(NetworkSustainability.Comfortable, networkSustainability(50_000_000, profile))
        assertEquals(NetworkSustainability.Borderline, networkSustainability(70_000_000, profile))
        assertEquals(NetworkSustainability.Unsustainable, networkSustainability(90_000_000, profile))
        assertEquals(NetworkSustainability.Unknown, networkSustainability(null, profile.copy(estimatedThroughputBps = null)))
    }

    @Test fun `local server URLs are excluded from passive internet measurements`() {
        assertEquals(false, isRemoteNetworkUri("http://127.0.0.1:11470/stream"))
        assertEquals(false, isRemoteNetworkUri("http://localhost:8080/video"))
        assertEquals(true, isRemoteNetworkUri("https://cdn.example/video"))
        assertEquals(NetworkEstimateConfidence.High, measuredConfidence(30_000))
        assertEquals(NetworkEstimateConfidence.Medium, measuredConfidence(10 * 60 * 1000L))
        assertEquals(NetworkEstimateConfidence.Unknown, measuredConfidence(25 * 60 * 60 * 1000L))
    }

    @Test fun `known lower bitrate wins a slow network choice while unknown network keeps quality preference`() {
        val network = NetworkPlaybackProfile(estimatedThroughputBps = 35_000_000, estimateSource = NetworkEstimateSource.Media3Measured, confidence = NetworkEstimateConfidence.High)
        val expensive4k = option("remux", "2160p", StreamSourceKind.Direct).copy(name = "2160p HEVC REMUX", videoSize = 2_000_000_000)
        val affordable4k = option("web", "2160p", StreamSourceKind.Direct).copy(name = "2160p HEVC WEB-DL", videoSize = 100_000_000)
        val lower = option("1080", "1080p", StreamSourceKind.Direct).copy(videoSize = 200_000_000)
        val slow = TvSmartStreamSelector.select(listOf(expensive4k, affordable4k, lower), "2160p", network = network, durationSeconds = 60)
        assertEquals("web", slow.selected?.semanticKey)
        val unknown = TvSmartStreamSelector.select(listOf(option("1080", "1080p"), option("4k", "2160p")), "2160p")
        assertEquals("4k", unknown.selected?.semanticKey)
    }

    @Test fun `shared metadata parser extracts video release audio size age and languages`() {
        val metadata = parseStreamVideoMetadata(option("rich", null, StreamSourceKind.Direct).copy(
            name = "The.Show.1080p.WEB-DL.AVC.DV.Atmos.DD+5.1-GROUP",
            description = "4.19 GB 5.58 Mbps 2d Audio: en Subs: es",
            size = "4.19 GB",
        ))
        assertEquals("1080p", metadata.quality)
        assertEquals(VideoCodec.Avc, metadata.codec)
        assertEquals(VideoHdr.DolbyVision, metadata.hdr)
        assertEquals(ReleaseKind.WebDl, metadata.release)
        assertEquals("DD+", metadata.audioCodec)
        assertEquals(listOf("Atmos"), metadata.audioFeatures)
        assertEquals("5.1", metadata.channels)
        assertEquals("4.19 GB", metadata.sizeLabel)
        assertEquals(5.58, metadata.bitrateMbps!!, 0.001)
        assertFalse(metadata.bitrateCalculatedFromSizeAndDuration)
        assertEquals("2d", metadata.age)
        assertEquals(listOf("EN"), metadata.languages)
        assertEquals(listOf("ES"), metadata.subtitleLanguages)
        assertEquals("GROUP", metadata.releaseLabel)
    }

    @Test fun `shared metadata parser supports web rip hevc hdr10 and common audio`() {
        val metadata = parseStreamVideoMetadata(option("web-rip", null).copy(
            name = "Movie 4K WEBRip H265 HDR10 DD 5.1",
        ))
        assertEquals("2160p", metadata.quality)
        assertEquals(VideoCodec.Hevc, metadata.codec)
        assertEquals(VideoHdr.Hdr10, metadata.hdr)
        assertEquals(ReleaseKind.WebRip, metadata.release)
        assertEquals("DD", metadata.audioCodec)
        assertEquals("5.1", metadata.channels)
    }

    @Test fun `structured file size takes precedence and average bitrate is marked calculated`() {
        val metadata = parseStreamVideoMetadata(option("size", null).copy(
            description = "Size: 700 MB",
            size = "700 MB",
            videoSize = 4_294_967_296L,
        ), durationSeconds = 6_160)
        assertEquals(4_294_967_296L, metadata.sizeBytes)
        assertEquals("4.29 GB", metadata.sizeLabel)
        assertTrue(metadata.bitrateCalculatedFromSizeAndDuration)
        assertEquals(5.58, metadata.bitrateMbps!!, 0.02)
    }

    @Test fun `duration is parsed only from supplied runtime metadata`() {
        assertEquals(6_060L, parseTrustedDurationSeconds("101 min"))
        assertEquals(6_060L, parseTrustedDurationSeconds("1h 41m"))
        assertEquals(6_060L, parseTrustedDurationSeconds("01:41:00"))
        assertNull(parseTrustedDurationSeconds(null))
        assertNull(parseTrustedDurationSeconds("unknown"))
    }

    @Test fun `missing metadata stays absent and addon title is not parsed as stream data`() {
        val metadata = parseStreamVideoMetadata(option("missing", null, provider = "4K WEB-DL Atmos 5.1"))
        assertEquals("unknown", metadata.quality)
        assertEquals(VideoCodec.Unknown, metadata.codec)
        assertEquals(VideoHdr.Unknown, metadata.hdr)
        assertEquals(ReleaseKind.Unknown, metadata.release)
        assertNull(metadata.audioCodec)
        assertTrue(metadata.audioFeatures.isEmpty())
        assertNull(metadata.channels)
        assertNull(metadata.sizeBytes)
        assertNull(metadata.bitrateMbps)
        assertNull(metadata.age)
        assertTrue(metadata.languages.isEmpty())
        assertNull(metadata.releaseLabel)
    }

    @Test fun `direct transport stays direct when text has torrent-like release details`() {
        val rich = option("rich-direct", null, StreamSourceKind.Direct).copy(name = "1080p WEB-DL AVC release")
        assertEquals(StreamSourceKind.Direct, rich.core.stream.source.let { if (it is Stream.Source.Url) StreamSourceKind.Direct else StreamSourceKind.Other })
        assertEquals("1080p", parseStreamVideoMetadata(rich).quality)
        assertEquals(ReleaseKind.WebDl, parseStreamVideoMetadata(rich).release)
    }

    @Test fun `raw url is never returned as presentation text`() {
        val raw = "https://cdn.example.invalid/1080p-WEB-DL?token=secret"
        val option = option("url", null, StreamSourceKind.Direct).copy(name = "1080p WEB-DL $raw", description = raw)
        val safeTitle = safeStreamPresentationText(option.name)
        val metadata = parseStreamVideoMetadata(option)
        assertFalse(safeTitle.contains("https://"))
        assertFalse(safeTitle.contains("token=secret"))
        assertFalse(metadata.releaseLabel?.contains("https://") == true)
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
