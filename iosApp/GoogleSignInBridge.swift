import UIKit
import ComposeApp
import FirebaseCore
import GoogleSignIn

/// Swift side of the sign-in boundary (design §6.4): runs the GoogleSignIn-iOS UI and hands
/// the tokens to Kotlin, which exchanges them with Firebase Auth. The client ID comes from
/// GoogleService-Info.plist (via FirebaseApp options); the URL scheme is injected into
/// Info.plist at build time from the same file (see project.yml).
final class GoogleSignInBridgeImpl: GoogleSignInBridge {
    func signIn(callback: @escaping (String?, String?, KotlinBoolean, String?) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            callback(nil, nil, false.kt, "Missing CLIENT_ID in GoogleService-Info.plist"); return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let presenter = Self.topViewController() else {
            callback(nil, nil, false.kt, "No view controller to present sign-in"); return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError? {
                let cancelled = error.domain == kGIDSignInErrorDomain
                    && error.code == GIDSignInError.canceled.rawValue
                callback(nil, nil, cancelled.kt, cancelled ? nil : error.localizedDescription)
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                callback(nil, nil, false.kt, "Google returned no ID token"); return
            }
            callback(idToken, user.accessToken.tokenString, false.kt, nil)
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var vc = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }
}

private extension Bool {
    /// Kotlin/Native boxes `Boolean` inside function types as `KotlinBoolean`.
    var kt: KotlinBoolean { KotlinBoolean(bool: self) }
}
