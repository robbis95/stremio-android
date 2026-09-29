# Smart Playback & Seamless Episodes — Feasibility Audit

## Phase 5C empirical stream audit (2026-09-29)

The TV source-selection runtime audit exercised Silo S2E7 through the pinned Core `MetaDetails` stream path. Three provider requests were observed: Local Files (without catalog support) errored with 0 streams, NoTorrent was ready with 19, and Torrentio was ready with 52 (71 results total). Torrentio results arrived before NoTorrent results. Five duplicate semantic keys were collapsed in the TV interaction snapshot, leaving 66 unique displayed options.

Aggregate source kinds: Direct 12, Torrent 52, External 2, YouTube 0, Archive 0, Other 0. Core hint/field coverage: `bingeGroup` 52, filename 52, `videoHash` 0, `videoSize` 0, `notWebReady` 0. The display-only conservative resolution parser recognized quality for 54 options. Seeds were available on 52 and size on 52. These observations support source-kind-aware parsing and confirm that torrent name/hint metadata is substantially richer than the direct/external sample from this episode; this is one real episode and should not be generalized into an addon-wide distribution. No raw URLs, signed URLs, or raw filenames are retained here.

The planned non-episodic runtime audit was not captured. The non-episodic target path is covered by pure tests and uses `videoId=null`, `guessStreamPath=true`; provider counts, source-kind distribution, and metadata coverage for a movie remain unknown. A naturally timed stale A→B stream race was also not observed, so stale rejection evidence is from semantic-target unit tests plus the actual S0E4 error and S2E7 success target audits.

Phase 5C only selects a source. Runtime and call-path review observed no URL resolution, Player load, playback start, or playback-history mutation.

Status: planning / feasibility only  
Audit date: 2026-09-29  
Canonical branch: `feat/android-tv`  
App baseline inspected: `9a99ba7283964b4a5a07998698ab07012064feaf`  
Pinned Stremio Core baseline: `90c38f181d290fc705049e4c8bd30df00f6f3e66` (Core 0.59.0)  
Android Media3 dependency: `1.10.1`

This document investigates four related features for the Android TV client:

1. Smart Play
2. Smart Fallback
3. Episode Continuity
4. Seamless Episodes / next-episode preloading

It is an architecture and feasibility audit only. It does not authorize implementation.

The broader production sequencing, research classification, Skip Segments design, and shared Playback Experience guardrails are maintained in `TV_PLAYBACK_EXPERIENCE_ROADMAP.md`.

The design goal is to make normal playback require fewer manual source choices while keeping the ordinary Stremio stream picker available at all times.

No external AI service, proprietary server, or special addon is required for the baseline design.

---

## 1. Executive conclusion

All four ideas are feasible, but they should not be implemented as one large feature.

The current app already contains several useful foundations:

- parsed stream quality/seeds/size metadata
- user preferred quality
- remembered local stream selections
- torrent-health monitoring
- automatic fallback to another stream for unhealthy torrents
- Stremio Core `nextVideo`
- Stremio Core's built-in next-episode stream prefetch and `bingeGroup` matching
- Media3 / ExoPlayer 1.10.1
- external subtitle MediaItem configuration
- audio/subtitle track state
- playback timing callbacks

The major architectural gaps are:

- stream metadata is not rich/normalized enough for robust Smart Play scoring
- fallback is currently narrow and torrent-oriented
- the Kotlin bridge does not expose all useful Core next-stream state
- resolving a future stream through the current Android API mutates the single Core Player model
- `PlaybackManager.load()` destroys the current player and creates a new one for every stream
- `ExoStreamPlayer.load()` replaces its single MediaItem
- playlist/media-item transition synchronization with Stremio Core does not exist yet
- MPV does not share ExoPlayer's Media3 playlist-preloading path

Therefore the recommended direction is:

