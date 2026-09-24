import { createBrowserRouter } from 'react-router'
import { Shell } from './Shell'
import { TripListPage } from '../features/trips/TripListPage'
import { ComingSoon } from './ComingSoon'
import { flags } from '../lib/flags'

/**
 * URL carries trip, day and view so members can share "Day 2, Day view" (companion §8.2).
 * No basename: the app lives under /app/** and also owns /join/{code} (companion §8.5), which
 * Hosting rewrites to the same shell. The trip page (dnd-kit, Maps, dialogs) and the join page are
 * route-level lazy chunks so the trip list starts under the 200 KB budget (companion §6.5).
 */
export const router = createBrowserRouter([
  { path: '/', Component: Shell, HydrateFallback: () => <p className="p-4">Loading…</p>, children: [
    { path: 'app', Component: TripListPage },
    // Rollout gate (companion §14): the trip screen is behind web_client_enabled; the list and /join stay open.
    { path: 'app/t/:tripId', lazy: async () => { const m = await import('../features/trips/TripPage'); return { Component: () => (flags.webClientEnabled ? <m.TripPage /> : <ComingSoon />) } } },
    { path: 'join/:code', lazy: async () => ({ Component: (await import('../features/invites/JoinPage')).JoinPage }) },
    { path: '*', Component: TripListPage },
  ] },
])
