package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.EpisodeSeriesInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvEpisodeBrowserTest {
    @Test fun `episodes group and sort numerically with source order as tie breaker`() {
        val browser = browser(
            listOf(
                episode("s10e2", 10, 2), episode("s2e10", 2, 10),
                episode("s2e2-first", 2, 2), episode("s2e2-second", 2, 2),
            ),
        )!!
        assertEquals(listOf(2L, 10L), browser.seasons.map { it.season })
        assertEquals(listOf("s2e2-first", "s2e2-second", "s2e10"), browser.episodesFor(2).map { it.videoId })
        assertEquals(listOf("s10e2"), browser.episodesFor(10).map { it.videoId })
    }

    @Test fun `specials stay distinct and sort before positive seasons`() {
        val browser = browser(listOf(episode("normal", 1, 1), episode("special", 0, 2), episode("un-grouped", null, null)))!!
        assertEquals(listOf("Specials", "Season 1"), browser.seasons.map { it.label })
        assertEquals(1, browser.ungroupedVideoCount)
        assertNull(browser.episodes.first { it.videoId == "un-grouped" }.seriesInfo)
        assertEquals(0L, browser.seasons.first().season)
    }

    @Test fun `movie without episodic series info has no browser`() {
        assertNull(browser(listOf(episode("un-grouped", null, null))))
    }

    @Test fun `default season follows current then continue then progress then first positive`() {
        val episodes = listOf(
            episode("special", 0, 1),
            episode("s2", 2, 1),
            episode("s5-current", 5, 1, current = true, progress = 0.0),
            episode("s3-progress", 3, 1, progress = 25.0),
        )
        assertEquals(5L, browser(episodes, continueId = "s2")!!.defaultSeason)
        assertEquals(2L, browser(episodes.map { if (it.videoId == "s5-current") it.copy(isCurrent = false) else it }, "s2")!!.defaultSeason)
        assertEquals(3L, browser(episodes.filterNot { it.videoId == "s5-current" }.map { if (it.videoId == "s2") it.copy(progress = 0.0) else it })!!.defaultSeason)
        assertEquals(2L, browser(listOf(episode("special", 0, 1), episode("s2", 2, 1)))!!.defaultSeason)
        assertEquals(0L, browser(listOf(episode("special", 0, 1)))!!.defaultSeason)
    }

    @Test fun `current and continue identities remain independent`() {
        val browser = browser(listOf(episode("current", 1, 1, current = true), episode("continue", 1, 2)), "continue")!!
        assertEquals("current", browser.currentVideoId)
        assertEquals("continue", browser.continueWatchingVideoId)
    }

    @Test fun `episode selection filters strictly to selected season`() {
        val browser = browser(listOf(episode("s1", 1, 1), episode("s2", 2, 1)))!!
        assertEquals(listOf("s2"), browser.episodesFor(2).map { it.videoId })
    }

    @Test fun `progress percentage converts to clamped fraction`() {
        assertNull(coreEpisodeProgressFraction(null))
        assertEquals(0f, coreEpisodeProgressFraction(0.0)!!, 0f)
        assertEquals(0.25f, coreEpisodeProgressFraction(25.0)!!, 0f)
        assertEquals(0.5f, coreEpisodeProgressFraction(50.0)!!, 0f)
        assertEquals(1f, coreEpisodeProgressFraction(100.0)!!, 0f)
        assertEquals(1f, coreEpisodeProgressFraction(125.0)!!, 0f)
        assertEquals(0f, coreEpisodeProgressFraction(-25.0)!!, 0f)
    }

    @Test fun `focus restores by video id across updates and clamps after removal`() {
        val initial = listOf(episode("one", 1, 1), episode("two", 1, 2), episode("three", 1, 3))
        val reordered = listOf(initial[2], initial[0], initial[1].copy(progress = 20.0))
        assertEquals(2, restoredEpisodeIndex(reordered, "two", 0))
        assertEquals(1, restoredEpisodeIndex(reordered, "removed", 1))
        assertEquals(0, restoredEpisodeIndex(reordered, "removed", -10))
        assertNull(restoredEpisodeIndex(emptyList(), "one", 0))
    }

    private fun browser(episodes: List<EpisodeOption>, continueId: String? = null) =
        TvEpisodeBrowserUiState.from(episodes, continueId)

    private fun episode(
        id: String,
        season: Long?,
        number: Long?,
        current: Boolean = false,
        progress: Double? = null,
    ) = EpisodeOption(
        videoId = id,
        season = season?.toInt() ?: 0,
        episode = number?.toInt() ?: 0,
        title = id,
        thumbnail = null,
        releaseDate = null,
        watched = false,
        isCurrent = current,
        progress = progress,
        seriesInfo = if (season == null || number == null) null else EpisodeSeriesInfo(season, number),
    )
}
