import UIKit
import UserNotifications
import ComposeApp
import FirebaseMessaging

/// Swift side of the push boundary (design §6.4, §10): permission prompt, APNs registration,
/// FCM token hand-off to Kotlin, foreground presentation, and the tap -> deep link.
/// FirebaseAppDelegateProxyEnabled is off (Info.plist), so the APNs token is forwarded here.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        // Registers with APNs when the user already granted permission on an earlier launch.
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional {
                DispatchQueue.main.async { application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        NSLog("Push: APNs token received (%d bytes)", deviceToken.count)
        Messaging.messaging().apnsToken = deviceToken
        // The delegate only fires on refresh; Firebase Messaging refuses to mint an FCM token
        // before the APNs token is set, so fetch it explicitly now.
        Messaging.messaging().token { token, error in
            if let error = error { NSLog("Push: FCM token error: %@", error.localizedDescription) }
            IosModuleKt.setPushToken(token: token)
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Simulators without APNs support (Intel Macs / older Xcode) land here; no push on this device.
        NSLog("Push: APNs registration failed: %@", error.localizedDescription)
    }

    // MARK: MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        NSLog("Push: FCM token %@", fcmToken == nil ? "cleared" : "refreshed")
        IosModuleKt.setPushToken(token: fcmToken)
    }

    // MARK: UNUserNotificationCenterDelegate

    /// Show the banner even while the app is in front; the feed updates live anyway.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if let tripId = info["tripId"] as? String, !tripId.isEmpty {
            let stopId = (info["stopId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            _ = IosModuleKt.offerTripLink(tripId: tripId, stopId: stopId)
        }
        completionHandler()
    }
}

/// Kotlin's `PushBridge`: one permission prompt, then APNs registration on the main thread.
final class PushBridgeImpl: PushBridge {
    func requestPermission(callback: @escaping (KotlinBoolean) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            DispatchQueue.main.async {
                if granted { UIApplication.shared.registerForRemoteNotifications() }
                callback(KotlinBoolean(bool: granted))
            }
        }
    }
}

/// Kotlin's `ReminderBridge`: "time to leave" reminders as calendar-trigger local notifications.
/// A tap carries tripId/stopId in userInfo and is handled by AppDelegate like an activity push.
final class ReminderBridgeImpl: ReminderBridge {
    func schedule(id: String, tripId: String, stopId: String, fireAtEpochSeconds: Int64, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.threadIdentifier = tripId
        content.userInfo = ["tripId": tripId, "stopId": stopId]
        let date = Date(timeIntervalSince1970: TimeInterval(fireAtEpochSeconds))
        let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger)) { error in
            if let error = error { NSLog("Reminders: schedule failed for %@: %@", id, error.localizedDescription) }
        }
    }

    func cancel(ids: [String]) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }
}
