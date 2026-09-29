# Stremio Android TV Architecture & Performance Strategy

Status: active technical strategy  
Research date: 2026-09-29  
Canonical branch: `feat/android-tv`  
Current TV implementation baseline: `f8ae2245080277fa23235b18d0bcb4ccbd7deda2`

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

Official Core 0.59 contains a LocalSearch model and search action designed for local autocomplete.

The Android Field enum includes `LocalSearch`, but the current Kotlin/protobuf bridge does not expose a usable LocalSearch model payload to the app.

Do not implement a duplicate Android-only Cinemeta index merely to bypass this.

Preferred future work:
1. extend/update the Kotlin/Core bridge
2. expose LocalSearch results
3. use local suggestions while typing
4. run full addon catalog search only on committed search

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

### Bridge rule

Do not create a second private search engine just because LocalSearch is not currently surfaced by the Kotlin bridge.

Add the bridge support in a dedicated infrastructure task.

---

## 9. Dynamic information architecture

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

---

## 10. TV state architecture

The current `TvApp` collects the entire `MainUiState`.

`MainUiState` combines unrelated state including server settings, analytics preferences, player options, addon state, search state and Home state.

As TV functionality grows, this can cause broad recomposition and creates unnecessary coupling.

### Target

Introduce small TV-facing state slices without duplicating Core truth.

Candidate states:

- `TvAuthUiState`
- `TvHomeUiState`
- `TvDetailsUiState`
- `TvSearchUiState`
- `TvPlaybackUiState`

These may be exposed from the existing MainViewModel initially.

Do not split the ViewModel merely for aesthetic architecture.

Split ownership only when responsibility or performance evidence supports it.

### Rule

A Home-only change should not require TvHomeScreen to observe unrelated subtitle/server/settings state.

Use stable immutable state and selective collection.

---

## 11. Compose for TV strategy

Current TV presentation uses regular `androidx.compose.material3`.

Current Android guidance recommends TV-optimized Material components via:

`androidx.tv:tv-material`

The TV library provides remote/focus-oriented components.

### Migration rule

Evaluate migration before the TV component surface becomes large.

Because mobile and TV presentation are already separate, prefer:

- mobile: existing Compose Material 3
- TV: TV Material where an appropriate TV component exists

Do not blindly mix mobile `MaterialTheme` and TV `MaterialTheme` in the same TV subtree.

### Migration sequence

1. build a small compatibility audit
2. identify current TV Material replacements for buttons/cards/surfaces
3. migrate shared TV theme primitives first
4. migrate TV controls incrementally
5. verify D-pad/focus runtime after each behavioral component migration

Do not rewrite already-correct custom lazy focus restoration just because TV Material is introduced.

---

## 12. Lazy layout performance rules

For TV shelves/grids:

- use stable keys
- use stable semantic focus keys
- specify `contentType` where item structures vary
- do not recreate FocusRequesters on ordinary data emissions
- avoid deriving expensive data inside each item composition
- keep poster composables small
- avoid global animation state causing every visible card to recompose
- load only enough shelves ahead to keep navigation smooth

Do not assess LazyRow/LazyColumn performance from a debug build.

Release + R8 is the relevant performance baseline.

---

## 13. Catalog loading strategy

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

## 14. Image pipeline strategy

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

## 15. Streaming-server startup strategy

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

## 16. Performance measurement plan

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

## 17. Core bridge modernization track

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

## 18. Smart behavior principles

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

## 19. Near-term implementation sequence

This is the preferred sequence from the current Phase 3A baseline.

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

Status: implemented for the current TV root. `TvApp` collects read-only account, board-shelf, and selected-details flows; Continue Watching is exposed independently for a later Home phase.

Expose minimal Home/auth/details TV state so TvApp does not depend on the full MainUiState for every screen.

Keep the existing MainViewModel unless a concrete reason appears to split it.

### Gate 4 — TV Material compatibility audit/migration

Before the number of TV controls grows substantially:
- assess `androidx.tv:tv-material`
- migrate TV-only theme/control primitives where beneficial
- preserve custom focus restoration logic

### Gate 5 — Phase 3B production Home

Then build:
- top-level TV shell
- real Continue Watching
- preview-driven hero
- actual board shelves
- dynamic navigation structure
- production Home visual hierarchy

### Gate 6 — Search bridge and Search UI

Before final Search:
- expose LocalSearch/search-history capabilities where practical
- avoid full network search per keystroke

### Gate 7 — details/episodes/stream intelligence

Preserve:
- episode progress
- current/watched/upcoming
- stream behavior hints including binge-group information
- real metadata

### Gate 8 — performance instrumentation

As soon as core user journeys are stable:
- macrobenchmark
- Baseline Profile
- startup/server experiment
- image/cache tuning

---

## 20. Do-not-do list

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

## 21. Research references

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
