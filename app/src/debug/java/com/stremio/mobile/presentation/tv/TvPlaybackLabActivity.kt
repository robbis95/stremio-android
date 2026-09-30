package com.stremio.mobile.presentation.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.model.TvSkipSegment
import com.stremio.mobile.data.model.TvSkipSegments
import com.stremio.mobile.player.PlaybackManager
import com.stremio.mobile.player.Player
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.player.PlayerPlaybackEvent
import com.stremio.mobile.player.PlayerRuntimeState
import com.stremio.mobile.player.PlayerTrackOption
import com.stremio.mobile.player.PlayerTrackType
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val SIM_DURATION_MS = 30 * 60 * 1000L
private const val SAMPLE_MP4_URL = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"
private const val SAMPLE_HLS_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
private const val LOCAL_MAC_URL = "http://10.0.2.2:8080/sample.mp4"
private val SIM_SKIP_SEGMENTS = TvSkipSegments(
    intro = TvSkipSegment(startMs = 90_000L, endMs = 150_000L),
    outroStartMs = 27 * 60 * 1000L,
)
private val SIM_AUDIO_TRACKS = listOf(
    PlayerTrackOption("lab-audio-en", PlayerTrackType.AUDIO, "English", "English", selected = true, languageCode = "eng"),
    PlayerTrackOption("lab-audio-sv", PlayerTrackType.AUDIO, "Swedish", "Swedish", selected = false, languageCode = "swe"),
    PlayerTrackOption("lab-audio-es", PlayerTrackType.AUDIO, "Spanish", "Spanish", selected = false, languageCode = "spa"),
)
private val SIM_SUBTITLE_TRACKS = listOf(
    PlayerTrackOption("lab-sub-en", PlayerTrackType.SUBTITLE, "English", "English", selected = false, languageCode = "eng"),
    PlayerTrackOption("lab-sub-sv", PlayerTrackType.SUBTITLE, "Swedish", "Swedish", selected = false, languageCode = "swe"),
    PlayerTrackOption("lab-sub-es", PlayerTrackType.SUBTITLE, "Spanish", "Spanish", selected = false, languageCode = "spa"),
    PlayerTrackOption("lab-sub-addon", PlayerTrackType.SUBTITLE, "English SDH", "English", selected = false, languageCode = "eng", origin = "EXTERNAL", url = "https://example.invalid/subtitles.vtt", embedded = false),
)

private enum class LabMode { RealMedia, SimulatedState }
private enum class MediaPreset(val label: String, val url: String?) {
    SampleMp4("Sample MP4", SAMPLE_MP4_URL),
    SampleHls("Sample HLS", SAMPLE_HLS_URL),
    LocalMac("Local Mac", LOCAL_MAC_URL),
    CustomUrl("Custom URL", null),
}
internal enum class SimPreset(val label: String) {
    Starting("Starting"), Buffering("Buffering"), Playing("Playing"),
    Paused("Paused"), Error("Error"), Ended("Ended"),
    IntroActive("Intro active"), OutroActive("Outro active"),
}

class TvPlaybackLabActivity : ComponentActivity() {
    private lateinit var playbackManager: PlaybackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackManager = PlaybackManager(applicationContext)
        setContent { TvTheme { PlaybackLab(playbackManager) } }
    }

    override fun onDestroy() {
        playbackManager.release()
        super.onDestroy()
    }
}

