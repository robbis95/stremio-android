# Stremio TV Core Capability Audit

Audit date: 2026-09-29  
Canonical branch: `feat/android-tv`  
App baseline inspected: `ad51e362e51062312871cc6737729685b0b97ba3`

This audit separates:

1. capabilities usable by the current Android client today,
2. capabilities present in the pinned official Stremio Core but incompletely exposed through the Kotlin/protobuf bridge,
3. capabilities that appeared in newer official Core releases and therefore require a deliberate bridge upgrade.

The purpose is to stop UI work from guessing what Stremio can do.

---

## Current dependency chain

The Android project includes the local composite build:

`streamio-core-kotlin/stremio-core-kotlin`

That wrapper's Cargo lock currently pins official:

`Stremio/stremio-core @ 90c38f181d290fc705049e4c8bd30df00f6f3e66`

which is Core 0.59.0.

The Android Gradle file also declares `com.github.Stremio:stremio-core-kotlin:1.15.0`, but the included composite build is the source that must be audited for actual generated models/actions used by this checkout.

Latest official Core release observed on 2026-09-29:

`stremio-core-web-v0.63.2`

Do not conflate upstream latest with capabilities actually bridged into this app.

---

## Current Android surface

### Auth

Usable now:
- email/password login
- Facebook token login in mobile flow
- token login
- logout
- QR account-link flow implemented by the TV app
- session persistence/restore

Core itself also contains a Link model. The current TV QR implementation uses direct Stremio link-service HTTP instead of the Core Link model.

Recommendation:
- keep the working QR implementation for now
- later assess migrating it to the Core Link model only as an isolated cleanup

### Board / Home

Usable now:
- Board `CatalogsWithExtra`
- range loading
- incremental catalog pages
- real addon-provided shelf names
- Continue Watching preview

High-value gap:
- Android's `CatalogItem` loses rich preview metadata available from Core.

### Discover

Usable now:
- `CatalogWithFilters`
- selectable types/requests
- load selected request
- extract loaded catalog items

Design implication:
- Discover navigation/filtering can be driven by actual Stremio selectable data rather than hard-coded Movies/Series categories.

### Library

Usable now:
- `LibraryWithFilters`
- load/sort/page request
- sync library
- add/remove item

Bridge also has LibraryByType model support.

Recommendation:
- audit LibraryByType before implementing a custom TV grouping layer.

### Search

Usable now:
- full addon-catalog search using `CatalogsWithExtra`
- range loading of search results

Current behavior:
- ViewModel debounces 300 ms
- full Core addon search can run while typing

Core 0.59 capability not end-to-end exposed to Android app:
- LocalSearch

The included Rust `AndroidModel` contains `local_search: LocalSearch`, and the runtime `Field` enum maps `LocalSearch`. `ActionLoad` protobuf also has a `local_search` arm. However, Android cannot currently consume LocalSearch autocomplete end-to-end:

- `AndroidModel.get_state_binary(LocalSearch)` is explicitly `unimplemented!()` in `model/model.rs`
- the runtime protobuf `Action` oneof has no Core `ActionSearch` query-action arm, so Kotlin cannot dispatch the LocalSearch query action
- the Kotlin bridge therefore exposes neither a usable LocalSearch result state nor the full query action path

Do not call this a working Android LocalSearch capability merely because the Rust model and Field enum exist.

Official Stremio Web uses LocalSearch for autocomplete.

Phase 4A TV Search uses the already working addon-aware `Field.SEARCH` / `CatalogsWithExtra` path and `loadSearchRange`. Its on-screen keyboard updates immediately; TV submits meaningful (at least two non-space Unicode code point) queries through the existing ViewModel's cancellable 300 ms debounce. Blank and one-character input do not dispatch remote addon searches.

Future targeted task: **LocalSearch bridge completion**. Complete the Kotlin/protobuf query-action and state-payload bridge, add bridge tests, then consider local autocomplete. Do not bypass Core with a custom Android search index.

### Search history

Official Stremio Web obtains search history from Core Ctx.

Current Android protobuf Ctx contains:
- profile
- events
- streaming server URLs

It does not expose search history.

Core ActionCtx supports `ClearSearchHistory`, but the current Android ActionCtx protobuf does not expose that action.

Recommendation:
- include search history in bridge modernization if upstream/current schema supports it cleanly
- do not create a TV-only duplicate history store without an explicit product reason

### Metadata previews

Current generated `MetaItemPreview` exposes:

- id
- type
- name
- poster shape
- poster
- background
- logo
- description
- release info
- runtime
- released
- links
- behavior hints
- deep links
- in-library
- watched
- in-cinema

Android `CatalogItem` now preserves the rich preview fields, including
`defaultVideoId`, `featuredVideoId`, and `hasScheduledVideos` through a stable
app-domain behavior-hints model. The AddToLibrary mapper carries those values
back into Core. Its deep-links value remains empty because that action has no
request context from which to derive Core deep links.

Use the already-loaded preview fields for the Home hero before requesting
`MetaDetails` on focus.

This is a direct speed optimization because Home can react from loaded preview state without requesting MetaDetails on focus.

### Full metadata

Usable now through MetaDetails:
- metadata item
- description
- links
- genres
- cast
- directors
- runtime
- release info/year
- trailers
- videos
- streams

### Series / episodes

Current generated Video exposes:
- id
- title
- released
- overview
- thumbnail
- streams
- series season/episode
- upcoming
- watched
- current video
- progress
- deep links

Current Android EpisodeOption throws away some of that information.

Recommendation:
- preserve `overview`, `progress`, and `upcoming` before final episode UI

### Streams

