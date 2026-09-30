package com.stremio.mobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvSkipSegmentsTest {
    @Test
    fun `normalizes valid Core intervals in playback milliseconds`() {
        assertEquals(
            TvSkipSegments(TvSkipSegment(90_000L, 120_000L), 1_300_000L),
            normalizeTvSkipSegments(90_000L, 120_000L, 1_800_000L, 1_300_000L, 1_500_000L),
        )
    }

    @Test
    fun `rejects malformed intervals and out of range outro`() {
        assertEquals(
            TvSkipSegments(),
            normalizeTvSkipSegments(20L, 20L, 100L, 1_000L, 900L),
        )
        assertEquals(
            TvSkipSegments(),
            normalizeTvSkipSegments(-1L, 20L, 100L, -1L, 900L),
        )
        assertEquals(TvSkipSegments(), normalizeTvSkipSegments(null, null, null, 0L, 900L))
        assertEquals(TvSkipSegments(), normalizeTvSkipSegments(0L, 10L, null, 20L, 0L))
    }

    @Test
    fun `intro button visibility is limited to the half open intro window and seeks to its end`() {
        val segments = TvSkipSegments(intro = TvSkipSegment(10_000L, 20_000L))
        assertNull(tvIntroSkipTarget(segments, 9_999L))
        assertEquals(20_000L, tvIntroSkipTarget(segments, 10_000L))
        assertEquals(20_000L, tvIntroSkipTarget(segments, 19_999L))
        assertNull(tvIntroSkipTarget(segments, 20_000L))
    }

    @Test
    fun `outro button is visible only in valid outro region and seeks to media end`() {
        val segments = TvSkipSegments(outroStartMs = 90_000L)
        assertNull(tvOutroSkipTarget(segments, 89_999L, 100_000L))
        assertEquals(100_000L, tvOutroSkipTarget(segments, 90_000L, 100_000L))
        assertEquals(100_000L, tvOutroSkipTarget(segments, 99_999L, 100_000L))
        assertNull(tvOutroSkipTarget(segments, 100_000L, 100_000L))
        assertNull(tvOutroSkipTarget(TvSkipSegments(outroStartMs = 100_000L), 100_000L, 100_000L))
    }
}
