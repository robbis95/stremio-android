# Stremio Android TV Architecture & Performance Strategy

Status: active technical strategy  
Research date: 2026-09-29  
Canonical branch: `feat/android-tv`  
Current TV implementation baseline: `f19d8c267289b32f9f0e3f8054d1d120a1edfc37`

This document turns the TV design direction into implementation rules grounded in the Stremio architecture, the capabilities exposed by the current Android client, the vendored Kotlin/Core bridge, and current Android TV performance guidance.

It complements `TV_TRANSFORMATION_PLAN.md`. The transformation plan defines product phases. This file defines how those phases should be engineered.

---

## 1. Primary product goal

The Android TV client should feel:

- immediate
- deterministic
- content-led
- remote-native
- visually premium
- noticeably lighter and faster than a phone UI adapted to television

Speed is part of the product design.

The user should see useful content as early as possible, get immediate feedback to D-pad input, and rarely wait for a network request before the UI can react.

The app must use real Stremio semantics and real data. Do not invent a parallel recommendation engine, account system, media database, or stream protocol merely to imitate another streaming service.

---

## 2. Source-of-truth hierarchy

Use this ownership model.

### Stremio Core owns

- account/session state
- profile settings
- installed addons
- board/catalog state
- Discover state
- Library state
- Continue Watching state
- metadata requests
- episode/video metadata
- stream discovery
- player state
- playback progress synchronization
- next-video behavior
- streaming-server settings/state where already bridged

### Android repositories own

- conversion from Core models into Android presentation-friendly models
- Android-local preferences that are genuinely client-specific
- Android platform integration
- image/cache configuration
- local playback engine integration

### TV presentation owns

- screen composition
- focus/navigation
- TV-specific interaction rules
- visual prioritization
- temporary per-route UI state
- presentation-only focus/scroll memory

The TV layer must not duplicate Stremio business state just to make screens easier to build.

---

## 3. Current Core baseline and modernization rule

The included `stremio-core-kotlin` build currently resolves official `Stremio/stremio-core` commit:

`90c38f181d290fc705049e4c8bd30df00f6f3e66`

That commit is the Core 0.59.0 release.

As of 2026-09-29, the latest published official Core release is 0.63.2.

Therefore:

- do not assume current upstream Core APIs are available through our Android bridge
- do not update Core opportunistically during a UI task
- do not permanently design new TV features around limitations that exist only because the bridge is older
- maintain a separate controlled Core-bridge modernization track

A Core upgrade must be treated as an infrastructure migration with regression checks for auth, board, Discover, Library, addons, metadata, streams, player state and synchronization.

---

## 4. Capability matrix

### A. Available in the current Android app and safe to design around now

| Capability | Current source | TV use |
| --- | --- | --- |
| Account/session | AuthRepository + Core Ctx | QR login, session restore, account state |
| Board/catalog shelves | Board model | Home shelves |
| Continue Watching | ContinueWatchingPreview | High-priority Home shelf, resume UI |
| Discover | CatalogWithFilters | Dynamic content browsing |
| Library | LibraryWithFilters | Library route, add/remove |
| Search across addon catalogs | SEARCH CatalogsWithExtra | Full submitted search |
| Full metadata | MetaDetails | Details hero, description, cast, genres, runtime |
| Series videos | MetaDetails videos | seasons/episodes |
| Episode watched/current status | Video | episode state |
| Episode progress | Video.progress | progress UI once mapped through Android |
| Stream discovery | MetaDetails streams | stream selection |
| Playback resolution | Core Player model | direct/torrent playback |
| Next video | Player.nextVideo | next episode |
| Watch progress/resume | Player/library state | Resume |
| Installed addons | AddonsWithFilters | management later |
| Streaming server | JNI server controller | torrent/local streaming |
| ExoPlayer + MPV | existing Android playback | TV playback engines |

### B. Available in the current official Core 0.59 model, but not fully exposed/used by our Android presentation

These are high-value bridge/presentation opportunities.

#### Rich MetaItemPreview data

The current protobuf bridge already defines preview fields including:

- poster shape
- poster
- background
- logo
- description
- release info
- runtime
- released timestamp
- links
- behavior hints
- deep links
- in-library
- watched
- in-cinema

Our current `CatalogItem` preserves only a subset.

This is a major optimization opportunity because Home can use already-loaded preview data instead of opening MetaDetails merely to paint a hero.

#### LocalSearch

Official Core 0.59 contains a LocalSearch model and search action designed for local autocomplete. The Android Field enum includes `LocalSearch` and ActionLoad has a LocalSearch arm, but the current bridge has no usable LocalSearch payload (`get_state_binary(LocalSearch)` is unimplemented) and the runtime protobuf does not expose Core's ActionSearch query action.

Do not implement a duplicate Android-only Cinemeta index merely to bypass this.

Future targeted work, titled **LocalSearch bridge completion**:
1. complete the Kotlin/protobuf query-action and result-state bridge
2. add bridge tests
3. then consider local suggestions while typing

Until then, TV uses the working addon-aware Core SEARCH model for meaningful queries. Do not add a parallel Android index.

#### Search history

