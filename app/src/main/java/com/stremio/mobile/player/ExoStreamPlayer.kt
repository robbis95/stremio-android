package com.stremio.mobile.player

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.decoder.DecoderException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ExoStreamPlayer(
    context: Context,
    private val settings: com.stremio.core.types.profile.Profile.Settings? = null
) : ReusableExoPlayer {
    override val engine: PlayerEngine = PlayerEngine.EXO
    override val constructionKey: ExoConstructionKey = ExoConstructionKey.from(settings)
    override val instanceId: Long = nextInstanceId.incrementAndGet()

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableRuntimeState = MutableStateFlow(PlayerRuntimeState())

    override val runtimeState: StateFlow<PlayerRuntimeState> = mutableRuntimeState

    private val exoPlayer = run {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(dataSourceFactory)

        val hardwareDecoding = settings?.hardwareDecoding ?: true
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                val audioPassthrough = settings?.audioPassthrough ?: false
                val surroundSound = settings?.surroundSound ?: false
                val audioCapabilities = if (!audioPassthrough || !surroundSound) {
                    val encodings = if (audioPassthrough) {
                        intArrayOf(
                            android.media.AudioFormat.ENCODING_PCM_16BIT,
                            android.media.AudioFormat.ENCODING_AC3,
                            android.media.AudioFormat.ENCODING_E_AC3,
                            android.media.AudioFormat.ENCODING_DTS,
                            android.media.AudioFormat.ENCODING_DTS_HD
                        )
                    } else {
                        intArrayOf(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    }
                    val maxChannels = if (surroundSound) 8 else 2
                    androidx.media3.exoplayer.audio.AudioCapabilities(encodings, maxChannels)
                } else {
                    androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context)
                }
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(audioCapabilities)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }.setEnableDecoderFallback(true)

        if (!hardwareDecoding) {
            renderersFactory.setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                    .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                decoders.filter { info ->
                    val name = info.name.lowercase()
                    name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw.") || name.endsWith(".sw")
                }
            }
        }

        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .build()
    }

    private var playerView: PlayerView? = null
    private var currentSubtitleStyle = PlayerSubtitleStyle()
    private val firstVisualSignal = FirstVisualSignalGate()
    private var playbackEventListener: ((PlayerPlaybackEvent) -> Unit)? = null

    private val itemState = ExoItemState()
    private val itemLoadGeneration = ItemLoadGeneration()
    private var currentGeneration = 0L
    private var listener: androidx.media3.common.Player.Listener = createListener(currentGeneration)

    init {
        playbackReuseLog("Exo created instance=$instanceId")
        exoPlayer.addListener(listener)
        scope.launch {
            while (isActive) {
                publishState()
                delay(500)
            }
        }
    }

    override fun createView(context: Context): View {
        // A keyed Compose AndroidView can be replaced while this player survives. Detach
        // the old output before attaching the new PlayerView so only one surface is active.
        val resizeMode = playerView?.resizeMode ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
        playerView?.player = null
        return PlayerView(context).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            keepScreenOn = true
            this.resizeMode = resizeMode
            player = exoPlayer
            playerView = this
            applySubtitleStyleToView()
        }
    }

    override fun setPlaybackEventListener(listener: ((PlayerPlaybackEvent) -> Unit)?) {
        playbackEventListener = listener
    }

    override fun load(
        uri: Uri,
        startPositionMs: Long,
        subtitles: List<ExternalSubtitle>,
        preferredSubtitleLang: String?,
        settings: com.stremio.core.types.profile.Profile.Settings?,
    ) {
        val previousListener = listener
        val generation = itemLoadGeneration.begin()
        currentGeneration = generation
        exoPlayer.removeListener(previousListener)
        listener = createListener(generation)
        exoPlayer.addListener(listener)
        resetItemState()
        firstVisualSignal.reset()
        itemState.uri = uri
        itemState.startPositionMs = startPositionMs
        itemState.subtitles = subtitles
        itemState.preferredSubtitleLang = preferredSubtitleLang

        playbackReuseLog("Exo load instance=$instanceId generation=$generation item=item-$generation")
        val mediaItem = buildMediaItem(uri, subtitles, preferredSubtitleLang)
        exoPlayer.setMediaItem(mediaItem, startPositionMs)
        exoPlayer.prepare()
        publishState(error = null, ended = false)
    }

    override fun retry() {
        firstVisualSignal.reset()
        mutableRuntimeState.value = mutableRuntimeState.value.copy(error = null, ended = false)
        itemState.uri?.let { uri ->
            val resumePosition = exoPlayer.currentPosition.coerceAtLeast(itemState.startPositionMs)
            exoPlayer.setMediaItem(buildMediaItem(uri, itemState.subtitles, itemState.preferredSubtitleLang), resumePosition)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun reportNonFatalError(message: String?) {
        if (message == null) return
        publishState(error = message, ended = false)
    }

    override fun play() {
        exoPlayer.play()
        publishState()
    }

    override fun pause() {
        exoPlayer.pause()
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        publishState()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        publishState()
    }

    override fun setResizeMode(mode: PlayerResizeMode) {
        playerView?.resizeMode = when (mode) {
            PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResizeMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    override fun selectAudioTrack(id: String) {
        val parsed = ExoTrackId.parse(id) ?: return
        if (parsed.type != PlayerTrackType.AUDIO) return
        selectTrack(parsed, C.TRACK_TYPE_AUDIO)
    }

    override fun selectSubtitleTrack(id: String) {
        val parsed = ExoTrackId.parse(id) ?: return
        if (parsed.type != PlayerTrackType.SUBTITLE) return
        enableTextTracks()
        selectTrack(parsed, C.TRACK_TYPE_TEXT)
    }

    override fun disableSubtitles() {
        val disabledTypes = exoPlayer.trackSelectionParameters.disabledTrackTypes.toMutableSet()
        disabledTypes.add(C.TRACK_TYPE_TEXT)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setDisabledTrackTypes(disabledTypes)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        publishState()
    }

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {
        currentSubtitleStyle = style
        applySubtitleStyleToView()
    }

    override fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) {
        val unique = (itemState.subtitles + tracks)
            .distinctBy { it.id }
        if (unique.size == itemState.subtitles.size) return
        itemState.subtitles = unique
        rebuildMediaItemPreservingPlayback()
    }

    override fun addLocalSubtitle(track: ExternalSubtitle) {
        itemState.preferredSubtitleLang = LanguageCatalog.LOCAL_SUBTITLES_LANGUAGE
        addExternalSubtitleTracks(
            listOf(
                track.copy(
                    lang = track.lang.ifBlank { LanguageCatalog.LOCAL_SUBTITLES_LANGUAGE },
                    origin = "LOCAL",
                    embedded = false,
                    local = true,
                )
            )
        )
        enableTextTracks()
    }

    override fun release() {
        scope.cancel()
        playerView?.player = null
        playerView = null
        exoPlayer.removeListener(listener)
        playbackReuseLog("Exo released instance=$instanceId generation=$currentGeneration")
        exoPlayer.release()
        mutableRuntimeState.value = PlayerRuntimeState()
    }

    private fun resetItemState() {
        val speed = exoPlayer.playbackParameters.speed
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setDisabledTrackTypes(emptySet())
            .build()
        exoPlayer.setPlaybackSpeed(speed)
        itemState.reset()
        mutableRuntimeState.value = PlayerRuntimeState(speed = speed)
    }

    private fun createListener(generation: Long) = object : androidx.media3.common.Player.Listener {
        private fun isCurrentLoad() = itemLoadGeneration.isCurrent(generation)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isCurrentLoad()) publishState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isCurrentLoad()) publishState(ended = playbackState == androidx.media3.common.Player.STATE_ENDED)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isCurrentLoad()) return
            publishState(error = error.message ?: "Playback failed")
            playbackEventListener?.invoke(PlayerPlaybackEvent.PlaybackError(classifyPlaybackFailure(error)))
        }

        override fun onRenderedFirstFrame() {
            if (!isCurrentLoad() || !firstVisualSignal.tryEmit()) return
            playbackReuseLog("Exo first-visual instance=$instanceId generation=$generation item=item-$generation")
            playbackEventListener?.invoke(PlayerPlaybackEvent.FirstVisualFrame("ExoRenderedFirstFrame"))
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (isCurrentLoad()) publishState()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            if (isCurrentLoad()) publishState()
        }
    }

    private fun applySubtitleStyleToView() {
        playerView?.subtitleView?.let { subView ->
            val style = currentSubtitleStyle
            subView.setApplyEmbeddedFontSizes(false)
            subView.setApplyEmbeddedStyles(false)
            subView.setFractionalTextSize(0.0533f * (style.sizePercent / 100f))

            val textColor = parseSubtitleColor(style.textColor, Color.WHITE)
            val backgroundColor = parseSubtitleColor(style.backgroundColor, Color.TRANSPARENT)
            val outlineColor = parseSubtitleColor(style.outlineColor, Color.BLACK)
            subView.setStyle(
                CaptionStyleCompat(
                    textColor,
                    backgroundColor,
                    Color.TRANSPARENT,
                    if (Color.alpha(outlineColor) == 0) {
                        CaptionStyleCompat.EDGE_TYPE_NONE
                    } else {
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE
                    },
                    outlineColor,
                    null,
                )
            )

            val maxPaddingPx = with(subView.resources.displayMetrics) { 150 * density }
            val bottomPadding = ((style.offsetPercent / 100f) * maxPaddingPx).toInt()
            subView.setPadding(subView.paddingLeft, subView.paddingTop, subView.paddingRight, bottomPadding)
        }
    }

    private fun rebuildMediaItemPreservingPlayback() {
        val uri = itemState.uri ?: return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady
        val speed = exoPlayer.playbackParameters.speed
        exoPlayer.setMediaItem(buildMediaItem(uri, itemState.subtitles, itemState.preferredSubtitleLang), position)
        exoPlayer.prepare()
        exoPlayer.setPlaybackSpeed(speed)
        if (wasPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        publishState(error = null, ended = false)
    }

    private fun buildMediaItem(
        uri: Uri,
        subtitles: List<ExternalSubtitle>,
        preferredSubtitleLang: String?,
    ): MediaItem {
        val subtitleConfigs = subtitles.map { sub ->
            val isDefault = preferredSubtitleLang != null &&
                LanguageCatalog.matches(sub.lang, preferredSubtitleLang)
            val baseLabel = sub.label ?: sub.lang
            val label = if (sub.source != null) "$baseLabel (${sub.source})" else baseLabel
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setId(sub.id)
                .setMimeType(inferSubtitleMime(sub.url))
                .setLanguage(sub.lang)
                .setLabel(label)
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    private fun selectTrack(parsed: ExoTrackId, media3Type: Int) {
        val group = exoPlayer.currentTracks.groups.getOrNull(parsed.groupIndex) ?: return
        if (group.type != media3Type || parsed.trackIndex !in 0 until group.length) return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parsed.trackIndex))
            .build()
        publishState()
    }

    private fun enableTextTracks() {
        val disabledTypes = exoPlayer.trackSelectionParameters.disabledTrackTypes.toMutableSet()
        disabledTypes.remove(C.TRACK_TYPE_TEXT)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setDisabledTrackTypes(disabledTypes)
            .build()
    }

    private fun publishState(
        error: String? = mutableRuntimeState.value.error,
        ended: Boolean = exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED,
    ) {
        val videoFormat = exoPlayer.videoFormat
        val videoWidth = videoFormat?.width ?: 0
        val videoHeight = videoFormat?.height ?: 0
        val videoFrameRate = videoFormat?.frameRate ?: 0f

        mutableRuntimeState.value = PlayerRuntimeState(
            isPlaying = exoPlayer.isPlaying,
            isBuffering = exoPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = if (exoPlayer.duration == C.TIME_UNSET) 0L else exoPlayer.duration.coerceAtLeast(0L),
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
            speed = exoPlayer.playbackParameters.speed,
            audioTracks = getTrackOptions(C.TRACK_TYPE_AUDIO),
            subtitleTracks = getTrackOptions(C.TRACK_TYPE_TEXT),
            subtitlesDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT),
            error = error,
            ended = ended,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            videoFrameRate = videoFrameRate,
        )
    }

    private fun getTrackOptions(trackType: Int): List<PlayerTrackOption> {
        val options = mutableListOf<PlayerTrackOption>()
        for ((groupIndex, group) in exoPlayer.currentTracks.groups.withIndex()) {
            if (group.type != trackType) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val externalSubtitle = if (trackType == C.TRACK_TYPE_TEXT) {
                    findExternalSubtitle(format.id, group.mediaTrackGroup.id, format.label, format.language)
                } else {
                    null
                }
                val lang = LanguageCatalog.toCode(format.language)
                    ?: LanguageCatalog.toCode(externalSubtitle?.lang)
                val label = externalSubtitle?.let { buildExternalSubtitleLabel(it) }
                    ?: format.label
                    ?: lang?.uppercase(Locale.ROOT)
                    ?: if (trackType == C.TRACK_TYPE_AUDIO) {
                        "Track ${options.size + 1}"
                    } else {
                        "Subtitles ${options.size + 1}"
                    }
                val optionType = if (trackType == C.TRACK_TYPE_AUDIO) PlayerTrackType.AUDIO else PlayerTrackType.SUBTITLE
                options.add(
                    PlayerTrackOption(
                        id = ExoTrackId(
                            type = optionType,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                        ).encode(),
                        type = optionType,
                        label = label,
                        language = format.language ?: externalSubtitle?.lang,
                        selected = group.isTrackSelected(trackIndex),
                        languageCode = lang,
                        origin = externalSubtitle?.origin ?: if (trackType == C.TRACK_TYPE_TEXT) "EMBEDDED" else "AUDIO",
                        url = externalSubtitle?.url,
                        fallbackUrl = externalSubtitle?.fallbackUrl,
                        addonSubtitleId = externalSubtitle?.addonSubtitleId,
                        embedded = trackType == C.TRACK_TYPE_TEXT && externalSubtitle == null,
                        local = externalSubtitle?.local == true,
                        exclusive = externalSubtitle?.exclusive == true,
                    )
                )
            }
        }
        return options
    }

    private fun findExternalSubtitle(
        formatId: String?,
        groupId: String?,
        label: String?,
        language: String?,
    ): ExternalSubtitle? {
        return itemState.subtitles.firstOrNull { subtitle ->
            subtitle.id == formatId || subtitle.id == groupId
        } ?: itemState.subtitles.firstOrNull { subtitle ->
            subtitle.label != null &&
                subtitle.label == label &&
                LanguageCatalog.matches(subtitle.lang, language)
        }
    }

    private fun buildExternalSubtitleLabel(subtitle: ExternalSubtitle): String {
        val base = subtitle.label?.takeIf { it.isNotBlank() } ?: subtitle.lang.uppercase(Locale.ROOT)
        return if (subtitle.source != null) "$base (${subtitle.source})" else base
    }
}

