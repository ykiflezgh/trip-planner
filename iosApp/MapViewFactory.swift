import UIKit
import ComposeApp
import GoogleMaps

/// Thin Swift wrapper around GMSMapView (design §6.4). Kept deliberately small: all itinerary
/// logic stays in Kotlin; this only renders markers, follows the selection and reports taps.
final class GoogleMapFactory: NativeMapFactory {
    func create(onStopTapped: @escaping (String) -> Void) -> NativeMap {
        GoogleMapController(onStopTapped: onStopTapped)
    }
}

final class GoogleMapController: NSObject, NativeMap, GMSMapViewDelegate {
    private static let stopZoom: Float = 13
    private let onStopTapped: (String) -> Void
    private let mapView: GMSMapView
    private var markers: [String: GMSMarker] = [:]
    private var selectedId: String?

    let viewController: UIViewController

    init(onStopTapped: @escaping (String) -> Void) {
        self.onStopTapped = onStopTapped
        let options = GMSMapViewOptions()
        options.camera = GMSCameraPosition(latitude: 0, longitude: 0, zoom: 1)
        mapView = GMSMapView(options: options)
        viewController = UIViewController()
        super.init()
        mapView.delegate = self
        viewController.view = mapView
    }

    func setStops(stops: [NativeMapStop]) {
        markers.values.forEach { $0.map = nil }
        markers = [:]
        for stop in stops {
            let marker = GMSMarker(position: CLLocationCoordinate2D(latitude: stop.lat, longitude: stop.lng))
            marker.title = stop.name
            marker.userData = stop.id
            marker.map = mapView
            markers[stop.id] = marker
        }
        applySelectionStyle()
        // Before any selection, land on the first stop once stops arrive.
        if selectedId == nil, let first = stops.first {
            mapView.camera = GMSCameraPosition(latitude: first.lat, longitude: first.lng, zoom: Self.stopZoom)
        }
    }

    func setSelectedStop(id: String?) {
        selectedId = id
        applySelectionStyle()
        if let id, let marker = markers[id] {
            mapView.animate(to: GMSCameraPosition(target: marker.position, zoom: Self.stopZoom))
        }
    }

    private func applySelectionStyle() {
        for (id, marker) in markers {
            let selected = id == selectedId
            marker.icon = GMSMarker.markerImage(with: selected ? .systemBlue : nil)
            marker.zIndex = selected ? 1 : 0
        }
    }

    // MARK: GMSMapViewDelegate

    func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
        if let id = marker.userData as? String { onStopTapped(id) }
        return true // consumed: the Compose selection card replaces the info window
    }
}
