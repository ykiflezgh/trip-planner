import { createBrowserRouter } from 'react-router'
import { Shell } from './Shell'
import { TripListPage } from '../features/trips/TripListPage'
import { TripPage } from '../features/trips/TripPage'
import { JoinPage } from '../features/invites/JoinPage'

/**
 * URL carries trip, day and view so members can share "Day 2, Day view" (companion §8.2).
 * No basename: the app lives under /app/** and also owns /join/{code} (companion §8.5), which
 * Hosting rewrites to the same shell.
 */
export const router = createBrowserRouter([
  { path: '/', Component: Shell, children: [
    { path: 'app', Component: TripListPage },
    { path: 'app/t/:tripId', Component: TripPage },
    { path: 'join/:code', Component: JoinPage },
    { path: '*', Component: TripListPage },
  ] },
])