1. build a pure local stream metadata parser and deterministic ranker
2. integrate Smart Play without removing manual source selection
3. generalize fallback into an attempt coordinator
4. consume Core's existing binge/next-video behavior instead of duplicating it
5. expose missing next-stream information through the bridge if needed
6. make ExoPlayer long-lived and playlist-capable
7. add conservative Media3 playlist preloading
8. synchronize Media3 media transitions back into Stremio Core
9. add resource-aware torrent/debrid preloading only after direct-source playback is proven

---

## 2. Current Android playback architecture

Relevant files:

- `app/src/main/java/com/stremio/mobile/presentation/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/stremio/mobile/data/repository/PlaybackRepository.kt`
- `app/src/main/java/com/stremio/mobile/player/PlaybackManager.kt`
- `app/src/main/java/com/stremio/mobile/player/Player.kt`
- `app/src/main/java/com/stremio/mobile/player/ExoStreamPlayer.kt`
- `app/src/main/java/com/stremio/mobile/player/MpvStreamPlayer.kt`
- `app/src/main/java/com/stremio/mobile/core/StremioCore.kt`
- `app/src/main/java/com/stremio/mobile/data/model/StreamOption.kt`
- `app/src/main/java/com/stremio/mobile/data/model/CorePresentationMappers.kt`
- `app/src/main/java/com/stremio/mobile/core/utils/StreamParser.kt`
- `app/src/main/java/com/stremio/mobile/data/model/StreamSortCriterion.kt`
- `app/src/main/java/com/stremio/mobile/data/model/LocalStreamSelection.kt`
- `app/src/main/java/com/stremio/mobile/data/repository/AuthRepository.kt`

Current user-selected playback path:

`StreamOption -> MainViewModel.playStream() -> proceedWithPlayback() -> PlaybackRepository.resolveAndLoadStream() -> StremioCore.resolvePlayableUrl() -> PlaybackManager.load() -> PlayerFactory -> ExoStreamPlayer/MPV`

For ExoPlayer, `ExoStreamPlayer.load()` currently:

1. builds one MediaItem
2. calls `exoPlayer.setMediaItem(...)`
3. calls `prepare()`

At the manager level, `PlaybackManager.load()` currently releases any active Player instance and creates a new one.

This is compatible with ordinary one-stream-at-a-time playback but is not compatible with seamless next-episode playlist transitions.

---

## 3. How the next episode is identified today

The Android client does not calculate the next episode itself during playback.

`PlaybackRepository.getNextVideo()` delegates to:

`StremioCore.getNextVideo() -> Core Player.nextVideo`

The pinned Stremio Core Player model computes `next_video` from:

- the selected current stream request / video ID
- the loaded MetaItem
- `MetaItem.next_video(video_id)`

Therefore Core remains the correct source of truth for next-episode identity.

Android should not duplicate season/episode arithmetic for playback progression.

---

## 4. Core already prefetches next-episode streams

This is the most important finding in the audit.

The pinned Core Player contains internal state:

- `next_video`
- `next_streams`
- `next_stream`

When a current series item is selected, Core:

1. identifies the next video
2. clones the current stream request
3. changes that request's video ID to the next video's ID
4. requests streams for that next video
5. waits for the result
6. attempts to find a stream that is a binge match for the current stream

The matching rule in the pinned Core is:

`Stream.is_binge_match(other)`

which compares:

`behaviorHints.bingeGroup`

for equality.

If Core finds a matching stream, it inserts that stream into the next video's `streams` list.

Implication:

**basic Episode Continuity already exists in Stremio Core.**

The Android client should consume and extend this behavior rather than reimplementing the same addon request path.

---

## 5. Current Android code unnecessarily re-fetches next streams

The current `MainViewModel.playNextVideo(video)` does not use the Core-prefetched binge candidate as its primary path.

Instead it:

1. calls `reportNextVideo()`
2. requests `MetaDetails` for the next video ID
3. waits for addon streams
4. maps them to StreamOptions
5. prefers a stream from the same `addonTitle` as the previous stream
6. otherwise selects the first returned stream
7. starts playback

This duplicates work Core may already have performed.