Official Stremio Web reads search history from Core Ctx, but the current Android protobuf Ctx omits that search-history data and the Android ActionCtx protobuf also omits the clear-history action despite Core supporting it.

Treat search-history integration as another bridge capability, not as a new private TV-only history database unless a deliberate product decision is later made.

#### LibraryByType

The current bridge includes LibraryByType protobuf support.

Evaluate this before building complex TV Library grouping/sorting manually.

### C. Core features that require explicit bridge audit before product use

- Calendar
- newer Live TV / guide functionality from post-0.59 Core
- newer playback preference changes
- newer Core fixes and model changes
- any new fields introduced after the pinned Core commit

Never claim these are available to the TV app until the Kotlin/protobuf bridge exposes and verifies them.

---

## 5. Preserve metadata instead of requesting it again

This is a key architecture rule.

Current pattern:

`Core preview -> BoardRepository -> reduced CatalogItem -> TV`

Target pattern:

`Core preview -> loss-minimized presentation model -> TV`

### Expand the Android preview model deliberately

Before the production Home/hero is built, evaluate extending `CatalogItem` or introducing a TV-neutral metadata-preview model with fields such as:

- `posterShape`
- `logo`
- `description`
- `runtime`
- `released`
- `links`
- `inLibrary`
- `background`
- existing release/watched/progress state

Do not add fields the Core preview does not supply.

Keep raw Core classes out of composables when a small stable presentation model is cleaner.

### Why this matters

A focused Home card should be able to update nearby UI from memory immediately.

Do not perform a MetaDetails request every time focus moves across posters.

Full details loading belongs to deliberate navigation into Details or other actions that genuinely need the full model.

---

## 6. Hero strategy

The future Home hero should be **focus-driven, preview-driven, and non-blocking**.

### Preferred behavior

1. Home appears with real shelves.
2. Initial focus is established.
3. The hero uses metadata already available for that focused preview.
4. Rapid D-pad movement does not trigger full metadata requests.
5. Hero transitions only after a short focus dwell or when focus settles.
6. Opening Details starts/uses the full MetaDetails flow.

A small dwell can reduce visual thrashing, but it must never delay D-pad focus itself.

### Hero fallback order

Use real available data only.

Suggested fallback:
1. background artwork
2. appropriate poster artwork if no background exists
3. neutral branded surface

Title treatment:
1. content logo if supplied and suitable
2. text title

Metadata:
- release info
- runtime
- real genres/links if already present in preview
- watched/library state where meaningful

Do not invent ratings or descriptions.

Do not auto-play trailers on focus in the first production version. It adds network, decoder, audio and focus complexity and works against the speed goal.

---

## 7. Continue Watching should be a first-class fast path

Continue Watching is already derived from Core and includes useful state such as:

- progress
- video id
- watched state
- remaining episodes

The Android client also stores a local remembered stream selection and already attempts to reuse it for Continue Watching.

TV design should capitalize on this.

### Desired interaction

From Home:
- focused Continue Watching card clearly displays progress
- Select may open Details or a contextual resume action depending on the final interaction model
- a deliberate Resume action should minimize steps to playback

### Later smart stream continuation

Stremio stream behavior hints include `bingeGroup`.

Audit and preserve this field in the Android stream model before implementing smarter automatic next-episode stream matching.

Desired behavior:
- prefer a compatible stream in the same binge group for the next episode
- fall back safely to normal stream selection if no compatible stream exists
- never silently choose an unrelated source merely because it is first in the list

This is a real Stremio protocol capability and should be used before inventing heuristic stream matching.

---

## 8. Search architecture

Search should feel immediate while avoiding expensive repeated addon searches.

### Current behavior

The Android ViewModel debounces typing and then runs a full Core search across installed catalogs.

That is functional but is not the desired TV interaction for every keystroke.

### Target design

#### Stage 1: local interaction

While typing:
- update text instantly
- show recent history if Core bridge exposes it
- show LocalSearch suggestions once bridge support exists
- never block the keyboard on network search

#### Stage 2: committed search

On Search/Enter or an intentional suggestion selection:
- run the full addon search
- preserve shelf source/context
- progressively render real returned results

Phase 4A's TV keyboard updates local query text immediately. Blank input and fewer than two non-space Unicode code points clear/hold an empty state without remote work. Meaningful changes use the existing cancellable 300 ms ViewModel debounce, Core `Field.SEARCH`, addon `CatalogsWithExtra`, and `loadSearchRange`. Render real shelves in emitted order, including their titles, loading/errors, and see-all request identity. The current stage still runs addon search after the threshold while typing; LocalSearch remains a bridge task.

### Bridge rule

Do not create a second private search engine just because LocalSearch is not currently surfaced by the Kotlin bridge.

Add the bridge support in a dedicated infrastructure task.

---

## 9. Addon-first architecture

Stremio addons are a runtime capability system, not merely a settings screen.

The TV client should therefore be built so installed addons can change:

- which catalogs exist
- catalog titles and ordering
- content types
- which catalogs support search
- which filters/extras are available
- metadata sources
- stream sources
- subtitles
- loading/error characteristics

The TV client does **not** need to provide full addon installation/configuration workflows as part of the primary TV experience. Users may manage/configure addons on another Stremio client.

