import SwiftUI
import ComposeApp
import FirebaseCore
// import GoogleMaps

@main
struct iOSApp: App {
    init() {
        // GoogleService-Info.plist is gitignored; fetch it with the Firebase CLI (docs/phase0-spike.md).
        FirebaseApp.configure()
        // Maps/Places key lives in Info.plist (MAPS_API_KEY), never in source (design §11).
        let mapsKey = Bundle.main.object(forInfoDictionaryKey: "MAPS_API_KEY") as? String ?? ""
        // GMSServices.provideAPIKey(mapsKey)  // spike task 6, iOS half
        IosModuleKt.doInitKoin(placesApiKey: mapsKey)
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
