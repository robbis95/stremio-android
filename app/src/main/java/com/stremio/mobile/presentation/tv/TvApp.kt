package com.stremio.mobile.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stremio.mobile.presentation.viewmodel.MainViewModel
import com.stremio.mobile.presentation.tv.focus.rememberTvFocusMemory
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvTheme

private enum class TvRoute { Login, Home, Details }

@Composable
internal fun TvApp(viewModel: MainViewModel) {
    val account by viewModel.tvAccount.collectAsStateWithLifecycle()
    val boardShelves by viewModel.tvBoardShelves.collectAsStateWithLifecycle()
    val selectedDetails by viewModel.tvSelectedDetails.collectAsStateWithLifecycle()
    val tvLinkState by viewModel.tvAccountLink.collectAsStateWithLifecycle()
    val isRestoring by viewModel.sessionRestoring.collectAsStateWithLifecycle()
    val isBoardLoading by viewModel.tvBoardLoading.collectAsStateWithLifecycle()
    var routeName by rememberSaveable { mutableStateOf(TvRoute.Login.name) }
    var restoreFocusRequestId by rememberSaveable { mutableIntStateOf(0) }
    var detailsRequested by remember { mutableStateOf(false) }
    val focusMemory = rememberTvFocusMemory()
    val route = runCatching { TvRoute.valueOf(routeName) }.getOrDefault(TvRoute.Login)
    val authenticated = account.isAuthenticated

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
    LaunchedEffect(routeName, selectedDetails) {
        if (routeName == TvRoute.Details.name && selectedDetails == null && !detailsRequested) {
            // A recreated activity cannot retain an in-memory detail request; recover to Home.
            restoreFocusRequestId++
            routeName = TvRoute.Home.name
        }
    }

    val returnToHome = {
        viewModel.closeDetails()
        detailsRequested = false
        restoreFocusRequestId++
        routeName = TvRoute.Home.name
    }

    BackHandler(enabled = route == TvRoute.Details) {
        returnToHome()
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
                    TvHomeScreen(
                        shelves = boardShelves,
                        isBoardLoading = isBoardLoading,
                        isActive = route == TvRoute.Home,
                        restoreFocusRequestId = restoreFocusRequestId,
                        focusMemory = focusMemory,
                        onOpenDetails = { item ->
                            detailsRequested = true
                            viewModel.openDetails(item)
                            routeName = TvRoute.Details.name
                        },
                    )
                    if (route == TvRoute.Details) {
                        TvDetailsScreen(
                            details = selectedDetails,
                            onBack = returnToHome,
                        )
                    }
                }
            }
        }
    }
}
