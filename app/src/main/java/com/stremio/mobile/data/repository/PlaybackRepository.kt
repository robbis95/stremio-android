package com.stremio.mobile.data.repository

import android.net.Uri
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.core.PlaybackResolutionException
import com.stremio.mobile.core.PlaybackResolutionFailure
import com.stremio.mobile.core.ResolvedPlayableSource
import com.stremio.mobile.core.coreResolutionTimeoutFailure
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.TvSkipSegments
import com.stremio.mobile.data.model.normalizeTvSkipSegments
import com.stremio.mobile.player.ExternalSubtitle
import com.stremio.mobile.player.LanguageCatalog
import com.stremio.mobile.player.PlaybackManager
import com.stremio.mobile.player.PlaybackState
import com.stremio.mobile.player.Player
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.player.PlayerSubtitleStyle
import com.stremio.mobile.player.PlayerTrackOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class PlaybackRepository(
    private val core: StremioCore,
    private val playbackManager: PlaybackManager
) {
    enum class PlaybackLoadStage { ResolutionStarted, PlayableSourceResolved, PlayerLoadStarted, PlayerLoadReturned }
    data class PlaybackLoadEvent(
        val stage: PlaybackLoadStage,
        val monotonicNanos: Long,
        val source: ResolvedPlayableSource? = null,
    )
    val state: StateFlow<PlaybackState> get() = playbackManager.state

    fun getPlayer(): Player? = playbackManager.getPlayer()

    fun actualEngine(): PlayerEngine? = playbackManager.actualEngine

    fun setPlaybackEventListener(listener: ((com.stremio.mobile.player.PlayerPlaybackEvent) -> Unit)?) =
        playbackManager.setPlaybackEventListener(listener)

    fun attachView(view: android.view.View) = playbackManager.attachView(view)

    fun detachView(view: android.view.View) = playbackManager.getPlayer()?.detachView(view)

    fun detachOutput() = playbackManager.detachOutput()

    fun detachView() = playbackManager.detachView()

    fun release() = playbackManager.release()

    suspend fun resolveAndLoadStream(
        option: StreamOption,
        engine: PlayerEngine = PlayerEngine.EXO,
        displayTitle: String? = null,
        attemptId: String? = null,
        mediaId: String? = null,
        reuseExoPlayer: Boolean = false,
        eventListener: ((com.stremio.mobile.player.PlayerPlaybackEvent) -> Unit)? = null,
        onEvent: ((PlaybackLoadEvent) -> Unit)? = null,
    ): Boolean {
        onEvent?.invoke(PlaybackLoadEvent(PlaybackLoadStage.ResolutionStarted, android.os.SystemClock.elapsedRealtimeNanos()))
        val resolvedSource = try {
            withTimeout(CORE_RESOLUTION_TIMEOUT_MS) { core.resolvePlayableUrl(option.core).first() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (cancelled is TimeoutCancellationException) {
                throw coreResolutionTimeoutFailure()
            }
            throw cancelled
        } catch (failure: PlaybackResolutionException) {
            throw failure
        } catch (_: Exception) {
            throw PlaybackResolutionException(PlaybackResolutionFailure.CoreConversionError)
        }
        val url = resolvedSource.playableUri

        if (url.isNullOrBlank()) {
            throw PlaybackResolutionException(PlaybackResolutionFailure.NoPlayableSource)
        }
        onEvent?.invoke(PlaybackLoadEvent(PlaybackLoadStage.PlayableSourceResolved, android.os.SystemClock.elapsedRealtimeNanos(), resolvedSource))

        val subtitles = option.core.stream.subtitles.map {
            ExternalSubtitle(
                id = it.id,
                lang = it.lang,
                url = it.url,
                label = it.name,
                source = option.core.addonTitle,
                origin = option.core.addonTitle,
                addonSubtitleId = it.id,
                embedded = false,
            )
        }
        val preferredLang = runCatching { core.getSubtitleSettings().language }.getOrNull()
        val startPositionMs = runCatching { core.getResumePositionMs(option.core.streamRequest) }.getOrDefault(0L)
        val settings = runCatching { core.getCtx().profile.settings }.getOrNull()

        onEvent?.invoke(PlaybackLoadEvent(PlaybackLoadStage.PlayerLoadStarted, android.os.SystemClock.elapsedRealtimeNanos()))
        // Keep the current Player listener through source resolution. PlaybackManager transfers
        // this pending listener to the new attempt immediately before its item load starts.
        if (eventListener != null) playbackManager.setPlaybackEventListener(eventListener)
        playbackManager.load(
            uri = Uri.parse(url),
            title = displayTitle ?: option.name,
            startPositionMs = startPositionMs,
            subtitles = subtitles,
            preferredSubtitleLang = preferredLang,
            engine = engine,
            settings = settings,
            reuseExoPlayer = reuseExoPlayer,
            attemptId = attemptId,
            mediaId = mediaId,
        )
        onEvent?.invoke(PlaybackLoadEvent(PlaybackLoadStage.PlayerLoadReturned, android.os.SystemClock.elapsedRealtimeNanos()))
        return true
    }

    fun playerFlow(): Flow<com.stremio.core.models.Player> = core.playerFlow()

    internal fun tvSkipSegments(player: com.stremio.core.models.Player, playbackDurationMs: Long): TvSkipSegments {
        val introOutro = player.introOutro
        return normalizeTvSkipSegments(
            introFromMs = introOutro?.intro?.from,
            introToMs = introOutro?.intro?.to,
            introSourceDurationMs = introOutro?.intro?.duration,
            outroStartMs = introOutro?.outro,
            playbackDurationMs = playbackDurationMs,
        )
    }

    fun extractAddonSubtitles(player: com.stremio.core.models.Player): List<ExternalSubtitle> {
        return player.subtitles.flatMap { loadable ->
            val subtitles = loadable.ready?.subtitles ?: return@flatMap emptyList()
            subtitles.map { subtitle ->
                ExternalSubtitle(
                    id = "${loadable.request.base}:${subtitle.id}",
                    lang = subtitle.lang,
                    url = subtitle.url,
                    label = subtitle.name,
                    source = loadable.title,
                    origin = loadable.title,
                    addonSubtitleId = subtitle.id,
                    embedded = false,
                )
            }
        }
    }

    fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) = playbackManager.addExternalSubtitleTracks(tracks)

    fun addLocalSubtitle(track: ExternalSubtitle) = playbackManager.addLocalSubtitle(track)

    fun getPlayerStreamState(): com.stremio.core.models.Player.StreamState? =
        runCatching { core.getPlayer().streamState }.getOrNull()

    fun rememberAudioTrack(track: PlayerTrackOption) {
        runCatching { core.setPlayerAudioTrack(track.id, track.languageCode ?: LanguageCatalog.toCode(track.language)) }
    }

    fun rememberSubtitleTrack(track: PlayerTrackOption) {
        runCatching {
            core.setPlayerSubtitleTrack(
                id = track.id,
                embedded = track.embedded,
                language = track.languageCode ?: LanguageCatalog.toCode(track.language),
            )
        }
    }

    fun rememberSubtitlesDisabled() {
        runCatching { core.clearPlayerSubtitleTrack() }
    }

    fun rememberSubtitleStyle(style: PlayerSubtitleStyle) {
        runCatching {
            core.setPlayerSubtitleSettings(
                delayMs = style.delayMs,
                sizePercent = style.sizePercent.toFloat(),
                offsetPercent = style.offsetPercent.toFloat(),
            )
        }
    }

    fun getNextVideo(): com.stremio.core.types.resource.Video? = runCatching { core.getNextVideo() }.getOrNull()

    fun reportTimeChanged(timeMs: Long, durationMs: Long) = runCatching { core.playerTimeChanged(timeMs, durationMs) }

    fun reportSeek(timeMs: Long, durationMs: Long) = runCatching { core.playerSeek(timeMs, durationMs) }

    fun reportPausedChanged(paused: Boolean) = runCatching { core.playerPausedChanged(paused) }

    fun reportEnded() = runCatching { core.playerEnded() }

    fun reportNextVideo() = runCatching { core.playerNextVideo() }

    fun requestStreamStatistics(infoHash: String, fileIndex: Int) = runCatching { core.requestStreamStatistics(infoHash, fileIndex) }

    fun getStreamStatistics(): com.stremio.core.models.StreamingServer.Statistics? = runCatching { core.getStreamStatistics() }.getOrNull()

    fun getSubtitlePrefs(): Pair<Int, Int> {
        val prefs = runCatching { core.getSubtitleSettings() }.getOrNull()
        return Pair(prefs?.sizePercent ?: 100, prefs?.offsetPercent ?: 0)
    }

    fun updateSubtitlePrefs(sizePercent: Int, offsetPercent: Int) {
        runCatching { core.updateSubtitleSettings(sizePercent = sizePercent, offsetPercent = offsetPercent) }
    }

    companion object {
        const val CORE_RESOLUTION_TIMEOUT_MS = 15_000L
    }
}