private val nextInstanceId = java.util.concurrent.atomic.AtomicLong()

internal fun classifyPlaybackFailure(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    return when {
        causes.any { it is UnknownHostException } -> "DNS"
        causes.any { it is SocketTimeoutException } -> "ConnectTimeout"
        causes.any { it is ConnectException } -> "Connect"
        causes.any { it is SSLException } -> "TLS"
        causes.any { it is HttpDataSource.InvalidResponseCodeException } -> "HTTPResponseCode"
        causes.any { it is androidx.media3.exoplayer.source.UnrecognizedInputFormatException } -> "UnsupportedContainer"
        causes.any { it is DecoderException } -> "Decoder"
        else -> "Other"
    }
}

/** Addon subtitle URLs rarely carry a useful Content-Type; guess from the file extension. */
private fun inferSubtitleMime(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        path.endsWith(".ass", ignoreCase = true) || path.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

private fun parseSubtitleColor(value: String?, fallback: Int): Int {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return fallback
    return runCatching {
        when {
            raw.length == 9 && raw.startsWith("#") -> {
                val alpha = raw.substring(1, 3)
                val rgb = raw.substring(3)
                Color.parseColor("#$alpha$rgb")
            }
            raw.length == 7 && raw.startsWith("#") -> Color.parseColor(raw)
            else -> Color.parseColor(raw)
        }
    }.getOrDefault(fallback)
}
