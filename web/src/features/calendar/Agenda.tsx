import { useRef } from 'react'
import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { DaySchedule, Leg, Stop } from '../../lib/kotlin/tripPlanner'
import { hhmm, km, minutesLabel } from '../../lib/time'
import { neighboursAfterMove } from '../../lib/order'
import { Menu, type MenuItem } from '../../components/Menu'

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
  // Delete and Move to Day unmount the row that was acted on once the snapshot arrives, so focus is moved first
  // (WCAG 2.4.3): to a neighbouring row's main button, kept here by stop id, or else to this wrapper, which
  // outlives the list (the <ol> goes with its last row, replaced by the empty-day message).
  const root = useRef<HTMLDivElement>(null)
  const buttons = useRef<Record<string, HTMLButtonElement | null>>({})
  const wrap = (body: React.ReactNode) => <div ref={root} tabIndex={-1} className="rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500">{body}</div>
  if (!schedule) return wrap(<p className="text-stone-600">Computing…</p>)
  if (schedule.entries.length === 0) return wrap(<p className="text-stone-600">No stops on this day yet.</p>)
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
  const leave = (i: number) => (buttons.current[ids[i + 1]] ?? buttons.current[ids[i - 1]] ?? root.current)?.focus()

  const rows = schedule.entries.map((e, i) => (
    <Row key={e.id} entry={e} index={i} stop={byId.get(e.id)} legs={legs.filter((l) => l.to === e.id)} warnings={schedule.warnings.filter((w) => w.entryId === e.id)}
      selected={selectedId === e.id} onSelect={() => onSelect(selectedId === e.id ? null : e.id)} buttonRef={(el) => { buttons.current[e.id] = el }} leave={() => leave(i)}
      edit={edit} count={ordered.length} nudge={(d) => nudge(i, d)} />
  ))
  const list = <ol className="divide-y divide-stone-200 rounded border border-stone-200 bg-white">{rows}</ol>
  // The drag contexts sit outside the <ol>: dnd-kit renders its own live region and instructions where DndContext
  // is, and inside the list they would be non-<li> children (WCAG 1.3.1, axe "list").
  return wrap(edit ? (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>{list}</SortableContext>
    </DndContext>
  ) : list)
}

function Row({ entry: e, index, stop, legs, warnings, selected, onSelect, buttonRef, leave, edit, count, nudge }: {
  entry: DaySchedule['entries'][number]; index: number; stop?: Stop; legs: Leg[]; warnings: DaySchedule['warnings']; selected: boolean; onSelect: () => void
  buttonRef: (el: HTMLButtonElement | null) => void; leave: () => void; edit?: AgendaEdit; count: number; nudge: (delta: number) => void
}) {
  const sortable = useSortable({ id: e.id, disabled: !edit })
  const style = { transform: CSS.Transform.toString(sortable.transform), transition: sortable.transition }
  const name = stop?.name ?? e.id
  // The row's actions go through the shared Menu (ARIA menu keyboard model, Escape, focus return), so an
  // open menu never lingers over the next row's controls (WCAG 2.4.11); each trigger is named after its stop.
  // Move to Day and Delete unmount this row, trigger included, with the next snapshot, so they hand focus to a
  // neighbour first (WCAG 2.4.3); Menu refocuses the trigger before it runs onSelect, so the move here wins.
  const actions: MenuItem[] = edit ? [
    { label: 'Move up', disabled: index === 0, onSelect: () => nudge(-1) },
    { label: 'Move down', disabled: index === count - 1, onSelect: () => nudge(1) },
    ...Array.from({ length: edit.dayCount }, (_, d) => d).filter((d) => d !== edit.selectedDay).map((d) => ({ label: `Move to Day ${d + 1}`, onSelect: () => { leave(); edit.moveToDay(e.id, d) } })),
    { label: 'Delete', danger: true, onSelect: () => { if (confirm(`Remove "${stop?.name ?? 'this stop'}" from the trip?`)) { leave(); edit.remove(e.id) } } },
  ] : []
  return (
    <li ref={sortable.setNodeRef} style={style} className={sortable.isDragging ? 'relative z-10 bg-white shadow-lg' : ''}>
      {legs.length > 0 && (
        <p className="px-4 pt-2 text-xs text-stone-600">
          {legs.map((l) => (
            <span key={l.mode} className="mr-4">{l.mode === 'walk' ? '🚶' : '🚗'} {l.error ? 'no route' : l.seconds == null ? '…' : `${minutesLabel(Math.round(l.seconds / 60))}${l.meters ? ` · ${km(l.meters)}` : ''}`}</span>
          ))}
        </p>
      )}
      {/* Selection shows as a left bar as well as the tint (WCAG 1.4.1); the transparent bar keeps unselected rows aligned. */}
      <div className={`flex items-start gap-2 border-l-4 px-2 py-3 ${selected ? 'border-indigo-600 bg-indigo-50' : 'border-transparent'}`}>
        {edit && (
          <button {...sortable.attributes} {...sortable.listeners} aria-label={`Reorder ${stop?.name ?? ''}: press Space, move with the arrow keys, press Space again`}
            className="cursor-grab touch-none rounded px-1 text-stone-600 hover:bg-stone-100 focus:ring-2 focus:ring-indigo-400">≡</button>
        )}
        {/* A true toggle: pressing the selected row clears the selection, as aria-pressed promises. */}
        <button ref={buttonRef} onClick={onSelect} aria-pressed={selected} className="flex flex-1 gap-3 text-left">
          <span className="w-5 text-stone-600">{index + 1}</span>
          <span className="flex-1">
            <span className="font-medium">{name}</span>
            <span className="block text-sm text-stone-600">{e.pinned && '📌 '}{hhmm(e.start)} – {hhmm(e.end)}{e.gapBeforeMin > 0 && ` · ${e.gapBeforeMin} min free before`}</span>
            {stop?.address && <span className="block text-sm text-stone-600">{stop.address}</span>}
            {stop?.notes && <span className="block text-sm text-stone-600">{stop.notes}</span>}
            {warnings.map((w, j) => <span key={j} className="block text-sm text-red-700">{warningText(w)}</span>)}
          </span>
        </button>
        {edit && (
          <Menu label={<span role="img" aria-label={`Actions for ${name}`}>⋯</span>} items={actions} triggerClassName="rounded px-2 py-1 text-stone-600 hover:bg-stone-100" />
        )}
      </div>
    </li>
  )
}