It also treats matching `addonTitle` as stronger than the standardized `bingeGroup` signal.

Recommended future order:

1. use a Core-provided exact binge candidate when available
2. otherwise use Episode Continuity similarity scoring
3. otherwise use ordinary Smart Play ranking
4. retain manual source selection as fallback/user choice

---

## 6. Kotlin bridge limitation

The Rust Core Player contains `next_streams` and `next_stream`, but the current Kotlin protobuf `Player` does not expose those fields directly.

The current bridge does expose:

- `next_video`

and the protobuf `Video` model contains:

- `repeated Stream streams`

Because Core copies the binge-matched stream into `next_video.streams`, Android may already be able to consume the strongest candidate from `nextVideo.streams`.

This should be verified with a real addon/series before changing the protobuf.

If direct access to the complete prefetched candidate set is needed, exposing `next_streams` and/or `next_stream` through the bridge is a reasonable targeted bridge enhancement.

Do not create a separate Android addon-fetch implementation merely to bypass this omission.

---

## 7. Stream data currently available to Smart Play

`StreamOption` currently preserves:

- key
- name
- description
- addonTitle
- quality
- CoreStream
- seeds
- size
- origin
- cleanDescription
- bingeGroup
- notWebReady
- filename
- videoSize
- videoHash

The underlying Core Stream also preserves source semantics and subtitles.

This is enough for a first deterministic ranker, but not enough for the desired final Smart Play quality.

---

## 8. Current stream parsing is intentionally basic

`StreamParser.kt` currently extracts from description text:

- seeds
- size
- origin

The current mapper identifies quality only by looking for:

- 2160p
- 4k
- 1080p
- 720p
- 480p

in `stream.name`.

The desired Smart Play system needs a richer, testable parser across:

- stream.name
- stream.description
- behavior-hint filename
- structured fields when available

Candidate metadata:

- resolution
- video codec
- HDR10
- HDR10+
- Dolby Vision
- HLG
- audio codec
- Atmos
- channel layout / 5.1 / 7.1
- release source
- release type
- release group
- language tags
- file size
- bitrate when actually available or safely derivable

Likely deterministic tokens include variants of:

- `2160p`, `1080p`, `720p`
- `DV`, `DoVi`, `Dolby Vision`
- `HDR`, `HDR10`, `HDR10+`
- `HEVC`, `H265`, `x265`, `AV1`, `AVC`, `H264`
- `TrueHD`, `Atmos`, `DD+`, `EAC3`, `DTS-HD`
- `REMUX`, `BluRay`, `WEB-DL`, `WEBRip`

Parsing must be deterministic and confidence-aware.

Unknown metadata must remain unknown rather than being treated as bad.

---

## 9. Cached/debrid status

There is no universal Stremio stream field that guarantees a generic:

`cached = true/false`

across all addons.

Some addons encode service/cache status in:

- name
- description
- provider-specific text
- source behavior

Smart Play may use cache/debrid information only when it can be identified with sufficient confidence.

The ranker must not depend on provider-specific strings for baseline correctness.

Do not hard-code one popular addon as the canonical Stremio stream format.

---

## 10. Device capability model

Smart Play should score against the actual playback device.

Android can inspect:

- display HDR capabilities
- MediaCodec decoder support
- hardware/software codec status on supported API levels
- display resolution/modes
- audio output capabilities
- current Stremio hardware decoding / passthrough / surround settings

Create a small immutable Android-side model such as:

`DevicePlaybackCapabilities`

Potential fields:

- max practical video dimensions
- HEVC support
- AV1 support
- AVC support
- VP9 support
- HDR10
- HDR10+
- Dolby Vision
- HLG
- decoder hardware acceleration where known
- supported audio encodings / channel count where available

Do not infer these from TV model-name lists.

Compatibility must have higher priority than cosmetic quality scoring.

A stream that cannot be decoded reliably should normally be rejected or heavily penalized rather than being chosen because it says "4K".

---

## 11. User preference model

Useful existing Stremio/Core settings include:

