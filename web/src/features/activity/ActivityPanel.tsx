import { useEffect, useRef } from 'react'
import { activity } from '../../lib/kotlin/tripPlanner'
import { useKotlinState } from '../../hooks/useKotlinState'
import { useFacade } from '../../hooks/useFacade'

const when = (ms: number | null) => {
  if (!ms) return 'just now'
  const diff = Date.now() - ms
  if (diff < 60_000) return 'just now'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} min ago`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} h ago`
  return new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

/** Live activity feed for the open trip (design §10); rows are worded in Kotlin. `autoFocus`: the opener asks for focus (below). */
export function ActivityPanel({ tripId, autoFocus = false, onOpenStop, onClose }: { tripId: string; autoFocus?: boolean; onOpenStop: (day: number, stopId: string | null) => void; onClose: () => void }) {
  const facade = useFacade(() => activity(tripId), [tripId])
  const state = useKotlinState(facade)
  // Opened from the More menu at the top of the page and mounted near its end: focus follows so a
  // keyboard user is not sent back through the calendar to reach it (WCAG 2.4.3). Only when that menu
  // opened it (`autoFocus`): a page load or reload of a URL with ?panel=activity mounts the panel too,
  // and taking focus there would pull the reader away from the top of the page.
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { if (autoFocus) heading.current?.focus() }, [autoFocus])
  return (
    <section aria-label="Activity" className="flex h-full flex-col rounded border border-stone-200 bg-white">
      <header className="flex items-center justify-between border-b border-stone-200 px-3 py-2">
        <h2 ref={heading} tabIndex={-1} className="rounded text-sm font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500">Activity</h2>
        <button type="button" className="rounded px-1 text-sm text-stone-600 hover:text-stone-900" onClick={onClose} aria-label="Close activity">✕</button>
      </header>
      {!state || state.loading ? <p className="p-3 text-sm text-stone-600">Loading…</p> : state.error ? <p className="p-3 text-sm text-red-700">{state.error}</p> : state.rows.length === 0 ? <p className="p-3 text-sm text-stone-600">No changes yet.</p> : (
        <ol className="max-h-[560px] divide-y divide-stone-100 overflow-auto">
          {state.rows.map((r) => (
            <li key={r.id}>
              <button type="button" className={`w-full px-3 py-2 text-left text-sm hover:bg-stone-50 ${r.mine ? 'text-stone-600' : ''}`} onClick={() => onOpenStop(r.day, r.stopId)}>
                <span className="block">{r.text}</span>
                <span className="block text-xs text-stone-600">{when(r.createdAtMs)}</span>
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
