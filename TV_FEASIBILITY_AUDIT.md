# Android TV / Google TV feasibility audit

## Scope and repository preflight

Audit date: 2026-09-28. **Audited commit:** `4bdef3b6be1186d9aa22acd8b3ec391c22f0e083` on local `master`. `git status --short --branch` showed a clean working tree before this report was created. `master` tracks `origin/master`; both the locally recorded `origin/master` and live `git ls-remote origin refs/heads/master` resolved to the audited SHA. Live `git ls-remote upstream refs/heads/master` also resolved to that SHA, so the local branch was **not behind either checked remote tip at preflight**. No fetch, checkout, reset, or submodule update was performed.

Configured remotes: `origin` = `https://github.com/robbis95/stremio-android.git`; `upstream` = `https://github.com/stremio-native/stremio-android.git`. This identifies the configured upstream as `stremio-native/stremio-android`; a Git remote name alone does not prove the full historical fork ancestry. The README still points to `perpetus` repositories, which appears to be older documentation and is not the current remote configuration. Sources read include [README.md](README.md), [.gitmodules](.gitmodules), [settings.gradle.kts](settings.gradle.kts), [build.gradle.kts](build.gradle.kts), [app/build.gradle.kts](app/build.gradle.kts), and the build/CI workflows under [.github/workflows](.github/workflows). No `AGENTS.md` or contribution guide was found inside this repository; a sibling repository's instructions were not applied.

Submodules are initialized at `streamio-core-kotlin` commit `439109c12fad7f7e69431a74c3f278ea0d6712f8` and `stream-server` commit `368666eb09f9c4849a7df7f2a6a8b26171f24cfb`. The former provides the Kotlin/JNI Stremio Core bridge, generated models, and native libraries ([Core.kt](streamio-core-kotlin/stremio-core-kotlin/src/androidMain/kotlin/com/stremio/core/Core.kt), [Storage.kt](streamio-core-kotlin/stremio-core-kotlin/src/commonMain/kotlin/com/stremio/core/Storage.kt)); it is included as a composite Gradle build. The latter provides the Rust local streaming server and torrent/HLS/cache implementation ([server](stream-server/server), [enginefs](stream-server/enginefs)); app JNI integration is in [JniStreamingServerController.kt](app/src/main/java/com/stremio/mobile/server/JniStreamingServerController.kt). The vendored MPV Android library is a regular repository directory and Gradle module, not a submodule ([settings.gradle.kts](settings.gradle.kts), [MPVLib.kt](third_party/mpv-android-lib/app/src/main/java/is/xyz/mpv/MPVLib.kt)).

This is static inspection. No Android TV device/emulator run, APK install, frame trace, merged-manifest dump, or build was performed. Performance and platform behavior that depend on a device are labeled accordingly.

## Architecture traced from code

The Gradle project has one app module, `:app`, one local MPV library module, `:mpv-android-lib`, and the included Core build ([settings.gradle.kts](settings.gradle.kts)). [MainApplication.kt](app/src/main/java/com/stremio/mobile/MainApplication.kt) creates [AppContainer.kt](app/src/main/java/com/stremio/mobile/di/AppContainer.kt), which initializes Core and owns repositories, the player manager, update services, and the streaming server controller. [MainActivity.kt](app/src/main/java/com/stremio/mobile/MainActivity.kt) creates [MainViewModel.kt](app/src/main/java/com/stremio/mobile/presentation/viewmodel/MainViewModel.kt) and renders [StremioMobileApp.kt](app/src/main/java/com/stremio/mobile/presentation/screens/StremioMobileApp.kt). The ViewModel combines Core/repository flows into [MainUiState.kt](app/src/main/java/com/stremio/mobile/presentation/state/MainUiState.kt), a separate streams state, and playback state.

