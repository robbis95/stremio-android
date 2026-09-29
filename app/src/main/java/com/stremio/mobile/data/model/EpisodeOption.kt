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
)
