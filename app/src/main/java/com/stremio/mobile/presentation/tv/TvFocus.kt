package com.stremio.mobile.presentation.tv

import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Focus memory is keyed by the Stremio type/id pair, never by a shelf position. */
@Stable
internal class TvFocusMemory {
    var focusedContentKey by mutableStateOf<String?>(null)
}

@Composable
internal fun rememberTvFocusMemory(): TvFocusMemory = rememberSaveable(
    saver = listSaver(
        save = { listOfNotNull(it.focusedContentKey) },
        restore = { values -> TvFocusMemory().apply { focusedContentKey = values.firstOrNull() } },
    ),
) { TvFocusMemory() }

internal fun contentFocusKey(type: String, id: String): String = "$type:$id"
