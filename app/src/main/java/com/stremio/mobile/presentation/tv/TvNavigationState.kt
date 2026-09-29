package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.tv.focus.shelfFocusKeys

internal enum class TvRoute { Login, Home, Discover, Library, Search, Details, Streams, Player }

internal enum class TvTopLevelRoute { Home, Discover, Library, Search }

internal data class TvRouteState(
    val route: TvRoute,
    val detailsOrigin: TvTopLevelRoute? = null,
)

internal val tvTopLevelDestinations = listOf(TvTopLevelRoute.Home, TvTopLevelRoute.Discover, TvTopLevelRoute.Library, TvTopLevelRoute.Search)

internal fun TvRouteState.openDetails(): TvRouteState {
    val origin = when (route) {
        TvRoute.Home -> TvTopLevelRoute.Home
        TvRoute.Discover -> TvTopLevelRoute.Discover
        TvRoute.Library -> TvTopLevelRoute.Library
        TvRoute.Search -> TvTopLevelRoute.Search
        TvRoute.Details, TvRoute.Streams, TvRoute.Player -> checkNotNull(detailsOrigin) { "Details route is missing its explicit origin" }
        TvRoute.Login -> error("Details can only be opened from a top-level content route")
    }
    return TvRouteState(TvRoute.Details, origin)
}

internal fun TvRouteState.closeDetails(): TvRouteState = TvRouteState(
    route = when (checkNotNull(detailsOrigin) { "Details route is missing its explicit origin" }) {
        TvTopLevelRoute.Home -> TvRoute.Home
        TvTopLevelRoute.Discover -> TvRoute.Discover
        TvTopLevelRoute.Library -> TvRoute.Library
        TvTopLevelRoute.Search -> TvRoute.Search
    },
)

internal fun TvRouteState.openStreams(): TvRouteState {
    require(route == TvRoute.Details) { "Streams are nested under Details" }
    return copy(route = TvRoute.Streams)
}

internal fun TvRouteState.closeStreams(): TvRouteState {
    require(route == TvRoute.Streams) { "Only Streams can return to Details" }
    return copy(route = TvRoute.Details)
}

internal fun TvRouteState.openPlayer(): TvRouteState {
    require(route == TvRoute.Streams) { "Player is nested under Streams" }
    return copy(route = TvRoute.Player)
}

internal fun TvRouteState.closePlayer(): TvRouteState {
    require(route == TvRoute.Player) { "Only Player can return to Streams" }
    return copy(route = TvRoute.Streams)
}

internal fun TvRouteState.select(destination: TvTopLevelRoute): TvRouteState = TvRouteState(
    route = when (destination) {
        TvTopLevelRoute.Home -> TvRoute.Home
        TvTopLevelRoute.Discover -> TvRoute.Discover
        TvTopLevelRoute.Library -> TvRoute.Library
        TvTopLevelRoute.Search -> TvRoute.Search
    },
)

internal fun searchShelfFocusKeys(shelves: List<CatalogShelf>): List<String> =
    shelfFocusKeys(shelves).map { "tv:search:$it" }
