import type { DaySchedule, Stop } from '../../lib/kotlin/tripPlanner'
import { hhmm, minutesFrom } from '../../lib/time'

const PX_PER_MIN = 1.2 // 72 px per hour
const SLOT = 15

/** Grid geometry shared with the tests: rows are minutes from the day's start. */
export function layout(schedule: DaySchedule) {
  const origin = schedule.hoursStart
  const endMin = Math.max(minutesFrom(origin, schedule.hoursEnd), minutesFrom(origin, schedule.end)) + SLOT
  const totalMin = Math.ceil(endMin / 60) * 60
  const blocks = schedule.entries.map((e) => ({ id: e.id, top: minutesFrom(origin, e.start), height: Math.max(SLOT, minutesFrom(e.start, e.end)), pinned: e.pinned }))
  const legs = schedule.entries.flatMap((e) => (e.travelBefore ? [{ id: `${e.travelBefore.from}_${e.id}`, top: minutesFrom(origin, e.travelBefore.start), height: minutesFrom(e.travelBefore.start, e.travelBefore.end), pending: e.travelBefore.pending, mode: e.travelBefore.mode }] : []))
  return { totalMin, blocks, legs }
}

/** Read-only vertical time grid (design §6.6): blocks sized by duration, hatched travel legs, gaps as free time. */
export function DayView({ schedule, stops, selectedId, onSelect }: { schedule: DaySchedule | null; stops: Stop[]; selectedId: string | null; onSelect: (id: string | null) => void }) {
  if (!schedule) return <p className="text-stone-500">Computing…</p>
  const byId = new Map(stops.map((s) => [s.id, s]))
  const warn = new Map(schedule.warnings.map((w) => [w.entryId, w]))
  const { totalMin, blocks, legs } = layout(schedule)
  const hours = Array.from({ length: totalMin / 60 + 1 }, (_, i) => i)
  const startHour = +schedule.hoursStart.slice(11, 13)
  return (
    <div className="relative overflow-hidden rounded border border-stone-200 bg-white" style={{ height: totalMin * PX_PER_MIN + 16 }} role="list" aria-label="Day timeline">
      {hours.map((h) => (
        <div key={h} className="absolute left-0 right-0 border-t border-stone-100 text-[11px] text-stone-400" style={{ top: h * 60 * PX_PER_MIN }}>
          <span className="absolute -top-2 left-1">{String((startHour + h) % 24).padStart(2, '0')}:00</span>
        </div>
      ))}
      {legs.map((l) => (
        <div key={l.id} title={`${l.mode} ${l.pending ? '(computing)' : ''}`}
          className="absolute left-14 right-3 rounded-sm opacity-70"
          style={{ top: l.top * PX_PER_MIN, height: Math.max(2, l.height * PX_PER_MIN), backgroundImage: 'repeating-linear-gradient(135deg, #a8a29e 0 3px, transparent 3px 8px)' }} />
      ))}
      {blocks.map((b) => {
        const w = warn.get(b.id)
        return (
          <button key={b.id} role="listitem" onClick={() => onSelect(b.id)} aria-pressed={selectedId === b.id}
            className={`absolute left-14 right-3 overflow-hidden rounded border px-2 py-1 text-left text-sm ${selectedId === b.id ? 'border-indigo-600 bg-indigo-100' : 'border-indigo-300 bg-indigo-50'} ${w ? 'ring-2 ring-red-400' : ''}`}
            style={{ top: b.top * PX_PER_MIN, height: b.height * PX_PER_MIN }}>
            <span className="font-medium">{b.pinned && '📌 '}{byId.get(b.id)?.name ?? b.id}</span>
            <span className="ml-2 text-stone-600">{hhmm(schedule.entries.find((e) => e.id === b.id)!.start)} – {hhmm(schedule.entries.find((e) => e.id === b.id)!.end)}</span>
            {w && <span className="ml-2 rounded bg-red-100 px-1 text-xs text-red-800">{w.type} {w.minutes} min</span>}
          </button>
        )
      })}
    </div>
  )
}
