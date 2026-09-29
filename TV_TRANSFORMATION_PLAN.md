# Stremio Android TV Transformation Plan

Status: active planning document  
Project: `robbis95/stremio-android`  
Target: Android TV / Google TV  
Planning baseline: `feat/android-tv-milestone-1`

## 1. Purpose

Transform the existing Android application into a first-class TV experience while preserving and reusing the existing Stremio Core, account, catalog, library, addon, stream-resolution, streaming-server, and playback infrastructure wherever appropriate.

The TV application must feel intentionally designed for a 10-foot interface and D-pad/remote interaction. It must not be a stretched or lightly modified mobile UI.

This document is the long-lived implementation roadmap. Individual Codex tasks should implement one bounded phase at a time and must not silently expand scope.

## 2. Non-negotiable branding rule

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

If a concept conflicts with actual Stremio capabilities or data, preserve the visual/design principle but adapt the behavior to Stremio rather than fabricating functionality.

## 3. Working model

The user is not expected to manually implement this transformation.

Development proceeds as a sequence of narrowly scoped Codex tasks.

For every phase:

1. Inspect the latest pushed branch before editing.
2. Work from the correct current branch tip, never an assumed stale local checkout.
3. Keep TV presentation isolated from mobile presentation unless shared logic genuinely belongs below the UI layer.
4. Reuse existing Stremio Core/repositories/actions instead of duplicating backend behavior.
5. Build and run unit tests after each task.
6. Do not claim runtime behavior is verified unless it was actually tested on Android TV / Google TV.
7. Report exact files changed, architecture decisions, build results, APK paths, and remaining runtime tests.
8. Push completed work to the requested feature branch.
9. Do not start the next phase without an explicit next task.

## 4. Design north star

The supplied TV concepts define the desired overall feel:

- premium, restrained, modern television UI
- generous spacing and strong visual hierarchy
- content-led imagery
- simple top-level navigation
- large hero/detail imagery where useful
- horizontally browsable content shelves
- strong, unmistakable D-pad focus
- QR-first account linking
- search designed specifically for remote control
- series pages with season navigation and readable episode rows
- minimal visual clutter
- layouts designed for viewing from normal television distance

The concept's light visual language is a direction, not a requirement to blindly force every surface to white. Legibility, poster artwork, Stremio assets, focus visibility, content imagery and real TV behavior take priority.

### Focus visibility

Concept imagery may show subtle outlines, but production focus must be more obvious.

A focused TV element should be immediately identifiable from viewing distance through an appropriate combination of:
- border/glow
- elevation/shadow
- scale or emphasis where appropriate
- contrast

Never sacrifice deterministic navigation for visual subtlety.

## 5. Concept-to-Stremio translation

### Splash / startup

Concept principle:
- calm full-screen startup
- centered brand
- simple progress/loading state

Stremio implementation:
- Stremio branding only
- no AURA logo or slogan
- avoid fake progress
- transition as soon as actual restore/startup state permits

### Account linking

Concept principle:
- QR login is visually primary
- manual credential login is secondary

Stremio implementation:
- use official Stremio account-link flow
- display Stremio-generated link/code
- poll account-link authorization
- pass returned authKey through existing token-login pipeline
- optional email/password fallback
- no direct Facebook TV flow when QR linking can handle authentication on the user's phone
- no fictional profile selector unless Stremio itself provides a real profile model

### Home

Concept principle:
- clear top navigation
- hero/content feature area
- Continue Watching
- multiple horizontal shelves
- strong content imagery

Stremio implementation should eventually use real:
- board/catalog shelves
- Continue Watching
- library state
- discover/catalog metadata

Do not invent recommendation systems such as "Top Picks for You" unless actual Stremio data exposes an equivalent source.

### Search

Concept principle:
- large search field
- remote-friendly on-screen keyboard
- filters/categories
- results directly below

Stremio implementation:
- use existing Core search
- categories/filters must correspond to real available Stremio types/data
- support TV remote, physical keyboard, and compatible phone-remote text entry where possible
- deterministic transition between search field, keyboard, filters and results

Do not fabricate unsupported categories such as Actors merely because a concept image contains them.

### Movie / series details

Concept principle:
- large artwork/hero area
- immediately readable metadata
- clear primary action
- library action
- details
- for series: season tabs and large episode rows with watch/progress state

Stremio implementation:
- real metadata from Core/addons
- real library state
- real episode data
- real resume/progress state where available
- stream selection remains a separate functional step unless a safe automatic-resume path already exists

### Player

Concept direction for later phase:
- TV-native control overlay
- large readable transport controls
- D-pad seek
- audio/subtitle access
- episode navigation where applicable
- predictable Back behavior

Reuse existing playback engines and playback state; do not rebuild playback infrastructure without demonstrated need.

## 6. Architecture principles

### Reuse

Prefer reuse of:
- Stremio Core
- repositories
- MainViewModel actions/state when practical
- auth/session pipeline
- board/catalog/search/library/addon flows
- stream resolution
- local streaming server
- ExoPlayer/MPV playback engines

### TV-specific presentation

Create dedicated TV presentation for:
- navigation/focus
- Home
- search
- detail/episode layouts
- stream selection
- player controls
- library/discover browsing
- addons/settings where needed

Do not progressively turn mobile composables into conditional phone/TV components if that makes focus/navigation fragile.

### Stable identity

Remote focus must be based on stable semantic identity whenever possible:
- content: `type:id`
- shelf/catalog: stable catalog/shelf identity
- episode: stable video/episode identity
- route destination: stable route identity

Do not use visual index alone for persistent focus restoration.

### Async data

Core/addon/catalog data often arrives incrementally.

