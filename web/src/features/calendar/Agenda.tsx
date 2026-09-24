import { useState } from 'react'
import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { DaySchedule, Leg, Stop } from '../../lib/kotlin/tripPlanner'
import { hhmm, km, minutesLabel } from '../../lib/time'
import { neighboursAfterMove } from '../../lib/order'

const warningText = (w: DaySchedule['warnings'][number]) =>
  w.type === 'lateArrival' ? `⚠ ${w.minutes} min late for the pinned time` : w.type === 'overlap' ? `⚠ overlaps the previous entry by ${w.minutes} min` : `⚠ runs ${w.minutes} min past the day's end`

export interface AgendaEdit {
  move: (stopId: string, afterOrder: string | null, beforeOrder: string | null) => void
  moveToDay: (stopId: string, day: number) => void
  remove: (stopId: string) => void
  dayCount: number
  selectedDay: number
}

/**
 * The day as a list with computed times, both travel legs between place stops, and warnings
 * (design §6.6). With [edit], rows reorder by drag (pointer) and by keyboard (Space, arrows,
 * Space), plus explicit Move up / down / to day / Delete actions so every change has a non-drag path.
 */
export function Agenda({ schedule, stops, legs, selectedId, onSelect, edit }: { schedule: DaySchedule | null; stops: Stop[]; legs: Leg[]; selectedId: string | null; onSelect: (id: string | null) => void; edit?: AgendaEdit }) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }))
  if (!schedule) return <p className="text-stone-500">Computing…</p>
  if (schedule.entries.length === 0) return <p className="text-stone-500">No stops on this day yet.</p>
  const byId = new Map(stops.map((s) => [s.id, s]))
  // The list order is the stored order (stops are sorted by key); the schedule keeps it.
  const ordered = schedule.entries.map((e) => byId.get(e.id)).filter((s): s is Stop => !!s)
  const ids = ordered.map((s) => s.id)

  const onDragEnd = (ev: DragEndEvent) => {
    if (!edit || !ev.over || ev.active.id === ev.over.id) return
    const from = ids.indexOf(String(ev.active.id)); const to = ids.indexOf(String(ev.over.id))
    if (from < 0 || to < 0) return
    const { afterOrder, beforeOrder } = neighboursAfterMove(ordered, from, to)
    edit.move(String(ev.active.id), afterOrder, beforeOrder)
  }
  const nudge = (i: number, delta: number) => {
    if (!edit) return
    const to = i + delta
    if (to < 0 || to >= ordered.length) return
    const { afterOrder, beforeOrder } = neighboursAfterMove(ordered, i, to)
    edit.move(ordered[i].id, afterOrder, beforeOrder)
  }

  const rows = schedule.entries.map((e, i) => (
    <Row key={e.id} entry={e} index={i} stop={byId.get(e.id)} legs={legs.filter((l) => l.to === e.id)} warnings={schedule.warnings.filter((w) => w.entryId === e.id)}
      selected={selectedId === e.id} onSelect={() => onSelect(e.id)} edit={edit} count={ordered.length} nudge={(d) => nudge(i, d)} />
  ))
  return (
    <ol className="divide-y divide-stone-200 rounded border border-stone-200 bg-white">
      {edit ? (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext items={ids} strategy={verticalListSortingStrategy}>{rows}</SortableContext>
        </DndContext>
      ) : rows}
    </ol>
  )
}

function Row({ entry: e, index, stop, legs, warnings, selected, onSelect, edit, count, nudge }: {
  entry: DaySchedule['entries'][number]; index: number; stop?: Stop; legs: Leg[]; warnings: DaySchedule['warnings']; selected: boolean; onSelect: () => void
  edit?: AgendaEdit; count: number; nudge: (delta: number) => void
}) {
  const sortable = useSortable({ id: e.id, disabled: !edit })
  const [menu, setMenu] = useState(false)
  const style = { transform: CSS.Transform.toString(sortable.transform), transition: sortable.transition }
  return (
    <li ref={sortable.setNodeRef} style={style} className={sortable.isDragging ? 'relative z-10 bg-white shadow-lg' : ''}>
      {legs.length > 0 && (
        <p className="px-4 pt-2 text-xs text-stone-500">
          {legs.map((l) => (
            <span key={l.mode} className="mr-4">{l.mode === 'walk' ? '🚶' : '🚗'} {l.error ? 'no route' : l.seconds == null ? '…' : `${minutesLabel(Math.round(l.seconds / 60))}${l.meters ? ` · ${km(l.meters)}` : ''}`}</span>
          ))}
        </p>
      )}
      <div className={`flex items-start gap-2 px-2 py-3 ${selected ? 'bg-indigo-50' : ''}`}>
        {edit && (
          <button {...sortable.attributes} {...sortable.listeners} aria-label={`Reorder ${stop?.name ?? ''}: press Space, move with the arrow keys, press Space again`}
            className="cursor-grab touch-none rounded px-1 text-stone-400 hover:bg-stone-100 focus:ring-2 focus:ring-indigo-400">≡</button>
        )}
        <button onClick={onSelect} aria-pressed={selected} className="flex flex-1 gap-3 text-left">
          <span className="w-5 text-stone-400">{index + 1}</span>
          <span className="flex-1">
            <span className="font-medium">{stop?.name ?? e.id}</span>
            <span className="block text-sm text-stone-600">{e.pinned && '📌 '}{hhmm(e.start)} – {hhmm(e.end)}{e.gapBeforeMin > 0 && ` · ${e.gapBeforeMin} min free before`}</span>
            {stop?.address && <span className="block text-sm text-stone-500">{stop.address}</span>}
            {stop?.notes && <span className="block text-sm text-stone-500">{stop.notes}</span>}
            {warnings.map((w, j) => <span key={j} className="block text-sm text-red-700">{warningText(w)}</span>)}
          </span>
        </button>
        {edit && (
          <span className="relative">
            <button aria-label="Stop actions" aria-expanded={menu} onClick={() => setMenu(!menu)} className="rounded px-2 py-1 text-stone-500 hover:bg-stone-100">⋯</button>
            {menu && (
              <span className="absolute right-0 z-20 mt-1 flex w-44 flex-col rounded border border-stone-200 bg-white py-1 text-sm shadow-lg" onMouseLeave={() => setMenu(false)}>
                <button className="px-3 py-1.5 text-left hover:bg-stone-50 disabled:opacity-40" disabled={index === 0} onClick={() => { setMenu(false); nudge(-1) }}>Move up</button>
                <button className="px-3 py-1.5 text-left hover:bg-stone-50 disabled:opacity-40" disabled={index === count - 1} onClick={() => { setMenu(false); nudge(1) }}>Move down</button>
                {Array.from({ length: edit.dayCount }, (_, d) => d).filter((d) => d !== edit.selectedDay).map((d) => (
                  <button key={d} className="px-3 py-1.5 text-left hover:bg-stone-50" onClick={() => { setMenu(false); edit.moveToDay(e.id, d) }}>Move to Day {d + 1}</button>
                ))}
                <button className="px-3 py-1.5 text-left text-red-700 hover:bg-red-50" onClick={() => { setMenu(false); if (confirm(`Remove "${stop?.name ?? 'this stop'}" from the trip?`)) edit.remove(e.id) }}>Delete</button>
              </span>
            )}
          </span>
        )}
      </div>
    </li>
  )
}
