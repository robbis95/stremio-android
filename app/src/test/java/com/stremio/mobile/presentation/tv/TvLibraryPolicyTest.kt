package com.stremio.mobile.presentation.tv

import com.stremio.core.models.LibraryWithFilters
import com.stremio.mobile.data.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLibraryPolicyTest {
    @Test
    fun `library types map null known and custom Core values`() {
        assertEquals("All", libraryTypeLabel(null))
        assertEquals("Movie", libraryTypeLabel("movie"))
        assertEquals("Series", libraryTypeLabel("series"))
        assertEquals("Audio Book", libraryTypeLabel("audio_book"))
    }

    @Test
    fun `every Core sort has a deterministic user-facing label`() {
        assertEquals(
            listOf("Last watched", "Name A–Z", "Name Z–A", "Most watched", "Watched first", "Unwatched first"),
            LibraryWithFilters.Sort.values.map(::librarySortLabel),
        )
    }

    @Test
    fun `top level destinations and Library details origin are exhaustive`() {
        assertEquals(
            listOf(TvTopLevelRoute.Home, TvTopLevelRoute.Discover, TvTopLevelRoute.Library, TvTopLevelRoute.Search),
            tvTopLevelDestinations,
        )
        val details = TvRouteState(TvRoute.Library).openDetails()
        assertEquals(TvRoute.Details, details.route)
        assertEquals(TvTopLevelRoute.Library, details.detailsOrigin)
        assertEquals(TvRoute.Library, details.closeDetails().route)
        assertNull(details.closeDetails().detailsOrigin)
    }

    @Test
    fun `same Library selection keeps current order while refreshing fields and appending new items`() {
        val selection = LibrarySelectionIdentity("movie", LibraryWithFilters.Sort.NAME)
        val previous = TvLibraryOrderSnapshot(selection, listOf(item("a", "Old A"), item("b", "Old B")))

        val result = stableLibraryOrder(
            previous,
            selection,
            listOf(item("b", "Updated B"), item("a", "Updated A"), item("c", "New C")),
        )

        assertEquals(listOf("a", "b", "c"), result.items.map { it.id })
        assertEquals(listOf("Updated A", "Updated B", "New C"), result.items.map { it.name })
    }

    @Test
    fun `removed Library items disappear without disturbing the surviving order`() {
        val selection = LibrarySelectionIdentity(null, LibraryWithFilters.Sort.LAST_WATCHED)
        val previous = TvLibraryOrderSnapshot(selection, listOf(item("a"), item("b"), item("c")))

        val result = stableLibraryOrder(previous, selection, listOf(item("c"), item("a")))

        assertEquals(listOf("a", "c"), result.items.map { it.id })
    }

    @Test
    fun `explicit type or sort change adopts Core order`() {
        val previousSelection = LibrarySelectionIdentity("movie", LibraryWithFilters.Sort.NAME)
        val previous = TvLibraryOrderSnapshot(previousSelection, listOf(item("a"), item("b")))
        val coreOrder = listOf(item("b"), item("a"))

        val typeChanged = stableLibraryOrder(
            previous,
            LibrarySelectionIdentity("series", LibraryWithFilters.Sort.NAME),
            coreOrder,
        )
        val sortChanged = stableLibraryOrder(
            previous,
            LibrarySelectionIdentity("movie", LibraryWithFilters.Sort.TIMES_WATCHED),
            coreOrder,
        )

        assertEquals(listOf("b", "a"), typeChanged.items.map { it.id })
        assertEquals(listOf("b", "a"), sortChanged.items.map { it.id })
    }

    @Test
    fun `pagination page does not change the logical type and sort snapshot`() {
        val firstPage = request(type = "series", sort = LibraryWithFilters.Sort.LAST_WATCHED, page = 1)
        val nextPage = request(type = "series", sort = LibraryWithFilters.Sort.LAST_WATCHED, page = 2)
        assertEquals(firstPage.selectionIdentity(), nextPage.selectionIdentity())

        val first = listOf(item("a"), item("b"))
        val snapshot = stableLibraryOrder(TvLibraryOrderSnapshot(null, emptyList()), firstPage.selectionIdentity(), first)
        val expanded = stableLibraryOrder(
            snapshot,
            nextPage.selectionIdentity(),
            listOf(item("b"), item("a"), item("c")),
        )
        assertEquals(listOf("a", "b", "c"), expanded.items.map { it.id })
    }

    @Test
    fun `disappearing focus falls back to the nearest prior visible index`() {
        assertEquals("series:c", resolveLibraryFocusKey(listOf("series:a", "series:c"), "series:b", 1))
        assertEquals("series:c", resolveLibraryFocusKey(listOf("series:a", "series:c"), "series:gone", 8))
        assertNull(resolveLibraryFocusKey(emptyList(), "series:gone", 0))
    }

    @Test
    fun `pagination waits for focus near end and advances trigger only after focus moves`() {
        assertNull(libraryPageTriggerIndex(focusedIndex = 2, itemCount = 15, columns = 5, lastTriggerIndex = -1))
        assertEquals(10, libraryPageTriggerIndex(focusedIndex = 10, itemCount = 15, columns = 5, lastTriggerIndex = -1))
        assertNull(libraryPageTriggerIndex(focusedIndex = 10, itemCount = 15, columns = 5, lastTriggerIndex = 10))
        assertEquals(15, libraryPageTriggerIndex(focusedIndex = 15, itemCount = 20, columns = 5, lastTriggerIndex = 10))
    }

    @Test
    fun `pagination request identity is de-duplicated`() {
        val tracker = LibraryPageRequestTracker()
        assertTrue(tracker.tryMark("request-page-2"))
        assertFalse(tracker.tryMark("request-page-2"))
        assertTrue(tracker.tryMark("request-page-3"))
        assertFalse(tracker.tryMark(null))
    }

    private fun request(type: String?, sort: LibraryWithFilters.Sort, page: Long) =
        LibraryWithFilters.LibraryRequest(type = type, sort = sort, page = page)

    private fun item(id: String, name: String = id) = CatalogItem(
        id = id,
        type = "series",
        name = name,
        poster = null,
        background = null,
        releaseInfo = null,
        imdbRating = null,
    )
}
