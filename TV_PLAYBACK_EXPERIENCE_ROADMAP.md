# TV Playback Experience Roadmap

Status: active implementation / architecture guardrail
Date: 2026-10-02
Canonical branch: `feat/android-tv`

This document consolidates the playback-oriented research track, the existing Smart Playback feasibility audit, and the planned generic Skip Segments system.

It is a decision framework, not an instruction to implement all systems at once.

The core principle is:

> prepare early when mistakes are cheap; commit late when mistakes affect playback, history, or user state.

The TV client should feel one step ahead without duplicating Stremio Core, destabilizing focus, blocking playback on optional services, or introducing mandatory AI/backend dependencies.

---

## 1. Playback Experience system

Long-term architecture:

`PlaybackExperienceCoordinator`

- Smart Playback
  - stream metadata parsing
  - compatibility and preference ranking
  - local reliability history
- Playback Attempt / Recovery
  - resolve/startup attempts
  - retry / re-resolve / fallback
  - no retry loops
- Segment Coordinator
  - Core intro/outro provider
  - optional external/community providers
  - resolver and confidence
  - skip action selection
- Episode Continuity
  - Core `bingeGroup`
  - current-stream fingerprint
  - next-episode candidate ranking
- Next Episode Transaction
  - prepared vs committed state
  - future stream/media preparation
- Playback Language Intent
  - semantic audio/subtitle intent
  - not file-specific track IDs
- Seamless Episode Coordinator
  - persistent Exo session
  - next MediaItem
  - bounded preload
  - Core transition synchronization
- Resource Governor
  - low-RAM / memory pressure
  - playback buffer health
  - network headroom
  - source type / player engine
  - speculation/preload budget

These are logical responsibilities. Do not create one giant class or prematurely implement every abstraction.

---

## 2. Research classification

### ADOPT

Use as durable product/architecture principles:

- interaction-stable lists: late results must not move the user's active target
- semantic identity and focus restoration
- prepare early / commit late
- playback progress/history must not be mutated for speculative future playback
- semantic audio/subtitle intent rather than persisting only file-specific track IDs
- measured recovery ladder instead of making the user manually retry every transient failure
- device/resource headroom should govern speculative work
- measure TTFF, episode-transition latency, recovery success, unnecessary work, keypress count, focus stability, rebuffering, and memory
- optional work must never block normal playback

### ADAPT

Useful ideas that must fit Stremio/Core rather than replace it:

- Request Broker: only for Android-owned speculative/preparation work where a real duplication/cancellation problem is demonstrated; do not proxy all Core addon/resource semantics through a second orchestration layer
- Priority Scheduler: introduce only when multiple client-owned background tasks actually compete
- Playback Orchestrator: split into small playback-experience coordinators around the existing Core/repository/player boundaries
- NextEpisodeTransaction: consume Core next-video / next-stream behavior; do not rediscover the next episode independently
- focus-driven speculation: cheap already-loaded preview/image work is safe; metadata/stream requests on focus require an isolated Core-safe prefetch path before adoption

### VERIFY BEFORE BUILDING

- immutable Home snapshot / stale-while-revalidate startup
- player/network warm pipeline
- exact Media3 preload API and behavior on the app's pinned Media3 version
- low-end TV memory and network budgets
- current refresh-rate switching order and device behavior
- Core skip-segment accuracy on actual played releases
- external segment provider contracts, availability, rate limits, licenses, identifier requirements, and duration matching
- stateless future-stream resolution without mutating active Core Player state

### DEFER / EXPERIMENT

- local route/navigation predictor
- hedged/source racing
- aggressive focus-driven stream preparation
- automatic skip behavior
- community feedback/write-back for segment timing
- learned segment-offset corrections
- broad multi-candidate media preloading

These should be feature-flagged experiments only after baseline playback metrics exist.

### REJECT AS BASELINE ARCHITECTURE

- bypassing Stremio Core to call installed addons directly
- a mandatory external AI/LLM dependency
- a mandatory project-owned server for core playback behavior
- metadata/stream fetch on every poster focus
- one-provider-specific segment architecture
- auto-skipping low-confidence community timestamps
- permanent provider/device blacklists based on a few failures
- progress/Continue Watching mutation for a merely prepared next episode

---

## 3. Verified current Stremio Core segment capability

Pinned official Core:

`Stremio/stremio-core @ 90c38f181d290fc705049e4c8bd30df00f6f3e66`  
Core release: 0.59.0

The Core Player already contains:

- `intro_outro`
  - optional intro
    - `from`
    - `to`
    - optional source-duration information
  - optional outro timestamp
- internal skip-gap request/cache state
- seek history used for skip-gap learning/reporting

The Kotlin protobuf already exposes:

`Player.IntroOutro`
- `intro`
- `outro`

and:

`Player.IntroData`
- `from`
- `to`
- `duration`

Therefore Android can potentially consume the current Core intro/outro result without first modifying the bridge.

### Core 0.59 matching behavior

For supported series playback, Core can request skip-gap data using stream/video identity that includes:

- series identity / series info
- OpenSubtitles/video hash when available
- a SHA-256-derived stream-name hash

Core then compares available gap records against the current library/player duration and selects the closest duration.

For intro, it scales the matched seek interval according to the ratio between current duration and the matched source duration.

For outro, it adjusts the matched outro timestamp against current duration.

This is materially stronger than a fixed:

`episode ID -> one timestamp`

mapping.

### Important limitations

The pinned implementation requests Stremio skip-gap data only when all required context is present and the account has active Premium status.

