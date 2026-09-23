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

/// UIViewController that reports layout so camera fits can wait for a non-zero map frame.
private final class MapHostController: UIViewController {
    var onLayout: (() -> Void)?
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        onLayout?()
    }
}

final class GoogleMapController: NSObject, NativeMap, GMSMapViewDelegate {
    private static let stopZoom: Float = 14
    private static let fitPadding: CGFloat = 56
    private let onStopTapped: (String) -> Void
    private let mapView: GMSMapView
    private let host: MapHostController
    private var markers: [String: GMSMarker] = [:]
    private var polyline: GMSPolyline?
    private var selectedId: String?
    private var lastIds: [String] = []
    private var pendingFit: GMSCameraUpdate?

    var viewController: UIViewController { host }

    init(onStopTapped: @escaping (String) -> Void) {
        self.onStopTapped = onStopTapped
        let options = GMSMapViewOptions()
        options.camera = GMSCameraPosition(latitude: 0, longitude: 0, zoom: 1)
        mapView = GMSMapView(options: options)
        host = MapHostController()
        super.init()
        mapView.delegate = self
        host.view = mapView
        host.onLayout = { [weak self] in
            guard let self, let fit = self.pendingFit, !self.mapView.bounds.isEmpty else { return }
            self.pendingFit = nil
            self.mapView.moveCamera(fit)
        }
    }

    /// Markers + route polyline in itinerary order; refits the camera when the *set* of stops
    /// changes (tab switch, add, remove) and nothing is selected, not on every reorder frame.
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

        polyline?.map = nil
        polyline = nil
        if stops.count >= 2 {
            let path = GMSMutablePath()
            stops.forEach { path.add(CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng)) }
            let line = GMSPolyline(path: path)
            line.strokeColor = .systemBlue
            line.strokeWidth = 4
            line.zIndex = -1
            line.map = mapView
            polyline = line
        }

        let ids = stops.map(\.id)
        let setChanged = Set(ids) != Set(lastIds)
        lastIds = ids
        if setChanged, selectedId == nil, let fit = fitUpdate(for: stops) {
            if mapView.bounds.isEmpty { pendingFit = fit } else { mapView.animate(with: fit) }
        }
    }

    func setSelectedStop(id: String?) {
        selectedId = id
        applySelectionStyle()
        if let id, let marker = markers[id] {
            let zoom = max(mapView.camera.zoom, Self.stopZoom)
            mapView.animate(to: GMSCameraPosition(target: marker.position, zoom: zoom))
        }
    }

    private func fitUpdate(for stops: [NativeMapStop]) -> GMSCameraUpdate? {
        guard let first = stops.first else { return nil }
        let firstCoord = CLLocationCoordinate2D(latitude: first.lat, longitude: first.lng)
        if stops.count == 1 { return GMSCameraUpdate.setTarget(firstCoord, zoom: Self.stopZoom) }
        let bounds = stops.dropFirst().reduce(GMSCoordinateBounds(coordinate: firstCoord, coordinate: firstCoord)) {
            $0.includingCoordinate(CLLocationCoordinate2D(latitude: $1.lat, longitude: $1.lng))
        }
        return GMSCameraUpdate.fit(bounds, withPadding: Self.fitPadding)
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