- audio language
- secondary audio language
- subtitle language
- secondary subtitle language
- hardware decoding
- audio passthrough
- surround sound
- binge watching
- player type

The Android app also stores:

- preferred quality
- minimum seed threshold
- minimum download speed
- auto-switch-on-dead-stream

Future Smart Play-specific preferences could remain Android-local if they are genuinely client preferences, for example:

- prefer highest compatible quality
- prefer HDR / prefer SDR
- prefer Dolby Vision when compatible
- prefer Atmos
- prefer smaller/larger files
- preferred release type
- Smart Play enabled
- Smart Fallback enabled

Do not overload Stremio profile fields whose semantics do not match.

---

## 12. Recommended ranking architecture

Do not scatter ranking conditions through MainViewModel.

Create a pure deterministic layer, conceptually:

`StreamMetadataParser`

`StreamCandidateMetadata`

`DevicePlaybackCapabilities`

`SmartPlaybackPreferences`

`StreamReliabilitySnapshot`

`EpisodeContinuityFingerprint`

`SmartStreamRanker`

A candidate score should be explainable.

Recommended score families:

### Compatibility

Examples:

- unsupported codec
- unsupported HDR format
- source unavailable to selected player engine
- Android-TV external URL availability
- `notWebReady` where relevant to selected pipeline

Compatibility can hard-reject impossible candidates.

### User preference

Examples:

- preferred quality
- preferred audio language
- preferred HDR behavior
- preferred release type

### Quality

Examples:

- resolution
- HDR/DV
- audio format
- estimated bitrate/file-size suitability

### Continuity

Examples:

- exact `bingeGroup`
- same addon/provider
- same release group
- same release source/type
- same resolution
- same HDR family
- same codecs/audio profile
- similar filename structure

### Reliability

Examples:

- recent resolve success rate
- recent startup success rate
- time-to-first-frame moving average
- recent provider failures
- torrent health / seeds when meaningful

### Efficiency

Examples:

- unreasonable file size for connection/profile
- unnecessary resolution above device capability
- metered network penalties

The exact weights must be tuned from real streams, not invented once and treated as permanent truth.

The ranker should return both:

- total rank
- structured reasons

so the UI/debug tooling can explain why a candidate won.

---

## 13. Smart Play UI contract

The normal user path should eventually become:

`Play`

with a short selected-stream summary such as:

`4K · Dolby Vision · Atmos`

and a secondary action:

`Choose another source`

The stream list must remain available.

Smart Play must not remove user control.

When metadata confidence is low, the UI should avoid claiming features that were only guessed.

---

## 14. Smart Fallback already partially exists

Current `MainViewModel.startHealthWatch()` monitors torrent stream health using the local streaming server.

It considers:

- peers
- download speed
- stream progress

After a sustained unhealthy period, the current implementation can call:

`playNextBestStream()`

The current fallback rank is narrow:

1. seed count
2. preferred-quality closeness

This is useful proof that automatic fallback already exists conceptually in the app.

It should be generalized rather than duplicated.

---

## 15. Recommended fallback architecture

Introduce a playback-attempt coordinator separate from the ranker.

Conceptually:

`PlaybackAttemptCoordinator`

State:

- ranked candidates
- attempted stream keys
- current candidate
- attempt count
- start timestamp
- resolve state
- startup state
- stable-playback flag

Candidate progression:

`candidate A -> failure -> candidate B -> failure -> candidate C -> stable playback`

Never allow retry cycles such as:

`A -> B -> A -> B`

unless a deliberate user retry resets the attempt session.

---

## 16. Distinguish failure phases

Fallback must not treat every buffering event as a failed source.

Recommended phases:

- resolving
- preparing
- waiting-for-first-frame
- stable playback
- degraded
- failed

Automatic fallback is appropriate for strong failures such as:

- URL cannot be resolved
- explicit HTTP/source failure
- fatal decoder error
- startup timeout with no first frame
- dead torrent before meaningful playback begins
- known unavailable provider response