@Composable
private fun PlaybackLab(playbackManager: PlaybackManager) {
    var mode by remember { mutableStateOf(LabMode.RealMedia) }
    var preset by remember { mutableStateOf(SimPreset.Playing) }
    var mediaPreset by remember { mutableStateOf(MediaPreset.SampleMp4) }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var positionInput by remember { mutableStateOf("120000") }
    var durationInput by remember { mutableStateOf(SIM_DURATION_MS.toString()) }
    var bufferedInput by remember { mutableStateOf("600000") }
    var inPlayer by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<Player?>(null) }
    var playbackState by remember { mutableStateOf(labState(preset, 120_000L, SIM_DURATION_MS, 600_000L)) }
    val simulatedRuntime = remember { MutableStateFlow(PlayerRuntimeState()) }
    val runtime by (player?.runtimeState ?: simulatedRuntime).collectAsState(
        initial = PlayerRuntimeState(),
    )
    fun applyPreset(next: SimPreset) {
        preset = next
        val presetPosition = when (next) {
            SimPreset.IntroActive -> 100_000L
            SimPreset.OutroActive -> (SIM_SKIP_SEGMENTS.outroStartMs ?: 27 * 60 * 1000L) + 30_000L
            else -> positionInput.toLongOrNull()?.coerceAtLeast(0L) ?: 120_000L
        }
        val initialPosition = presetPosition
        val duration = durationInput.toLongOrNull()?.coerceAtLeast(0L) ?: SIM_DURATION_MS
        val buffered = bufferedInput.toLongOrNull()?.coerceAtLeast(0L) ?: initialPosition
        playbackState = labState(next, initialPosition, duration, buffered).copy(attempt = playbackState.attempt)
        simulatedRuntime.value = playbackState.runtime
    }

    fun updateRuntime(update: (PlayerRuntimeState) -> PlayerRuntimeState) {
        if (mode == LabMode.SimulatedState) {
            simulatedRuntime.value = update(simulatedRuntime.value)
            playbackState = playbackState.copy(runtime = simulatedRuntime.value)
        }
    }

    fun leavePlayer() {
        if (mode == LabMode.RealMedia) playbackManager.release()
        player = null
        inPlayer = false
    }

    BackHandler(enabled = inPlayer) { leavePlayer() }

    LaunchedEffect(inPlayer, mode, preset) {
        while (inPlayer && mode == LabMode.SimulatedState) {
            delay(500)
            val current = simulatedRuntime.value
            if (current.isPlaying && !current.isBuffering) {
                val nextPosition = (current.positionMs + 500L).coerceAtMost(current.durationMs)
                simulatedRuntime.value = current.copy(
                    positionMs = nextPosition,
                    bufferedPositionMs = maxOf(current.bufferedPositionMs, nextPosition + 30_000L)
                        .coerceAtMost(current.durationMs),
                    isPlaying = nextPosition < current.durationMs,
                    ended = nextPosition >= current.durationMs,
                )
                playbackState = playbackState.copy(
                    stage = if (nextPosition >= current.durationMs) TvPlaybackStage.Ended else TvPlaybackStage.Playing,
                    runtime = simulatedRuntime.value,
                )
            }
        }
    }

    if (inPlayer) {
        val currentState = if (mode == LabMode.SimulatedState) {
            playbackState.copy(
                runtime = runtime,
                isBuffering = runtime.isBuffering,
                stage = if (runtime.ended) TvPlaybackStage.Ended else playbackState.stage,
            )
        } else playbackState.copy(
            runtime = runtime,
            isBuffering = runtime.isBuffering,
            stage = when {
                runtime.ended -> TvPlaybackStage.Ended
                playbackState.stage == TvPlaybackStage.Error -> TvPlaybackStage.Error
                playbackState.firstVisualObserved -> TvPlaybackStage.Playing
                else -> playbackState.stage
            },
        )
        TvPlayerScreen(
            state = currentState,
            player = player,
            seekDurationMs = 10_000L,
            onTogglePlayback = {
                if (mode == LabMode.RealMedia) {
                    if (runtime.isPlaying) playbackManager.pause() else playbackManager.play()
                } else {
                    val playing = !simulatedRuntime.value.isPlaying
                    updateRuntime { it.copy(isPlaying = playing, isBuffering = false, ended = false) }
                    playbackState = playbackState.copy(stage = TvPlaybackStage.Playing)
                }
            },
            onSeek = { delta ->
                val target = (runtime.positionMs + delta).coerceIn(0L, runtime.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
                if (mode == LabMode.RealMedia) player?.seekTo(target)
                else updateRuntime { it.copy(positionMs = target, bufferedPositionMs = maxOf(it.bufferedPositionMs, target)) }
            },
            onSeekTo = { target ->
                if (mode == LabMode.RealMedia) player?.seekTo(target)
                else updateRuntime { it.copy(positionMs = target.coerceIn(0L, it.durationMs)) }
            },
            onAudioTrackSelected = { track ->
                if (mode == LabMode.SimulatedState) updateRuntime {
                    it.copy(audioTracks = it.audioTracks.map { option -> option.copy(selected = option.id == track.id) })
                }
            },
            onSubtitleTrackSelected = { track ->
                if (mode == LabMode.SimulatedState) updateRuntime {
                    it.copy(
                        subtitleTracks = it.subtitleTracks.map { option -> option.copy(selected = option.id == track.id) },
                        subtitlesDisabled = false,
                    )
                }
            },
            onSubtitlesDisabled = {
                if (mode == LabMode.SimulatedState) updateRuntime {
                    it.copy(subtitleTracks = it.subtitleTracks.map { option -> option.copy(selected = false) }, subtitlesDisabled = true)
                }
            },
            onRetry = {
                if (mode == LabMode.RealMedia) {
                    player?.retry()
                    playbackState = playbackState.copy(stage = TvPlaybackStage.Preparing, firstVisualObserved = false)
                } else applyPreset(SimPreset.Starting)
            },
            onBack = ::leavePlayer,
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(TvColors.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TV Playback Lab", style = MaterialTheme.typography.headlineMedium, color = TvColors.primaryText)
        Text("Runs the production TV player surface in an isolated debug Activity.", color = TvColors.secondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { mode = LabMode.RealMedia }) { Text("Real Media") }
            Button(onClick = { mode = LabMode.SimulatedState }) { Text("Simulated State") }
        }
        if (mode == LabMode.RealMedia) {
            Text("Media source", color = TvColors.primaryText, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaPreset.entries.forEach { source ->
                    Button(onClick = {
                        if (source == MediaPreset.CustomUrl && mediaPreset != MediaPreset.CustomUrl) url = ""
                        mediaPreset = source
                    }) { Text(if (mediaPreset == source) "✓ ${source.label}" else source.label) }
                }
            }
            val selectedUrl = mediaPreset.url ?: url.trim()
            Text(
                when (mediaPreset) {
                    MediaPreset.SampleMp4 -> "Big Buck Bunny · 10 seconds · H.264 MP4"
                    MediaPreset.SampleHls -> "Mux public HLS test stream · H.264/AAC variants"
                    MediaPreset.LocalMac -> "Emulator host: serve sample.mp4 at 10.0.2.2:8080"
                    MediaPreset.CustomUrl -> "Enter an HTTP or HTTPS media URL."
                },
                color = TvColors.secondaryText,
            )
            if (mediaPreset == MediaPreset.CustomUrl) {
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("MP4 or HLS URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
            }
            Text("Cleartext HTTP is enabled only in debug.", color = TvColors.secondaryText)
            Button(onClick = {
                val uri = runCatching { Uri.parse(selectedUrl) }.getOrNull() ?: return@Button
                if (uri.scheme !in setOf("http", "https")) return@Button
                val playbackTitle = when (mediaPreset) {
                    MediaPreset.SampleMp4 -> "Sample MP4 · Big Buck Bunny"
                    MediaPreset.SampleHls -> "Sample HLS · Mux test stream"
                    MediaPreset.LocalMac -> "Local Mac sample"
                    MediaPreset.CustomUrl -> title.ifBlank { selectedUrl }
                }
                playbackManager.setPlaybackEventListener { event ->
                    when (event) {
                        is PlayerPlaybackEvent.FirstVisualFrame -> playbackState = playbackState.copy(
                            stage = TvPlaybackStage.Playing, firstVisualObserved = true,
                        )
                        is PlayerPlaybackEvent.PlaybackError -> playbackState = playbackState.copy(
                            stage = TvPlaybackStage.Error, error = event.category,
                        )
                    }
                }
                playbackState = labState(SimPreset.Starting, 0L, 0L, 0L).copy(
                    attempt = labAttempt(playbackTitle),
                )
                runCatching { playbackManager.load(uri, playbackTitle) }
                    .onSuccess { player = playbackManager.getPlayer() }
                    .onFailure { playbackState = playbackState.copy(stage = TvPlaybackStage.Error, error = it.message) }
                inPlayer = true
            }) { Text("Play") }
        } else {
            Text("Preset", color = TvColors.primaryText, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimPreset.entries.take(4).forEach { state -> Button(onClick = { applyPreset(state) }) { Text(state.label) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimPreset.entries.drop(4).forEach { state -> Button(onClick = { applyPreset(state) }) { Text(state.label) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(positionInput, { positionInput = it }, label = { Text("Position ms") }, modifier = Modifier.weight(1f))
                OutlinedTextField(durationInput, { durationInput = it }, label = { Text("Duration ms") }, modifier = Modifier.weight(1f))
                OutlinedTextField(bufferedInput, { bufferedInput = it }, label = { Text("Buffered ms") }, modifier = Modifier.weight(1f))
            }
            Text("Fake intro: 01:30–02:30 · fake outro starts at 27:00", color = TvColors.secondaryText)
            Button(onClick = {
                applyPreset(preset)
                playbackState = playbackState.copy(attempt = labAttempt("Simulated ${preset.label}"))
                player = null
                inPlayer = true
            }) { Text("Open simulated player") }
        }
        Spacer(Modifier.height(8.dp))
        Text("ADB: adb shell am start -n com.stremio.mobile/.presentation.tv.TvPlaybackLabActivity", color = TvColors.secondaryText)
    }
}

private fun labAttempt(label: String) = TvPlaybackAttempt(
    attemptId = newTvPlaybackAttemptId(),
    target = TvStreamTarget("other", "playback-lab", label, guessStreamPath = true),
    semanticStreamKey = "playback-lab",
    sourceKind = StreamSourceKind.Direct,
    providerTitle = "Playback Lab",
    quality = null,
    proxyHeadersPresent = false,
    serverRequired = false,
    requestedEngine = PlayerEngine.EXO,
    startedAtNanos = System.nanoTime(),
)

internal fun labState(preset: SimPreset, positionMs: Long, durationMs: Long, bufferedMs: Long): TvPlaybackUiState {
    val stage = when (preset) {
        SimPreset.Starting -> TvPlaybackStage.Preparing
        SimPreset.Buffering, SimPreset.Playing, SimPreset.Paused, SimPreset.IntroActive, SimPreset.OutroActive -> TvPlaybackStage.Playing
        SimPreset.Error -> TvPlaybackStage.Error
        SimPreset.Ended -> TvPlaybackStage.Ended
    }
    val playing = preset in setOf(SimPreset.Playing, SimPreset.IntroActive, SimPreset.OutroActive)
    val buffering = preset == SimPreset.Buffering
    val ended = preset == SimPreset.Ended
    val firstVisual = preset in setOf(SimPreset.Buffering, SimPreset.Playing, SimPreset.Paused, SimPreset.Ended, SimPreset.IntroActive, SimPreset.OutroActive)
    val runtime = PlayerRuntimeState(
        isPlaying = playing,
        isBuffering = buffering,
        positionMs = if (ended) durationMs else positionMs.coerceAtMost(durationMs.takeIf { it > 0 } ?: positionMs),
        durationMs = durationMs,
        bufferedPositionMs = bufferedMs.coerceAtMost(durationMs.takeIf { it > 0 } ?: bufferedMs),
        ended = ended,
        error = if (preset == SimPreset.Error) "Simulated playback failure" else null,
        audioTracks = SIM_AUDIO_TRACKS,
        subtitleTracks = SIM_SUBTITLE_TRACKS,
        subtitlesDisabled = true,
    )
    return TvPlaybackUiState(
        stage = stage,
        requestedEngine = PlayerEngine.EXO,
        actualEngine = PlayerEngine.EXO,
        error = runtime.error,
        firstVisualObserved = firstVisual,
        isBuffering = buffering,
        runtime = runtime,
        skipSegments = SIM_SKIP_SEGMENTS.takeIf { durationMs >= 27 * 60 * 1000L } ?: TvSkipSegments(),
    )
}
