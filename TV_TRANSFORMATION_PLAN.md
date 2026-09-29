# Stremio Android TV Transformation Plan

Status: active planning document  
Project: `robbis95/stremio-android`  
Target: Android TV / Google TV  
Canonical TV integration branch: `feat/android-tv`

## Branch strategy

`feat/android-tv` is now the single canonical branch for the TV transformation.

All completed TV work must ultimately land here before the next phase starts.

Historical phase branches such as:
- `feat/android-tv-milestone-1`
- `feat/android-tv-phase-2-navigation`

are implementation history only. They are not the branch future phases should build from.

Future Codex tasks should:
1. fetch latest
2. checkout `feat/android-tv`
3. confirm it matches `origin/feat/android-tv`
4. confirm the working tree is clean
5. implement the requested phase directly on `feat/android-tv`
6. commit and push back to `feat/android-tv`

Do not create another phase branch unless explicitly instructed.

## Purpose

Transform the existing Android application into a first-class TV experience while preserving and reusing the existing Stremio Core, account, catalog, library, addon, stream-resolution, streaming-server, and playback infrastructure wherever appropriate.

The TV application must feel intentionally designed for a 10-foot interface and D-pad/remote interaction. It must not be a stretched mobile UI.

## Non-negotiable branding rule

The product is **Stremio**.

The visual concept references supplied during planning used the fictional name "AURA". That name, its slogan, fictional profiles, fictional titles, and fictional product identity are design-reference material only.

They MUST NOT be introduced into the codebase or user-facing UI.

Use:
- Stremio name
- existing Stremio identity/assets where suitable
- real Stremio data
- real Stremio account/session behavior
- real Stremio catalog/library/addon semantics

Do not invent a new streaming brand.

If a concept conflicts with actual Stremio capabilities or data, preserve the visual/design principle but adapt behavior to Stremio rather than fabricating functionality.

## Working model

The user is not expected to manually implement this transformation.

Development proceeds as a sequence of tightly scoped Codex tasks.

For every task:
1. inspect the latest pushed `feat/android-tv`
2. never work from a stale local checkout
3. keep TV presentation isolated from mobile presentation unless shared logic belongs below UI
4. reuse existing Stremio Core/repositories/actions
5. build and run unit tests when the environment allows
6. do not claim runtime behavior is verified without Android TV / Google TV testing
7. report files changed, architecture decisions, build results, APK paths, and remaining runtime checks
8. push completed work to `feat/android-tv`
9. do not start the next phase without an explicit task

## Design north star

The supplied TV concepts define the desired overall feel:
- premium, restrained, modern TV UI
- generous spacing
- strong visual hierarchy
- content-led imagery
- simple top-level navigation
- large hero/detail imagery where useful
- horizontal content shelves
- strong and unmistakable D-pad focus
- QR-first account linking
- TV-native search
- series pages with season navigation and readable episode rows
- minimal clutter
- layouts designed for normal TV viewing distance

The concept's light visual language is directional, not absolute. Legibility, Stremio identity, focus visibility, content artwork and real TV behavior take priority.

### Focus visibility

Production focus must be obvious from viewing distance through a suitable combination of:
- border/glow
- elevation/shadow
- scale/emphasis where appropriate
- contrast

Never trade deterministic navigation for subtle visuals.

## Concept-to-Stremio translation

### Splash / startup
- Stremio branding only
- no AURA name or slogan
- no fake progress
- transition as soon as real restore/startup state allows

### Account linking
- QR login is primary
- use official Stremio account-link flow
- display Stremio-generated link/code
- poll authorization
- pass returned authKey through the existing token-login pipeline
- optional email/password fallback
- no fictional profile selector unless Stremio exposes a real profile model

### Home
Concept:
- top navigation
- hero/content feature area
- Continue Watching
- multiple shelves

Stremio implementation:
- real board/catalog shelves
- real Continue Watching
- real library state
- real discover/catalog metadata
- no invented recommendation source

### Search
- use existing Core search
- filters/categories must map to real available Stremio data
- support remote, physical keyboard and compatible phone-remote text entry where possible
- deterministic focus between field, keyboard, filters and results

### Movie / series details
- real metadata from Core/addons
- real library state
- real episode data
- real resume/progress where available
- stream selection remains a real functional step unless existing safe behavior supports direct resume

### Player
Later phase:
- TV-native controls
- D-pad seek
- Back contract
- audio/subtitle access
- episode navigation
- reuse current playback engines

## Engineering references

Implementation details, capability boundaries, and performance rules are maintained in:

- `TV_ARCHITECTURE_STRATEGY.md`
- `TV_CORE_CAPABILITY_AUDIT.md`
- `TV_SMART_PLAYBACK_FEASIBILITY.md`
- `TV_PLAYBACK_EXPERIENCE_ROADMAP.md`

Codex tasks should read these before making architecture decisions that touch Stremio Core usage, metadata mapping, search, playback, state ownership, TV Material, loading strategy, or performance.

## Architecture principles

### Reuse
Prefer reuse of:
- Stremio Core
- repositories
- MainViewModel actions/state where practical
- auth/session pipeline
- board/catalog/search/library/addon flows
- stream resolution
- local streaming server
- ExoPlayer/MPV

### TV-specific presentation
Dedicated TV presentation is expected for:
- navigation/focus
- Home
- search
- details/episodes
- stream selection
- player controls
- Discover/Library
- addons/settings as needed

### Stable identity
Use semantic identity whenever possible:
- content: `type:id`
- shelf/catalog: stable catalog/shelf identity
- episode: stable video/episode identity
- route: stable route identity

Do not persist focus by visual index alone.

### Async data
Incremental Core/addon/catalog emissions must not:
- steal focus
- reposition lists unexpectedly
- reset active selection
- rerun initial focus logic

Focus restoration should be event-driven.

## Roadmap

### Phase 1 — Runnable TV proof
Status: implemented; runtime stress verification still pending.

Implemented:
- TV launcher/activity
- manifest/banner
- QR account linking
- first Home shelf
- Details
- Back
- stable content identity
- deterministic Home → Details → Back restoration

Runtime checks still required:
- repeated Details/Back
- catalog updates while navigating
- launcher/session restore