The important requirement is that ordinary TV browsing and playback remain addon-neutral.

### Capability-driven UI

Do not hard-code assumptions such as:

- every catalog is Cinemeta
- only movie/series types exist
- every catalog supports search
- every catalog supports genre
- every item ID starts with `tt`
- metadata and streams come from the same addon
- every stream is a simple HTTP URL or torrent

Use Core-generated requests/selectable state and addon descriptors rather than reconstructing addon routing in TV code.

### Catalog extras

Addon catalogs may declare extras such as:

- `search`
- `genre`
- `skip`
- `date`

Extras may be required or optional and may carry explicit option lists.

Future Discover/Search UI should derive controls from real Core/addon capabilities where they are exposed.

Do not invent filters that the selected catalog cannot satisfy.

### Progressive failure isolation

Addon requests can complete independently and at different speeds.

A slow or failed addon/catalog must not turn the whole Home/Search/Discover surface into one blocking spinner.

Prefer:

- render ready shelves immediately
- show loading/error state at the smallest meaningful scope
- allow navigation among ready content
- let later addon emissions populate their own surfaces without stealing focus

### Metadata and streams

Do not bind a title to one assumed provider.

Core should remain responsible for aggregating compatible metadata and stream addon responses according to Stremio rules.

TV presentation should consume the resulting Core models and preserve source/addon identity only where it helps the user understand or choose between streams.

### Subtitles

Subtitle availability is addon-driven.

Preserve stream metadata such as filename/video hash/video size because Stremio can use these to identify subtitle matches.

Do not build a separate TV subtitle-discovery backend.

### Stream behavior hints

Respect real stream semantics, including:

- `bingeGroup`
- `notWebReady`
- source type
- filename
- video size/hash
- external/Android-TV-specific targets where Core exposes them

Do not flatten every stream into an assumed direct playable URL before Core/player resolution.

### Addon management scope

For the current product plan:

- TV browsing/playback must support already-installed/configured addons
- full addon configuration on TV is not required
- do not block core TV milestones on building addon management/configuration UI
- if a future TV surface encounters an addon that requires external configuration, a lightweight explanatory state is sufficient unless product scope explicitly changes

### Testing implication

Later integration tests should include more than the default addon set.

At minimum validate:

- multiple catalogs with duplicate/similar names
- non-Cinemeta metadata
- more than one stream addon responding
- one slow/failing addon alongside healthy addons
- a catalog supporting search
- a catalog that does not support search
- non-standard content types where supported by Core
- subtitle-capable streams

## 10. Dynamic information architecture

Do not hard-code the concept artwork's fictional navigation taxonomy.

Preferred high-level TV destinations remain:

- Home
- Discover
- Library
- Search
- account/settings entry

Movies and Series can be dynamic Discover types/filters when that matches real Core selectable data.

Other types should appear only when real installed addons/Core data expose them.

This makes the UI adapt to the user's Stremio configuration instead of pretending every account has the same content universe.

The production top-level shell now contains Home, Discover, Library, and Search, in that order. Addons and Settings remain absent until their routes are implemented and wired.

---

## 11. TV state architecture

The TV root no longer collects the full `MainUiState`. The current implementation exposes read-only TV-facing flows for account state, board shelves, selected details, Continue Watching, account-link state, session restoration, board loading, Search query/results/shelves, and narrow Discover and Library projections as needed.

`MainUiState` still exists for the broader/mobile application and combines many unrelated concerns, so future TV routes must continue to avoid falling back to whole-state collection.

### Target

Continue adding narrow TV-facing state slices without duplicating Core truth as new TV routes are implemented.

Candidate grouped states, only if/when individual flows become unwieldy:

- `TvAuthUiState`
- `TvHomeUiState`
- `TvDetailsUiState`
- `TvSearchUiState`
- `TvDiscoverUiState`
- `TvLibraryUiState`
- `TvPlaybackUiState`

The existing MainViewModel may continue exposing narrow read-only flows until grouping has a concrete benefit.

### Top-level route and focus ownership

The shared navigation exposes Home, Discover, Library, and Search. `TvRouteState` records an explicit, exhaustive Home/Discover/Library/Search origin when Details opens; closing Details returns to that exact route. Home and Search stay independently composed during top-level route switches; Discover and Library compose only while active and save their own grid/filter state explicitly. Home keeps its semantic `TvFocusMemory`; Search has a separate result-key namespace, requester registry, row-scroll positions, and last keyboard/result target; Discover and Library each have a separate saveable `type:id` content key, fallback index, selected filter identity, grid scroll position, and pagination trigger state. Switching top-level destinations is not a back stack and does not write another destination's content memory.

MainViewModel exposes read-only `tvSearchQuery`, `tvSearchResults`, and `tvSearchShelves` views of its existing search flows. It also exposes a narrow immutable `TvDiscoverUiState` mapped from existing Core `CatalogWithFilters` and `CatalogShelf` state. The TV screen owns focus and presentation; Core remains the sole addon-search and Discover backend.

### Production Discover grid and focus

