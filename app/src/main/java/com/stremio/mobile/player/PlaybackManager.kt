package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val activeUri: String? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
)

class PlaybackManager(
    private val context: Context,
    private val playerFactory: (Context, PlayerEngine, com.stremio.core.types.profile.Profile.Settings?) -> Player = PlayerFactory::create,
) {
    private val mutableState = MutableStateFlow(PlaybackState())
    private var player: Player? = null
    private var listenerOwnerAttemptId: String? = null
    var actualEngine: PlayerEngine? = null
        private set
    private var playbackEventListener: ((PlayerPlaybackEvent) -> Unit)? = null

    val state: StateFlow<PlaybackState> = mutableState

    fun setPlaybackEventListener(listener: ((PlayerPlaybackEvent) -> Unit)?) {
        playbackEventListener = listener
    }

    fun load(
        uri: Uri,
        title: String? = null,
        startPositionMs: Long = 0,
        subtitles: List<ExternalSubtitle> = emptyList(),
        preferredSubtitleLang: String? = null,
        engine: PlayerEngine = PlayerEngine.EXO,
        settings: com.stremio.core.types.profile.Profile.Settings? = null,
        reuseExoPlayer: Boolean = false,
        attemptId: String? = null,
        mediaId: String? = null,
        eventListener: ((PlayerPlaybackEvent) -> Unit)? = playbackEventListener,
    ) {
        val requestedConfiguration = ExoConstructionKey.from(settings)
        val reusable = selectReusablePlayer(player, engine, requestedConfiguration, reuseExoPlayer)

        if (reusable == null) {
            if (reuseExoPlayer) {
                val reason = reuseRejectionReason(player, engine, requestedConfiguration)
                playbackReuseLog("reuse-check rejected attempt=${attemptId ?: "unknown"} instance=${(player as? ReusableExoPlayer)?.instanceId ?: "none"} reason=$reason")
            }
            player?.setPlaybackEventListener(null)
            player?.release()
            player = null
            actualEngine = null
        }
        val fallbackMessage = if (engine == PlayerEngine.MPV) "MPV unavailable; using ExoPlayer." else null
        if (reusable != null) {
            playbackReuseLog("reuse-check accepted attempt=${attemptId ?: "unknown"} instance=${reusable.instanceId} generation=${reusable.itemGeneration} media=${mediaId ?: "unknown"}")
            reusable.detachOutput()
            transferListenerOwner(reusable, eventListener, attemptId)
            reusable.setPlaybackAttemptId(attemptId)
            reusable.setPlaybackMediaId(mediaId)
            playbackReuseLog("load-start attempt=${attemptId ?: "unknown"} instance=${reusable.instanceId} generation=${reusable.itemGeneration + 1} media=${mediaId ?: "unknown"} reuse=true")
            reusable.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
            reusable.play()
            player = reusable
        } else {
            playbackEventListener = eventListener
            listenerOwnerAttemptId = attemptId
            playbackReuseLog("load-start attempt=${attemptId ?: "unknown"} engine=${engine.name} reuse=false media=${mediaId ?: "unknown"}")
            player = runCatching {
                playerFactory(context, engine, settings).also {
                    it.setPlaybackAttemptId(attemptId)
                    it.setPlaybackMediaId(mediaId)
                    it.setPlaybackEventListener(playbackEventListener)
                    it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                    it.play()
                }
            }.getOrElse { failure ->
                if (engine != PlayerEngine.MPV) throw failure
                ExoStreamPlayer(context, settings).also {
                    it.setPlaybackAttemptId(attemptId)
                    it.setPlaybackMediaId(mediaId)
                    it.setPlaybackEventListener(playbackEventListener)
                    it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                    it.play()
                    it.reportNonFatalError(fallbackMessage)
                }
            }
        }
        actualEngine = player?.engine
        mutableState.value = PlaybackState(activeUri = uri.toString(), title = title, isPlaying = true)
    }

    fun attachView(view: android.view.View) = Unit

    fun detachView() = Unit

    fun detachOutput() = player?.detachOutput()

    private fun transferListenerOwner(
        reusable: ReusableExoPlayer,
        listener: ((PlayerPlaybackEvent) -> Unit)?,
        attemptId: String?,
    ) {
        val previousOwner = listenerOwnerAttemptId ?: "unknown"
        val newOwner = attemptId ?: "unknown"
        playbackReuseLog("listener-owner attempt=$previousOwner -> attempt=$newOwner instance=${reusable.instanceId} generation=${reusable.itemGeneration + 1}")
        playbackEventListener = listener
        listenerOwnerAttemptId = attemptId
        reusable.setPlaybackEventListener(listener)
    }

    fun play() {
        player?.play()
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    fun pause() {
        player?.pause()
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) {
        player?.addExternalSubtitleTracks(tracks)
    }

    fun addLocalSubtitle(track: ExternalSubtitle) {
        player?.addLocalSubtitle(track)
    }

    fun release() {
        (player as? ReusableExoPlayer)?.let {
            playbackReuseLog("release attempt=${listenerOwnerAttemptId ?: "unknown"} instance=${it.instanceId} generation=${it.itemGeneration}")
        }
        player?.setPlaybackEventListener(null)
        player?.release()
        player = null
        actualEngine = null
        listenerOwnerAttemptId = null
        playbackEventListener = null
        mutableState.value = PlaybackState()
    }

    fun getPlayer(): Player? = player
}

data class ExoConstructionKey(
    val hardwareDecoding: Boolean,
    val audioPassthrough: Boolean,
    val surroundSound: Boolean,
) {
    companion object {
        fun from(settings: com.stremio.core.types.profile.Profile.Settings?): ExoConstructionKey = ExoConstructionKey(
            hardwareDecoding = settings?.hardwareDecoding ?: true,
            audioPassthrough = settings?.audioPassthrough ?: false,
            surroundSound = settings?.surroundSound ?: false,
        )
    }
}

internal fun findReusableExoPlayer(
    player: Player?,
    requestedEngine: PlayerEngine,
    requestedConfiguration: ExoConstructionKey,
): ReusableExoPlayer? = (player as? ReusableExoPlayer)
    ?.takeIf { requestedEngine == PlayerEngine.EXO && it.engine == PlayerEngine.EXO && it.constructionKey == requestedConfiguration }

internal fun selectReusablePlayer(
    player: Player?,
    requestedEngine: PlayerEngine,
    requestedConfiguration: ExoConstructionKey,
    reuseOptIn: Boolean,
): ReusableExoPlayer? = if (reuseOptIn) {
    findReusableExoPlayer(player, requestedEngine, requestedConfiguration)
} else {
    null
}

internal fun reuseRejectionReason(
    player: Player?,
    requestedEngine: PlayerEngine,
    requestedConfiguration: ExoConstructionKey,
): String = when {
    player == null -> "no-current-player"
    requestedEngine != PlayerEngine.EXO -> "requested-engine-${requestedEngine.name.lowercase()}"
    player.engine != PlayerEngine.EXO -> "current-engine-${player.engine.name.lowercase()}"
    player !is ReusableExoPlayer -> "current-player-not-reusable-exo"
    player.constructionKey.hardwareDecoding != requestedConfiguration.hardwareDecoding -> "hardware-decoding-changed"
    player.constructionKey.audioPassthrough != requestedConfiguration.audioPassthrough -> "audio-passthrough-changed"
    player.constructionKey.surroundSound != requestedConfiguration.surroundSound -> "surround-sound-changed"
    else -> "reuse-not-requested"
}

internal fun playbackReuseLog(message: String) {
    if (com.stremio.mobile.BuildConfig.DEBUG) android.util.Log.d("PlaybackReuse", message)
}
