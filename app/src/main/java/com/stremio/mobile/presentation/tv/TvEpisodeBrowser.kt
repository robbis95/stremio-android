package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.EpisodeOption

internal data class TvEpisodeSeasonUiState(
    val season: Long,
    val label: String,
    val episodes: List<EpisodeOption>,
)

internal data class TvEpisodeBrowserUiState(
    val episodes: List<EpisodeOption>,
    val seasons: List<TvEpisodeSeasonUiState>,
    val defaultSeason: Long?,
    val currentVideoId: String?,
    val continueWatchingVideoId: String?,
    val ungroupedVideoCount: Int,
) {
    fun episodesFor(season: Long): List<EpisodeOption> =
        seasons.firstOrNull { it.season == season }?.episodes.orEmpty()

    companion object {
        fun from(episodes: List<EpisodeOption>, continueWatchingVideoId: String?): TvEpisodeBrowserUiState? {
            val grouped = episodes.withIndex()
                .filter { it.value.seriesInfo != null }
                .groupBy { it.value.seriesInfo!!.season }
                .toSortedMap()
                .map { (season, indexed) ->
                    TvEpisodeSeasonUiState(
                        season = season,
                        label = if (season == 0L) "Specials" else "Season $season",
                        episodes = indexed.sortedWith(
                            compareBy<IndexedValue<EpisodeOption>> { it.value.seriesInfo?.episode ?: Long.MAX_VALUE }
                                .thenBy { it.value.originalIndex }
                                .thenBy { it.index },
                        ).map { it.value },
                    )
                }
            if (grouped.isEmpty()) return null

            val eligible = episodes.filter { it.seriesInfo != null }
            val current = eligible.firstOrNull { it.isCurrent }
            val continueEpisode = continueWatchingVideoId?.let { id -> eligible.firstOrNull { it.videoId == id } }
            val progressed = eligible.firstOrNull { (it.progress ?: 0.0) > 0.0 }
            val positiveSeason = grouped.firstOrNull { it.season > 0L }
            val defaultSeason = current?.seriesInfo?.season
                ?: continueEpisode?.seriesInfo?.season
                ?: progressed?.seriesInfo?.season
                ?: positiveSeason?.season
                ?: grouped.singleOrNull { it.season == 0L }?.season
                ?: grouped.firstOrNull()?.season

            return TvEpisodeBrowserUiState(
                episodes = episodes,
                seasons = grouped,
                defaultSeason = defaultSeason,
                currentVideoId = current?.videoId,
                continueWatchingVideoId = continueEpisode?.videoId,
                ungroupedVideoCount = episodes.count { it.seriesInfo == null },
            )
        }
    }
}

/** Core episode progress is a percentage (0..100); Compose progress bars require a fraction (0..1). */
internal fun coreEpisodeProgressFraction(progressPercent: Double?): Float? =
    progressPercent?.coerceIn(0.0, 100.0)?.div(100.0)?.toFloat()

/** Restore by semantic identity; when removed, clamp the remembered row index to a stable neighbor. */
internal fun restoredEpisodeIndex(episodes: List<EpisodeOption>, videoId: String?, fallbackIndex: Int): Int? {
    if (episodes.isEmpty()) return null
    episodes.indexOfFirst { it.videoId == videoId }.takeIf { it >= 0 }?.let { return it }
    return fallbackIndex.coerceIn(0, episodes.lastIndex)
}
