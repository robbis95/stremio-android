package com.stremio.mobile.presentation.tv

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind

/** DEBUG source data for legal end-to-end raw Torrent playback validation. */
internal object TvRawTorrentFixtureSource {
    internal const val RAW_TORRENT_KEY = "tv-validation-raw-torrent-sintel"
    internal const val SINTEL_INFO_HASH = "08ada5a7a6183aae1e09d831df6748d566095a10"

    // Tracker URLs are copied from the official WebTorrent Sintel magnet example.
    internal val SINTEL_ANNOUNCE = listOf(
        "udp://explodie.org:6969",
        "udp://tracker.coppersurfer.tk:6969",
        "udp://tracker.empire-js.us:1337",
        "udp://tracker.leechers-paradise.org:6969",
        "udp://tracker.opentrackr.org:1337",
        "wss://tracker.btorrent.xyz",
        "wss://tracker.fastcast.nz",
        "wss://tracker.openwebtorrent.com",
    )

    fun target(): TvStreamTarget = TvStreamTarget(
        contentType = "movie",
        contentId = "debug-sintel",
        contentName = "Sintel",
        videoId = "debug-sintel",
        guessStreamPath = false,
    )

    fun create(target: TvStreamTarget): StreamOption {
        val request = ResourceRequest(
            base = "debug-raw-torrent",
            path = ResourcePath("stream", target.contentType, target.videoId ?: target.contentId),
        )
        val source = Stream.Source.Tramvai(
            Stream.Tramvai(
                infoHash = SINTEL_INFO_HASH,
                fileIdx = null,
                announce = SINTEL_ANNOUNCE,
                fileMustInclude = emptyList(),
            ),
        )
        return StreamOption(
            key = RAW_TORRENT_KEY,
            semanticKey = RAW_TORRENT_KEY,
            name = "Raw Torrent · Sintel",
            description = "Official WebTorrent Sintel validation media",
            addonTitle = "DEBUG raw torrent",
            quality = null,
            core = CoreStream(
                stream = Stream(
                    name = "Sintel",
                    source = source,
                    behaviorHints = com.stremio.core.types.resource.StreamBehaviorHints(false),
                    deepLinks = com.stremio.core.types.resource.StreamDeepLinks(
                        player = "",
                        externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink(),
                    ),
                ),
                streamRequest = request,
                metaRequest = null,
                addonTitle = "DEBUG raw torrent",
            ),
            origin = "DEBUG validation fixture",
            sourceKind = StreamSourceKind.Torrent,
        )
    }

    fun mustUseCoreResolver(option: StreamOption): Boolean = option.semanticKey == RAW_TORRENT_KEY
}
