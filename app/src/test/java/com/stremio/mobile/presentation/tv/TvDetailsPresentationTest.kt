package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogPosterShape
import com.stremio.mobile.data.model.MetaDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDetailsPresentationTest {
    @Test fun `artwork mode uses metadata shape and real background`() {
        assertEquals(TvDetailsArtworkMode.RichBackdrop, classifyDetailsArtwork(item(background = "backdrop")))
        assertEquals(TvDetailsArtworkMode.LandscapeArtwork, classifyDetailsArtwork(item(poster = "landscape", shape = CatalogPosterShape.Landscape)))
        assertEquals(TvDetailsArtworkMode.ContainedPoster, classifyDetailsArtwork(item(poster = "portrait", shape = CatalogPosterShape.Poster)))
        assertEquals(TvDetailsArtworkMode.ContainedPoster, classifyDetailsArtwork(item(poster = "square", shape = CatalogPosterShape.Square)))
        assertEquals(TvDetailsArtworkMode.TextOnly, classifyDetailsArtwork(item()))
    }

    @Test fun `metadata line includes only real nonempty distinct facts`() {
        val details = MetaDetails(
            item = item(type = "movie", releaseInfo = "2024", runtime = "1h 45m", imdbRating = "8.2"),
            year = "2024",
        )
        assertEquals("2024  •  1h 45m  •  Movie  •  IMDb 8.2", detailsMetadataLine(details))

        val sparse = MetaDetails(item = item(type = "custom-type"))
        assertEquals("Custom Type", detailsMetadataLine(sparse))
        assertFalse(detailsMetadataLine(sparse).contains("••"))
    }

    @Test fun `Library membership requires both type and id`() {
        val target = item(id = "shared", type = "series")
        assertTrue(isDetailsItemInLibrary(target, listOf(item(id = "shared", type = "series")), fallback = false))
        assertFalse(isDetailsItemInLibrary(target, listOf(item(id = "shared", type = "movie")), fallback = false))
    }

    @Test fun `live Library result overrides stale preview membership`() {
        val stalePreview = item(id = "shared", type = "series").copy(inLibrary = true)
        assertFalse(
            isDetailsItemInLibrary(
                stalePreview,
                libraryItems = emptyList(),
                fallback = true,
                libraryIsAuthoritative = true,
            ),
        )
        assertTrue(
            isDetailsItemInLibrary(
                stalePreview,
                libraryItems = emptyList(),
                fallback = true,
                libraryIsAuthoritative = false,
            ),
        )
    }

    @Test fun `loading details retains the real preview item`() {
        val preview = item(id = "tt99", name = "Visible title", background = "preview-art", poster = "poster")
        val loading = detailsWhileLoading(preview)
        assertTrue(loading.isLoading)
        assertEquals(preview, loading.item)
    }

    @Test fun `all supported top level origins open and close Details`() {
        assertEquals(
            listOf(TvTopLevelRoute.Home, TvTopLevelRoute.Discover, TvTopLevelRoute.Library, TvTopLevelRoute.Search),
            tvTopLevelDestinations,
        )
        tvTopLevelDestinations.forEach { origin ->
            val source = TvRouteState(
                route = when (origin) {
                    TvTopLevelRoute.Home -> TvRoute.Home
                    TvTopLevelRoute.Discover -> TvRoute.Discover
                    TvTopLevelRoute.Library -> TvRoute.Library
                    TvTopLevelRoute.Search -> TvRoute.Search
                },
            )
            assertEquals(source.route, source.openDetails().closeDetails().route)
        }
    }

    private fun item(
        id: String = "id",
        type: String = "series",
        name: String = "Title",
        poster: String? = null,
        background: String? = null,
        releaseInfo: String? = null,
        runtime: String? = null,
        imdbRating: String? = null,
        shape: CatalogPosterShape? = null,
    ) = CatalogItem(
        id = id,
        type = type,
        name = name,
        poster = poster,
        background = background,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        posterShape = shape,
        runtime = runtime,
    )
}