Supported source conditions in the pinned implementation also constrain when this path is available.

Therefore Core should be treated as a high-value SegmentProvider when data exists, not as the only possible source for the generic TV segment system.

---

## 4. Generic Skip Segments model

The player UI must not depend on provider-specific schemas.

Normalize to:

`SegmentType`
- Recap
- Intro
- Credits
- Preview

Potential future types may be added only when a provider and UI behavior are clearly defined.

`SegmentCandidate`
- type
- startMs
- endMs
- providerId
- providerConfidence
- sourceDurationMs?
- content identity
- stream identity/fingerprint evidence
- raw provider evidence needed for diagnostics

`ResolvedSegment`
- type
- startMs
- endMs
- confidence
- evidence summary
- resolution strategy
- provider IDs that contributed

The player receives only normalized resolved actions.

---

## 5. Segment query identity

Do not assume IMDb alone is sufficient.

A query should preserve as much verified identity as is available:

Content:
- Stremio/meta type
- Stremio/meta ID
- external IDs such as IMDb/TMDB only when actually available
- season
- episode / video ID

Actual playback:
- runtime `durationMs`
- stream filename
- video hash
- video size
- addon/provider identity
- release fingerprint when Smart Playback parsing can derive one safely
- player engine where relevant

This identity model should eventually be shared with Smart Play / Episode Continuity rather than duplicated.

---

## 6. Segment providers

Provider interface concept:

`SegmentProvider.load(query) -> segment candidates`

Initial provider priority:

1. Stremio Core provider using `Player.introOutro`
2. local cache
3. optional external/community providers after separate verification
4. future local/community-derived providers only if explicitly approved

Candidate external providers currently under consideration:

- SkipDB
- IntroDB
- TheIntroDB

Do not integrate any external provider until its current API, license, availability, identifiers, matching semantics, and rate limits are verified.

No provider may become mandatory for playback.

---

## 7. Segment resolver

`SegmentResolver` owns confidence and normalization.

Inputs may include:

- provider confidence
- exact/near playback-duration match
- source-duration delta
- stream/release fingerprint match
- agreement between multiple providers
- whether the result comes from Core's stream-aware match
- segment bounds sanity
- overlap/conflict with other segment types

V1 policy:

- only high-confidence resolved segments create a button
- medium/low-confidence results are ignored
- no auto skip
- missing/failed lookup means normal playback with no segment action

Prefer a false negative over skipping actual content.

The confidence model should be explicit and testable, not an opaque ML score.

---

## 8. Segment cache

Cache must be release/duration aware.

Do not key only by:

`series + season + episode`

Prefer a compound identity using:

- content/video identity
- duration or duration bucket
- stream fingerprint fields when available
- provider/version context where necessary

Never persist short-lived signed playback URLs as the identity.

Cache writes should not delay playback.

---

## 9. Playback action arbitration

Skip Segments and Next Episode are one playback-experience surface.

The player should receive one prioritized contextual action model rather than independent buttons fighting for attention.

Conceptual priority examples:

- recap active -> Skip recap
- intro active -> Skip intro
- credits active + no ready next episode -> Skip credits
- credits active + next episode ready -> Next episode
- preview active -> Skip preview or Next episode depending on next-episode state

Exact policy must be tested with the production player UI.

Do not show multiple equally prominent overlapping actions without a deliberate interaction design.

---

## 10. V1 Skip Segments behavior

V1:

- consume high-confidence segment data
- show contextual skip button
- user explicitly activates it
- call existing player `seekTo(endMs)`
- no auto skip
- no playback blocking
- no provider error surfaced unless diagnostically useful
- local cache
- conservative timeout/cancellation
- no external provider required for initial Core-only POC

Current player abstraction already supplies:

- `positionMs`
- `durationMs`
- `bufferedPositionMs`
- `seekTo(positionMs)`

so the hard problem is segment identity/resolution, not seeking.

---

## 11. Later Skip Segments behavior

Only after V1 accuracy is measured:

Global preference:
- Off
- Show buttons
- Auto skip

Possible later per-type controls:
- Recap
- Intro
- Credits
- Preview

Each may eventually support:
- Off
- Button
- Auto

Auto behavior must require a stricter confidence threshold than button behavior.

---

## 12. Relationship to Episode Continuity

Desired end-to-end playback experience:

`episode starts`
-> optional Skip recap
-> optional Skip intro
-> normal playback
-> Core identifies/prefetches next video/stream candidate
-> Episode Continuity validates/ranks
-> next episode becomes Prepared
-> credits/preview segment becomes active
-> contextual Next Episode / Skip action
-> seamless or fast transition
-> commit next-episode state only after real transition/playback confirmation

Skip Segments, Smart Playback, recovery, language intent, next-episode preparation, and seamless preload must share playback-session identity and not become independent competing hacks.

---

## 13. Metrics

Before automatic behavior, measure locally/debug-first:

Segments:
- lookup latency
- cache hit rate
- provider hit rate
- resolver confidence distribution
- button shown rate
- button activation rate
- user seek-back soon after skip as a possible bad-match signal
- disagreement between providers

Playback:
- play press -> resolved source
- resolve latency
- first-frame latency
- fallback attempts/success
- recovery latency
- episode transition visual/audio gap
- rebuffering while speculative work is active
- memory during preload

Do not send new behavioral telemetry externally without an explicit product/privacy decision.

---

## 14. Implementation order

Do not implement this roadmap before the normal TV playback path exists.