| System | Actual owner and interaction |
|---|---|
| Account/auth | [AuthFlow.kt](app/src/main/java/com/stremio/mobile/presentation/screens/AuthFlow.kt) calls `MainViewModel.login/signup/loginWithFacebook`; [AuthRepository.kt](app/src/main/java/com/stremio/mobile/data/repository/AuthRepository.kt) calls [StremioCore.kt](app/src/main/java/com/stremio/mobile/core/StremioCore.kt) Core actions and persists a session key in `stremio_account` preferences. `MainViewModel.restoreCoreSession` restores it. Facebook uses [FacebookLoginBridge.kt](app/src/main/java/com/stremio/mobile/auth/FacebookLoginBridge.kt). |
| Core, networking, synchronization | `StremioCore` wraps Core fields/actions/events for account, board, discover, search, library, addons, metadata, player, and settings. The core submodule contains its Rust fetch/environment bridge; app-level direct HTTP exists in `CatalogRepository.fetchCatalog`, update checks, and server health/settings calls. Library/profile/player progress sync actions are in `StremioCore` and `MainViewModel` (`refreshLibrary`, player tick/seek/pause/end). |
| Addons/catalogs/metadata | [AddonRepository.kt](app/src/main/java/com/stremio/mobile/data/repository/AddonRepository.kt) maps Core addon models and installation actions. [BoardRepository.kt](app/src/main/java/com/stremio/mobile/data/repository/BoardRepository.kt) maps board and continue-watching Core models to shelves. [CatalogRepository.kt](app/src/main/java/com/stremio/mobile/data/repository/CatalogRepository.kt) exposes discover, search, library and meta-details flows. `MainViewModel.openDetails/openStreams` turn these into UI state. |
| Library/continue watching | Core library and continue-watching fields feed `CatalogRepository.getLibraryShelfFlow` and `BoardRepository.getContinueWatchingFlow`; local remembered stream choice is stored by `AuthRepository` and reused by `MainViewModel.openStreams`. |
| Search | `MainViewModel.search` debounces 300 ms, calls Core search across installed catalogs, requests ranges, and maps results; [SearchScreen.kt](app/src/main/java/com/stremio/mobile/presentation/screens/SearchScreen.kt) owns the mobile input/results UI. |
| Stream discovery/selection | Core meta-details stream requests are flattened by `StremioCore.extractStreams`; `MainViewModel.launchStreamsMenuJob` builds episodes/stream options, then [StreamsSheet.kt](app/src/main/java/com/stremio/mobile/presentation/screens/StreamsSheet.kt) filters/sorts/selects. [PlaybackRepository.kt](app/src/main/java/com/stremio/mobile/data/repository/PlaybackRepository.kt) resolves Core stream URLs and resume position. |
| Local server | [JniStreamingServerController.kt](app/src/main/java/com/stremio/mobile/server/JniStreamingServerController.kt) starts native `stream_server` and checks readiness off the main thread. [ServerService.kt](app/src/main/java/com/stremio/mobile/server/ServerService.kt) provides its foreground notification; [BootReceiver.kt](app/src/main/java/com/stremio/mobile/server/BootReceiver.kt) supports optional boot start. |
| Player/audio/subtitles | [Player.kt](app/src/main/java/com/stremio/mobile/player/Player.kt) defines backend-neutral controls and runtime state. [PlaybackManager.kt](app/src/main/java/com/stremio/mobile/player/PlaybackManager.kt) chooses [ExoStreamPlayer.kt](app/src/main/java/com/stremio/mobile/player/ExoStreamPlayer.kt) or [MpvStreamPlayer.kt](app/src/main/java/com/stremio/mobile/player/MpvStreamPlayer.kt) via [PlayerFactory.kt](app/src/main/java/com/stremio/mobile/player/PlayerFactory.kt). PlaybackRepository reports progress and track preferences to Core. [PlayerScreen.kt](app/src/main/java/com/stremio/mobile/presentation/screens/PlayerScreen.kt) and [PlayerControls.kt](app/src/main/java/com/stremio/mobile/presentation/screens/player/PlayerControls.kt) provide the current controls, dialogs, seek UI, and subtitle/audio selection. |
| Settings | Core profile/server settings and local preferences are coordinated by `MainViewModel` and `AuthRepository`; [SettingsPanel.kt](app/src/main/java/com/stremio/mobile/presentation/screens/SettingsPanel.kt) routes to the individual settings screens. |
| Compose/navigation/images/cache | `StremioMobileApp` conditionally stacks auth, board, search, detail, stream, addon, and player composables; it does not use the declared Navigation Compose dependency for these routes. `BoardScreen` selects `MainSection` tabs via [StremioBottomBar.kt](app/src/main/java/com/stremio/mobile/presentation/components/StremioBottomBar.kt). [PosterComponents.kt](app/src/main/java/com/stremio/mobile/presentation/components/PosterComponents.kt) uses Coil `AsyncImage`; `MainApplication.newImageLoader` configures 25% memory and 250 MB disk caches. Core persistence uses [AndroidStorage.kt](app/src/main/java/com/stremio/mobile/core/AndroidStorage.kt) SharedPreferences; `AuthRepository` holds local UI, player and stream preferences. |

