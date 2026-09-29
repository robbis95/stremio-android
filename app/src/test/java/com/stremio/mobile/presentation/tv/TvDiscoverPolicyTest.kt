package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDiscoverPolicyTest {
    private fun option(name: String, selected: Boolean = false) = DiscoverSelectableRequest(selected, name)

    @Test fun existingCoreSelectedRequestWins() {
        assertEquals("core-current", preferredDiscoverRequest("core-current", listOf(option("selected-type", true)), listOf(option("selected-catalog", true))))
    }

    @Test fun selectedTypeWinsWhenCoreHasNoSelectedRequest() {
        assertEquals("series", preferredDiscoverRequest(null, listOf(option("movie"), option("series", true)), listOf(option("catalog", true))))
    }

    @Test fun selectedCatalogIsFallbackAfterSelectedType() {
        assertEquals("catalog", preferredDiscoverRequest(null, emptyList(), listOf(option("catalog", true))))
    }

    @Test fun firstAvailableTypeIsFallback() {
        assertEquals("anime", preferredDiscoverRequest(null, listOf(option("anime"), option("tv")), emptyList()))
    }

    @Test fun firstAvailableCatalogIsFinalFallback() {
        assertEquals("addon catalog", preferredDiscoverRequest(null, emptyList(), listOf(option("addon catalog"))))
    }

    @Test fun unselectedMovieDoesNotOverrideSelectedCustomType() {
        assertEquals("anime", preferredDiscoverRequest(null, listOf(option("movie"), option("anime", true)), emptyList()))
    }

    @Test fun filterPresentationMapsTypeCatalogAndArbitraryExtraAndOmitsEmptyGroups() {
        val mapped = mapDiscoverFilterGroups(
            types = discoverTypeInputs(listOf(DiscoverFilterOptionInput("Anime", true, "type-request"))),
            catalogs = discoverCatalogInputs(listOf(DiscoverFilterOptionInput("My Real Addon Catalog", true, "catalog-request"))),
            extras = listOfNotNull(
                discoverExtraInput("content_rating", listOf(Triple(null, true, "all"), Triple("PG-13", false, "pg13"))),
                discoverExtraInput("empty", emptyList()),
            ),
        )
        assertEquals(listOf(TvDiscoverFilterKind.Type, TvDiscoverFilterKind.Catalog, TvDiscoverFilterKind.Extra), mapped.map { it.kind })
        assertEquals("Anime", mapped[0].options.single().label)
        assertEquals("My Real Addon Catalog", mapped[1].options.single().label)
        assertEquals("Content Rating", mapped[2].label)
        assertEquals(listOf("All", "PG-13"), mapped[2].options.map { it.label })
        assertEquals("all", mapped[2].options.first().request)
    }

    @Test fun typeHumanizationKeepsUnknownTypesRepresentable() {
        assertEquals("Movie", humanizeDiscoverType("movie"))
        assertEquals("Series", humanizeDiscoverType("series"))
        assertEquals("Live Channel", humanizeDiscoverType("live_channel"))
    }

    @Test fun discoverContentIdentityUsesTypeAndIdAndSurvivesObjectRecreation() {
        val first = CatalogItem("abc", "anime", "A", null, null, null, null)
        val recreated = first.copy(name = "Renamed title")
        assertEquals(discoverContentKeys(listOf(first)), discoverContentKeys(listOf(recreated)))
        assertEquals("anime:abc", discoverContentKeys(listOf(first)).single())
    }

    @Test fun disappearingDiscoverItemUsesClampedIndexFallback() {
        assertEquals("movie:replacement", resolveDiscoverFocusKey(listOf("movie:replacement"), "movie:gone", 9))
        assertNull(resolveDiscoverFocusKey(emptyList(), "movie:gone", 0))
    }

    @Test fun paginationRequestIdentityIsAcceptedOnlyOnce() {
        val tracker = DiscoverPageRequestTracker()
        assertTrue(tracker.tryMark("request-page-2"))
        assertFalse(tracker.tryMark("request-page-2"))
        assertTrue(tracker.tryMark("request-page-3"))
        assertFalse(tracker.tryMark(null))
    }

    @Test fun gridVerticalMovementKeepsColumnAndClampsToPartialLastRow() {
        assertEquals(8, discoverGridVerticalTarget(index = 3, itemCount = 12, columns = 5, direction = 1))
        assertEquals(9, discoverGridVerticalTarget(index = 4, itemCount = 12, columns = 5, direction = 1))
        assertEquals(2, discoverGridVerticalTarget(index = 7, itemCount = 12, columns = 5, direction = -1))
        assertNull(discoverGridVerticalTarget(index = 2, itemCount = 12, columns = 5, direction = -1))
    }
}
