import { createBrowserRouter } from 'react-router'
import { Shell } from './Shell'
import { TripListPage } from '../features/trips/TripListPage'
import { TripPage } from '../features/trips/TripPage'

/** URL carries trip, day and view so members can share "Day 2, Day view" (companion §8.2). */
export const router = createBrowserRouter(
  [{ path: '/', Component: Shell, children: [{ index: true, Component: TripListPage }, { path: 't/:tripId', Component: TripPage }] }],
  { basename: '/app' },
)
