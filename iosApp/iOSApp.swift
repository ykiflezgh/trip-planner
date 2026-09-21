import SwiftUI
import ComposeApp
// import FirebaseCore
// import GoogleMaps

@main
struct iOSApp: App {
    init() {
        // Phase 0 tasks (planning/issues.json #2, #4):
        // FirebaseApp.configure()
        // GMSServices.provideAPIKey(<key from GoogleService-Info-style plist, not source>)
    }
    var body: some Scene {
        WindowGroup { ComposeView().ignoresSafeArea() }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(mapFactory: { MapViewFactory.make() })
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
