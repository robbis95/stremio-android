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
    ) {
        val requestedConfiguration = ExoConstructionKey.from(settings)
        val reusable = selectReusablePlayer(player, engine, requestedConfiguration, reuseExoPlayer)

        if (reusable == null) {
            player?.setPlaybackEventListener(null)
            player?.release()
            player = null
            actualEngine = null
        }
        val fallbackMessage = if (engine == PlayerEngine.MPV) "MPV unavailable; using ExoPlayer." else null
        if (reusable != null) {
            playbackReuseLog("switch-requested engine=EXO reuse=true instance=${reusable.instanceId}")
            reusable.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
            // Keep the previous attempt's listener authoritative throughout Core resolution.
            // Exo load advances its item generation synchronously; publish the new owner now.
            reusable.setPlaybackEventListener(playbackEventListener)
            reusable.play()
            player = reusable
        } else {
            playbackReuseLog("switch-requested engine=${engine.name} reuse=false")
            player = runCatching {
                playerFactory(context, engine, settings).also {
                    it.setPlaybackEventListener(playbackEventListener)
                    it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                    it.play()
                }
            }.getOrElse { failure ->
                if (engine != PlayerEngine.MPV) throw failure
                ExoStreamPlayer(context, settings).also {
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
        player?.setPlaybackEventListener(null)
        player?.release()
        player = null
        actualEngine = null
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

internal fun playbackReuseLog(message: String) {
    if (com.stremio.mobile.BuildConfig.DEBUG) android.util.Log.d("PlaybackReuse", message)
}
