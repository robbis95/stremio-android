package com.stremio.mobile.data.model

/** Core-backed skip boundaries expressed in the active playback timeline, in milliseconds. */
internal data class TvSkipSegments(
    val intro: TvSkipSegment? = null,
    /** Start of the outro region. Skipping seeks to the active media duration. */
    val outroStartMs: Long? = null,
)

internal data class TvSkipSegment(
    val startMs: Long,
    val endMs: Long,
)

/**
 * Core reports intro boundaries in milliseconds and aligns them to the active stream duration.
 * Its optional intro duration is the matched source duration used by Core for that alignment;
 * it is not a second playback interval to scale in the UI.
 */
internal fun normalizeTvSkipSegments(
    introFromMs: Long?,
    introToMs: Long?,
    introSourceDurationMs: Long?,
    outroStartMs: Long?,
    playbackDurationMs: Long,
): TvSkipSegments {
    if (playbackDurationMs <= 0L) return TvSkipSegments()

    val intro = if (
        introFromMs != null && introToMs != null &&
        introFromMs >= 0L && introToMs > introFromMs && introToMs <= playbackDurationMs &&
        (introSourceDurationMs == null || introSourceDurationMs > 0L)
    ) {
        TvSkipSegment(introFromMs, introToMs)
    } else {
        null
    }
    val outro = outroStartMs?.takeIf { it > 0L && it < playbackDurationMs }
    return TvSkipSegments(intro, outro)
}

internal fun tvIntroSkipTarget(segments: TvSkipSegments, positionMs: Long): Long? =
    segments.intro?.takeIf { positionMs >= it.startMs && positionMs < it.endMs }?.endMs

internal fun tvOutroSkipTarget(segments: TvSkipSegments, positionMs: Long, durationMs: Long): Long? =
    segments.outroStartMs
        ?.takeIf { durationMs > 0L && it < durationMs && positionMs >= it && positionMs < durationMs }
        ?.let { durationMs }
