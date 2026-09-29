package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val TV_HERO_PREVIEW_DWELL_MS = 150L

/** Keeps preview-only focus feedback out of the Home screen's broad Compose state. */
internal class TvHeroPreviewController(
    private val scope: CoroutineScope,
    initialItem: CatalogItem?,
) {
    val item = androidx.compose.runtime.mutableStateOf(initialItem)
    private var pending: Job? = null
    private var hasFocusedContent = false

    fun updateInitial(item: CatalogItem?) {
        if (!hasFocusedContent) this.item.value = item
    }

    fun onFocused(item: CatalogItem) {
        hasFocusedContent = true
        pending?.cancel()
        pending = scope.launch {
            delay(TV_HERO_PREVIEW_DWELL_MS)
            this@TvHeroPreviewController.item.value = item
        }
    }
}