### Phase 2 — Navigation/focus foundation
Status: accepted for progression. Implemented, compiled, assembled, unit-tested, installed, and runtime-verified on the Google TV ARM64 emulator. The historical unreproduced focus warning and a future live-catalog-emission regression check remain watch items, not blockers for Phase 3.

Implemented:
- reusable TV poster card
- reusable shelf row
- multi-shelf Home
- Left/Right navigation
- Up/Down shelf traversal
- per-shelf remembered content
- nested lazy-list restoration
- deterministic Details return to shelf + item
- pure Kotlin focus/fallback tests

Verified:
- Java 21 build works
- `compileDebugKotlin` passes
- `assembleDebug` passes
- `testDebugUnitTest` passes (21 tests, 0 failures)
- fresh universal APK installs successfully on Google TV ARM64 emulator
- `TvActivity` launches and remains resumed
- basic D-pad smoke input does not crash
- the Details return-focus regression reproduced from the emulator recording is fixed: 10/10 Home → Details → Back cycles returned focus to the exact same semantic item on the first Home frame, including items farther right in the shelf
- Details paints an opaque TV background; title/body contrast and visible Back focus were confirmed on emulator
- no AndroidRuntime/FATAL or ANR was observed during the regression run; one earlier runtime session reported 16 non-fatal `FocusRelatedWarning` messages saying a `FocusRequester` was not initialized

Runtime-polish gate (2026-09-29):
- emulator display: 1920×1080 physical pixels at 320 dpi (2.0 density); logical viewport approximately 960×540 dp
- the original 184×306 dp poster cards, 18 dp row padding, 30 dp shelf gap, and vertically centered Home `Column` left one shelf dominant in the viewport
- Home now uses a top-aligned header and a `LazyColumn` that fills the remaining height; reusable TV tokens size posters at 144×242 dp with a 178 dp image, 10 dp row padding, and 16 dp shelf spacing
- initial Home shows one complete shelf and about 30% of the next shelf; posters and titles remain readable at the tested TV viewport
- direct user feedback identified why ordinary Up/Down felt jumpy: traversal could reposition the vertical list and then horizontally `scrollToItem` the target row to a remembered card. The remembered card could be far from the source card's on-screen X
- ordinary Up/Down is now spatial X-preserving traversal. It measures the focused source item center from its row's `visibleItemsInfo`, keeps the target row's current horizontal position, and focuses the currently visible target card with the closest center X. Exact distance ties resolve by row item index. If the target shelf is offscreen, the vertical list advances by approximately one shelf step; an already visible target shelf is not repositioned first
- shelf-local `rememberLazyListState()` did not provide a reliable position across lazy item disposal. Home now retains first-visible index and pixel offset in an in-memory map keyed by stable semantic shelf key, and initializes recomposed rows from that map. It is separate from focused-item memory
- emulator D-pad verification from shelf 1 several cards to the right landed on the visually nearest card in shelf 2; Down again and Up twice returned through the same X-aligned path. Further checks scrolled two shelves horizontally, traversed Down/Up, and confirmed each row retained its own horizontal position without rewinding
- the poster focus rim came from the 2 dp unfocused shadow and divider-colored outer focus border. Unfocused cards now use 0 dp shadow and a transparent outer border; the image's subtle 1 dp divider frame remains. Across more than 10 poster focus changes, only the active poster showed the purple outer outline, scale, and elevation; no previous-card dark rim remained
- ten Home → Details → Back cycles were exercised across multiple shelves and horizontally scrolled positions; all returned focus to the exact semantic card that opened Details. The explicit route restore still resolves the semantic item and scrolls its row to that exact item. Pixel captures differed because the exact restore repositions the vertical shelf and focus/shadow frames settle, while the semantic item remained exact
- a 30-second idle interval produced the same visible focus and scroll position before/after. Logcat was cleared before runtime checks; no `FocusRelatedWarning`, `FocusRequester`, `FATAL EXCEPTION`, `AndroidRuntime`, Compose exception, or ANR matches were captured
- the historical 16-message `FocusRelatedWarning` remains unreproduced; its old log was unavailable, so its exact stack/path and root cause remain unknown. No warning suppression or speculative requester change was made

Non-blocking watch items:
- if the historical `FocusRelatedWarning` reappears, capture its complete stack context and identify its source
- validate during normal future development that a newly arriving real catalog/addon emission does not steal focus or reposition the active shelf
- continue visual review as the production Home shell replaces the Phase 3A proof layout

### Phase 3A — TV visual foundation
Status: implemented and runtime-reviewed on Google TV emulator. Its purpose as a visual/focus foundation is complete; remaining visual refinement belongs to the production Home and later route work.

Implemented:
- TV-specific color, typography, spacing, and focus design system
- branded TV startup state using the existing Stremio splash mark
- QR-first account-link presentation with secondary email/password sign-in
- visual refinement of the existing real-data multi-shelf Home and poster cards

### TV Material foundation gate — accepted for progression

Adopted `androidx.tv:tv-material:1.1.0` in the shared app module. The TV theme and routes now use TV Material types and remote controls where suitable; mobile Material3 remains available for the mobile subtree. The optional TV email/password fallback retains explicitly TV-styled mobile `OutlinedTextField`, and small loading indicators retain mobile Material3 `CircularProgressIndicator`.

Build on the migration source passed `:app:compileDebugKotlin`, `:app:assembleDebug`, and `:app:testDebugUnitTest` (27 tests, 0 failures). The ARM64 debug APK installed over the existing Google TV emulator app with app data preserved. Home rendered with Stremio branding; D-pad Left/Right movement, spatial Down/Up traversal, row-position retention, and the single purple focused-poster treatment were observed. The unfocused posters showed no dark outer rim. Post-install logcat had no `FocusRelatedWarning`, `FocusRequester`, Compose exception, `FATAL EXCEPTION`, or ANR match.

The emulator's ADB Center/Enter injection did not open the proof Details route, so the 10-cycle Details → Back test was not rerun on this build. Static review of the migration confirms that `TvPosterCard` activation, semantic Home focus/restore state, and route-return logic were not changed; the migration changed TV Material theme/control primitives and the Details Back control only. Because poster D-pad traversal, row retention, focus visuals, build/tests, install, and logcat all remained clean—and exact Details restoration had already been verified 10/10 immediately before this isolated migration—the failed ADB activation attempt is treated as an automation limitation/watch item rather than evidence of a regression. Login/startup states remain deferred when they can be exercised without destroying the preserved session.

