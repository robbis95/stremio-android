package com.stremio.mobile.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stremio.mobile.presentation.tv.focus.rememberTvFocusMemory
import com.stremio.mobile.presentation.tv.focus.rememberTvDiscoverFocusMemory
import com.stremio.mobile.presentation.tv.focus.rememberTvLibraryFocusMemory
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import com.stremio.mobile.presentation.tv.theme.TvTheme
import com.stremio.mobile.presentation.viewmodel.MainViewModel

@Composable
internal fun TvApp(viewModel: MainViewModel) {
    val account by viewModel.tvAccount.collectAsStateWithLifecycle()
    val boardShelves by viewModel.tvBoardShelves.collectAsStateWithLifecycle()
    val continueWatching by viewModel.tvContinueWatching.collectAsStateWithLifecycle()
    val detailsUiState by viewModel.tvDetailsUiState.collectAsStateWithLifecycle()
    val streamUiState by viewModel.tvStreamSelection.collectAsStateWithLifecycle()
    val tvPlaybackState by viewModel.tvPlayback.collectAsStateWithLifecycle()
    val profileSettings by viewModel.tvProfileSettings.collectAsStateWithLifecycle()
    val tvLinkState by viewModel.tvAccountLink.collectAsStateWithLifecycle()
    val isRestoring by viewModel.sessionRestoring.collectAsStateWithLifecycle()
    val isBoardLoading by viewModel.tvBoardLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.tvSearchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.tvSearchResults.collectAsStateWithLifecycle()
    val searchShelves by viewModel.tvSearchShelves.collectAsStateWithLifecycle()
    val discoverState by viewModel.tvDiscover.collectAsStateWithLifecycle()
    val libraryState by viewModel.tvLibrary.collectAsStateWithLifecycle()
    var routeName by rememberSaveable { mutableStateOf(TvRoute.Login.name) }
    var detailsOriginName by rememberSaveable { mutableStateOf(TvTopLevelRoute.Home.name) }
    var restoreFocusRequestId by rememberSaveable { mutableIntStateOf(0) }
    var searchFocusRequestId by rememberSaveable { mutableIntStateOf(0) }
    var discoverFocusRequestId by rememberSaveable { mutableIntStateOf(0) }
    var libraryFocusRequestId by rememberSaveable { mutableIntStateOf(0) }
    var episodeFocusRestoreId by rememberSaveable { mutableIntStateOf(0) }
    var episodeFocusRestoreVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsRequested by remember { mutableStateOf(false) }
    val streamFocusMemory = remember { mutableStateMapOf<String, TvStreamFocusMemory>() }
    val focusMemory = rememberTvFocusMemory()
    val discoverFocusMemory = rememberTvDiscoverFocusMemory()
    val libraryFocusMemory = rememberTvLibraryFocusMemory()
    val route = runCatching { TvRoute.valueOf(routeName) }.getOrDefault(TvRoute.Login)
    val detailsOrigin = TvTopLevelRoute.valueOf(detailsOriginName)
    val navRequesters = remember { tvTopLevelDestinations.associateWith { FocusRequester() } }
    val authenticated = account.isAuthenticated

    LaunchedEffect(routeName) {
        if (route == TvRoute.Login) {
            viewModel.startTvAccountLink()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                viewModel.stopTvAccountLink()
            }
        }
    }

    LaunchedEffect(authenticated, isRestoring) {
        if (authenticated) {
            if (route == TvRoute.Login) routeName = TvRoute.Home.name
            viewModel.refreshCatalogs()
        } else if (!isRestoring) {
            routeName = TvRoute.Login.name
        }
    }
    LaunchedEffect(routeName, detailsUiState.details) {
        if (route == TvRoute.Details && detailsUiState.details == null && !detailsRequested) {
            // A recreated activity cannot retain an in-memory detail request; recover to its origin.
            routeName = when (detailsOrigin) {
                TvTopLevelRoute.Home -> TvRoute.Home.name
                TvTopLevelRoute.Discover -> TvRoute.Discover.name
                TvTopLevelRoute.Library -> TvRoute.Library.name
                TvTopLevelRoute.Search -> TvRoute.Search.name
            }
            when (detailsOrigin) {
                TvTopLevelRoute.Home -> restoreFocusRequestId++
                TvTopLevelRoute.Discover -> discoverFocusRequestId++
                TvTopLevelRoute.Library -> libraryFocusRequestId++
                TvTopLevelRoute.Search -> searchFocusRequestId++
            }
        }
    }

    val returnFromDetails = {
        viewModel.closeDetails()
        detailsRequested = false
        routeName = TvRouteState(TvRoute.Details, detailsOrigin).closeDetails().route.name
        when (detailsOrigin) {
            TvTopLevelRoute.Home -> restoreFocusRequestId++
            TvTopLevelRoute.Discover -> discoverFocusRequestId++
            TvTopLevelRoute.Library -> libraryFocusRequestId++
            TvTopLevelRoute.Search -> searchFocusRequestId++
        }
        Unit
    }

    val returnFromStreams = {
        episodeFocusRestoreVideoId = streamUiState.target?.videoId
        if (episodeFocusRestoreVideoId != null) episodeFocusRestoreId++
        viewModel.closeTvStreams()
        routeName = TvRouteState(TvRoute.Streams, detailsOrigin).closeStreams().route.name
        Unit
    }

    BackHandler(enabled = route == TvRoute.Search) {
        routeName = TvRoute.Home.name
        restoreFocusRequestId++
    }
    BackHandler(enabled = route == TvRoute.Discover) {
        routeName = TvRoute.Home.name
        restoreFocusRequestId++
    }
    BackHandler(enabled = route == TvRoute.Library) {
        routeName = TvRoute.Home.name
        restoreFocusRequestId++
    }
    BackHandler(enabled = route == TvRoute.Streams) {
        returnFromStreams()
    }
    BackHandler(enabled = route == TvRoute.Player) {
        viewModel.closeTvPlayback()
        routeName = TvRouteState(TvRoute.Player, detailsOrigin).closePlayer().route.name
    }
    BackHandler(enabled = route == TvRoute.Details) { returnFromDetails() }

    LaunchedEffect(routeName, tvPlaybackState.playbackAttemptId, tvPlaybackState.stage) {
        if (route == TvRoute.Player && (tvPlaybackState.playbackAttemptId == null || tvPlaybackState.stage == TvPlaybackStage.Idle)) {
            routeName = TvRouteState(TvRoute.Player, detailsOrigin).closePlayer().route.name
        }
    }

    TvTheme {
        Box(Modifier.fillMaxSize().background(TvColors.background)) {
            when {
                isRestoring -> TvStartupScreen()
                authenticated && route == TvRoute.Login -> TvStartupScreen()
                route == TvRoute.Login || !authenticated -> TvLoginScreen(
                    account = account,
                    linkState = tvLinkState,
                    onLogin = viewModel::login,
                    onRequestNewLink = viewModel::requestNewTvAccountLink,
                )
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        if (route == TvRoute.Home || route == TvRoute.Discover || route == TvRoute.Library || route == TvRoute.Search) {
                            TvTopNavigation(
                                selected = when (route) {
                                    TvRoute.Home -> TvTopLevelRoute.Home
                                    TvRoute.Discover -> TvTopLevelRoute.Discover
                                    TvRoute.Library -> TvTopLevelRoute.Library
                                    TvRoute.Search -> TvTopLevelRoute.Search
                                    TvRoute.Login, TvRoute.Details, TvRoute.Streams, TvRoute.Player -> error("Top navigation is hidden for $route")
                                },
                                onSelect = { destination ->
                                    if (destination == TvTopLevelRoute.Discover) discoverFocusRequestId = 0
                                    routeName = destination.toRoute().name
                                },
                                onMoveDown = { destination ->
                                    when (destination) {
                                        TvTopLevelRoute.Home -> restoreFocusRequestId++
                                        TvTopLevelRoute.Discover -> discoverFocusRequestId++
                                        TvTopLevelRoute.Library -> libraryFocusRequestId++
                                        TvTopLevelRoute.Search -> searchFocusRequestId++
                                    }
                                },
                                requesters = navRequesters,
                                modifier = Modifier.padding(horizontal = TvDimens.safeHorizontal),
                            )
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            // Both top-level routes stay composed so their own lazy-row scroll
                            // positions survive a route switch for the duration of this session.
                            TvHomeScreen(
                                continueWatching = continueWatching,
                                shelves = boardShelves,
                                isBoardLoading = isBoardLoading,
                                isActive = route == TvRoute.Home,
                                navFocusRequester = navRequesters.getValue(TvTopLevelRoute.Home),
                                restoreFocusRequestId = restoreFocusRequestId,
                                focusMemory = focusMemory,
                                onOpenDetails = { item ->
                                    detailsRequested = true
                                    detailsOriginName = TvTopLevelRoute.Home.name
                                    viewModel.openDetails(item)
                                    routeName = TvRouteState(TvRoute.Home).openDetails().route.name
                                },
                                onBoardShelfVisible = viewModel::onShelfVisible,
                            )
                            if (route == TvRoute.Discover) {
                                TvDiscoverScreen(
                                    state = discoverState,
                                    memory = discoverFocusMemory,
                                    isActive = true,
                                    restoreFocusRequestId = discoverFocusRequestId,
                                    navFocusRequester = navRequesters.getValue(TvTopLevelRoute.Discover),
                                    onSelectFilter = viewModel::selectDiscoverFilter,
                                    onOpenDetails = { item ->
                                        detailsRequested = true
                                        detailsOriginName = TvTopLevelRoute.Discover.name
                                        viewModel.openDetails(item)
                                        routeName = TvRouteState(TvRoute.Discover).openDetails().route.name
                                    },
                                    onLoadNextPage = viewModel::loadDiscoverNextPage,
                                )
                            }
                            if (route == TvRoute.Library) {
                                TvLibraryScreen(
                                    state = libraryState,
                                    memory = libraryFocusMemory,
                                    isActive = true,
                                    restoreFocusRequestId = libraryFocusRequestId,
                                    navFocusRequester = navRequesters.getValue(TvTopLevelRoute.Library),
                                    onSelectFilter = viewModel::selectLibraryFilter,
                                    onOpenDetails = { item ->
                                        detailsRequested = true
                                        detailsOriginName = TvTopLevelRoute.Library.name
                                        viewModel.openDetails(item)
                                        routeName = TvRouteState(TvRoute.Library).openDetails().route.name
                                    },
                                    onLoadNextPage = viewModel::loadLibraryNextPage,
                                )
                            }
                            TvSearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                shelves = searchShelves,
                                isActive = route == TvRoute.Search,
                                restoreFocusRequestId = searchFocusRequestId,
                                navFocusRequester = navRequesters.getValue(TvTopLevelRoute.Search),
                                onQueryChange = viewModel::searchFromTv,
                                onOpenDetails = { item, _ ->
                                    detailsRequested = true
                                    detailsOriginName = TvTopLevelRoute.Search.name
                                    viewModel.openDetails(item)
                                    routeName = TvRouteState(TvRoute.Search).openDetails().route.name
                                },
                            )
                        }
                    }
                    if (route == TvRoute.Details || route == TvRoute.Streams) {
                        TvDetailsScreen(
                            state = detailsUiState,
                            episodeFocusRestoreVideoId = episodeFocusRestoreVideoId,
                            episodeFocusRestoreId = episodeFocusRestoreId,
                            onLibraryAction = viewModel::toggleTvDetailsLibrary,
                            onChooseSource = {
                                val item = detailsUiState.details?.item ?: return@TvDetailsScreen
                                val target = TvStreamTarget.nonEpisodic(item)
                                viewModel.openTvStreams(target)
                                routeName = TvRouteState(TvRoute.Details, detailsOrigin).openStreams().route.name
                            },
                            onEpisodeActivate = { episode ->
                                val item = detailsUiState.details?.item ?: return@TvDetailsScreen
                                val target = TvStreamTarget.episode(item, episode) ?: return@TvDetailsScreen
                                viewModel.openTvStreams(target)
                                routeName = TvRouteState(TvRoute.Details, detailsOrigin).openStreams().route.name
                            },
                            onBack = returnFromDetails,
                        )
                    }
                    if (route == TvRoute.Streams) {
                        TvStreamsScreen(
                            state = streamUiState,
                            focusMemory = streamFocusMemory,
                            onBack = returnFromStreams,
                            onSelectProvider = viewModel::selectTvProvider,
                            onSelectStream = { semanticKey ->
                                viewModel.activateTvStream(semanticKey)
                                routeName = TvRouteState(TvRoute.Streams, detailsOrigin).openPlayer().route.name
                            },
                            onFocusChanged = { targetKey, memory -> streamFocusMemory[targetKey] = memory },
                            onEnableValidationMedia = viewModel::enableTvValidationMedia,
                            onArmIncompatibleValidation = viewModel::armTvIncompatibleConstructionValidation,
                            onArmHoldAfterFirstVisual = viewModel::armTvHoldAfterFirstVisual,
                            onArmMpvRequestedEngine = viewModel::armTvMpvRequestedEngineValidation,
                        )
                    }
                    if (route == TvRoute.Player) {
                        var subtitleStyle by remember(tvPlaybackState.playbackAttemptId, profileSettings) {
                            mutableStateOf(viewModel.tvSubtitleStyle())
                        }
                        TvPlayerScreen(
                            state = tvPlaybackState,
                            player = viewModel.tvPlaybackPlayer(),
                            seekDurationMs = viewModel.tvSeekDurationMs(),
                            onTogglePlayback = viewModel::toggleTvPlayback,
                            onSeek = viewModel::seekTvPlaybackBy,
                            onSeekTo = viewModel::seekTvPlaybackTo,
                            onRetry = viewModel::retryTvPlayback,
                            onAudioTrackSelected = { track ->
                                viewModel.selectTvAudioTrack(track)
                            },
                            onSubtitleTrackSelected = { track ->
                                viewModel.selectTvSubtitleTrack(track)
                            },
                            onSubtitlesDisabled = {
                                viewModel.disableTvSubtitles()
                            },
                            subtitleStyle = subtitleStyle,
                            onSubtitleStyleChanged = { style ->
                                subtitleStyle = style
                                viewModel.rememberTvSubtitleStyle(style)
                            },
                            onBack = {
                                viewModel.closeTvPlayback()
                                routeName = TvRouteState(TvRoute.Player, detailsOrigin).closePlayer().route.name
                            },
                            onPlayNext = { tvPlaybackState.playbackAttemptId?.let(viewModel::playTvNextEpisode) },
                            onDismissNext = { tvPlaybackState.playbackAttemptId?.let(viewModel::dismissTvNextEpisode) },
                        )
                    }
                }
            }
        }
    }
}

private fun TvTopLevelRoute.toRoute(): TvRoute = when (this) {
    TvTopLevelRoute.Home -> TvRoute.Home
    TvTopLevelRoute.Discover -> TvRoute.Discover
    TvTopLevelRoute.Library -> TvRoute.Library
    TvTopLevelRoute.Search -> TvRoute.Search
}