Fallback should be conservative after stable playback has been established.

A temporary network stall 40 minutes into a movie should not silently switch the user to a different release after a few seconds.

---

## 17. Local reliability history

A fully local history store is feasible.

Useful dimensions:

- addon/provider
- source kind
- perhaps player engine
- perhaps coarse quality family

Useful metrics:

- recent attempts
- recent successes
- consecutive failures
- exponentially weighted resolve latency
- exponentially weighted time-to-first-frame
- last failure time
- last success time

Do not persist raw signed stream URLs.

Do not permanently blacklist a provider.

Use time decay so old failures lose influence.

This can be stored locally without a backend.

---

## 18. Episode Continuity ranking

Recommended priority:

### Tier 1 — exact Core binge match

If Core supplies a next-video stream whose `bingeGroup` matches the currently selected stream, treat it as the strongest continuity signal.

### Tier 2 — local similarity

If no exact binge match is available, compare candidate metadata to a fingerprint of the current stream.

Potential fingerprint:

- addon
- bingeGroup
- resolution
- release group
- release source/type
- HDR/DV
- video codec
- audio codec
- audio features
- language
- filename pattern

### Tier 3 — ordinary Smart Play

If continuity confidence is low, use the ordinary ranker.

Continuity must not override hard device incompatibility.

---

## 19. Why addon title alone is not enough

Current next-episode Android logic prefers the same `addonTitle`.

That is a useful weak signal, but it does not guarantee:

- same release
- same quality
- same source
- same codec
- same audio
- same cached/debrid state

A single addon can return many releases.

`bingeGroup` is the stronger protocol-level signal where present.

---

## 20. When Core requests next streams

For the pinned Core, next-stream loading is tied to the active Player model rather than a "90 seconds remaining" threshold.

Once the selected stream and metadata are available, Core can identify the next video and issue the corresponding next-stream request.

Therefore the proposed client timeline should take advantage of work already done early.

Suggested conceptual timeline:

### Playback begins

Core:

- loads current metadata/player state
- identifies next video
- requests next streams based on current stream request
- attempts exact bingeGroup match

### Approximately 90 seconds remaining

Client:

- inspect whether Core already has a strong candidate
- if not, optionally prepare broader candidate collection/ranking

### Approximately 30 seconds remaining

Client:

- resolve selected future stream if safe
- prepare next MediaItem and subtitles

### Near end

Media3:

- queue next item
- preload a small amount only when resource policy allows

The exact time thresholds must be configurable/tested rather than treated as protocol requirements.

---

## 21. Current stream resolution is unsafe for future-item preload

`StremioCore.resolvePlayableUrl(option)` constructs:

`Player.Selected(...)`

and dispatches:

`ActionLoad.Player(selected)`

to the single Core Player model.

That is correct for the stream that is about to become active.

It is not a safe generic future-stream resolver while the current episode is still playing because it mutates Core Player state for:

- current selected stream
- metadata
- subtitles
- library/progress context
- next-video state
- analytics/trakt context

Therefore Seamless Episodes must **not** simply call the current `resolvePlayableUrl()` for E05 while E04 is still active.

---

## 22. Streams that can be prepared without replacing Core Player state

Some sources can be mapped without a Player load.

### Direct URL

The Android wrapper can read a direct URL from suitable stream sources without loading Core Player.

### Local-server/torrent URL

For the existing Tramvai/torrent bridge representation, Android already knows the local streaming-server URL format.

This may allow preparation without replacing the Core Player model.

However, initiating next-torrent work may create significant network/disk/CPU competition.

Use conservative policy.

### Complex/proxied/archive/NZB sources

These may require Core/stream-server conversion behavior that should not be duplicated in Android.

For full source compatibility, a future stateless stream-resolution/conversion API in Core or the bridge is the cleaner solution.

---

## 23. Recommended Core/bridge enhancement for seamless playback

Investigate a narrowly scoped API that can convert/resolve a stream for future playback **without selecting it as the active Core Player item**.