### Phase 3B-A — Production Home content shell
Status: implemented, unit-tested, built, installed, and exercised on the Google TV ARM64 emulator. This milestone covers the real-data Home content shell. The Phase 3B top-level navigation shell remains deferred until its destinations are implemented and wired.

Implemented:
- a single scrolling Home feed with Stremio identity in a compact, non-focusable hero, Continue Watching when non-empty, then the original addon board shelves in their existing order and with their real titles
- a real preview-driven hero using only current `CatalogItem` presentation data; it shows the real logo or title, available metadata/description, and real background artwork when present, with a contained-poster fallback rather than stretching portrait artwork
- a 150 ms visual-only hero dwell; D-pad focus and semantic focus memory remain immediate, and focus changes do not call `openDetails`, `getMetaDetailsFlow`, or another metadata endpoint
- a dedicated landscape Continue Watching card with real progress and deliberate Details activation; background/landscape art fills the card, while portrait/square art remains contained on a neutral surface
- pure local Continue Watching enrichment by matching `type + id` against loaded board items. It fills missing presentation fields while preserving progress, watched state, remaining episodes, continue-watching video ID, and the Continue Watching marker. A no-match item remains valid without fabricated fields
- an immutable TV Home presentation structure that separates the non-focusable hero, optional stable `tv:continue-watching` shelf, and board shelves. Semantic shelf keys map explicitly to LazyColumn indices; original board indices remain distinct from presentation and lazy indices
- bounded board visibility prefetch through `onShelfVisible(originalBoardShelfIndex)`, de-duplicated as shelves become visible/near-visible. Hero and Continue Watching do not shift the index sent to `MainViewModel`
- restrained non-focusable loading/error presentation. One catalog error remained isolated to its shelf while ready shelves stayed navigable
- initial focus waits for real focusable content or completed loading, so empty/loading board emissions do not create a fake focus target

Runtime results:
- the 960×540 dp Google TV ARM64 emulator displayed real Continue Watching first when populated, followed by real board content; the hero was tuned to a 128 dp section after observing the actual viewport
- rapid horizontal navigation across eight cards remained responsive; the preview settled on the final focused real item. Static call-path review and filtered runtime logs showed no focus-triggered MetaDetails request
- Continue Watching progress rendered from the real item state; board row position remained retained across vertical traversal, including a far-right item
- Continue Watching Details activation and Back restored the exact focused CW card. Board Details activation and Back also restored the exact focused item, including a far-right board item across repeated cycles
- visibility callbacks loaded only the current and nearby board catalogs at original indices 3, 4, and 5 during traversal. A late shelf error arrived in the test window and was isolated. A successful late catalog population while focus was active was not confirmed, so the asynchronous successful-emission check remains open
- a 30-second idle interval on Home retained the visible focus and scroll position. Filtered logcat contained no `FocusRelatedWarning`, `FocusRequester`, `FATAL EXCEPTION`, `AndroidRuntime`, Compose exception, or ANR match
- the hero naturally scrolls with the feed. It can remain partly visible while the first board row is focused and leaves the viewport farther down the feed; it never receives focus

### Phase 3B-B — Production Home visual polish

Status: implemented, unit-tested, built, installed, and visually verified on the 1920×1080 Google TV ARM64 emulator (960×540 dp). The Home content shell is visually ready for later top-level navigation integration. This does not complete all of Phase 3B.

Implemented:
- the stable Home hero is 142 dp high at the tested viewport
- rich-artwork mode uses a real background or a poster explicitly classified as Landscape, fills the hero with that source, preserves its original colors, and protects copy with the existing horizontal light-theme gradient. It shows the real logo when available, otherwise the title, then available metadata and at most two description lines
- contained-artwork mode uses a neutral artwork panel and a true 2:3 contained poster. Portrait art is not stretched, cropped into a backdrop, blurred, or recolored. Name-only and name-plus-poster previews remain valid
- the Stremio icon remains as a small, low-emphasis mark at the lower right of the hero, away from the content-title hierarchy
- initial hero selection now ranks real preview suitability within these source priorities: best hero-capable enriched Continue Watching item; best hero-capable board item; useful enriched Continue Watching item; first named real board item; null. Background, Landscape poster, logo, and description are the hero-capable signals. Poster-only Continue Watching is a weak fallback. Popularity is not scored and no content is invented
- once a real item receives focus, the existing 150 ms dwell still previews that exact item even when its hero quality is weak
- both Continue Watching modes now share the dark media surface and light type. Portrait fallback artwork is 88×132 dp (2:3) inside the existing 292×164 dp card. Title, real metadata, flexible space, and the real progress track form the text layout; no episode label or video ID is fabricated
- rich CW artwork retains its dark readability gradient. The same media track and accent progress treatment appear in both modes

Runtime results:
- the For All Mankind poster-only hero showed the title, contained artwork panel, and a properly proportioned portrait while the first focused CW fallback showed the same poster at 88×132 dp, title, and progress on the shared dark surface
- the American Horror Story rich hero retained its real backdrop colors; the horizontal gradient kept its real logo, metadata, and two-line description readable. Copy is anchored low enough to remain visible while the Home feed scrolls to focused content
- American Horror Story and Slow Horses rich CW cards retained their landscape art, dark gradient, title/metadata, and visible progress. Focus showed one purple outline
- Right/Left navigation, spatial Down/Up movement, and horizontally scrolled board-row retention were exercised. Moving Down from a CW item chose the visually aligned board poster; after scrolling that board row right and moving Down/Up, the same semantic board item and row position were restored
- Details opened from a CW item and a board item; Back returned to the exact focused semantic item in each case, including a horizontally scrolled board item
- the final ARM64 debug APK installed over the existing emulator app with app data preserved. The screen reported 1920×1080 at 320 dpi. Filtered logcat had no `FocusRelatedWarning`, `FocusRequester`, `AndroidRuntime`, `FATAL EXCEPTION`, Compose exception, or ANR match
- `:app:compileDebugKotlin`, `:app:assembleDebug`, and `:app:testDebugUnitTest` passed. Unit tests total 43 with 0 failures (8 new Home presentation tests)
- screenshots were captured outside the repository at `/private/tmp/stremio-android-tv-phase3bb/`: `01-initial-home.png`, `02-portrait-hero-fallback.png`, `03-rich-backdrop-hero.png`, `04-portrait-cw-focused.png`, `05-rich-cw-focused.png`, `06-details-from-cw.png`, `07-back-to-cw.png`, `18-board-enter-key.png`, and `19-back-to-board-final.png`

