package app.tripplanner

import platform.UIKit.UIViewController

/** Marker payload handed to the Swift map; kept flat so it crosses the ObjC bridge cheaply. */
data class NativeMapStop(val id: String, val name: String, val lat: Double, val lng: Double)

/**
 * The Swift-side GMSMapView wrapper (iosApp/MapViewFactory.swift). Kotlin owns the itinerary
 * state and pushes it down; the map only renders markers and reports taps (design §6.4).
 */
interface NativeMap {
    val viewController: UIViewController
    fun setStops(stops: List<NativeMapStop>)
    fun setSelectedStop(id: String?)
}

/** Registered by iosApp at startup; `onStopTapped` receives the tapped stop's id. */
interface NativeMapFactory {
    fun create(onStopTapped: (String) -> Unit): NativeMap
}