### PE0 — Playback instrumentation baseline
- production Details/Episodes/Streams/Player exists
- define TTFF, transition and failure timestamps
- measure current behavior

#### Phase 6A implementation status

The ordinary TV playback instrumentation baseline is implemented in the Android app. Each manual TV playback attempt receives a unique identity and uses monotonic timestamps for resolution start, playable-source resolution, player-load start/return, and first visual progress. TTFF is measured from attempt start to first visual progress when both timestamps exist; incomplete attempts remain without a TTFF value. ExoPlayer reports its rendered-first-frame callback. MPV reports its first playback-restart event after file load as a first-visual proxy. Retry receives a new attempt identity and stale callbacks are rejected.

TV playback calls the existing Core resolve-and-load repository path and maintains a separate TV playback state. It does not enter mobile `playStream` orchestration. Requested and actual engine are represented separately. Stream-history selection and progress reporting are gated on first visual progress, with final progress reported on Back when eligible. Completion is reported without automatic episode advance. `PlaybackManager` continues to release and recreate the player for each load; this remains the baseline for later measurement. Trace formatting is tested to exclude stream URLs.

The compile, assemble, and unit-test tasks passed (125 tests, 0 failures) after the focused runtime defect fix described below. The debug universal APK was installed with data preserved on the ARM64 Google TV API 36 emulator. The signed-in route was Home Continue Watching → Silo Details → Season 2 → S2E7 (“The Dive”) → Choose Source. No library state was changed. The episode showed watched status and no resume position, so resume was not applicable.

#### Phase 6A runtime audit — 2026-09-29 (PE0 remains open)

The current profile engine was ExoPlayer (`EXO`). Direct and Torrent rows were distinguishable in the stream UI; the explicit Choose Source screen did not start playback until a row was activated. The addon result counts had changed naturally from the Phase 5C audit; results were not forced to match the old counts.

| Manually selected run | Requested / actual | Resolution ms | Player load-call ms | Post-resolve to visual ms | TTFF ms | First visual | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| Direct, NoTorrent, 1080p (first attempt) | EXO / EXO | 89 | 301 | N/A | N/A | None | Exo source error after HTTP socket timeout; no frame |
| Direct, NoTorrent, 1080p (same semantic source retried) | EXO / EXO | 23 | 12 | N/A | N/A | None | Cancelled during Preparing with physical Back |
| Direct, NoTorrent, 1080p (Server 1) | EXO / EXO | 15 | 48 | N/A | N/A | None | Preparing failed; no frame |
| Direct, NoTorrent, 1080p (CineStream) | EXO / EXO | 15 | 208 | N/A | N/A | None | Exo source error; no frame |
| Direct, NoTorrent, 1080p (VidPlay) | EXO / EXO | 12 | 12 | N/A | N/A | None | Exo source error; no frame |
| Torrent, Torrentio, 1080p (608.91 MB, 809 seeds) | EXO / N/A | N/A | N/A | N/A | N/A | None | Failed in `Resolving` before engine load; local-server start/resolution did not complete |

These are separate attempts, not a performance average. No attempt reached `ExoRenderedFirstFrame`, so no TTFF or visual-latency value can be reported and a 30-second playback run was not possible. No repeat-success TTFF sample exists. MPV was not exercised: the profile was configured for ExoPlayer and no safe local-only engine switch was available. There was no engine fallback in the observed attempts.

Direct attempts remained on the manually selected row; there was no automatic fallback. Physical Back from a Direct Preparing attempt returned to the same S2E7 Streams list with that row selected, released the player, and did not reopen playback or produce delayed audio/video during observation. Back from a Torrent error returned to the same S2E7 Streams list; Back again restored Details at Season 2/S2E7 with the episode focused. The player Back path therefore restored the nested route in the observed checks.

The initial natural Direct error exposed an unfocused Error UI: arrow keys were consumed at the Player root, leaving Retry and Back unreachable by D-pad. The root preview handler consumed D-pad events during Error and the action buttons had no explicit focus contract. The minimal fix adds an explicit initial focus target for Starting, Playing, and Error; Error now focuses Retry and allows D-pad traversal to Back. A pure regression test covers these targets. After rebuilding/reinstalling, runtime focus confirmed Retry initially and Back/Retry traversal in both directions. Retry used the same semantic stream and a new attempt ID. Starting focused the player surface. No natural Error focus issue remains observed.

Progress reporting before first visual is forbidden by the TV state gate (`firstVisualObserved`) and covered by existing source/unit behavior, but runtime reporting after a first visual could not be verified because none occurred. Seek reporting, progress cadence, controls/seek/pause operation during successful playback, controls hiding/restoration, and final eligible progress on Back remain unverified at runtime. Cancel-during-Preparing was exercised: Streams returned with the source selected, the player was released, and no later playback or stale route/state mutation was observed. Retry UI was exercised; a Retry that itself reached playback was not.

The trace scan found no `http://`, `https://`, `magnet:`, `token=`, or `auth` strings in `TvPlaybackTrace`. Log review found Exo `PlaybackException`s (HTTP timeout/connection reset) in the Stremio process, but no Stremio fatal exception, app-process ANR, Compose exception, FocusRequester warning, or FocusRelatedWarning. `AndroidRuntime` startup entries were from the separate `uiautomator` process. MPV errors were not applicable. Source review confirms completion reports ended without auto-advance and no TV activation of health watch, next-video lookup, Skip Segments, preloading, or Smart ranking/fallback.