The useful sharing boundary already exists in code, although not in separate Gradle modules: Core/repositories/models/player/server are distinct packages from Compose. `MainViewModel` is mixed: it exposes reusable account, catalog, metadata, stream and playback actions, but also owns mobile section/search/sheet flags, 50-plus combined inputs, update dialogs and styling settings. A TV UI can initially use selected existing actions; long-term state extraction should follow measured friction, not precede the first milestone.

## Reuse classification and architecture choice

**A — essentially unchanged:** the Core bridge and submodule; `AndroidStorage`; board/catalog/addon repositories and their mapping models; stream-server native/controller path; `Player` interface, Exo and MPV engines, `PlaybackManager`, Core playback reporting; existing auth/session actions. These encode Stremio and playback behavior independent of screen geometry. Validate native ABI/server behavior on target TV hardware before declaring runtime parity.

**B — adaptation:** `MainViewModel` and `MainUiState` (reusable actions/data, mobile flags and broad state); `PlaybackRepository` (reuse stream resolution, add TV media-session/key lifecycle at the integration edge); `AuthRepository` (reuse account/prefs, assess TV-specific preference defaults); poster/image models and Coil cache (reuse loading, change dimensions/prefetch policy for TV); search flow (reuse Core query/results, change input semantics); settings actions (reuse writes, change focusable controls). The current `MainViewModel` is practical for a proof of architecture but is not a clean shared domain API.

**C — TV-specific implementation:** `StremioMobileApp`/`BoardScreen` navigation, bottom bar, poster rows/grids, auth presentation, detail and stream sheets, search field/results layout, addon/settings pages, player controls/dialogs. Their sizing, overlays, pointer gestures, automatic hero motion, and lack of explicit focus paths would make a 10-foot, remote-first client fragile. The engines need no evidenced rewrite; their control surface does.

Three possible layouts:

1. **One responsive Compose tree:** smallest file count, but `BoardScreen` mixes mobile navigation, a three-column grid, bottom bar, sheet overlays and glass backdrop. Adding TV focus routes and restoring focus inside this tree would spread mode branches through large composables ([StremioMobileApp.kt](app/src/main/java/com/stremio/mobile/presentation/screens/StremioMobileApp.kt), [DetailSheet.kt](app/src/main/java/com/stremio/mobile/presentation/screens/DetailSheet.kt), [StremioBottomBar.kt](app/src/main/java/com/stremio/mobile/presentation/components/StremioBottomBar.kt)). Maintenance cost is high.
2. **Separate TV presentation package in the existing `:app`:** reuse `AppContainer`, `MainViewModel` actions initially, repositories, Core, native server and player. Add a TV root and focus/navigation components under `com.stremio.mobile.presentation.tv`, selected from a TV launcher activity. This creates limited duplicated screen composition but little duplicated Stremio logic. It is the best incremental fit for the current one-module structure and keeps phone development independent.
3. **Separate `:tv` Android app module:** gives a clean TV manifest/artifact and package identity, but `:app` currently owns all repositories, models, `MainApplication`, JNI packaging and player code. A new app module first requires moving those into shared Android library module(s), reworking native packaging and build/release wiring. That abstraction is premature for a feasibility milestone; consider it if separate distribution/release cadence becomes a requirement.

