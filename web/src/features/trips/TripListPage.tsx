import { useMemo } from 'react'
import { Link } from 'react-router'
import { useKotlinState } from '../../hooks/useKotlinState'
import { listFacade } from '../../app/Shell'

export function TripListPage() {
  const facade = useMemo(() => listFacade(), [])
  const state = useKotlinState(facade)
  if (!state) return <p className="p-4">Starting…</p>
  if (!state.signedIn) return <p className="p-4 text-stone-600">Sign in to see your trips.</p>
  if (state.loading) return <p className="p-4">Loading trips…</p>
  return (
    <main className="mx-auto max-w-3xl p-4">
      <h1 className="mb-3 text-xl font-semibold">Your trips</h1>
      <ul className="divide-y divide-stone-200 rounded border border-stone-200 bg-white">
        {state.trips.map((t) => (
          <li key={t.id}>
            <Link to={`/app/t/${t.id}`} className="flex items-baseline justify-between px-4 py-3 hover:bg-stone-50">
              <span className="font-medium">{t.name || 'Untitled trip'}{t.pendingSync && <span className="ml-2 text-xs text-stone-400">syncing…</span>}</span>
              <span className="text-sm text-stone-500">{t.startDate} → {t.endDate} · {t.timeZone}</span>
            </Link>
          </li>
        ))}
        {state.trips.length === 0 && <li className="px-4 py-3 text-stone-500">No trips yet. Create one in the app.</li>}
      </ul>
    </main>
  )
}