Conceptually:

`resolveStreamForPreload(stream, request/context) -> resolved source`

The exact API must follow Core architecture; do not add this name blindly.

Requirements:

- must not mutate active Player.selected
- must not reset active subtitle/player/library state
- must reuse official Core conversion semantics
- must support the same source types as normal playback
- should remain cancellable
- should not introduce a new network service

If existing Core code already has a reusable pure conversion path, prefer exposing/reusing it over adding duplicate logic.

---

## 24. ExoPlayer architecture blocks true seamless episodes today

`PlaybackManager.load()` currently:

- releases the previous Player
- creates a new Player
- loads the URI
- starts playback

`ExoStreamPlayer.load()` currently:

- calls `setMediaItem()`
- calls `prepare()`

This guarantees a teardown/setup boundary between episodes.

For seamless episodes, ExoPlayer should become a long-lived playback session capable of holding:

- current MediaItem
- next MediaItem

and transitioning within one ExoPlayer instance.

---

## 25. Media3 playlist preloading is a strong fit

The app already uses Media3 ExoPlayer 1.10.1.

Media3 supports playlist preloading through:

`ExoPlayer.setPreloadConfiguration(...)`

with:

`ExoPlayer.PreloadConfiguration(targetPreloadDurationUs)`

A target such as 3–5 seconds maps directly to the product goal.

This approach is a better first fit for predictable episode order than building a custom buffer mechanism.

Media3's default loading behavior is designed so preloading does not normally compete with loading required for active playback.

This should still be tested on low-memory/low-bandwidth TV hardware.

---

## 26. PreloadConfiguration vs DefaultPreloadManager

For linear episode playback, first evaluate the simpler playlist-preloading API.

Preferred first experiment:

- one long-lived ExoPlayer
- current + next MediaItem playlist
- `PreloadConfiguration` of a small target duration

`DefaultPreloadManager` is more useful when the app manages a larger ranked collection of potential next media items, such as a carousel/feed or multiple candidates.

It may become relevant for broader future predictive playback, but it is not required for a first seamless episode implementation.

---

## 27. Subtitle preparation

`ExoStreamPlayer.buildMediaItem()` already supports:

`MediaItem.SubtitleConfiguration`

for addon-provided external subtitles.

Therefore a future next-episode MediaItem can carry its subtitle configurations before transition.

Important caveat:

current Core subtitle discovery is tied to active Player state and selected stream/video parameters.

Full prefetch of subtitle-addon results may therefore depend on:

- whether the selected stream already embeds useful subtitle metadata
- whether next-stream video hash/size/filename is known
- whether bridge/Core can request next subtitles without replacing active Player state

A first seamless implementation can still:

- attach stream-provided subtitle entries
- apply language preference
- load additional addon subtitle results after transition if necessary

Do not delay all playback waiting for subtitle addons.

---

## 28. Audio/subtitle language selection

Core profile settings already include:

- primary audio language
- secondary audio language
- primary subtitle language
- secondary subtitle language

Current Exo code builds external subtitle MediaItems with a default selection flag based on preferred subtitle language.

For seamless playlist transitions, prefer language-based track-selection policy over preserving raw track IDs because track IDs/group indexes can change between files.

Future Exo work should evaluate global `TrackSelectionParameters` for:

- preferred audio language(s)
- preferred text language(s)
- text-role / forced-subtitle behavior where appropriate

Manual track selection should still override automatic preference for the current playback session where practical.

---

## 29. Core synchronization across an Exo playlist transition

This is a critical correctness requirement.

Today the app treats each episode as a separate explicit load.

With a persistent Exo playlist, the player may transition to the next MediaItem without the old `STATE_ENDED -> destroy/reload` flow.

Android must listen to Media3 item transitions and synchronize Stremio Core so the active Core Player context advances to the new episode.

The transition path must preserve:

- watched/progress semantics
- Continue Watching state
- library video ID
- Trakt events
- analytics context
- next-video calculation
- subtitle state
- stream-state memory

