package com.stremio.mobile.presentation.tv

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.activity.compose.BackHandler
import com.stremio.mobile.player.PlayerTrackOption
import com.stremio.mobile.presentation.tv.segments.TvContextualPlaybackAction
import com.stremio.mobile.presentation.tv.segments.TvSegmentType
import com.stremio.mobile.presentation.tv.segments.tvContextualPlaybackAction
import com.stremio.mobile.player.Player
import com.stremio.mobile.player.PlayerSubtitleStyle
import com.stremio.mobile.presentation.tv.theme.TvColors
import kotlinx.coroutines.delay

private enum class TvPlayerControl { Rewind, PlayPause, Forward, SkipIntro, SkipOutro, Audio, Subtitles }
private enum class TvTrackPanel { Audio, Subtitles }
private enum class TvSubtitleAppearanceItem { Size, Position, TextColor, BackgroundColor, OutlineColor }

private val tvSubtitleSizePresets = listOf(75, 85, 100, 115, 130, 150)
private val tvSubtitleOffsetPresets = listOf(0, 20, 40, 60, 80, 100)
private val tvSubtitleColors = listOf(
    "#FFFFFF" to "White",
    "#FFFF00" to "Yellow",
    "#00FFFF" to "Cyan",
    "#FF00FF" to "Magenta",
    "#00FF00" to "Green",
    "#FF0000" to "Red",
    "#000000" to "Black",
)
private val tvSubtitleBackgroundColors = listOf("#00000000" to "Transparent") + tvSubtitleColors