**Recommendation:** option 2. Keep the current app ID, native dependencies and mobile entry point; introduce a TV-specific activity/root and package in `:app`, with launcher/feature declarations appropriate to the chosen combined APK. Share data/core/player packages as they stand. Once TV implementation shows a concrete need for smaller state contracts, extract only those contracts. Mobile and TV screens can evolve independently; duplication is mainly layout, focus and navigation, which genuinely differ.

## Android TV platform readiness

[AndroidManifest.xml](app/src/main/AndroidManifest.xml) declares `MAIN` + `LAUNCHER` for `MainActivity`, but no `LEANBACK_LAUNCHER`, TV banner, `android.software.leanback` declaration, or `android.hardware.touchscreen` with `required=false`. The source manifest therefore does not advertise a proper TV launcher entry or non-touch compatibility. A merged manifest could add features through dependencies; verify the merged APK manifest before a store/distribution claim. Existing adaptive/mipmap icons are phone launcher resources ([ic_launcher.xml](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)); there is no TV banner resource. These are concrete packaging blockers.

There is no fixed `screenOrientation` in the manifest. `MainActivity` handles configuration changes and renders a phone-root UI with system-bar/window-inset and bottom navigation assumptions. `minSdk=24`, `targetSdk=37`, and four ABIs including `arm64-v8a`/`armeabi-v7a` are configured in [app/build.gradle.kts](app/build.gradle.kts); SDK levels alone do not prevent TV operation, but target-SDK permissions/foreground-service behavior need actual TV validation. `MainActivity` accepts `magnet`, `stremio`, and torrent-content deep links; these route to `MainViewModel.acceptIntent` and require TV navigation testing. The manifest grants Internet/network, notifications, foreground media-playback service, install-package, and boot permissions. `ServerService` is a local streaming-server foreground service, not a Media3 playback service. `supportsPictureInPicture=true` is declared, but the activity/player code inspected has no explicit PiP entry/remote behavior. `media3-session` is a dependency, yet no `MediaSession` construction, session service, or media-key dispatch was found in app source. No `onKeyEvent`, `onPreviewKeyEvent`, DPAD keycode, or `MainActivity.dispatchKeyEvent` path was found. These are TV integration tasks, not evidence against the existing playback engines.

## D-pad, Back, focus, and keyboard assessment

The current Compose source has one `FocusRequester` in `SearchScreen`, used to focus the text field on entry. No `focusProperties`, `focusGroup`, focus restoration, per-card focus requests, or key-event routing was found in the app source. Material buttons and `Modifier.clickable` may receive default keyboard focus, but default traversal is not a deterministic contract for nested rows, dynamic shelves or overlays. [PosterComponents.kt](app/src/main/java/com/stremio/mobile/presentation/components/PosterComponents.kt) has horizontal `LazyRow`s with stable poster keys; the outer `BoardScreen` is a vertical `LazyColumn`, and Discover/Library use manual `chunked(3)` rows. A TV tree needs explicit entry focus, horizontal-to-vertical routes, focus identity keyed by content, scroll-to-focused-item, restoration after details/Back, and a fallback when a lazy item is recycled or removed. `onShelfVisible` triggers Core range loading when a shelf composes, so loading and focus movement may coincide; focus must remain responsive through those updates.

Back is handled for selected overlays in `StremioMobileApp` and for the player/search via `BackHandler`; the settings/discover handler covers some nested states. There is no centralized route stack or focus-return contract. Details are a bottom sheet whose swipe-up opens streams ([DetailSheet.kt](app/src/main/java/com/stremio/mobile/presentation/screens/DetailSheet.kt)); backdrop taps dismiss it. Search taps outside to clear focus. Player video/stats overlays use tap detectors. These interactions cannot be relied on with a D-pad. Dropdowns, alert dialogs, season/provider rows, stream rows, settings sliders/toggles, and audio/subtitle dialogs need initial focus, visible focus states, ordered traversal, Center/Enter activation and predictable Back priority. Physical keyboard arrow/Tab/Enter/Escape behavior should be tested alongside D-pad, not assumed equivalent.

