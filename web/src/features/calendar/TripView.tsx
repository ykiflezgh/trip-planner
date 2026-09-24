import { useState } from 'react'
import type { DaySchedule, Stop } from '../../lib/kotlin/tripPlanner'
import { TimeGrid, type GridEdit } from './DayView'

const PAGE = 7

/** Up to seven day columns per page (design §6.6); editable at 1024 px and above (companion §6.7). */
export function TripView({ schedules, stopsByDay, dayLabel, selectedDay, selectedId, onSelect, onSelectDay, edit }: {
  schedules: (DaySchedule | null)[]; stopsByDay: Stop[][]; dayLabel: (d: number) => string; selectedDay: number; selectedId: string | null
  onSelect: (day: number, id: string | null) => void; onSelectDay: (day: number) => void; edit?: GridEdit
}) {
  const [page, setPage] = useState(Math.floor(selectedDay / PAGE))
  const pages = Math.ceil(schedules.length / PAGE)
  const days = Array.from({ length: Math.min(PAGE, schedules.length - page * PAGE) }, (_, i) => page * PAGE + i)
  // The day buttons above the grid are its visible headers; each column still carries the day as its accessible name.
  const columns = days.map((d) => ({ day: d, ariaLabel: dayLabel(d), schedule: schedules[d], stops: stopsByDay[d] ?? [] }))
  const select = (id: string | null) => { const d = days.find((x) => (stopsByDay[x] ?? []).some((s) => s.id === id)); if (d != null) onSelect(d, id) }
  return (
    <div>
      {pages > 1 && (
        <p className="mb-2 flex items-center gap-2 text-sm">
          <button type="button" className="min-h-6 min-w-6 rounded border p-1 leading-none" aria-label="Previous days" disabled={page === 0} onClick={() => setPage(page - 1)}><span aria-hidden="true">‹</span></button>
          Days {page * PAGE + 1}–{Math.min((page + 1) * PAGE, schedules.length)}
          <button type="button" className="min-h-6 min-w-6 rounded border p-1 leading-none" aria-label="Next days" disabled={page >= pages - 1} onClick={() => setPage(page + 1)}><span aria-hidden="true">›</span></button>
        </p>
      )}
      <div className="mb-1 flex text-xs text-stone-600"><span className="w-12" />{days.map((d) => <button key={d} type="button" aria-pressed={d === selectedDay} className={`min-h-6 flex-1 px-2 py-1 text-left ${d === selectedDay ? 'font-medium text-indigo-700' : ''}`} onClick={() => onSelectDay(d)}>{dayLabel(d)}</button>)}</div>
      <TimeGrid columns={columns} selectedId={selectedId} onSelect={select} edit={edit} compact />
    </div>
  )
}