@Composable
internal fun TvPlayerScreen(
    state: TvPlaybackUiState,
    player: Player?,
    seekDurationMs: Long,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlayNext: () -> Unit = {},
    onDismissNext: () -> Unit = {},
    onAudioTrackSelected: (PlayerTrackOption) -> Unit,
    onSubtitleTrackSelected: (PlayerTrackOption) -> Unit,
    onSubtitlesDisabled: () -> Unit,
    subtitleStyle: PlayerSubtitleStyle = PlayerSubtitleStyle(),
    onSubtitleStyleChanged: (PlayerSubtitleStyle) -> Unit = {},
) {
    var controlsVisible by remember(state.playbackAttemptId) { mutableStateOf(true) }
    var controlsActivity by remember(state.playbackAttemptId) { mutableIntStateOf(0) }
    var lastFocusedControl by remember(state.playbackAttemptId) { mutableStateOf(TvPlayerControl.PlayPause) }
    var trackPanel by remember(state.playbackAttemptId) { mutableStateOf<TvTrackPanel?>(null) }
    var showSubtitleAppearance by remember(state.playbackAttemptId) { mutableStateOf(false) }
    val rewindRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val playPauseRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val forwardRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val skipIntroRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val skipOutroRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val retryRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val backRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val rootRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val audioRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val subtitlesRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val appearanceRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val nextPlayRequester = remember(state.playbackAttemptId) { FocusRequester() }
    var nextCardFocused by remember(state.playbackAttemptId) { mutableStateOf(false) }
    val dismissNext = {
        onDismissNext()
        nextCardFocused = false
        runCatching { rootRequester.requestFocus() }
        Unit
    }

    BackHandler(enabled = nextCardFocused && state.nextEpisode.transition != TvNextEpisodeTransition.Loading) {
        dismissNext()
    }

    BackHandler(enabled = trackPanel != null || showSubtitleAppearance) {
        if (showSubtitleAppearance) {
            showSubtitleAppearance = false
            runCatching { appearanceRequester.requestFocus() }
        } else {
            trackPanel = null
            controlsVisible = true
            runCatching {
                (if (lastFocusedControl == TvPlayerControl.Audio) audioRequester else subtitlesRequester).requestFocus()
            }
        }
    }

    fun closeTrackPanel(panel: TvTrackPanel) {
        showSubtitleAppearance = false
        trackPanel = null
        controlsVisible = true
        runCatching { (if (panel == TvTrackPanel.Audio) audioRequester else subtitlesRequester).requestFocus() }
    }

    LaunchedEffect(player, subtitleStyle) {
        player?.setSubtitleStyle(subtitleStyle)
    }

    LaunchedEffect(state.stage, state.runtime.isPlaying, state.isBuffering, controlsVisible, controlsActivity, trackPanel) {
        if (trackPanel != null) return@LaunchedEffect
        if (controlsVisible && state.stage == TvPlaybackStage.Playing && state.runtime.isPlaying && !state.isBuffering) {
            delay(5_000)
            controlsVisible = false
            runCatching { rootRequester.requestFocus() }
        }
    }
    LaunchedEffect(state.stage, state.firstVisualObserved, controlsVisible, state.playbackAttemptId) {
        when {
            state.stage == TvPlaybackStage.Error -> runCatching { retryRequester.requestFocus() }
            state.firstVisualObserved && controlsVisible -> requestControlFocus(
                lastFocusedControl,
                rewindRequester,
                playPauseRequester,
                forwardRequester,
                skipIntroRequester,
                skipOutroRequester,
                audioRequester,
                subtitlesRequester,
            )
            else -> runCatching { rootRequester.requestFocus() }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (state.stage == TvPlaybackStage.Error) return@onPreviewKeyEvent false
                if (nextCardFocused) return@onPreviewKeyEvent false
                if (state.nextEpisode.promptVisible && event.key == Key.DirectionDown) {
                    runCatching { nextPlayRequester.requestFocus() }
                    return@onPreviewKeyEvent true
                }
                if (controlsVisible && state.firstVisualObserved) {
                    controlsActivity++
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!controlsVisible && state.firstVisualObserved) onSeek(-seekDurationMs)
                        else controlsVisible = true
                        true
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible && state.firstVisualObserved) onSeek(seekDurationMs)
                        else controlsVisible = true
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        if (player != null && state.stage !in setOf(TvPlaybackStage.Resolving, TvPlaybackStage.Error, TvPlaybackStage.Idle)) {
            key(state.playbackAttemptId, player) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext -> player.createView(viewContext).apply { keepScreenOn = true } },
                    update = View::requestLayout,
                    onRelease = player::detachView,
                )
            }
        }

        when (state.stage) {
            TvPlaybackStage.Idle, TvPlaybackStage.Resolving, TvPlaybackStage.Preparing ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn(initialScale = 0.97f),
                    exit = fadeOut() + scaleOut(targetScale = 0.97f),
                ) { TvStartingOverlay(state) }
            TvPlaybackStage.Error ->
                AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.97f)) {
                    TvErrorOverlay(state, retryRequester, backRequester, onRetry, onBack)
                }
            TvPlaybackStage.Playing -> Unit
            TvPlaybackStage.Ended -> TvEndedOverlay(state, onPlayNext, onBack)
        }

        AnimatedVisibility(
            visible = state.firstVisualObserved && controlsVisible && state.stage !in setOf(TvPlaybackStage.Error, TvPlaybackStage.Ended),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(1f),
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut() + slideOutVertically { it / 12 },
        ) {
            TvPlayerControls(
                state = state,
                seekDurationMs = seekDurationMs,
                lastFocusedControl = lastFocusedControl,
                onFocused = { lastFocusedControl = it },
                rewindRequester = rewindRequester,
                playPauseRequester = playPauseRequester,
                forwardRequester = forwardRequester,
                skipIntroRequester = skipIntroRequester,
                skipOutroRequester = skipOutroRequester,
                audioRequester = audioRequester,
                subtitlesRequester = subtitlesRequester,
                trackPanel = trackPanel,
                onOpenTrackPanel = { panel, control ->
                    lastFocusedControl = control
                    trackPanel = panel
                    controlsVisible = true
                },
                onActivity = { controlsActivity++ },
                onTogglePlayback = onTogglePlayback,
                onSeek = onSeek,
                onSeekTo = onSeekTo,
            )
        }

        trackPanel?.let { panel ->
            val tracks = if (panel == TvTrackPanel.Audio) state.runtime.audioTracks else state.runtime.subtitleTracks
            TvTrackSelectionPanel(
                panel = panel,
                tracks = tracks,
                subtitlesDisabled = state.runtime.subtitlesDisabled,
                onDismiss = { closeTrackPanel(panel) },
                onSelectAudio = { track -> onAudioTrackSelected(track); closeTrackPanel(panel) },
                onSelectSubtitle = { track -> onSubtitleTrackSelected(track); closeTrackPanel(panel) },
                onDisableSubtitles = { onSubtitlesDisabled(); closeTrackPanel(panel) },
                onOpenAppearance = { showSubtitleAppearance = true },
                appearanceRequester = appearanceRequester,
            )
        }

        if (trackPanel == TvTrackPanel.Subtitles && showSubtitleAppearance) {
            TvSubtitleAppearancePanel(
                style = subtitleStyle,
                onStyleChanged = onSubtitleStyleChanged,
                onDismiss = { showSubtitleAppearance = false; runCatching { appearanceRequester.requestFocus() } },
            )
        }

        if (state.stage == TvPlaybackStage.Playing && (state.nextEpisode.promptVisible || state.nextEpisode.transition == TvNextEpisodeTransition.Loading)) {
            TvNextEpisodeCard(
                state = state.nextEpisode,
                playRequester = nextPlayRequester,
                onFocused = { nextCardFocused = it },
                onPlay = onPlayNext,
                onDismiss = dismissNext,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 48.dp).zIndex(2f),
            )
        }

        AnimatedVisibility(
            visible = state.firstVisualObserved && state.isBuffering,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TvBufferingIndicator()
        }
    }
}