Screenshots are outside Git under `/private/tmp/` with `pe0-` prefixes (Home, Details, Streams, Starting, natural Error, fixed focus, Torrent list/Starting, canceled Streams, and restored Details). PE0 remains open: no real stream reached a genuine first visual, so the visual, successful progress, control, and stable-playback baseline is incomplete. Do not begin PE1 until a Direct or Torrent run reaches the required first-visual signal and Back/progress behavior is verified.

#### PE0.2 runtime continuation — 2026-09-30 (PE0 remains open)

A clean launch retained the authenticated Home session. The manually focused `Torrentio` filter showed a 1080p Torrent with 809 seeds for Silo S2E7; Center explicitly activated that row and the Player Starting screen appeared. No Smart ranking or automatic source selection was used. This continuation did not reach a log-verifiable Core or engine stage: ADB detached after activation and `adb devices -l` returned no devices. The existing `Television_1080p` AVD remained listed as stopped in Device Manager. Launching that profile from Android Studio did not reconnect ADB; direct launch exited with status 134 and `Incompatible processor ... Qt build requires ... neon` on the arm64 host. This is an emulator startup/transport boundary, not evidence of a playback failure in the app.

No logs from the activated attempt were available after ADB detached, so current-run JNI/server state, `/settings`, Core conversion, player load, Exo preparation, progress callbacks, and privacy-log audit are unverified. The visible Player Starting screen is not a first-frame success. No `ExoRenderedFirstFrame`, moving video, audio, controls, seeks/reportSeek, post-first-frame progress cadence, 30-second stability, Back finalization, or route restoration was verified. PE0 remains open and PE1 remains blocked. Next step: restore a compatible running `Television_1080p` AVD/ADB connection and repeat the manual S2E7 Torrent gate; no source or native changes are indicated by this interrupted run.

#### PE0.3 emulator recovery and playback continuation — 2026-09-30 (PE0 remains open)

The NEON startup failure was a sandbox boundary: host audit found native Apple M1 arm64, `sysctl.proc_translated=0`, and NEON available; the native arm64 emulator and ARM64 Google TV API 36 image are compatible. Qt's feature query was denied inside the sandbox. Launching the existing AVD outside the sandbox recovered it without changing its userdata; authenticated Home survived and ADB/device boot remained stable for over two minutes. Reinstalling the official Emulator package left it at 37.1.11 and did not change the later failure.

Two manual Torrentio 1080p attempts for Silo S2E7 (first candidate 809 seeds) selected JNI, reached server Ready, and returned a matching Core-converted Torrent and EXO load call. The first reported resolution/load-call timings of 115/158 ms; the second 199/371 ms. Both attempts then lost the host QEMU process before `ExoRenderedFirstFrame`. The new macOS crash report matches the first: native arm64 QEMU `EXC_BAD_ACCESS` / `SIGSEGV` at `0x1000002c0`, faulting in `main_loop`; ADB disappeared because QEMU exited. Host monitoring showed no guest-only reboot or overlapping sleep event. The latest safe trace ends at `load-returned`; no first visual occurred. PE0 remains open and PE1 remains blocked. Next step: establish a stable QEMU run beyond the EXO load dispatch; resume the visual and interaction gate only then.

#### PE0.4 emulator-version and renderer isolation — 2026-09-30 (PE0 remains open)

Verified `origin/feat/android-tv` at `68686c4fd1d8ec69b1eb8da0c9a19004d94b0e82` before testing. Kept the installed SDK Emulator 37.1.11 untouched and used separate extracts from Google's official [Emulator download archive](https://developer.android.com/studio/emulator_archive). The 36.6.11 macOS Apple Silicon package was `emulator-darwin_aarch64-15507667.zip`, SHA-256 `aebcd4dde29a4921d47e5e79b8c1ffece69a70f5b280d8dc7033ebaffa737072`; its emulator and `qemu-system-aarch64` are Mach-O arm64. The latest 37.2.x Canary listed in the archive was 37.2.9 build 16322952, package `emulator-darwin_aarch64-16322952.zip`, SHA-256 `a3c3897a86590c1b400705db4a87b257bd410ac7f51f47c2a9a405df39cb25c5`; both binaries are Mach-O arm64.

Both versions booted the same `Television_1080p` Google TV API 36 ARM64 AVD with snapshots disabled. Each retained the authenticated Stremio Home/session and installed APK and remained ADB-connected through repeated checks for more than two minutes. No userdata wipe/recreation, app uninstall, or application source change occurred. On both, manually selected the same Torrentio 1080p Torrent for Silo S2E7 (608.91 MB, 809 seeds, `ilCorSaRoNeRo`); no Smart selection or source fallback was used.

| Emulator | Server Ready / `/settings` | Core-converted EXO load | First frame | Host result |
| --- | --- | --- | --- | --- |
| 37.1.11 stable (prior baseline) | Yes | Yes; 115/158 ms and 199/371 ms resolution/load-call samples | No | Native ARM64 QEMU `EXC_BAD_ACCESS` / `SIGSEGV`, `main_loop`, fault `0x1000002c0`; ADB disappeared |
| 36.6.11 stable | Yes; startup 118 ms, `/settings` 2.866 s | Yes; resolution 116 ms, load call 205 ms | No | Native ARM64 QEMU `EXC_BAD_ACCESS` / `SIGSEGV`, `MainLoopThread` → `main_loop`, fault `0x1000000c00`; ADB disappeared |
| 37.2.9 Canary | Yes; startup 189 ms, `/settings` 1.823 s | Yes; resolution 306 ms, load call 364 ms | No | Native ARM64 QEMU `EXC_BAD_ACCESS` / `SIGSEGV`, `MainLoopThread` → `main_loop`, fault `0x1000002d8`; ADB disappeared |
| 37.2.9 Canary, explicit `-gpu software` | Yes; startup 225 ms, `/settings` 12.496 s | Yes; resolution 164 ms, load call 181 ms | No | Native ARM64 QEMU `EXC_BAD_ACCESS` / `SIGSEGV`, `MainLoopThread` → `main_loop`, fault `0x1000002d8`; ADB disappeared |