Discover maps only Core-exposed types, catalogs, and non-empty extra groups. It keeps every Core `ResourceRequest` attached to its option and never assumes a type, add-on, catalog, genre, or IMDb identity. The shared mobile ViewModel's default selection policy prefers Core's selected request, then selected type, selected catalog, first type, and first catalog; it does not synthesize a movie request.

The grid uses five fixed columns at the tested 960×540 dp viewport, 144 dp posters, 72 dp safe horizontal margins, stable semantic keys, and the existing TV poster visual contract. Left/Right choose adjacent items; Up/Down choose the same or nearest available column in the neighboring row. Explicit D-pad handling avoids relying on Compose's dynamic lazy-grid focus search. The first row returns focus to the last available filter group, or to Discover nav when no filter controls exist. Filter groups have explicit cross-group traversal and a Down path back to a sensible grid item.

Core's `CatalogWithFilters.selectable.nextPage` is the only pagination source. The app-side `StremioCore.loadDiscoverNextPage()` dispatches the generated `ActionCatalogWithFilters.LoadNextPage` action to `Field.DISCOVER`. A page is requested when a user-focused item reaches the last currently loaded row, only after the focus index advances; the next-page request identity is de-duplicated in the ViewModel. Appending items does not initiate another request just because item count changed and does not reset semantic focus or scroll. Initial loading leaves filters navigable, and a later page error leaves existing items present.

### Production Library state and interaction stability

The production Library uses Core's existing `LibraryWithFilters` selection and Core-provided `LibraryRequest` values for each type and sort option. `TvApp` observes only a narrow immutable `TvLibraryUiState`; raw Core state stays inside repository/ViewModel boundaries. The app dispatches `ActionLibraryWithFilters.LoadNextPage` to `Field.LIBRARY` and uses Core's `selectable.nextPage` as the pagination source. It does not manually increment page numbers.

`LibraryItem` is mapped loss-minimally to `CatalogItem`: id, type, name, poster and poster shape, watched state, remaining episodes, supported behavior hints, and real progress from `timeOffset / duration`, normalized and clamped to the presentation range 0..1. Unknown progress stays null; absent LibraryItem metadata is not fabricated.

Library type labels retain null as All, known Movie/Series labels, and humanized unknown types. The supported Core sorts remain LastWatched, Name, NameReverse, TimesWatched, Watched, and NotWatched, shown as Last watched, Name A–Z, Name Z–A, Most watched, Watched first, and Unwatched first.

The five-column grid and poster geometry match Discover. Library has independent semantic focus and scroll memory. Its pure interaction snapshot is keyed by logical selection (type + sort), not page: same-selection emissions refresh item data while retaining relative order for survivors, dropping removed items, and appending newly exposed items in incoming Core order. An explicit type/sort change adopts Core's authoritative order. Pagination preserves the snapshot, focus, and scroll; only focus reaching the loaded tail may trigger the current Core nextPage request, and request identity suppresses duplicates. If the focused item disappears, nearest prior index is restored when possible, otherwise focus moves to the remaining items or filters/nav.

Runtime verified All/Movie/Series, all six labels, watched/progress presentation, selected-versus-focused filters, five-column movement, exact Silo Details return, and one Library → Home → Library cycle that restored All / Last watched, visible order, and filter focus. Discover rendered on route entry; a fresh Search regression was not completed. The first launch after reinstall hit one ActivityManager startup ANR while the emulator also reported system-service ANRs; a clean relaunch resumed `TvActivity`, and the bounded final log sample had no recurring app fatal, Compose, or focus warnings. Repeated multi-position Details, deep-position route retention, Search regression, runtime pagination, natural sync mutation, and an empty filter remain unexercised; pure tests cover their state policies. Runtime screenshots are saved outside Git under `/private/tmp/stremio-android-tv-phase4c-final/`. The production route returns to Home on Back, while Details returns to its explicit Library origin. The resulting top-level order is Home, Discover, Library, Search.

### Production Home ownership

`TvApp` supplies the existing board-shelf and Continue Watching flows to a small immutable `TvHomePresentation`; it does not introduce another backend state model. That presentation adds only layout semantics: a non-focusable hero section, an optional stable `tv:continue-watching` shelf, and board shelves that retain original board indices. Its explicit semantic-key-to-LazyColumn-index mapping keeps focus restoration independent of the hero and optional shelf. Continue Watching enrichment is a pure presentation transformation that matches existing board previews by `type + id` and preserves all library progress/playback state.

The hero observes the focused `CatalogItem` preview only. Its visual candidate settles after a 150 ms dwell, with no MetaDetails request on focus movement. Shelf visibility invokes the existing bounded `MainViewModel.onShelfVisible(originalBoardShelfIndex)` policy; focus movement itself does not preload catalogs. The Home hero scrolls as part of the same feed. Search, Discover, and Library are real top-level destinations; Settings controls remain absent until their route can complete navigation.

Do not split the ViewModel merely for aesthetic architecture.

Split ownership only when responsibility or performance evidence supports it.

### Rule

A Home-only change should not require TvHomeScreen to observe unrelated subtitle/server/settings state.

Use stable immutable state and selective collection.

---

## 12. Compose for TV strategy

The TV subtree now uses TV-optimized Material components from:

`androidx.tv:tv-material`

