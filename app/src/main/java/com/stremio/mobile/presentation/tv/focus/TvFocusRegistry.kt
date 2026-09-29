package com.stremio.mobile.presentation.tv.focus

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester

/** Keeps requester instances stable as catalog emissions append or reorder items. */
@Stable
class TvFocusRegistry {
    private val requesters = mutableMapOf<String, FocusRequester>()
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    fun retain(keys: Set<String>) { requesters.keys.retainAll(keys) }
}