Search's reusable part is `MainViewModel.search` and Core search. [SearchScreen.kt](app/src/main/java/com/stremio/mobile/presentation/screens/SearchScreen.kt) uses a Compose `OutlinedTextField`, `TextFieldValue` for cursor selection, `ImeAction.Search`, and a search keyboard action that only hides the IME. It auto-requests text-field focus and has a `BackHandler` that closes search; it does not define TV remote/physical-keyboard-specific Enter, IME-dismiss-versus-route-Back, result focus after dismissal, or restoration from details. A TV implementation should use the platform IME-capable text field, retain the debounced search flow, accept text from Android TV IME, USB/Bluetooth keyboard and Google TV phone remote through the normal input connection, and explicitly move focus between field and results on Search/Enter or keyboard dismissal. Validate phone-remote text injection on real Google TV; static code cannot establish that device behavior. Login fields in [AuthFlow.kt](app/src/main/java/com/stremio/mobile/presentation/screens/AuthFlow.kt) likewise have no explicit IME next/done or TV focus route; login needs a D-pad-only path and keyboard handoff.

## Performance evidence and profiling targets

**Confirmed by source:** `MainViewModel.uiState` combines 52 flows into one `MainUiState`, collected at the root of `StremioMobileApp`; a change to any included field can invalidate broad composition scopes. This is a structural invalidation risk, not a measured frame drop. Home shelves and poster tiles do use lazy lists and stable poster keys; stream and addon lists also use keys, so a blanket claim of unkeyed lists would be false. Discover, Library and Search use `chunked(3)` rows without stable row keys ([StremioMobileApp.kt](app/src/main/java/com/stremio/mobile/presentation/screens/StremioMobileApp.kt), [SearchScreen.kt](app/src/main/java/com/stremio/mobile/presentation/screens/SearchScreen.kt)); insertions/reordering can disturb row identity and focus. `FeaturedHeroPager` auto-animates every five seconds. Modern mode allocates backdrop layers/blur effects in the board, bottom bar and glass components; Classic mode bypasses much of that path. `PosterTile` requests a bounded 224×340 Coil image with memory caching and crossfade, while `MainApplication` configures disk caching. `BoardRepository` mapping and search shelf mapping use `flowOn(Dispatchers.Default)`; server network checks and update work use `Dispatchers.IO`. Those are positive safeguards.

**Probable risks:** frequent Core/player updates through the broad root state; image decoding/crossfades plus many simultaneously visible posters on weak GPUs; nested vertical/horizontal lazy traversal; automatic hero animation interrupting focus perception; backdrop draw cost in Modern mode; `MainApplication` initializes Core and integrations during application start, and `MainViewModel` starts the server and checks updates on construction. These could affect cold start or focus latency, but no runtime measurement establishes impact. `AndroidStorage` uses synchronous SharedPreferences reads and `Core.initialize` is invoked from `AppContainer` creation in `Application.onCreate`; inspect traces before attributing startup slowness to either. `MainViewModel.onShelfVisible` dispatches range loads upon shelf composition; no request is directly wired to focus-change events, but scrolling focus could cause newly composed shelves to load. Keep such work asynchronous and independent of focus feedback.

**Profile before optimizing:** frame timing during rapid Left/Right and Up/Down on a low-end TV; recomposition counts per focused card and across root state updates; image cache hit/miss and decode pressure; startup Core/JNI/server/update timeline; network request counts while traversing shelves; memory/GPU usage for Classic vs Modern; player control overlay and subtitle dialog latency. Use those measurements to decide any state splitting or image policy changes. No performance bottleneck is claimed solely from the existence of animation, blur, or Core calls.