Current Stream model can carry:
- URL source
- YouTube
- torrent/tramvai
- external source including Android TV URL
- player frame
- archive/NZB variants
- subtitles
- behavior hints
- deep links

Behavior hints include:
- notWebReady
- bingeGroup
- country whitelist
- proxy headers
- filename
- video hash
- video size

Current Android StreamOption does not preserve all of these semantics as first-class presentation/selection data.

Recommendation:
- preserve `bingeGroup` before smart next-episode stream matching
- preserve source semantics rather than treating every stream as an equivalent text row

### Player

Usable now:
- Player model
- stream resolution
- direct URL fallback
- audio track state
- subtitle track state
- subtitle settings
- play/pause/progress/seek actions
- end action
- next-video action
- resume position
- playback repository
- ExoPlayer
- MPV

This is enough to build a real TV player without replacing playback infrastructure.

### Streaming server

Usable now:
- native server start/stop
- readiness probe
- settings
- torrent statistics
- foreground service mode
- stream-server fallback stub when JNI unavailable

Current app starts the server during MainViewModel initialization.

Recommendation:
- benchmark eager vs deferred prewarm rather than changing it speculatively

### Addons

Usable now:
- installed addon state
- addon details
- install
- uninstall
- upgrade
- Trakt install/logout actions

TV addon management is therefore mostly a presentation/input problem, not a backend capability gap.

---

## Current wrapper omissions / mismatches worth fixing

Priority order for TV:

1. rich preview preservation in Android models — no Core upgrade required
2. episode progress/overview/upcoming preservation — no Core upgrade required
3. stream behavior-hint preservation — no Core upgrade required
4. LocalSearch bridge completion (query action and state exposure) — bridge work required
5. search-history bridge exposure — bridge work required
6. evaluate Core Link model instead of custom QR HTTP — optional cleanup
7. newer Core models/fixes — controlled Core upgrade required

---

## Newer Core track

Latest official Core is ahead of the pinned 0.59.0 release.

Do not upgrade merely to "be current".

Upgrade when one of these is true:
- a needed TV capability is materially better upstream
- a bug affecting TV has been fixed upstream
- maintaining the old bridge becomes more expensive than upgrading

Before upgrade:
- diff protobuf/model changes
- diff runtime actions
- diff player model behavior
- diff library and board semantics
- regenerate/update bridge code as required

After upgrade, regression-test:
- auth
- QR/token login path
- account restore
- board
- Continue Watching
- Discover
- Library
- addons
- metadata
- streams
- direct URL playback
- torrent playback
- progress sync
- next episode
- settings

---

## Product conclusions

### Safe to build immediately

- real Home shelves
- Continue Watching
- preview-driven hero after metadata mapping is fixed
- real Details
- real series seasons/episodes
- real streams
- real playback
- real Library
- real Discover
- addon management

### Build only after bridge work

- fast LocalSearch autocomplete
- Core-backed search history
- any Core model missing from generated protobuf output

### Build only after Core upgrade audit

- post-0.59 TV/live-guide features
- functionality whose required model/action only exists in newer Core

---

## Design guardrail

If a concept-screen element has no corresponding Stremio capability:

1. check current Android bridge
2. check pinned Core
3. check current upstream Core
4. classify it as:
   - currently available
   - bridge gap
   - future Core upgrade
   - unsupported / should not be invented

Do not silently substitute fake runtime data.

## PE0.1 — Android playback resolution and Core 0.59 conversion

Pinned Core 0.59 performs stream conversion synchronously while processing `ActionLoad.Player`. Its converted Ready payload is bridged back as a `Stream`; the streaming endpoint is exposed through Core-generated `deepLinks.externalPlayer.streaming`. Android playback must therefore wait for the requested `Player.selected` and its matching converted `Player.stream` Ready state. Matching checks stream source and payload, `streamRequest`, and `metaRequest`; computed deep links are not request identity. Old shared Player state is ignored, Loading continues, and conversion Error is surfaced safely.

The resolver subscribes to Player changes before dispatch and immediately reads post-dispatch state, so synchronous conversion is observed even when Core emits no later asynchronous event. A deterministic unit test covers that ordering.

The old Android `resolvePlayableUrl` could emit the original `directUrl(stream)` immediately from Flow `onStart`. Repository `.first()` could consume it before observing Core conversion. This is incorrect for URL `proxyHeaders`, Torrent tracker/file inclusion parameters, and other Core server transformations. Android now uses only the converted Ready stream and no longer reconstructs a Torrent or proxy URL. Raw fallback is disabled for all source types, including plain HTTP(S).

TV server-required classification is conservative for sources converted through the local server: Torrent, YouTube, archive formats, NZB, player-frame, URL proxy headers, and non-HTTP(S) URL schemes. Plain HTTP(S) without proxy headers does not require server startup. A 15-second timeout bounds Core resolution. The final-source S2E7 NoTorrent Direct was confirmed Core-converted (`Url`) with server not required; resolution was 105 ms and player load-call was 84 ms. It failed after remaining on Starting with the safe Exo category `ConnectTimeout`, without a first visual. Audited S2E7 Direct candidates had no proxy headers. For the selected Torrentio candidate: 0 announce entries, 0 `fileMustInclude` entries, `fileIdx` present, and `infoHash` present. Across 52 candidates, some announce lists had 26 or 27 entries; all `fileMustInclude` counts were zero. Server was Failed before and after startup; safe category was `NativeStartFailed` in 0 ms. `/settings` was not reached and Core conversion was not dispatched because the APK lacked `libstream_server.so`. Torrent conversion/runtime confirmation remains pending. See PE0.1 in `TV_PLAYBACK_EXPERIENCE_ROADMAP.md`.