The 36.6.11 and default 37.2.9 launches selected software GL automatically under host memory pressure (`lavapipe` Vulkan and `swangle` GLES); the additional 37.2.9 run explicitly used the supported `-gpu software` mode confirmed by that binary's own help. It logged color-buffer/texture-binding errors before the same QEMU main-loop crash. No `ExoRenderedFirstFrame`, Android app exception, or `PlaybackException` was observed. The 36.6.11, Canary, and software-mode reports identify native ARM64 QEMU `MainLoopThread` / `main_loop`; ADB disappeared when QEMU exited.

The evidence does not support a 37.1.11-only emulator regression: the same host-QEMU failure boundary reproduces on 36.6.11, 37.2.9 Canary, and explicit software rendering. The JNI server, Core conversion, EXO dispatch, and app load call complete; no stable runtime exposes an Android playback defect. No `reportTimeChanged`, `reportSeek`, visual/audio, control, seek, 30-second, or Back-restoration gate could be evaluated. The filtered trace contains only safe stage/provider/engine/latency fields, reports `proxyHeaders=no`, and includes no raw stream URL or credentials. PE0 remains blocked and PE1 remains blocked. Stop emulator-version experiments; next reproduce on real Android TV/Google TV hardware if available, otherwise a separately created clean compatible TV AVD. Keep this existing AVD and application playback code unchanged until that environment yields evidence.

### PE1 — Core Skip Segments POC
- expose Core `introOutro` through the existing Android playback state/repository path if not already surfaced
- normalized segment domain model
- button-only Skip Intro / Skip Outro
- no external providers
- verify actual stream/duration alignment

### PE2 — Generic Segment Coordinator
- provider interface
- resolver
- local stream-aware cache
- confidence/evidence diagnostics
- recap/intro/credits/preview model

#### PE2 implementation — 2026-10-02

Implemented a provider-neutral query, candidate, resolved-segment, provider, resolver, bounded in-memory LRU cache, and coordinator. Core is the only production provider: Core Intro boundaries retain Core's matched-source alignment without Android scaling; Core Outro maps to Credits ending at the active playback duration. Only high-confidence, bounded candidates become actions, and unsafe cross-type overlaps are dropped. The player consumes resolved segments and an attempt-scoped contextual-action policy; manual activation continues through the existing ViewModel seek/report path. Segment state resets with each attempt and late publication is guarded by attempt ID, including when Exo is reused.

The DEBUG Playback Lab's explicit simulated-state presets use a deterministic fixture through the same coordinator and resolver. It is absent from Release sources. The cache is bounded to 64 entries and keyed by content/video identity, exact duration, stream kind/semantic identity, optional file/hash/size fingerprint, and provider identity; no playback URLs, auth, or session values are stored. Debug diagnostics contain only attempt ID, provider, counts, normalized boundaries, confidence, and cache status.

Validation: `:app:compileDebugKotlin :app:testDebugUnitTest` passed (219 tests, 0 failures); `:app:compileReleaseKotlin :app:assembleRelease` passed; `git diff --check` is recorded with the change. No physical Android TV/Google TV device was connected for PE2 runtime validation. The DEBUG fixture is covered by the debug unit suite; real Core segment availability and on-device skip/arbitration behavior remain unobserved for this PE2 change. External/community providers and auto-skip remain unimplemented.

### PE3 — External segment provider evaluation — PARKED
External/community segment-provider evaluation and implementation are parked.

### PE4 — Smart Play V1 — implemented
- Deterministic tuple-based ranker reuses the existing preferred-quality setting, parsed quality, source kind, torrent seeds, size, semantic identity, and stable tie-break. With no exact preference, 1–4 seed torrents lose two quality tiers and dead torrents lose three; healthy torrents retain their quality tier. An exact user preference remains strongest.
- TV Play and episode Center use Core-backed `MetaDetails` discovery. Candidates settle for 1.3 seconds after the first usable result; completed discovery selects immediately; a 5-second maximum bounds waiting.
- Smart selection starts playback through the existing `startTvPlayback` path. Empty discovery returns to the ordinary no-source state; manual `Choose Source` remains available, and a selected stream is marked Recommended.
- Details exposes Play then Choose Source. Focused episodes play on Center and expose a trailing Sources action on Right.
- DEBUG diagnostics report only rank, normalized quality, source kind, seed bucket, and reasons. Ranking tests cover weak-torrent reliability, explicit preferences, arrival order, provider independence, and semantic-key ties.
- The connected Google TV API 36 emulator ran episode Center → Smart Play on Silo S1E4 using 56 real addon streams and started the regular playback attempt. Its pre-fix diagnostics exposed and led to fixing explicit-quality precedence over title tokens. After the fix, the DEBUG fixture reported D as rank 1 and started the normal attempt path. Player startup hit the existing emulator `StubControllerUnavailable` boundary for Torrent sources; ranking does not depend on playback success. Right → Sources was focused from an episode row, and Center on a manually focused NoTorrent stream started a Direct playback attempt. Movie Details initially focused Play; Right focused Choose Source. Movie Play reached the normal no-source state for the tested item, and the four-stream movie DEBUG fixture selected D and started a normal Torrent playback attempt. D-pad focus was restored after returning to the source picker; no focus requester warning or app crash was observed.
- This change passed DEBUG compile and unit tests (252 tests), DEBUG APK assembly, RELEASE compile/assembly, and `git diff --check`.

