package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.focus.FocusShelf
import com.stremio.mobile.presentation.tv.focus.TvFocusLocation
import com.stremio.mobile.presentation.tv.focus.VisibleFocusItem
import com.stremio.mobile.presentation.tv.focus.adjacentFocusableShelf
import com.stremio.mobile.presentation.tv.focus.closestVisibleFocusItem
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.focus.preferredRestoreLocation
import com.stremio.mobile.presentation.tv.focus.resolveFocusLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TvFocusPolicyTest {
    private fun item(id: String) = CatalogItem(id, "movie", id, null, null, null, null)
    private fun shelf(key: String, vararg ids: String, loading: Boolean = false) =
        FocusShelf(key, ids.map(::item), loading)

    @Test fun rememberedContentStillExists() {
        val wanted = TvFocusLocation("series|A#0", contentFocusKey("movie", "two"), 0)
        assertEquals(wanted, resolveFocusLocation(wanted, listOf(shelf("series|A#0", "one", "two")), emptyMap()))
    }

    @Test fun missingContentFallsBackToFirstAfterShelfFinishesLoading() {
        val wanted = TvFocusLocation("a", contentFocusKey("movie", "gone"))
        assertEquals(TvFocusLocation("a", contentFocusKey("movie", "first"), 0),
            resolveFocusLocation(wanted, listOf(shelf("a", "first", "second")), emptyMap()))
    }

    @Test fun missingContentIsRetainedWhileItsShelfIsLoading() {
        val wanted = TvFocusLocation("a", contentFocusKey("movie", "later"))
        assertNull(resolveFocusLocation(wanted, listOf(FocusShelf("a", emptyList(), true), shelf("b", "b1")), emptyMap()))
    }

    @Test fun missingShelfFallsToNearestShelfInPreviousOrder() {
        val wanted = TvFocusLocation("gone", contentFocusKey("movie", "x"))
        val shelves = listOf(shelf("before", "b"), shelf("after", "a"))
        assertEquals(TvFocusLocation("before", contentFocusKey("movie", "b"), 0), resolveFocusLocation(wanted, shelves, emptyMap()))
    }

    @Test fun shelfReorderingPreservesShelfAndContentIdentity() {
        val wanted = TvFocusLocation("b", contentFocusKey("movie", "b2"), 0)
        val shelves = listOf(shelf("b", "b1", "b2"), shelf("a", "a1"))
        assertEquals(wanted, resolveFocusLocation(wanted, shelves, emptyMap()))
    }

    @Test fun insertingShelfBeforeActiveShelfDoesNotChangeLocation() {
        val wanted = TvFocusLocation("c", contentFocusKey("movie", "c1"), 2)
        val shelves = listOf(shelf("new", "n1"), shelf("a", "a1"), shelf("c", "c1"))
        assertEquals(wanted, resolveFocusLocation(wanted, shelves, emptyMap()))
    }

    @Test fun rememberedItemSurvivesItemOrderingChanges() {
        val wanted = TvFocusLocation("a", contentFocusKey("movie", "two"), 0)
        assertEquals(wanted, resolveFocusLocation(wanted, listOf(shelf("a", "two", "one")), emptyMap()))
    }

    @Test fun explicitRouteReturnTargetWinsOverCurrentMemory() {
        val routeTarget = TvFocusLocation("shelf", contentFocusKey("movie", "opened"), 2)
        val currentMemory = TvFocusLocation("shelf", contentFocusKey("movie", "later"), 2)
        assertEquals(routeTarget, preferredRestoreLocation(routeTarget, currentMemory))
    }

    @Test fun currentMemoryIsUsedWithoutRouteReturnTarget() {
        val currentMemory = TvFocusLocation("shelf", contentFocusKey("movie", "remembered"), 1)
        assertEquals(currentMemory, preferredRestoreLocation(null, currentMemory))
    }

    @Test fun spatialTargetChoosesVisibleCardNearestSourceCenterX() {
        val target = closestVisibleFocusItem(310f, listOf(
            VisibleFocusItem("movie:a", 120f, 0),
            VisibleFocusItem("movie:b", 286f, 1),
            VisibleFocusItem("movie:c", 452f, 2),
        ))
        assertEquals("movie:b", target?.key)
    }

    @Test fun exactSpatialDistanceTieChoosesLowerStableRowIndex() {
        val target = closestVisibleFocusItem(200f, listOf(
            VisibleFocusItem("movie:later", 250f, 4),
            VisibleFocusItem("movie:earlier", 150f, 2),
        ))
        assertEquals("movie:earlier", target?.key)
    }

    @Test fun spatialTargetReturnsNullWhenNoTargetCardsAreVisible() {
        assertNull(closestVisibleFocusItem(200f, emptyList()))
    }

    @Test fun ordinarySpatialTargetDoesNotPreferOldRememberedContent() {
        val remembered = "movie:old"
        val visible = listOf(VisibleFocusItem("movie:underneath", 300f, 1))
        assertEquals("movie:underneath", closestVisibleFocusItem(300f, visible)?.key)
        assertFalse(visible.any { it.key == remembered })
    }

    @Test fun explicitRouteRestorationStillPrefersItsSemanticTarget() {
        val routeTarget = TvFocusLocation("shelf", contentFocusKey("movie", "opened"), 0)
        val currentMemory = TvFocusLocation("shelf", contentFocusKey("movie", "later"), 0)
        assertEquals(routeTarget, resolveFocusLocation(
            preferredRestoreLocation(routeTarget, currentMemory), listOf(shelf("shelf", "later", "opened")), emptyMap(),
        ))
    }

    @Test fun adjacentShelfSearchSkipsEmptyShelvesDeterministically() {
        val shelves = listOf(shelf("one", "a"), shelf("loading"), shelf("three", "c"))
        assertEquals("three", adjacentFocusableShelf(shelves, "one", 1)?.key)
        assertEquals("one", adjacentFocusableShelf(shelves, "three", -1)?.key)
        assertNull(adjacentFocusableShelf(shelves, "one", -1))
    }
}