Version `1.1.0` is the current stable release and is adopted by the app module. `TvTheme` supplies the existing Stremio palette and typography through `androidx.tv.material3.MaterialTheme`; it does not use TV Material defaults as the product palette. Android's mobile UI continues to use the existing `androidx.compose.material3` dependency and behavior.

### Migration rule

Use TV Material `Text`, `Button`, `OutlinedButton`, and the non-interactive `Surface` where they are a good semantic match. Do not use mobile `MaterialTheme` in TV routes, and do not globally replace imports in the shared app module.

The QR login's optional email/password fallback remains a localized compatibility exception using mobile `OutlinedTextField`, because TV Material has no equivalent text-field surface. Its colors and typography are explicitly set from the TV palette; its FocusRequester, focus properties, and IME actions remain in place. The small non-interactive loading indicators remain mobile Material3 `CircularProgressIndicator`s. These exceptions do not wrap the TV subtree in mobile MaterialTheme.

### Migration sequence

1. keep the mobile/TV dependency boundary explicit
2. use TV Material controls for remote actions, with its focus interaction and no duplicate custom focus decoration
3. keep the QR card non-interactive and use TV Material's non-interactive Surface
4. verify D-pad/focus runtime after behavioral component migrations

The custom semantic Home shelf architecture remains authoritative: `TvFocusRegistry`, explicit Left/Right routing, spatial X-preserving Up/Down traversal, semantic route return, and independent row-scroll memory are unchanged. `TvPosterCard` intentionally remains a custom focusable layout rather than TV Material `Card`, preserving its verified one-purple-outline, subtle-scale, focused-elevation contract and transparent/no-shadow unfocused state. Do not rewrite this focus restoration architecture merely because TV Material is present.

---

## 13. Lazy layout performance rules

For TV shelves/grids:

- use stable keys
- use stable semantic focus keys
- specify `contentType` where item structures vary
- do not recreate FocusRequesters on ordinary data emissions
- avoid deriving expensive data inside each item composition
- keep poster composables small
- avoid global animation state causing every visible card to recompose
- load only enough shelves ahead to keep navigation smooth
- for paginated Discover grids, trigger from bounded user focus near the loaded end and de-duplicate with Core request identity; item-count growth alone must not trigger another page or focus restoration

Do not assess LazyRow/LazyColumn performance from a debug build.

Release + R8 is the relevant performance baseline.

---

## 14. Catalog loading strategy

The current client already supports loading ranges as shelves become visible.

The TV version should evolve toward perceived-speed prioritization.

### Desired startup priority

1. restore/authenticate session
2. obtain enough Home structure to render
3. Continue Watching when available
4. first visible shelf(s)
5. make Home interactive
6. prefetch near-future shelves
7. lazy-load deeper shelves as navigation approaches them

Do not block first interaction on loading the entire board.

### Important

Any preload tuning must be measured against real Core behavior.

Do not increase parallel network work simply because more data can be requested.

---

## 15. Image pipeline strategy

The app currently uses a singleton Coil 3 loader with:

- memory cache up to 25% of process memory
- 250 MB disk cache

Do not immediately change these numbers.

Measure them on representative TV hardware.

### TV image rules

- decode posters near the actual rendered size
- request appropriately sized hero backgrounds
- avoid decoding original-resolution art when a screen-sized variant is sufficient
- use memory/disk caching
- do not crossfade every poster by default
- avoid expensive runtime blur
- prefetch only a small navigation window
- do not preload the entire catalog

The goal is low decode/GPU pressure, not maximal caching.

---

## 16. Streaming-server startup strategy

Today `MainViewModel.init` starts the native streaming server immediately.

That can improve time-to-play but may add work to app cold start.

Do not change this based on intuition.

Benchmark two explicit variants later:

### Variant A — eager
- start streaming server during app initialization

### Variant B — deferred prewarm
- render/enable Home first
- begin server prewarm immediately after the critical startup path
- ensure playback still has a safe server-start path if prewarm has not completed

Measure:
- cold startup
- time-to-first-interactive Home
- time from Play to actual playback
- CPU/memory during startup

Choose based on measured user-visible tradeoff.

---

## 17. Performance measurement plan

Performance work must be evidence-driven.

### Build configuration for measurement

Measure:
- release-like build
- R8 enabled
- representative ABI/device

Do not use debug scrolling performance as a product metric.

### Macrobenchmark journeys

Add a benchmark module when the main TV journeys exist.

Critical journeys:

1. cold start -> interactive Home
2. restored session -> Home
3. Home horizontal navigation
4. multi-shelf vertical navigation
5. Home -> Details
6. Details -> Home return focus
7. Search open
8. search result navigation
9. Details/episode -> streams
10. player open
11. player -> Back

### Baseline Profile

Generate an app-specific Baseline Profile from high-frequency TV journeys.

At minimum include:
- startup
- Home
- shelf navigation
- Details
- Search
- player entry once implemented

Regenerate it as major route architecture changes.

### Metrics

Collect at minimum:
- startup timing
- frame timing/jank
- dropped/slow frames
- memory
- CPU where useful
- image/cache behavior
- playback startup timing

Emulator results are useful for correctness and relative comparison, but performance conclusions should be validated on real TV hardware.

