# Android TV milestone 1 result

**Status: PARTIAL.** The TV entry, presentation, data flow, and focus behavior are implemented in source. A debug APK could not be produced because this environment has no installed Android SDK, so runtime behavior remains unverified.

## Starting state

- Starting commit: `4bdef3b6be1186d9aa22acd8b3ec391c22f0e083`.
- Starting branch: `master`.
- Working tree contained the pre-existing untracked `TV_FEASIBILITY_AUDIT.md`; it was preserved unchanged.
- Configured remotes: `origin` → `https://github.com/robbis95/stremio-android.git`; `upstream` → `https://github.com/stremio-native/stremio-android.git`.
- `git fetch --all --prune` completed. Both `origin/master` and `upstream/master` resolved to the starting commit.
- Implementation branch: `feat/android-tv-milestone-1`, created at the starting commit.

## Architecture implemented

- `TvActivity` is a separate exported activity that obtains the existing `MainApplication.container`, creates `MainViewModel` with the existing repositories/Core, and renders only `TvApp`.
- The TV root has Login, Home, and Details routes. `BackHandler` owns Back only on Details; its button uses the same return action. Home and Login use normal Activity/system Back behavior.
- The TV focus foundation stores the current poster as `type:id`, explicitly routes D-pad directions across the horizontal shelf, displays a high-contrast focus border, handles Center/Enter, and scrolls the selected lazy item into view.
- TV Login calls the existing `MainViewModel.login`; session restoration and account state remain owned by existing auth infrastructure.
- TV Home reads `boardShelves` from the existing `MainViewModel`/`BoardRepository` flow. A small `tvBoardLoading` state was added because an empty shelf list alone cannot distinguish an initial load from a completed empty catalog response.
- TV Details calls the existing `openDetails`/`closeDetails` metadata flow.

## Files changed

- `app/src/main/AndroidManifest.xml` — optional Leanback and touchscreen feature declarations, app banner, and TV launcher activity filter; the existing normal launcher entry remains.
- `app/src/main/java/com/stremio/mobile/TvActivity.kt` — dedicated TV Activity using the existing app container and ViewModel.
- `app/src/main/java/com/stremio/mobile/presentation/tv/TvApp.kt` — TV-only route selection, session gate, and Details Back handling.
- `app/src/main/java/com/stremio/mobile/presentation/tv/TvFocus.kt` — stable content focus identity and saveable focus memory.
- `app/src/main/java/com/stremio/mobile/presentation/tv/TvLoginScreen.kt` — remote- and keyboard-friendly credential login with IME actions and visible focus.
- `app/src/main/java/com/stremio/mobile/presentation/tv/TvHomeScreen.kt` — one real Core-backed shelf with loading, error, empty, poster, and D-pad handling.
- `app/src/main/java/com/stremio/mobile/presentation/tv/TvDetailsScreen.kt` — basic metadata display and focusable Back action.
- `app/src/main/java/com/stremio/mobile/presentation/viewmodel/MainViewModel.kt` — narrowly scoped board-loading state for correct empty/loading presentation.
- `app/src/main/res/drawable/tv_banner.xml` — temporary banner using the existing Stremio play mark on a dark background.
- `app/src/main/res/values/styles.xml` — simple TV window theme based on the existing app theme.

## Mobile compatibility

`MainActivity`, its `MAIN` + `LAUNCHER` intent, and `StremioMobileApp` remain unchanged. The manifest adds a separate Leanback launcher activity and marks Leanback and touchscreen features as optional, allowing the combined APK to remain eligible for touch devices. Mobile navigation and screens were not migrated. This source-level compatibility review is not a substitute for a successful Android build or merged-manifest inspection.

## Build verification

- `./gradlew :app:compileDebugKotlin` — could not start because the wrapper file is not executable in this checkout.
- `bash gradlew :app:compileDebugKotlin` — could not start because `/usr/bin/java` has no installed runtime.
- With Android Studio's bundled JDK, Gradle 9.5.1 downloaded and configured, then stopped before compilation: `SDK location not found`. The repository has no `local.properties`, and `ANDROID_HOME` is unset.
- Final attempt: `:app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest` — failed during task dependency resolution for the same missing SDK. Kotlin compilation, APK packaging, JVM tests, and lint therefore did not run.
- Static checks passed: `git diff --check`; Python XML parsing of the manifest, TV banner, and styles resource.
- APK path: **no APK was produced**. `app/build/outputs/apk/debug/` contains no milestone artifact.

## Runtime verification

- **Emulator:** not tested; no Android SDK, `adb`, or emulator executable/device was available.
- **Physical TV:** not tested.
- Launcher discovery, restored-session login bypass, fresh login, IME/phone-remote text input, shelf loading, details, and focus restoration remain runtime-unverified.

## Focus behavior

- Login initially requests focus on the email/username field. D-pad Down moves through password to Sign in; Up returns through password to the email field. IME Next moves to password and IME Done submits. Center/Enter activates the focused button through the normal Compose button behavior.
- Home initially focuses the first poster in the first available populated shelf. Left and Right move to adjacent posters; at either end focus remains on that endpoint. Up and Down remain on the only shelf row. Center/Enter opens Details.
- Home stays composed while Details is shown, retaining focus requesters and scroll position. The selected poster identity is saved as `type:id`; returning to Home requests that same item even if its array position changed and scrolls it into view.
- If the previously focused item is absent from the current shelf, focus falls back to the first current poster. If no item is available, the screen shows loading, error, or empty state and does not request poster focus.
- Details initially focuses its visible Back button. The button and system Back both close Details and return to Home.

## Known limitations

- Android compilation, APK generation, merged-manifest behavior, and all runtime flows remain unverified because an Android SDK is unavailable in this environment.
- Google TV phone-remote text entry and actual launcher banner presentation require device/emulator verification.
- The banner is temporary and should be replaced with approved production TV artwork.

## Deferred work

Milestone 1 does not add stream selection, episodes, playback, TV player controls, search, addons UI, full settings, MediaSession, media keys, PiP work, performance optimization, ViewModel splitting, a separate `:tv` module, a new application ID, release signing, or Play Store packaging.

## Recommended milestone 2

First provide an Android SDK/build environment and complete the milestone 1 build plus launcher, auth, and focus-flow checks on an Android TV emulator or device. That gives a verified baseline before stream selection or player work begins.