## Player suitability

The engine abstraction is suitable for TV reuse: `Player` exposes play/pause/seek, speed, resize, audio/subtitle track selection, external subtitles and runtime state; Exo uses Media3 `ExoPlayer` plus `PlayerView` with its built-in controller disabled, and MPV uses a Surface-based view and native library. `PlaybackRepository` resolves Core streams, resumes, and reports progress/track preferences. `PlayerScreen` already handles fullscreen video, buffering/errors, next episode, track selection and refresh-rate matching logic. None of this demonstrates a need to replace either engine.

TV work is concentrated in controls and system integration. [PlayerControls.kt](app/src/main/java/com/stremio/mobile/presentation/screens/player/PlayerControls.kt) provides buttons, menus and a `Slider`; its seek action is called directly on value changes, which needs deliberate D-pad step/commit semantics. `PlayerScreen` currently toggles controls with tap gestures and has a Back handler that exits the player; the desired Back priority when a menu/dialog/controls is open must be defined. There is no explicit remote media-key mapping or actual `MediaSession` despite the dependency. MPV needs the same session/key bridge as Exo if background/system controls are required. Test video output, audio pass-through/track selection, subtitles, seeking, Back and playback state on representative TV chipsets; static inspection cannot confirm codec or remote-key behavior.

## Screen-by-screen TV assessment

Risk below means implementation uncertainty/complexity, not appearance.

| Screen | Current implementation | Reusable logic | Mobile assumptions and TV work | Risk |
|---|---|---|---|---|
| Login | `AuthFlow.kt`; `StremioMobileApp.kt` | `AuthRepository`, Core login/session, ViewModel actions | Phone-sized intro/forms, Facebook activity, no explicit field/action focus or IME route. Build remote-navigable login and test credential entry. | Medium |
| Home | `StremioMobileApp.kt` `BoardScreen`; `PosterComponents.kt`; `FeaturedHero.kt` | Board and continue-watching shelves, pagination actions | Bottom nav, 112 dp posters, auto hero, nested rows. Build 10-foot shelves and stable focus/return. | High |
| Discover | `BoardScreen` Discover branch and filters | Core discover request, `CatalogRepository`, filters | Three-column manual rows/dropdowns and mobile section navigation. Build focusable filters/grid and paging. | Medium |
| Search | `SearchScreen.kt` | Core search, ViewModel debounce/results | Auto-focused mobile text field, tap-dismiss, no remote Enter/result focus. Build TV keyboard/focus handoff. | Medium |
| Library | `BoardScreen` Library branch | Core library/sync, `CatalogRepository` | Manual 3-column rows, no focus restoration after return/update. Build TV grid and filter route. | Medium |
| Meta/details | `DetailSheet.kt` | `openDetails`, metadata mapping, library toggle/open-stream actions | Height-limited bottom sheet, swipe/tap close, small text. Build full TV detail route with explicit Back. | Medium |
| Stream selection | `StreamsSheet.kt` | Episodes, providers, sorting, stream resolution actions | Full-screen mobile list/season chips, dynamic rows; focus not preserved as streams arrive. Build TV episode/stream route and loading focus. | High |
| Player | `PlayerScreen.kt`, `PlayerControls.kt` | Player engines, playback state/progress, tracks/subtitles | Tap-to-show, slider/menu/dialog focus, Back exits, no media keys/session. Build remote controls and test both engines. | High |
| Addons | `AddonsScreen.kt`, `AddonDetailsSheet.kt`, Settings branch | Core addon browse/install/actions | URL text input, dropdowns, bottom detail sheet. Build TV management and keyboard handoff. | Medium |
| Settings | `SettingsPanel.kt` and `*SettingsScreen.kt` | Profile/server/local preference operations | Phone rows, dropdowns/sliders, nested state within board. Build TV settings routes and focus/Back contract. | Medium |

## Phased migration plan

