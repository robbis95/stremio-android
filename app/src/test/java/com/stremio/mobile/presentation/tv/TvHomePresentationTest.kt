package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogBehaviorHints
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogLink
import com.stremio.mobile.data.model.CatalogPosterShape
import com.stremio.mobile.data.model.CatalogShelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class TvHomePresentationTest {
    private fun item(
        id: String,
        type: String = "movie",
        name: String = id,
        poster: String? = null,
    ) = CatalogItem(id, type, name, poster, null, null, null)

    @Test
    fun continueWatchingEnrichmentFillsPreviewAndPreservesResumeState() {
        val watching = item("same", type = "series", poster = "cw-poster").copy(
            progress = 0.42f,
            watched = true,
            remainingEpisodes = 3,
            continueWatchingVideoId = "opaque-video-key",
            isContinueWatching = true,
        )
        val rich = item("same", type = "series", poster = "board-poster").copy(
            background = "background",
            releaseInfo = "2025",
            posterShape = CatalogPosterShape.Landscape,
            logo = "logo",
            description = "Real preview description",
            runtime = "42 min",
            links = listOf(CatalogLink("Drama", "genre")),
            inLibrary = true,
            behaviorHints = CatalogBehaviorHints("default", "featured", true),
        )

        val result = enrichContinueWatching(CatalogShelf("Continue Watching", listOf(watching), false), listOf(CatalogShelf("Addon", listOf(rich), false)))
            .items.single()

        assertEquals("cw-poster", result.poster)
        assertEquals("background", result.background)
        assertEquals("2025", result.releaseInfo)
        assertEquals(CatalogPosterShape.Landscape, result.posterShape)
        assertEquals("logo", result.logo)
        assertEquals("Real preview description", result.description)
        assertEquals("42 min", result.runtime)
        assertEquals(listOf(CatalogLink("Drama", "genre")), result.links)
        assertEquals(0.42f, result.progress!!, 0f)
        assertTrue(result.watched)
        assertEquals(3, result.remainingEpisodes)
        assertEquals("opaque-video-key", result.continueWatchingVideoId)
        assertTrue(result.isContinueWatching)
    }

    @Test
    fun continueWatchingWithoutMatchRemainsUnmodifiedAndInventsNoMetadata() {
        val original = item("only-a-title")
        val result = enrichContinueWatching(CatalogShelf("Continue Watching", listOf(original), false), emptyList()).items.single()
        assertEquals(original, result)
        assertNull(result.background)
        assertNull(result.description)
        assertNull(initialHeroCandidate(CatalogShelf("Continue Watching", listOf(original), false), emptyList()))
    }

    @Test
    fun initialHeroPrefersEnrichedContinueWatchingCandidate() {
        val watching = item("cw", type = "other", poster = "real-cw-poster")
        val board = item("board")
        val candidate = initialHeroCandidate(
            CatalogShelf("Continue Watching", listOf(watching), false),
            listOf(CatalogShelf("Real addon title", listOf(board), false)),
        )
        assertEquals("cw", candidate?.id)
        assertEquals("other", candidate?.type)
        assertEquals("Real addon title", buildTvHomePresentation(
            CatalogShelf("Continue Watching", listOf(watching), false),
            listOf(CatalogShelf("Real addon title", listOf(board), false)),
        ).sections.filterIsInstance<TvHomeSection.Shelf>().last().title)
    }

    @Test
    fun initialHeroUsesFirstBoardItemWhenContinueWatchingHasNoUsefulMetadata() {
        val candidate = initialHeroCandidate(
            CatalogShelf("Continue Watching", listOf(item("cw")), false),
            listOf(CatalogShelf("First addon", listOf(item("first")), false), CatalogShelf("Second addon", listOf(item("second")), false)),
        )
        assertEquals("first", candidate?.id)
    }

    @Test
    fun initialHeroIsNullWhenNoRealPreviewExists() {
        assertNull(initialHeroCandidate(CatalogShelf("Continue Watching", isLoading = false), emptyList()))
    }

    @Test
    fun initialHeroPrefersBackgroundOverPosterOnlyContinueWatchingItem() {
        val posterOnly = item("poster-only", poster = "portrait").copy(posterShape = CatalogPosterShape.Poster)
        val backdrop = item("backdrop").copy(background = "real-background")
        assertEquals(
            "backdrop",
            initialHeroCandidate(
                CatalogShelf("Continue Watching", listOf(posterOnly, backdrop), false),
                emptyList(),
            )?.id,
        )
    }

    @Test
    fun landscapeArtworkIsHeroCapable() {
        val landscape = item("landscape", poster = "real-landscape")
            .copy(posterShape = CatalogPosterShape.Landscape)
        assertEquals(3, heroPresentationQuality(landscape))
        assertEquals(TvHeroArtworkTreatment.RichArtwork, heroArtworkTreatment(landscape))
    }

    @Test
    fun descriptionAndLogoImproveHeroSuitabilityOverPosterOnlyArtwork() {
        val posterOnly = item("poster", poster = "portrait")
        val withDescription = item("description").copy(description = "Real description")
        val withLogo = item("logo").copy(logo = "real-logo")

        assertTrue(heroPresentationQuality(withDescription) > heroPresentationQuality(posterOnly))
        assertTrue(heroPresentationQuality(withLogo) > heroPresentationQuality(posterOnly))
        assertEquals("description", initialHeroCandidate(
            CatalogShelf("Continue Watching", listOf(posterOnly), false),
            listOf(CatalogShelf("Board", listOf(withDescription), false)),
        )?.id)
    }

    @Test
    fun heroCapableContinueWatchingItemIsPreferredOverHeroCapableBoardItem() {
        val watching = item("cw", poster = "landscape")
            .copy(posterShape = CatalogPosterShape.Landscape)
        val board = item("board").copy(background = "real-background")

        assertEquals("cw", initialHeroCandidate(
            CatalogShelf("Continue Watching", listOf(watching), false),
            listOf(CatalogShelf("Board", listOf(board), false)),
        )?.id)
    }

    @Test
    fun heroCapableBoardItemIsPreferredOverPosterOnlyContinueWatchingItem() {
        val watching = item("cw", poster = "portrait")
        val board = item("board").copy(logo = "real-logo")

        assertEquals("board", initialHeroCandidate(
            CatalogShelf("Continue Watching", listOf(watching), false),
            listOf(CatalogShelf("Board", listOf(board), false)),
        )?.id)
    }

    @Test
    fun weakContinueWatchingItemRemainsFallbackWhenBoardHasNoRicherContent() {
        val watching = item("cw", poster = "portrait")
        val board = item("board")

        assertEquals("cw", initialHeroCandidate(
            CatalogShelf("Continue Watching", listOf(watching), false),
            listOf(CatalogShelf("Board", listOf(board), false)),
        )?.id)
    }

    @Test
    fun focusedPosterOnlyItemStillBecomesHeroAfterDwell() = runBlocking {
        val focused = item("focused", poster = "portrait")
        val controller = TvHeroPreviewController(CoroutineScope(Dispatchers.Unconfined), item("initial"))

        assertEquals(0, heroPresentationQuality(focused))
        controller.onFocused(focused)
        assertEquals(item("initial"), controller.item.value)
        withTimeout(2_000) {
            while (controller.item.value != focused) delay(10)
        }

        assertEquals(focused, controller.item.value)
    }

    @Test
    fun heroArtworkFallbackClassifiesContainedAndNeutralItems() {
        assertEquals(TvHeroArtworkTreatment.ContainedPoster,
            heroArtworkTreatment(item("portrait", poster = "poster")))
        assertEquals(TvHeroArtworkTreatment.Neutral, heroArtworkTreatment(item("text-only")))
        assertEquals(TvHeroArtworkTreatment.RichArtwork,
            heroArtworkTreatment(item("background").copy(background = "real-background")))
    }

    @Test
    fun heroContinueWatchingAndBoardSectionsHaveExplicitLazyAndSemanticMappings() {
        val home = buildTvHomePresentation(
            CatalogShelf("Continue Watching", listOf(item("cw")), false),
            listOf(CatalogShelf("Board A", listOf(item("a")), false), CatalogShelf("Board B", listOf(item("b")), false)),
        )
        val boardA = home.sections.filterIsInstance<TvHomeSection.Shelf>().first { it.title == "Board A" }
        assertEquals("tv:home-hero", home.sections[0].lazyKey)
        assertEquals(1, home.lazyIndexForShelf(TV_CONTINUE_WATCHING_SHELF_KEY))
        assertEquals(2, home.lazyIndexForShelf(boardA.semanticKey))
        assertEquals(2, home.lazyIndexForBoardShelf(0))
        assertEquals(3, home.lazyIndexForBoardShelf(1))
        assertEquals(0, boardA.boardShelfIndex)
        assertEquals(1, boardA.focusIndex)
        assertTrue(home.focusShelves.first().key == TV_CONTINUE_WATCHING_SHELF_KEY)
    }

    @Test
    fun noContinueWatchingKeepsBoardIndexSeparateFromLazyIndex() {
        val home = buildTvHomePresentation(
            CatalogShelf("Continue Watching", emptyList(), false),
            listOf(CatalogShelf("Board A", listOf(item("a")), false), CatalogShelf("Board B", listOf(item("b")), false)),
        )
        val boardA = home.sections.filterIsInstance<TvHomeSection.Shelf>().first()
        assertEquals(1, home.lazyIndexForBoardShelf(0))
        assertEquals(2, home.lazyIndexForBoardShelf(1))
        assertEquals(0, boardA.boardShelfIndex)
        assertEquals(0, boardA.focusIndex)
        assertEquals(1, home.lazyIndexForShelf(boardA.semanticKey))
    }

    @Test
    fun continueWatchingSemanticIdentityIsStableAndReserved() {
        assertEquals("tv:continue-watching", TV_CONTINUE_WATCHING_SHELF_KEY)
        val home = buildTvHomePresentation(
            CatalogShelf("Custom title", listOf(item("cw")), false),
            emptyList(),
        )
        assertEquals(TV_CONTINUE_WATCHING_SHELF_KEY, home.focusShelves.single().key)
        assertFalse(home.focusShelves.single().key.contains("Custom title"))
    }
}