Scope confirmation: focus registry, semantic keys, spatial traversal policy, row-position memory, `detailsReturnTarget`, route restoration, board visibility indexing, and hero non-focusability were not redesigned. No navigation, Search, Discover, Library, production Details, playback, or Smart Playback work was started.

At this milestone, top-level Discover, Library, Search, and Settings destinations were intentionally deferred. No dead navigation controls, fake recommendations, or fake metadata were added. Do not mark all of Phase 3B complete on this milestone.

Do not invent recommendation sources.

### Phase 4 — Search
- Search route
- large search field
- TV keyboard
- physical/remote input
- real-data filters
- result shelves/grid
- deterministic Back/focus restoration

### Phase 5 — Details, seasons, episodes, streams
- Phase 5A production Details and Library action are recorded below
- Phase 5B series seasons/episodes and watched/resume state
- Phase 5C stream discovery and stable provider/stream focus

### Phase 6 — Playback
- Phase 6A ordinary manually selected playback is recorded below
- follow-up playback controls, tracks, and transitions remain separate milestones

After the ordinary TV playback path is working and instrumented, continue through the staged Playback Experience roadmap in `TV_PLAYBACK_EXPERIENCE_ROADMAP.md`: conservative Core-backed Skip Segments, provider-neutral segment resolution, Smart Play/Fallback, Episode Continuity, Next Episode transaction, and bounded seamless preloading. Do not implement these as unrelated hacks.

Smart Playback is a separate staged playback track documented in `TV_SMART_PLAYBACK_FEASIBILITY.md`.

Do not implement Smart Play, Smart Fallback, Episode Continuity or Seamless Episodes as one monolithic change. The feasibility audit currently recommends:
- deterministic stream metadata/parser first
- explainable local ranking second
- Smart Play/manual override
- generalized fallback
- consume Core's existing bingeGroup/next-video behavior
- bridge work only where verified necessary
- persistent Exo playlist/preload work after ordinary playback architecture is stable

### Phase 7 — Discover and Library
- Discover production route is recorded in Phase 4B below
- Library production route is recorded in Phase 4C below

### Phase 8 — Addons, account, settings
- addon management
- account actions
- relevant TV settings
- remote-friendly controls
- safe text input where unavoidable

### Phase 9 — Production polish/performance
- focus visuals
- animation where performance allows
- typography/spacing
- image loading
- startup
- low-end TV performance
- memory/GPU behavior
- launcher/banner assets
- accessibility
- safe-area checks
- real-device codec/player testing
- crash/error handling
- regression pass

## Phase 4A — TV Search + first real top-level navigation

Status: implemented on `feat/android-tv`; production verification is bounded to the runtime checks recorded below. At Phase 4A the only top-level destinations exposed were Home and Search. Phase 4B adds the real Discover route; Library, Settings, Addons, and account destinations remain unwired and hidden.

- Search uses the existing addon-aware Core `Field.SEARCH` path through `MainViewModel.search(...)`, `CatalogRepository.search(...)`, and `StremioCore.search(...)`. It does not call addon endpoints from UI code, assume Cinemeta, or create a local index.
- The TV route shell is a small state machine with Login, Home, Search, and Details. Details stores Home or Search as its explicit origin. Home and Search remain composed while switching between them so their independent lazy-row scroll/focus state survives the app session.
- MainViewModel exposes read-only `tvSearchQuery`, `tvSearchResults`, and `tvSearchShelves` flows over its existing search state. TV input updates immediately; only queries with at least two non-space Unicode code points dispatch Core work. The existing cancellable 300 ms debounce remains in place.
- Search renders actual Core result shelves in emitted order with their real titles, loading/error state, and existing TV poster/focus behavior. Search shelf identities are stable and namespaced separately from Home. Search → Details → Back restores the semantic result and row position; Home → Details → Back returns to Home focus memory.
- The keyboard uses QWERTY letter rows plus Space, Backspace, and Clear. D-pad traversal is explicit; the last action row enters the first available result, and Search nav Down restores the Search route's remembered keyboard/result context.
- Verified bridge limitation: the Rust Android model contains `LocalSearch`, the runtime has `Field.LocalSearch`, and `ActionLoad.LocalSearch` exists, but Kotlin state serialization for LocalSearch is unimplemented and the runtime protobuf lacks Core `ActionSearch`. End-to-end autocomplete is deferred to the targeted **LocalSearch bridge completion** task. The submodule was not modified.

Runtime evidence on the existing 960×540 dp Google TV ARM64 emulator (app data preserved): initial Home and Search routes render; Up reaches the compact nav; Center selects Search while retaining nav focus; Down enters Search; on-screen query entry and Clear/Backspace/Space were exercised; real addon-backed result shelves/cards appeared while per-catalog loading continued; Search query/results survived a controlled Home → Search route switch in the same session. Runtime testing found TV poster cards lacked a Compose click action, so Center activation did not reliably enter Details. Added a no-ripple click action matching Continue Watching cards while retaining explicit D-pad behavior. On the rebuilt APK, Search → Details → Back restored the exact focused result and horizontal row position for five consecutive cycles; Home → Details → Back restored the focused Continue Watching item. A 30-second Search idle check showed no spontaneous focus jump, and the filtered error/warning logcat check had no matching FocusRelatedWarning, FocusRequester, AndroidRuntime error, fatal exception, Compose exception, or ANR entries.

The 142 dp Home hero and normal poster sizing were preserved. A late catalog emission/focus stability remains a Phase 3B watch item; no controlled late-emission regression was performed specifically for Phase 4A.

### Remaining candidate top-level destinations

Add Library, Addons, or Settings only alongside working routes and their real state/actions. Movies/Series may be Discover filters rather than permanent destinations. Do not expose placeholders in navigation.

## Phase 4B — Production TV Discover

Status: implemented, unit-tested, built, installed, and exercised on the existing Google TV ARM64 emulator at 960×540 dp. The app remained on `feat/android-tv`; the `stremio-core-kotlin` submodule was not modified.