| Phase | Objective and components | Dependencies | Major risks | Validation criteria |
|---|---|---|---|---|
| 1. TV boot and focus proof | TV manifest/banner, TV activity/root, login, one home shelf, details/Back; reuse `AppContainer`/ViewModel/Core | Existing auth/catalog flow | Launcher packaging, auth IME, focus restoration | TV launcher icon/banner; fresh and restored login; D-pad-only path to a details page and back with correct focus |
| 2. Focus/navigation foundation | Reusable TV focusable card, route/focus key store, lazy row/grid traversal, overlay Back ordering | Phase 1 route model | Recycled items, async shelf changes | Deterministic row transitions and return focus after navigation/data refresh |
| 3. Browsing | Full Home/continue watching, Discover and Library TV layouts/filters | Phase 2 | Large catalogs, lazy focus restoration | Remote-only browse/filters, stable loading/error states |
| 4. Search and keyboard | TV search route and IME/physical/phone remote handoff | Phase 2 | Device-specific IME events | Text input from all three sources; Enter/Search, Back and result focus tested |
| 5. Episodes and streams | TV details actions, series episodes, provider/sort and stream selection | Phases 2–3 | Incremental addon results changing list | Choose movie/episode stream using D-pad; focus stable as streams load |
| 6. Playback controls | TV control overlay, D-pad seek, media keys/session, track/subtitle dialogs | Phase 5 | Exo/MPV parity, hardware codecs, Back priority | Play/pause/seek/Back/track changes on both engines and representative hardware |
| 7. Management | TV settings and addons, server preferences and nested routes | Phase 2 | Text URL input and complex settings controls | Remote-only account/addon/settings tasks with saved values |
| 8. Measure and polish | Trace navigation/startup/player; tune only evidenced bottlenecks, accessibility/focus visibility | Runnable flows | Hardware variance | Focus latency/frame metrics and cold-start traces on low-end devices; regression pass |

## Verdict

**Yes, with significant TV UI work.** The repository already supplies Stremio Core, account/addon/catalog/library/search/metadata flows, local streaming, two playable engine backends and subtitles in Android code. Its one-module layout permits a TV entry and presentation package without first moving infrastructure. The current APK and Compose tree are phone-oriented: no TV launcher declarations/banner and no deterministic D-pad/focus or remote-player controls. Static inspection supports feasibility, not a claim of TV runtime performance or certification.

## Reuse Matrix

| System | Class | TV disposition |
|---|---|---|
| Core/JNI, account/session, addon/catalog/library/sync | A | Share existing bridge and repositories |
| Streaming server, stream resolution | A | Share; validate native/device behavior |
| Exo/MPV engines and playback reporting | A | Share engines; add TV control integration |
| `MainViewModel`/large UI state | B | Reuse actions for milestone; split only where TV friction is demonstrated |
| Search logic, image/cache, settings operations | B | Share logic; adapt input, dimensions and presentation |
| Mobile navigation, shelves/grids, sheets, login/search/settings UI | C | Dedicated TV composables and focus routes |
| Player controls/dialogs | C | Dedicated remote UI over shared `Player` |

## Top 10 TV blockers

Ordered by dependency and impact:

1. Missing Leanback launcher/TV banner/non-touch feature declarations in the app manifest and resources.
2. No TV entry/root; `MainActivity` always renders `StremioMobileApp`.
3. No deterministic D-pad focus model across nested shelves, grid items, overlays and Back.
4. Login has no verified D-pad/IME/physical-keyboard completion path.
5. Mobile bottom navigation, narrow poster/grid geometry and detail sheet are unsuitable as primary 10-foot routes.
6. No focus identity/restoration across lazy recycling, async catalog updates and return from details.
7. Search lacks explicit TV keyboard-dismiss, Enter/Search, results focus and return behavior.
8. Stream selection/episode/provider lists lack stable remote focus during incremental addon loading.
9. Player controls rely on taps/slider interaction; no explicit D-pad seeking or Back/menu focus contract.
10. Media keys/session and TV hardware playback/startup performance remain unimplemented or unverified.