---

## 18. Core bridge modernization track

Create a dedicated future infrastructure task rather than bundling this into screen work.

### Phase A — compatibility audit

Compare:
- pinned Core 0.59.0
- current Core release
- Kotlin wrapper models
- protobuf fields/actions
- app's StremioCore wrapper assumptions

Document breaking/added models.

### Phase B — bridge completeness

Prioritize exposing capabilities valuable to TV:

- richer previews without loss
- LocalSearch
- search history if feasible through updated bridge
- current library models
- current player preference/state models
- relevant newer TV/live models only if product scope needs them

### Phase C — upgrade

Upgrade in isolation.

Regression-test:
- QR/token auth
- board
- Continue Watching
- Discover
- Library
- addons
- metadata
- streams
- direct playback
- torrent/local-server playback
- playback progress
- next episode

No screen redesign should be mixed into that upgrade commit.

---

## 19. Smart behavior principles

"Smart" should mean fewer unnecessary actions, not opaque automation.

Prefer:

- remembering where the user was
- preserving row position
- fast resume
- preserving chosen stream/source compatibility
- using preview metadata before network requests
- dynamic filters based on real addon capabilities
- local/autocomplete search before expensive search
- preloading the next likely UI data, not everything

Avoid:

- hidden content ranking with no source
- invented personalization
- automatically choosing unrelated streams
- background network work without user benefit
- surprise navigation
- focus movement caused by data updates

---

## 20. Near-term implementation sequence

This is the preferred sequence from the production Home baseline; Phase 4A Search and Phase 4B Discover are now implemented.

### Gate 1 — runtime verification

When Android Studio/emulator is available:
- build with JDK 21
- test Phase 2 focus/navigation
- test Phase 3A visual scale
- verify QR flow
- verify no catalog emission steals focus

### Gate 2 — data preservation

Status: implemented on `feat/android-tv` after baseline `f8ae2245080277fa23235b18d0bcb4ccbd7deda2`.

Before building the real Home hero:
- audit MetaItemPreview mapping
- preserve high-value preview fields in Android models
- add mapper tests
- avoid changing focus behavior

### Gate 3 — TV state slicing

Status: implemented for the current TV root. `TvApp` collects read-only account, board-shelf, Continue Watching, and selected-details flows; the production Home presentation consumes the two content flows without collecting the monolithic `MainUiState`.

Expose minimal Home/auth/details TV state so TvApp does not depend on the full MainUiState for every screen.

Keep the existing MainViewModel unless a concrete reason appears to split it.

### Gate 4 — TV Material compatibility audit/migration

Before the number of TV controls grows substantially:
- assess `androidx.tv:tv-material`
- migrate TV-only theme/control primitives where beneficial
- preserve custom focus restoration logic

### Gate 5 — Phase 3B production Home

Phase 3B-A production content shell is implemented and runtime-reviewed on the Google TV ARM64 emulator. The hero, real Continue Watching, and real addon shelves share one scrolling feed; board visibility uses the bounded existing prefetch policy. The semantic focus architecture remains intact. Phase 4A Search and Phase 4B Discover have since added real top-level destinations.

Remaining Phase 3B watch item:
- continue verifying successful asynchronous catalog emissions while focus is active

### Gate 6 — Phase 4A TV Search

Status: TV Search and the first working Home/Search navigation shell are implemented. The screen uses Core addon SEARCH shelves through the existing ViewModel path, with an immediate TV keyboard, a two-character non-space threshold, and the existing 300 ms cancellable debounce. Search and Home focus/row-scroll memories are route-local; Details returns to its explicit origin. LocalSearch remains deferred to **LocalSearch bridge completion** because state serialization and query-action bridge support are missing. Search history remains a separate bridge capability.

### Gate 6B — Phase 4B production TV Discover

Status: implemented and runtime-verified on the Google TV ARM64 emulator. The Home/Discover/Search nav order is live. Discover consumes Core `CatalogWithFilters` through a narrow TV state mapping, uses Core requests for generic type/catalog/extra filters, has its own five-column semantic focus and saveable scroll memory, and uses the app-side Core pagination wrapper with bounded end-of-grid triggering and request-identity de-duplication. Runtime exposed Movie, Series, Channel, add-on-defined catalogs, and Genre; Channel pagination appended while the focused semantic item stayed stable. Library remains unimplemented.

### Gate 7A — Phase 5A production TV Details

Status: implemented and runtime-reviewed on `feat/android-tv` from starting remote tip `70c19166780cf88271fb41598991bb6eb75296b9`. `TvApp` observes immutable `TvDetailsUiState`; Compose does not inspect Core protobufs. Opening Details displays the existing preview immediately, then merges matching full `MetaItem` data. A missing/failed optional metadata response leaves preview content, Library action, and Back usable. The full response must match the requested `type:id`; missing full fields fall back to preview values. Continue Watching progress/video identity, rating, inCinema, remaining episodes, and its semantic marker survive enrichment. Core-backed membership/watched fields remain authoritative.