- Discover uses the existing Core `CatalogWithFilters` state through `MainViewModel` and `CatalogRepository`; TV UI never calls add-on catalog endpoints. `TvApp` collects a narrow immutable `TvDiscoverUiState` flow with only the mapped filter groups, selected/resolved request, `CatalogShelf`, title, and Core next-page request.
- Removed the shared mobile ViewModel's hard-coded `movie` preference. Initial selection is protocol-driven: Core selected request, selected type, selected catalog, first type, first catalog, then null. Every navigation uses a `ResourceRequest` supplied by Core.
- Filters are mapped generically from available types, catalogs, and named extras. Empty groups are omitted; unknown type names are humanized without being filtered out; add-on catalog names are preserved. Options use Core-provided requests. The current runtime account exposed Movie, Series, Channel; add-on catalogs including Popular, New, Featured, Top Picks for You, Because you watched…, and the Channel catalog Audio Book Torrents; and the Genre extra. No fixed movie catalog/add-on is assumed.
- Discover uses the existing `TvPosterCard` contract in a five-column `LazyVerticalGrid`, with 144 dp posters and 72 dp safe horizontal margins. D-pad routing is explicit: adjacent horizontal items, same/nearest column vertically, deterministic row edges, first grid row Up into the last filter (or nav when filters are absent), and filter Down into the grid.
- Discover has its own saveable focus memory: semantic `type:id`, fallback index, filter group/option identity, grid first-visible item/offset, and pagination trigger state. The grid is composed only on the active Discover route, so route changes retain explicit state without keeping an offscreen grid alive. Details stores Discover as an explicit exhaustive origin and returns to the same semantic card when still present.
- Added the app-side `StremioCore.loadDiscoverNextPage()` wrapper using the generated `ActionCatalogWithFilters.LoadNextPage` constructor and `Field.DISCOVER`, then delegated through repository and ViewModel. The route triggers only when the user-focused item enters the last loaded row; the trigger must advance to a later focused index and Core's next-page request identity is de-duplicated. Page results append without replacing or reordering existing items.
- The top navigation is now exactly Home, Discover, Search, in that order. Discover Back returns Home; top-level switching is not a stack.

Runtime results on the final APK: initial Core selection was Movie/Popular with Movie, Series, and Channel available; Movie and Series results were observed, and Channel returned real audiobook catalog content. The account exposed a Genre extra (including options such as Animation); no other extra group was observed. Catalog and extra selections changed content through Core. The five-column grid navigated across multiple rows. Discover → Details → Back returned to the same semantic item after several rows; repeated earlier runtime checks covered five distinct item cycles. Discover → Home → Discover retained the selected Series/Featured/Genre state and the grid data; nav focus remained on Discover until Down restored Discover focus. Search route/keyboard and live WW search results worked; Home Details return worked. A final Channel pagination run appended another page while the same Backwater Flats item remained focused and visible item order stayed stable. The final app-data-preserving APK install succeeded. Logcat had no focus warning, FocusRequester warning, application AndroidRuntime exception, fatal exception, Compose exception, or ANR; normal Android `monkey` process startup messages were the only AndroidRuntime-pattern matches.

The previous Phase 3B late Home catalog emission remains a watch item: this phase observed Home returning with existing content and focus, but did not stage a controlled late Home emission. Runtime screenshots from this phase are outside Git at `/private/tmp/stremio-android-tv-phase4b-final/`.

Library is not complete. Remaining browse-route work is Library plus later browse refinements such as explicit-request Discover entry/See All if scheduled; do not start Library, Settings, playback, episodes, or Smart Playback as part of Phase 4B.

## Phase 4C — Production TV Library

Status: implemented on `feat/android-tv`, unit-tested, built, installed, and exercised on the existing Google TV ARM64 emulator. Starting remote tip: `a3ff961bfaca6bafb41bba9bfb3984542686e645`.

- Library consumes the existing Core `LibraryWithFilters` and `LibraryItem` through `CatalogRepository` and a narrow immutable `TvLibraryUiState`. Core's selected `LibraryRequest`, selectable type/sort requests, and `nextPage` remain authoritative. The TV route does not collect `MainUiState` or create a second Library backend model.
- `LibraryItem` mapping now preserves id, type, name, poster and shape, watched, remaining episodes, supported behavior hints, and real progress normalized from `state.timeOffset / state.duration` into 0..1. Missing duration yields unknown progress. Library-only fields are not replaced with fabricated metadata.
- Type labels map null to All, known values to Movie/Series, and retain humanized custom types. Runtime exposed All, Movie, and Series. The six Core sorts map to Last watched, Name A–Z, Name Z–A, Most watched, Watched first, and Unwatched first.
- The route uses the Discover-proven five-column, 144 dp poster, 72 dp safe-margin grid and existing TV poster focus contract. Library owns independent semantic focus, type/sort focus, grid scroll, and pagination memory. Selected-filter styling remains visible when focus moves elsewhere.
- For unchanged type+sort identity, a pure presentation snapshot retains the relative order of surviving items, refreshes each item's data from Core, removes missing items, and appends newly exposed items in Core order. A type or sort change adopts Core order. Page is excluded from the logical identity, so pagination extends the snapshot without a reset.
- Pagination dispatches Core `ActionLibraryWithFilters.LoadNextPage` through the app wrapper to `Field.LIBRARY`; the next request comes from Core `selectable.nextPage`. Focus-near-end triggering is bounded and de-duplicated by request identity.
- Details stores Library as its explicit origin and returns to the semantic item where it remains available. Library Back returns Home. Top-level switching is not a Back stack.
- Final top navigation is Home, Discover, Library, Search. Settings, Addons, and other nonfunctional destinations remain absent.

