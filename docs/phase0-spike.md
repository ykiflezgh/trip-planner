# Phase 0 spike — plan

**Goal (design §17):** prove the three riskiest integrations before committing to the
architecture: (1) the KMP + Compose Multiplatform skeleton builds and runs on both
platforms, (2) Firebase works from common code on both platforms, (3) the two platform
boundaries — Google Maps and Google Sign-In — work behind their shared interfaces.

**Timebox:** 1 week. If an exit criterion can't be met, trigger the documented fallback
(design §16) rather than extending the spike.

## Prerequisites (half-day)

- [x] Create two Firebase projects: `tripplanner-dev`, `tripplanner-prod` (only `dev` is used this week).
- [ ] Enable: **Authentication → Sign-in method → Google** (still off as of 2026-09-17 — `getProjectConfig` shows no IdPs; this needs the console because the admin API wants the OAuth client secret. Until it is on, Firebase rejects the Google ID token with `OPERATION_NOT_ALLOWED`; Apple added with the first TestFlight build), ~~Cloud Firestore~~ (exists: `(default)`, native mode), Cloud Functions, App Check (unenforced for now).
- [ ] Google Cloud console (same project): ~~enable Maps SDK for Android~~ (iOS: the Firebase iOS key is injected as the Maps key at build time; Maps SDK for iOS enabled on it 2026-09-22) (already enabled: the Firebase-created Android key in `google-services.json` renders tiles, and it is in `local.properties` as `MAPS_API_KEY` since 2026-09-22), Maps SDK for iOS, Places API (New). Create two API keys — Android key restricted by package `app.tripplanner` + debug SHA-256; iOS key restricted by bundle ID — each limited to Maps + Places (§11). Until then the app uses the Firebase key.
- [x] ~~Download `google-services.json` → `composeApp/`~~ done 2026-09-17 (Android app `app.tripplanner` registered in `tripplanner-dev-fe0a4`; fetch again with `firebase apps:sdkconfig ANDROID 1:528170989448:android:9ddccf898ddd23473b6c3d --out composeApp/google-services.json`). `GoogleService-Info.plist` done too (iOS app `app.tripplanner` registered; `firebase apps:sdkconfig IOS 1:528170989448:ios:07a7d8c91e46d4a33b6c3d --out iosApp/GoogleService-Info.plist`). Both are gitignored.
- [x] ~~`cd firebase && firebase deploy --only firestore:rules,firestore:indexes` against `tripplanner-dev`~~ deployed 2026-09-17. Debug-keystore SHA-1 + SHA-256 are registered on the Android app (needed for the Android OAuth client and, later, `assetlinks.json`).

## Tasks

| # | Task | Notes |
|---|------|-------|
| 1 | Build the scaffold | **Done 2026-09-17.** `./gradlew build` is green incl. `FractionalIndexTest`; version fixes are listed in the README "Status" section. Xcode build still pending (task 3). |
| 2 | Android app runs | **Done 2026-09-17.** Trip list renders the signed-out empty state on the API 37 emulator; Firebase and Koin (9 definitions) initialise cleanly. Koin is started in `TripPlannerApp` (Application), not `MainActivity`; the Places key is read from the manifest `com.google.android.geo.API_KEY` meta-data. |
| 3 | iOS app runs | **Done 2026-09-22.** `iosApp/project.yml` (XcodeGen) generates the project; Firebase iOS SDK 12.19 via SPM links against GitLive 2.3.0's klibs; `FirebaseApp.configure()` -> `initKoin` -> Compose renders the signed-out trip list on the iPhone 17 Pro simulator. `CADisableMinimumFrameDurationOnPhone` must be true or Compose aborts at startup. |
| 4 | Firebase from common code | **Done 2026-09-22 (both halves).** iOS shows the trip written from Android through the shared `myTrips` listener. Google sign-in works; the "New trip" action writes a `trips` doc + 3 `stops` docs through `TripRepository` (`SampleTrip` fixture) and the trip appears through the live `myTrips` listener; `stops` listener feeds the map. GitLive calls (`document.set`, `where { contains }`, `snapshots`, `data<T>()`) verified at runtime against 2.3.0. Still to do: watch it appear live on iOS. |
| 5 | Sign-in boundary | **Android implemented 2026-09-17** (`composeApp/androidMain/auth/AndroidGoogleSignInProvider.kt`: Credential Manager + `GetSignInWithGoogleOption`; `shared/data/AuthRepository.kt` exchanges the ID token via `signInWithCredential`). Verified on the emulator up to Google's account screen; completing it needs (a) the Google provider enabled in the console (prerequisite above) and (b) a Google account on the device. **iOS implemented 2026-09-22:** GoogleSignIn-iOS 10 via SPM; `iosApp/GoogleSignInBridge.swift` implements the Kotlin `GoogleSignInBridge` protocol (callback -> `suspendCancellableCoroutine` in `IosModule.kt`); the reversed client ID URL scheme is injected into Info.plist at build time from the gitignored plist. Verified on the simulator up to the system "wants to use google.com" prompt and the cancel path; completing it needs a Google account (user). The boundary now returns `GoogleTokens(idToken, accessToken)` because FIRGoogleAuthProvider needs both. |
| 6 | Map boundary | **Android done 2026-09-22.** `TripDetailScreen` -> `MapView` actual shows the trip's stops as markers, camera lands on the first stop, marker tap reaches `TripDetailViewModel.selectStop` (Kermit log + selection card). **iOS implemented 2026-09-22:** GoogleMaps 11 via SPM; `MapViewFactory.swift` (`GoogleMapController`) implements the Kotlin `NativeMap` protocol - Kotlin pushes stops/selection, marker taps come back through the factory callback (`MapView.ios.kt`). Verified on the simulator: the Android-created trip appears on iOS (live sync, task 4 done), markers render, marker tap -> Kotlin selection -> Swift tint + card. Tiles rendered once **Maps SDK for iOS** was enabled for the key in the Cloud console (the SDK logs nothing when it is missing - blank tiles with working markers is the only symptom). |
| 7 | Interop stress test | On iOS, put the map inside a scrolling Compose screen with a bottom sheet; check gesture conflicts, text-field input on the same screen, and frame drops. This is the fallback trigger for §16 ("SwiftUI for those screens only"). |

## Exit criteria

| ID | Criterion | Fallback if failed |
|----|-----------|--------------------|
| E1 | Both apps build in CI-like conditions from a clean checkout in < 15 min | Simplify: drop Coil/Navigation from the skeleton until Phase 1 |
| E2 | A Firestore write on one platform appears on the other in < 2 s via shared code | Wrap native Firebase SDKs behind expect/actual instead of GitLive; re-estimate Phase 1 |
| E3 | Map renders with markers + tap callbacks on both platforms behind the shared `MapView` | Keep the map screen fully native per platform (SwiftUI screen on iOS) |
| E4 | Google sign-in completes on both platforms and yields a Firebase user | Ship Firebase Auth's prebuilt flows for v1; revisit custom UI in v1.1 |
| E5 | Interop stress test shows no blocking gesture/input issues | SwiftUI for map + detail screens on iOS (§16, decision log #1) |

## Out of scope this week

Invites, notifications, travel times, Calendar, Gemini, offline UX polish, CI setup
(tracked as Phase 1–3 issues in `planning/issues.json`).

## End-of-week output

A go/no-go note per exit criterion appended to this file, plus any decision-log
updates to `trip-planner-system-design.md` (§18/§19).