Details art presentation is a pure classification from actual metadata: background → rich backdrop; landscape poster only when `posterShape` says Landscape → wide poster art; other poster → contained poster; no image → neutral text-first page. The TV page uses a left readability gradient without blur/recolor, a logo/title hierarchy, factual metadata, a clamped synopsis, and informational genre/director/cast text. The one content action is exact-identity Library Add/Remove through the existing repository/Core action. Live Library membership projects into its label, and action progress preserves the focused button and route. Initial focus is Library action, with Back as the fallback. Play, episodes, trailers, and stream selection are deliberately absent at this gate.

On the 960×540 dp Google TV emulator, clean relaunch resumed `TvActivity` without a recurring app-process startup ANR. Home, Discover, Library, and Search each opened real Details and returned to the exact originating semantic item on Back (Home Silo; Discover Unabomber; Library Silo; Search Midnight Driver). Details was left idle for 30 seconds without spontaneous movement. Real Core metadata rendered release/runtime/type, synopsis, and available genres/director/cast. The Add/Remove action updated and returned to its original state with focus retained. The final filtered logcat sample had no app-process ANR, FocusRelatedWarning/FocusRequester, AndroidRuntime/FATAL, or Compose exception. The checked APK was installed with `adb install -r`, preserving app data. No natural metadata failure occurred; pure fallback tests cover that state.

The pinned generated Core model carries `MetaItem.videos`; each `Video` includes identity/title/release/overview/thumbnail and can include streams plus `seriesInfo` season/episode, upcoming, watched, current-video, and progress. Phase 5B captured the real Silo values from the ready Details response: 35 videos; seasons 0–4; no missing `seriesInfo`; thumbnails 35, overviews 34, releases 35; upcoming 1, watched 21, current 1; one progress value `0.08573740073854498`; embedded streams 0. Season 0 is a real Specials season.

Ten new pure tests bring the suite to 85 passing tests, including partial-link retention and stale-preview Library membership override. Compile, assemble, and unit-test Gradle tasks passed without a reproduced stall. Screenshots are outside Git at `/private/tmp/stremio-android-tv-phase5a/`. Portrait-only/no-background and TextOnly runtime examples were not available; their deterministic policy is covered by tests. No natural metadata error occurred and the error UI has no dedicated pure presentation test.

### Gate 7B — Phase 5B episode browsing

Status: implemented, pure-tested, built, installed, and runtime-reviewed. The existing `MetaDetails(type, id, videoId = null)` response maps its videos once into app-domain `EpisodeOption` records; `MetaDetails.episodes` preserves identity/title/artwork/overview/release/formatted date/watched/current/upcoming/raw percentage and nullable `EpisodeSeriesInfo`. Keeping the explicit nullable series-info model avoids conflating missing metadata with season 0 while leaving existing mobile `season`/`episode` integer fields and Streams sheet behavior compatible.

The narrow `TvDetailsUiState` exposes a `TvEpisodeBrowserUiState`; TV Compose does not inspect protobufs and does not own a second metadata flow. The projection keeps ungrouped episodes/count, groups only actual `seriesInfo`, sorts numeric season and episode values with original Core order as the tie-breaker, and labels zero `Specials`. Its default-season policy is exact current, exact Continue Watching video ID, first non-zero progress, first positive season, Specials when it is the only season, first available, then null. Core `Video.progress` remains percentage points (0–100), distinct from Library's 0–1 ratio, and a tested conversion clamps/divides by 100 for TV progress bars.

Series Details presents a compact hero and actions above local season controls and focusable episode rows. D-pad focus and season changes are presentation-only. The current/continue/watched/upcoming states remain separate. In-route focus/season/scroll memory is keyed by `videoId`, with deterministic remembered-index fallback if an episode disappears. Initial focus remains in the action area while the current/continue row scrolls into view. The loaded logo remains rendered after Coil success and the text title remains available.

Silo runtime audit results: `tt14688458`, 35 videos, `[0, 1, 2, 3, 4]`, missing-seriesInfo 0, thumbnails 35, overviews 34, releases 35, upcoming 1, watched 21, current 1, progress `[0.08573740073854498]`, embedded episode streams 0. Runtime verified S1/S2 local switching and returning to S1E3, ten paced focus moves ending at S2E9, the S4E1 Upcoming row, a 30-second S1E3 idle interval, the movie Details regression and Library toggle restoration, and Home-origin Back return. No natural data emission occurred during episode navigation; stable semantic-ID refresh/removal behavior is covered by pure tests. Source review plus filtered logs confirmed browsing makes no episode stream request.

`:app:compileDebugKotlin`, `:app:assembleDebug`, and `:app:testDebugUnitTest` pass (95 tests). The debug APK was installed with `adb install -r`; app data was preserved. Screenshots are outside Git under `/private/tmp/` using the `stremio-phase5b-` prefix.

### Gate 7C — Phase 5C TV source discovery and selection

Status: implemented and runtime-reviewed on `feat/android-tv` from `29733faf578782b48f5f7ca9d9981bd741363ef4`.

Streams is a nested Details surface, keeping the original top-level origin and Details/episode composition alive. The TV target uses exact Core episode `videoId` plus `guessStreamPath=false`, or null `videoId` plus `guessStreamPath=true` for non-episodic metadata. TV state is a narrow immutable mapping over incremental `LoadableStreams`. The ViewModel cancels the previous target and rejects any emission whose `MetaDetails.selected` does not match the current request semantically.