Runtime checks confirmed All/Movie/Series; all six Core sort labels; real watched/progress presentation; the five-column grid; and distinct selected/focused filters. D-pad route entry showed the complete Home/Discover/Library/Search navigation. Library → Details → Back returned to the exact Silo item and grid position. A direct Library → Home → Library cycle restored All / Last watched, the visible item order, and the filter focus target. Discover also rendered after D-pad route selection, but a fresh Search regression was not completed. The first launch immediately after reinstall hit one ActivityManager startup ANR (`failed to complete startup`) while the emulator was concurrently reporting system-service ANRs; a clean force-stop/relaunch resumed `TvActivity`, and no further app fatal, Compose, or focus warnings appeared in the bounded final log sample. Repeated multi-position Details returns, non-initial grid-position retention, Search regression, runtime pagination, natural Library mutation, and empty-filter behavior remain unverified. Pure tests cover bounded pagination, duplicate suppression, stable append/update/remove behavior, and focused-item fallback. Screenshots are outside Git at `/private/tmp/stremio-android-tv-phase4c-final/`: `tv-home.png`, `library-nav.png`, `library-type-focused.png`, `library-sort-focused.png`, `library-deeper-grid.png`, `details-from-library.png`, `library-restored.png`, and `library-home-retention.png`. The Library images show watched badges/progress and the complete Home/Discover/Library/Search nav.

Pure tests cover Library mapping, type/sort policy, exhaustive top-level routes and Details origins, snapshot behavior, pagination identity/de-duplication, and disappearing-item focus fallback. The full requested compile, assemble, and unit-test tasks pass (75 tests total, 0 failures). Runtime APK installation used `adb install -r` and preserved app data.

## Phase 5A — Production TV Details

Status: implemented on `feat/android-tv`, unit-tested, built, installed, and runtime-reviewed on the existing Google TV ARM64 emulator. Starting remote tip: `70c19166780cf88271fb41598991bb6eb75296b9`.

- `TvDetailsUiState` is a narrow immutable route presentation state for current Library membership and action progress. `TvApp` observes it directly and does not observe `MainUiState` or raw Core protobuf models.
- Opening Details publishes the existing `CatalogItem` preview immediately. Full `MetaDetails` enriches that same route state when it arrives. A load failure keeps the preview page and actions usable and adds only a restrained inline message. Loading-to-ready updates do not recreate the route or its focus requesters.
- `mergeDetailsPreview(preview, fullMetaItem)` requires matching `type:id`, uses non-empty full Core values when present, and otherwise retains preview fields. It carries Continue Watching progress/video identity, IMDb rating, inCinema, remaining episodes, and the CW marker from the preview pipeline. Core's available `inLibrary` and watched values remain authoritative. Links, poster shape, logo, artwork, runtime, release values, and behavior hints are preserved or enriched without hand-building a second `CatalogItem` mapper in `MainViewModel`.
- Artwork selection uses actual metadata: a real background selects `RichBackdrop`; a poster is wide only when Core says `posterShape == Landscape`; all other usable posters use `ContainedPoster`; no usable image selects `TextOnly`. Backdrop colors remain visible at the right while a readability gradient blends toward the TV background at the left. No blur or image recoloring is applied.
- The wide 960×540 dp page puts logo/title, factual release/runtime/type metadata, a three-to-five-line synopsis, Add/Remove Library and secondary Back controls at the left, with backdrop art across the right. Genres, director, and a short cast list are informational text. There is no Play, trailer, episode, or stream control.
- The metadata line includes only real release info/year, runtime, type, and a genuine existing IMDb rating when available; it trims empty values and removes duplicates. Logo is preferred when it loads, while a textual title stays visible beneath it so an unavailable or visually empty logo cannot hide the title.
- The Library label is projected against exact `type:id` membership from the live Library state, with full metadata as fallback. Add and Remove use the existing repository/Core actions. The membership label updates while Details remains open, and the focused action stays in place. The emulator check restored the original Add-to-Library state after exercising add and remove.
- Initial focus is the Library action, with visible Back as the fallback. The focused TV Material action has the standard strong focus indication; Library membership is communicated by label/icon without a persistent second focus border.

Runtime results on the final APK (1920×1080 physical pixels at 320 dpi = 960×540 dp): a clean force-stop/relaunch resumed `TvActivity`; no app-process startup ANR recurred. Home opened Silo Details immediately from preview and enriched to its real background, 2023–, 51 min, Series, synopsis, and available metadata. Discover opened Unabomber with real background, release/runtime/type, synopsis, genres, director, and cast. The title remained visible during enrichment. The focus remained on the Library action as its Add/Remove state changed. Ordinary Details content fit without scrolling. No natural MetaDetails failure occurred to exercise the inline error state; the exception/error branch retains the preview in source, but no dedicated UI error test or forced network-error run was performed.

Route returns on the final APK: Home → Details → Home returned to the exact Silo Continue Watching item; Discover → Details → Discover returned to the exact Unabomber result; Library → Details → Library returned to the exact Silo card and grid position; Search → a real result → Details → Search returned to the exact first result card and its row position. The earlier Library action check removed For All Mankind from the current Continue Watching feed as a side effect, so the Home route check was repeated on the still-present Silo item. The 30-second Details idle check did not show a spontaneous route or focus change. Filtered final logcat contained no app-process ANR, `FocusRelatedWarning`, `FocusRequester`, AndroidRuntime/FATAL, or Compose exception.

The series audit opened the real Silo series through `MetaDetails`. The pinned generated Core `MetaItem` exposes `videos`; each `Video` can carry id/title/released/overview/thumbnail/streams and `seriesInfo` season/episode, upcoming, watched, current-video, and progress fields. The Phase 5A TV Details presentation intentionally does not render or request episode streams, and its runtime UI does not expose the Core video count/current-video/watch-state values. Record those values from a Core-backed series fixture or targeted 5B instrumentation before choosing season grouping and resume behavior; no episode list or season count is claimed here.

Ten new pure tests raise the suite from 75 to 85: Details merge/enrichment and preview retention; partial-link preservation; artwork classification for all four modes; factual metadata formatting; exact type:id Library membership including stale-preview override; exhaustive Home/Discover/Library/Search route-origin support; and preview presence during loading. `:app:compileDebugKotlin`, `:app:assembleDebug`, and `:app:testDebugUnitTest` all passed (85 tests, 0 failures). No Gradle stall reproduced. The installed APK is `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`; the final rebuilt APK was installed with `adb install -r` and app data was preserved. Screenshots remain outside Git at `/private/tmp/stremio-android-tv-phase5a/`: `06-final-enriched-details.png`, `10-library-add.png`, `11-library-restored.png`, `14-details-from-discover.png`, `15-discover-return.png`, `18-details-from-library.png`, `19-library-return.png`, `23-details-from-search.png`, `24-search-return.png`, `26-details-from-home.png`, `27-home-return.png`, and the final-source screenshots `29-final-source-preview-details.png`, `30-final-source-enriched-details.png`, and `31-final-source-details-idle-30s.png`.

