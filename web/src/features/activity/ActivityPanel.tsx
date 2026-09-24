import { useEffect, useMemo } from 'react'
import { activity } from '../../lib/kotlin/tripPlanner'
import { useKotlinState } from '../../hooks/useKotlinState'

const when = (ms: number | null) => {
  if (!ms) return 'just now'
  const diff = Date.now() - ms
  if (diff < 60_000) return 'just now'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} min ago`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} h ago`
  return new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

/** Live activity feed for the open trip (design §10); rows are worded in Kotlin. */
export function ActivityPanel({ tripId, onOpenStop, onClose }: { tripId: string; onOpenStop: (day: number, stopId: string | null) => void; onClose: () => void }) {
  const facade = useMemo(() => activity(tripId), [tripId])
  useEffect(() => () => facade.close(), [facade])
  const state = useKotlinState(facade)
  return (
    <section aria-label="Activity" className="flex h-full flex-col rounded border border-stone-200 bg-white">
      <header className="flex items-center justify-between border-b border-stone-200 px-3 py-2">
        <h2 className="text-sm font-semibold">Activity</h2>
        <button className="text-sm text-stone-500" onClick={onClose} aria-label="Close activity">✕</button>
      </header>
      {!state || state.loading ? <p className="p-3 text-sm text-stone-500">Loading…</p> : state.error ? <p className="p-3 text-sm text-red-700">{state.error}</p> : state.rows.length === 0 ? <p className="p-3 text-sm text-stone-500">No changes yet.</p> : (
        <ol className="max-h-[560px] divide-y divide-stone-100 overflow-auto">
          {state.rows.map((r) => (
            <li key={r.id}>
              <button className={`w-full px-3 py-2 text-left text-sm hover:bg-stone-50 ${r.mine ? 'text-stone-500' : ''}`} onClick={() => onOpenStop(r.day, r.stopId)}>
                <span className="block">{r.text}</span>
                <span className="block text-xs text-stone-400">{when(r.createdAtMs)}</span>
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
