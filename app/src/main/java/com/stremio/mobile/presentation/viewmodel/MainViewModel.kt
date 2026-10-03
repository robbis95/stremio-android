package com.stremio.mobile.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stremio.mobile.BuildConfig
import com.stremio.core.runtime.RuntimeEvent
import com.stremio.core.runtime.msg.Event
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.core.PlaybackResolutionException
import com.stremio.mobile.core.PlaybackResolutionFailure
import com.stremio.mobile.core.streamRequiresLocalServer
import com.stremio.mobile.data.model.*
import com.stremio.mobile.data.repository.*
import com.stremio.mobile.player.PlaybackState
import com.stremio.mobile.player.ExternalSubtitle
import com.stremio.mobile.player.LanguageCatalog
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.player.PlayerSubtitleStyle
import com.stremio.mobile.player.PlayerTrackOption
import com.stremio.mobile.player.PlayerPlaybackEvent
import com.stremio.mobile.core.theme.AppFont
import timber.log.Timber
import com.stremio.mobile.presentation.state.*
import com.stremio.mobile.presentation.tv.DiscoverPageRequestTracker
import com.stremio.mobile.presentation.tv.DiscoverSelectableRequest
import com.stremio.mobile.presentation.tv.TvDiscoverUiState
import com.stremio.mobile.presentation.tv.TvLibraryUiState
import com.stremio.mobile.presentation.tv.LibraryPageRequestTracker
import com.stremio.mobile.presentation.tv.TvLibraryOrderSnapshot
import com.stremio.mobile.presentation.tv.selectionIdentity
import com.stremio.mobile.presentation.tv.stableLibraryOrder
import com.stremio.mobile.presentation.tv.toTvLibraryUiState
import com.stremio.mobile.presentation.tv.preferredDiscoverRequest
import com.stremio.mobile.presentation.tv.toTvDiscoverUiState
import com.stremio.mobile.presentation.tv.toTvDiscoverFilterGroups
import com.stremio.mobile.presentation.tv.TvDetailsUiState
import com.stremio.mobile.presentation.tv.TvEpisodeBrowserUiState
import com.stremio.mobile.presentation.tv.TvStreamTarget
import com.stremio.mobile.presentation.tv.TvStreamSelectionUiState
import com.stremio.mobile.presentation.tv.TvStreamPrefetchCache
import com.stremio.mobile.presentation.tv.TvStreamPrefetchRequest
import com.stremio.mobile.presentation.tv.TvEpisodeTransitionTracker
import com.stremio.mobile.presentation.tv.TvNextEpisodeTrigger
import com.stremio.mobile.presentation.tv.TvNextEpisodeDiscovery
import com.stremio.mobile.presentation.tv.TvEpisodeTransitionTrace
import com.stremio.mobile.presentation.tv.TvPlaybackUiState
import com.stremio.mobile.presentation.tv.TvPlaybackAttempt
import com.stremio.mobile.presentation.tv.TvPlaybackStage
import com.stremio.mobile.presentation.tv.TvPlaybackTiming
import com.stremio.mobile.presentation.tv.TvStartupTrace
import com.stremio.mobile.presentation.tv.safeTvStartupSummary
import com.stremio.mobile.presentation.tv.TvPlaybackFailureReason
import com.stremio.mobile.presentation.tv.TvSmartPlaybackSession
import com.stremio.mobile.presentation.tv.tvSmartFallbackNextCandidateIndex
import com.stremio.mobile.presentation.tv.TvNextEpisodeState
import com.stremio.mobile.presentation.tv.TvNextEpisodeTransition
import com.stremio.mobile.presentation.tv.isCurrentTvNextEpisode
import com.stremio.mobile.presentation.tv.isCurrentTvAttempt
import com.stremio.mobile.presentation.tv.shouldReleaseRetainedPlayerAfterTvFailure
import com.stremio.mobile.presentation.tv.tvProgressReportingAllowed
import com.stremio.mobile.presentation.tv.clampTvSeekTarget
import com.stremio.mobile.presentation.tv.safeTvPlaybackTrace
import com.stremio.mobile.presentation.tv.tvPlaybackCompletionPolicy
import com.stremio.mobile.presentation.tv.tvNextEpisodePromptVisible
import com.stremio.mobile.presentation.tv.tvNextEpisodeDismiss
import com.stremio.mobile.presentation.tv.tvNextEpisodeTransitionStarted
import com.stremio.mobile.presentation.tv.tvShouldAutoAdvanceEnded
import com.stremio.mobile.presentation.tv.tvSegmentsForAttempt
import com.stremio.mobile.presentation.tv.segments.CoreTvSegmentProvider
import com.stremio.mobile.presentation.tv.segments.TvSegmentCoordinator
import com.stremio.mobile.presentation.tv.segments.TvSegmentQuery
import com.stremio.mobile.presentation.tv.segments.tvSegmentQuery
import com.stremio.mobile.presentation.tv.nextEpisodeTarget
import com.stremio.mobile.presentation.tv.preferredNextEpisodeOption
import com.stremio.mobile.presentation.tv.TvProviderLoadStatus
import com.stremio.mobile.presentation.tv.TvSmartStreamSelector
import com.stremio.mobile.presentation.tv.AndroidPlaybackCapabilities
import com.stremio.mobile.presentation.tv.NetworkPlaybackHistory
import com.stremio.mobile.presentation.tv.networkSnapshot
import com.stremio.mobile.presentation.tv.parseStreamVideoMetadata
import com.stremio.mobile.presentation.tv.safeStreamPresentationText
import com.stremio.mobile.presentation.tv.parseTrustedDurationSeconds
import com.stremio.mobile.presentation.tv.tvSmartResultApplies
import com.stremio.mobile.presentation.tv.tvSmartShouldFinish
import com.stremio.mobile.presentation.tv.TvValidationFixtures
import com.stremio.mobile.presentation.tv.TvNextVideoProvider
import com.stremio.mobile.presentation.tv.CoreTvNextVideoProvider
import com.stremio.mobile.presentation.tv.tvNextVideoForAttempt
import com.stremio.mobile.presentation.tv.tvStreamTargetMatches
import com.stremio.mobile.presentation.tv.mapTvStreamEmission
import com.stremio.mobile.presentation.tv.stableInteractionOptions
import com.stremio.mobile.presentation.tv.keepSelectionIfPresent
import com.stremio.mobile.presentation.tv.selectProviderLocally
import com.stremio.mobile.presentation.tv.sourceKindCount
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.presentation.tv.detailsWhileLoading
import com.stremio.mobile.presentation.tv.isDetailsItemInLibrary
import com.stremio.mobile.server.StreamingServerController
import com.stremio.mobile.server.StreamingServerState
import com.stremio.mobile.server.formatServerErrorMessage
import com.stremio.mobile.update.ApkInstaller
import com.stremio.mobile.update.UpdateInfo
import com.stremio.mobile.update.UpdateRepository
import com.stremio.mobile.update.UpdateState

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val TV_SMART_SETTLE_MS = 1_300L
private const val TV_SMART_MAX_DISCOVERY_MS = 5_000L
private const val TV_SMART_STARTUP_TIMEOUT_MS = 45_000L
private const val TV_DETAILS_PREVIEW_ACTION_FALLBACK_MS = 8_000L

