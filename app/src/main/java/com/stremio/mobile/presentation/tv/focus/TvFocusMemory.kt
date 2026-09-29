package com.stremio.mobile.presentation.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver

/** A route return point is identified by catalog identity and content identity, never list positions. */
data class TvFocusLocation(val shelfKey: String, val contentKey: String, val shelfIndex: Int? = null)

@Stable
class TvFocusMemory {
    var location by mutableStateOf<TvFocusLocation?>(null)
    private val shelfLocations = mutableStateMapOf<String, String>()

    fun contentForShelf(shelfKey: String): String? = shelfLocations[shelfKey]
    internal fun savedLocations(): Map<String, String> = shelfLocations.toMap()
    internal fun rememberShelf(shelfKey: String, contentKey: String) {
        shelfLocations[shelfKey] = contentKey
    }
    fun remember(shelfKey: String, contentKey: String, shelfIndex: Int? = null) {
        rememberShelf(shelfKey, contentKey)
        location = TvFocusLocation(shelfKey, contentKey, shelfIndex)
    }
}

@Composable
fun rememberTvFocusMemory(): TvFocusMemory = rememberSaveable(
    saver = mapSaver(
        save = { memory ->
            buildMap<String, Any> {
                memory.location?.let { put("activeShelf", it.shelfKey); put("activeContent", it.contentKey); it.shelfIndex?.let { index -> put("activeIndex", index) } }
                memory.savedLocations().forEach { (shelf, content) -> put("shelf:$shelf", content) }
            }
        },
        restore = { values ->
            TvFocusMemory().apply {
                values.filterKeys { it.startsWith("shelf:") }.forEach { (key, value) ->
                    rememberShelf(key.removePrefix("shelf:"), value as String)
                }
                val shelf = values["activeShelf"] as? String
                val content = values["activeContent"] as? String
                if (shelf != null && content != null) location = TvFocusLocation(shelf, content, values["activeIndex"] as? Int)
            }
        },
    ),
) { TvFocusMemory() }

fun contentFocusKey(type: String, id: String): String = "$type:$id"
