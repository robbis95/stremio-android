package com.stremio.mobile.presentation.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Library focus, filters, scroll and pagination are independent from Home, Discover and Search. */
@Stable
internal class TvLibraryFocusMemory {
    var contentKey by mutableStateOf<String?>(null)
    var indexHint by mutableIntStateOf(0)
    var filterGroupKey by mutableStateOf<String?>(null)
    var filterOptionKey by mutableStateOf<String?>(null)
    var filterOptionIndex by mutableIntStateOf(0)
    var focusRegion by mutableStateOf("content")
    var firstVisibleIndex by mutableIntStateOf(0)
    var firstVisibleOffset by mutableIntStateOf(0)
    var lastPaginationTriggerIndex by mutableIntStateOf(-1)
    var paginationSelectionKey by mutableStateOf<String?>(null)
}

@Composable
internal fun rememberTvLibraryFocusMemory(): TvLibraryFocusMemory = rememberSaveable(
    saver = mapSaver(
        save = { memory ->
            mapOf(
                "contentKey" to (memory.contentKey ?: ""),
                "indexHint" to memory.indexHint,
                "filterGroupKey" to (memory.filterGroupKey ?: ""),
                "filterOptionKey" to (memory.filterOptionKey ?: ""),
                "filterOptionIndex" to memory.filterOptionIndex,
                "focusRegion" to memory.focusRegion,
                "firstVisibleIndex" to memory.firstVisibleIndex,
                "firstVisibleOffset" to memory.firstVisibleOffset,
                "lastPaginationTriggerIndex" to memory.lastPaginationTriggerIndex,
                "paginationSelectionKey" to (memory.paginationSelectionKey ?: ""),
            )
        },
        restore = { saved ->
            TvLibraryFocusMemory().apply {
                contentKey = (saved["contentKey"] as? String).orEmpty().ifEmpty { null }
                indexHint = saved["indexHint"] as? Int ?: 0
                filterGroupKey = (saved["filterGroupKey"] as? String).orEmpty().ifEmpty { null }
                filterOptionKey = (saved["filterOptionKey"] as? String).orEmpty().ifEmpty { null }
                filterOptionIndex = saved["filterOptionIndex"] as? Int ?: 0
                focusRegion = saved["focusRegion"] as? String ?: "content"
                firstVisibleIndex = saved["firstVisibleIndex"] as? Int ?: 0
                firstVisibleOffset = saved["firstVisibleOffset"] as? Int ?: 0
                lastPaginationTriggerIndex = saved["lastPaginationTriggerIndex"] as? Int ?: -1
                paginationSelectionKey = (saved["paginationSelectionKey"] as? String).orEmpty().ifEmpty { null }
            }
        },
    ),
) { TvLibraryFocusMemory() }