Do not implement visually seamless Exo playback while leaving Core thinking the previous episode is still active.

This synchronization is likely the hardest correctness problem in the Seamless Episodes work.

---

## 30. Player engine scope

Media3 playlist preloading applies to ExoPlayer.

The current app also supports MPV.

Recommended product behavior:

### ExoPlayer

Eligible for the full Seamless Episodes pipeline.

### MPV

Initially keep ordinary next-episode switching unless a safe MPV playlist/cache strategy is separately validated.

Smart Play, Smart Fallback and Episode Continuity can still work with MPV even if seamless preloading is Exo-only.

Do not force users away from MPV solely to advertise seamless playback.

The UI may treat Seamless Episodes as an engine capability.

---

## 31. Resource policy

Do not start large future downloads simply because a next episode exists.

Recommended staged work:

### Early

Cheap:
- next-video identity
- Core's existing next-stream request
- metadata parsing/ranking

### Later

Moderate:
- future stream resolution when safe
- manifests/track metadata

### Near transition

Potentially expensive:
- small Media3 preload target

Abort all future-work when:

- user leaves player
- user manually chooses another episode
- selected next candidate changes
- connection becomes unsuitable
- current playback becomes unhealthy
- resource policy disables preloading

---

## 32. Network/resource gates

Future preload policy should consider:

- metered/cellular network
- current active-player buffer health
- current bandwidth/load
- device memory class
- source type
- torrent/server health
- selected player engine
- battery is less relevant for fixed TVs but cannot be assumed for all Android devices

Do not use emulator behavior as the final resource baseline.

Real low-end Android TV hardware is required before enabling aggressive preload defaults.

---

## 33. Avoid two competing torrent sessions by default

Torrent playback is special.

Current episode:

- may be actively downloading through the local streaming server

Next episode:

- may require another torrent/file/index or another torrent entirely

Preloading the next torrent too early can reduce current playback quality.

Initial torrent policy should be conservative, for example:

- allow Core to fetch next stream metadata immediately
- do not start next torrent data transfer while current playback is struggling
- prefer a very small/late preload window
- consider same-torrent/different-file cases separately from different-torrent cases
- abort immediately on current-buffer deterioration

Do not copy direct HTTP preload policy blindly to torrents.

---

## 34. Smart Play can be implemented entirely locally

The following require no external server:

- stream text/filename parsing
- device capability detection
- scoring
- user preference weighting
- local reliability history
- candidate selection
- attempt/fallback state
- Episode Continuity similarity
- Media3 playlist construction
- Media3 preload policy

Addon requests continue to flow through Stremio Core.

---

## 35. What likely requires Core/bridge work

Potential bridge/Core tasks:

1. verify/use `nextVideo.streams` as the exact binge candidate
2. optionally expose `next_stream`
3. optionally expose `next_streams`
4. expose or add stateless stream conversion/resolution for a future item
5. potentially expose future-subtitle querying if required for full pre-transition subtitle preparation

Do not modify Core before proving each gap is real.

---

## 36. Minimum viable implementation sequence

### SP0 — audit/instrumentation

No behavior change.

- collect representative stream metadata from several addons
- document source shapes
- verify `nextVideo.streams` with real binge-compatible series
- measure current resolve/startup time
- define first-frame metric

### SP1 — metadata parser

Pure Kotlin.

- richer deterministic parsing
- tests using anonymized representative samples
- no auto-selection

### SP2 — ranker

Pure Kotlin.

- compatibility
- preference
- quality
- continuity
- reliability inputs
- structured scoring reasons
- extensive unit tests

Still no auto-play behavior.

### SP3 — Smart Play

- Play uses top compatible ranked candidate
- Choose another source remains available
- manual source choice overrides automatic choice

### SP4 — Smart Fallback

- attempt session
- resolve/startup failure classification
- no retry loops
- conservative post-start behavior
- merge existing torrent-health fallback

### SP5 — Core Episode Continuity integration