class MainViewModel internal constructor(
    private val authRepository: AuthRepository,
    private val boardRepository: BoardRepository,
    private val catalogRepository: CatalogRepository,
    private val addonRepository: AddonRepository,
    private val playbackRepository: PlaybackRepository,
    private val updateRepository: UpdateRepository,
    private val apkInstaller: ApkInstaller,
    private val serverController: StreamingServerController,
    private val core: StremioCore,
    appContext: Context,
    private val tvValidationFixtures: TvValidationFixtures? = null,
    tvNextVideoProvider: TvNextVideoProvider? = null,
) : ViewModel() {
    private val tvNextVideoProvider = tvNextVideoProvider ?: CoreTvNextVideoProvider { playbackRepository.getNextVideo() }
    private val appContext = appContext.applicationContext
    private val playbackNetworkHistory = NetworkPlaybackHistory(this.appContext)
    private val tvDeviceCapabilitySnapshots = java.util.concurrent.ConcurrentHashMap<Boolean, com.stremio.mobile.presentation.tv.DevicePlaybackCapabilities>()
    private val latestIntentUri = MutableStateFlow<String?>(null)
    private val account = MutableStateFlow(authRepository.accountFromCore())
    val tvAccount: StateFlow<AccountUiState> = account.asStateFlow()
    private val tvAccountLinkSession = TvAccountLinkSession(viewModelScope, StremioLinkRepository(), ::loginWithToken)
    val tvAccountLink: StateFlow<TvAccountLinkUiState> = tvAccountLinkSession.state
    private var authInFlight = false
    private val selectedSection = MutableStateFlow(MainSection.Home)
    private val searchQuery = MutableStateFlow("")
    val tvSearchQuery: StateFlow<String> = searchQuery.asStateFlow()
    private val searchResults = MutableStateFlow(CatalogShelf(title = "Search", isLoading = false))
    val tvSearchResults: StateFlow<CatalogShelf> = searchResults.asStateFlow()
    private val searchShelves = MutableStateFlow<List<CatalogShelf>>(emptyList())
    val tvSearchShelves: StateFlow<List<CatalogShelf>> = searchShelves.asStateFlow()
    private val isSearchOpen = MutableStateFlow(false)
    private val library = MutableStateFlow(CatalogShelf(title = "Library", isLoading = false))
    private val addons = MutableStateFlow(AddonsUiState())
    private val selectedAddonDetails = MutableStateFlow<AddonDetailsUiState?>(null)
    private val nextVideo = MutableStateFlow<com.stremio.core.types.resource.Video?>(null)
    private val showNextVideoPopup = MutableStateFlow(false)
    private val showNoSeedsBanner = MutableStateFlow(false)
    private val noSeedsReason = MutableStateFlow<String?>(null)
    private val selectedDetails = MutableStateFlow<MetaDetails?>(null)
    val tvSelectedDetails: StateFlow<MetaDetails?> = selectedDetails.asStateFlow()
    private val _tvDetailsUiState = MutableStateFlow(TvDetailsUiState())
    internal val tvDetailsUiState: StateFlow<TvDetailsUiState> = _tvDetailsUiState.asStateFlow()
    private val _tvStreamSelection = MutableStateFlow(TvStreamSelectionUiState())
    internal val tvStreamSelection: StateFlow<TvStreamSelectionUiState> = _tvStreamSelection.asStateFlow()
    private val _tvPlayback = MutableStateFlow(TvPlaybackUiState())
    internal val tvPlayback: StateFlow<TvPlaybackUiState> = _tvPlayback.asStateFlow()
    private var tvPlaybackJob: Job? = null
    private var tvReusePendingAttemptId: String? = null
    private var tvPlaybackMonitorJob: Job? = null
    private var tvStartupTimeoutJob: Job? = null
    private var tvTorrentStartupStatsJob: Job? = null
    private var tvSmartPlaybackSession: TvSmartPlaybackSession? = null
    private var tvStartupTrace: TvStartupTrace? = null
    private var tvSegmentProviderJob: Job? = null
    private var latestTvCorePlayer: com.stremio.core.models.Player? = null
    private val tvSegmentCoordinator = TvSegmentCoordinator()
    private var tvStreamsJob: Job? = null
    private val tvDeviceCapabilityWarmups = mutableMapOf<Boolean, Job>()
    private var tvSmartSettleJob: Job? = null
    private var tvSmartMaximumJob: Job? = null
    private var tvNextEpisodeJob: Job? = null
    private val tvNextEpisodePrefetch = TvStreamPrefetchCache()
    private val tvEpisodeTransition = TvEpisodeTransitionTracker()
    private var tvNextVideoAttemptId: String? = null
    private var tvNextVideo: com.stremio.core.types.resource.Video? = null
    private val tvStreamReadyOrder = mutableMapOf<String, LinkedHashSet<String>>()
    private val auditedTvStreamTargets = mutableSetOf<String>()
    private var detailsLibraryOverride: Boolean? = null
    private var detailsActivatedNanos: Long? = null
    private var detailsLibraryActionJob: Job? = null
    private var isDetailsLibraryActionLoading = false
    private var hasAuthoritativeLibraryMembership = false
    private val continueWatching = MutableStateFlow(CatalogShelf(title = "Continue Watching"))
    val tvContinueWatching: StateFlow<CatalogShelf> = continueWatching.asStateFlow()
    private val boardShelves = MutableStateFlow<List<CatalogShelf>>(emptyList())
    val tvBoardShelves: StateFlow<List<CatalogShelf>> = boardShelves.asStateFlow()
    private val isBoardLoading = MutableStateFlow(true)
    val tvBoardLoading: StateFlow<Boolean> = isBoardLoading
    private val requestedRequests = mutableSetOf<String>()

    @Volatile
    private var cachedBoard: com.stremio.core.models.CatalogsWithExtra? = null

    private val discoverCatalogRequest = MutableStateFlow<com.stremio.core.types.addon.ResourceRequest?>(null)
    private val discoverCatalogTitle = MutableStateFlow<String?>(null)
    private val discoverCatalog = MutableStateFlow(CatalogShelf(title = "Discover", isLoading = false))
    private val discoverCatalogWithFilters = MutableStateFlow<com.stremio.core.models.CatalogWithFilters?>(null)
    private val _tvDiscover = MutableStateFlow(TvDiscoverUiState())
    internal val tvDiscover: StateFlow<TvDiscoverUiState> = _tvDiscover.asStateFlow()
    private val discoverPageRequests = DiscoverPageRequestTracker()
    private val libraryWithFilters = MutableStateFlow<com.stremio.core.models.LibraryWithFilters?>(null)
    private val _tvLibrary = MutableStateFlow(TvLibraryUiState())
    internal val tvLibrary: StateFlow<TvLibraryUiState> = _tvLibrary.asStateFlow()
    private var tvLibraryOrderSnapshot = TvLibraryOrderSnapshot(selection = null, items = emptyList())
    private var pendingLibraryRequest: com.stremio.core.models.LibraryWithFilters.LibraryRequest? = null
    private val libraryPageRequests = LibraryPageRequestTracker()
    private val isDiscoverSeeAll = MutableStateFlow(false)
    private val profileSettings = MutableStateFlow<com.stremio.core.types.profile.Profile.Settings?>(null)
    internal val tvProfileSettings: StateFlow<com.stremio.core.types.profile.Profile.Settings?> = profileSettings.asStateFlow()
    private val serverSettings = MutableStateFlow<com.stremio.core.models.StreamingServer.Settings?>(null)
    private val isAutoStartOnBoot = MutableStateFlow(authRepository.isAutoStartOnBoot())
    private val isServerInForeground = MutableStateFlow(authRepository.isServerInForeground())
    private val isMobileDataWarning = MutableStateFlow(authRepository.isMobileDataWarning())
    private val isKeepScreenOn = MutableStateFlow(authRepository.isKeepScreenOn())
    private val isAnalyticsEnabled = MutableStateFlow(authRepository.isAnalyticsEnabled())
    private val showAnalyticsDisclosure = MutableStateFlow(false)
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState
    private val isAutoUpdateEnabled = MutableStateFlow(authRepository.isAutoUpdateEnabled())
    private val isTraktAuthenticated = MutableStateFlow(false)
    private val isSeedingEnabled = MutableStateFlow(true)
    private val minSeedsThreshold = MutableStateFlow(authRepository.getMinSeedsThreshold())
    private val minDownloadSpeedBps = MutableStateFlow(authRepository.getMinDownloadSpeedBps())
    private val preferredQuality = MutableStateFlow(authRepository.getPreferredQuality())
    private val globalUiStyle = MutableStateFlow(authRepository.getGlobalUiStyle())
    private val playerUiStyle = MutableStateFlow(authRepository.getPlayerUiStyle())
    private val glassEffectsMode = MutableStateFlow(authRepository.getGlassEffectsMode())
    private val isAutoSwitchOnDeadStream = MutableStateFlow(authRepository.isAutoSwitchOnDeadStream())
    private val globalGlassAlpha = MutableStateFlow(authRepository.getGlobalGlassAlpha())
    private val adaptiveGlassContrast = MutableStateFlow(authRepository.isAdaptiveGlassContrastEnabled())
    private val glassHapticsEnabled = MutableStateFlow(authRepository.getGlassHapticsEnabled())
    private val hapticsIntensity = MutableStateFlow(authRepository.getHapticsIntensity())
    private val liquidGlassTuning = MutableStateFlow(authRepository.getLiquidGlassTuning())
    private val selectedFont = MutableStateFlow(authRepository.getSelectedFont())
    private val pendingMobileDataStream = MutableStateFlow<StreamOption?>(null)

    private var lastServerActivityMs: Long = System.currentTimeMillis()
    private val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    private val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val serverPingStatus = MutableStateFlow("Not checked")
    private val isPingLoading = MutableStateFlow(false)

    private val configPath = File(this.appContext.filesDir, "stream-server").absolutePath
    private val cachePath = File(this.appContext.cacheDir, "stream-server").absolutePath

    private val streams = MutableStateFlow(StreamsUiState())
    val streamsState: StateFlow<StreamsUiState> = streams

    private val isRestoringSession = MutableStateFlow(
        run {
            val authKey = authRepository.getSavedAuthKey()
            !authKey.isNullOrBlank() && authKey != "mock_auth_key" && !authRepository.isAuthenticated()
        }
    )
    val sessionRestoring: StateFlow<Boolean> = isRestoringSession

    val playbackState: StateFlow<PlaybackState> = playbackRepository.state

    private val playerOpen = MutableStateFlow(false)
    val isPlayerOpen: StateFlow<Boolean> = playerOpen

    private var streamsJob: Job? = null
    private var playJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private val auditedTvDetailsIds = mutableSetOf<String>()
    private var addonDetailsJob: Job? = null
    private var nextVideoJob: Job? = null
    private var subtitleObserverJob: Job? = null
    private var lastPlayedOption: StreamOption? = null
    private var dismissedNextVideoId: String? = null
    private var lastTimeReportMs = 0L
    private var noSeedsPollJob: Job? = null
    private var unhealthySinceMs: Long? = null

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<MainUiState> = combine(
        listOf(
            serverController.state,
            serverPingStatus,
            isPingLoading,
            latestIntentUri,
            account,
            selectedSection,
            searchQuery,
            searchResults,
            library,
            addons,
            selectedDetails,
            continueWatching,
            boardShelves,
            discoverCatalogRequest,
            discoverCatalogTitle,
            discoverCatalog,
            discoverCatalogWithFilters,
            libraryWithFilters,
            isDiscoverSeeAll,
            searchShelves,
            profileSettings,
            serverSettings,
            isAutoStartOnBoot,
            isServerInForeground,
            isMobileDataWarning,
            isKeepScreenOn,
            isTraktAuthenticated,
            isSeedingEnabled,
            isSearchOpen,
            selectedAddonDetails,
            nextVideo,
            showNextVideoPopup,
            minSeedsThreshold,
            minDownloadSpeedBps,
            preferredQuality,
            globalUiStyle,
            glassEffectsMode,
            isAutoSwitchOnDeadStream,
            showNoSeedsBanner,
            noSeedsReason,
            globalGlassAlpha,
            adaptiveGlassContrast,
            glassHapticsEnabled,
            hapticsIntensity,
            liquidGlassTuning,
            selectedFont,
            pendingMobileDataStream,
            isAnalyticsEnabled,
            showAnalyticsDisclosure,
            _updateState,
            isAutoUpdateEnabled,
            playerUiStyle,
        )
    ) { values ->
        MainUiState(
            server = values[0] as StreamingServerState,
            selectedFont = values[45] as AppFont,
            showMobileDataWarning = values[46] != null,
            isAnalyticsEnabled = values[47] as Boolean,
            showAnalyticsDisclosure = values[48] as Boolean,
            updateState = values[49] as UpdateState,
            isAutoUpdateEnabled = values[50] as Boolean,
            serverPingStatus = values[1] as String,
            isPingLoading = values[2] as Boolean,
            serverVersion = "0.1.8",
            serverConfigPath = configPath,
            serverCachePath = cachePath,
            latestIntentUri = values[3] as String?,
            account = values[4] as AccountUiState,
            selectedSection = values[5] as MainSection,
            searchQuery = values[6] as String,
            searchResults = values[7] as CatalogShelf,
            searchShelves = values[19] as List<CatalogShelf>,
            library = values[8] as CatalogShelf,
            addons = values[9] as AddonsUiState,
            selectedDetails = values[10] as MetaDetails?,
            continueWatching = values[11] as CatalogShelf,
            boardShelves = values[12] as List<CatalogShelf>,
            discoverCatalogRequest = values[13] as com.stremio.core.types.addon.ResourceRequest?,
            discoverCatalogTitle = values[14] as String?,
            discoverCatalog = values[15] as CatalogShelf,
            discoverCatalogWithFilters = values[16] as com.stremio.core.models.CatalogWithFilters?,
            libraryWithFilters = values[17] as com.stremio.core.models.LibraryWithFilters?,
            isDiscoverSeeAll = values[18] as Boolean,
            profileSettings = values[20] as com.stremio.core.types.profile.Profile.Settings?,
            serverSettings = values[21] as com.stremio.core.models.StreamingServer.Settings?,
            isAutoStartOnBoot = values[22] as Boolean,
            isServerInForeground = values[23] as Boolean,
            isMobileDataWarning = values[24] as Boolean,
            isKeepScreenOn = values[25] as Boolean,
            isTraktAuthenticated = values[26] as Boolean,
            isSeedingEnabled = values[27] as Boolean,
            isSearchOpen = values[28] as Boolean,
            selectedAddonDetails = values[29] as AddonDetailsUiState?,
            nextVideo = values[30] as com.stremio.core.types.resource.Video?,
            showNextVideoPopup = values[31] as Boolean,
            minSeedsThreshold = values[32] as Int,
            minDownloadSpeedBps = values[33] as Long,
            preferredQuality = values[34] as String,
            globalUiStyle = values[35] as String,
            playerUiStyle = values[51] as String,
            glassEffectsMode = values[36] as String,
            isAutoSwitchOnDeadStream = values[37] as Boolean,
            showNoSeedsBanner = values[38] as Boolean,
            noSeedsReason = values[39] as String?,
            globalGlassAlpha = values[40] as Float,
            adaptiveGlassContrast = values[41] as Boolean,
            glassHapticsEnabled = values[42] as Boolean,
            hapticsIntensity = values[43] as String,
            liquidGlassTuning = values[44] as LiquidGlassTuning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        observeBoard()
        observeContinueWatching()
        observeDiscover()
        observeLibrary()
        observeAddons()
        refreshLibrary()
        observeCoreAuth()
        observeStreamingServer()
        restoreCoreSession()
        checkForUpdates(manual = true)

        startServer() // Always start the streaming server on app startup

        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                stopServerIfIdle()
            }
        }
    }

    private fun observeCoreAuth() {
        viewModelScope.launch {
            authRepository.getAccountStateFlow().collect { accountState ->
                account.value = accountState
                isRestoringSession.value = false
                if (accountState.isAuthenticated && accountState.authKey != null) {
                    authInFlight = false
                    authRepository.saveAuthKeyAndEmail(accountState.authKey, accountState.email ?: "")
                    refreshLibrary()
                    val email = accountState.email
                    if (!email.isNullOrBlank()) {
                        val hashedEmail = sha256(email)
                        com.posthog.PostHog.identify(hashedEmail)
                        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setUserId(hashedEmail)
                    }
                }
            }
        }
        viewModelScope.launch {
            authRepository.getProfileSettingsFlow().collect { settings ->
                profileSettings.value = settings
            }
        }
        viewModelScope.launch {
            authRepository.getTraktAuthFlow().collect { traktAuth ->
                if (traktAuth && !isTraktAuthenticated.value) {
                    com.posthog.PostHog.capture(event = "Trakt Authenticated")
                }
                isTraktAuthenticated.value = traktAuth
            }
        }
        viewModelScope.launch {
            core.events.collect { runtimeEvent ->
                val inner = runtimeEvent.event
                if (inner is RuntimeEvent.Event.CoreEvent && inner.value.type is Event.Type.Error && authInFlight) {
                    authInFlight = false
                    val message = (inner.value.type as Event.Type.Error).value.error
                    account.value = account.value.copy(isLoading = false, error = message)
                    tvAccountLinkSession.markSignInFailed("Could not sign in with that link. Request a new link and try again.")
                }
            }
        }
    }

    private fun observeStreamingServer() {
        viewModelScope.launch {
            core.streamingServerFlow().collect { server ->
                val content = server.settings.content
                if (content is com.stremio.core.models.LoadableSettings.Content.Ready) {
                    serverSettings.value = content.value
                }
            }
        }
        viewModelScope.launch {
            serverController.state.collect { state ->
                val stateName = when (state) {
                    is StreamingServerState.Ready -> "Ready"
                    is StreamingServerState.Failed -> "Failed"
                    StreamingServerState.Starting -> "Starting"
                    StreamingServerState.Stopped -> "Stopped"
                }
                com.posthog.PostHog.capture(
                    event = "Streaming Server State Changed",
                    properties = mapOf("new_state" to stateName)
                )
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCustomKey("server_state", stateName)
                if (state is StreamingServerState.Failed) {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCustomKey("server_error", formatServerErrorMessage(state.message))
                }

                if (state is StreamingServerState.Ready) {
                    fetchStreamingServerSettingsDirectly()
                }
            }
        }
    }

    private suspend fun startServerInternal() {
        lastServerActivityMs = System.currentTimeMillis()
        serverController.start()
        when (val state = serverController.state.value) {
            is StreamingServerState.Ready -> Unit
            is StreamingServerState.Failed -> error(state.message)
            StreamingServerState.Starting -> error("Streaming server is still starting")
            StreamingServerState.Stopped -> error("Streaming server did not start")
        }
    }

    private suspend fun stopServerIfIdle() {
        // No-op: Server should run continuously as long as the app is running
    }

    fun fetchStreamingServerSettingsDirectly() {
        viewModelScope.launch {
            val currentState = serverController.state.value
            if (currentState is StreamingServerState.Ready) {
                val url = "${currentState.baseUrl}/settings"
                val settingsObj = withContext(Dispatchers.IO) {
                    try {
                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.setRequestProperty("Accept", "application/json")
                        if (connection.responseCode == 200) {
                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                            JSONObject(body).optJSONObject("values")
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch settings directly from server")
                        null
                    }
                }
                if (settingsObj != null) {
                    val current = serverSettings.value
                    val newSettings = com.stremio.core.models.StreamingServer.Settings(
                        appPath = settingsObj.optString("appPath", current?.appPath ?: ""),
                        cacheRoot = settingsObj.optString("cacheRoot", current?.cacheRoot ?: ""),
                        serverVersion = settingsObj.optString("serverVersion", current?.serverVersion ?: ""),
                        remoteHttps = if (settingsObj.has("remoteHttps") && !settingsObj.isNull("remoteHttps")) settingsObj.optString("remoteHttps") else null,
                        transcodeProfile = if (settingsObj.has("transcodeProfile") && !settingsObj.isNull("transcodeProfile")) settingsObj.optString("transcodeProfile") else null,
                        cacheSize = if (settingsObj.has("cacheSize") && !settingsObj.isNull("cacheSize")) settingsObj.optDouble("cacheSize") else null,
                        proxyStreamsEnabled = settingsObj.optBoolean("proxyStreamsEnabled", current?.proxyStreamsEnabled ?: true),
                        btMaxConnections = settingsObj.optLong("btMaxConnections", current?.btMaxConnections ?: 200L),
                        btHandshakeTimeout = settingsObj.optLong("btHandshakeTimeout", current?.btHandshakeTimeout ?: 200L),
                        btRequestTimeout = settingsObj.optLong("btRequestTimeout", current?.btRequestTimeout ?: 10L),
                        btDownloadSpeedSoftLimit = settingsObj.optDouble("btDownloadSpeedSoftLimit", current?.btDownloadSpeedSoftLimit ?: 1.0),
                        btDownloadSpeedHardLimit = settingsObj.optDouble("btDownloadSpeedHardLimit", current?.btDownloadSpeedHardLimit ?: 2.0),
                        btMinPeersForStable = settingsObj.optLong("btMinPeersForStable", current?.btMinPeersForStable ?: 10L)
                    )
                    serverSettings.value = newSettings
                    isSeedingEnabled.value = settingsObj.optBoolean("seedingEnabled", true)
                    core.updateStreamingServerSettings(newSettings)
                }
            }
        }
    }

    fun updateProfileSettings(newSettings: com.stremio.core.types.profile.Profile.Settings) {
        val oldSettings = profileSettings.value
        if (oldSettings != null) {
            val changes = mutableMapOf<String, Any>()
            if (oldSettings.playerType != newSettings.playerType) {
                changes["playerType"] = newSettings.playerType ?: ""
            }
            if (oldSettings.subtitlesAutoSelect != newSettings.subtitlesAutoSelect) {
                changes["subtitlesAutoSelect"] = newSettings.subtitlesAutoSelect
            }
            if (oldSettings.subtitlesLanguage != newSettings.subtitlesLanguage) {
                changes["subtitlesLanguage"] = newSettings.subtitlesLanguage ?: ""
            }
            if (oldSettings.subtitlesSize != newSettings.subtitlesSize) {
                changes["subtitlesSize"] = newSettings.subtitlesSize
            }
            if (oldSettings.subtitlesTextColor != newSettings.subtitlesTextColor) {
                changes["subtitlesTextColor"] = newSettings.subtitlesTextColor
            }
            if (oldSettings.subtitlesBackgroundColor != newSettings.subtitlesBackgroundColor) {
                changes["subtitlesBackgroundColor"] = newSettings.subtitlesBackgroundColor
            }
            if (oldSettings.subtitlesOutlineColor != newSettings.subtitlesOutlineColor) {
                changes["subtitlesOutlineColor"] = newSettings.subtitlesOutlineColor
            }
            if (oldSettings.assSubtitlesStyling != newSettings.assSubtitlesStyling) {
                changes["assSubtitlesStyling"] = newSettings.assSubtitlesStyling
            }
            if (oldSettings.audioLanguage != newSettings.audioLanguage) {
                changes["audioLanguage"] = newSettings.audioLanguage ?: ""
            }
            if (oldSettings.audioPassthrough != newSettings.audioPassthrough) {
                changes["audioPassthrough"] = newSettings.audioPassthrough
            }
            if (oldSettings.surroundSound != newSettings.surroundSound) {
                changes["surroundSound"] = newSettings.surroundSound
            }
            if (oldSettings.playInBackground != newSettings.playInBackground) {
                changes["playInBackground"] = newSettings.playInBackground
            }
            if (oldSettings.hardwareDecoding != newSettings.hardwareDecoding) {
                changes["hardwareDecoding"] = newSettings.hardwareDecoding
            }
            if (oldSettings.frameRateMatchingStrategy != newSettings.frameRateMatchingStrategy) {
                changes["frameRateMatchingStrategy"] = newSettings.frameRateMatchingStrategy.name ?: ""
            }
            if (oldSettings.seekTimeDuration != newSettings.seekTimeDuration) {
                changes["seekTimeDuration"] = newSettings.seekTimeDuration
            }
            if (oldSettings.bingeWatching != newSettings.bingeWatching) {
                changes["bingeWatching"] = newSettings.bingeWatching
            }
            if (oldSettings.nextVideoNotificationDuration != newSettings.nextVideoNotificationDuration) {
                changes["nextVideoNotificationDuration"] = newSettings.nextVideoNotificationDuration
            }

            for ((key, value) in changes) {
                com.posthog.PostHog.capture(
                    event = "Setting Changed",
                    properties = mapOf(
                        "setting_name" to key,
                        "new_value" to value
                    )
                )
            }
        }
        viewModelScope.launch {
            authRepository.updateSettings(newSettings)
        }
    }

    fun updateStreamingServerSettings(newSettings: com.stremio.core.models.StreamingServer.Settings) {
        viewModelScope.launch {
            serverSettings.value = newSettings
            core.updateStreamingServerSettings(newSettings)

            val currentState = serverController.state.value
            if (currentState is StreamingServerState.Ready) {
                val url = "${currentState.baseUrl}/settings"
                withContext(Dispatchers.IO) {
                    try {
                        val payloadObj = JSONObject().apply {
                            put("appPath", newSettings.appPath)
                            put("cacheRoot", newSettings.cacheRoot)
                            put("serverVersion", newSettings.serverVersion)
                            put("remoteHttps", newSettings.remoteHttps ?: JSONObject.NULL)
                            put("transcodeProfile", newSettings.transcodeProfile ?: JSONObject.NULL)
                            put("cacheSize", newSettings.cacheSize ?: JSONObject.NULL)
                            put("proxyStreamsEnabled", newSettings.proxyStreamsEnabled)
                            put("btMaxConnections", newSettings.btMaxConnections)
                            put("btHandshakeTimeout", newSettings.btHandshakeTimeout)
                            put("btRequestTimeout", newSettings.btRequestTimeout)
                            put("btDownloadSpeedSoftLimit", newSettings.btDownloadSpeedSoftLimit)
                            put("btDownloadSpeedHardLimit", newSettings.btDownloadSpeedHardLimit)
                            put("btMinPeersForStable", newSettings.btMinPeersForStable)
                            put("seedingEnabled", isSeedingEnabled.value)
                        }

                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Accept", "application/json")

                        connection.outputStream.use { os ->
                            os.write(payloadObj.toString().toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            Timber.d("Successfully updated settings directly on server")
                        } else {
                            Timber.e("Failed to update settings directly on server: response code %d", responseCode)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error posting settings to server")
                    }
                }
            }
        }
    }

    fun setSeedingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            isSeedingEnabled.value = enabled
            val currentSettings = serverSettings.value
            if (currentSettings != null) {
                updateStreamingServerSettings(currentSettings)
            }
        }
    }

    fun isAutoStartOnBoot(): Boolean = authRepository.isAutoStartOnBoot()
    fun setAutoStartOnBoot(enabled: Boolean) {
        authRepository.setAutoStartOnBoot(enabled)
        isAutoStartOnBoot.value = enabled
    }

    fun isServerInForeground(): Boolean = authRepository.isServerInForeground()
    fun setServerInForeground(enabled: Boolean) {
        val current = authRepository.isServerInForeground()
        if (current != enabled) {
            authRepository.setServerInForeground(enabled)
            isServerInForeground.value = enabled
            
            val packageManager = appContext.packageManager
            val intent = packageManager.getLaunchIntentForPackage(appContext.packageName)
            if (intent != null) {
                val componentName = intent.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                appContext.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }
    }

    fun isMobileDataWarning(): Boolean = authRepository.isMobileDataWarning()
    fun setMobileDataWarning(enabled: Boolean) {
        authRepository.setMobileDataWarning(enabled)
        isMobileDataWarning.value = enabled
    }

    fun getSelectedFont(): AppFont = authRepository.getSelectedFont()
    fun setSelectedFont(font: AppFont) {
        authRepository.setSelectedFont(font)
        selectedFont.value = font
    }

    fun isKeepScreenOn(): Boolean = authRepository.isKeepScreenOn()
    fun setKeepScreenOn(enabled: Boolean) {
        authRepository.setKeepScreenOn(enabled)
        isKeepScreenOn.value = enabled
    }

    fun isAnalyticsEnabled(): Boolean = authRepository.isAnalyticsEnabled()
    fun setAnalyticsEnabled(enabled: Boolean) {
        authRepository.setAnalyticsEnabled(enabled)
        isAnalyticsEnabled.value = enabled
        
        if (enabled) {
            com.posthog.PostHog.optIn()
        } else {
            com.posthog.PostHog.optOut()
        }
        
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(enabled)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        authRepository.setAutoUpdateEnabled(enabled)
        isAutoUpdateEnabled.value = enabled
        if (enabled) {
            maybeAutoCheckForUpdates()
        }
    }

    private fun maybeAutoCheckForUpdates() {
        if (!isAutoUpdateEnabled.value) return
        val lastCheckMs = authRepository.getLastUpdateCheckMs()
        if (System.currentTimeMillis() - lastCheckMs < UPDATE_CHECK_INTERVAL_MS) return
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            if (!manual) {
                if (!isAutoUpdateEnabled.value) return@launch
                val lastCheckMs = authRepository.getLastUpdateCheckMs()
                if (System.currentTimeMillis() - lastCheckMs < UPDATE_CHECK_INTERVAL_MS) return@launch
            }

            com.posthog.PostHog.capture(
                event = "Update Check Started",
                properties = mapOf("manual" to manual)
            )

            _updateState.value = UpdateState.Checking
            when (val result = runCatching { updateRepository.check() }.getOrElse { error ->
                UpdateState.Error(error.message ?: "Update check failed.")
            }) {
                is UpdateState.Available -> {
                    authRepository.setLastUpdateCheckMs(System.currentTimeMillis())
                    com.posthog.PostHog.capture(
                        event = "Update Check Finished",
                        properties = mapOf(
                            "status" to "Available",
                            "version" to result.info.tagName,
                            "manual" to manual
                        )
                    )
                    if (!manual && authRepository.getIgnoredUpdateVersion() == result.info.tagName) {
                        _updateState.value = UpdateState.Idle
                    } else {
                        _updateState.value = result
                    }
                }
                is UpdateState.UpToDate -> {
                    authRepository.setLastUpdateCheckMs(System.currentTimeMillis())
                    com.posthog.PostHog.capture(
                        event = "Update Check Finished",
                        properties = mapOf(
                            "status" to "UpToDate",
                            "manual" to manual
                        )
                    )
                    _updateState.value = result
                }
                is UpdateState.Error -> {
                    com.posthog.PostHog.capture(
                        event = "Update Check Finished",
                        properties = mapOf(
                            "status" to "Error",
                            "error_message" to result.message,
                            "manual" to manual
                        )
                    )
                    if (manual) {
                        _updateState.value = result
                    } else {
                        Timber.e("Auto update check failed: ${result.message}")
                        _updateState.value = UpdateState.Idle
                    }
                }
                else -> {
                    _updateState.value = result
                }
            }
        }
    }

    fun downloadAndInstallUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            runCatching {
                val file = updateRepository.download(info) { bytesRead, totalBytes ->
                    _updateState.value = UpdateState.Downloading(
                        bytesRead = bytesRead,
                        totalBytes = totalBytes,
                        progress = totalBytes?.takeIf { it > 0L }?.let { bytesRead.toFloat() / it.toFloat() },
                    )
                }
                installDownloadedUpdate(file)
            }.onFailure { error ->
                Timber.e(error, "Failed to download update")
                _updateState.value = UpdateState.Error(error.message ?: "Update download failed.")
            }
        }
    }

    fun installDownloadedUpdate(file: File) {
        runCatching {
            val needsPermission = apkInstaller.install(file)
            _updateState.value = UpdateState.ReadyToInstall(
                file = file,
                needsUnknownSourcesPermission = needsPermission,
            )
        }.onFailure { error ->
            Timber.e(error, "Failed to launch APK installer")
            _updateState.value = UpdateState.Error(error.message ?: "Could not open the system installer.")
        }
    }

    fun ignoreUpdate(tagName: String) {
        authRepository.setIgnoredUpdateVersion(tagName)
        _updateState.value = UpdateState.Idle
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateState.Idle
    }

    fun acknowledgeAnalyticsDisclosure(enableAnalytics: Boolean) {
        authRepository.setAnalyticsDisclosureAcknowledged(true)
        showAnalyticsDisclosure.value = false
        setAnalyticsEnabled(enableAnalytics)
    }

    fun showAnalyticsDisclosure() {
        showAnalyticsDisclosure.value = true
    }

    fun setMinSeedsThreshold(value: Int) {
        authRepository.setMinSeedsThreshold(value)
        minSeedsThreshold.value = value
    }

    fun setMinDownloadSpeedBps(value: Long) {
        authRepository.setMinDownloadSpeedBps(value)
        minDownloadSpeedBps.value = value
    }

    fun setPreferredQuality(value: String) {
        authRepository.setPreferredQuality(value)
        preferredQuality.value = value
    }

    fun setGlobalUiStyle(value: String) {
        authRepository.setGlobalUiStyle(value)
        globalUiStyle.value = if (value == "modern") "modern" else "classic"
    }

    fun setPlayerUiStyle(value: String) {
        authRepository.setPlayerUiStyle(value)
        playerUiStyle.value = when (value) {
            "classic" -> "classic"
            "modern" -> "modern"
            else -> "global"
        }
    }

    fun setGlassEffectsMode(value: String) {
        val resolved = when (value) {
            "full" -> "full"
            "static" -> "static"
            else -> "balanced"
        }
        authRepository.setGlassEffectsMode(resolved)
        glassEffectsMode.value = resolved
    }

    fun setGlobalGlassAlpha(value: Float) {
        authRepository.setGlobalGlassAlpha(value)
        globalGlassAlpha.value = value
    }

    fun setAdaptiveGlassContrastEnabled(enabled: Boolean) {
        authRepository.setAdaptiveGlassContrastEnabled(enabled)
        adaptiveGlassContrast.value = enabled
    }

    fun setGlassHapticsEnabled(enabled: Boolean) {
        authRepository.setGlassHapticsEnabled(enabled)
        glassHapticsEnabled.value = enabled
    }

    fun setHapticsIntensity(value: String) {
        authRepository.setHapticsIntensity(value)
        hapticsIntensity.value = value
    }

    fun setLiquidGlassTuning(value: LiquidGlassTuning) {
        val tuning = value.clamped()
        authRepository.setLiquidGlassTuning(tuning)
        liquidGlassTuning.value = tuning
    }

    fun resetLiquidGlassTuning() {
        setLiquidGlassTuning(LiquidGlassTuning())
    }

    fun setAutoSwitchOnDeadStream(enabled: Boolean) {
        authRepository.setAutoSwitchOnDeadStream(enabled)
        isAutoSwitchOnDeadStream.value = enabled
    }

    fun isTraktAuthenticated(): Boolean = authRepository.isTraktAuthenticated()

    fun authenticateTrakt(context: Context) {
        authRepository.authenticateTrakt(context)
    }

    fun logoutTrakt() {
        viewModelScope.launch {
            authRepository.logoutTrakt()
        }
        com.posthog.PostHog.capture(event = "Trakt Logged Out")
    }

    fun installTraktAddon() {
        viewModelScope.launch {
            authRepository.installTraktAddon()
        }
        com.posthog.PostHog.capture(event = "Trakt Addon Installed")
    }

    private fun restoreCoreSession() {
        if (authRepository.isAuthenticated()) {
            isRestoringSession.value = false
            return
        }
        val authKey = authRepository.getSavedAuthKey()
        if (!authKey.isNullOrBlank() && authKey != "mock_auth_key") {
            isRestoringSession.value = true
            viewModelScope.launch {
                val result = withTimeoutOrNull(5000) {
                    runCatching { authRepository.loginWithToken(authKey) }
                }
                if (result == null || result.isFailure) {
                    isRestoringSession.value = false
                }
            }
        } else {
            isRestoringSession.value = false
        }
    }

    private fun observeBoard() {
        viewModelScope.launch {
            boardRepository.getBoardFlow()
                .map { board -> board to boardRepository.extractBoardShelves(board) }
                .flowOn(Dispatchers.Default)
                .collect { (board, shelves) ->
                    cachedBoard = board
                    boardShelves.value = shelves
                    isBoardLoading.value = false
                    if (board.catalogs.isNotEmpty()) {
                        val toPreload = board.catalogs.take(5)
                            .flatMap { catalog -> catalog.pages.filter { it.content == null } }
                            .map { it.request.toString() }
                            .filter { it !in requestedRequests }
                        if (toPreload.isNotEmpty()) {
                            requestedRequests.addAll(toPreload)
                            boardRepository.loadBoardRange(0, minOf(board.catalogs.size - 1, 4))
                        }
                    }
                }
        }
    }

    fun onShelfVisible(shelfIndex: Int) {
        val board = cachedBoard ?: return
        var currentShelfIdx = 0
        var foundCatalogIdx = -1
        for (cIdx in board.catalogs.indices) {
            val catalog = board.catalogs[cIdx]
            val pageCount = catalog.pages.size
            if (shelfIndex >= currentShelfIdx && shelfIndex < currentShelfIdx + pageCount) {
                foundCatalogIdx = cIdx
                break
            }
            currentShelfIdx += pageCount
        }
        if (foundCatalogIdx != -1) {
            val start = foundCatalogIdx
            val end = minOf(foundCatalogIdx + 2, board.catalogs.size - 1)
            var needLoad = false
            for (i in start..end) {
                if (i in board.catalogs.indices) {
                    val catalog = board.catalogs[i]
                    for (page in catalog.pages) {
                        if (page.content == null) {
                            val reqStr = page.request.toString()
                            if (!requestedRequests.contains(reqStr)) {
                                requestedRequests.add(reqStr)
                                needLoad = true
                            }
                        }
                    }
                }
            }
            if (needLoad) {
                Timber.d("onShelfVisible(%d): catalogIndex=%d. Loading range %d..%d", shelfIndex, foundCatalogIdx, start, end)
                boardRepository.loadBoardRange(start, end)
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            boardRepository.getContinueWatchingFlow().collect { cwShelf ->
                continueWatching.value = cwShelf
            }
        }
    }

    private fun observeDiscover() {
        viewModelScope.launch {
            catalogRepository.getDiscoverFlow().collect { discover ->
                discoverCatalogWithFilters.value = discover
                var req = discoverCatalogRequest.value
                if (req == null) {
                    req = preferredDiscoverRequest(
                        coreSelectedRequest = discover.selected?.request,
                        types = discover.selectable.types.map { DiscoverSelectableRequest(it.selected, it.request) },
                        catalogs = discover.selectable.catalogs.map { DiscoverSelectableRequest(it.selected, it.request) },
                    )
                    if (req != null) {
                        discoverCatalogRequest.value = req
                        discoverCatalogTitle.value = "Discover"
                        val alreadyCurrent = discover.selected?.request == req && discover.catalog.pages.isNotEmpty()
                        if (!alreadyCurrent) catalogRepository.loadDiscover(req)
                    }
                }

                val currentReq = discoverCatalogRequest.value
                if (currentReq != null && (discover.selected?.request == currentReq)) {
                    val items = catalogRepository.extractDiscoverItems(discover)
                    val isLoading = discover.catalog.pages.any { it.content is com.stremio.core.models.LoadablePage.Content.Loading || it.content == null }
                    val error = discover.catalog.pages.firstNotNullOfOrNull { page ->
                        (page.content as? com.stremio.core.models.LoadablePage.Content.Error)?.value?.message
                    }
                    discoverCatalog.value = CatalogShelf(
                        title = discoverCatalogTitle.value ?: "Discover",
                        items = items,
                        isLoading = isLoading,
                        error = error,
                        seeAllRequest = currentReq
                    )
                }
                if (currentReq == null || discover.selected?.request == currentReq) {
                    _tvDiscover.value = discover.toTvDiscoverUiState(
                        shelf = discoverCatalog.value,
                        title = discoverCatalogTitle.value ?: "Discover",
                        selectedRequest = currentReq,
                    )
                } else {
                    _tvDiscover.value = _tvDiscover.value.copy(
                        selectedRequest = currentReq,
                        title = discoverCatalogTitle.value ?: "Discover",
                        shelf = discoverCatalog.value.copy(isLoading = true, seeAllRequest = currentReq),
                        filterGroups = discover.toTvDiscoverFilterGroups(),
                    )
                }
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            catalogRepository.getLibraryWithShelfFlow().collect { (libraryWithFiltersVal, shelf) ->
                libraryWithFilters.value = libraryWithFiltersVal
                val coreRequest = libraryWithFiltersVal.selected?.request
                val pending = pendingLibraryRequest
                if (pending != null && coreRequest != pending) {
                    _tvLibrary.value = _tvLibrary.value.copy(isLoading = true)
                    return@collect
                }
                if (pending == coreRequest) pendingLibraryRequest = null
                if (!shelf.isLoading && shelf.error == null) hasAuthoritativeLibraryMembership = true

                val identity = coreRequest?.selectionIdentity()
                tvLibraryOrderSnapshot = stableLibraryOrder(
                    previous = tvLibraryOrderSnapshot,
                    selection = identity,
                    incoming = shelf.items,
                )
                library.value = shelf.copy(items = tvLibraryOrderSnapshot.items)
                _tvLibrary.value = libraryWithFiltersVal.toTvLibraryUiState(
                    items = tvLibraryOrderSnapshot.items,
                    loading = pendingLibraryRequest != null,
                )
                refreshTvDetailsUiState()
            }
        }
    }

    fun openDiscoverCatalog(request: com.stremio.core.types.addon.ResourceRequest, title: String) {
        isDiscoverSeeAll.value = true
        discoverCatalogRequest.value = request
        discoverCatalogTitle.value = title
        discoverCatalog.value = CatalogShelf(title = title, isLoading = true, seeAllRequest = request)
        selectedSection.value = MainSection.Discover
        catalogRepository.loadDiscover(request)
    }

    fun closeDiscoverCatalog() {
        isDiscoverSeeAll.value = false
        discoverCatalogRequest.value = null
        discoverCatalogTitle.value = null
        discoverCatalog.value = CatalogShelf(title = "Discover", isLoading = false)
        selectedSection.value = MainSection.Home
    }

    fun openStreams(item: CatalogItem) {
        streamsJob?.cancel()
        val rememberedSelection = localContinueWatchingStreamSelection(item)
        if (rememberedSelection != null) {
            openRememberedContinueWatchingStream(item, rememberedSelection)
            return
        }

        streamsJob = launchStreamsMenuJob(item)
    }

    private fun openRememberedContinueWatchingStream(item: CatalogItem, rememberedSelection: LocalStreamSelection) {
        val isSeries = item.type == "series"
        streams.value = StreamsUiState(
            forItem = item,
            isOpen = false,
            isLoading = true,
            isSeries = isSeries,
            selectedVideoId = if (isSeries) item.continueWatchingVideoId else null,
        )

        streamsJob = viewModelScope.launch {
            runCatching { startServerInternal() }

            val videoId = if (isSeries) item.continueWatchingVideoId else null
            val matchedOption = runCatching {
                withTimeoutOrNull(30_000) {
                    catalogRepository.getMetaDetailsFlow(
                        type = item.type,
                        id = item.id,
                        videoId = videoId,
                        guessStreamPath = !isSeries,
                    )
                        .map { details ->
                            if (isSeries && videoId != null) {
                                episodeDisplayInfo(details, videoId)?.let { episode ->
                                    streams.value = streams.value.copy(
                                        selectedEpisodeLabel = episode.label,
                                        releaseDateLabel = episode.releaseDate,
                                    )
                                }
                            }
                            catalogRepository.extractStreams(details)
                                .mapIndexed { index, coreStream -> buildStreamOption(index, coreStream) }
                                .firstOrNull { rememberedSelection.matches(it) }
                        }
                        .first { it != null }
                }
            }.getOrNull()

            if (matchedOption != null) {
                playStream(matchedOption)
            } else {
                streams.value = StreamsUiState()
                streamsJob = launchStreamsMenuJob(item)
            }
        }
    }

    private fun localContinueWatchingStreamSelection(item: CatalogItem): LocalStreamSelection? {
        if (!item.isContinueWatching) return null
        val videoId = continueWatchingVideoKey(item)
        return authRepository.getLocalStreamSelection(item.type, item.id, videoId)
    }

    private fun continueWatchingVideoKey(item: CatalogItem): String {
        return item.continueWatchingVideoId?.takeIf { it.isNotBlank() } ?: item.id
    }

    private fun launchStreamsMenuJob(item: CatalogItem): Job {
        val isSeries = item.type == "series"
        streams.value = StreamsUiState(forItem = item, isOpen = true, isLoading = true, isSeries = isSeries)

        return viewModelScope.launch {
            runCatching { startServerInternal() }

            if (isSeries) {
                val collector = launch {
                    catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = false)
                        .collect { details ->
                            val videos = catalogRepository.extractVideos(details)
                            if (videos.isNotEmpty()) {
                                val seasons = videos.mapNotNull { it.seriesInfo?.season?.toInt() }.distinct().sorted()
                                val defaultSeason = defaultSeasonForVideos(videos) ?: seasons.firstOrNull()
                                val episodes = videos.mapIndexed { index, video -> video.toEpisodeOption(index) }
                                if (streams.value.isOpen) {
                                    streams.value = streams.value.copy(
                                        isLoading = false,
                                        episodes = episodes,
                                        seasons = seasons,
                                        selectedSeason = defaultSeason,
                                    )
                                }
                            }
                        }
                }
                withTimeoutOrNull(15_000) { collector.join() }
                if (streams.value.isOpen && streams.value.isLoading) {
                    streams.value = streams.value.copy(
                        isLoading = false,
                        error = if (streams.value.episodes.isEmpty()) "No episodes found." else null,
                    )
                }
            } else {
                val collector = launch {
                    catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = true)
                        .collect { details ->
                            val options = catalogRepository.extractStreams(details).mapIndexed { index, coreStream ->
                                buildStreamOption(index, coreStream)
                            }
                            if (streams.value.isOpen) {
                                streams.value = streams.value.copy(
                                    streams = options,
                                    releaseDateLabel = movieReleaseDate(details),
                                    isLoading = options.isEmpty(),
                                )
                            }
                        }
                }
                withTimeoutOrNull(30_000) { collector.join() }
                if (streams.value.isOpen && streams.value.isLoading) {
                    streams.value = streams.value.copy(
                        isLoading = false,
                        error = if (streams.value.streams.isEmpty()) "No streams found. Install a stream addon to watch." else null,
                    )
                }
            }
        }
    }

    fun selectEpisode(episode: EpisodeOption) {
        val item = streams.value.forItem ?: return
        streamsJob?.cancel()
        streams.value = streams.value.copy(
            selectedVideoId = episode.videoId,
            selectedEpisodeLabel = episodeDisplayLabel(episode.season, episode.episode, episode.title),
            releaseDateLabel = episode.releaseDate,
            isLoading = true,
            streams = emptyList(),
            error = null,
        )
        streamsJob = viewModelScope.launch {
            runCatching { startServerInternal() }
            val collector = launch {
                catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = episode.videoId, guessStreamPath = false)
                    .collect { details ->
                        val options = catalogRepository.extractStreams(details).mapIndexed { index, coreStream ->
                            buildStreamOption(index, coreStream)
                        }
                        if (streams.value.isOpen) {
                            streams.value = streams.value.copy(
                                streams = options,
                                isLoading = options.isEmpty(),
                            )
                        }
                    }
            }
            withTimeoutOrNull(30_000) { collector.join() }
            if (streams.value.isOpen && streams.value.isLoading) {
                streams.value = streams.value.copy(
                    isLoading = false,
                    error = if (streams.value.streams.isEmpty()) "No streams found. Install a stream addon to watch." else null,
                )
            }
        }
    }

    fun selectSeason(season: Int) {
        streams.value = streams.value.copy(selectedSeason = season)
    }

    fun selectStreamProvider(provider: String?) {
        streams.value = streams.value.copy(selectedProvider = provider)
    }

    fun selectStreamSortCriterion(criterion: StreamSortCriterion) {
        streams.value = streams.value.copy(sortCriterion = criterion)
    }

    fun backToEpisodes() {
        streams.value = streams.value.copy(
            selectedVideoId = null,
            selectedEpisodeLabel = null,
            releaseDateLabel = null,
            streams = emptyList(),
            error = null,
        )
    }

    private fun defaultSeasonForVideos(videos: List<com.stremio.core.types.resource.Video>): Int? {
        val currentVideo = videos.firstOrNull { it.currentVideo }
        val lastProgressedVideo = videos
            .filter { it.watched || (it.progress ?: 0.0) > 0.0 }
            .maxWithOrNull(
                compareBy<com.stremio.core.types.resource.Video> { it.seriesInfo?.season ?: 0L }
                    .thenBy { it.seriesInfo?.episode ?: 0L }
            )
        return (currentVideo ?: lastProgressedVideo)?.seriesInfo?.season?.toInt()
    }

    private fun movieReleaseDate(details: com.stremio.core.models.MetaDetails): String? {
        val content = details.metaItem?.content
        return if (content is com.stremio.core.models.LoadableMetaItem.Content.Ready) {
            formatReleaseDate(content.value.released) ?: content.value.releaseInfo
        } else {
            streams.value.forItem?.releaseInfo
        }
    }

    private data class EpisodeDisplayInfo(
        val label: String,
        val releaseDate: String?,
    )

    private fun episodeDisplayInfo(
        details: com.stremio.core.models.MetaDetails,
        videoId: String,
    ): EpisodeDisplayInfo? {
        return catalogRepository.extractVideos(details)
            .firstOrNull { it.id == videoId }
            ?.let { video ->
                EpisodeDisplayInfo(
                    label = episodeDisplayLabel(
                        season = video.seriesInfo?.season?.toInt(),
                        episode = video.seriesInfo?.episode?.toInt(),
                        title = video.title,
                    ) ?: video.title,
                    releaseDate = formatReleaseDate(video.released),
                )
            }
    }

    private fun episodeDisplayLabel(season: Int?, episode: Int?, title: String?): String? {
        val cleanTitle = title?.takeIf { it.isNotBlank() }
        val number = if ((season ?: 0) > 0 && (episode ?: 0) > 0) {
            "S${season}E${episode}"
        } else {
            null
        }
        return listOfNotNull(cleanTitle, number)
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }

    private fun playbackDisplayTitle(): String? {
        val current = streams.value
        val item = current.forItem ?: return null
        return if (current.isSeries) {
            current.selectedEpisodeLabel?.takeIf { it.isNotBlank() } ?: item.name
        } else {
            item.name
        }
    }

    private fun formatReleaseDate(timestamp: pbandk.wkt.Timestamp?): String? {
        val seconds = timestamp?.seconds ?: return null
        if (seconds <= 0L) return null
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(seconds * 1000L))
    }

    private fun buildStreamOption(index: Int, coreStream: CoreStream): StreamOption {
        return coreStream.toStreamOption(index)
    }

    fun closeStreams() {
        streamsJob?.cancel()
        streamsJob = null
        streams.value = StreamsUiState()
    }

    fun playStream(option: StreamOption) {
        if (isMobileDataWarning.value && isUsingMobileData()) {
            pendingMobileDataStream.value = option
        } else {
            proceedWithPlayback(option)
        }
    }

    fun confirmMobileDataPlayback() {
        val option = pendingMobileDataStream.value ?: return
        pendingMobileDataStream.value = null
        proceedWithPlayback(option)
    }

    fun cancelMobileDataPlayback() {
        pendingMobileDataStream.value = null
    }

    private fun isUsingMobileData(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun proceedWithPlayback(option: StreamOption) {
        playJob?.cancel()
        subtitleObserverJob?.cancel()
        streams.value = streams.value.copy(isResolving = true, error = null)
        playJob = viewModelScope.launch {
            val engine = PlayerEngine.fromProfileValue(profileSettings.value?.playerType)
            if (streamRequiresLocalServer(option)) {
                runCatching { startServerInternal() }
                    .onFailure { error ->
                        val errorMsg = "Streaming server failed to start: ${error.message ?: "Unknown error"}"
                        com.posthog.PostHog.capture(
                            event = "Stream Playback Failed",
                            properties = mapOf(
                                "player_engine" to engine.name,
                                "error_message" to errorMsg
                            )
                        )
                        streams.value = streams.value.copy(
                            isResolving = false,
                            error = errorMsg,
                        )
                        return@launch
                    }
            }
            val loaded = playbackRepository.resolveAndLoadStream(
                option = option,
                engine = engine,
                displayTitle = playbackDisplayTitle(),
            )
            if (!loaded) {
                com.posthog.PostHog.capture(
                    event = "Stream Playback Failed",
                    properties = mapOf(
                        "player_engine" to engine.name,
                        "error_message" to "Could not resolve a playable stream."
                    )
                )
                streams.value = streams.value.copy(isResolving = false, error = "Could not resolve a playable stream.")
                return@launch
            }
            com.posthog.PostHog.capture(
                event = "Stream Playback Started",
                properties = mapOf(
                    "stream_type" to option.addonTitle,
                    "player_engine" to engine.name,
                    "is_resolving" to streamRequiresLocalServer(option)
                )
            )
            rememberLocalStreamSelection(option)
            lastPlayedOption = option
            nextVideo.value = null
            showNextVideoPopup.value = false
            dismissedNextVideoId = null
            lastTimeReportMs = 0L
            showNoSeedsBanner.value = false
            noSeedsReason.value = null
            unhealthySinceMs = null
            streams.value = streams.value.copy(isResolving = false, isOpen = false)
            playerOpen.value = true
            observeAddonSubtitlesForPlayback()
            startHealthWatch(option)
        }
    }

    private fun streamRequiresLocalServer(option: StreamOption): Boolean {
        if (option.core.stream.source is com.stremio.core.types.resource.Stream.Source.Tramvai) {
            return true
        }
        val directUrl = runCatching { core.directUrl(option.core.stream) }.getOrNull()
        return directUrl?.startsWith(StremioCore.STREAMING_SERVER_BASE) == true
    }

    private fun rememberLocalStreamSelection(option: StreamOption) {
        val currentStreams = streams.value
        val item = currentStreams.forItem ?: return
        val videoId = currentStreams.selectedVideoId
            ?: item.continueWatchingVideoId
            ?: item.id
        authRepository.rememberLocalStreamSelection(item.type, item.id, videoId, option)
    }

    fun closePlayer() {
        cancelTvNextEpisodePrefetch("playback-closed")
        playJob?.cancel()
        playJob = null
        nextVideoJob?.cancel()
        nextVideoJob = null
        subtitleObserverJob?.cancel()
        subtitleObserverJob = null
        noSeedsPollJob?.cancel()
        noSeedsPollJob = null
        playbackRepository.release()
        playerOpen.value = false
        nextVideo.value = null
        showNextVideoPopup.value = false
        showNoSeedsBanner.value = false
        noSeedsReason.value = null

        lastServerActivityMs = 0L
        viewModelScope.launch {
            stopServerIfIdle()
        }
    }

    /** Polls live torrent health for a torrent stream and surfaces a fallback when it's dead/too slow. */
    private fun startHealthWatch(option: StreamOption) {
        noSeedsPollJob?.cancel()
        unhealthySinceMs = null
        val source = option.core.stream.source
        if (source !is com.stremio.core.types.resource.Stream.Source.Tramvai) return
        val infoHash = source.value.infoHash
        val fileIndex = source.value.fileIdx ?: StremioCore.STREAMING_SERVER_AUTO_FILE_INDEX

        noSeedsPollJob = viewModelScope.launch {
            while (true) {
                playbackRepository.requestStreamStatistics(infoHash, fileIndex)
                // Local JNI/HTTP roundtrip to the on-device streaming server; comfortably finishes well under this.
                delay(800)
                val stats = playbackRepository.getStreamStatistics()
                if (stats != null && stats.infoHash == infoHash) {
                    val tooFewSeeds = stats.peers < minSeedsThreshold.value
                    val tooSlow = minDownloadSpeedBps.value > 0 && stats.downloadSpeed < minDownloadSpeedBps.value
                    val isNearComplete = stats.streamProgress >= 0.95
                    if ((tooFewSeeds || tooSlow) && !isNearComplete) {
                        val since = unhealthySinceMs ?: System.currentTimeMillis().also { unhealthySinceMs = it }
                        if (System.currentTimeMillis() - since >= 20_000) {
                            val reason = if (tooFewSeeds) "No seeds found for this stream." else "This stream is downloading too slowly."
                            if (isAutoSwitchOnDeadStream.value && playNextBestStream()) {
                                return@launch
                            }
                            noSeedsReason.value = reason
                            showNoSeedsBanner.value = true
                        }
                    } else {
                        unhealthySinceMs = null
                        showNoSeedsBanner.value = false
                    }
                }
                delay(2_200)
            }
        }
    }

    /** Switches to the best remaining candidate stream, ranked by seeds then closeness to the preferred quality. Returns false if there was nothing else to try. */
    fun playNextBestStream(): Boolean {
        val current = lastPlayedOption
        val candidates = streams.value.streams.filter { it.key != current?.key }
        val best = candidates.sortedWith(
            compareByDescending<StreamOption> { parseSeedCount(it.seeds) }
                .thenByDescending { qualityScore(it.quality, preferredQuality.value) }
        ).firstOrNull() ?: return false
        showNoSeedsBanner.value = false
        playStream(best)
        return true
    }

    /** Called every ~500ms by [PlayerScreen]'s position tracker. */
    fun onPlayerTick(positionMs: Long, durationMs: Long) {
        lastServerActivityMs = System.currentTimeMillis()
        if (durationMs <= 0) return

        if (positionMs - lastTimeReportMs >= 5_000 || lastTimeReportMs == 0L) {
            lastTimeReportMs = positionMs
            playbackRepository.reportTimeChanged(positionMs, durationMs)
        }

        val next = nextVideo.value ?: playbackRepository.getNextVideo()?.also { nextVideo.value = it }
        if (next == null) {
            showNextVideoPopup.value = false
            return
        }

        val notificationDurationMs = profileSettings.value?.nextVideoNotificationDuration ?: return
        val remainingMs = durationMs - positionMs
        showNextVideoPopup.value = remainingMs in 0..notificationDurationMs && dismissedNextVideoId != next.id
    }

    fun onPlayerSeek(positionMs: Long, durationMs: Long) {
        lastTimeReportMs = positionMs
        playbackRepository.reportSeek(positionMs, durationMs)
    }

    fun onPlayerPausedChanged(paused: Boolean) {
        playbackRepository.reportPausedChanged(paused)
    }

    fun dismissNextVideoPopup() {
        dismissedNextVideoId = nextVideo.value?.id
        showNextVideoPopup.value = false
    }

    /** Called when ExoPlayer reaches the end of the current stream. */
    fun onPlaybackEnded() {
        playbackRepository.reportEnded()
        val next = nextVideo.value
        if (profileSettings.value?.bingeWatching == true && next != null) {
            playNextVideo(next)
        } else {
            closePlayer()
        }
    }

    /** Plays [video] next: re-resolves its streams and auto-picks one, skipping the streams sheet. */
    fun playNextVideo(video: com.stremio.core.types.resource.Video) {
        val item = streams.value.forItem ?: return
        playbackRepository.reportNextVideo()
        showNextVideoPopup.value = false
        streams.value = streams.value.copy(
            selectedVideoId = video.id,
            selectedEpisodeLabel = episodeDisplayLabel(
                season = video.seriesInfo?.season?.toInt(),
                episode = video.seriesInfo?.episode?.toInt(),
                title = video.title,
            ),
            releaseDateLabel = formatReleaseDate(video.released),
        )
        nextVideoJob?.cancel()
        nextVideoJob = viewModelScope.launch {
            runCatching { startServerInternal() }
            val collector = launch {
                catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = video.id, guessStreamPath = false)
                    .collect { details ->
                        val options = catalogRepository.extractStreams(details).mapIndexed { index, coreStream ->
                            buildStreamOption(index, coreStream)
                        }
                        if (options.isNotEmpty()) {
                            val preferred = lastPlayedOption?.let { last ->
                                options.firstOrNull { it.addonTitle == last.addonTitle }
                            }
                            playStream(preferred ?: options.first())
                            nextVideoJob?.cancel()
                        }
                    }
            }
            withTimeoutOrNull(30_000) { collector.join() }
        }
    }

    fun attachPlayerView(view: android.view.View) = playbackRepository.attachView(view)

    fun detachPlayerView() = playbackRepository.detachView()

    fun getSubtitlePrefs(): Pair<Int, Int> = playbackRepository.getSubtitlePrefs()

    fun updateSubtitlePrefs(sizePercent: Int, offsetPercent: Int) {
        playbackRepository.updateSubtitlePrefs(sizePercent, offsetPercent)
    }

    fun getPlayerStreamState(): com.stremio.core.models.Player.StreamState? =
        playbackRepository.getPlayerStreamState()

    fun rememberAudioTrack(track: PlayerTrackOption) {
        playbackRepository.rememberAudioTrack(track)
    }

    fun rememberSubtitleTrack(track: PlayerTrackOption) {
        playbackRepository.rememberSubtitleTrack(track)
    }

    fun rememberSubtitlesDisabled() {
        playbackRepository.rememberSubtitlesDisabled()
    }

    fun rememberSubtitleStyle(style: PlayerSubtitleStyle) {
        playbackRepository.rememberSubtitleStyle(style)
    }

    fun importLocalSubtitle(uri: Uri) {
        viewModelScope.launch {
            val track = withContext(Dispatchers.IO) { copyLocalSubtitle(uri) } ?: return@launch
            playbackRepository.addLocalSubtitle(track)
        }
    }

    fun getPlayer(): com.stremio.mobile.player.Player? = playbackRepository.getPlayer()

    fun acceptIntent(intent: Intent?) {
        latestIntentUri.value = intent?.dataString
    }

    fun startServer() {
        viewModelScope.launch {
            runCatching { startServerInternal() }
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            serverController.stop()
        }
    }

    fun onAppForegrounded() {
        // No-op for now, symmetry/future use
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            stopServerIfIdle()
        }
    }

    fun checkServerWorking() {
        viewModelScope.launch {
            isPingLoading.value = true
            serverPingStatus.value = "Checking..."
            val currentState = serverController.state.value
            if (currentState is StreamingServerState.Ready) {
                val url = "${currentState.baseUrl}/heartbeat"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            val text = connection.inputStream.bufferedReader().use { it.readText() }
                            text.contains("success")
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
                if (ok) {
                    serverPingStatus.value = "Online"
                } else {
                    serverPingStatus.value = "Offline (no response)"
                }
            } else {
                serverPingStatus.value = "Server is not started"
            }
            isPingLoading.value = false
        }
    }

    fun refreshCatalogs() {
        requestedRequests.clear()
        isBoardLoading.value = true
        boardRepository.loadBoard()
    }

    fun selectSection(section: MainSection) {
        selectedSection.value = section
        isDiscoverSeeAll.value = false
        if (section == MainSection.Settings) {
            fetchStreamingServerSettingsDirectly()
        }
        if (section == MainSection.Library) {
            refreshLibrary()
        }
        if (section == MainSection.Discover) {
            val discover = catalogRepository.getDiscover()
            var req = discoverCatalogRequest.value
            if (req == null) {
                req = preferredDiscoverRequest(
                    coreSelectedRequest = discover.selected?.request,
                    types = discover.selectable.types.map { DiscoverSelectableRequest(it.selected, it.request) },
                    catalogs = discover.selectable.catalogs.map { DiscoverSelectableRequest(it.selected, it.request) },
                )
            }
            if (req != null && discover.selected?.request != req) {
                discoverCatalogRequest.value = req
                discoverCatalogTitle.value = "Discover"
                catalogRepository.loadDiscover(req)
            }
        }
    }

    fun search(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            searchResults.value = CatalogShelf(title = "Search", isLoading = false)
            return
        }
        searchResults.value = CatalogShelf(title = "Search", isLoading = true)
        searchShelves.value = emptyList()
        searchJob = viewModelScope.launch {
            delay(300)
            val collector = launch {
                    catalogRepository.search(trimmed)
                    .map { board ->
                        board to boardRepository.extractBoardShelves(board).filter {
                            it.items.isNotEmpty() || it.isLoading || it.error != null
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .collect { (board, shelves) ->
                        val toPreload = board.catalogs
                            .flatMap { catalog -> catalog.pages.filter { it.content == null } }
                            .map { it.request.toString() }
                            .filter { it !in requestedRequests }
                        if (board.catalogs.isNotEmpty() && toPreload.isNotEmpty()) {
                            requestedRequests.addAll(toPreload)
                            catalogRepository.loadSearchRange(0, minOf(board.catalogs.size - 1, 14))
                        }
                        searchShelves.value = shelves
                        val items = shelves.flatMap { it.items }.distinctBy { "${it.type}-${it.id}" }
                        searchResults.value = CatalogShelf(
                            title = "Search",
                            items = items,
                            isLoading = false
                        )
                    }
            }
            withTimeoutOrNull(15_000) { collector.join() }
            if (searchResults.value.isLoading) {
                searchResults.value = searchResults.value.copy(
                    isLoading = false,
                    error = if (searchShelves.value.isEmpty()) "No results for \"$trimmed\"." else null,
                )
            }
            com.posthog.PostHog.capture(
                event = "Content Searched",
                properties = mapOf(
                    "query_length" to trimmed.length,
                    "has_results" to searchResults.value.items.isNotEmpty()
                )
            )
        }
    }

    /** TV remote search waits for at least two non-space Unicode code points before Core work. */
    fun searchFromTv(query: String) {
        val normalized = query.trim()
        searchQuery.value = query
        if (normalized.codePointCount(0, normalized.length) >= 2) {
            search(query)
            return
        }
        searchJob?.cancel()
        searchJob = null
        searchResults.value = CatalogShelf(title = "Search", isLoading = false)
        searchShelves.value = emptyList()
    }

    fun openSearch() {
        isSearchOpen.value = true
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        isSearchOpen.value = false
        searchQuery.value = ""
        searchResults.value = CatalogShelf(title = "Search", isLoading = false)
        searchShelves.value = emptyList()
    }

    fun openDetails(item: CatalogItem) {
        val activatedNanos = SystemClock.elapsedRealtimeNanos()
        detailsActivatedNanos = activatedNanos
        if (BuildConfig.DEBUG) Log.d("TvDetailsTrace", "phase=activation trace=$activatedNanos elapsedRealtimeNanos=$activatedNanos")
        detailsJob?.cancel()
        detailsLibraryActionJob?.cancel()
        isDetailsLibraryActionLoading = false
        detailsLibraryOverride = null
        _tvDetailsUiState.value = TvDetailsUiState(
            details = detailsWhileLoading(item),
            detailsActivatedNanos = activatedNanos,
            isInLibrary = isDetailsItemInLibrary(
                item = item,
                libraryItems = library.value.items,
                libraryIsAuthoritative = hasAuthoritativeLibraryMembership,
            ),
        )
        selectedDetails.value = detailsWhileLoading(item)
        detailsJob = viewModelScope.launch {
            var previewFallbackActivated = false
            val previewFallbackJob = launch {
                delay(TV_DETAILS_PREVIEW_ACTION_FALLBACK_MS)
                if (
                    detailsActivatedNanos == activatedNanos &&
                    selectedDetails.value?.item?.let { it.id == item.id && it.type == item.type } == true &&
                    selectedDetails.value?.isLoading == true
                ) {
                    previewFallbackActivated = true
                    publishTvDetails(detailsWhileLoading(item).copy(isLoading = false))
                    if (BuildConfig.DEBUG) {
                        Log.w(
                            "TvDetailsTrace",
                            "phase=preview-actions-unblocked trace=$activatedNanos elapsedMs=${(SystemClock.elapsedRealtimeNanos() - activatedNanos) / 1_000_000}",
                        )
                    }
                }
            }
            try {
                var lastLoggedPhase: String? = null
                catalogRepository.getMetaDetailsFlow(type = item.type, id = item.id, videoId = null, guessStreamPath = false)
                    .collect { details ->
                        val content = details.metaItem?.content
                        val phase = when (content) {
                            null -> "meta-pending"
                            is com.stremio.core.models.LoadableMetaItem.Content.Loading -> "meta-loading"
                            is com.stremio.core.models.LoadableMetaItem.Content.Ready -> "meta-ready"
                            is com.stremio.core.models.LoadableMetaItem.Content.Error -> "meta-error"
                        }
                        if (BuildConfig.DEBUG && phase != lastLoggedPhase) {
                            lastLoggedPhase = phase
                            Log.d(
                                "TvDetailsTrace",
                                "phase=$phase trace=$activatedNanos elapsedMs=${(SystemClock.elapsedRealtimeNanos() - activatedNanos) / 1_000_000} providerStates=${details.streams.size}",
                            )
                        }
                        val metaItem = details.metaItem
                        if (metaItem == null) {
                            publishTvDetails(detailsWhileLoading(item).copy(isLoading = !previewFallbackActivated))
                            return@collect
                        }
                        when (val metaContent = metaItem.content) {
                            is com.stremio.core.models.LoadableMetaItem.Content.Loading -> {
                                publishTvDetails(detailsWhileLoading(item).copy(isLoading = !previewFallbackActivated))
                            }
                            is com.stremio.core.models.LoadableMetaItem.Content.Error -> {
                                publishTvDetails(detailsWhileLoading(item).copy(isLoading = false, error = metaContent.value.message))
                            }
                            is com.stremio.core.models.LoadableMetaItem.Content.Ready -> {
                                val meta = metaContent.value
                                if (meta.id != item.id || meta.type != item.type) return@collect
                                if (BuildConfig.DEBUG && auditedTvDetailsIds.add("${meta.type}:${meta.id}") && meta.videos.isNotEmpty()) {
                                    val videos = meta.videos
                                    val progress = videos.mapNotNull { it.progress }
                                    Timber.tag("TvMetaDetailsAudit").d(
                                        "%s videos=%d seasons=%s missingSeriesInfo=%d specials=%s thumbnails=%d overviews=%d released=%d upcoming=%d watched=%d current=%d progressCount=%d progressValues=%s progressRange=%s embeddedStreams=%d videosWithStreams=%d",
                                        meta.id,
                                        videos.size,
                                        videos.mapNotNull { it.seriesInfo?.season }.distinct().sorted(),
                                        videos.count { it.seriesInfo == null },
                                        videos.any { it.seriesInfo?.season == 0L },
                                        videos.count { !it.thumbnail.isNullOrBlank() },
                                        videos.count { !it.overview.isNullOrBlank() },
                                        videos.count { it.released != null },
                                        videos.count { it.upcoming },
                                        videos.count { it.watched },
                                        videos.count { it.currentVideo },
                                        progress.size,
                                        progress.distinct().sorted(),
                                        if (progress.isEmpty()) "none" else "${progress.minOrNull()}..${progress.maxOrNull()}",
                                        videos.sumOf { it.streams.size },
                                        videos.count { it.streams.isNotEmpty() },
                                    )
                                }
                                val trailer = meta.trailerStreams.firstOrNull()?.let { catalogRepository.directUrl(it) }
                                publishTvDetails(meta.toMetaDetails(item, trailer))
                            }
                            else -> publishTvDetails(detailsWhileLoading(item).copy(isLoading = !previewFallbackActivated))
                        }
                    }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publishTvDetails(detailsWhileLoading(item).copy(isLoading = false, error = error.message ?: "MetaDetails request failed"))
            } finally {
                previewFallbackJob.cancel()
            }
        }
    }

    /** TV-only source discovery. It deliberately avoids mobile openStreams/remembered autoplay. */
    internal fun openTvStreams(target: TvStreamTarget) = discoverTvStreams(target.withTrustedDuration(), smartPlay = false)

    internal fun playTvSmart(target: TvStreamTarget) = discoverTvStreams(target.withTrustedDuration(), smartPlay = true)

    private fun TvStreamTarget.withTrustedDuration(): TvStreamTarget = copy(
        durationSeconds = durationSeconds ?: selectedContentDurationSeconds(this),
    )

    private fun selectedContentDurationSeconds(target: TvStreamTarget): Long? {
        val details = selectedDetails.value ?: return null
        if (details.item.id != target.contentId || details.item.type != target.contentType) return null
        return details.runtime?.let(::parseTrustedDurationSeconds)
            ?: details.item.runtime?.let(::parseTrustedDurationSeconds)
    }

    private fun discoverTvStreams(target: TvStreamTarget, smartPlay: Boolean) {
        val discoveryStartedNanos = SystemClock.elapsedRealtimeNanos()
        warmTvDeviceCapabilities()
        tvStartupTrace = TvStartupTrace(
            userActivatedNanos = discoveryStartedNanos,
            discoveryStartedNanos = discoveryStartedNanos,
        )
        if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T1-discovery-started trace=${tvStartupTrace?.traceId} elapsedRealtimeNanos=$discoveryStartedNanos smartPlay=$smartPlay")
        tvSmartPlaybackSession = null
        tvSmartSettleJob?.cancel()
        tvSmartMaximumJob?.cancel()
        tvStreamsJob?.cancel()
        val previous = _tvStreamSelection.value.takeIf { it.target?.semanticTargetKey == target.semanticTargetKey }
        _tvStreamSelection.value = TvStreamSelectionUiState(
            target = target,
            selectedProvider = previous?.selectedProvider,
            selectedStreamKey = previous?.selectedStreamKey,
            isLoading = true,
            isActive = true,
            smartSelecting = smartPlay,
        )
        if (smartPlay) tvSmartMaximumJob = viewModelScope.launch {
            kotlinx.coroutines.delay(TV_SMART_MAX_DISCOVERY_MS)
            finishTvSmartSelection(target, "maximum-window")
        }
        tvStreamsJob = viewModelScope.launch {
            try {
                var lastDiscoverySignature: String? = null
                tvValidationFixtures?.optionsFor(target)?.let { options ->
                    if (_tvStreamSelection.value.target?.semanticTargetKey == target.semanticTargetKey) {
                        val selectedKey = keepSelectionIfPresent(previous?.selectedStreamKey, options) ?: options.first().semanticKey
                        _tvStreamSelection.value = TvStreamSelectionUiState(
                            target = target,
                            options = options,
                            selectedStreamKey = selectedKey,
                            isActive = true,
                            smartSelecting = smartPlay,
                        )
                    }
                    if (smartPlay) finishTvSmartSelection(target, "all-complete")
                    return@launch
                }
                catalogRepository.getMetaDetailsFlow(
                    type = target.contentType,
                    id = target.contentId,
                    videoId = target.videoId,
                    guessStreamPath = target.guessStreamPath,
                ).collect { details ->
                    if (_tvStreamSelection.value.target?.semanticTargetKey != target.semanticTargetKey) return@collect
                    if (!tvStreamTargetMatches(details, target)) {
                        if (BuildConfig.DEBUG) {
                            val selected = details.selected
                            val path = selected?.streamPath
                            val metaMatches = selected?.metaPath?.let { it.type == target.contentType && it.id == target.contentId } ?: false
                            val guessMatches = selected?.guessStreamPath == target.guessStreamPath
                            val pathResource = path?.resource ?: "none"
                            val pathTypeMatches = path?.type == target.contentType
                            val expectedPathId = target.videoId ?: target.contentId
                            val pathIdMatches = path?.id == expectedPathId
                            val rejectedEmission = mapTvStreamEmission(details)
                            val providerStates = rejectedEmission.providers.joinToString(",") {
                                "${it.title.take(48)}:${it.status.name.lowercase()}:${it.readyStreamCount}"
                            }
                            val signature = "target-drop|selected=${selected != null}|meta=$metaMatches|guess=$guessMatches|path=$pathResource|pathType=$pathTypeMatches|pathId=$pathIdMatches|providers=$providerStates|mapped=${rejectedEmission.options.size}|duplicates=${rejectedEmission.duplicateSemanticKeyCount}"
                            if (signature != lastDiscoverySignature) {
                                lastDiscoverySignature = signature
                                Log.d(
                                    "TvStreamDiscovery",
                                    "target=${target.contentType} videoIdPresent=${target.videoId != null} guessStreamPath=${target.guessStreamPath} " +
                                        "drop=target-match selected=${selected != null} metaIdentityMatch=$metaMatches guessMatch=$guessMatches " +
                                        "streamPathResource=$pathResource streamPathTypeMatch=$pathTypeMatches streamPathIdMatch=$pathIdMatches " +
                                        "requestCount=${rejectedEmission.providers.size} providerStates=$providerStates " +
                                        "coreReadyStreams=${rejectedEmission.providers.sumOf { it.readyStreamCount }} mappedStreams=${rejectedEmission.options.size} " +
                                        "duplicates=${rejectedEmission.duplicateSemanticKeyCount} torrents=${sourceKindCount(rejectedEmission.options, StreamSourceKind.Torrent)} " +
                                        "direct=${sourceKindCount(rejectedEmission.options, StreamSourceKind.Direct)}",
                                )
                            }
                        }
                        return@collect
                    }
                    val incoming = mapTvStreamEmission(details)
                    if (BuildConfig.DEBUG) {
                        val coreReadyStreams = incoming.providers.sumOf { it.readyStreamCount }
                        val providerStates = incoming.providers.joinToString(",") {
                            "${it.title.take(48)}:${it.status.name.lowercase()}:${it.readyStreamCount}"
                        }
                        val signature = "matched|$providerStates|${incoming.options.size}|${incoming.duplicateSemanticKeyCount}|${incoming.isLoading}"
                        if (signature != lastDiscoverySignature) {
                            lastDiscoverySignature = signature
                            Log.d(
                                "TvStreamDiscovery",
                                "target=${target.contentType} videoIdPresent=${target.videoId != null} requestCount=${incoming.providers.size} " +
                                    "providerStates=$providerStates coreReadyStreams=$coreReadyStreams mappedStreams=${incoming.options.size} " +
                                    "duplicates=${incoming.duplicateSemanticKeyCount} torrents=${sourceKindCount(incoming.options, StreamSourceKind.Torrent)} " +
                                    "direct=${sourceKindCount(incoming.options, StreamSourceKind.Direct)} external=${sourceKindCount(incoming.options, StreamSourceKind.External)} " +
                                    "loading=${incoming.isLoading}",
                            )
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        val readyOrder = tvStreamReadyOrder.getOrPut(target.semanticTargetKey) { linkedSetOf() }
                        incoming.providers.filter { it.status == TvProviderLoadStatus.Ready }.forEach { readyOrder += it.title }
                        val finalEmission = incoming.providers.isNotEmpty() && !incoming.isLoading
                        if (finalEmission && auditedTvStreamTargets.add(target.semanticTargetKey)) {
                            val auditOptions = incoming.options
                            val outcomes = incoming.providers.joinToString(",") {
                                "${it.title}:${it.status.name.lowercase()}:${it.readyStreamCount}"
                            }
                            Timber.tag("TvStreamAudit").d(
                                "targetType=%s episode=%s providerRequests=%d providers=%s arrivalOrder=%s streams=%d sourceKinds=direct:%d,torrent:%d,external:%d,youtube:%d,archive:%d,other:%d bingeGroup=%d filename=%d videoHash=%d videoSize=%d notWebReady=%d parsedQuality=%d seeds=%d size=%d duplicateSemanticKeys=%d",
                                target.contentType,
                                target.episodeLabel ?: "none",
                                incoming.providers.size,
                                outcomes,
                                tvStreamReadyOrder[target.semanticTargetKey].orEmpty().joinToString(","),
                                auditOptions.size,
                                sourceKindCount(auditOptions, StreamSourceKind.Direct),
                                sourceKindCount(auditOptions, StreamSourceKind.Torrent),
                                sourceKindCount(auditOptions, StreamSourceKind.External),
                                sourceKindCount(auditOptions, StreamSourceKind.YouTube),
                                sourceKindCount(auditOptions, StreamSourceKind.Archive),
                                sourceKindCount(auditOptions, StreamSourceKind.Other),
                                auditOptions.count { !it.bingeGroup.isNullOrBlank() },
                                auditOptions.count { !it.filename.isNullOrBlank() },
                                auditOptions.count { !it.videoHash.isNullOrBlank() },
                                auditOptions.count { it.videoSize != null },
                                auditOptions.count { it.notWebReady },
                                auditOptions.count { !it.quality.isNullOrBlank() },
                                auditOptions.count { !it.seeds.isNullOrBlank() },
                                auditOptions.count { !it.size.isNullOrBlank() },
                                incoming.duplicateSemanticKeyCount,
                            )
                            auditOptions.forEachIndexed { index, option ->
                                val metadata = parseStreamVideoMetadata(option, target.durationSeconds)
                                val stream = option.core.stream
                                val hints = stream.behaviorHints
                                val bitrate = metadata.bitrateMbps?.let {
                                    "${if (metadata.bitrateCalculatedFromSizeAndDuration) "average~" else "explicit"}:${"%.2f".format(java.util.Locale.ROOT, it)}"
                                } ?: "unknown"
                                Log.d(
                                    "TvStreamMetadata",
                                    "index=$index sourceKind=${option.sourceKind.name} resolution=${metadata.quality} " +
                                        "releaseType=${metadata.release.name} videoCodec=${metadata.codec.name} hdr=${metadata.hdr.name} " +
                                        "audioCodec=${metadata.audioCodec ?: "unknown"} audioFeatures=${metadata.audioFeatures.joinToString("+").ifBlank { "unknown" }} " +
                                        "channels=${metadata.channels ?: "unknown"} size=${metadata.sizeLabel ?: "unknown"} bitrateMbps=$bitrate " +
                                        "age=${metadata.age ?: "unknown"} languages=${metadata.languages.joinToString("+").ifBlank { "unknown"}} " +
                                        "subtitleLanguages=${metadata.subtitleLanguages.joinToString("+").ifBlank { "unknown"}} " +
                                        "releaseLabel=${metadata.releaseLabel?.let(::safeStreamPresentationText)?.take(40) ?: "unknown"} " +
                                        "presence_name=${stream.name != null} presence_description=${stream.description != null} " +
                                        "presence_thumbnail=${stream.thumbnail != null} presence_filename=${hints.filename != null} " +
                                        "presence_videoSize=${hints.videoSize != null} presence_bingeGroup=${hints.bingeGroup != null}",
                                )
                            }
                            auditOptions.filter { it.sourceKind == StreamSourceKind.Direct }.forEach { direct ->
                                val proxyHeaders = direct.core.stream.behaviorHints.proxyHeaders
                                Timber.tag("TvDirectAudit").d(
                                    "provider=%s quality=%s proxyHeaders=%s requestHeaderCount=%d responseHeaderCount=%d notWebReady=%s",
                                    direct.addonTitle.take(80), direct.quality?.take(32) ?: "unknown",
                                    if (proxyHeaders == null) "no" else "yes",
                                    proxyHeaders?.request?.size ?: 0,
                                    proxyHeaders?.response?.size ?: 0,
                                    direct.notWebReady,
                                )
                            }
                            auditOptions.filter { it.sourceKind == StreamSourceKind.Torrent }.forEach { torrent ->
                                val source = torrent.core.stream.source as? com.stremio.core.types.resource.Stream.Source.Tramvai
                                val metadata = source?.value
                                Timber.tag("TvTorrentAudit").d(
                                    "provider=%s quality=%s announceCount=%d fileMustIncludeCount=%d fileIdxPresent=%s infoHashPresent=%s",
                                    torrent.addonTitle.take(80), torrent.quality?.take(32) ?: "unknown",
                                    metadata?.announce?.size ?: 0,
                                    metadata?.fileMustInclude?.size ?: 0,
                                    metadata?.fileIdx != null,
                                    !metadata?.infoHash.isNullOrBlank(),
                                )
                            }
                        }
                    }
                    val current = _tvStreamSelection.value
                    val sameTarget = current.target?.semanticTargetKey == target.semanticTargetKey
                    val options = if (sameTarget) stableInteractionOptions(current.options, incoming.options) else incoming.options
                    if (options.isNotEmpty()) {
                        val trace = tvStartupTrace
                        if (trace != null) {
                            val firstCandidateNanos = trace.firstCandidateNanos ?: SystemClock.elapsedRealtimeNanos()
                            tvStartupTrace = trace.copy(
                                firstCandidateNanos = firstCandidateNanos,
                                candidateCount = options.size,
                            )
                            if (trace.firstCandidateNanos == null && BuildConfig.DEBUG) {
                                Log.d("TvPlaybackTrace", "phase=T2-first-candidate trace=${trace.traceId} elapsedRealtimeNanos=$firstCandidateNanos count=${options.size}")
                            }
                        }
                    }
                    val selectedProvider = selectProviderLocally(incoming.providers, current.selectedProvider)
                    _tvStreamSelection.value = current.copy(
                        options = options,
                        providers = incoming.providers,
                        selectedProvider = selectedProvider,
                        selectedStreamKey = keepSelectionIfPresent(current.selectedStreamKey, options),
                        isLoading = incoming.isLoading,
                        requestError = null,
                    )
                    if (smartPlay) {
                        if (!incoming.isLoading) finishTvSmartSelection(target, "all-complete")
                        else if (options.isNotEmpty() && tvSmartSettleJob == null) tvSmartSettleJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(TV_SMART_SETTLE_MS)
                            finishTvSmartSelection(target, "settled")
                        }
                    }
                }
                if (smartPlay) finishTvSmartSelection(target, "flow-complete")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_tvStreamSelection.value.target?.semanticTargetKey == target.semanticTargetKey) {
                    _tvStreamSelection.value = _tvStreamSelection.value.copy(
                        isLoading = false,
                        requestError = error.message?.take(180) ?: "Sources could not be loaded.",
                    )
                    if (smartPlay) finishTvSmartSelection(target, "discovery-error")
                }
            }
        }
    }

    private fun finishTvSmartSelection(target: TvStreamTarget, trigger: String) {
        val state = _tvStreamSelection.value
        if (!tvSmartResultApplies(state.target, target, state.smartSelecting)) return
        val complete = trigger in setOf("all-complete", "flow-complete", "debug-fixture", "discovery-error")
        val selectionNanos = SystemClock.elapsedRealtimeNanos()
        val elapsed = when (trigger) {
            "settled" -> TV_SMART_SETTLE_MS
            "maximum-window" -> TV_SMART_MAX_DISCOVERY_MS
            else -> 0L
        }
        if (!complete && !tvSmartShouldFinish(false, elapsed, state.options.isNotEmpty())) return
        tvStartupTrace = tvStartupTrace?.copy(
            candidateSnapshotNanos = selectionNanos,
            candidateCount = state.options.size,
        )
        if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T3-candidate-snapshot trace=${tvStartupTrace?.traceId} elapsedRealtimeNanos=$selectionNanos candidates=${state.options.size} trigger=$trigger")
        tvSmartSettleJob?.cancel(); tvSmartSettleJob = null
        tvSmartMaximumJob?.cancel(); tvSmartMaximumJob = null
        if (trigger == "settled" || trigger == "maximum-window") {
            tvStreamsJob?.cancel()
            tvStreamsJob = null
        }
        val hardwareDecoding = profileSettings.value?.hardwareDecoding ?: true
        val capabilityStartedNanos = SystemClock.elapsedRealtimeNanos()
        val cachedCapabilities = tvDeviceCapabilitySnapshots[hardwareDecoding]
        val deviceCaps = cachedCapabilities ?: AndroidPlaybackCapabilities.collect(appContext, hardwareDecoding).also {
            tvDeviceCapabilitySnapshots[hardwareDecoding] = it
        }
        val capabilityElapsedMs = (SystemClock.elapsedRealtimeNanos() - capabilityStartedNanos) / 1_000_000L
        val networkStartedNanos = SystemClock.elapsedRealtimeNanos()
        val networkProfile = networkSnapshot(appContext, playbackNetworkHistory)
        val networkElapsedMs = (SystemClock.elapsedRealtimeNanos() - networkStartedNanos) / 1_000_000L
        val rankingStartedNanos = SystemClock.elapsedRealtimeNanos()
        val result = TvSmartStreamSelector.select(state.options, preferredQuality.value, deviceCaps, networkProfile,
            target.durationSeconds ?: selectedContentDurationSeconds(target))
        val rankingElapsedMs = (SystemClock.elapsedRealtimeNanos() - rankingStartedNanos) / 1_000_000L
        val selectorReturnedNanos = SystemClock.elapsedRealtimeNanos()
        tvStartupTrace = tvStartupTrace?.copy(selectorReturnedNanos = selectorReturnedNanos)
        if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T4-selector-returned trace=${tvStartupTrace?.traceId} elapsedRealtimeNanos=$selectorReturnedNanos selected=${result.selected != null}")
        if (BuildConfig.DEBUG) Log.d("TvStartup", "phase=selector-inputs target=${target.contentType} capabilitySource=${if (cachedCapabilities == null) "synchronous" else "warm-cache"} capabilityMs=$capabilityElapsedMs networkMs=$networkElapsedMs rankMs=$rankingElapsedMs")
        if (BuildConfig.DEBUG) {
            Timber.tag("TvPlaybackCaps").d("displayCurrent=%s displayMax=%s supports2160p=%s avc4k=%s hevc4k=%s av1_4k=%s vp9_4k=%s hdr10=%s hdr10Plus=%s dolbyVision=%s hlg=%s",
                displaySize(deviceCaps.currentDisplayWidth, deviceCaps.currentDisplayHeight), displaySize(deviceCaps.maxDisplayWidth, deviceCaps.maxDisplayHeight), deviceCaps.supports2160pOutput,
                deviceCaps.avc.supports2160p, deviceCaps.hevc.supports2160p, deviceCaps.av1.supports2160p, deviceCaps.vp9.supports2160p,
                deviceCaps.hdr10, deviceCaps.hdr10Plus, deviceCaps.dolbyVision, deviceCaps.hlg)
            val age = networkProfile.measuredAtMs?.let { (android.os.SystemClock.elapsedRealtime() - it).coerceAtLeast(0) }
            Timber.tag("TvNetworkProfile").d("transport=%s estimateMbps=%s source=%s confidence=%s ageMs=%s",
                networkProfile.transport.name.lowercase(), networkProfile.estimatedThroughputBps?.div(1_000_000) ?: "unknown",
                networkProfile.estimateSource.name.lowercase(), networkProfile.confidence.name.lowercase(), age ?: "unknown")
            Timber.tag("TvSmartPlay").d("targetType=%s episode=%s trigger=%s candidates=%d", target.contentType, if (target.videoId == null) "no" else "yes", trigger, result.ranked.size)
            result.ranked.take(3).forEach { candidate ->
                Timber.tag("TvSmartPlay").d("rank=%d quality=%s codec=%s compatibility=%s network=%s source=%s seeds=%s reasons=%s", candidate.rank, candidate.quality, candidate.codec.name.lowercase(), candidate.compatibility.name.lowercase(), candidate.network.name.lowercase(), candidate.option.sourceKind.name.lowercase(), candidate.seedsBucket, candidate.reasons.joinToString(","))
            }
            Timber.tag("TvSmartPlay").d("selectedRank=%s reason=%s", result.ranked.firstOrNull()?.rank ?: "none", result.reason ?: "none")
        }
        if (result.selected != null) {
            tvSmartPlaybackSession = TvSmartPlaybackSession(target, result.ranked.map { it.option })
            if (BuildConfig.DEBUG) Log.d("TvSmartFallback", "session-start candidates=${result.ranked.size}")
            _tvStreamSelection.value = state.copy(smartSelecting = false, recommendedStreamKey = result.selected.semanticKey,
                selectedStreamKey = result.selected.semanticKey, isLoading = false)
            startTvPlayback(target, result.selected, smartSessionIndex = 0)
        } else _tvStreamSelection.value = state.copy(smartSelecting = false, isLoading = false)
    }

    private fun warmTvDeviceCapabilities() {
        val hardwareDecoding = profileSettings.value?.hardwareDecoding ?: true
        if (tvDeviceCapabilitySnapshots.containsKey(hardwareDecoding) || tvDeviceCapabilityWarmups[hardwareDecoding]?.isActive == true) return
        tvDeviceCapabilityWarmups[hardwareDecoding] = viewModelScope.launch(Dispatchers.Default) {
            val startedNanos = SystemClock.elapsedRealtimeNanos()
            val capabilities = AndroidPlaybackCapabilities.collect(appContext, hardwareDecoding)
            tvDeviceCapabilitySnapshots.putIfAbsent(hardwareDecoding, capabilities)
            if (BuildConfig.DEBUG) {
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000L
                Log.d("TvStartup", "phase=capabilities-prewarm-complete elapsedMs=$elapsedMs")
            }
        }
    }

    private fun displaySize(width: Int?, height: Int?) = if (width == null || height == null) "unknown" else "${width}x$height"
    internal fun playTvSmartValidationFixture() {
        tvStreamsJob?.cancel(); tvStreamsJob = null
        tvSmartSettleJob?.cancel(); tvSmartSettleJob = null
        tvSmartMaximumJob?.cancel(); tvSmartMaximumJob = null
        val current = _tvStreamSelection.value
        val target = current.target ?: return
        val options = tvValidationFixtures?.smartPlayOptions(target) ?: return
        _tvStreamSelection.value = current.copy(options = options, providers = emptyList(), selectedProvider = null,
            isLoading = false, isActive = true, smartSelecting = true)
        finishTvSmartSelection(target, "debug-fixture")
    }

    internal fun enableTvValidationMedia() {
        val target = _tvStreamSelection.value.target ?: return
        if (tvValidationFixtures?.enableForCurrentSequence(target) == true) openTvStreams(target)
    }

    internal fun armTvHoldAfterFirstVisual() {
        tvValidationFixtures?.armHoldAfterFirstVisual()
    }

    internal fun armTvMpvRequestedEngineValidation() {
        tvValidationFixtures?.armMpvRequestedEngine()
    }

    internal fun armTvIncompatibleConstructionValidation() {
        val armed = tvValidationFixtures?.armHardwareDecodingMismatch() == true
        if (BuildConfig.DEBUG && armed) Log.i("TvValidation", "incompatible-transition armed setting=hardwareDecoding media=fixture-B-and-C")
    }

    internal fun selectTvProvider(providerIdentity: String?) {
        val state = _tvStreamSelection.value
        _tvStreamSelection.value = state.copy(
            selectedProvider = selectProviderLocally(state.providers, providerIdentity),
        )
    }

    internal fun selectTvStream(semanticKey: String) {
        val state = _tvStreamSelection.value
        if (state.options.any { it.semanticKey == semanticKey }) {
            _tvStreamSelection.value = state.copy(selectedStreamKey = semanticKey)
        }
    }

    /** Starts the explicitly activated TV source without entering the mobile autoplay/health path. */
    internal fun startTvPlayback(target: TvStreamTarget, option: StreamOption, reuseExistingExo: Boolean = false, smartSessionIndex: Int? = null) {
        val smartSession = if (smartSessionIndex == null) {
            tvSmartPlaybackSession = null
            null
        } else {
            tvSmartPlaybackSession?.takeIf { session ->
                session.target.semanticTargetKey == target.semanticTargetKey &&
                    session.rankedCandidates.getOrNull(smartSessionIndex)?.semanticKey == option.semanticKey &&
                    !session.cancelled && !session.committed && option.semanticKey !in session.attemptedSemanticKeys
            } ?: return
        }
        cancelTvNextEpisodePrefetch("attempt-changed")
        tvPlaybackJob?.cancel()
        tvPlaybackMonitorJob?.cancel()
        tvStartupTimeoutJob?.cancel()
        tvTorrentStartupStatsJob?.cancel(); tvTorrentStartupStatsJob = null
        tvSegmentProviderJob?.cancel()
        latestTvCorePlayer = null
        val heldExoForReuse = reuseExistingExo && playbackRepository.getPlayer()?.engine == PlayerEngine.EXO
        if (heldExoForReuse) {
            playbackRepository.getPlayer()?.pause()
            playbackRepository.detachOutput()
        } else {
            playbackRepository.setPlaybackEventListener(null)
            playbackRepository.release()
        }

        val configuredEngine = PlayerEngine.fromProfileValue(profileSettings.value?.playerType)
        val requestedEngine = tvValidationFixtures?.requestedEngineFor(
            target.videoId ?: target.contentId,
            configuredEngine,
        ) ?: configuredEngine
        val serverRequired = streamRequiresLocalServer(option.core.stream)
        val attempt = TvPlaybackAttempt.create(target, option, requestedEngine, SystemClock.elapsedRealtimeNanos())
        val startup = tvStartupTrace ?: TvStartupTrace(userActivatedNanos = attempt.startedAtNanos)
        tvStartupTrace = startup.copy(
            attemptCount = startup.attemptCount + 1,
            winningRank = smartSessionIndex?.plus(1) ?: startup.winningRank,
        )
        if (smartSession != null) {
            tvSmartPlaybackSession = smartSession.start(smartSessionIndex!!, attempt.attemptId) ?: return
        }
        tvReusePendingAttemptId = attempt.attemptId.takeIf { heldExoForReuse }
        if (reuseExistingExo && BuildConfig.DEBUG) {
            Log.d("PlaybackReuse", "next-episode requested attempt=${attempt.attemptId} previousAttempt=${_tvPlayback.value.playbackAttemptId ?: "unknown"} media=${target.videoId ?: target.contentId}")
        }
        tvEpisodeTransition.trace?.let { transition ->
            if (transition.targetKey == target.semanticTargetKey) {
                tvEpisodeTransition.sourceActivated(transition.oldAttemptId, attempt.attemptId, attempt.startedAtNanos)
                    ?.let(::logTvEpisodeTransition)
            } else {
                tvEpisodeTransition.reset()
            }
        }
        val selectedState = _tvStreamSelection.value
        if (selectedState.target?.semanticTargetKey == target.semanticTargetKey) {
            _tvStreamSelection.value = selectedState.copy(selectedStreamKey = option.semanticKey)
        }
        _tvPlayback.value = TvPlaybackUiState(
            attempt = attempt,
            option = option,
            stage = TvPlaybackStage.Resolving,
            requestedEngine = requestedEngine,
            timing = TvPlaybackTiming(
                userSourceActivatedNanos = attempt.startedAtNanos,
                userPlayActivatedNanos = startup.userActivatedNanos,
                discoveryStartedNanos = startup.discoveryStartedNanos,
                firstCandidateNanos = startup.firstCandidateNanos,
                candidateSnapshotNanos = startup.candidateSnapshotNanos,
                selectorReturnedNanos = startup.selectorReturnedNanos,
            ),
            nextEpisode = nextEpisodeStateFor(attempt.attemptId, target),
            fallbackProgress = if (smartSessionIndex != null && smartSessionIndex > 0)
                "Source ${smartSessionIndex + 1} of ${smartSession?.rankedCandidates?.size ?: 0}" else null,
        )
        lastTvTimeReportNanos = 0L
        if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T5-source-activated trace=${tvStartupTrace?.traceId} attempt=${attempt.attemptId} elapsedRealtimeNanos=${attempt.startedAtNanos}")
        Log.i("TvPlaybackTrace", safeTvPlaybackTrace(attempt, "source-activated", server = if (serverRequired) "pending" else "not-required"))
        if (smartSessionIndex != null && BuildConfig.DEBUG) {
            val seeds = option.seeds?.filter(Char::isDigit)?.toIntOrNull()?.let { if (it >= 100) "100+" else it.toString() } ?: "unknown"
            Log.d("TvSmartFallback", "attempt rank=${smartSessionIndex + 1} quality=${option.quality ?: "unknown"} source=${option.sourceKind.name.lowercase()} seeds=$seeds")
        }
        tvStartupTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(TV_SMART_STARTUP_TIMEOUT_MS)
            val state = _tvPlayback.value
            if (isCurrentTvAttempt(state, attempt.attemptId) && !state.firstVisualObserved) {
                failTvPlayback(attempt, "Couldn’t start this source", TvPlaybackFailureReason.StartupTimeout)
            }
        }

        tvPlaybackJob = viewModelScope.launch {
            try {
                if (serverRequired) {
                    val previous = safeServerState(serverController.state.value)
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    _tvPlayback.value = _tvPlayback.value.copy(serverStatus = "start-started")
                    _tvPlayback.value = _tvPlayback.value.copy(timing = _tvPlayback.value.timing.copy(serverStartRequestedNanos = startedAt))
                    if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T6-server-start-requested trace=${tvStartupTrace?.traceId} attempt=${attempt.attemptId} elapsedRealtimeNanos=$startedAt")
                    Log.i("TvPlaybackTrace", safeTvPlaybackTrace(attempt, "server-start-started", server = "start-started previous=$previous"))
                    try {
                        startServerInternal()
                    } catch (_: Exception) {
                        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
                        val resulting = safeServerState(serverController.state.value)
                        _tvPlayback.value = _tvPlayback.value.copy(serverStatus = "start-failed")
                        Log.w(
                            "TvPlaybackTrace",
                            safeTvPlaybackTrace(attempt, "server-start-failed", server = "start-failed previous=$previous resulting=$resulting startupMs=$elapsedMs reason=${safeServerFailureCategory(serverController.state.value)}"),
                        )
                        throw PlaybackResolutionException(PlaybackResolutionFailure.StreamingServerStartFailed)
                    }
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
                    val resulting = safeServerState(serverController.state.value)
                    _tvPlayback.value = _tvPlayback.value.copy(
                        serverStatus = "ready",
                        timing = _tvPlayback.value.timing.copy(serverReadyNanos = SystemClock.elapsedRealtimeNanos()),
                    )
                    if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T7-server-ready trace=${tvStartupTrace?.traceId} attempt=${attempt.attemptId} elapsedRealtimeNanos=${_tvPlayback.value.timing.serverReadyNanos}")
                    Log.i("TvPlaybackTrace", safeTvPlaybackTrace(attempt, "server-ready", server = "ready previous=$previous resulting=$resulting startupMs=$elapsedMs"))
                } else {
                    _tvPlayback.value = _tvPlayback.value.copy(serverStatus = "not-required")
                }
                val loaded = playbackRepository.resolveAndLoadStream(
                    option = option,
                    engine = requestedEngine,
                    displayTitle = listOfNotNull(target.contentName, target.episodeLabel).joinToString(" · "),
                    attemptId = attempt.attemptId,
                    mediaId = target.videoId ?: target.contentId,
                    reuseExoPlayer = reuseExistingExo,
                    eventListener = { event -> onTvPlaybackEvent(attempt, event) },
                    onEvent = { event ->
                        if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return@resolveAndLoadStream
                        if (BuildConfig.DEBUG) {
                            val phase = when (event.stage) {
                                PlaybackRepository.PlaybackLoadStage.ResolutionStarted -> "T8-core-resolve-started"
                                PlaybackRepository.PlaybackLoadStage.PlayableSourceResolved -> "T9-core-resolve-complete"
                                PlaybackRepository.PlaybackLoadStage.PlayerLoadStarted -> "T10-playback-manager-load-started"
                                PlaybackRepository.PlaybackLoadStage.PlayerLoadReturned -> "T10-playback-manager-load-returned"
                            }
                            Log.d("TvPlaybackTrace", "phase=$phase trace=${tvStartupTrace?.traceId} attempt=${attempt.attemptId} elapsedRealtimeNanos=${event.monotonicNanos}")
                        }
                        if (event.stage == PlaybackRepository.PlaybackLoadStage.PlayableSourceResolved && BuildConfig.DEBUG) {
                            Log.d("PlaybackReuse", "source-resolved attempt=${attempt.attemptId} media=${target.videoId ?: target.contentId} kind=${event.source?.resolutionKind ?: "core"}")
                        }
                        _tvPlayback.value = _tvPlayback.value.copy(
                            stage = when (event.stage) {
                                PlaybackRepository.PlaybackLoadStage.ResolutionStarted -> TvPlaybackStage.Resolving
                                PlaybackRepository.PlaybackLoadStage.PlayerLoadStarted,
                                PlaybackRepository.PlaybackLoadStage.PlayerLoadReturned -> TvPlaybackStage.Preparing
                                PlaybackRepository.PlaybackLoadStage.PlayableSourceResolved -> TvPlaybackStage.Resolving
                            },
                            timing = when (event.stage) {
                                PlaybackRepository.PlaybackLoadStage.ResolutionStarted -> _tvPlayback.value.timing.copy(resolutionStartedNanos = event.monotonicNanos)
                                PlaybackRepository.PlaybackLoadStage.PlayableSourceResolved -> _tvPlayback.value.timing.copy(playableSourceResolvedNanos = event.monotonicNanos)
                                PlaybackRepository.PlaybackLoadStage.PlayerLoadStarted -> _tvPlayback.value.timing.copy(playerLoadStartedNanos = event.monotonicNanos)
                                PlaybackRepository.PlaybackLoadStage.PlayerLoadReturned -> _tvPlayback.value.timing.copy(playerLoadReturnedNanos = event.monotonicNanos)
                            },
                            resolutionKind = event.source?.resolutionKind ?: _tvPlayback.value.resolutionKind,
                            convertedSourceKind = event.source?.convertedSourceKind ?: _tvPlayback.value.convertedSourceKind,
                        )
                    },
                )
                if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return@launch
                if (!loaded) {
                    failTvPlayback(attempt, "Couldn’t start this source", TvPlaybackFailureReason.Source)
                    return@launch
                }
                if (tvReusePendingAttemptId == attempt.attemptId) tvReusePendingAttemptId = null
                val actualEngine = playbackRepository.actualEngine()
                _tvPlayback.value = _tvPlayback.value.copy(
                    stage = TvPlaybackStage.Preparing,
                    actualEngine = actualEngine,
                    runtime = playbackRepository.getPlayer()?.runtimeState?.value ?: _tvPlayback.value.runtime,
                )
                Log.i("TvPlaybackTrace", traceTvPlayback(attempt, "load-returned", actualEngine, _tvPlayback.value.timing))
                startTvPlaybackMonitor(attempt)
                startTvTorrentStartupStats(attempt, option)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) {
                    failTvPlayback(attempt, "Couldn’t start this source", tvPlaybackFailureReason(failure))
                }
            }
        }
    }

    internal fun activateTvStream(semanticKey: String) {
        val state = _tvStreamSelection.value
        val target = state.target ?: return
        val option = state.options.firstOrNull { it.semanticKey == semanticKey } ?: return
        tvNextEpisodeJob?.cancel()
        tvNextEpisodeJob = null
        _tvStreamSelection.value = state.copy(selectedStreamKey = option.semanticKey)
        startTvPlayback(target, option)
    }

    internal fun retryTvPlayback() {
        val current = _tvPlayback.value
        val target = current.attempt?.target ?: return
        val option = current.option ?: return
        tvNextEpisodeJob?.cancel()
        tvNextEpisodeJob = null
        startTvPlayback(target, option)
    }

    internal fun tvPlaybackPlayer() = playbackRepository.getPlayer()

    internal fun tvSubtitleStyle(): PlayerSubtitleStyle {
        val settings = profileSettings.value
        val (savedSize, savedOffset) = playbackRepository.getSubtitlePrefs()
        val streamState = playbackRepository.getPlayerStreamState()
        return PlayerSubtitleStyle(
            sizePercent = streamState?.subtitleSize?.roundToInt() ?: settings?.subtitlesSize ?: savedSize,
            offsetPercent = streamState?.subtitleOffset?.roundToInt() ?: settings?.subtitlesOffset ?: savedOffset,
            delayMs = streamState?.subtitleDelay ?: 0L,
            textColor = settings?.subtitlesTextColor ?: "#FFFFFF",
            backgroundColor = settings?.subtitlesBackgroundColor ?: "#00000000",
            outlineColor = settings?.subtitlesOutlineColor ?: "#000000",
            assStyling = settings?.assSubtitlesStyling ?: true,
        )
    }

    internal fun rememberTvSubtitleStyle(style: PlayerSubtitleStyle) {
        playbackRepository.rememberSubtitleStyle(style)
        val current = profileSettings.value ?: return
        val updated = current.copy(
            subtitlesTextColor = style.textColor,
            subtitlesBackgroundColor = style.backgroundColor,
            subtitlesOutlineColor = style.outlineColor,
        )
        if (updated != current) updateProfileSettings(updated)
    }

    internal fun tvSeekDurationMs(): Long = profileSettings.value?.seekTimeDuration ?: 10_000L

    private fun safeServerState(state: StreamingServerState): String = when (state) {
        StreamingServerState.Stopped -> "Stopped"
        StreamingServerState.Starting -> "Starting"
        is StreamingServerState.Ready -> "Ready"
        is StreamingServerState.Failed -> "Failed"
    }

    private fun safeServerFailureCategory(state: StreamingServerState): String = when (state) {
        is StreamingServerState.Failed -> state.category.name
        StreamingServerState.Starting -> "StillStarting"
        StreamingServerState.Stopped -> "DidNotStart"
        is StreamingServerState.Ready -> "UnexpectedReadyState"
    }

    private fun traceTvPlayback(
        attempt: TvPlaybackAttempt,
        stage: String,
        actualEngine: PlayerEngine? = null,
        timing: TvPlaybackTiming = _tvPlayback.value.timing,
        signal: String? = null,
    ): String {
        val state = _tvPlayback.value
        return safeTvPlaybackTrace(
            attempt, stage, actualEngine, timing, signal,
            server = state.serverStatus,
            resolution = state.resolutionKind,
            convertedSource = state.convertedSourceKind,
        )
    }

    private fun onTvPlaybackEvent(attempt: TvPlaybackAttempt, event: PlayerPlaybackEvent) {
        if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return
        if (_tvPlayback.value.stage == TvPlaybackStage.Error) return
        when (event) {
            is PlayerPlaybackEvent.FirstVisualFrame -> {
                val current = _tvPlayback.value
                if (current.firstVisualObserved) return
                tvStartupTimeoutJob?.cancel(); tvStartupTimeoutJob = null
                tvTorrentStartupStatsJob?.cancel(); tvTorrentStartupStatsJob = null
                tvSmartPlaybackSession = tvSmartPlaybackSession?.commit(attempt.attemptId)
                val firstVisualNanos = SystemClock.elapsedRealtimeNanos()
                val timing = current.timing.copy(firstVisualSignalNanos = firstVisualNanos)
                if (BuildConfig.DEBUG) Log.d("TvPlaybackTrace", "phase=T13-first-visual trace=${tvStartupTrace?.traceId} attempt=${attempt.attemptId} elapsedRealtimeNanos=$firstVisualNanos")
                _tvPlayback.value = current.copy(
                    stage = TvPlaybackStage.Playing,
                    firstVisualObserved = true,
                    timing = timing,
                    fallbackProgress = null,
                )
                if (BuildConfig.DEBUG && tvSmartPlaybackSession?.currentAttemptId == attempt.attemptId) {
                    Log.d("TvSmartFallback", "committed attempt=${attempt.attemptId} candidateRank=${tvSmartPlaybackSession?.currentIndex?.plus(1)}")
                }
                maybePrefetchTvNextEpisode(attempt, _tvPlayback.value)
                val actualEngine = current.actualEngine ?: playbackRepository.actualEngine()
                authRepository.rememberLocalStreamSelection(
                    attempt.target.contentType,
                    attempt.target.contentId,
                    attempt.target.videoId ?: attempt.target.contentId,
                    current.option ?: return,
                )
                Log.i("TvPlaybackTrace", traceTvPlayback(attempt, "first-visual", actualEngine, timing, event.signalKind))
                if (BuildConfig.DEBUG) {
                    tvStartupTrace?.let { startup ->
                        Log.d("TvStartupSummary", safeTvStartupSummary(startup, attempt, timing))
                    }
                }
                tvStartupTrace = null
                if (tvValidationFixtures?.consumeHoldAfterFirstVisual(attempt.target) == true) {
                    val player = playbackRepository.getPlayer()
                    val reusable = player as? com.stremio.mobile.player.ReusableExoPlayer
                    player?.pause()
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            "TvValidation",
                            "hold-after-first-visual attempt=${attempt.attemptId} media=${attempt.target.videoId ?: attempt.target.contentId} " +
                                "instance=${reusable?.instanceId ?: "none"} generation=${reusable?.itemGeneration ?: "none"}",
                        )
                    }
                }
                timing.firstVisualSignalNanos?.let { tvEpisodeTransition.firstVisual(attempt.attemptId, it) }
                    ?.let { trace ->
                        logTvEpisodeTransition(trace)
                        tvEpisodeTransition.reset()
                    }
            }
            is PlayerPlaybackEvent.PlaybackError -> failTvPlayback(attempt, "Couldn’t start this source", tvPlaybackFailureReason(event.category))
        }
    }

    private fun startTvPlaybackMonitor(attempt: TvPlaybackAttempt) {
        tvPlaybackMonitorJob?.cancel()
        val player = playbackRepository.getPlayer() ?: return
        tvSegmentProviderJob?.cancel()
        tvSegmentProviderJob = viewModelScope.launch {
            playbackRepository.playerFlow().collect { corePlayer ->
                if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return@collect
                latestTvCorePlayer = corePlayer
                val current = _tvPlayback.value
                val query = current.option?.let { tvSegmentQuery(attempt, it, current.runtime.durationMs) }
                if (query != null) publishTvSegments(attempt, query, corePlayer)
            }
        }
        tvPlaybackMonitorJob = viewModelScope.launch {
            player.runtimeState.collect { runtime ->
                if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return@collect
                syncTvNextVideo(attempt.attemptId)
                val current = _tvPlayback.value
                if (current.stage == TvPlaybackStage.Error) return@collect
                val updated = current.copy(
                    runtime = runtime,
                    isBuffering = runtime.isBuffering,
                    error = if (runtime.error != null && !current.firstVisualObserved) "Couldn’t start this source" else current.error,
                    stage = when {
                        current.stage == TvPlaybackStage.Ended -> TvPlaybackStage.Ended
                        current.stage == TvPlaybackStage.Error -> TvPlaybackStage.Error
                        runtime.ended -> TvPlaybackStage.Ended
                        else -> current.stage
                    },
                    nextEpisode = current.nextEpisode.copy(
                        promptVisible = tvNextEpisodePromptVisible(
                            runtime.positionMs,
                            runtime.durationMs,
                            profileSettings.value?.nextVideoNotificationDuration ?: 0L,
                            current.nextEpisode.available,
                            current.nextEpisode.dismissed,
                        ),
                        automaticEnabled = profileSettings.value?.bingeWatching == true,
                    ),
                )
                _tvPlayback.value = updated
                val query = updated.option?.let { tvSegmentQuery(attempt, it, runtime.durationMs) }
                if (query != null) latestTvCorePlayer?.let { publishTvSegments(attempt, query, it) }
                maybePrefetchTvNextEpisode(attempt, updated)
                val fallbackNotice = current.requestedEngine != current.actualEngine &&
                    runtime.error == "MPV unavailable; using ExoPlayer."
                if (runtime.error != null && !fallbackNotice && current.stage != TvPlaybackStage.Error) {
                    failTvPlayback(attempt, "Couldn’t start this source", TvPlaybackFailureReason.UnknownStartup)
                } else if (runtime.ended && current.stage != TvPlaybackStage.Ended) {
                    if (tvProgressReportingAllowed(current) && runtime.durationMs > 0 && tvPlaybackCompletionPolicy.reportEnded) {
                        playbackRepository.reportTimeChanged(runtime.positionMs, runtime.durationMs)
                        playbackRepository.reportEnded()
                    }
                    _tvPlayback.value = _tvPlayback.value.copy(stage = TvPlaybackStage.Ended)
                    Log.i("TvPlaybackTrace", traceTvPlayback(attempt, "ended", current.actualEngine, current.timing))
                    if (tvProgressReportingAllowed(current) &&
                        tvShouldAutoAdvanceEnded(true, current.nextEpisode.available, profileSettings.value?.bingeWatching == true)
                    ) {
                        playTvNextEpisode(attempt.attemptId, automatic = true)
                    }
                } else if (tvProgressReportingAllowed(current) && runtime.durationMs > 0 &&
                    (lastTvTimeReportNanos == 0L || SystemClock.elapsedRealtimeNanos() - lastTvTimeReportNanos >= 5_000_000_000L)
                ) {
                    lastTvTimeReportNanos = SystemClock.elapsedRealtimeNanos()
                    playbackRepository.reportTimeChanged(runtime.positionMs, runtime.durationMs)
                }
            }
        }
    }

    private fun startTvTorrentStartupStats(attempt: TvPlaybackAttempt, option: StreamOption) {
        tvTorrentStartupStatsJob?.cancel(); tvTorrentStartupStatsJob = null
        if (!BuildConfig.DEBUG || option.sourceKind != StreamSourceKind.Torrent) return
        val torrent = option.core.stream.source as? com.stremio.core.types.resource.Stream.Source.Tramvai ?: return
        val infoHash = torrent.value.infoHash
        val fileIndex = torrent.value.fileIdx ?: StremioCore.STREAMING_SERVER_AUTO_FILE_INDEX
        tvTorrentStartupStatsJob = viewModelScope.launch(Dispatchers.IO) {
            var firstPeerAtNanos: Long? = null
            var firstDownloadAtNanos: Long? = null
            while (isActive && isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId) && !_tvPlayback.value.firstVisualObserved) {
                playbackRepository.requestStreamStatistics(infoHash, fileIndex)
                delay(800)
                val stats = playbackRepository.getStreamStatistics()
                if (stats?.infoHash == infoHash && isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (stats.peers > 0 && firstPeerAtNanos == null) firstPeerAtNanos = now
                    if (stats.downloadSpeed > 0.0 && firstDownloadAtNanos == null) firstDownloadAtNanos = now
                    val elapsedMs = (now - attempt.startedAtNanos).coerceAtLeast(0L) / 1_000_000
                    val timeToFirstPeerMs = firstPeerAtNanos?.let { (it - attempt.startedAtNanos).coerceAtLeast(0L) / 1_000_000 }
                    val timeToFirstDownloadMs = firstDownloadAtNanos?.let { (it - attempt.startedAtNanos).coerceAtLeast(0L) / 1_000_000 }
                    Log.d(
                        "TvStartupTorrent",
                        "attempt=${attempt.attemptId} elapsedMs=$elapsedMs peers=${stats.peers} " +
                            "downloadSpeedBps=${stats.downloadSpeed.toLong().coerceAtLeast(0L)} " +
                            "streamProgressPct=${(stats.streamProgress * 100).toInt().coerceIn(0, 100)} " +
                            "firstPeerMs=${timeToFirstPeerMs ?: "pending"} firstDownloadMs=${timeToFirstDownloadMs ?: "pending"}",
                    )
                }
                delay(200)
            }
        }
    }

    private fun publishTvSegments(
        attempt: TvPlaybackAttempt,
        query: TvSegmentQuery,
        corePlayer: com.stremio.core.models.Player,
    ) {
        if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return
        val result = tvSegmentCoordinator.resolve(query, listOf(CoreTvSegmentProvider(corePlayer)))
        if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return
        _tvPlayback.value = tvSegmentsForAttempt(_tvPlayback.value, attempt.attemptId, result.segments)
        if (BuildConfig.DEBUG) {
            Log.d("TvSegments", "attempt=${attempt.attemptId} provider=core candidateCount=${result.candidateCount} " +
                "resolvedCount=${result.segments.size} cache=${if (result.cacheHit) "hit" else "miss"} reason=${result.resolutionReason}")
            result.segments.forEach { segment ->
                Log.d("TvSegments", "attempt=${attempt.attemptId} provider=core type=${segment.type.name.lowercase()} " +
                    "startMs=${segment.startMs} endMs=${segment.endMs} confidence=${segment.confidence.name.lowercase()}")
            }
        }
    }

    private fun nextEpisodeStateFor(attemptId: String, target: TvStreamTarget): TvNextEpisodeState {
        val video = tvNextVideoForAttempt(tvNextVideoProvider, target, attemptId, attemptId)
        tvNextVideoAttemptId = attemptId
        tvNextVideo = video
        return nextEpisodeState(attemptId, video)
    }

    private fun nextEpisodeState(
        attemptId: String,
        video: com.stremio.core.types.resource.Video?,
    ): TvNextEpisodeState {
        val info = video?.seriesInfo
        val label = if (info != null && info.season > 0 && info.episode > 0) {
            "S${info.season.toString().padStart(2, '0')}E${info.episode.toString().padStart(2, '0')}"
        } else null
        return TvNextEpisodeState(
            playbackAttemptId = attemptId,
            videoId = video?.id,
            episodeLabel = label,
            title = video?.title?.takeIf(String::isNotBlank),
            automaticEnabled = profileSettings.value?.bingeWatching == true,
        )
    }

    private fun syncTvNextVideo(attemptId: String) {
        if (tvNextVideoAttemptId != attemptId) return
        val target = _tvPlayback.value.attempt?.target ?: return
        val latest = tvNextVideoForAttempt(tvNextVideoProvider, target, attemptId, _tvPlayback.value.attempt?.attemptId)
        if (latest?.id == tvNextVideo?.id) return
        cancelTvNextEpisodePrefetch("next-video-changed")
        tvNextVideo = latest
        val current = _tvPlayback.value
        if (isCurrentTvAttempt(current, attemptId)) {
            _tvPlayback.value = current.copy(nextEpisode = nextEpisodeState(attemptId, latest))
        }
    }

    private fun maybePrefetchTvNextEpisode(attempt: TvPlaybackAttempt, state: TvPlaybackUiState) {
        if (!isCurrentTvAttempt(state, attempt.attemptId) || !state.firstVisualObserved) return
        if (tvNextVideoAttemptId != attempt.attemptId) return
        val video = tvNextVideo ?: return
        if (video.id.isBlank() || state.nextEpisode.videoId != video.id) return
        val durationMs = state.runtime.durationMs
        if (durationMs <= 0L) return
        val leadWindowMs = maxOf(120_000L, profileSettings.value?.nextVideoNotificationDuration ?: 0L)
        if (durationMs - state.runtime.positionMs !in 0L..leadWindowMs) return

        val target = nextEpisodeTarget(attempt.target, video)
        if (tvNextEpisodePrefetch.find(attempt.attemptId, target.semanticTargetKey) != null) return
        tvNextEpisodePrefetch.clear()?.let { stale ->
            cancelTvNextEpisodePrefetchRequest(stale, "stale")
        }
        val request = tvNextEpisodePrefetch.begin(
            attemptId = attempt.attemptId,
            targetKey = target.semanticTargetKey,
            startedAtNanos = SystemClock.elapsedRealtimeNanos(),
        )
        logTvPrefetch("started", target, attempt.attemptId)
        request.job = viewModelScope.launch {
            try {
                val fixtureOptions = tvValidationFixtures?.optionsFor(target)
                val options = fixtureOptions ?: catalogRepository.getMetaDetailsFlow(
                    type = target.contentType,
                    id = target.contentId,
                    videoId = target.videoId,
                    guessStreamPath = target.guessStreamPath,
                ).mapNotNull { details ->
                    if (!tvStreamTargetMatches(details, target)) null else mapTvStreamEmission(details)
                }.first { emission -> !emission.isLoading }.options
                if (!tvNextEpisodePrefetch.isCurrent(request) ||
                    !isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId) ||
                    tvNextVideoAttemptId != attempt.attemptId || tvNextVideo?.id != target.videoId
                ) {
                    cancelTvNextEpisodePrefetchRequest(request, "stale")
                    return@launch
                }
                if (request.result.complete(options.takeIf { it.isNotEmpty() })) {
                    logTvPrefetchReady(target, attempt.attemptId, options.size, request.startedAtNanos)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                request.result.complete(null)
                logTvPrefetch("miss/fallback", target, attempt.attemptId)
            }
        }
    }

    private fun cancelTvNextEpisodePrefetch(reason: String) {
        tvNextEpisodePrefetch.clear()?.let { request ->
            cancelTvNextEpisodePrefetchRequest(request, reason)
        }
    }

    private fun cancelTvNextEpisodePrefetchRequest(request: TvStreamPrefetchRequest, reason: String) {
        val wasActive = !request.result.isCompleted || request.job?.isActive == true
        request.job?.cancel()
        request.result.cancel()
        if (BuildConfig.DEBUG && wasActive) {
            Log.d("TvStreamPrefetch", "cancelled/stale attempt=${request.attemptId} reason=$reason")
        }
    }

    private fun logTvPrefetch(event: String, target: TvStreamTarget, attemptId: String) {
        if (BuildConfig.DEBUG) {
            Log.d("TvStreamPrefetch", "$event attempt=$attemptId target=${target.semanticTargetKey}")
        }
    }

    private fun logTvPrefetchReady(target: TvStreamTarget, attemptId: String, streamCount: Int, startedAtNanos: Long) {
        if (BuildConfig.DEBUG) {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L
            Log.d("TvStreamPrefetch", "ready attempt=$attemptId target=${target.semanticTargetKey} streams=$streamCount elapsedMs=$elapsedMs")
        }
    }

    internal fun dismissTvNextEpisode(attemptId: String) {
        val current = _tvPlayback.value
        if (!isCurrentTvAttempt(current, attemptId) || !isCurrentTvNextEpisode(current.nextEpisode, attemptId)) return
        _tvPlayback.value = current.copy(nextEpisode = tvNextEpisodeDismiss(current.nextEpisode))
    }

    internal fun playTvNextEpisode(attemptId: String, automatic: Boolean = false) {
        val current = _tvPlayback.value
        if (!isCurrentTvAttempt(current, attemptId) || !isCurrentTvNextEpisode(current.nextEpisode, attemptId)) return
        if (automatic && !current.nextEpisode.automaticEnabled) return
        val transition = tvNextEpisodeTransitionStarted(current.nextEpisode) ?: return
        val next = tvNextVideo.takeIf { tvNextVideoAttemptId == attemptId }
        if (next == null || next.id != transition.videoId) {
            _tvPlayback.value = current.copy(
                nextEpisode = transition.copy(transition = TvNextEpisodeTransition.Failed, error = "The next episode is no longer available."),
            )
            return
        }
        val parentTarget = current.attempt?.target ?: return
        val target = nextEpisodeTarget(parentTarget, next)
        val prepared = tvEpisodeTransition.prepare(
            oldAttemptId = attemptId,
            targetVideoId = next.id,
            targetKey = target.semanticTargetKey,
            trigger = if (automatic) TvNextEpisodeTrigger.Automatic else TvNextEpisodeTrigger.Manual,
            atNanos = SystemClock.elapsedRealtimeNanos(),
        ) ?: return
        logTvEpisodeTransition(prepared)
        _tvPlayback.value = current.copy(nextEpisode = transition)
        tvNextEpisodeJob?.cancel()
        tvNextEpisodeJob = viewModelScope.launch {
            val prefetched = tvNextEpisodePrefetch.find(attemptId, target.semanticTargetKey)
            val resolvedOptions = if (prefetched != null) {
                logTvPrefetch("reused on transition", target, attemptId)
                withTimeoutOrNull(30_000L) { prefetched.result.await() }
            } else null
            if (prefetched != null && resolvedOptions == null && tvNextEpisodePrefetch.isCurrent(prefetched)) {
                cancelTvNextEpisodePrefetch("prefetch-timeout")
            }
            if (resolvedOptions.isNullOrEmpty()) {
                logTvPrefetch("miss/fallback", target, attemptId)
                openTvStreams(target)
            }
            val resolved = if (!resolvedOptions.isNullOrEmpty()) {
                resolvedOptions
            } else {
                withTimeoutOrNull(30_000L) {
                    _tvStreamSelection.first { selection ->
                        selection.target?.semanticTargetKey == target.semanticTargetKey && !selection.isLoading
                    }.options
                }
            }
            if (!isCurrentTvAttempt(_tvPlayback.value, attemptId)) return@launch
            if (resolved.isNullOrEmpty()) {
                val latest = _tvPlayback.value
                if (isCurrentTvAttempt(latest, attemptId)) {
                    _tvPlayback.value = latest.copy(nextEpisode = latest.nextEpisode.copy(
                        transition = TvNextEpisodeTransition.Failed,
                        error = "No playable sources were found for ${target.episodeLabel ?: "the next episode"}.",
                    ))
                }
                tvEpisodeTransition.reset()
                return@launch
            }
            val previousOption = current.option
            val option = preferredNextEpisodeOption(resolved, previousOption)
            if (option == null) {
                val latest = _tvPlayback.value
                _tvPlayback.value = latest.copy(nextEpisode = latest.nextEpisode.copy(
                    transition = TvNextEpisodeTransition.Failed,
                    error = "No playable sources were found for ${target.episodeLabel ?: "the next episode"}.",
                ))
                tvEpisodeTransition.reset()
                return@launch
            }
            val discovery = if (prefetched != null && !resolvedOptions.isNullOrEmpty()) {
                TvNextEpisodeDiscovery.Prefetched
            } else TvNextEpisodeDiscovery.Live
            val readyAt = SystemClock.elapsedRealtimeNanos()
            val ready = tvEpisodeTransition.optionReady(
                oldAttemptId = attemptId,
                targetVideoId = target.videoId.orEmpty(),
                targetKey = target.semanticTargetKey,
                discovery = discovery,
                hasUsableOption = true,
                atNanos = readyAt,
            ) ?: return@launch
            logTvEpisodeTransition(ready)
            val latest = _tvPlayback.value
            val currentTarget = latest.attempt?.target
            val currentProviderNextId = currentTarget?.let {
                tvNextVideoForAttempt(tvNextVideoProvider, it, attemptId, latest.attempt?.attemptId)?.id
            }
            val committed = tvEpisodeTransition.commit(
                oldAttemptId = attemptId,
                currentAttemptId = latest.attempt?.attemptId.takeIf { isCurrentTvAttempt(latest, attemptId) },
                targetVideoId = target.videoId.orEmpty(),
                currentVideoId = tvNextVideo?.id?.takeIf {
                    tvNextVideoAttemptId == attemptId && currentProviderNextId == it
                },
                targetKey = target.semanticTargetKey,
                hasUsableOption = true,
                atNanos = SystemClock.elapsedRealtimeNanos(),
            )
            if (committed == null) {
                tvEpisodeTransition.reset()
                return@launch
            }
            logTvEpisodeTransition(committed)
            // Core mutates the current LibraryItem and emits next-video before Player.Load.
            playbackRepository.reportNextVideo()
            if (BuildConfig.DEBUG) {
                Log.d("PlaybackReuse", "next-episode load-request attempt=${latest.playbackAttemptId} media=${target.videoId ?: target.contentId}")
            }
            startTvPlayback(target, option, reuseExistingExo = true)
        }
    }

    private fun logTvEpisodeTransition(trace: TvEpisodeTransitionTrace) {
        if (!BuildConfig.DEBUG) return
        fun elapsed(start: Long, end: Long?): String = end?.let { "${(it - start) / 1_000_000L}ms" } ?: "pending"
        val source = trace.discovery?.name?.lowercase() ?: "pending"
        val ready = trace.optionReadyNanos?.let { "${(it - trace.triggerNanos) / 1_000_000L}ms" } ?: "pending"
        val triggerToCommit = elapsed(trace.triggerNanos, trace.commitNanos)
        val triggerToActivation = elapsed(trace.triggerNanos, trace.sourceActivatedNanos)
        val commitToActivation = trace.commitNanos?.let { elapsed(it, trace.sourceActivatedNanos) } ?: "pending"
        val commitToVisual = if (trace.commitNanos != null) elapsed(trace.commitNanos, trace.firstVisualNanos) else "pending"
        val triggerToVisual = elapsed(trace.triggerNanos, trace.firstVisualNanos)
        Log.d(
            "TvEpisodeTransition",
            "oldAttempt=${trace.oldAttemptId} newAttempt=${trace.newAttemptId ?: "pending"} trigger=${trace.trigger.name.lowercase()} discovery=$source optionReadyMs=$ready triggerToCommitMs=$triggerToCommit triggerToSourceActivationMs=$triggerToActivation commitToSourceActivationMs=$commitToActivation commitToFirstVisualMs=$commitToVisual triggerToFirstVisualMs=$triggerToVisual",
        )
    }

    private var lastTvTimeReportNanos = 0L

    private fun failTvPlayback(attempt: TvPlaybackAttempt, message: String, reason: TvPlaybackFailureReason) {
        if (!isCurrentTvAttempt(_tvPlayback.value, attempt.attemptId)) return
        if (maybeFallbackTvPlayback(attempt.attemptId, reason)) return
        tvStartupTimeoutJob?.cancel(); tvStartupTimeoutJob = null
        tvTorrentStartupStatsJob?.cancel(); tvTorrentStartupStatsJob = null
        tvPlaybackJob?.cancel(); tvPlaybackJob = null
        tvPlaybackMonitorJob?.cancel(); tvPlaybackMonitorJob = null
        if (shouldReleaseRetainedPlayerAfterTvFailure(tvReusePendingAttemptId, attempt.attemptId)) {
            tvReusePendingAttemptId = null
            playbackRepository.setPlaybackEventListener(null)
            playbackRepository.release()
        }
        _tvPlayback.value = _tvPlayback.value.copy(stage = TvPlaybackStage.Error, error = message)
        tvEpisodeTransition.reset()
        Log.w("TvPlaybackTrace", traceTvPlayback(attempt, "failure:${reason.name.lowercase()}", _tvPlayback.value.actualEngine, _tvPlayback.value.timing))
        if (BuildConfig.DEBUG) {
            tvStartupTrace?.let { startup ->
                Log.w(
                    "TvStartupSummary",
                    safeTvStartupSummary(startup, attempt, _tvPlayback.value.timing, result = "failed:${reason.name.lowercase()}", observedAtNanos = SystemClock.elapsedRealtimeNanos()),
                )
            }
        }
        tvStartupTrace = null
    }

    /** The only startup recovery decision point. Every callback must still own the active attempt. */
    private fun maybeFallbackTvPlayback(failedAttemptId: String, reason: TvPlaybackFailureReason): Boolean {
        val playback = _tvPlayback.value
        if (!isCurrentTvAttempt(playback, failedAttemptId) || playback.firstVisualObserved) return false
        val session = tvSmartPlaybackSession ?: return false
        if (session.cancelled || session.committed || session.currentAttemptId != failedAttemptId ||
            session.target.semanticTargetKey != playback.attempt?.target?.semanticTargetKey
        ) return false
        val nextIndex = tvSmartFallbackNextCandidateIndex(
            session = session,
            failedAttemptId = failedAttemptId,
            currentAttemptId = playback.playbackAttemptId,
            currentTargetKey = playback.attempt.target.semanticTargetKey,
            firstVisualObserved = playback.firstVisualObserved,
            smartPlay = true,
        ) ?: run {
            tvSmartPlaybackSession = null
            return false
        }
        val option = session.rankedCandidates[nextIndex]
        if (BuildConfig.DEBUG) Log.d("TvSmartFallback", "failed rank=${session.currentIndex + 1} reason=${reason.name.lowercase()}")
        val updated = playback.copy(fallbackProgress = "Source ${nextIndex + 1} of ${session.rankedCandidates.size}")
        _tvPlayback.value = updated
        tvSmartPlaybackSession = session.copy(currentAttemptId = null)
        startTvPlayback(session.target, option, smartSessionIndex = nextIndex)
        return true
    }

    private fun tvPlaybackFailureReason(failure: Exception): TvPlaybackFailureReason = when (failure) {
        is com.stremio.mobile.presentation.tv.TvSmartFallbackFixtureFailure -> failure.reason
        is PlaybackResolutionException -> when (failure.category) {
            PlaybackResolutionFailure.StreamingServerStartFailed -> TvPlaybackFailureReason.ServerStartup
            PlaybackResolutionFailure.CoreConversionError,
            PlaybackResolutionFailure.CoreResolutionTimeout,
            PlaybackResolutionFailure.NoPlayableSource -> TvPlaybackFailureReason.Resolution
            PlaybackResolutionFailure.PlayerLoadError -> TvPlaybackFailureReason.Source
        }
        else -> TvPlaybackFailureReason.UnknownStartup
    }

    private fun tvPlaybackFailureReason(category: String): TvPlaybackFailureReason = when {
        category.contains("container", ignoreCase = true) || category.contains("format", ignoreCase = true) -> TvPlaybackFailureReason.Container
        category.contains("decoder", ignoreCase = true) || category.contains("codec", ignoreCase = true) -> TvPlaybackFailureReason.Decoder
        category.contains("source", ignoreCase = true) || category.contains("http", ignoreCase = true) || category.contains("network", ignoreCase = true) -> TvPlaybackFailureReason.Source
        else -> TvPlaybackFailureReason.UnknownStartup
    }

    internal fun toggleTvPlayback() {
        val state = _tvPlayback.value
        if (state.stage == TvPlaybackStage.Ended || state.stage == TvPlaybackStage.Error) return
        val player = playbackRepository.getPlayer() ?: return
        if (state.runtime.isPlaying) {
            logTvControl("pause", state, player, "positionMs=${state.runtime.positionMs}")
            player.pause()
            playbackRepository.reportPausedChanged(true)
            reportTvFinalProgress(state)
        } else {
            logTvControl("resume", state, player, "positionMs=${state.runtime.positionMs}")
            player.play()
            playbackRepository.reportPausedChanged(false)
        }
    }

    internal fun seekTvPlaybackBy(deltaMs: Long) {
        val state = _tvPlayback.value
        if (!state.firstVisualObserved) return
        val target = clampTvSeekTarget(state.runtime.positionMs + deltaMs, state.runtime.durationMs) ?: return
        playbackRepository.getPlayer()?.let { logTvControl("seek-relative", state, it, "deltaMs=$deltaMs targetMs=$target") }
        seekTvPlaybackTo(target)
    }

    internal fun seekTvPlaybackTo(targetMs: Long) {
        val state = _tvPlayback.value
        if (!state.firstVisualObserved || state.runtime.durationMs <= 0L) return
        val player = playbackRepository.getPlayer() ?: return
        val target = clampTvSeekTarget(targetMs, state.runtime.durationMs) ?: return
        logTvControl("seek-direct", state, player, "targetMs=$target")
        player.seekTo(target)
        playbackRepository.reportSeek(target, state.runtime.durationMs)
        reportTvFinalProgress(state.copy(runtime = state.runtime.copy(positionMs = target)))
    }

    internal fun selectTvAudioTrack(track: PlayerTrackOption) {
        val state = _tvPlayback.value
        val player = playbackRepository.getPlayer() ?: return
        logTvControl("audio-select", state, player, "trackId=${track.id} label=${track.label}")
        player.selectAudioTrack(track.id)
        rememberAudioTrack(track)
    }

    internal fun selectTvSubtitleTrack(track: PlayerTrackOption) {
        val state = _tvPlayback.value
        val player = playbackRepository.getPlayer() ?: return
        logTvControl("subtitle-select", state, player, "trackId=${track.id} label=${track.label}")
        player.selectSubtitleTrack(track.id)
        rememberSubtitleTrack(track)
    }

    internal fun disableTvSubtitles() {
        val state = _tvPlayback.value
        val player = playbackRepository.getPlayer() ?: return
        logTvControl("subtitle-disable", state, player, "")
        player.disableSubtitles()
        rememberSubtitlesDisabled()
    }

    private fun logTvControl(action: String, state: TvPlaybackUiState, player: com.stremio.mobile.player.Player, details: String) {
        if (!BuildConfig.DEBUG) return
        val attempt = state.attempt ?: return
        val reusable = player as? com.stremio.mobile.player.ReusableExoPlayer
        Log.d(
            "TvPlaybackControl",
            "action=$action attempt=${attempt.attemptId} media=${attempt.target.videoId ?: attempt.target.contentId} " +
                "engine=${player.engine} instance=${reusable?.instanceId ?: "none"} generation=${reusable?.itemGeneration ?: "none"} " +
                "stage=${state.stage} ${details.trim()}",
        )
    }

    private fun reportTvFinalProgress(state: TvPlaybackUiState) {
        if (!tvProgressReportingAllowed(state) || state.runtime.durationMs <= 0) return
        playbackRepository.reportTimeChanged(state.runtime.positionMs, state.runtime.durationMs)
    }

    internal fun closeTvPlayback() {
        cancelTvNextEpisodePrefetch("playback-closed")
        tvEpisodeTransition.reset()
        val state = _tvPlayback.value
        state.attempt?.let { attempt ->
            reportTvFinalProgress(state)
            Log.i("TvPlaybackTrace", traceTvPlayback(attempt, "closed", state.actualEngine, state.timing))
        }
        tvPlaybackJob?.cancel()
        tvStartupTimeoutJob?.cancel(); tvStartupTimeoutJob = null
        tvTorrentStartupStatsJob?.cancel(); tvTorrentStartupStatsJob = null
        tvSmartPlaybackSession = tvSmartPlaybackSession?.cancel()
        tvSmartPlaybackSession = null
        tvStartupTrace = null
        tvNextEpisodeJob?.cancel()
        tvNextEpisodeJob = null
        tvPlaybackJob = null
        tvPlaybackMonitorJob?.cancel()
        tvPlaybackMonitorJob = null
        tvSegmentProviderJob?.cancel()
        tvSegmentProviderJob = null
        latestTvCorePlayer = null
        tvNextVideoAttemptId = null
        tvNextVideo = null
        playbackRepository.setPlaybackEventListener(null)
        playbackRepository.release()
        tvReusePendingAttemptId = null
        lastTvTimeReportNanos = 0L
        _tvPlayback.value = TvPlaybackUiState()
    }

    internal fun closeTvStreams() {
        tvSmartSettleJob?.cancel(); tvSmartSettleJob = null
        tvSmartMaximumJob?.cancel(); tvSmartMaximumJob = null
        tvStreamsJob?.cancel()
        tvStreamsJob = null
        _tvStreamSelection.value = _tvStreamSelection.value.copy(smartSelecting = false, isLoading = false, isActive = false)
    }

    fun closeDetails() {
        detailsJob?.cancel()
        detailsJob = null
        detailsActivatedNanos = null
        selectedDetails.value = null
        detailsLibraryActionJob?.cancel()
        detailsLibraryActionJob = null
        detailsLibraryOverride = null
        isDetailsLibraryActionLoading = false
        _tvDetailsUiState.value = TvDetailsUiState()
    }

    private fun publishTvDetails(details: MetaDetails) {
        selectedDetails.value = details
        refreshTvDetailsUiState()
    }

    private fun refreshTvDetailsUiState() {
        val details = selectedDetails.value
        _tvDetailsUiState.value = TvDetailsUiState(
            details = details,
            detailsActivatedNanos = detailsActivatedNanos,
            isInLibrary = detailsLibraryOverride ?: details?.let {
                isDetailsItemInLibrary(
                    item = it.item,
                    libraryItems = library.value.items,
                    libraryIsAuthoritative = hasAuthoritativeLibraryMembership,
                )
            } ?: false,
            isLibraryActionLoading = isDetailsLibraryActionLoading,
            episodeBrowser = details?.let {
                TvEpisodeBrowserUiState.from(it.episodes, it.item.continueWatchingVideoId)
            },
        )
    }

    fun toggleTvDetailsLibrary() {
        val item = selectedDetails.value?.item ?: return
        if (_tvDetailsUiState.value.isLibraryActionLoading) return
        val target = !_tvDetailsUiState.value.isInLibrary
        detailsLibraryOverride = target
        isDetailsLibraryActionLoading = true
        refreshTvDetailsUiState()
        val actionJob = viewModelScope.launch {
            try {
                if (target) {
                    catalogRepository.addToLibrary(item)
                    com.posthog.PostHog.capture(
                        event = "Library Item Added",
                        properties = mapOf("item_type" to item.type, "hashed_item_id" to sha256(item.id)),
                    )
                } else {
                    catalogRepository.removeFromLibrary(item.id)
                    com.posthog.PostHog.capture(
                        event = "Library Item Removed",
                        properties = mapOf("item_type" to item.type, "hashed_item_id" to sha256(item.id)),
                    )
                }
            } finally {
                // Core actions publish through the Library flow; the short busy state prevents
                // duplicate remote actions while keeping the Details route and focus in place.
                kotlinx.coroutines.delay(800)
                isDetailsLibraryActionLoading = false
                refreshTvDetailsUiState()
            }
        }
        detailsLibraryActionJob?.cancel()
        detailsLibraryActionJob = actionJob
    }

    fun toggleLibrary(item: CatalogItem) {
        val inLibrary = library.value.items.any { it.id == item.id && it.type == item.type }
        if (inLibrary) {
            catalogRepository.removeFromLibrary(item.id)
            com.posthog.PostHog.capture(
                event = "Library Item Removed",
                properties = mapOf(
                    "item_type" to item.type,
                    "hashed_item_id" to sha256(item.id)
                )
            )
        } else {
            catalogRepository.addToLibrary(item)
            com.posthog.PostHog.capture(
                event = "Library Item Added",
                properties = mapOf(
                    "item_type" to item.type,
                    "hashed_item_id" to sha256(item.id)
                )
            )
        }
    }

    private fun observeAddons() {
        viewModelScope.launch {
            addonRepository.getAddonsFlow().collect { raw ->
                addons.value = addonRepository.extractAddonsUiState(raw)
            }
        }
    }

    fun loadInstalledAddons(type: String? = null) {
        addonRepository.loadInstalledAddons(type)
    }

    /** Applies a filter taken from `state.addons.selectableTypes`/`selectableCatalogs`. */
    fun selectAddonsFilter(request: com.stremio.core.types.addon.ResourceRequest) {
        addonRepository.selectFilter(request)
    }

    fun installAddon(item: AddonItem) {
        addonRepository.installAddon(item)
        com.posthog.PostHog.capture(
            event = "Addon Installed",
            properties = mapOf(
                "addon_id" to sha256(item.id),
                "addon_name" to item.name,
                "addon_version" to (item.version ?: "unknown"),
                "official" to item.official
            )
        )
    }

    fun uninstallAddon(item: AddonItem) {
        addonRepository.uninstallAddon(item)
        com.posthog.PostHog.capture(
            event = "Addon Uninstalled",
            properties = mapOf(
                "addon_id" to sha256(item.id),
                "addon_name" to item.name,
                "addon_version" to (item.version ?: "unknown"),
                "official" to item.official
            )
        )
    }

    fun upgradeAddon(item: AddonItem) {
        addonRepository.upgradeAddon(item)
        com.posthog.PostHog.capture(
            event = "Addon Upgraded",
            properties = mapOf(
                "addon_id" to sha256(item.id),
                "addon_name" to item.name,
                "addon_version" to (item.version ?: "unknown"),
                "official" to item.official
            )
        )
    }

    fun openAddonDetails(transportUrl: String) {
        addonDetailsJob?.cancel()
        selectedAddonDetails.value = AddonDetailsUiState(transportUrl = transportUrl)
        addonDetailsJob = viewModelScope.launch {
            addonRepository.getAddonDetailsFlow(transportUrl).collect { details ->
                addonRepository.extractAddonDetailsUiState(details)?.let { selectedAddonDetails.value = it }
            }
        }
    }

    fun closeAddonDetails() {
        addonDetailsJob?.cancel()
        addonDetailsJob = null
        selectedAddonDetails.value = null
    }

    fun installAddonByUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val isUrl = runCatching { URL(url) }.isSuccess
        if (isUrl) {
            com.posthog.PostHog.capture(
                event = "Custom Addon Detail Opened",
                properties = mapOf(
                    "hashed_url" to sha256(url)
                )
            )
            openAddonDetails(url)
        }
        return isUrl
    }

    fun login(email: String, password: String) {
        authInFlight = true
        account.value = account.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { authRepository.login(email, password) }.onFailure {
                authInFlight = false
                account.value = account.value.copy(isLoading = false, error = it.message ?: "Login failed")
            }
        }
    }

    fun loginWithToken(authKey: String) {
        if (authKey.isBlank()) return
        authInFlight = true
        account.value = account.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { authRepository.loginWithToken(authKey) }.onFailure {
                authInFlight = false
                account.value = account.value.copy(isLoading = false, error = it.message ?: "Login failed")
                tvAccountLinkSession.markSignInFailed("Could not sign in. Request a new link and try again.")
            }
        }
    }

    fun startTvAccountLink() {
        if (account.value.isAuthenticated) return
        tvAccountLinkSession.start()
    }

    fun requestNewTvAccountLink() = tvAccountLinkSession.requestNewLink()

    fun retryTvAccountLinkCheck() = tvAccountLinkSession.retry()

    fun stopTvAccountLink() = tvAccountLinkSession.stop()

    fun loginWithFacebook(token: String) {
        authInFlight = true
        account.value = account.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { authRepository.loginWithFacebook(token) }.onFailure {
                authInFlight = false
                account.value = account.value.copy(isLoading = false, error = it.message ?: "Facebook login failed")
            }
        }
    }

    fun signup(email: String, password: String, marketingConsent: Boolean) {
        authInFlight = true
        account.value = account.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { authRepository.register(email, password, marketingConsent) }.onFailure {
                authInFlight = false
                account.value = account.value.copy(isLoading = false, error = it.message ?: "Sign up failed")
            }
        }
    }

    fun logout() {
        clearSearch()
        viewModelScope.launch { runCatching { authRepository.logout() } }
        authRepository.clearSavedSession()
        account.value = AccountUiState()
        refreshLibrary()
        com.posthog.PostHog.reset()
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setUserId("")
    }

    fun clearAccountError() {
        account.value = account.value.copy(error = null)
    }

    fun setAccountError(message: String) {
        authInFlight = false
        account.value = account.value.copy(isLoading = false, error = message)
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            try {
                catalogRepository.syncLibrary()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync library")
            }
        }
    }

    fun selectLibraryFilter(request: com.stremio.core.models.LibraryWithFilters.LibraryRequest) {
        pendingLibraryRequest = request
        _tvLibrary.value = _tvLibrary.value.copy(
            selectedRequest = request,
            logicalSelection = request.selectionIdentity(),
            items = emptyList(),
            nextPageRequest = null,
            isLoading = true,
        )
        viewModelScope.launch {
            try {
                catalogRepository.loadLibrary(request)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load library request")
            }
        }
    }

    fun loadLibraryNextPage(requestIdentity: String) {
        val state = _tvLibrary.value
        val request = state.nextPageRequest ?: return
        if (requestIdentity != request.toString()) return
        if (state.selectedRequest?.selectionIdentity() != request.selectionIdentity()) return
        if (!libraryPageRequests.tryMark(requestIdentity)) return
        catalogRepository.loadLibraryNextPage()
    }

    fun selectDiscoverFilter(request: com.stremio.core.types.addon.ResourceRequest) {
        discoverCatalogRequest.value = request
        discoverCatalog.value = discoverCatalog.value.copy(isLoading = true, seeAllRequest = request)
        _tvDiscover.value = _tvDiscover.value.copy(
            selectedRequest = request,
            shelf = _tvDiscover.value.shelf.copy(isLoading = true, seeAllRequest = request),
        )
        viewModelScope.launch {
            try {
                catalogRepository.loadDiscover(request)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load discover request")
            }
        }
    }

    fun loadDiscoverNextPage(requestIdentity: String) {
        val current = _tvDiscover.value
        val nextRequest = current.nextPageRequest ?: return
        if (current.selectedRequest == null || nextRequest.toString() != requestIdentity) return
        if (!discoverPageRequests.tryMark(requestIdentity)) return
        catalogRepository.loadDiscoverNextPage()
    }

    private fun observeAddonSubtitlesForPlayback() {
        subtitleObserverJob?.cancel()
        val seen = mutableSetOf<String>()
        subtitleObserverJob = viewModelScope.launch {
            playbackRepository.playerFlow().collect { player ->
                val newTracks = playbackRepository.extractAddonSubtitles(player)
                    .filter { seen.add(it.id) }
                if (newTracks.isNotEmpty()) {
                    playbackRepository.addExternalSubtitleTracks(newTracks)
                }
            }
        }
    }

    private fun copyLocalSubtitle(uri: Uri): ExternalSubtitle? {
        val displayName = queryDisplayName(uri) ?: "subtitle-${System.currentTimeMillis()}.srt"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outDir = File(appContext.cacheDir, "local-subtitles").apply { mkdirs() }
        val outFile = File(outDir, "${System.currentTimeMillis()}-$safeName")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        return ExternalSubtitle(
            id = "local:${outFile.name}",
            lang = LanguageCatalog.LOCAL_SUBTITLES_LANGUAGE,
            url = Uri.fromFile(outFile).toString(),
            label = displayName,
            source = "Local",
            origin = "LOCAL",
            embedded = false,
            local = true,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
    }

    private fun sha256(input: String): String {
        val bytes = input.lowercase().trim().toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
