package com.stremio.mobile.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stremio.mobile.core.theme.StremioMobileTheme
import com.stremio.mobile.presentation.viewmodel.MainViewModel

private enum class TvRoute { Login, Home, Details }

@Composable
internal fun TvApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tvLinkState by viewModel.tvAccountLink.collectAsStateWithLifecycle()
    val isRestoring by viewModel.sessionRestoring.collectAsStateWithLifecycle()
    val isBoardLoading by viewModel.tvBoardLoading.collectAsStateWithLifecycle()
    var routeName by rememberSaveable { mutableStateOf(TvRoute.Login.name) }
    var detailsRequested by remember { mutableStateOf(false) }
    val focusMemory = rememberTvFocusMemory()
    val route = runCatching { TvRoute.valueOf(routeName) }.getOrDefault(TvRoute.Login)
    val authenticated = uiState.account.isAuthenticated

    LaunchedEffect(routeName) {
        if (routeName == TvRoute.Login.name) {
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
            if (routeName == TvRoute.Login.name) routeName = TvRoute.Home.name
            viewModel.refreshCatalogs()
        } else if (!isRestoring) {
            routeName = TvRoute.Login.name
        }
    }
    LaunchedEffect(routeName, uiState.selectedDetails) {
        if (routeName == TvRoute.Details.name && uiState.selectedDetails == null && !detailsRequested) {
            // A recreated activity cannot retain an in-memory detail request; recover to Home.
            routeName = TvRoute.Home.name
        }
    }

    BackHandler(enabled = route == TvRoute.Details) {
        viewModel.closeDetails()
        detailsRequested = false
        routeName = TvRoute.Home.name
    }

    StremioMobileTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF101216))) {
            when {
                isRestoring -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                authenticated && route == TvRoute.Login -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                route == TvRoute.Login || !authenticated -> TvLoginScreen(
                    account = uiState.account,
                    linkState = tvLinkState,
                    onLogin = viewModel::login,
                    onRequestNewLink = viewModel::requestNewTvAccountLink,
                )
                else -> {
                    TvHomeScreen(
                        shelves = uiState.boardShelves,
                        isBoardLoading = isBoardLoading,
                        isActive = route == TvRoute.Home,
                        focusMemory = focusMemory,
                        onOpenDetails = { item ->
                            detailsRequested = true
                            viewModel.openDetails(item)
                            routeName = TvRoute.Details.name
                        },
                    )
                    if (route == TvRoute.Details) {
                        TvDetailsScreen(
                            details = uiState.selectedDetails,
                            onBack = {
                                viewModel.closeDetails()
                                detailsRequested = false
                                routeName = TvRoute.Home.name
                            },
                        )
                    }
                }
            }
        }
    }
}
