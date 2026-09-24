import { StrictMode } from 'react'
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
  const flags = await loadFlags()
  start(config, import.meta.env.VITE_APP_LINK_HOST ?? '', flags.calendarFeedEnabled, flags.suggestOrderEnabled)
  void resumeRedirect().catch(() => undefined)
  if (import.meta.env.VITE_FCM_VAPID_KEY && typeof Notification !== 'undefined' && Notification.permission === 'granted') void import('./lib/push/webPush').then((m) => m.resumePush(config))
  reportWebVitals(Boolean(config.measurementId))
  if (import.meta.env.VITE_SENTRY_DSN) void import('./lib/sentry').then((m) => m.initSentry())
  // Dev only: lets the browser console poke the facades directly.
  if (import.meta.env.DEV) (window as unknown as { __tp: unknown }).__tp = { tripList, tripDetail, computeDay, flags }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ErrorBoundary>
        <RouterProvider router={router} />
      </ErrorBoundary>
    </StrictMode>,
  )
}

void boot()