### PE5 — Smart Fallback V1 — implemented and emulator validated
- A bounded session reuses the Smart Play ranked snapshot, tries at most five semantic candidates once each, and commits at first visual.
- Startup failures advance automatically through the existing `startTvPlayback` path; manual source selection and failures after first visual retain normal Error behavior.
- DEBUG fixture deterministically fails A (container), fails B (source), then loads C through the ordinary player path.

#### Smart Play V1.2 — device/network awareness — implemented
- Android display modes, display HDR capabilities, and MediaCodec video capabilities are collected into a pure immutable device snapshot; `hardwareDecoding=false` leaves codec support unknown rather than assuming hardware decoding.
- Smart candidates are parsed for resolution, codec, HDR, and release tokens. Verified output/decoder/HDR incompatibilities rank below eligible candidates and are excluded from Smart Play; manual Choose Source retains the full source list.
- Network selection uses passive Media3 HTTP transfer measurements with bounded local EWMA history and age-based confidence. Localhost/loopback HTTP transfers are filtered before entering the shared meter/history. Android downstream link bandwidth is a low-confidence fallback only; Torrent download speed is not stored as internet throughput.
- File-size bitrate estimates are used only with an available target runtime and include a 1.4× average-bitrate safety factor. Unknown duration or network keeps candidates eligible. Device/network state is captured before Smart Play ranking and the existing fallback session keeps its fixed ranked candidate snapshot.
- Validation: Android unit suite passed (263 tests); debug compile/APK and release compile/APK passed; `git diff --check` passed. On the Philips TPM171E (Android 8/API 26), the collector reported physical current/max mode 1920×1080, 2160p output unsupported, AVC/HEVC/VP9 4K support, AV1 4K unsupported, and HDR10/HLG supported with HDR10+/Dolby Vision unsupported. One Silo S1E4 Smart Play ranked six real sources, chose 1080p Direct, and reached the normal committed first-visual path. Passive remote playback updated local measured-throughput history from a prior ~4 Mbps estimate to ~7.7 Mbps. The Android link estimate was not exposed by this device's diagnostic snapshot. Localhost/loopback exclusion is covered by unit tests; no Torrent playback was needed to verify its URI filter.

### PE6 — Episode Continuity + language intent
- Core binge match first
- local similarity fallback
- semantic audio/subtitle intent

### PE7 — Next Episode Transaction
- Prepared vs Committed lifecycle
- safe future-stream resolution
- Core state synchronization design

### PE8 — Persistent Exo session + bounded preload
- long-lived ExoPlayer
- current + next playlist
- verified Media3 preload API
- resource governor
- direct/safe sources first

### PE9 — Auto Skip experiments
Only after segment precision is strong:
- feature flag
- per-segment confidence threshold
- easy manual override
- measure seek-back / disable behavior

### PE10 — Production tuning
- low-end real TV hardware
- memory/network limits
- torrent-specific policy
- refresh-rate coordination
- recovery and transition metrics

---

## 15. Guardrails

- Stremio Core remains authoritative for Stremio addon/resource semantics.
- Do not perform MetaDetails/streams work merely because poster focus moved unless an isolated safe prefetch path exists.
- Current playback owns resources; speculative work yields immediately when playback health degrades.
- A segment lookup failure must degrade to ordinary playback, not an error screen.
- A future episode may be prepared but must not be marked watched/started until playback is genuinely committed.
- Do not let late stream/provider results reorder an actively navigated list.
- Manual stream selection and manual seek remain available.
- MPV and Exo may have different seamless capabilities; do not fake parity.
- No external provider, AI service, or project backend is mandatory for core playback.

### PE0.1 — Core playback resolution correctness (2026-09-29)

The previous Android resolver could emit `directUrl(option.stream)` from `onStart` before Core's `Player.stream` conversion completed. `PlaybackRepository` consumed the first emission, so a raw URL could beat Core's converted result. This bypassed Core's URL `proxyHeaders` conversion and omitted Torrent conversion parameters such as announce trackers and `fileMustInclude`. The direct candidates audited on S2E7 were all NoTorrent 1080p Direct streams with no proxy headers (request and response header counts were both zero; `notWebReady=false`), so this bug was not established as the cause of those earlier failures. There was no Direct-with-proxy candidate available in that audit.

Android now dispatches `Player.Load` and accepts only a matching `Player.selected` and `Player.stream` Ready result. Matching requires equal stream source and payload fields, `streamRequest`, and `metaRequest`; computed `deepLinks` are excluded because Core derives them while bridging. Loading waits, matching conversion Error is a typed failure, and stale ready state is rejected. The bridge source confirms converted Ready streams are serialized as the converted stream, with the Core-generated streaming endpoint in the external-player streaming deep link. Android reads that converted result and no longer reconstructs Torrent or proxy URLs.

Raw fallback is disabled for every source, including plain HTTP(S). Resolution has a named 15,000 ms safety timeout. The original server-required classification was conservative and is corrected in PE0.2: Torrent, YouTube, archive/NZB sources, URL proxy headers, and FTP/FTPS URLs require the server; ordinary HTTP/HTTPS, RTMP, External, and PlayerFrame do not require it merely for conversion. A missing source is invalid/no playable source. The server is prepared before Player conversion for required sources. Attempt traces report only safe source categories and server/resolution stages.

