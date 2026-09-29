package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.tv.focus.shelfFocusKeys

internal enum class TvRoute { Login, Home, Search, Details }

internal enum class TvTopLevelRoute { Home, Search }

internal data class TvRouteState(
    val route: TvRoute,
    val detailsOrigin: TvTopLevelRoute? = null,
)

internal val tvTopLevelDestinations = listOf(TvTopLevelRoute.Home, TvTopLevelRoute.Search)

internal fun TvRouteState.openDetails(): TvRouteState {
    val origin = when (route) {
        TvRoute.Home -> TvTopLevelRoute.Home
        TvRoute.Search -> TvTopLevelRoute.Search
        else -> detailsOrigin ?: TvTopLevelRoute.Home
    }
    return TvRouteState(TvRoute.Details, origin)
}

internal fun TvRouteState.closeDetails(): TvRouteState = TvRouteState(
    route = when (detailsOrigin ?: TvTopLevelRoute.Home) {
        TvTopLevelRoute.Home -> TvRoute.Home
        TvTopLevelRoute.Search -> TvRoute.Search
    },
)

internal fun TvRouteState.select(destination: TvTopLevelRoute): TvRouteState = TvRouteState(
    route = when (destination) {
        TvTopLevelRoute.Home -> TvRoute.Home
        TvTopLevelRoute.Search -> TvRoute.Search
    },
)

internal fun searchShelfFocusKeys(shelves: List<CatalogShelf>): List<String> =
    shelfFocusKeys(shelves).map { "tv:search:$it" }
