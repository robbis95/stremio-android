package com.stremio.mobile.core

import com.stremio.core.models.LoadableConvertedStream
import com.stremio.core.models.Player
import com.stremio.core.types.resource.Stream
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import java.net.URI

/** Safe categories used across Core resolution and playback startup. */
enum class PlaybackResolutionFailure {
    CoreConversionError,
    CoreResolutionTimeout,
    NoPlayableSource,
    StreamingServerStartFailed,
    PlayerLoadError,
}

class PlaybackResolutionException(
    val category: PlaybackResolutionFailure,
) : Exception(category.name)

data class ResolvedPlayableSource(
    val playableUri: String,
    val resolutionKind: String,
    val convertedSourceKind: String,
    val usedCoreConversion: Boolean,
    val usedStreamingServer: Boolean,
)

internal sealed interface MatchingPlayerStream {
    data object Stale : MatchingPlayerStream
    data object Loading : MatchingPlayerStream
    data object Error : MatchingPlayerStream
    data class Ready(val stream: Stream) : MatchingPlayerStream
}

/** Match all request identity and content fields before considering shared Player.stream state. */
internal fun matchingPlayerStream(player: Player, expected: Player.Selected): MatchingPlayerStream {
    val selected = player.selected
    if (selected == null || !sameResolutionStream(selected.stream, expected.stream) ||
        selected.streamRequest != expected.streamRequest || selected.metaRequest != expected.metaRequest
    ) return MatchingPlayerStream.Stale

    return when (val content = player.stream?.content) {
        is LoadableConvertedStream.Content.Ready -> MatchingPlayerStream.Ready(content.value)
        is LoadableConvertedStream.Content.Error -> MatchingPlayerStream.Error
        is LoadableConvertedStream.Content.Loading, null -> MatchingPlayerStream.Loading
    }
}

/** `deepLinks` are derived presentation links; compare the stream's source and payload fields. */
internal fun sameResolutionStream(actual: Stream, expected: Stream): Boolean =
    actual.source == expected.source &&
        actual.name == expected.name &&
        actual.description == expected.description &&
        actual.thumbnail == expected.thumbnail &&
        actual.subtitles == expected.subtitles &&
        actual.behaviorHints == expected.behaviorHints

internal data class PlayerSelectionMatchDiagnostics(
    val selectedPresent: Boolean,
    val streamMatches: Boolean,
    val streamRequestMatches: Boolean,
    val metaRequestMatches: Boolean,
    val convertedState: String,
) {
    val matches: Boolean get() = selectedPresent && streamMatches && streamRequestMatches && metaRequestMatches
}

internal fun playerSelectionMatchDiagnostics(player: Player, expected: Player.Selected): PlayerSelectionMatchDiagnostics {
    val selected = player.selected
    val convertedState = when (player.stream?.content) {
        is LoadableConvertedStream.Content.Ready -> "Ready"
        is LoadableConvertedStream.Content.Loading -> "Loading"
        is LoadableConvertedStream.Content.Error -> "Error"
        null -> "Missing"
    }
    return PlayerSelectionMatchDiagnostics(
        selectedPresent = selected != null,
        streamMatches = selected?.stream?.let { sameResolutionStream(it, expected.stream) } == true,
        streamRequestMatches = selected?.streamRequest == expected.streamRequest,
        metaRequestMatches = selected?.metaRequest == expected.metaRequest,
        convertedState = convertedState,
    )
}

/** Raw fallback is intentionally disabled: only Core's converted Ready stream is authoritative. */
internal fun mayUseRawStreamFallback(stream: Stream): Boolean = false

/** The bridge serializes Core's converted streaming endpoint in the converted stream deep link. */
internal fun convertedPlayableUri(stream: Stream): String? =
    stream.deepLinks.externalPlayer.streaming?.takeIf(String::isNotBlank)

internal fun resolvedCoreSource(matching: MatchingPlayerStream, requested: Stream): ResolvedPlayableSource? = when (matching) {
    MatchingPlayerStream.Stale, MatchingPlayerStream.Loading -> null
    MatchingPlayerStream.Error -> throw PlaybackResolutionException(PlaybackResolutionFailure.CoreConversionError)
    is MatchingPlayerStream.Ready -> {
        if (requested.source == null) {
            throw PlaybackResolutionException(PlaybackResolutionFailure.NoPlayableSource)
        }
        val uri = convertedPlayableUri(matching.stream)
            ?: throw PlaybackResolutionException(PlaybackResolutionFailure.NoPlayableSource)
        ResolvedPlayableSource(
            playableUri = uri,
            resolutionKind = "core-converted",
            convertedSourceKind = convertedSourceKind(matching.stream),
            usedCoreConversion = true,
            usedStreamingServer = streamRequiresLocalServer(requested) ||
                uri.startsWith(StremioCore.STREAMING_SERVER_BASE),
        )
    }
}

internal fun coreResolutionTimeoutFailure() =
    PlaybackResolutionException(PlaybackResolutionFailure.CoreResolutionTimeout)

/** Observe changes before dispatch, then read current state to cover synchronous Core updates. */
internal fun <T> raceSafeDispatchObservations(
    subscribe: (onChange: () -> Unit) -> AutoCloseable,
    dispatch: () -> Unit,
    readCurrent: () -> T,
): Flow<T> = callbackFlow {
    val subscription = subscribe { trySend(Unit) }
    dispatch()
    trySend(Unit)
    awaitClose { subscription.close() }
}.map { readCurrent() }

/** Identify Core conversions that need the local server before Player.Load is dispatched. */
internal fun streamRequiresLocalServer(stream: Stream): Boolean {
    return when (val source = stream.source) {
        is Stream.Source.Tramvai,
        is Stream.Source.YouTube,
        is Stream.Source.Rar,
        is Stream.Source.Zip,
        is Stream.Source.Zip7,
        is Stream.Source.Tgz,
        is Stream.Source.Tar,
        is Stream.Source.Nzb -> true
        is Stream.Source.PlayerFrame,
        is Stream.Source.External -> false
        is Stream.Source.Url -> {
            val url = source.value.url
            stream.behaviorHints.proxyHeaders != null ||
                runCatching { URI(url).scheme?.lowercase() }.getOrNull() in setOf("ftp", "ftps")
        }
        null -> false
    }
}

internal fun convertedSourceKind(stream: Stream): String = when (stream.source) {
    is Stream.Source.Url -> "Url"
    is Stream.Source.Tramvai -> "Torrent"
    is Stream.Source.YouTube -> "YouTube"
    is Stream.Source.External -> "External"
    is Stream.Source.PlayerFrame -> "PlayerFrame"
    is Stream.Source.Rar, is Stream.Source.Zip, is Stream.Source.Zip7,
    is Stream.Source.Tgz, is Stream.Source.Tar -> "Archive"
    is Stream.Source.Nzb -> "NZB"
    null -> "Unknown"
}
