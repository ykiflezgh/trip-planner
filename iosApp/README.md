# iosApp

Swift host for the Compose Multiplatform UI (design §14). The Xcode project is **generated**
from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) and gitignored:

```bash
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

What the spec sets up:

- A "Compile Kotlin Framework" pre-build script that runs
  `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` (needs a JDK 17+; it uses
  `JAVA_HOME` or `/usr/libexec/java_home`). `FRAMEWORK_SEARCH_PATHS`/`OTHER_LDFLAGS` point at
  the produced `ComposeApp.framework`.
- Swift Package dependencies: firebase-ios-sdk (Auth, Firestore, Functions, RemoteConfig -
  the products GitLive's Kotlin klibs link against), GoogleSignIn-iOS and GoogleMaps
  (`MapViewFactory.swift` wraps GMSMapView behind the Kotlin `NativeMapFactory`/`NativeMap`
  protocols from `composeApp/src/iosMain/.../NativeMap.kt`).
- A post-build script copies `REVERSED_CLIENT_ID` (Google Sign-In callback URL scheme) and,
  when the `MAPS_API_KEY` build setting is empty, `API_KEY` (Maps SDK key) from
  `GoogleService-Info.plist` into the built Info.plist, so nothing key-like is committed.
- `GoogleService-Info.plist` is picked up from this folder as a resource. It is gitignored;
  fetch it with the Firebase CLI:
  `firebase apps:sdkconfig IOS 1:528170989448:ios:07a7d8c91e46d4a33b6c3d --out iosApp/GoogleService-Info.plist`
- `MAPS_API_KEY` is a build setting substituted into Info.plist (empty by default, which
  falls back to the Firebase iOS key as above); set it in an untracked xcconfig or on the
  `xcodebuild` command line once a Maps-restricted key exists (design §11).

Startup order in `iOSApp.swift`: `FirebaseApp.configure()` -> `initKoin(placesApiKey:)`
(shared + iOS Koin modules, `composeApp/src/iosMain/.../IosModule.kt`) -> `MainViewController`.

Command-line build for the simulator (ad-hoc signed by the spec - do **not** pass
`CODE_SIGNING_ALLOWED=NO`: an unsigned app cannot use the keychain and Firebase Auth fails
with "Keychain error" / FIRAuthErrorDomain 17995 at sign-in):

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

Sign in with Apple must be in the first TestFlight build (design §16).
