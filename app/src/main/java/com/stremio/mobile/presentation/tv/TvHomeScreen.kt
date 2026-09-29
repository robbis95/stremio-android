package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.R
import com.stremio.mobile.presentation.tv.components.TvShelfRow
import com.stremio.mobile.presentation.tv.focus.FocusShelf
import com.stremio.mobile.presentation.tv.focus.TvFocusLocation
import com.stremio.mobile.presentation.tv.focus.TvFocusMemory
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.focus.preferredRestoreLocation
import com.stremio.mobile.presentation.tv.focus.resolveFocusLocation
import com.stremio.mobile.presentation.tv.focus.shelfFocusKeys
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
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
    var detailsReturnTarget by remember { mutableStateOf<TvFocusLocation?>(null) }
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
        if (detailsReturnTarget != null) detailsReturnTarget = null
    }

    // Only route activation/restoration drives this. Catalog emissions are observed only while waiting
    // for the one explicit request to become satisfiable; they never restart a completed request.
    LaunchedEffect(isActive, restoreFocusRequestId) {
        if (!isActive) return@LaunchedEffect
        val requested = preferredRestoreLocation(detailsReturnTarget, focusMemory.location)
        val requestedContentKey = requested?.contentKey
        snapshotFlow { Triple(latestShelves, latestKeys, latestLoading) }.first { (currentShelves, keys, loading) ->
            val requestedIndex = requested?.let { keys.indexOf(it.shelfKey) } ?: -1
            val requestedItemExists = requestedIndex >= 0 && currentShelves[requestedIndex].items.any {
                contentFocusKey(it.type, it.id) == requestedContentKey
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

    Column(Modifier.fillMaxSize().padding(top = 34.dp, bottom = 30.dp), verticalArrangement = Arrangement.Center) {
        Row(
            modifier = Modifier.padding(horizontal = TvDimens.safeHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(painterResource(R.drawable.ic_stremio_splash_logo), "Stremio", Modifier.height(28.dp))
            Text("Stremio", style = MaterialTheme.typography.titleMedium, color = TvColors.accent)
        }
        Spacer(Modifier.height(4.dp))
        Text("Home", Modifier.padding(horizontal = TvDimens.safeHorizontal), style = MaterialTheme.typography.headlineLarge, color = TvColors.primaryText)
        Spacer(Modifier.height(TvDimens.titleSpacing))
        LazyColumn(state = verticalState, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TvDimens.shelfSpacing)) {
            itemsIndexed(shelves, key = { index, _ -> shelfKeys[index] }) { index, shelf ->
                Column {
                    Text(shelf.title, Modifier.padding(start = TvDimens.safeHorizontal, top = 8.dp, bottom = 2.dp), style = MaterialTheme.typography.titleLarge, color = TvColors.primaryText)
                    when {
                        shelf.items.isNotEmpty() -> TvShelfRow(
                            shelfKeys[index], shelf.items, isActive, registry, rowStates,
                            onFocused = { content ->
                                if (isActive) {
                                    val location = TvFocusLocation(shelfKeys[index], content, index)
                                    focusedLocation = location
                                    focusMemory.remember(location.shelfKey, location.contentKey, index)
                                }
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
                                val location = TvFocusLocation(shelfKeys[index], content, index)
                                focusMemory.remember(location.shelfKey, location.contentKey, index)
                                detailsReturnTarget = location
                                onOpenDetails(item)
                            },
                        )
                        shelf.isLoading || isBoardLoading -> androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TvColors.accent) }
                        shelf.error != null -> Text(shelf.error, Modifier.padding(horizontal = TvDimens.safeHorizontal, vertical = 24.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (shelves.isEmpty()) item(key = "empty") {
                Text(if (isBoardLoading) "Loading catalogs…" else "No catalogs are available yet.",
                    Modifier.padding(horizontal = TvDimens.safeHorizontal, vertical = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    LaunchedEffect(isActive, pendingRestore) {
        val target = pendingRestore ?: return@LaunchedEffect
        if (isActive) restore(target)
    }
}
