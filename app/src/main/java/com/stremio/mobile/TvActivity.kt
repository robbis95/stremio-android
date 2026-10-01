package com.stremio.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stremio.mobile.presentation.tv.TvApp
import com.stremio.mobile.presentation.viewmodel.MainViewModel

class TvActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as MainApplication
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
                authRepository = app.container.authRepository,
                boardRepository = app.container.boardRepository,
                catalogRepository = app.container.catalogRepository,
                addonRepository = app.container.addonRepository,
                playbackRepository = app.container.playbackRepository,
                updateRepository = app.container.updateRepository,
                apkInstaller = app.container.apkInstaller,
                serverController = app.container.serverController,
                core = app.container.core,
                appContext = app.applicationContext,
                tvValidationFixtures = app.container.tvValidationFixtures,
                tvNextVideoProvider = app.container.tvNextVideoProvider,
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(16, 18, 22)
        window.navigationBarColor = android.graphics.Color.rgb(16, 18, 22)
        setContent {
            TvApp(viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }
}