Data emissions must not:
- steal focus
- unexpectedly reposition lists
- reset active selection
- repeatedly rerun initial focus logic

Focus restoration should be event-driven, not driven by arbitrary data-list changes.

## 7. Phased implementation roadmap

### Phase 1 — Runnable TV proof

Status: implemented; runtime verification still ongoing.

Scope:
- TV launcher/activity
- manifest/banner
- QR account linking
- first Home shelf
- Details
- Back
- stable content identity
- deterministic Home → Details → Back restoration

Known runtime work:
- intensive emulator regression test still required
- test catalog updates while navigating
- test repeated details/back restoration
- test launcher/session restore behavior

### Phase 2 — Navigation/focus foundation

Goal:
Build reusable TV focus/navigation primitives and prove them across multiple Home shelves.

Scope:
- reusable focusable poster/card
- reusable shelf row
- multi-shelf Home
- Left/Right navigation
- Up/Down shelf traversal
- per-shelf remembered content
- nested lazy-list restoration
- deterministic Details return to exact shelf + item
- pure-Kotlin fallback/focus-location tests where practical

Do not add full Discover/Search/Streams/Player in this phase.

### Phase 3 — Real Stremio Home shell

Goal:
Move from navigation proof to a recognizable Stremio TV Home matching the visual direction.

Scope to evaluate after Phase 2 runtime validation:
- production Home hierarchy
- Continue Watching
- real board/catalog shelves
- top-level TV navigation shell
- optional content hero based only on real available metadata
- Stremio branding
- production spacing/type scale/focus treatment

Do not invent recommendation sources.

### Phase 4 — Search

Goal:
TV-native search based on existing Stremio Core search.

Scope:
- Search route
- large search field
- remote-friendly keyboard
- physical keyboard/remote text handling
- filter/type controls supported by real data
- results shelves/grid
- deterministic Back/focus restoration

### Phase 5 — Details, seasons, episodes, streams

Goal:
Turn the details proof into production movie/series flows.

Scope:
- production hero/details
- library action
- seasons
- episode rows
- watched/resume state
- incremental stream discovery
- stable provider/stream selection focus

### Phase 6 — Playback

Goal:
Make existing playback engines usable as a real TV player.

Scope:
- TV player overlay
- play/pause
- D-pad seek
- media keys
- Back contract
- audio tracks
- subtitles
- subtitle settings
- next episode
- ExoPlayer and MPV parity where possible
- actual hardware/runtime verification

### Phase 7 — Discover and Library

Goal:
Complete core browsing routes.

Scope:
- Discover
- real filters supported by Stremio data
- Library
- focus restoration
- pagination/loading/error behavior
- top-level nav integration

The exact ordering of Phase 7 relative to Search/Details may be adjusted based on dependencies found during implementation.

### Phase 8 — Addons, account, settings

Goal:
Make management tasks practical with a remote.

Scope:
- addon browsing/details/install/uninstall
- account actions
- relevant app/player/server settings
- remote-friendly controls
- safe text entry where unavoidable

### Phase 9 — Production polish and performance

Goal:
Move from functional TV client to release-quality TV experience.

Scope:
- focus visual polish
- animation only where performance allows
- typography/spacing
- image loading
- startup
- low-end TV performance
- memory/GPU behavior
- launcher/banner assets
- accessibility
- overscan/safe-area checks
- real-device codec/player testing
- crash/error handling
- regression suite

## 8. Planned top-level TV navigation

Do not copy the fictional concept labels literally.

The final structure should map to real Stremio capabilities.

Candidate structure to validate during Phase 3:

- Home
- Discover
- Library
- Search
- account/settings entry

Movies/Series may be surfaced as Discover filters/types rather than permanent top-level destinations if that better matches actual Stremio data.

Addons and Settings may live under a secondary/account destination rather than occupying primary navigation.

This decision should be based on actual app behavior and remote-navigation simplicity, not on the fictional AURA concept.

## 9. Runtime verification gates

A phase that materially changes navigation is not considered complete solely because it compiles.

Required later emulator/device gates include:

### Navigation
- repeated Left/Right
- repeated Up/Down
- first/last boundary behavior
- asynchronous catalog updates
- exact return-focus restoration
- activity recreation/session restore
- no focus theft while idle

### Authentication
- QR scan
- authorization polling
- new-link behavior
- session restoration after restart
- network failure

### Search
- D-pad keyboard
- physical keyboard
- phone remote text input when supported
- Back/IME behavior

### Playback
- ExoPlayer
- MPV
- HLS/direct streams
- torrent/local server path
- subtitle/audio tracks
- remote media keys
- performance on real TV hardware

## 10. Definition of done for each Codex task

A task report must include:

1. branch
2. starting/base commit
3. files changed
4. architecture/behavior implemented
5. explicit out-of-scope items
6. tests added
7. build/test commands and results
8. APK paths when applicable
9. git status after commit/push
10. runtime tests still required
11. any uncertainty or discovered dependency

No phase should silently declare runtime success from static inspection.

## 11. Repository hygiene

Do not commit local-machine artifacts such as:
- `local.properties`
- Gradle daemon property files
- temporary JDK configuration
- generated build output

Do not accidentally update submodule pointers.

Before committing:
- inspect `git status --short`
- inspect the full diff
- run `git diff --check`

## 12. Current next task

Current intended next development phase:

**Phase 2 — Navigation/focus foundation**

Before moving into visual Home transformation, Phase 2 focus/navigation must be implemented and later stress-tested on the Google TV emulator.

After Phase 2 runtime validation, use the concepts summarized in this document as the design north star for the production Stremio TV Home and navigation shell.
