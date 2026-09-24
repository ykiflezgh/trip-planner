import type { DaySchedule, Leg, Stop } from '../../lib/kotlin/tripPlanner'
import { hhmm, km, minutesLabel } from '../../lib/time'

const warningText = (w: DaySchedule['warnings'][number]) =>
  w.type === 'lateArrival' ? `⚠ ${w.minutes} min late for the pinned time` : w.type === 'overlap' ? `⚠ overlaps the previous entry by ${w.minutes} min` : `⚠ runs ${w.minutes} min past the day's end`

/** The day as a list with computed times, both travel legs between place stops, and warnings (design §6.6). */
export function Agenda({ schedule, stops, legs, selectedId, onSelect }: { schedule: DaySchedule | null; stops: Stop[]; legs: Leg[]; selectedId: string | null; onSelect: (id: string | null) => void }) {
  if (!schedule) return <p className="text-stone-500">Computing…</p>
  const byId = new Map(stops.map((s) => [s.id, s]))
  const warningsFor = (id: string) => schedule.warnings.filter((w) => w.entryId === id)
  const legsBefore = (id: string) => legs.filter((l) => l.to === id)
  if (schedule.entries.length === 0) return <p className="text-stone-500">No stops on this day yet.</p>
  return (
    <ol className="divide-y divide-stone-200 rounded border border-stone-200 bg-white">
      {schedule.entries.map((e, i) => {
        const stop = byId.get(e.id)
        const before = legsBefore(e.id)
        return (
          <li key={e.id}>
            {before.length > 0 && (
              <p className="px-4 pt-2 text-xs text-stone-500">
                {before.map((l) => (
                  <span key={l.mode} className="mr-4">{l.mode === 'walk' ? '🚶' : '🚗'} {l.error ? 'no route' : l.seconds == null ? '…' : `${minutesLabel(Math.round(l.seconds / 60))}${l.meters ? ` · ${km(l.meters)}` : ''}`}</span>
                ))}
              </p>
            )}
            <button onClick={() => onSelect(e.id)} aria-pressed={selectedId === e.id}
              className={`flex w-full gap-3 px-4 py-3 text-left ${selectedId === e.id ? 'bg-indigo-50' : 'hover:bg-stone-50'}`}>
              <span className="w-6 text-stone-400">{i + 1}</span>
              <span className="flex-1">
                <span className="font-medium">{stop?.name ?? e.id}</span>
                <span className="block text-sm text-stone-600">
                  {e.pinned && '📌 '}{hhmm(e.start)} – {hhmm(e.end)}{e.gapBeforeMin > 0 && ` · ${e.gapBeforeMin} min free before`}
                </span>
                {stop?.address && <span className="block text-sm text-stone-500">{stop.address}</span>}
                {warningsFor(e.id).map((w, j) => <span key={j} className="block text-sm text-red-700">{warningText(w)}</span>)}
              </span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}
