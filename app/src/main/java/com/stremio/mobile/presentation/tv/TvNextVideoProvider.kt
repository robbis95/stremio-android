package com.stremio.mobile.presentation.tv

import com.stremio.core.types.resource.Video

/** Supplies the next metadata item for TV playback. The Core provider remains the default. */
internal fun interface TvNextVideoProvider {
    fun getNextVideo(target: TvStreamTarget, attemptId: String): Video?
}

internal class CoreTvNextVideoProvider(
    private val coreNextVideo: () -> Video?,
) : TvNextVideoProvider {
    override fun getNextVideo(target: TvStreamTarget, attemptId: String): Video? = coreNextVideo()
}

/** Replaces next-video metadata only while the explicitly enabled DEBUG fixture sequence owns target. */
internal class FixtureAwareTvNextVideoProvider(
    private val coreProvider: TvNextVideoProvider,
    private val fixtureNextVideo: (TvStreamTarget) -> FixtureNextVideo?,
) : TvNextVideoProvider {
    constructor(coreProvider: TvNextVideoProvider, fixtures: TvValidationFixtures) : this(coreProvider, fixtures::nextVideoFor)

    override fun getNextVideo(target: TvStreamTarget, attemptId: String): Video? {
        val fixtureResult = fixtureNextVideo(target)
        return if (fixtureResult != null) fixtureResult.video else coreProvider.getNextVideo(target, attemptId)
    }
}

internal fun tvNextVideoForAttempt(
    provider: TvNextVideoProvider,
    target: TvStreamTarget,
    attemptId: String,
    currentAttemptId: String?,
): Video? = if (attemptId == currentAttemptId) provider.getNextVideo(target, attemptId) else null