The post-change APK compiled, assembled, and passed the full 135-test unit suite. It installed with app data preserved and launched on the ARM64 Google TV emulator. Final-source S2E7 NoTorrent 1080p EXO Direct resolved through Core (`resolution=core-converted`, converted source `Url`, `server=not-required`) in 105 ms; `PlaybackManager.load` returned in 84 ms. The source remained on Starting and later failed as `ConnectTimeout`; no ExoRenderedFirstFrame occurred. Direct candidates audited on S2E7 were all 12 NoTorrent 1080p with proxy headers absent, request/response header counts zero, and `notWebReady=false`; no Direct-with-proxy candidate was available. A Torrentio Torrent was explicitly selected. Its original metadata had 0 announce entries, 0 `fileMustInclude` entries, `fileIdx` present, and `infoHash` present. Across all 52 Torrent candidates, announce counts reached 26 or 27 for some candidates; all had zero `fileMustInclude` entries and both file index and info hash present. Server state was Failed before startup and remained Failed afterward; startup returned `NativeStartFailed` in 0 ms, so Core conversion was not dispatched. The APK lacks `libstream_server.so`; `/settings` was not reached and Torrent conversion, endpoint reachability, and playback were not exercised. The Error screen's D-pad focus moved between Retry and Back. Back from the Direct attempt restored the exact S2E7 source list. No stream reached first visual, so PE0 remains open and PE1 remains blocked. Next: provide a build with the native streaming-server library, then repeat the Torrent conversion/playback baseline and obtain a Direct source that reaches first visual before completing controls, progress, Back restoration, and stability checks.

### PE0.2 — Native stream-server ARM64 debug build and Torrent gate (2026-09-30)

The old APK's missing `lib/arm64-v8a/libstream_server.so` is explained by the build graph: `preBuild -> copyStreamServerJniLibs` was disabled. Normal `:app:assembleDebug` and the current android-ci/android-nightly debug workflows do not compile or inject Rust. The release workflow separately builds the pinned `stream-server` gitlink, then injects its artifact into `app/src/main/jniLibs/<abi>/` before release assembly. `AppContainer` caught the absent-library `UnsatisfiedLinkError` and created `StubStreamingServerController`; that APK could not complete Torrent playback.

The new ARM64-only developer command is `VCPKG_ROOT=/path/to/vcpkg ANDROID_NDK_HOME=/path/to/android-ndk bash ./gradlew :app:assembleTvArm64Debug`. It follows the release target/features/API semantics, prepares vcpkg's ARM64 Android dependencies into ignored `app/build/`, generates the server library from the pinned source, copies it into `app/build/generated/`, verifies the ELF reports ARM64 Android, assembles the ABI split, and inspects the APK ZIP for `lib/arm64-v8a/libstream_server.so` and `libc++_shared.so`. Normal Kotlin compile/test and ordinary debug assembly remain Rust-free. No generated `.so` is committed. Pushes to `feat/android-tv` automatically invoke `android-ci.yml`, which calls the reusable `android-tv-native-arm64.yml`; the branch push is the supported trigger for this TV native gate. A `workflow_dispatch` attempt returning 403 was irrelevant.

The earlier 2026-09-29 local-toolchain limitation is superseded by the cloud build. Actions did run automatically after pushes. Run `36642179916` on `a0fbdbf` passed Rust, cargo-ndk, NDK r27c, and pinned vcpkg setup, then failed because the tracked `gradlew` is not executable; `bash ./gradlew` fixed that. Run `36676492990` on `adfcecf` exposed a second concrete defect: the native script invoked manifest-mode `vcpkg install` without `--x-manifest-root`, so vcpkg treated it as classic mode. Adding `--x-manifest-root=$stream_server_root` fixed the invocation; no Rust/vcpkg toolchain was installed locally and neither submodule pointer changed.

Run `36676492990` succeeded. It built pinned stream-server commit `368666eb09f9c4849a7df7f2a6a8b26171f24cfb`, verified `libstream_server.so` as ELF64 ARM aarch64, assembled `app-arm64-v8a-debug.apk`, and passed `verifyArm64StreamServerPackaging`, which confirmed the APK contains `lib/arm64-v8a/libstream_server.so` and `lib/arm64-v8a/libc++_shared.so`. Artifact `11080028900` (`stremio-android-tv-arm64-native-debug`) was downloaded from that exact run to `/private/tmp/stremio-android-tv-native-run-36676492990/app-arm64-v8a-debug.apk`; the local APK ZIP inspection confirmed both libraries and `file` identified the extracted stream server as ELF64 ARM aarch64.

PE0.2 continuation — signing-compatible local repackaging (2026-09-30): successful Actions run `36678971298` produced artifact `11081072003` (`stremio-android-tv-arm64-native-debug`). Its APK was rechecked and contained both ARM64 libraries. The cloud APK could not replace the existing installation because GitHub's hosted debug build used the runner's ephemeral debug certificate, while the installed app used the workstation's Android debug certificate. The installed base APK was pulled for a public SHA-256 certificate fingerprint comparison; no private key material was accessed or recorded. Only the verified cloud `libstream_server.so` was extracted to the intentionally ignored `app/src/main/jniLibs/arm64-v8a/libstream_server.so`; the existing `libc++_shared.so` was left untouched. A normal local `:app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest` build packaged both libraries. The local native APK's signing certificate exactly matched the installed APK, so `adb install -r` succeeded and preserved app data. No app source change or submodule pointer change was needed.

