import UIKit
// import GoogleMaps

/// Thin Swift wrapper around GMSMapView (design SS6.4). Kept deliberately small:
/// all itinerary logic stays in Kotlin; this only renders markers and reports taps.
enum MapViewFactory {
    static func make() -> UIViewController {
        // Phase 0 spike: return a UIViewController whose view is a GMSMapView.
        // Until GoogleMaps is added via SPM, return a placeholder so the app runs.
        let vc = UIViewController()
        vc.view.backgroundColor = .systemGray5
        let label = UILabel()
        label.text = "GMSMapView goes here (Phase 0)"
        label.translatesAutoresizingMaskIntoConstraints = false
        vc.view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: vc.view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: vc.view.centerYAnchor),
        ])
        return vc
    }
}