Per-provider Loading/Ready/Error state is retained, errors are isolated, and already-ready options render before all providers complete. A local provider filter never requests streams. Stable stream identities use request/source evidence and stream metadata rather than the flattened mobile index. Existing options update in place, disappearances are removed, and late arrivals append in Core order, preserving a focused semantic source where possible. Core behavior hints and source kinds survive mapping. Center selects only; it does not resolve or play.

Silo S2E7 returned 71 provider results from 3 requests (Local Files error/0, NoTorrent ready/19, Torrentio ready/52), with Torrentio arriving first. Five duplicate semantic keys were collapsed, leaving 66 displayed options. Displayed source-kind counts were Direct 12, Torrent 52, External 2, YouTube 0, Archive 0, Other 0. Hint/metadata coverage: bingeGroup 52, filename 52, videoHash 0, videoSize 0, notWebReady 0, quality 54, seeds 52, size 52. Runtime verified provider filtering, selection separate from focus, deep list movement, idle stability, S2E7 focus restoration on Back, and same-target re-entry. A different-episode late-result race was not naturally observed. Non-episodic action is implemented and covered by pure tests, but no movie stream audit was captured due to a safety-review rejection of an emulator action that could have toggled Library membership. The stale episode error-state check used S0E4 and correctly retained its target on return. The S4E1 Upcoming row remained inert.

Source review and runtime logs found no resolve/play/player/history transition from selection. There were no observed app fatals, Compose exceptions, focus warnings, or app-process ANRs in the reviewed log interval. Raw URLs and raw filenames are excluded from audit logs and committed documentation.

### Gate 7D — Phase 6A ordinary TV playback

Status: implemented on `feat/android-tv`, starting at `41bbb1a4db205f1854f7879b61d54d4ee71980bb`.

Explicit activation of a selected TV `StreamOption` routes into the nested TV Player, preserving the Streams/Details origin. The ViewModel owns a narrow attempt state and calls the existing `PlaybackRepository.resolveAndLoadStream` path directly rather than the mobile `playStream` orchestration. Repository callbacks stamp resolution start, playable source resolved, player load start, and load return with monotonic nanoseconds. Player callbacks stamp ExoPlayer's rendered first frame or MPV's first playback restart following file load. An attempt ID gates asynchronous callbacks; retry creates a fresh ID, while requested and actual engine are tracked across the existing MPV-unavailable-to-ExoPlayer fallback.

History stream selection is persisted only after the first visual signal, and progress events are gated on first visual progress. Back finalizes eligible progress, releases this TV player, and returns to Streams. Basic play/pause and bounded seek controls are available. End is reported without automatic next-episode behavior. No ranking/Smart Play, fallback policy, Skip Segments, continuity, next-episode preparation, or preload was added.

Compile, assemble, and unit tests pass (124 tests, 0 failures); `git diff --check` passes. The universal debug APK installed with data preserved on the ARM64 Google TV API 36 emulator and the TV activity was foregrounded. The signed-in TV Home screen was visually confirmed. Silo S2E7 playback and actual ExoPlayer/MPV first-visual runs were not completed; TTFF values and engine runtime validation remain outstanding. Safe trace formatting is unit-tested to omit URLs. Complete the manual source runtime audit before advancing to Gate 8 performance instrumentation or the staged Playback Experience work.

### Gate 8 — performance instrumentation

As soon as core user journeys are stable:
- macrobenchmark
- Baseline Profile
- startup/server experiment
- image/cache tuning

---

## 21. Do-not-do list

Do not:

- create a separate backend for ordinary Stremio features
- fork user library state into a second database
- invent recommendations not supplied by Stremio/addons
- request full MetaDetails merely to update Home focus visuals
- perform full addon search for every D-pad keyboard keystroke long-term
- use index-only focus restoration
- start every catalog eagerly
- add expensive blur/glass effects without performance evidence
- optimize based only on emulator/debug impressions
- update Core during an unrelated visual task
- let upstream Core capabilities bypass the Kotlin bridge without explicit implementation and tests

---

## 22. Research references

Technical decisions in this document were grounded in:

- the current `robbis95/stremio-android` source
- the included `perpetus/stremio-core-kotlin` bridge
- official `Stremio/stremio-core` source
- official `Stremio/stremio-web` behavior
- Stremio addon protocol models
- current Android Compose for TV guidance
- current Android Compose performance guidance
- current Android Baseline Profile guidance

This document intentionally distinguishes what the current Android bridge can do from what the upstream Core can do.


## Playback Experience roadmap

Future playback intelligence, Skip Segments, recovery, next-episode preparation, language intent, and seamless episode work are governed by `TV_PLAYBACK_EXPERIENCE_ROADMAP.md` together with `TV_SMART_PLAYBACK_FEASIBILITY.md`.

Key rule: Stremio Core remains authoritative for Stremio resource/addon semantics. Android-side coordination may normalize and prioritize client-owned playback work, but must not duplicate Core orchestration. Optional segment providers and speculative work must degrade to ordinary playback without blocking it.
