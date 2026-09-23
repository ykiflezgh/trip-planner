import SwiftUI
import ComposeApp
import FirebaseCore
import GoogleSignIn
import GoogleMaps

@main
struct iOSApp: App {
    // Push notifications need UIApplicationDelegate callbacks (design §10, PushBridge.swift).
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // GoogleService-Info.plist is gitignored; fetch it with the Firebase CLI (docs/phase0-spike.md).
        FirebaseApp.configure()
        // Maps/Places key lives in Info.plist (MAPS_API_KEY), never in source (design §11).
        let mapsKey = Bundle.main.object(forInfoDictionaryKey: "MAPS_API_KEY") as? String ?? ""
        GMSServices.provideAPIKey(mapsKey)
        let appLinkHost = Bundle.main.object(forInfoDictionaryKey: "APP_LINK_HOST") as? String ?? ""
        IosModuleKt.doInitKoin(placesApiKey: mapsKey, googleSignIn: GoogleSignInBridgeImpl(), push: PushBridgeImpl(), appLinkHost: appLinkHost)
    }
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // Google Sign-In returns through the reversed-client-ID URL scheme; anything else may
                // be an invite link (tripplanner://join/{code}, or the https link once Universal
                // Links are configured - design §8.2).
                .onOpenURL { url in
                    if !GIDSignIn.sharedInstance.handle(url) { _ = IosModuleKt.offerInviteUrl(url: url.absoluteString) }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL { _ = IosModuleKt.offerInviteUrl(url: url.absoluteString) }
                }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(mapFactory: GoogleMapFactory())
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
