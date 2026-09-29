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
Status: implemented, compiled, assembled, unit-tested, installed and smoke-launched on the Google TV ARM64 emulator. Manual navigation stress verification is still pending.

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
- `testDebugUnitTest` passes (19 tests, 0 failures)
- fresh universal APK installs successfully on Google TV ARM64 emulator
- `TvActivity` launches and remains resumed
- basic D-pad smoke input does not crash
- no fatal/ANR/Compose/FocusRequester crash markers observed in smoke logcat

Still required:
- manual emulator stress test of D-pad behavior
- exact per-shelf focus-memory verification
- repeated Home → Details → Back restoration
- validate real catalog emissions do not steal focus
- visual review of Phase 3A at TV viewing distance

### Phase 3A — TV visual foundation
Status: implemented in source; Phase 2 runtime stress verification is still pending.

Implemented:
- TV-specific color, typography, spacing, and focus design system
- branded TV startup state using the existing Stremio splash mark
- QR-first account-link presentation with secondary email/password sign-in
- visual refinement of the existing real-data multi-shelf Home and poster cards

### Phase 3B — Production Home shell
Next intended step, only after Phase 2 emulator verification:
- production Home hierarchy
- Continue Watching
- real board/catalog shelves
- top-level TV navigation shell
- optional real-data hero
- Stremio branding

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
- production details
- library action
- seasons
- episode rows
- watched/resume state
- stream discovery
- stable provider/stream focus

### Phase 6 — Playback
- TV player overlay
- play/pause
- D-pad seek
- media keys
- Back contract
- audio/subtitle tracks
- subtitle settings
- next episode
- ExoPlayer/MPV validation

### Phase 7 — Discover and Library
- Discover
- real supported filters
- Library
- focus restoration
- pagination/loading/error handling
- top-level nav integration

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

## Candidate top-level TV navigation

Validate during Phase 3 against real Stremio behavior:
- Home
- Discover
- Library
- Search
- account/settings entry

Movies/Series may be Discover filters/types rather than permanent destinations.

Addons and Settings may live under a secondary destination.

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
- D-pad keyboard
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

## Current next gate

Before Phase 3B, Phase 2 must pass the remaining manual Google TV stress gate.

Build/runtime smoke verification is complete as of commit `5d8b246f468456052a19095648bb236ea596d39e`.

Do not start Phase 3B until the manual focus/navigation and visual review is completed.

Safe bounded work that may proceed before emulator access returns:
- preserve currently discarded Core preview metadata in Android presentation models, with mapper tests
- preserve episode progress/overview/upcoming metadata needed by future TV episode UI
- preserve stream behavior hints needed for later binge-compatible stream selection
- reduce TV dependency on the monolithic `MainUiState` through narrow state slices without changing focus behavior
- audit and stage a TV-only migration toward `androidx.tv:tv-material` without replacing the custom Phase 2 focus restoration model
- document/prepare LocalSearch and search-history bridge work without inventing duplicate TV-only backend state
- prepare performance benchmark/Baseline Profile infrastructure once stable test journeys exist

The next coding task should prioritize data preservation before building the final Home hero.