After clean launch, safe diagnostics confirmed `JniStreamingServerController` was selected, the JNI library loaded, native start returned in 473 ms, and `StreamingServerState.Ready` followed a successful local `/settings` 2xx check 2,602 ms after startup began. This verifies native packaging and readiness. Manual UI playback was not continued because the computer-use interface reported that the Mac was locked; account state, Torrent source selection/Core conversion, playback endpoint/data acquisition, Exo connection/demux/decoder, first visual/TTFF, controls, progress, 30-second stability, and Back restoration are therefore still unverified. No source was selected in this continuation, so no runtime fallback/next-episode/Skip-Segments claim is made. PE0 remains open and PE1 remains blocked until the full manual playback contract is observed.

JNI diagnostics now distinguish library load, native start invocation/latency, null return, local `/settings` readiness, native function linkage failure, and other startup exceptions. Readiness now requires HTTP 2xx. Raw server log contents are no longer copied into Logcat; diagnostics report safe categories, log-file counts/bytes, and timing only.

The local continuation build completed `:app:compileDebugKotlin`, `:app:assembleDebug`, and `:app:testDebugUnitTest` successfully. Gradle reported the existing unit-test task up-to-date; its reports contain all 136 tests with 0 failures/errors. No application source or source unit test changed. Trace code continues to restrict playback diagnostics to safe categories/timings and omit stream URLs, headers, trackers, and hashes; the runtime evidence above contains only controller, startup, and readiness timings.

PE0 remains open; PE1 remains blocked. Native packaging, local signing compatibility, JNI selection, and server readiness are now verified. Next step: unlock the Mac and complete the S2E7 Torrent first-visual and manual playback contract on the ARM64 emulator. Direct compatibility remains a separate open coverage item; no PE1 work is authorized by this result.

#### PE0.5 real-device-first / clean-AVD environment isolation — 2026-09-30 (PE0 blocked; physical hardware required)

No physical Android TV/Google TV device was connected. Created a separate clean `Stremio_TV_PE0_Clean` AVD using the installed Google TV API 36 `google-tv/google_apis` arm64-v8a image and 1080p Google TV profile (2 GiB RAM, 6 GiB data partition). It booted with snapshots disabled and remained ADB-connected with QEMU alive through a two-minute, 24-sample idle check. Installed the existing local native ARM64 debug APK; its ZIP contains both `lib/arm64-v8a/libstream_server.so` and `lib/arm64-v8a/libc++_shared.so`. After the user authenticated manually, Home/catalogs loaded, Silo and S2E7 (“The Dive”) were reachable, and JNI server startup reached Ready with `/settings` HTTP 2xx (3.301 s on TvActivity startup).

The user-navigated Choose Source screen showed Torrentio 1080p choices. Manually activated the visible EZTV result (405.51 MB, 357 seeds). Attempt `72a3107b-cbb8-4de0-afc8-931afd431ac0` recorded server Ready (137 ms), Core-converted Torrent, requested/actual EXO, resolution 112 ms, and PlaybackManager load-call return 185 ms. The host `qemu-system-aarch64` process (PID 29675) then exited with status 139 about 24 seconds after activation; ADB disappeared. The captured safe Logcat stream ends at `load-returned`, with no `ExoRenderedFirstFrame`, Android fatal exception, or Exo `PlaybackException` before host loss. No guest app defect was exposed, and no raw fallback or engine switch was observed.

Classification: `CLEAN_AVD_HOST_QEMU_REPRODUCED`. The clean environment reproduces the same host failure boundary after Core conversion and EXO load dispatch, so the old AVD's userdata is not required to trigger it. Stop all emulator-version, GPU-flag, and additional-AVD experiments. Require real Android TV/Google TV hardware to continue PE0. First frame, TTFF, moving video/audio, controls, seek, progress gates, 30-second stability, Back finalization, and route restoration remain unverified. PE0 is not complete; PE1 remains blocked. No application source, playback settings, server/Core/Torrent configuration, submodule pointer, or original AVD data was changed.

#### PE0.6 physical Philips completion — 2026-09-30

PE0 completed on the Philips TPM171E (Android 8/API 26, armeabi-v7a). The release-equivalent ARMv7 native debug APK packaged the pinned stream-server and shared C++ runtime. `JniStreamingServerController` reached Ready and `/settings` returned HTTP 2xx. A manually selected Torrentio 1080p Torrent for Silo S2E7 (“The Dive”) converted through Core and loaded in EXO; the safe trace recorded `ExoRenderedFirstFrame`. Core resolution was 483 ms, player load-call 206 ms, and TTFF was 52.4 s. The user confirmed stable moving video and audio, pause/resume, backward/forward seek, Back from Player, and restoration through Streams to the same S2E7 Details location.

The trace records one Torrentio attempt, Core-converted Torrent, requested/actual EXO, first visual, and normal close. It shows no fallback or subsequent episode attempt. Core progress/seek RPCs are not emitted as individual log lines; source guards defer periodic `reportTimeChanged` until first visual and bound it to at most once per five seconds, dispatch `reportSeek` on seek, and report eligible final progress on Back. Manual seek and Back checks passed. Skip Segments and preload were not implemented or exercised. Emulator QEMU crashes are now a non-blocking environment issue. The 52.4 s TTFF is a performance issue to investigate next, not a functional blocker. PE0 is complete; PE1 remains blocked and has not started.
