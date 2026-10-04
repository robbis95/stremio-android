package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.StreamOption

/** Release implementation contains no test media identity or action data. */
internal object TvRawTorrentFixtureSource {
    fun target(): TvStreamTarget? = null

    fun create(target: TvStreamTarget): StreamOption? = null

    fun mustUseCoreResolver(option: StreamOption): Boolean = false
}