Known runtime coverage gaps at the Phase 5A checkpoint: no real TextOnly or portrait-only no-background catalog item was available in the exercised items; pure tests cover those artwork policies. No real metadata error occurred. Video field values/counts were not observable from the TV UI at that checkpoint and were audited during Phase 5B below. Episodes, streams, player, Smart Playback, and Skip Segments were not implemented at the Phase 5A checkpoint.

## Phase 5B — Production TV seasons and episodes

Status: implemented on `feat/android-tv`, unit-tested, built, installed with app data preserved, and runtime-reviewed on the existing Google TV emulator. Starting commit: `02ed1ccae89174826fec00ec305204126e74e03d`.

### Real Core series audit

The debug-only, once-per-item audit observed Silo (`tt14688458`) directly from the ready `MetaDetails` response. The response contained 35 videos across numeric seasons `[0, 1, 2, 3, 4]`; season 0 is present as Specials, and no videos lacked `seriesInfo`. All 35 had thumbnails and release timestamps; 34 had an overview. Core marked 1 upcoming, 21 watched, and 1 current video. One video had non-null progress: `0.08573740073854498` (Core percentage points). No embedded episode streams were populated: total stream count 0 across 0 videos. Browsing used the existing full MetaDetails response and did not request episode streams.

### Episode model and presentation

- `MetaDetails.episodes` carries a loss-minimal app-domain `EpisodeOption` projection with video ID, title, thumbnail, overview, raw release timestamp, localized formatted release date, watched/current/upcoming flags, raw Core percentage, and optional `EpisodeSeriesInfo(season: Long, episode: Long)`. The explicit nullable series-info model distinguishes omitted metadata from a real season 0 while legacy integer fields remain compatible with the mobile Streams sheet.
- `TvDetailsUiState.episodeBrowser` is built from those already-loaded episodes. It groups only videos with real `seriesInfo`, retains ungrouped videos/count, sorts season numbers and episode numbers numerically, and uses Core source order as a stable tie-breaker. Season 0 is labeled `Specials`; positive values use `Season N`. Movies and metadata with no episodic series info emit no episode browser.
- Default season order is current episode, exact Continue Watching video ID, first non-zero progress, first positive season, Specials when it is the only season, then first available season. Current, Continue Watching, progress, watched, and upcoming remain separate states.
- Core `Video.progress` is a percentage from 0 to 100 and is preserved raw in the domain model. Episode bars explicitly clamp and divide by 100 to produce a UI fraction from 0 to 1. Library `CatalogItem.progress` remains a separate 0-to-1 ratio.
- Series Details keeps the production backdrop and logo/title hierarchy while compacting the hero, synopsis, and actions above a horizontal season selector and vertical episode list. Runtime showed three full episode rows with more content scrollable. Rows use real 16:9 thumbnails or a neutral placeholder, factual numbering/title/release/overview, separate current/continue/watched/upcoming labels, and progress only for values strictly between 0 and 100 when not watched. No runtime is invented and episode activation does not request streams.
- Explicit D-pad traversal connects actions, seasons, and episodes. Initial focus remains on the Library action; the list scrolls the real current/continue item into view. In-route memory is keyed by season and `videoId`, with remembered index/scroll fallback for removed items. Switching seasons is local, preserves per-season position, and never adds nested Back behavior.
- Logo rendering no longer disappears after Coil success. The logo stays composed and the title remains underneath as a fallback for loading, failure, or visually empty assets.

### Phase 5B runtime results

On the 1920×1080 emulator (960×540 dp), Silo showed all five real seasons, at least three readable rows, the production backdrop, and the loaded logo. Season 1 focused the real S1E3 current/continue item and returned to it after switching to Season 2 and back. Ten paced episode focus moves ended on S2E9 with the list scrolled and focus retained; no previous-row rim remained. Season 4 showed the real S4E1 `Upcoming` label and Jul 9, 2027 release date. A 30-second idle interval on focused S1E3 retained route, season, row focus, and scroll. No natural metadata/library progress emission occurred while browsing, so async-update retention is covered by pure semantic-ID/fallback tests only.

Unabomber remained on the Phase 5A movie layout with no season or episode section. Its Library action toggled Add → In Library → Add; physical Back returned to the exact originating Home item. Silo also returned to its Home Continue Watching item. A real logo-bearing item was available; its loaded logo remained visible through recomposition and its factual text title stayed available. Source review shows season changes and focus events only update local Compose state; filtered logcat had no episode stream lookup, FocusRelatedWarning, FocusRequester warning, app fatal, Compose exception, or app-process ANR. No episode stream request is issued by Phase 5B browsing.

The final installed universal APK was `app/build/outputs/apk/debug/app-universal-debug.apk`. Compile, assemble, and unit tests pass: 95 tests, 0 failures. Screenshots are outside Git under `/private/tmp/`: `stremio-phase5b-series.png`, `stremio-phase5b-season2selected.png`, `stremio-phase5b-ten-moves.png`, `stremio-phase5b-s4b.png`, `stremio-phase5b-movie.png`, and `stremio-phase5b-idle-before.png` / `stremio-phase5b-idle-after.png`. An automatic metadata or library-progress emission during episode focus was not observed.

## Runtime verification gates

Navigation:
- repeated Left/Right
- repeated Up/Down
- first/last boundaries
- async catalog updates
- exact return focus
- activity recreation
- no focus theft while idle

Authentication:
- QR scan
- polling
- new-link behavior
- session restore
- network failure

Search:
- repeated Search → Details → Back exact-focus loops
- physical keyboard
- phone remote text input where supported
- Back/IME behavior

Playback:
- ExoPlayer
- MPV
- HLS/direct
- torrent/local server
- subtitles/audio
- media keys
- real-TV performance

## Definition of done for each Codex task

Report:
1. branch
2. starting commit
3. files changed
4. architecture/behavior implemented
5. explicit out-of-scope items
6. tests added
7. build/test results
8. APK paths
9. git status after push
10. runtime checks still required
11. uncertainties/dependencies

Do not claim runtime success from static inspection.

## Repository hygiene

Do not commit:
- local.properties
- Gradle daemon files
- temporary JDK configuration
- generated build output

