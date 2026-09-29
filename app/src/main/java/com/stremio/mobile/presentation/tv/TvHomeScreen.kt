package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.tv.components.TvShelfRow
import com.stremio.mobile.presentation.tv.focus.FocusShelf
import com.stremio.mobile.presentation.tv.focus.TvFocusLocation
import com.stremio.mobile.presentation.tv.focus.TvFocusMemory
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.focus.resolveFocusLocation
import com.stremio.mobile.presentation.tv.focus.shelfFocusKeys
import kotlinx.coroutines.flow.first

@Composable
internal fun TvHomeScreen(
    shelves: List<CatalogShelf>, isBoardLoading: Boolean, isActive: Boolean,
    restoreFocusRequestId: Int, focusMemory: TvFocusMemory,
    onOpenDetails: (CatalogItem) -> Unit,
) {
    val shelfKeys = remember(shelves) { shelfFocusKeys(shelves) }
    val focusShelves = remember(shelves, shelfKeys) { shelves.mapIndexed { i, shelf ->
        FocusShelf(shelfKeys[i], shelf.items, shelf.isLoading)
    } }
    val focusable = focusShelves.filter { it.items.isNotEmpty() }
    val verticalState = rememberLazyListState()
    val registry = remember { TvFocusRegistry() }
    val rowStates = remember { mutableStateMapOf<String, LazyListState>() }
    var pendingRestore by remember { mutableStateOf<TvFocusLocation?>(null) }
    var focusedLocation by remember { mutableStateOf<TvFocusLocation?>(null) }
    val latestShelves by rememberUpdatedState(shelves)
    val latestKeys by rememberUpdatedState(shelfKeys)
    val latestFocusShelves by rememberUpdatedState(focusShelves)
    val latestLoading by rememberUpdatedState(isBoardLoading)
    val latestMemory by rememberUpdatedState(focusMemory)

    suspend fun restore(location: TvFocusLocation) {
        val targetShelfIndex = latestKeys.indexOf(location.shelfKey)
        if (targetShelfIndex < 0) return
        verticalState.scrollToItem(targetShelfIndex)
        val row = snapshotFlow { rowStates[location.shelfKey] }.first { it != null }!!
        val shelf = latestShelves.getOrNull(targetShelfIndex) ?: return
        val itemIndex = shelf.items.indexOfFirst { contentFocusKey(it.type, it.id) == location.contentKey }
        if (itemIndex < 0) return
        row.scrollToItem(itemIndex)
        snapshotFlow { row.layoutInfo.visibleItemsInfo.any { it.key == location.contentKey } }.first { it }
        registry.requester("${location.shelfKey}|${location.contentKey}").requestFocus()
        focusedLocation = location
        latestMemory.remember(location.shelfKey, location.contentKey, location.shelfIndex)
        pendingRestore = null
    }

    // Only route activation/restoration drives this. Catalog emissions are observed only while waiting
    // for the one explicit request to become satisfiable; they never restart a completed request.
    LaunchedEffect(isActive, restoreFocusRequestId) {
        if (!isActive) return@LaunchedEffect
        val requested = focusMemory.location
        snapshotFlow { Triple(latestShelves, latestKeys, latestLoading) }.first { (currentShelves, keys, loading) ->
            val requestedIndex = requested?.let { keys.indexOf(it.shelfKey) } ?: -1
            val requestedItemExists = requestedIndex >= 0 && currentShelves[requestedIndex].items.any {
                contentFocusKey(it.type, it.id) == requested.contentKey
            }
            val requestedStillLoading = requestedIndex >= 0 && currentShelves[requestedIndex].isLoading && !requestedItemExists
            requestedItemExists || (!loading && !requestedStillLoading)
        }
        val target = resolveFocusLocation(requested, latestFocusShelves, focusMemory.savedLocations())
        if (target != null) pendingRestore = target
    }

    LaunchedEffect(isActive, pendingRestore, shelfKeys) {
        if (!isActive || pendingRestore != null) return@LaunchedEffect
        // Cleanup only entries that no longer represent any live content key.
        val valid = shelves.flatMapIndexed { i, shelf -> shelf.items.map { "${shelfKeys[i]}|${contentFocusKey(it.type, it.id)}" } }.toSet()
        registry.retain(valid)
    }

    Column(Modifier.fillMaxSize().padding(top = 38.dp, bottom = 26.dp), verticalArrangement = Arrangement.Center) {
        Text("Stremio", Modifier.padding(horizontal = 64.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text("Home", Modifier.padding(horizontal = 64.dp), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = verticalState, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(shelves, key = { index, _ -> shelfKeys[index] }) { index, shelf ->
                Column {
                    Text(shelf.title, Modifier.padding(start = 64.dp, top = 8.dp), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    when {
                        shelf.items.isNotEmpty() -> TvShelfRow(
                            shelfKeys[index], shelf.items, isActive, registry, rowStates,
                            onFocused = { content ->
                                val location = TvFocusLocation(shelfKeys[index], content)
                                focusedLocation = location
                                focusMemory.remember(location.shelfKey, location.contentKey, index)
                            },
                            onVertical = { content, direction ->
                                val neighbors = focusable
                                val current = neighbors.indexOfFirst { it.key == shelfKeys[index] }
                                val next = neighbors.getOrNull(current + direction)
                                if (next != null && isActive) {
                                    val remembered = focusMemory.contentForShelf(next.key)
                                    val item = next.items.firstOrNull { contentFocusKey(it.type, it.id) == remembered } ?: next.items.first()
                                    val target = TvFocusLocation(next.key, contentFocusKey(item.type, item.id), focusShelves.indexOfFirst { it.key == next.key })
                                    pendingRestore = target
                                }
                            },
                            onActivate = { item, content ->
                                focusMemory.remember(shelfKeys[index], content, index)
                                onOpenDetails(item)
                            },
                        )
                        shelf.isLoading || isBoardLoading -> androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        shelf.error != null -> Text(shelf.error, Modifier.padding(horizontal = 64.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (shelves.isEmpty()) item(key = "empty") {
                Text(if (isBoardLoading) "Loading catalogs…" else "No catalogs are available yet.",
                    Modifier.padding(horizontal = 64.dp, vertical = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    LaunchedEffect(isActive, pendingRestore) {
        val target = pendingRestore ?: return@LaunchedEffect
        if (isActive) restore(target)
    }
}
