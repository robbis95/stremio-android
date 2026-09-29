package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.tv.focus.FocusShelf
import com.stremio.mobile.presentation.tv.focus.TvFocusLocation
import com.stremio.mobile.presentation.tv.focus.contentFocusKey
import com.stremio.mobile.presentation.tv.focus.resolveFocusLocation
import org.junit.Assert.assertEquals
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
}
