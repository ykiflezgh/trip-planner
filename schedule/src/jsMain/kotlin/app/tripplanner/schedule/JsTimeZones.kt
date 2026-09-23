package app.tripplanner.schedule

/**
 * kotlinx-datetime on JS resolves named zones through js-joda; the timezone database is a
 * separate npm module that must be imported once per process (the ICS feed needs the trip
 * zone for VTIMEZONE and epoch conversion).
 */
@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

private var loaded = false

internal actual fun ensureTimeZones() {
    if (!loaded) {
        JsJodaTimeZoneModule // referencing the external object pulls the module in
        loaded = true
    }
}
