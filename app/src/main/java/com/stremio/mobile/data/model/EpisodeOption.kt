package com.stremio.mobile.data.model

data class EpisodeOption(
    val videoId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: String?,
    val releaseDate: String?,
    val watched: Boolean,
    val isCurrent: Boolean,
    val overview: String? = null,
    /** Core defines this as watch progress percentage; retain its Double value unchanged. */
    val progress: Double? = null,
    val upcoming: Boolean = false,
    val released: CoreTimestamp? = null,
    /** Null only when Core omitted seriesInfo; numeric season zero remains a real Specials season. */
    val seriesInfo: EpisodeSeriesInfo? = null,
    /** Stable source order used only to break equal or missing episode-number ties. */
    val originalIndex: Int = 0,
)

data class EpisodeSeriesInfo(val season: Long, val episode: Long)
