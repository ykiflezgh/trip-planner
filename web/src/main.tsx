import { Fragment, StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import './index.css'
import { router } from './app/router'
import { computeDay, resumeRedirect, start, tripDetail, tripList } from './lib/kotlin/tripPlanner'
import { loadFlags } from './lib/flags'
import { ErrorBoundary } from './components/ErrorBoundary'
import { reportWebVitals } from './lib/vitals'

import { firebaseConfig as config } from './app/session'

async function boot() {
  // Remote Config first (companion §14): the Kotlin core reads its client flags once at start.
  const { initializeApp, getApps } = await import('firebase/app')
  if (getApps().length === 0) initializeApp(config)
  // E2E (web/e2e): emulators only, nothing leaves the machine. Before loadFlags()/start() so every SDK instance is redirected.
  if (import.meta.env.VITE_USE_EMULATORS === '1') await (await import('./lib/emulators')).connectEmulators()
  const flags = await loadFlags()
  start(config, import.meta.env.VITE_APP_LINK_HOST ?? '', flags.calendarFeedEnabled, flags.suggestOrderEnabled)
  void resumeRedirect().catch(() => undefined)
  if (import.meta.env.VITE_FCM_VAPID_KEY && typeof Notification !== 'undefined' && Notification.permission === 'granted') void import('./lib/push/webPush').then((m) => m.resumePush(config))
  reportWebVitals(Boolean(config.measurementId))
  if (import.meta.env.VITE_SENTRY_DSN) void import('./lib/sentry').then((m) => m.initSentry())
  // Dev only: lets the browser console poke the facades directly.
  if (import.meta.env.DEV) (window as unknown as { __tp: unknown }).__tp = { tripList, tripDetail, computeDay, flags }

  // E2E (web/e2e) runs on the dev server, where StrictMode's dev-only double mount runs TripPage's close-on-unmount
  // effect against the memoized facade and leaves the trip screen empty (bug noted in the suite's report). Production
  // never double-mounts, so the e2e run renders without StrictMode to match it.
  const Root = import.meta.env.VITE_USE_EMULATORS === '1' ? Fragment : StrictMode
  createRoot(document.getElementById('root')!).render(
    <Root>
      <ErrorBoundary>
        <RouterProvider router={router} />
      </ErrorBoundary>
    </Root>,
  )
}

void boot()
