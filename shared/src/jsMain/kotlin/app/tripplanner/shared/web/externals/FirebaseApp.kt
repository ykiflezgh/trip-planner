@file:JsModule("firebase/app")

package app.tripplanner.shared.web.externals

/** Lets the facade reuse a default app the page already created (Remote Config is read before the core starts). */
external fun getApps(): Array<dynamic>
