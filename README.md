# Trip Planner

Group trip planning on Kotlin Multiplatform + Compose Multiplatform (Android, iOS)
with a Firebase backend and Google Maps Platform, Calendar, and Gemini integrations.
Built from `trip-planner-system-design.md` **v1.0** — section references (§) in code
comments point there.

## Status — read this first

`./gradlew build` is **green** (Android debug/release, iOS frameworks, lint, and
`FractionalIndexTest`) as of 2026-09-17, on JDK 26 / Gradle 9.3 / Kotlin 2.2.20. The
scaffold was originally generated without a Gradle sync; the first build surfaced the
following, now fixed in place:

- **Firebase BoM.** GitLive's Android artifacts declare the Firebase SDK modules without
  versions, so `shared` exposes `firebase-bom` 33.15.0 (the BoM GitLive 2.3.0 was built
  against) as an `api` platform dependency.
- **Ktor 3.3.3.** 3.2.0 fails D8 dexing for `minSdk < 30` (KTOR-8583); 3.4+ is built on
  Kotlin 2.3 and its metadata is rejected by Kotlin 2.2.20. Bump Kotlin before bumping Ktor.
- **Koin `viewModel {}` DSL** lives in `koin-core-viewmodel`, not `koin-core`.
- **`google-services.json` is optional at build time.** `composeApp` applies the Google
  Services plugin only when the file exists, so a clean checkout builds; Firebase init at
  runtime still needs it (Phase 0, task 4).
- **iOS test binary is not linked.** GitLive's iOS klibs link against the native Firebase
  frameworks, which only exist inside the Xcode project (SPM). `commonTest` runs on the
  JVM via the Android unit-test tasks; iOS smoke tests are XCUITest (design §13).
- **App Link host** is the `APP_LINK_HOST` manifest placeholder (default: the dev Hosting
  site `tripplanner-dev-fe0a4.web.app`); lint rejects the old `REPLACE-…` placeholder.
- Maps Compose 6.7: `rememberMarkerState` was removed; use `rememberUpdatedMarkerState`.
- **kotlinx-datetime `0.8.0-0.6.x-compat`.** Compose Material3 1.8 (iOS date pickers) still calls
  `kotlinx.datetime.Clock`, which 0.7 removed; GitLive 2.3 needs the 0.7+ API. The compat build
  ships both, otherwise `DateRangePicker` crashes on iOS with an `IrLinkageError`.

**Android end-to-end works (2026-09-22):** Google sign-in (Credential Manager,
`composeApp/androidMain/auth/`), "New trip" writes a trip + sample stops through the shared
`TripRepository`, the list updates from the live Firestore listener, and the trip detail
screen shows the stops on Google Maps with marker taps reaching the shared ViewModel.
Navigation is `navigation-compose` with `@Serializable` routes (`App.kt`). Firestore rules
and indexes are deployed to the dev project. Secrets: `MAPS_API_KEY` is read from the env
or `local.properties`; `google-services.json` is fetched with the Firebase CLI (spike doc). **iOS runs (2026-09-22):** `iosApp/project.yml` + XcodeGen generate the Xcode project (see
`iosApp/README.md`); Firebase is configured from the dev project's plist and the shared UI
renders in the simulator. Google sign-in (GoogleSignIn-iOS) and the native map (GoogleMaps SDK behind the Kotlin
`NativeMap` boundary) are wired on iOS; simulator builds are ad-hoc signed so Firebase Auth
can use the keychain. The interop stress test (map in a scrolling list + bottom sheet +
keyboard) passed on iOS, so the design's SwiftUI fallback (§16) is not needed. All Phase 0
tasks are done except device-level frame measurement.

