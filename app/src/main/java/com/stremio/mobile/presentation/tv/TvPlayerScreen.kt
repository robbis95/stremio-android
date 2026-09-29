package com.stremio.mobile.presentation.tv

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremio.mobile.player.Player
import com.stremio.mobile.presentation.tv.theme.TvColors
import kotlinx.coroutines.delay

@Composable
internal fun TvPlayerScreen(
    state: TvPlaybackUiState,
    player: Player?,
    seekDurationMs: Long,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var controlsVisible by remember(state.playbackAttemptId) { mutableStateOf(true) }
    var controlsActivity by remember(state.playbackAttemptId) { mutableIntStateOf(0) }
    val controlsRequester = remember(state.playbackAttemptId) { FocusRequester() }
    val rootRequester = remember(state.playbackAttemptId) { FocusRequester() }

    LaunchedEffect(state.stage, state.runtime.positionMs, state.runtime.isPlaying, state.isBuffering, controlsVisible, controlsActivity) {
        if (controlsVisible && state.stage == TvPlaybackStage.Playing && state.runtime.isPlaying && !state.isBuffering) {
            delay(5_000)
            runCatching { rootRequester.requestFocus() }
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, state.firstVisualObserved) {
        if (controlsVisible && state.firstVisualObserved) runCatching { controlsRequester.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .focusRequester(rootRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (controlsVisible && state.firstVisualObserved) {
                    controlsActivity++
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (!controlsVisible) controlsVisible = true
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
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
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.stage != TvPlaybackStage.Idle) androidx.compose.material3.CircularProgressIndicator()
                    Text("Starting…", color = TvColors.primaryText, style = MaterialTheme.typography.headlineSmall)
                    state.attempt?.target?.let { target ->
                        Text(
                            listOfNotNull(target.contentName, target.episodeLabel).joinToString(" · "),
                            color = TvColors.secondaryText,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            TvPlaybackStage.Error ->
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Couldn’t start this source", color = TvColors.primaryText, style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onRetry) { Text("Retry") }
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
            TvPlaybackStage.Playing, TvPlaybackStage.Ended -> Unit
        }

        if (state.firstVisualObserved && controlsVisible) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(TvColors.background.copy(alpha = 0.86f))
                    .padding(horizontal = 56.dp, vertical = 22.dp)
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    listOfNotNull(state.attempt?.target?.contentName, state.attempt?.target?.episodeLabel).joinToString(" · "),
                    color = TvColors.primaryText,
                    style = MaterialTheme.typography.titleMedium,
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = {
                        if (state.runtime.durationMs > 0) (state.runtime.positionMs.toFloat() / state.runtime.durationMs).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = TvColors.accent,
                    trackColor = TvColors.divider,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatTvTime(state.runtime.positionMs)} / ${formatTvTime(state.runtime.durationMs)}", color = TvColors.secondaryText)
                    if (state.isBuffering) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.width(20.dp))
                            Text("  Buffering", color = TvColors.secondaryText)
                        }
                    } else Box(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onSeek(-seekDurationMs) }) { Text("−${seekDurationMs / 1000}s") }
                        Button(onClick = onTogglePlayback, modifier = Modifier.focusRequester(controlsRequester)) {
                            Text(if (state.runtime.isPlaying) "Pause" else "Play")
                        }
                        Button(onClick = { onSeek(seekDurationMs) }) { Text("+${seekDurationMs / 1000}s") }
                    }
                }
            }
        } else if (state.firstVisualObserved && state.isBuffering) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (state.stage == TvPlaybackStage.Ended) {
            Text("Playback ended", Modifier.align(Alignment.TopCenter).padding(top = 36.dp), color = TvColors.primaryText)
        }
    }
}

private fun formatTvTime(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
