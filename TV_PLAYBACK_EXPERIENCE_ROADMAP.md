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

The compile, assemble, and unit-test tasks passed (124 tests, 0 failures). The debug universal APK was installed with data preserved on the ARM64 Google TV API 36 emulator, and `TvActivity` was foregrounded; the signed-in Home screen was visually confirmed. No Silo S2E7 playback attempt reached the Player in this verification session, so no source-resolution, first-visual, TTFF, buffering, or engine runtime measurements are available yet. ExoPlayer and MPV runtime paths have not been validated by this phase. PE0 measurement remains open until a manual source playback run records those values.

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