- prefer exact Core binge match
- verify `nextVideo.streams`
- similarity rank only if no exact match
- remove same-addon-as-primary heuristic

### SP6 — bridge preparation

Only if needed:

- next-stream exposure
- stateless future stream resolution

### SP7 — persistent Exo session

- stop recreating ExoPlayer for every sequential episode
- preserve normal one-item playback behavior
- add explicit playlist-capable API below ViewModel

### SP8 — direct-source seamless prototype

- two-item Exo playlist
- current + next
- 3–5 second `PreloadConfiguration`
- MediaItem transition callback
- Core transition synchronization
- external subtitle config
- no torrent preload initially

### SP9 — resource-aware torrent/debrid support

- streaming-server health
- safe thresholds
- cancellation
- real-device stress testing

### SP10 — production tuning

- latency metrics
- failure metrics
- low-end TV tests
- memory
- bandwidth
- settings/defaults
- manual override UX

---

## 37. Test matrix

Smart Play:

- direct HTTP
- HLS
- torrent/local server
- external Android-TV source
- multiple addons
- missing metadata
- conflicting metadata
- unsupported codec
- HDR display mismatch
- manual override

Fallback:

- resolution failure
- direct HTTP failure
- decoder failure
- first-frame timeout
- dead torrent
- slow torrent
- transient buffering after stable playback
- all candidates fail
- user cancels during fallback

Continuity:

- exact bingeGroup match
- no bingeGroup
- same addon / different release
- different addon / same release characteristics
- season boundary
- final episode
- skipped episode
- user manually chooses source on E05

Seamless:

- E04 -> E05 direct HTTP
- subtitles on both
- different audio-track layouts
- HDR/SDR transition
- quality/codec transition
- seek near episode end
- skip-next action
- Back while next item is preloading
- app background/foreground
- current source fails while next is preloading
- MPV selected
- metered network
- low-memory TV
- torrent same infohash
- torrent different infohash

---

## 38. Success metrics

Measure before and after.

Useful metrics:

- Play press -> source selected
- resolve latency
- player prepare latency
- first-frame latency
- Smart Play manual override rate
- automatic fallback success rate
- false fallback rate after stable playback
- current -> next episode visual gap
- current -> next episode audio gap
- memory during preload
- current playback rebuffer rate while preloading

A "seamless" implementation should be evaluated from measured transition latency, not only visual impression.

---

## 39. Non-goals

Do not:

- require OpenAI/Gemini/LLM calls
- create a mandatory backend
- create a mandatory companion addon
- bypass Stremio Core to call installed addons directly
- hide the normal stream list
- assume one provider/addon
- permanently blacklist providers
- aggressively preload full episodes
- preload torrents at the cost of current playback
- mutate active Core Player state merely to resolve a future stream
- make MPV pretend to support Media3 preloading
- implement ranking inside Compose UI
- grow MainViewModel into the entire smart-playback subsystem

---

## 40. Decision points before implementation

Before SP1 begins, verify:

1. representative real-world stream strings from installed addons
2. exact Android device capability APIs needed by target API range
3. whether `nextVideo.streams` reliably carries the Core binge match through the Kotlin bridge
4. whether Core 0.63.x materially improves next-stream/preload-relevant APIs compared with pinned 0.59
5. whether a stateless conversion hook already exists internally and can be exposed safely
6. how player transition callbacks should advance Core without double-reporting `Ended`/`NextVideo`
7. how audio/subtitle manual overrides should propagate to the next playlist item
8. whether seamless playback should default on only for ExoPlayer-capable sources initially

---

## 41. Recommended first coding task

Do **not** start by changing playback.

The first implementation task should be SP0/SP1:

- collect/fixture representative stream metadata
- introduce a pure deterministic `StreamCandidateMetadata` parser
- add unit tests
- do not automatically select anything

This creates the foundation for Smart Play, Episode Continuity and explainable ranking while carrying almost no playback risk.

The existing TV navigation/Home milestone should be allowed to reach its own gate before this future playback track begins.
