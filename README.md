# Trip Planner

Group trip planning on Kotlin Multiplatform + Compose Multiplatform (Android, iOS)
with a Firebase backend and Google Maps Platform and Claude (Anthropic API) integrations.
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

**Phase 2 (branch `phase2`) — invites (design §8.2):** the owner's **Share** action calls the
deployed `createInvite` callable and hands `https://<APP_LINK_HOST>/join/{code}` to the platform
share sheet; incoming links (Android App Link intent, iOS `tripplanner://join/{code}` scheme or a
Universal Link once the team ID is in `apple-app-site-association`, or the list's **Join**
dialog) go through `InviteIntake` -> `JoinRoute` -> `redeemInvite` (idempotent for existing
members; expired / used-up / invalid codes come back as clear messages). Hosting serves the
landing page (`/join/{code}` with an `intent://` fallback on Android) and `assetlinks.json`
carries the debug signing SHA-256 - Android reports the domain as verified. Cloud Functions are
deployed to the dev project (Blaze); `ROUTES_API_KEY` / `ANTHROPIC_API_KEY` hold placeholder values
until those features land.
**Activity feed + FCM fan-out (design §10):** every stop write is classified by `onStopWritten`
(added / moved / edited / removed; the actor comes from the write's auth context, falling back to
`updatedBy`/`addedBy`) into a structured `activity` event that `onActivityCreated` fans out to the
other members' devices, honouring `users/{uid}.notificationPrefs` (global **Notifications** switch in
the trip list menu, per-trip **Mute** in the trip menu). Bursts collapse per (trip, actor): the
first two changes within 60 s push at once, later ones are held and a Cloud Tasks flush
(`flushNotifyDigest`) sends one "A made N changes" digest at the window's end; the client keys
tray entries by trip+actor so the digest replaces the singles. Tokens FCM reports as dead are
pruned (`burst.ts`, unit-tested with `npm test`); the client keeps at most five per user.
Android: `TripMessagingService` words data messages on the device (`NotificationText`, shared with
the **Activity** screen) and a tap deep-links through `DeepLinkIntake` to the trip with the stop
selected. iOS: `PushBridge.swift` (APNs -> Firebase Messaging token, permission, tap) and the
same facts as `loc-key`/`loc-args` resolved from `en.lproj/Localizable.strings`; delivery to iOS
needs the APNs auth key uploaded in Firebase Console -> Cloud Messaging. The dev project sets
`NOTIFY_ACTOR_DEBUG=true` (`functions/.env.tripplanner-dev-fe0a4`) so a single test account sees
its own pushes; never set it for prod.
**Travel times (design §8.3):** `onStopWritten` recomputes only the affected days' adjacencies
(`travel.ts`: current consecutive pairs vs. stored `travel/{from}_{to}_{mode}` legs; legs whose
adjacency vanished are deleted, fresh ones < 24 h are kept, the rest go to one `computeRouteMatrix`
call per mode - two Routes calls per stop change, driving and walking, transit deferred). A failed
call writes an `error` leg so the client shows a dash and the next change retries. The trip screen
renders both modes' legs between consecutive stops (shimmer while missing, never blocking). `ROUTES_API_KEY` holds a real key on the dev project (2026-09-23; rotate with
`firebase functions:secrets:set ROUTES_API_KEY` and redeploy `onStopWritten`); verified on Android with
live Routes numbers between two Lisbon stops.
**Phase 3 (branch `phase3`, design v1.1) — shared trip calendar:** the Google Calendar export was
dropped (v1.1 decision 5). A new pure-Kotlin module `schedule/` (Android, iOS and a Node.js library
build for the future ICS feed) computes each day from the same Firestore data on every device:
day start + durations + travel legs, pinned entries (`stops.fixedStart` as wall-clock "HH:mm"),
custom entries (`kind: "custom"`, no place, skipped by the travel trigger), per-day hours
(`trips/{id}/days/{day}`) and warnings (late arrival, overlap, day overrun); nine golden tests run on
JVM and Node (`./gradlew :schedule:jsNodeTest`). The trip screen has Agenda (list with computed
times and warnings) and Day (time grid: long-press-drag to pin, snapped to 15 min and re-ordered
chronologically in one write; drag the bottom edge to resize; hatched travel legs; free-time gaps)
views, an Add-stop "Custom entry" form, pin/unpin in the stop sheet and a Day-hours dialog.
Pins and resizes produce activity events and pushes ("moved Belém Tower to 14:00 on Day 1").
**Trip view** (branch `trip-view`): the time grid is now a shared axis plus one column per day
(`TimeGrid` / `TimeGridColumn` in `ui/calendar/DayView.kt`); the **Trip** chip shows up to seven days
per horizontally paged screen with compact blocks, a tappable day header that selects the day, and
block taps that select the event (and its day) - read-only on phones, drag-to-pin and resize on
widths >= 600 dp (design §6.6). **Time-zone toggle** (branch `tz-toggle`): when the device zone differs from the trip's, a chip
next to the view chips switches the calendar between trip time (labelled with the trip zone) and
device-local time. `TimeDisplay.shift` converts a computed `DaySchedule` through instants (DST-safe)
and anchors it on the local date of the day's start, so a day that lands on another calendar day
still draws as one grid; the grid measures minutes from that date. Nothing stored is converted: a
drag-to-pin in local mode is converted back to trip wall-clock before the write, and the stop
sheet's pin field is labelled with the trip zone. **Reminders** (branch `reminders`, design §8.5): opt-in per user (`notificationPrefs.reminders {
enabled, leadMin }`, trip-list menu switch + lead-time dialog). `ReminderPlanner` (pure, tested) turns
the next 48 h of the user's trips into "Leave for X in N min" reminders fired N minutes before the
computed leave time (start of the travel leg into a stop, else its start), capped at 60 earliest-first
for iOS's 64-notification limit; `SyncReminders` re-plans on sign-in / preference change, app
foreground, any change to the open trip and each activity push, and hands the set to a
`ReminderScheduler` (Android: inexact `AlarmManager` alarms into `ReminderReceiver`, owned ids kept
in SharedPreferences; iOS: `UNCalendarNotificationTrigger` via `ReminderBridge`). A tap opens the trip
at that stop through the existing deep-link intake. Android alarms do not survive a reboot; the next
app start re-syncs. Also fixed here: a cold-start deep link to a later day crashed the Material tab
row (indicator indexed stale positions) - the indicator is now guarded. **ICS feed** (branch `ics-feed`, design §8.6):
"Add to my calendar…" in the trip menu calls `createCalendarFeed`, which mints a 256-bit token
(only its SHA-256 is stored in `calendarFeeds/{hash}`) and returns `https://<host>/cal/{token}.ics`;
the dialog copies it or opens it as `webcal://`. `GET /cal/**` is a Hosting rewrite to the
`calendarFeed` Function, which re-checks membership, runs the schedule engine's Node build
(`./gradlew :schedule:packageForFunctions` copies it to `firebase/functions/vendor/schedule`,
gitignored, also run as a deploy predeploy step) and emits one VEVENT per stop as UTC instants with
a stable `UID {stopId}@<host>`, `SEQUENCE` from `updatedAt`, LOCATION = name + Google Maps place
link, DESCRIPTION = notes + `tripplanner://trip/{tripId}/stop/{stopId}` (both apps open it via
`DeepLinkIntake`), `X-PUBLISHED-TTL: PT1H`, `Cache-Control: private, max-age=900`, a deterministic
ETag (304 on `If-None-Match` at the Function URL; Hosting does not forward the header, so through
the rewrite every refresh is a 200), 429 for fetches under 30 s apart and 404 once revoked
("Remove my calendar links") or when the owner left the trip. `AppConfig.calendarFeedEnabled`
hides the menu items. Tests: `firebase/functions/src/feed.test.ts` parses the output with `ical.js`.
**Suggest an order** (branch `suggest-order`, design §8.7): "Suggest an order for Day N" in the trip
menu (editors, two or more stops) calls `suggestDayOrder`, which loads the day's stops, pinned times,
day hours and travel legs, asks Claude (`claude-sonnet-5` via the Messages API with a forced
`propose_order` tool call, so the answer is schema-checked JSON; `firebase/functions/src/suggest.ts`)
and validates the answer: a permutation of
the day's ids, pinned entries kept, and no more late-arrival minutes than the current order per the
schedule engine's Node build; one retry with feedback, then the best candidate is returned with its
warnings. The client previews it in place (the day's stops are shown in the suggested order, a
banner carries the rationale and warnings, reordering is frozen) and Apply writes fresh evenly
spread `order` keys in one batched write. Flags: `SUGGEST_ORDER_ENABLED` param (in each project's
`functions/.env.*`) and `AppConfig.suggestOrderEnabled`. Gemini was tried first and dropped (model
retired for new users, then prepaid-credit billing); the unused `GEMINI_API_KEY` secret can be
deleted. Until `firebase functions:secrets:set ANTHROPIC_API_KEY` is run with a real key the
Function answers "Suggestions are not configured on this server yet".
Tests: `firebase/functions/src/suggest.test.ts` (prompt, validation, lateness, retry loop, REST call).
**Web W0 spike** (branch `web-spike`, design v1.1.1 §18 #10, companion 0.2 §17): the Kotlin core
runs in the browser. `shared/` and `schedule/` gained a `js(IR) { browser() }` target (`useEsModules()`
on `shared`, because Vite loads ES modules natively in dev; UMD only works once bundled);
`shared/src/jsMain` holds the browser actuals for the platform seams (popup/redirect Google sign-in
via the Firebase Web SDK, `navigator.onLine`, Web Share, no-op push and reminders) and the
`@JsExport` facade `TripPlannerWeb` (`start`, `tripList()`, `tripDetail(id)`, `computeDay`; state via
`subscribe(callback)`, suspend calls as Promises). `./gradlew :shared:packageForWeb` syncs the build
into `web/vendor/shared`; `web/` is a Vite + React app served at `/app/**` on the Hosting site (rewrite
added), with a preview channel per `firebase hosting:channel:deploy`. Go/no-go answers: (1) every
dependency resolved for JS, including `lifecycle-viewmodel-js` 2.9.2, so the ViewModel base class
stays; all 52 `commonTest` tests pass on Node (`:shared:jsNodeTest`); (2) the Kotlin chunk is
**378 KB gzipped** (2.0 MB raw) against the 500 KB budget, beside a 99 KB Firebase SDK chunk and a
70 KB app chunk. Ktor (~130 KB of the library, unused on the web where Places goes through the JS
library) is the obvious trim. Verified in the browser: engine output matches the Node fixture,
Firebase and Koin start, auth state drives the sign-in button. Open item: awaiting a Firebase promise
from a coroutine on `Dispatchers.Main` (`scope.promise { getRedirectResult(...).await() }`) logged
"Fatal exception in coroutines machinery" with kotlinx-coroutines 1.10.2 on JS; the redirect check
is a plain promise chain until that is understood. Sign-in findings: the desktop app's browser pane blocks popups, so the SDK falls back to
`signInWithRedirect`; a redirect with the default `firebaseapp.com` auth domain never completes on
`localhost` (third-party storage partitioning), so `authDomain` is the Hosting domain
(`tripplanner-dev-fe0a4.web.app`, first-party, companion §11). That in turn needs
`https://tripplanner-dev-fe0a4.web.app/__/auth/handler` added to the authorized redirect URIs of the
project's auto-created OAuth web client in Google Cloud Console (Credentials), or Google answers
`redirect_uri_mismatch`. With that URI added, redirect sign-in on the deployed site works end to end (verified
2026-09-24). Not yet done from W0: sign-in on Safari and Firefox, App Check for web, the Kotlin/JS
browser test run in CI.

Still unverified: the Places call shapes in `shared/data/`. Cloud Functions in `firebase/functions/` have no open TODOs.

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