**Phase 1 (branch `phase1`):** trip creation is real - `NewTripScreen` (name + Material date
range picker) -> `NewTripViewModel` -> `NewTrip.build` (owner + sole member, device time zone,
schema rules unit-tested in `NewTripTest`) -> `TripRepository.createTrip` (server
`createdAt`/`updatedAt`). The Phase 0 sample-trip button is gone.
**Add stop** (`AddStopSheet` -> `AddStopViewModel`): debounced Places autocomplete (>= 3 chars,
250 ms), details on selection, one session token per search series (asserted in
`PlacesApiTest` over a mock engine), stop written at the end of the chosen day
(`PlacesSearch.appendOrderKey`). Stops carry `addedAt`/`updatedAt`/`placeFetchedAt`; the
name/address refresh policy from §7 is still to do. The key needs **Places API (New)** in its
API restrictions. **Day tabs + reorder** (`TripDetailScreen`): tabs filter the map and list to one day; rows drag
by their handle (sh.calvin.reorderable) and commit a single fractional-key write
(`StopReorder.neighbours` -> `moveStop`); the stop sheet moves a stop to another day
(`moveToDay`, end of day) or removes it; a NOT_FOUND on a concurrent delete surfaces as a
snackbar. `StopReorderTest` includes the 500-random-move soak over `FractionalIndex`.
**Map for the selected day:** both `MapView` actuals draw a polyline through the day's stops
in itinerary order and fit the camera to them whenever the set of stops changes with nothing
selected (Android gates on `onMapLoaded`, iOS on the first non-empty layout); selection still
animates to the stop without zooming out. **Offline UX** (design §9): `Trip`/`Stop` carry a transient `pendingSync` from snapshot metadata
(`hasPendingWrites`) shown as "syncing…" on rows and the day header; a `ConnectivityMonitor`
boundary (Android `ConnectivityManager`, iOS `nw_path_monitor`) drives an offline banner on the
trip detail and disables Places search with a message; the repository logs `listen`/`unlisten`
so listener scoping can be checked in the device log (both trip listeners detach ~5 s after
leaving the screen, `WhileSubscribed`). Verified with an airplane-mode reorder -> relaunch ->
reconnect round trip on Android. Calendar export (Phase 2) must reuse the same gate.
**Sync-stall fixes:** `FirestoreNetworkSync` follows the connectivity monitor and disables /
re-enables Firestore's network on transitions (the SDK's gRPC streams can otherwise stay
closed for many minutes after an abrupt network change, leaving writes queued); repository
writes are fire-and-forget with rejections on `TripRepository.writeFailures` (surfaced as
messages), so create-trip / add-stop / reorder never block on the server ack; the trip detail
shows "Still syncing… Retry" after 30 s pending while online.
Still unverified: the Functions/Places/Calendar call shapes in `shared/data/`. Cloud Functions in `firebase/functions/` are compile-shaped
TypeScript with TODOs where Phase 2/3 work lands (Routes, Gemini, burst collapsing).

## Layout

```
composeApp/   Compose Multiplatform UI + Android entry point + expect/actual (map)
shared/       models, FractionalIndex, repositories, Ktor APIs, ViewModels, Koin
iosApp/       Swift sources + instructions to generate the Xcode project
firebase/     rules, indexes, hosting (.well-known for App/Universal Links), functions
docs/         phase0-spike.md — this week's plan with exit criteria
planning/     issues.json + script to file the Phase 0–4 roadmap as GitHub issues
```

## Getting to a first run

1. **Toolchain:** JDK 17+, Android Studio (latest stable), Xcode 16+, Node 22,
   Firebase CLI (`npm i -g firebase-tools`).
2. **Build check:** `./gradlew build` (green as of the date above; runs the shared unit
   tests on the JVM).
3. **Firebase:** follow the prerequisites checklist in `docs/phase0-spike.md`
   (projects, Auth, Firestore, restricted Maps/Places API keys, config files —
   `google-services.json` and `GoogleService-Info.plist` are gitignored on purpose;
   the spike doc has the CLI command to re-download the Android one).
   Run on Android: `./gradlew :composeApp:installDebug` with an emulator booted.
4. **iOS project:** `brew install xcodegen && (cd iosApp && xcodegen generate)`, then open
   `iosApp/iosApp.xcodeproj` (details in `iosApp/README.md`).
5. **Backend:** `cd firebase && firebase deploy --only firestore:rules,firestore:indexes`.
6. Work through `docs/phase0-spike.md` — its exit criteria decide whether the
   architecture's riskiest bets (design §16) hold.

## Filing the roadmap as issues

See `planning/README.md` — one dry-run command to preview, one to create, once a
GitHub repo exists.

## Design doc

Keep `trip-planner-system-design.md` (v1.0) next to this repo or in `docs/`; the
decision log (§18) and revision history (§19) are the source of truth when code and
doc disagree.
