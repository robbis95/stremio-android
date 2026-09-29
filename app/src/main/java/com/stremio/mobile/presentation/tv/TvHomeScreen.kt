package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.tv.components.TvRowScrollPosition
import com.stremio.mobile.presentation.tv.components.TvShelfRow
import com.stremio.mobile.presentation.tv.focus.TvFocusLocation
import com.stremio.mobile.presentation.tv.focus.TvFocusMemory
import com.stremio.mobile.presentation.tv.focus.TvFocusRegistry
import com.stremio.mobile.presentation.tv.focus.VisibleFocusItem
import com.stremio.mobile.presentation.tv.focus.adjacentFocusableShelf
import com.stremio.mobile.presentation.tv.focus.closestVisibleFocusItem
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.focus.preferredRestoreLocation
import com.stremio.mobile.presentation.tv.focus.resolveFocusLocation
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private data class PendingSpatialTraversal(val shelfKey: String, val anchorX: Float, val direction: Int)

@Composable
internal fun TvHomeScreen(
    continueWatching: CatalogShelf,
    shelves: List<CatalogShelf>,
    isBoardLoading: Boolean,
    isActive: Boolean,
    restoreFocusRequestId: Int,
    focusMemory: TvFocusMemory,
    onOpenDetails: (CatalogItem) -> Unit,
    onBoardShelfVisible: (Int) -> Unit,
) {
    val presentation = remember(continueWatching, shelves) {
        buildTvHomePresentation(continueWatching, shelves)
    }
    val focusShelves = presentation.focusShelves
    val heroController = rememberHeroController(
        initial = (presentation.sections.firstOrNull() as? TvHomeSection.Hero)?.initialItem,
    )
    val verticalState = rememberLazyListState()
    val registry = remember { TvFocusRegistry() }
    val rowStates = remember { mutableStateMapOf<String, LazyListState>() }
    val rowScrollPositions = remember { mutableMapOf<String, TvRowScrollPosition>() }
    var pendingRestore by remember { mutableStateOf<TvFocusLocation?>(null) }
    var focusedLocation by remember { mutableStateOf<TvFocusLocation?>(null) }
    var detailsReturnTarget by remember { mutableStateOf<TvFocusLocation?>(null) }
    val latestPresentation by rememberUpdatedState(presentation)
    val latestFocusShelves by rememberUpdatedState(focusShelves)
    val latestMemory by rememberUpdatedState(focusMemory)
    val density = LocalDensity.current
    val notifiedBoardShelves = remember { mutableSetOf<String>() }
    var pendingTraversal by remember { mutableStateOf<PendingSpatialTraversal?>(null) }

    suspend fun restore(location: TvFocusLocation) {
        val focusShelfIndex = latestFocusShelves.indexOfFirst { it.key == location.shelfKey }
        val lazyIndex = latestPresentation.lazyIndexForShelf(location.shelfKey)
        if (focusShelfIndex < 0 || lazyIndex < 0) return
        val section = latestPresentation.sections.getOrNull(lazyIndex) ?: return
        if (verticalState.layoutInfo.visibleItemsInfo.none { it.key == section.lazyKey }) {
            verticalState.scrollToItem(lazyIndex)
        }
        val row = snapshotFlow { rowStates[location.shelfKey] }.first { it != null }!!
        val shelf = latestPresentation.shelf(location.shelfKey) ?: return
        val itemIndex = shelf.items.indexOfFirst { contentFocusKey(it.type, it.id) == location.contentKey }
        if (itemIndex < 0) return
        if (row.layoutInfo.visibleItemsInfo.none { it.key == location.contentKey }) row.scrollToItem(itemIndex)
        snapshotFlow { row.layoutInfo.visibleItemsInfo.any { it.key == location.contentKey } }.first { it }
        registry.requester("${location.shelfKey}|${location.contentKey}").requestFocus()
        focusedLocation = location.copy(shelfIndex = focusShelfIndex)
        latestMemory.remember(location.shelfKey, location.contentKey, focusShelfIndex)
        pendingRestore = null
        if (detailsReturnTarget != null) detailsReturnTarget = null
    }

    LaunchedEffect(presentation) {
        heroController.updateInitial((presentation.sections.firstOrNull() as? TvHomeSection.Hero)?.initialItem)
    }

    // Only route activation/restoration drives this. Catalog emissions are observed only while waiting
    // for the one explicit request to become satisfiable; they never restart a completed request.
    LaunchedEffect(isActive, restoreFocusRequestId) {
        if (!isActive) return@LaunchedEffect
        val requested = preferredRestoreLocation(detailsReturnTarget, focusMemory.location)
        val requestedContentKey = requested?.contentKey
        snapshotFlow { latestPresentation to isBoardLoading }.first { (current, loading) ->
            if (requested == null) {
                current.focusShelves.any { it.items.isNotEmpty() } ||
                    (!loading && current.focusShelves.none { it.isLoading })
            } else {
                val requestedShelf = current.shelf(requested.shelfKey)
                val requestedItemExists = requestedShelf?.items?.any {
                    contentFocusKey(it.type, it.id) == requestedContentKey
                } == true
                val requestedStillLoading = requestedShelf?.let { it.isLoading && !requestedItemExists } == true
                requestedItemExists || (!loading && !requestedStillLoading)
            }
        }
        val target = resolveFocusLocation(requested, latestFocusShelves, focusMemory.savedLocations())
        if (target != null) pendingRestore = target
    }

    LaunchedEffect(isActive, pendingRestore, presentation.sections) {
        if (!isActive || pendingRestore != null) return@LaunchedEffect
        val valid = focusShelves.flatMap { shelf ->
            shelf.items.map { "${shelf.key}|${contentFocusKey(it.type, it.id)}" }
        }.toSet()
        registry.retain(valid)
    }

    // Report each real board shelf once when it enters or approaches the viewport. The semantic
    // section carries the original board index, independent of hero/CW lazy-list positions.
    LaunchedEffect(isActive, presentation.sections) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow {
            val visible = verticalState.layoutInfo.visibleItemsInfo
            val visibleIndices = visible.map { it.index }.toSet()
            val nextIndex = visible.maxOfOrNull { it.index }?.plus(1)
            visibleIndices + listOfNotNull(nextIndex)
        }.distinctUntilChanged().collect { nearbyIndices ->
            nearbyIndices.sorted().forEach { lazyIndex ->
                val section = presentation.sections.getOrNull(lazyIndex) as? TvHomeSection.Shelf ?: return@forEach
                val boardIndex = section.boardShelfIndex ?: return@forEach
                if (notifiedBoardShelves.add(section.semanticKey)) onBoardShelfVisible(boardIndex)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(top = TvDimens.homeTopInset, bottom = TvDimens.homeBottomInset),
        verticalArrangement = Arrangement.Top,
    ) {
        LazyColumn(
            state = verticalState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(TvDimens.shelfSpacing),
        ) {
            items(presentation.sections, key = { it.lazyKey }, contentType = { section ->
                when (section) {
                    is TvHomeSection.Hero -> "hero"
                    is TvHomeSection.Shelf -> if (section.isContinueWatching) "continue-watching" else "board-shelf"
                }
            }) { section ->
                when (section) {
                    is TvHomeSection.Hero -> TvHomeHero(controller = heroController, isBoardLoading = isBoardLoading)
                    is TvHomeSection.Shelf -> {
                        if (section.items.isNotEmpty() || section.isLoading || section.error != null) {
                            Column {
                                Text(
                                    section.title,
                                    Modifier.padding(
                                        start = TvDimens.safeHorizontal,
                                        top = TvDimens.shelfTitleTop,
                                        bottom = TvDimens.shelfTitleBottom,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TvColors.primaryText,
                                )
                                when {
                                    section.items.isNotEmpty() -> TvShelfRow(
                                        shelfKey = section.semanticKey,
                                        items = section.items,
                                        active = isActive,
                                        registry = registry,
                                        rowStates = rowStates,
                                        rowScrollPositions = rowScrollPositions,
                                        continueWatching = section.isContinueWatching,
                                        onFocused = { content, item ->
                                            if (isActive) {
                                                val location = TvFocusLocation(section.semanticKey, content, section.focusIndex)
                                                focusedLocation = location
                                                focusMemory.remember(location.shelfKey, location.contentKey, section.focusIndex)
                                                heroController.onFocused(item)
                                            }
                                        },
                                        onVertical = { content, direction ->
                                            if (isActive) {
                                                val sourceInfo = rowStates[section.semanticKey]?.layoutInfo?.visibleItemsInfo
                                                    ?.firstOrNull { it.key == content }
                                                val next = adjacentFocusableShelf(focusShelves, section.semanticKey, direction)
                                                if (sourceInfo != null && next != null) {
                                                    pendingTraversal = PendingSpatialTraversal(
                                                        next.key,
                                                        sourceInfo.offset + sourceInfo.size / 2f,
                                                        direction,
                                                    )
                                                }
                                            }
                                        },
                                        onActivate = { item, content ->
                                            val location = TvFocusLocation(section.semanticKey, content, section.focusIndex)
                                            focusMemory.remember(location.shelfKey, location.contentKey, section.focusIndex)
                                            detailsReturnTarget = location
                                            onOpenDetails(item)
                                        },
                                    )
                                    section.isLoading || isBoardLoading -> Box(
                                        Modifier.fillMaxWidth().height(58.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        CircularProgressIndicator(
                                            color = TvColors.accent,
                                            modifier = Modifier.padding(start = TvDimens.safeHorizontal).height(22.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                    section.error != null -> Text(
                                        section.error,
                                        Modifier.padding(horizontal = TvDimens.safeHorizontal, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (presentation.sections.size == 1 && isBoardLoading) {
                item(key = "tv:home-empty-loading") {
                    Text(
                        "Loading your catalogs…",
                        Modifier.padding(horizontal = TvDimens.safeHorizontal, vertical = 10.dp),
                        color = TvColors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    LaunchedEffect(isActive, pendingRestore) {
        val target = pendingRestore ?: return@LaunchedEffect
        if (isActive) restore(target)
    }
    LaunchedEffect(isActive, pendingTraversal) {
        val target = pendingTraversal ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        val sourceFocusIndex = focusedLocation?.let { location ->
            latestFocusShelves.indexOfFirst { it.key == location.shelfKey }
        } ?: -1
        val targetFocusIndex = latestFocusShelves.indexOfFirst { it.key == target.shelfKey }
        val targetLazyIndex = latestPresentation.lazyIndexForShelf(target.shelfKey)
        if (targetFocusIndex < 0 || targetLazyIndex < 0) {
            pendingTraversal = null
            return@LaunchedEffect
        }
        val sourceLazyIndex = focusedLocation?.let { latestPresentation.lazyIndexForShelf(it.shelfKey) } ?: -1
        val sourceSection = latestPresentation.sections.getOrNull(sourceLazyIndex)
        val sourceInfo = verticalState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == sourceSection?.lazyKey }
        val shelfStepPx = (sourceInfo?.size ?: 1) + with(density) { TvDimens.shelfSpacing.roundToPx() }
        var steps = 0
        val maxSteps = (targetFocusIndex - sourceFocusIndex).let { kotlin.math.abs(it) }.coerceAtLeast(1)
        while (verticalState.layoutInfo.visibleItemsInfo.none { it.key == latestPresentation.sections[targetLazyIndex].lazyKey } && steps < maxSteps) {
            verticalState.animateScrollBy((shelfStepPx * target.direction).toFloat())
            steps++
        }

        val row = snapshotFlow { rowStates[target.shelfKey] }.first { it != null }!!
        val visibleItems = snapshotFlow { row.layoutInfo.visibleItemsInfo.toList() }.first { it.isNotEmpty() }
        val spatialTarget = closestVisibleFocusItem(
            target.anchorX,
            visibleItems.mapIndexed { fallbackIndex, info ->
                VisibleFocusItem(info.key as String, info.offset + info.size / 2f, info.index.takeIf { it >= 0 } ?: fallbackIndex)
            },
        )
        if (spatialTarget != null) {
            registry.requester("${target.shelfKey}|${spatialTarget.key}").requestFocus()
            val resolved = TvFocusLocation(target.shelfKey, spatialTarget.key, targetFocusIndex)
            focusedLocation = resolved
            latestMemory.remember(resolved.shelfKey, resolved.contentKey, targetFocusIndex)
        }
        pendingTraversal = null
    }
}

@Composable
private fun rememberHeroController(initial: CatalogItem?): TvHeroPreviewController {
    val scope = rememberCoroutineScope()
    return remember(scope) { TvHeroPreviewController(scope, initial) }
}
