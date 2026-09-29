# TV Playback Experience Roadmap

Status: planning / architecture guardrail  
Date: 2026-09-29  
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

### PE3 — External segment provider evaluation
- verify candidate services
- implement one provider behind the neutral interface
- compare against Core and actual streams
- no playback dependency

### PE4 — Smart Play metadata/ranking
Follow `TV_SMART_PLAYBACK_FEASIBILITY.md` SP0-SP3:
- fixtures
- parser
- device capabilities
- explainable ranker
- Play + Choose another source

### PE5 — Recovery / Smart Fallback
- attempt coordinator
- failure classes
- retry / re-resolve / ranked fallback
- merge existing torrent health fallback

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
