package com.stremio.mobile.presentation.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaybackLabStateTest {
    @Test
    fun prefetchLabCoversHitReuseFallbackAndStaleAttemptRejection() {
        val cache = TvStreamPrefetchCache()
        val ready = cache.begin("attempt-1", "series:show:episode-7", 10L)
        ready.result.complete(emptyList())
        assertSame(ready, cache.find("attempt-1", "series:show:episode-7"))
        assertTrue(ready.result.isCompleted)

        val inProgress = cache.begin("attempt-2", "series:show:episode-8", 20L)
        assertSame(inProgress, cache.find("attempt-2", "series:show:episode-8"))
        assertFalse(inProgress.result.isCompleted)

        assertNull(cache.find("attempt-2", "series:show:episode-9")) // Live discovery fallback.
        val replacement = cache.begin("attempt-3", "series:show:episode-8", 30L)
        assertFalse(cache.isCurrent(inProgress))
        assertNull(cache.find("attempt-2", "series:show:episode-8"))
        assertSame(replacement, cache.find("attempt-3", "series:show:episode-8"))
    }

    @Test
    fun presetsMapToProductionPlayerStagesAndRuntime() {
        val starting = labState(SimPreset.Starting, 10_000L, 600_000L, 30_000L)
        val buffering = labState(SimPreset.Buffering, 10_000L, 600_000L, 30_000L)
        val playing = labState(SimPreset.Playing, 10_000L, 600_000L, 30_000L)
        val paused = labState(SimPreset.Paused, 10_000L, 600_000L, 30_000L)
        val error = labState(SimPreset.Error, 10_000L, 600_000L, 30_000L)
        val ended = labState(SimPreset.Ended, 10_000L, 600_000L, 30_000L)
        val intro = labState(SimPreset.IntroActive, 100_000L, 1_800_000L, 300_000L)
        val outro = labState(SimPreset.OutroActive, 1_650_000L, 1_800_000L, 1_700_000L)

        assertEquals(TvPlaybackStage.Preparing, starting.stage)
        assertFalse(starting.firstVisualObserved)
        assertTrue(buffering.isBuffering)
        assertEquals(TvPlaybackStage.Playing, playing.stage)
        assertTrue(playing.runtime.isPlaying)
        assertFalse(paused.runtime.isPlaying)
        assertEquals(TvPlaybackStage.Error, error.stage)
        assertEquals(TvPlaybackStage.Ended, ended.stage)
        assertEquals(600_000L, ended.runtime.positionMs)
        assertTrue(intro.runtime.isPlaying)
        assertTrue(outro.runtime.isPlaying)
        assertEquals(90_000L, intro.resolvedSegments.firstOrNull { it.type == com.stremio.mobile.presentation.tv.segments.TvSegmentType.Intro }?.startMs)
        assertEquals(1_620_000L, outro.resolvedSegments.firstOrNull { it.type == com.stremio.mobile.presentation.tv.segments.TvSegmentType.Credits }?.startMs)
    }

    @Test
    fun simulatedSkipSegmentsAreAvailableForAPlayableDuration() {
        val state = labState(SimPreset.Playing, 100_000L, 1_800_000L, 300_000L)

        val intro = state.resolvedSegments.first { it.type == com.stremio.mobile.presentation.tv.segments.TvSegmentType.Intro }
        val credits = state.resolvedSegments.first { it.type == com.stremio.mobile.presentation.tv.segments.TvSegmentType.Credits }
        assertEquals(90_000L, intro.startMs)
        assertEquals(150_000L, intro.endMs)
        assertEquals(1_620_000L, credits.startMs)
        assertEquals(100_000L, state.runtime.positionMs)
        assertEquals(300_000L, state.runtime.bufferedPositionMs)
    }

    @Test
    fun nextEpisodePresetsUseProductionPlayerStateAndMetadata() {
        val outside = labState(SimPreset.NextOutsideWindow, 120_000L, 1_800_000L, 150_000L)
        val visible = labState(SimPreset.NextPromptVisible, 1_790_000L, 1_800_000L, 1_800_000L)
        val dismissed = labState(SimPreset.NextPromptDismissed, 1_790_000L, 1_800_000L, 1_800_000L)
        val endedWithNext = labState(SimPreset.EndedWithNext, 1_800_000L, 1_800_000L, 1_800_000L)
        val endedWithoutNext = labState(SimPreset.EndedWithoutNext, 1_800_000L, 1_800_000L, 1_800_000L)

        assertEquals(TvPlaybackStage.Playing, outside.stage)
        assertFalse(outside.nextEpisode.promptVisible)
        assertTrue(visible.nextEpisode.promptVisible)
        assertTrue(dismissed.nextEpisode.dismissed)
        assertFalse(dismissed.nextEpisode.promptVisible)
        assertEquals("S01E07", visible.nextEpisode.episodeLabel)
        assertEquals("The Next Chapter", visible.nextEpisode.title)
        assertEquals(TvPlaybackStage.Ended, endedWithNext.stage)
        assertTrue(endedWithNext.nextEpisode.available)
        assertEquals(TvPlaybackStage.Ended, endedWithoutNext.stage)
        assertFalse(endedWithoutNext.nextEpisode.available)
    }
}
