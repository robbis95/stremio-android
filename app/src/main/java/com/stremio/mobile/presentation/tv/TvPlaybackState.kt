package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.model.TvSkipSegments
import com.stremio.mobile.player.PlayerEngine
import java.util.UUID

internal fun newTvPlaybackAttemptId(): String = UUID.randomUUID().toString()

internal enum class TvPlaybackStage { Idle, Resolving, Preparing, Playing, Ended, Error }

internal enum class TvNextEpisodeTransition { Idle, Loading, Failed }

/** Episode prompt/transition state is scoped to a single TV playback attempt. */
internal data class TvNextEpisodeState(
    val playbackAttemptId: String? = null,
    val videoId: String? = null,
    val episodeLabel: String? = null,
    val title: String? = null,
    val promptVisible: Boolean = false,
    val dismissed: Boolean = false,
    val automaticEnabled: Boolean = false,
    val transition: TvNextEpisodeTransition = TvNextEpisodeTransition.Idle,
    val error: String? = null,
) {
    val available: Boolean get() = !videoId.isNullOrBlank()
}

internal fun tvNextEpisodePromptVisible(
    positionMs: Long,
    durationMs: Long,
    notificationDurationMs: Long,
    available: Boolean,
    dismissed: Boolean,
): Boolean = available && !dismissed && notificationDurationMs > 0L && durationMs > 0L &&
    (durationMs - positionMs) in 0L..notificationDurationMs

internal fun isCurrentTvNextEpisode(state: TvNextEpisodeState, playbackAttemptId: String): Boolean =
    state.playbackAttemptId == playbackAttemptId

internal fun tvNextEpisodeDismiss(state: TvNextEpisodeState): TvNextEpisodeState =
    state.copy(promptVisible = false, dismissed = true)

internal fun tvNextEpisodeTransitionStarted(state: TvNextEpisodeState): TvNextEpisodeState? =
    if (!state.available || state.transition != TvNextEpisodeTransition.Idle) null
    else state.copy(promptVisible = false, transition = TvNextEpisodeTransition.Loading, error = null)

internal fun tvShouldAutoAdvanceEnded(ended: Boolean, available: Boolean, bingeWatching: Boolean): Boolean =
    ended && available && bingeWatching

internal enum class TvPlayerFocusTarget { PlayerSurface, PlayPause, Retry }

internal fun tvPlayerInitialFocusTarget(stage: TvPlaybackStage, firstVisualObserved: Boolean): TvPlayerFocusTarget =
    when {
        stage == TvPlaybackStage.Error -> TvPlayerFocusTarget.Retry
        firstVisualObserved -> TvPlayerFocusTarget.PlayPause
        else -> TvPlayerFocusTarget.PlayerSurface
    }

internal data class TvPlaybackAttempt(
    val attemptId: String,
    val target: TvStreamTarget,
    val semanticStreamKey: String,
    val sourceKind: StreamSourceKind,
    val providerTitle: String,
    val quality: String?,
    val proxyHeadersPresent: Boolean,
    val serverRequired: Boolean,
    val requestedEngine: PlayerEngine,
    val startedAtNanos: Long,
) {
    companion object {
        fun create(target: TvStreamTarget, option: StreamOption, engine: PlayerEngine, nowNanos: Long): TvPlaybackAttempt =
            TvPlaybackAttempt(
                newTvPlaybackAttemptId(), target, option.semanticKey, option.sourceKind, option.addonTitle,
                option.quality, option.core.stream.behaviorHints.proxyHeaders != null,
                com.stremio.mobile.core.streamRequiresLocalServer(option.core.stream), engine, nowNanos,
            )
    }
}

