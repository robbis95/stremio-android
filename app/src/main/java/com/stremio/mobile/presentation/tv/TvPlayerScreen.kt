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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.stremio.mobile.data.model.tvIntroSkipTarget
import com.stremio.mobile.data.model.tvOutroSkipTarget
import com.stremio.mobile.player.Player
import com.stremio.mobile.presentation.tv.theme.TvColors
import kotlinx.coroutines.delay

private enum class TvPlayerControl { Rewind, PlayPause, Forward, SkipIntro, SkipOutro }

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
) {
    var controlsVisible by remember(state.playbackAttemptId) { mutableStateOf(true) }
    var controlsActivity by remember(state.playbackAttemptId) { mutableIntStateOf(0) }
    var lastFocusedControl by remember(state.playbackAttemptId) { mutableStateOf(TvPlayerControl.PlayPause) }
    val rewindRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val playPauseRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val forwardRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val skipIntroRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val skipOutroRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val retryRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val backRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val rootRequester = remember(state.playbackAttemptId) { FocusRequester() }

    LaunchedEffect(state.stage, state.runtime.isPlaying, state.isBuffering, controlsVisible, controlsActivity) {
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
            TvPlaybackStage.Ended -> TvEndedOverlay(state)
        }

        AnimatedVisibility(
            visible = state.firstVisualObserved && controlsVisible && state.stage != TvPlaybackStage.Error,
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
                onActivity = { controlsActivity++ },
                onTogglePlayback = onTogglePlayback,
                onSeek = onSeek,
                onSeekTo = onSeekTo,
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
    onActivity: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val introTarget = tvIntroSkipTarget(state.skipSegments, state.runtime.positionMs)
    val outroTarget = tvOutroSkipTarget(state.skipSegments, state.runtime.positionMs, state.runtime.durationMs)
    val title = state.attempt?.target?.contentName.orEmpty().ifBlank { "Now playing" }
    val episodeLabel = state.attempt?.target?.episodeLabel
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f), Color.Black.copy(alpha = 0.9f))))
            .padding(start = 72.dp, end = 72.dp, top = 72.dp, bottom = 46.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (introTarget != null || outroTarget != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (introTarget != null) {
                    ContextSkipAction(
                        text = "Skip Intro", icon = Icons.Outlined.SkipNext,
                        requester = skipIntroRequester, focused = lastFocusedControl == TvPlayerControl.SkipIntro,
                        onFocus = { onFocused(TvPlayerControl.SkipIntro) }, onActivity = onActivity,
                        onClick = { onSeekTo(introTarget) },
                    )
                }
                if (introTarget != null && outroTarget != null) Spacer(Modifier.width(14.dp))
                if (outroTarget != null) {
                    ContextSkipAction(
                        text = "Skip Outro", icon = Icons.Outlined.SkipNext,
                        requester = skipOutroRequester, focused = lastFocusedControl == TvPlayerControl.SkipOutro,
                        onFocus = { onFocused(TvPlayerControl.SkipOutro) }, onActivity = onActivity,
                        onClick = { onSeekTo(outroTarget) },
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
    }
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
private fun TvActionButton(text: String, requester: FocusRequester, initiallyFocused: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    Box(
        Modifier.clip(RoundedCornerShape(26.dp))
            .background(if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.1f))
            .border(if (focused) 3.dp else 1.dp, if (focused) TvColors.onMedia else Color.White.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter)) { onClick(); true } else false }
            .padding(horizontal = 30.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (focused) Color.Black else TvColors.onMedia, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvEndedOverlay(state: TvPlaybackUiState) {
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
        Text("You’re all caught up", color = TvColors.onMedia, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        playerTitle(state).takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text("Playback ended", color = TvColors.mediaSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun requestControlFocus(
    control: TvPlayerControl,
    rewind: FocusRequester,
    playPause: FocusRequester,
    forward: FocusRequester,
    skipIntro: FocusRequester,
    skipOutro: FocusRequester,
) {
    val requester = when (control) {
        TvPlayerControl.Rewind -> rewind
        TvPlayerControl.PlayPause -> playPause
        TvPlayerControl.Forward -> forward
        TvPlayerControl.SkipIntro -> skipIntro
        TvPlayerControl.SkipOutro -> skipOutro
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