## Proposed architecture

Use **a dedicated TV presentation package in the existing `:app` module**. Add `com.stremio.mobile.presentation.tv` for `TvApp`, TV routes, focus primitives and screens, plus a TV launcher activity in the app package. Reuse `AppContainer`, repositories, `StremioCore`, server/controller, data models, `PlaybackRepository` and both `Player` implementations. Keep `StremioMobileApp` and mobile `MainActivity` intact for phones. The initial TV root may consume `MainViewModel` actions/state; limit later extraction to demonstrated TV state needs. Decide whether a separate TV application module and app ID are needed only after a runnable combined-APK milestone and distribution requirements are known.

## First milestone

Produce a debug APK with a TV launcher banner/entry, a TV-only login and home route, one Core-backed catalog shelf, a TV details route, and deterministic D-pad navigation/Back from shelf → details → same shelf item. Support fresh credential login and restored session. Provide visible focus, Center/Enter activation and a basic physical/TV IME path. Do not include stream selection, player, search, addons or full settings in this milestone. Validate on Android TV and Google TV launchers; test with remote only and a physical keyboard, including empty/loading/error shelf states and process recreation. This is the smallest useful proof that the existing data layer can drive a separate TV UI.

## Files likely involved first

| Path | Reason |
|---|---|
| `app/src/main/AndroidManifest.xml` | TV launcher filter, non-touch/Leanback declarations, TV activity and banner reference |
| `app/src/main/res/drawable/tv_banner.png` (new) | Launcher banner referenced from manifest |
| `app/src/main/java/com/stremio/mobile/TvActivity.kt` (new) | TV entry using existing `MainApplication.container` and ViewModel factory wiring |
| `app/src/main/java/com/stremio/mobile/presentation/tv/TvApp.kt` (new) | Auth/home/details route and Back ownership |
| `app/src/main/java/com/stremio/mobile/presentation/tv/TvFocus.kt` (new) | Stable item focus keys, directional rules and return focus |
| `app/src/main/java/com/stremio/mobile/presentation/tv/TvLoginScreen.kt` (new) | Remote-navigable credential/IME flow |
| `app/src/main/java/com/stremio/mobile/presentation/tv/TvHomeScreen.kt` (new) | One Core-backed shelf and loading/error states |
| `app/src/main/java/com/stremio/mobile/presentation/tv/TvDetailsScreen.kt` (new) | Metadata display, close/return focus |
| `app/src/main/java/com/stremio/mobile/presentation/viewmodel/MainViewModel.kt` (conditional) | Only if current action/state exposure cannot support the TV root cleanly; avoid broad refactor |
| `app/build.gradle.kts` (conditional) | Only if a chosen TV Compose dependency/resource configuration is actually needed |

## Unknowns requiring runtime verification

- Merged manifest feature requirements and launcher presentation on specific Android TV/Google TV launchers.
- Whether credential fields accept Google TV phone-remote text and how Back is delivered while the IME is open.
- Native Core/server/MPV libraries and media codecs on representative 32/64-bit TV hardware; Exo/MPV track and subtitle behavior.
- Foreground service, notification, boot receiver and installer flows under target-SDK rules on actual TV firmware.
- PiP behavior and whether media keys reach the app; system media session integration needs implementation and validation.
- Frame timing, focus response under image/network load, startup time, memory/GPU use, and Classic versus Modern effect cost on weak devices.
- Core/auth/addon network behavior and account sync on real networks; static source does not verify service availability.

## Recommended next Codex task

Implement only the **first runnable TV milestone** above on a separate branch/worktree: add the TV launcher/activity/banner and a small dedicated Compose TV login → one home catalog shelf → details → Back flow using existing Core/repositories and player code unchanged. Make focus identity/restoration explicit, cover D-pad Center, Back and keyboard/IME login, then build/install on Android TV and Google TV emulators or devices and record the observed behavior. Keep mobile UI behavior unchanged and defer stream playback and full-screen migration.
