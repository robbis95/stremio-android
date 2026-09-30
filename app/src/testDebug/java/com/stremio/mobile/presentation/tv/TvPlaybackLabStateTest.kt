package com.stremio.mobile.presentation.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaybackLabStateTest {
    @Test
    fun presetsMapToProductionPlayerStagesAndRuntime() {
        val starting = labState(SimPreset.Starting, 10_000L, 600_000L, 30_000L)
        val buffering = labState(SimPreset.Buffering, 10_000L, 600_000L, 30_000L)
        val playing = labState(SimPreset.Playing, 10_000L, 600_000L, 30_000L)
        val paused = labState(SimPreset.Paused, 10_000L, 600_000L, 30_000L)
        val error = labState(SimPreset.Error, 10_000L, 600_000L, 30_000L)
        val ended = labState(SimPreset.Ended, 10_000L, 600_000L, 30_000L)

        assertEquals(TvPlaybackStage.Preparing, starting.stage)
        assertFalse(starting.firstVisualObserved)
        assertTrue(buffering.isBuffering)
        assertEquals(TvPlaybackStage.Playing, playing.stage)
        assertTrue(playing.runtime.isPlaying)
        assertFalse(paused.runtime.isPlaying)
        assertEquals(TvPlaybackStage.Error, error.stage)
        assertEquals(TvPlaybackStage.Ended, ended.stage)
        assertEquals(600_000L, ended.runtime.positionMs)
    }

    @Test
    fun simulatedSkipSegmentsAreAvailableForAPlayableDuration() {
        val state = labState(SimPreset.Playing, 100_000L, 1_800_000L, 300_000L)

        assertEquals(90_000L, state.skipSegments.intro?.startMs)
        assertEquals(150_000L, state.skipSegments.intro?.endMs)
        assertEquals(1_620_000L, state.skipSegments.outroStartMs)
        assertEquals(100_000L, state.runtime.positionMs)
        assertEquals(300_000L, state.runtime.bufferedPositionMs)
    }
}