internal data class TvPlaybackTiming(
    val userSourceActivatedNanos: Long? = null,
    val resolutionStartedNanos: Long? = null,
    val playableSourceResolvedNanos: Long? = null,
    val playerLoadStartedNanos: Long? = null,
    val playerLoadReturnedNanos: Long? = null,
    val firstVisualSignalNanos: Long? = null,
) {
    val resolutionLatencyMs: Long? get() = deltaMs(resolutionStartedNanos, playableSourceResolvedNanos)
    val playerLoadCallLatencyMs: Long? get() = deltaMs(playerLoadStartedNanos, playerLoadReturnedNanos)
    val ttffMs: Long? get() = deltaMs(userSourceActivatedNanos, firstVisualSignalNanos)
    val postResolveToFirstVisualMs: Long? get() = deltaMs(playableSourceResolvedNanos, firstVisualSignalNanos)

    private fun deltaMs(start: Long?, end: Long?): Long? =
        if (start != null && end != null && end >= start) (end - start) / 1_000_000 else null
}

internal data class TvPlaybackUiState(
    val attempt: TvPlaybackAttempt? = null,
    val option: StreamOption? = null,
    val stage: TvPlaybackStage = TvPlaybackStage.Idle,
    val requestedEngine: PlayerEngine? = null,
    val actualEngine: PlayerEngine? = null,
    val error: String? = null,
    val timing: TvPlaybackTiming = TvPlaybackTiming(),
    val serverStatus: String? = null,
    val resolutionKind: String? = null,
    val convertedSourceKind: String? = null,
    val firstVisualObserved: Boolean = false,
    val isBuffering: Boolean = false,
    val runtime: com.stremio.mobile.player.PlayerRuntimeState = com.stremio.mobile.player.PlayerRuntimeState(),
    val skipSegments: TvSkipSegments = TvSkipSegments(),
    val nextEpisode: TvNextEpisodeState = TvNextEpisodeState(),
) {
    val isResolvingOrPreparing: Boolean get() = stage == TvPlaybackStage.Resolving || stage == TvPlaybackStage.Preparing
    val playbackAttemptId: String? get() = attempt?.attemptId
}

internal fun isCurrentTvAttempt(state: TvPlaybackUiState, attemptId: String): Boolean =
    state.attempt?.attemptId == attemptId

internal fun tvProgressReportingAllowed(state: TvPlaybackUiState): Boolean = state.firstVisualObserved

internal data class TvEngineResult(val requested: PlayerEngine, val actual: PlayerEngine) {
    val fallbackUsed: Boolean get() = requested != actual
}

internal fun clampTvSeekTarget(positionMs: Long, durationMs: Long): Long? =
    if (durationMs <= 0L) null else positionMs.coerceIn(0L, durationMs)

internal data class TvPlaybackCompletionPolicy(val reportEnded: Boolean = true, val autoAdvance: Boolean = false)

internal val tvPlaybackCompletionPolicy = TvPlaybackCompletionPolicy()

internal fun safeTvPlaybackTrace(
    attempt: TvPlaybackAttempt,
    stage: String,
    actualEngine: PlayerEngine? = null,
    durations: TvPlaybackTiming = TvPlaybackTiming(),
    signalKind: String? = null,
    server: String? = null,
    resolution: String? = null,
    convertedSource: String? = null,
): String = buildString {
    append("attempt=").append(attempt.attemptId)
    append(" source=").append(attempt.sourceKind.name)
    append(" provider=").append(attempt.providerTitle.take(80))
    attempt.quality?.let { append(" quality=").append(it.take(32)) }
    append(" requestedEngine=").append(attempt.requestedEngine.name)
    if (actualEngine != null) append(" actualEngine=").append(actualEngine.name)
    append(" proxyHeaders=").append(if (attempt.proxyHeadersPresent) "yes" else "no")
    append(" serverRequired=").append(if (attempt.serverRequired) "yes" else "no")
    server?.let { append(" server=").append(it) }
    resolution?.let { append(" resolution=").append(it) }
    convertedSource?.let { append(" convertedSource=").append(it) }
    append(" stage=").append(stage)
    durations.resolutionLatencyMs?.let { append(" resolutionMs=").append(it) }
    durations.playerLoadCallLatencyMs?.let { append(" loadCallMs=").append(it) }
    durations.ttffMs?.let { append(" ttffMs=").append(it) }
    durations.postResolveToFirstVisualMs?.let { append(" postResolveVisualMs=").append(it) }
    signalKind?.let { append(" signal=").append(it) }
}