@Composable
private fun TvPlayerControls(
    state: TvPlaybackUiState,
    seekDurationMs: Long,
    lastFocusedControl: TvPlayerControl,
    onFocused: (TvPlayerControl) -> Unit,
    rewindRequester: FocusRequester,
    playPauseRequester: FocusRequester,
    forwardRequester: FocusRequester,
    skipIntroRequester: FocusRequester,
    skipOutroRequester: FocusRequester,
    audioRequester: FocusRequester,
    subtitlesRequester: FocusRequester,
    trackPanel: TvTrackPanel?,
    onOpenTrackPanel: (TvTrackPanel, TvPlayerControl) -> Unit,
    onActivity: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val action = tvContextualPlaybackAction(
        segments = state.resolvedSegments,
        positionMs = state.runtime.positionMs,
        nextEpisodeActionable = state.nextEpisode.available && state.nextEpisode.promptVisible &&
            state.nextEpisode.transition == TvNextEpisodeTransition.Idle,
    )
    val skipSegment = (action as? TvContextualPlaybackAction.SkipSegment)?.segment
    val skipTarget = skipSegment?.let { segment ->
        when (segment.type) {
            TvSegmentType.Intro, TvSegmentType.Recap, TvSegmentType.Preview -> segment.endMs
            TvSegmentType.Credits -> state.runtime.durationMs
        }
    }
    val title = state.attempt?.target?.contentName.orEmpty().ifBlank { "Now playing" }
    val episodeLabel = state.attempt?.target?.episodeLabel
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f), Color.Black.copy(alpha = 0.9f))))
            .padding(start = 72.dp, end = 72.dp, top = 72.dp, bottom = 46.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (skipSegment != null && skipTarget != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (skipSegment.type == TvSegmentType.Intro || skipSegment.type == TvSegmentType.Recap || skipSegment.type == TvSegmentType.Preview) {
                    ContextSkipAction(
                        text = when (skipSegment.type) {
                            TvSegmentType.Intro -> "Skip Intro"
                            TvSegmentType.Recap -> "Skip Recap"
                            TvSegmentType.Preview -> "Skip Preview"
                            TvSegmentType.Credits -> "Skip Outro"
                        }, icon = Icons.Outlined.SkipNext,
                        requester = skipIntroRequester, focused = lastFocusedControl == TvPlayerControl.SkipIntro,
                        onFocus = { onFocused(TvPlayerControl.SkipIntro) }, onActivity = onActivity,
                        onClick = { onSeekTo(skipTarget) },
                    )
                } else {
                    ContextSkipAction(
                        text = "Skip Outro", icon = Icons.Outlined.SkipNext,
                        requester = skipOutroRequester, focused = lastFocusedControl == TvPlayerControl.SkipOutro,
                        onFocus = { onFocused(TvPlayerControl.SkipOutro) }, onActivity = onActivity,
                        onClick = { onSeekTo(skipTarget) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        Text(title, color = TvColors.onMedia, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!episodeLabel.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(episodeLabel, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Spacer(Modifier.height(20.dp))
        TvTimeline(
            positionMs = state.runtime.positionMs,
            durationMs = state.runtime.durationMs,
            bufferedPositionMs = state.runtime.bufferedPositionMs,
            modifier = Modifier.fillMaxWidth().height(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTvTime(state.runtime.positionMs), color = TvColors.onMedia, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("−${formatTvTime((state.runtime.durationMs - state.runtime.positionMs).coerceAtLeast(0L))}", color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            Text(formatTvTime(state.runtime.durationMs), color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TvTransportButton(
                icon = Icons.Outlined.FastRewind, description = "Rewind ${seekDurationMs / 1000} seconds",
                requester = rewindRequester, focused = lastFocusedControl == TvPlayerControl.Rewind,
                size = 66.dp, iconSize = 32.dp, onFocus = { onFocused(TvPlayerControl.Rewind) }, onActivity = onActivity,
                onClick = { onSeek(-seekDurationMs) },
            )
            Spacer(Modifier.width(38.dp))
            TvTransportButton(
                icon = if (state.runtime.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                description = if (state.runtime.isPlaying) "Pause" else "Play",
                requester = playPauseRequester, focused = lastFocusedControl == TvPlayerControl.PlayPause,
                size = 82.dp, iconSize = 42.dp, prominent = true,
                onFocus = { onFocused(TvPlayerControl.PlayPause) }, onActivity = onActivity, onClick = onTogglePlayback,
            )
            Spacer(Modifier.width(38.dp))
            TvTransportButton(
                icon = Icons.Outlined.FastForward, description = "Forward ${seekDurationMs / 1000} seconds",
                requester = forwardRequester, focused = lastFocusedControl == TvPlayerControl.Forward,
                size = 66.dp, iconSize = 32.dp, onFocus = { onFocused(TvPlayerControl.Forward) }, onActivity = onActivity,
                onClick = { onSeek(seekDurationMs) },
            )
        }
        if (state.runtime.audioTracks.isNotEmpty() || state.runtime.subtitleTracks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (state.runtime.audioTracks.isNotEmpty()) {
                    TvSecondaryAction(
                        text = "Audio", icon = Icons.Outlined.Audiotrack, requester = audioRequester,
                        focused = lastFocusedControl == TvPlayerControl.Audio && trackPanel == null,
                        selected = state.runtime.audioTracks.firstOrNull { it.selected }?.let(::trackSummary),
                        onFocus = { onFocused(TvPlayerControl.Audio) }, onActivity = onActivity,
                        onClick = { onOpenTrackPanel(TvTrackPanel.Audio, TvPlayerControl.Audio) },
                    )
                }
                if (state.runtime.audioTracks.isNotEmpty() && state.runtime.subtitleTracks.isNotEmpty()) Spacer(Modifier.width(12.dp))
                if (state.runtime.subtitleTracks.isNotEmpty()) {
                    TvSecondaryAction(
                        text = "Subtitles", icon = Icons.Outlined.Subtitles, requester = subtitlesRequester,
                        focused = lastFocusedControl == TvPlayerControl.Subtitles && trackPanel == null,
                        selected = if (state.runtime.subtitlesDisabled) "Off" else state.runtime.subtitleTracks.firstOrNull { it.selected }?.let(::trackSummary),
                        onFocus = { onFocused(TvPlayerControl.Subtitles) }, onActivity = onActivity,
                        onClick = { onOpenTrackPanel(TvTrackPanel.Subtitles, TvPlayerControl.Subtitles) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSecondaryAction(
    text: String,
    icon: ImageVector,
    requester: FocusRequester,
    focused: Boolean,
    selected: String?,
    onFocus: () -> Unit,
    onActivity: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.42f))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.36f), RoundedCornerShape(20.dp))
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter)) { onActivity(); onClick(); true } else false }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = TvColors.onMedia, modifier = Modifier.size(20.dp))
        Column {
            Text(text, color = TvColors.onMedia, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (!selected.isNullOrBlank()) Text(selected, color = TvColors.mediaSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

private data class TvTrackRow(val id: String, val title: String, val detail: String?, val selected: Boolean, val track: PlayerTrackOption? = null)

@Composable
private fun TvTrackSelectionPanel(
    panel: TvTrackPanel,
    tracks: List<PlayerTrackOption>,
    subtitlesDisabled: Boolean,
    onDismiss: () -> Unit,
    onSelectAudio: (PlayerTrackOption) -> Unit,
    onSelectSubtitle: (PlayerTrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onOpenAppearance: () -> Unit,
    appearanceRequester: FocusRequester,
) {
    val rows = buildList {
        if (panel == TvTrackPanel.Subtitles) add(TvTrackRow("off", "Off", "No subtitles", subtitlesDisabled))
        tracks.forEach { track ->
            val selected = track.selected && (panel != TvTrackPanel.Subtitles || !subtitlesDisabled)
            val origin = if (panel == TvTrackPanel.Subtitles) subtitleOriginLabel(track) else null
            val language = track.language?.takeIf { it.isNotBlank() && !track.label.contains(it, ignoreCase = true) }
            val detail = listOfNotNull(language, origin).joinToString(" · ").ifBlank { null }
            add(TvTrackRow(track.id, track.label.ifBlank { track.language ?: "Track" }, detail, selected, track))
        }
    }
    val selectedRow = rows.firstOrNull { it.selected } ?: rows.firstOrNull()
    val selectedRequester = remember(panel, selectedRow?.id) { FocusRequester() }
    LaunchedEffect(panel, selectedRow?.id) { runCatching { selectedRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).zIndex(3f), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.fillMaxHeight().padding(top = 28.dp, bottom = 28.dp, end = 28.dp)
                .width(510.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xF20B0D12), Color(0xF20B0D12), Color(0xE8171B23))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                .padding(horizontal = 28.dp, vertical = 26.dp)
                .focusGroup(),
        ) {
            Text(if (panel == TvTrackPanel.Audio) "Audio" else "Subtitles", color = TvColors.onMedia, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(if (panel == TvTrackPanel.Audio) "Choose an audio language" else "Choose a subtitle track", color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                rows.forEach { row ->
                    TvTrackRowItem(
                        row = row,
                        requester = if (row.id == selectedRow?.id) selectedRequester else null,
                        onSelect = {
                            when {
                                panel == TvTrackPanel.Audio && row.track != null -> onSelectAudio(row.track)
                                panel == TvTrackPanel.Subtitles && row.id == "off" -> onDisableSubtitles()
                                row.track != null -> onSelectSubtitle(row.track)
                            }
                        },
                        onBack = onDismiss,
                    )
                }
            }
            if (panel == TvTrackPanel.Subtitles) {
                Spacer(Modifier.height(10.dp))
                TvTrackRowItem(
                    row = TvTrackRow("appearance", "Appearance", "Size, position, and colors", false),
                    requester = appearanceRequester,
                    onSelect = onOpenAppearance,
                    onBack = onDismiss,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("↑  ↓  Browse     OK  Select     Back  Close", color = TvColors.mediaSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TvTrackRowItem(row: TvTrackRow, requester: FocusRequester?, onSelect: () -> Unit, onBack: () -> Unit) {
    var focused by remember(row.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.035f))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when {
                    e.key in setOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter) -> { onSelect(); true }
                    e.key == Key.Back || e.key == Key.Escape -> { onBack(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, color = TvColors.onMedia, style = MaterialTheme.typography.bodyLarge, fontWeight = if (row.selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            row.detail?.let { Text(it, color = TvColors.mediaSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        }
        if (row.selected) Text("✓", color = TvColors.accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun trackSummary(track: PlayerTrackOption): String = track.language?.takeIf(String::isNotBlank) ?: track.label

private fun subtitleOriginLabel(track: PlayerTrackOption): String = when {
    track.local || track.origin.equals("LOCAL", true) -> "Local"
    track.origin.equals("EXTERNAL", true) || track.origin.equals("ADDON", true) || track.addonSubtitleId != null || !track.embedded -> "Add-on"
    else -> "Embedded"
}

@Composable
private fun TvSubtitleAppearancePanel(
    style: PlayerSubtitleStyle,
    onStyleChanged: (PlayerSubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val requesters = remember { TvSubtitleAppearanceItem.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { requesters.getValue(TvSubtitleAppearanceItem.Size).requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).zIndex(4f), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.fillMaxHeight().padding(top = 28.dp, bottom = 28.dp, end = 28.dp)
                .width(510.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xF20B0D12), Color(0xF20B0D12), Color(0xE8171B23))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                .padding(horizontal = 28.dp, vertical = 26.dp)
                .focusGroup(),
        ) {
            Text("Subtitle appearance", color = TvColors.onMedia, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Changes preview while playback continues", color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                TvSubtitleSettingRow(
                    title = "Size", value = "${style.sizePercent}%", requester = requesters.getValue(TvSubtitleAppearanceItem.Size),
                    onBack = onDismiss, onAdjust = { direction -> onStyleChanged(style.copy(sizePercent = stepValue(tvSubtitleSizePresets, style.sizePercent, direction))) },
                )
                TvSubtitleSettingRow(
                    title = "Vertical position", value = "${style.offsetPercent}%", requester = requesters.getValue(TvSubtitleAppearanceItem.Position),
                    onBack = onDismiss, onAdjust = { direction -> onStyleChanged(style.copy(offsetPercent = stepValue(tvSubtitleOffsetPresets, style.offsetPercent, direction))) },
                )
                TvSubtitleColorRow("Text color", style.textColor, tvSubtitleColors, requesters.getValue(TvSubtitleAppearanceItem.TextColor), onDismiss) {
                    onStyleChanged(style.copy(textColor = it))
                }
                TvSubtitleColorRow("Background color", style.backgroundColor, tvSubtitleBackgroundColors, requesters.getValue(TvSubtitleAppearanceItem.BackgroundColor), onDismiss) {
                    onStyleChanged(style.copy(backgroundColor = it))
                }
                TvSubtitleColorRow("Outline color", style.outlineColor, tvSubtitleColors, requesters.getValue(TvSubtitleAppearanceItem.OutlineColor), onDismiss) {
                    onStyleChanged(style.copy(outlineColor = it))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("↑  ↓  Browse     ←  →  Adjust     OK  Cycle color     Back  Subtitles", color = TvColors.mediaSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TvSubtitleSettingRow(title: String, value: String, requester: FocusRequester, onBack: () -> Unit, onAdjust: (Int) -> Unit) {
    TvSubtitleAppearanceRow(title, value, requester, onBack, onActivate = { onAdjust(1) }, onAdjust = onAdjust)
}

@Composable
private fun TvSubtitleColorRow(
    title: String,
    selected: String,
    colors: List<Pair<String, String>>,
    requester: FocusRequester,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val index = colors.indexOfFirst { it.first.equals(selected, true) }.coerceAtLeast(0)
    val next = { direction: Int ->
        val nextIndex = (index + direction).mod(colors.size)
        onSelected(colors[nextIndex].first)
    }
    TvSubtitleAppearanceRow(title, colors.getOrNull(index)?.second ?: selected, requester, onBack, onActivate = { next(1) }, onAdjust = next)
}

@Composable
private fun TvSubtitleAppearanceRow(
    title: String,
    value: String,
    requester: FocusRequester,
    onBack: () -> Unit,
    onActivate: () -> Unit,
    onAdjust: (Int) -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.035f))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .focusRequester(requester).onFocusChanged { focused = it.isFocused }.focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionLeft -> { onAdjust(-1); true }
                    Key.DirectionRight -> { onAdjust(1); true }
                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> { onActivate(); true }
                    Key.Back, Key.Escape -> { onBack(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TvColors.onMedia, style = MaterialTheme.typography.bodyLarge, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(value, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun stepValue(presets: List<Int>, value: Int, direction: Int): Int {
    val index = presets.indexOf(value).takeIf { it >= 0 } ?: presets.indices.minByOrNull { kotlin.math.abs(presets[it] - value) } ?: 0
    return presets[(index + direction).coerceIn(presets.indices)]
}

@Composable
private fun ContextSkipAction(
    text: String,
    icon: ImageVector,
    requester: FocusRequester,
    focused: Boolean,
    onFocus: () -> Unit,
    onActivity: () -> Unit,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (focused) TvColors.onMedia else Color.Black.copy(alpha = 0.68f))
            .border(1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.68f), RoundedCornerShape(22.dp))
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter)) { onActivity(); onClick(); true } else false }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = if (focused) Color.Black else TvColors.onMedia, modifier = Modifier.size(19.dp))
            Text(text, color = if (focused) Color.Black else TvColors.onMedia, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TvTransportButton(
    icon: ImageVector,
    description: String,
    requester: FocusRequester,
    focused: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    prominent: Boolean = false,
    onFocus: () -> Unit,
    onActivity: () -> Unit,
    onClick: () -> Unit,
) {
    val background = when {
        focused -> TvColors.onMedia
        prominent -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(background)
            .border(if (focused) 3.dp else 1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = if (prominent) 0.76f else 0.3f), CircleShape)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter)) { onActivity(); onClick(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = description,
            tint = if (focused) Color.Black else TvColors.onMedia,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TvTimeline(positionMs: Long, durationMs: Long, bufferedPositionMs: Long, modifier: Modifier = Modifier) {
    val duration = durationMs.coerceAtLeast(1L)
    val played = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (bufferedPositionMs.toFloat() / duration).coerceIn(played, 1f)
    Canvas(modifier) {
        val centerY = size.height / 2f
        val strokeWidth = 4.dp.toPx()
        val knobRadius = 7.dp.toPx()
        val startX = knobRadius
        val endX = (size.width - knobRadius).coerceAtLeast(startX)
        val playheadX = startX + (endX - startX) * played
        drawLine(Color.White.copy(alpha = 0.28f), androidx.compose.ui.geometry.Offset(startX, centerY), androidx.compose.ui.geometry.Offset(endX, centerY), strokeWidth, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(startX, centerY), androidx.compose.ui.geometry.Offset(startX + (endX - startX) * buffered, centerY), strokeWidth, StrokeCap.Round)
        drawLine(TvColors.accent, androidx.compose.ui.geometry.Offset(startX, centerY), androidx.compose.ui.geometry.Offset(playheadX, centerY), strokeWidth, StrokeCap.Round)
        drawCircle(TvColors.onMedia, radius = knobRadius, center = androidx.compose.ui.geometry.Offset(playheadX, centerY))
        drawCircle(TvColors.accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(playheadX, centerY))
    }
}

@Composable
private fun TvStartingOverlay(state: TvPlaybackUiState) {
    val title = playerTitle(state)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 48.dp).alpha(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TvBufferingIndicator(size = 54.dp)
            Text(if (state.stage == TvPlaybackStage.Resolving) "Finding your stream" else "Getting things ready", color = TvColors.onMedia, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (state.fallbackProgress != null) {
                Text("Trying another source…", color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge)
                Text(state.fallbackProgress, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (title.isNotBlank()) Text(title, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TvBufferingIndicator(size: androidx.compose.ui.unit.Dp = 40.dp) {
    val transition = rememberInfiniteTransition(label = "tv-buffering")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 950)),
        label = "tv-buffering-rotation",
    )
    Canvas(Modifier.size(size)) {
        drawArc(
            color = Color.White.copy(alpha = 0.24f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = TvColors.onMedia, startAngle = phase, sweepAngle = 92f, useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun TvErrorOverlay(
    state: TvPlaybackUiState,
    retryRequester: FocusRequester,
    backRequester: FocusRequester,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("This stream couldn’t play", color = TvColors.onMedia, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            playerTitle(state).takeIf(String::isNotBlank)?.let {
                Text(it, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvActionButton("Retry", retryRequester, initiallyFocused = true, onClick = onRetry)
                TvActionButton("Back", backRequester, onClick = onBack)
            }
        }
    }
}

@Composable
private fun TvActionButton(
    text: String,
    requester: FocusRequester,
    initiallyFocused: Boolean = false,
    onClick: () -> Unit,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    Box(
        Modifier.clip(RoundedCornerShape(26.dp))
            .background(if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.1f))
            .border(if (focused) 3.dp else 1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; onFocus?.invoke(it.isFocused) }
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter)) { onClick(); true } else false }
            .padding(horizontal = 30.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (focused) Color.Black else TvColors.onMedia, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvEndedOverlay(state: TvPlaybackUiState, onPlayNext: () -> Unit, onBack: () -> Unit) {
    val playRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val backRequester = remember(state.playbackAttemptId) { FocusRequester() }
    LaunchedEffect(state.playbackAttemptId, state.nextEpisode.available, state.nextEpisode.automaticEnabled, state.nextEpisode.transition) {
        if (state.nextEpisode.available && !state.nextEpisode.automaticEnabled && state.nextEpisode.transition != TvNextEpisodeTransition.Failed) {
            runCatching { playRequester.requestFocus() }
        } else runCatching { backRequester.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.82f))))
            .padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text("✓", color = TvColors.onMedia, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text(if (state.nextEpisode.available) "Episode finished" else "You’re all caught up", color = TvColors.onMedia, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        playerTitle(state).takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text("Playback ended", color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
        if (state.nextEpisode.transition == TvNextEpisodeTransition.Loading) {
            Spacer(Modifier.height(26.dp))
            Text("Starting next episode…", color = TvColors.mediaSecondary, style = MaterialTheme.typography.titleMedium)
        } else if (state.nextEpisode.available && !state.nextEpisode.automaticEnabled) {
            Spacer(Modifier.height(26.dp))
            Text(nextEpisodeDescription(state.nextEpisode), color = TvColors.onMedia, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvActionButton("Play next episode", playRequester, initiallyFocused = true, onClick = onPlayNext)
                TvActionButton("Back", backRequester, onClick = onBack)
            }
        } else {
            Spacer(Modifier.height(18.dp))
            TvActionButton("Back", backRequester, initiallyFocused = true, onClick = onBack)
        }
        state.nextEpisode.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TvNextEpisodeCard(
    state: TvNextEpisodeState,
    playRequester: FocusRequester,
    onFocused: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(390.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xE622252C)).border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.transition == TvNextEpisodeTransition.Loading) {
            Text("Starting next episode…", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        } else {
            Text("Next episode", color = Color(0xFFB6C8FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(nextEpisodeDescription(state), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            state.title?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton("Play now", playRequester, initiallyFocused = false, onClick = onPlay, onFocus = onFocused)
                TvActionButton("Not now", remember { FocusRequester() }, onClick = onDismiss, onFocus = onFocused)
            }
        }
    }
}

private fun nextEpisodeDescription(state: TvNextEpisodeState): String =
    listOfNotNull(state.episodeLabel?.takeIf(String::isNotBlank), state.title?.takeIf(String::isNotBlank))
        .joinToString(" · ").ifBlank { "Continue watching" }

private fun requestControlFocus(
    control: TvPlayerControl,
    rewind: FocusRequester,
    playPause: FocusRequester,
    forward: FocusRequester,
    skipIntro: FocusRequester,
    skipOutro: FocusRequester,
    audio: FocusRequester,
    subtitles: FocusRequester,
) {
    val requester = when (control) {
        TvPlayerControl.Rewind -> rewind
        TvPlayerControl.PlayPause -> playPause
        TvPlayerControl.Forward -> forward
        TvPlayerControl.SkipIntro -> skipIntro
        TvPlayerControl.SkipOutro -> skipOutro
        TvPlayerControl.Audio -> audio
        TvPlayerControl.Subtitles -> subtitles
    }
    runCatching { requester.requestFocus() }.onFailure { runCatching { playPause.requestFocus() } }
}

private fun playerTitle(state: TvPlaybackUiState): String =
    listOfNotNull(state.attempt?.target?.contentName, state.attempt?.target?.episodeLabel)
        .filter(String::isNotBlank)
        .joinToString(" · ")

private fun formatTvTime(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
