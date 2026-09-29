package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogShelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSearchPolicyTest {
    @Test fun letterAppends() {
        assertEquals("AM", reduceTvSearchQuery("A", TvSearchKeyAction.Letter('M')))
    }

    @Test fun spaceAppendsOnceAndAvoidsLeadingOrRepeatedWhitespace() {
        assertEquals("A ", reduceTvSearchQuery("A", TvSearchKeyAction.Space))
        assertEquals("A ", reduceTvSearchQuery("A ", TvSearchKeyAction.Space))
        assertEquals("", reduceTvSearchQuery("", TvSearchKeyAction.Space))
    }

    @Test fun backspaceRemovesOneUnicodeCodePointAndIsAnEmptyQueryNoOp() {
        assertEquals("A", reduceTvSearchQuery("A😀", TvSearchKeyAction.Backspace))
        assertEquals("", reduceTvSearchQuery("", TvSearchKeyAction.Backspace))
    }

    @Test fun clearEmptiesQuery() {
        assertEquals("", reduceTvSearchQuery("A TEST", TvSearchKeyAction.Clear))
    }

    @Test fun tvSearchOnlyRunsForTwoNonSpaceUnicodeCodePoints() {
        assertFalse(shouldRunTvSearch(""))
        assertFalse(shouldRunTvSearch("   "))
        assertFalse(shouldRunTvSearch("A"))
        assertFalse(shouldRunTvSearch(" A "))
        assertTrue(shouldRunTvSearch("AM"))
        assertTrue(shouldRunTvSearch("😀x"))
    }

    @Test fun searchShelfIdentityIsStableAndNamespacedFromHome() {
        val shelves = listOf(
            CatalogShelf(title = "Addon Catalog", type = "series", isLoading = false),
            CatalogShelf(title = "Addon Catalog", type = "movie", isLoading = false),
        )
        val first = searchShelfFocusKeys(shelves)
        val recomposed = searchShelfFocusKeys(shelves.map { it.copy(items = it.items.toList()) })

        assertEquals(first, recomposed)
        assertEquals(2, first.distinct().size)
        assertTrue(first.all { it.startsWith("tv:search:") })
        assertFalse(first.any { it.startsWith("tv:home:") })
    }

    @Test fun routeOriginRestoresBothHomeAndSearchDetails() {
        assertEquals(TvRoute.Home, TvRouteState(TvRoute.Home).openDetails().closeDetails().route)
        assertEquals(TvRoute.Discover, TvRouteState(TvRoute.Discover).openDetails().closeDetails().route)
        assertEquals(TvRoute.Search, TvRouteState(TvRoute.Search).openDetails().closeDetails().route)
    }

    @Test fun onlyWiredTopLevelDestinationsAreExposed() {
        assertEquals(listOf(TvTopLevelRoute.Home, TvTopLevelRoute.Discover, TvTopLevelRoute.Library, TvTopLevelRoute.Search), tvTopLevelDestinations)
        assertEquals(TvRoute.Discover, TvRouteState(TvRoute.Home).select(TvTopLevelRoute.Discover).route)
        assertEquals(TvRoute.Search, TvRouteState(TvRoute.Home).select(TvTopLevelRoute.Search).route)
    }
}