Do not accidentally change submodule pointers.

Before commit:
- `git status --short`
- review full diff
- `git diff --check`

## Phase 5C — TV source discovery and selection

Status: implemented on `feat/android-tv`, covered by pure tests, built, installed with app data preserved, and runtime-reviewed on the ARM64 Google TV emulator. Starting commit: `29733faf578782b48f5f7ca9d9981bd741363ef4`.

`TvRoute.Streams` is a nested surface over the retained Details composition. It preserves the Details origin (Home, Discover, Library, or Search) and the exact selected episode across Back. Episode targets carry Core `videoId` directly with `guessStreamPath=false`; non-episodic targets carry null `videoId` and `guessStreamPath=true`. Upcoming episodes remain inert. Non-episodic Details exposes a real Choose Source action.

TV stream state is separate from mobile `MainUiState` and the mobile Streams sheet. A TV-only ViewModel job cancels obsolete requests and maps Core `LoadableStreams` into immutable provider and stream state. It additionally validates `MetaDetails.selected` against the expected type, content ID, video ID, and guess semantics before applying an emission. Provider loading/ready/error status is isolated, so one error cannot clear another provider's results. Ready results render incrementally. Provider filtering is local.

Each stream has a semantic key derived from request/source identity and available stable Core evidence (torrent identity/file index, hashes, filename/size, and descriptive fallback); flattened mobile list index is not authoritative. An interaction snapshot updates existing rows in place, removes missing rows, and appends arrivals in Core order, preserving focus where possible. Stream behavior hints and Core source variants are retained in the app model. The selector only marks a chosen source; no resolve/play/player/history path is called.

### Phase 5C runtime audit

Silo S2E7 (`videoId` from the real episode row) produced 3 provider requests: Local Files error with 0 streams, NoTorrent ready with 19, and Torrentio ready with 52 (71 provider results total). Arrival order was Torrentio then NoTorrent. The screen showed ready streams while preserving the provider error and allowed local filtering. Five duplicate semantic identities were collapsed, leaving 66 unique displayed options. Source-kind and metadata coverage counts below are for those 66 displayed options: Direct 12, Torrent 52, External 2, YouTube 0, Archive 0, Other 0; `bingeGroup` 52, filename 52, videoHash 0, videoSize 0, `notWebReady` 0, display quality 54, seeds 52, and parsed size 52. No raw URLs or filenames are recorded in committed docs.

Runtime checks exercised 10+ paced row moves, filtering, selection distinct from focus, idle stability, Details return to Season 2/S2E7 with the exact episode row focused, and re-entry to the same S2E7 stream target. An S0E4 all-provider error state also remained usable and returned focus to the same episode. A naturally timed different-episode stale race was not observed; the semantic guard and cancellation are unit-tested. S4E1 Upcoming was previously verified inert at the episode-browser gate; no stream route opened from that row in Phase 5C checks. The non-episodic Choose Source action is present, but no movie stream audit was captured: an automatic review rejected an emulator key sequence because focus could have activated Add to Library. Dedicated pure tests and source review cover target creation and guess-stream semantics.

Source call-path review confirms selection does not call `resolvePlayableUrl`, `PlaybackRepository.resolveAndLoadStream`, `MainViewModel.playStream`, `proceedWithPlayback`, or load the Player. Runtime remained on the TV Details/Streams route; no player load or playback-start event was observed. No raw URLs are logged. Filtered logcat contained no FocusRelatedWarning, FocusRequester warning, Compose exception, AndroidRuntime fatal, or app-process ANR in the checked interval.

Sixteen new pure tests cover target semantics, stale target matching, provider isolation, stream identity and metadata, interaction-stable ordering, selection clearing, filtering, focus fallback, upcoming behavior, nested route, and non-episodic action availability. The full existing suite remains included (111 tests total). The requested compile, assemble, and unit-test tasks passed. Screenshots are outside Git under `/private/tmp/stremio-android-tv-phase5c/`.

## Current next gate

Phase 6A ordinary manual playback is implemented on `feat/android-tv`, starting at `41bbb1a4db205f1854f7879b61d54d4ee71980bb`. Explicit stream activation enters a nested TV Player and uses the existing Core resolve-and-load repository path. Attempt-scoped monotonic timestamps cover resolution start, playable source resolution, player load start/return, and first visual signal. ExoPlayer uses its rendered-first-frame callback; MPV uses the first playback-restart event after file load. Retries receive a new attempt identity, stale events are ignored, and requested versus actual engine is tracked through the existing MPV-to-Exo compatibility fallback.

Playback state stays TV-specific. Starting a TV stream does not enter the broad mobile `playStream` flow; history stream selection is committed only after first visual progress, and progress reporting is gated on that same signal. Back reports final progress if eligible, releases the player, and returns to Streams. The player exposes basic play/pause and bounded D-pad seek controls. Completion is reported without auto-advancing. No Smart Play/ranking, fallback policy, segment skipping, next-episode preparation, or preload was added.

The requested compile, assemble, and unit-test tasks pass (124 tests, 0 failures); `git diff --check` passes. The universal debug APK installed with app data preserved on the ARM64 Google TV API 36 emulator and `TvActivity` was brought to the foreground. The signed-in TV Home screen was visually confirmed. A Silo S2E7 playback attempt and ExoPlayer/MPV first-frame run were not completed, so runtime TTFF measurements remain uncollected and neither engine is claimed runtime-validated by this phase. The phase 6A implementation includes privacy-safe trace formatting tests; logs and documentation do not include stream URLs.

The next exact milestone is the first manual Silo S2E7 source playback runtime audit on TV: capture resolution and first-visual timing, confirm controls/Back/progress behavior, then repeat with the other supported engine if available. Keep the staged Playback Experience roadmap gated on ordinary playback runtime validation; do not begin its later stages as part of that audit.

The Phase 3B late Home catalog emission remains a watch item. This phase did not stage a controlled late Home emission, and the historical focus warning remains a watch item because it has not reproduced. Continue observing normal asynchronous catalog arrivals in future runtime checks.

Safe follow-up work includes Core metadata preservation needed by real Details/episodes/streams, targeted LocalSearch/search-history bridge completion, and benchmark/Baseline Profile infrastructure when stable journeys are selected. Preserve the semantic TV focus/restoration architecture during those changes.
