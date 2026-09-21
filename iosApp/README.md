# iosApp

This folder ships Swift sources only — generate the Xcode project once, locally:

1. Easiest path: create a fresh project with the JetBrains KMP wizard
   (https://kmp.jetbrains.com), tick iOS + "share UI", then replace its
   `iosApp` Swift files with these and point the project at this repo's
   Gradle modules. The wizard wires the `embedAndSign` build phase for you
   (design SS14).
2. Add Swift Package dependencies: firebase-ios-sdk, GoogleSignIn-iOS,
   GoogleMaps (ios-maps-sdk).
3. Drop `GoogleService-Info.plist` (from the Firebase console) into the
   app target. Never commit it — it's gitignored.
4. Uncomment the Firebase/GoogleMaps lines in `iOSApp.swift` and implement
   `MapViewFactory.make()` with a real `GMSMapView` — that's Phase 0 exit
   criterion E3 (docs/phase0-spike.md).

Sign in with Apple must be in the first TestFlight build (design SS16).
