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
  the products GitLive's Kotlin klibs link against) and GoogleSignIn-iOS. GoogleMaps is
  added when spike task 6 lands on iOS.
- A post-build script copies `REVERSED_CLIENT_ID` from `GoogleService-Info.plist` into the
  built Info.plist as the Google Sign-In callback URL scheme, so no client ID is committed.
- `GoogleService-Info.plist` is picked up from this folder as a resource. It is gitignored;
  fetch it with the Firebase CLI:
  `firebase apps:sdkconfig IOS 1:528170989448:ios:07a7d8c91e46d4a33b6c3d --out iosApp/GoogleService-Info.plist`
- `MAPS_API_KEY` is a build setting substituted into Info.plist (empty by default); set it in
  an untracked xcconfig or on the `xcodebuild` command line, never in `project.yml`.

Startup order in `iOSApp.swift`: `FirebaseApp.configure()` -> `initKoin(placesApiKey:)`
(shared + iOS Koin modules, `composeApp/src/iosMain/.../IosModule.kt`) -> `MainViewController`.

Command-line build for the simulator:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO build
```

Sign in with Apple must be in the first TestFlight build (design §16).
