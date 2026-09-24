import { Fragment, useRef, useState } from 'react'
import type { DaySchedule, Stop } from '../../lib/kotlin/tripPlanner'
import { hhmm, minutesFrom } from '../../lib/time'
import { layout, SLOT } from '../../lib/layout'
import { snap, timeAt } from '../../lib/grid'

const PX_PER_MIN = 1.2 // 72 px per hour
const HANDLE_PX = 12 // the resize strip straddles the block's bottom edge

export interface GridColumn {
  day: number
  /** Visible sticky header; omitted by the Trip view, which draws its own day buttons above the grid. */
  label?: string
  /** Accessible name of the day column when the header is hidden (falls back to `label`, then "Day n"). */
  ariaLabel?: string
  schedule: DaySchedule | null
  stops: Stop[]
}
export interface GridEdit {
  /** Drop or keyboard move: pin `stopId` at `hhmm` on `day` (the target column). */
  pin: (stopId: string, day: number, hhmm: string) => void
  resize: (stopId: string, durationMin: number) => void
  moveToDay: (stopId: string, day: number) => void
  open: (stopId: string) => void
  announce: (text: string) => void
}

type Drag = { id: string; fromDay: number; startY: number; startX: number; dy: number; dx: number; kind: 'move' | 'resize'; height: number; top: number }
type Entry = DaySchedule['entries'][number]

/** The same wording as the Agenda rows, for the block's accessible name. */
const warningText = (w: DaySchedule['warnings'][number]) =>
  w.type === 'lateArrival' ? `${w.minutes} min late for the pinned time` : w.type === 'overlap' ? `overlaps the previous entry by ${w.minutes} min` : `runs ${w.minutes} min past the day's end`
/** The travel leg drawn above a block, in words: "10 min drive before" or "drive before, computing". */
const legText = (leg: NonNullable<Entry['travelBefore']>) => (leg.pending ? `${leg.mode} before, computing` : `${minutesFrom(leg.start, leg.end)} min ${leg.mode} before`)

/**
 * The vertical time grid (design §6.6): one column per day, blocks sized by duration, hatched
 * travel legs, free time as gaps. For assistive technology it is a "Calendar" region of one group
 * per day whose blocks are toggle buttons (pressed = selected) named with the stop, its times, the
 * travel leg before it and any warning; the hour axis and the hatching are decorative. With [edit]:
 * drag a block to pin it (15-minute snap, across columns in the Trip view), drag its bottom edge to
 * resize, and on a focused block Space selects, Enter opens the edit form, ArrowUp/Down pin ±15 min,
 * Shift+Arrow resize ±15 min, Alt+Left/Right change day.
 */
export function TimeGrid({ columns, selectedId, onSelect, edit, compact = false }: { columns: GridColumn[]; selectedId: string | null; onSelect: (id: string | null) => void; edit?: GridEdit; compact?: boolean }) {
  const first = columns.find((c) => c.schedule)?.schedule
  // The drag lives in a ref (read synchronously by pointer-up, which can land before a re-render) and in state (for drawing).
  const dragRef = useRef<Drag | null>(null)
  const [drag, setDragState] = useState<Drag | null>(null)
  const setDrag = (d: Drag | null) => { dragRef.current = d; setDragState(d) }
  const colRefs = useRef<(HTMLDivElement | null)[]>([])
  const blockRefs = useRef<Record<string, HTMLButtonElement | null>>({})
  if (!first) return <p className="text-stone-600">Computing…</p>
  // One axis for every column: the earliest start and the latest end across the page.
  const origin = columns.reduce((o, c) => (c.schedule && c.schedule.hoursStart.slice(11) < o.slice(11) ? c.schedule.hoursStart : o), first.hoursStart)
  const totalMin = Math.max(...columns.map((c) => (c.schedule ? layout(c.schedule).totalMin + minutesFrom(origin.slice(0, 10) + c.schedule.hoursStart.slice(10), origin.slice(0, 10) + origin.slice(10)) * -1 : 0)), 60)
  const hours = Array.from({ length: Math.ceil(totalMin / 60) + 1 }, (_, i) => i)
  const startHour = +origin.slice(11, 13)
  const rows = Math.ceil(totalMin / 60) * 60

  const columnAt = (x: number) => {
    const rects = colRefs.current.map((el) => el?.getBoundingClientRect())
    const i = rects.findIndex((r) => r && x >= r.left && x <= r.right)
    return i >= 0 ? i : null
  }
  const onPointerDown = (e: React.PointerEvent, id: string, day: number, kind: Drag['kind'], top: number, height: number) => {
    if (!edit || e.button !== 0) return
    // preventDefault keeps the drag from selecting text but also cancels the click's focus move, so focus
    // the block by hand (the resize strip is a sibling of its block, and the keyboard shortcuts need the focus).
    blockRefs.current[id]?.focus({ preventScroll: true })
    e.preventDefault(); try { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId) } catch { /* synthetic events have no active pointer */ }
    setDrag({ id, fromDay: day, startY: e.clientY, startX: e.clientX, dy: 0, dx: 0, kind, top, height })
  }
  const onPointerMove = (e: React.PointerEvent) => { const d = dragRef.current; if (d) setDrag({ ...d, dy: e.clientY - d.startY, dx: e.clientX - d.startX }) }
  const onPointerUp = (e: React.PointerEvent) => {
    const d = dragRef.current
    if (!d || !edit) return
    setDrag(null)
    d.dy = e.clientY - d.startY; d.dx = e.clientX - d.startX
    if (Math.abs(d.dy) < 3 && Math.abs(d.dx) < 3) return // a click, not a drag
    if (d.kind === 'resize') {
      const next = Math.max(SLOT, snap(d.height + d.dy / PX_PER_MIN))
      if (next !== d.height) { edit.resize(d.id, next); edit.announce(`${nameOf(d.id)} now ${next} min`) }
      return
    }
    const col = columnAt(e.clientX) ?? columns.findIndex((c) => c.day === d.fromDay)
    const day = columns[col]?.day ?? d.fromDay
    const minutes = snap(d.top + d.dy / PX_PER_MIN)
    const time = timeAt(origin, minutes)
    edit.pin(d.id, day, time); edit.announce(`${nameOf(d.id)} moved to ${time}, Day ${day + 1}`)
  }
  const nameOf = (id: string) => columns.flatMap((c) => c.stops).find((s) => s.id === id)?.name ?? id
  const onKey = (e: React.KeyboardEvent, id: string, day: number, top: number, height: number) => {
    // Space selects through the button's own click (with or without [edit]); Enter selects and opens the form
    // instead of clicking, so the arrows below stay free for pinning.
    if (e.key === 'Enter') { e.preventDefault(); onSelect(id); edit?.open(id); return }
    if (!edit) return
    const step = e.key === 'ArrowUp' || e.key === 'ArrowLeft' ? -1 : e.key === 'ArrowDown' || e.key === 'ArrowRight' ? 1 : 0
    if (!step) return
    e.preventDefault()
    if (e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) { const nd = day + step; if (nd >= 0) { edit.moveToDay(id, nd); edit.announce(`${nameOf(id)} moved to Day ${nd + 1}`) } return }
    if (e.shiftKey) { const next = Math.max(SLOT, height + step * SLOT); edit.resize(id, next); edit.announce(`${nameOf(id)} now ${next} min`); return }
    const minutes = Math.max(0, top + step * SLOT); const time = timeAt(origin, minutes)
    edit.pin(id, day, time); edit.announce(`${nameOf(id)} moved to ${time}, Day ${day + 1}`)
  }

  return (
    <div className="flex overflow-x-auto rounded border border-stone-200 bg-white" role="region" aria-label="Calendar" onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={() => setDrag(null)}>
      <div aria-hidden="true" className="relative w-12 shrink-0 border-r border-stone-100 text-[11px] text-stone-600" style={{ height: rows * PX_PER_MIN + 16 }}>
        {hours.map((h) => <span key={h} className="absolute left-1" style={{ top: h * 60 * PX_PER_MIN - 7 }}>{String((startHour + h) % 24).padStart(2, '0')}:00</span>)}
      </div>
      {columns.map((c, ci) => {
        const sch = c.schedule
        const offset = sch ? minutesFrom(origin, sch.hoursStart) : 0
        const l = sch ? layout(sch) : { blocks: [], legs: [] }
        const byId = new Map(c.stops.map((s) => [s.id, s]))
        const warn = new Map(sch?.warnings.map((w) => [w.entryId, w]) ?? [])
        return (
          <div key={c.day} ref={(el) => { colRefs.current[ci] = el }} role="group" aria-label={c.ariaLabel ?? c.label ?? `Day ${c.day + 1}`} className="relative min-w-40 flex-1 border-r border-stone-100 last:border-r-0" style={{ height: rows * PX_PER_MIN + 16 }}>
            {c.label && <div aria-hidden="true" className="sticky top-0 z-10 bg-white/90 px-2 py-1 text-xs font-medium text-stone-600">{c.label}</div>}
            {hours.map((h) => <div key={h} className="absolute left-0 right-0 border-t border-stone-100" style={{ top: h * 60 * PX_PER_MIN }} />)}
            {l.legs.map((g) => (
              <div key={g.id} aria-hidden="true" title={`${g.mode}${g.pending ? ' (computing)' : ''}`} className="absolute left-1 right-1 rounded-sm opacity-70"
                style={{ top: (g.top + offset) * PX_PER_MIN, height: Math.max(2, g.height * PX_PER_MIN), backgroundImage: 'repeating-linear-gradient(135deg, #a8a29e 0 3px, transparent 3px 8px)' }} />
            ))}
            {l.blocks.map((b) => {
              const entry = sch!.entries.find((e) => e.id === b.id)!
              const w = warn.get(b.id)
              const dragging = drag?.id === b.id
              const selected = selectedId === b.id
              const top = b.top + offset
              const dy = dragging && drag.kind === 'move' ? drag.dy : 0
              const h = dragging && drag.kind === 'resize' ? Math.max(SLOT, snap(b.height + drag.dy / PX_PER_MIN)) : b.height
              const name = byId.get(b.id)?.name ?? b.id
              return (
                <Fragment key={b.id}>
                  <button type="button" ref={(el) => { blockRefs.current[b.id] = el }} aria-pressed={selected}
                    aria-label={`${name}, ${hhmm(entry.start)} to ${hhmm(entry.end)}${b.pinned ? ', pinned' : ''}${entry.travelBefore ? `, ${legText(entry.travelBefore)}` : ''}${w ? `, warning: ${warningText(w)}` : ''}`}
                    onClick={() => onSelect(b.id)} onKeyDown={(e) => onKey(e, b.id, c.day, top, b.height)}
                    onPointerDown={(e) => onPointerDown(e, b.id, c.day, 'move', top, b.height)}
                    className={`absolute left-1 right-1 grid content-start overflow-hidden rounded border px-2 py-1 text-left text-sm select-none focus:outline-none focus:ring-2 focus:ring-indigo-500 ${edit ? 'cursor-grab' : ''} ${dragging ? 'z-20 opacity-80 shadow-lg' : ''} ${selected ? 'border-indigo-600 bg-indigo-100' : 'border-indigo-300 bg-indigo-50'} ${w ? 'ring-2 ring-red-500' : ''}`}
                    style={{ top: top * PX_PER_MIN + dy, height: h * PX_PER_MIN, touchAction: 'none' }}>
                    <span>
                      <span className="font-medium">{b.pinned && '📌 '}{name}</span>
                      {!compact && <span className="ml-2 text-stone-600">{hhmm(entry.start)} – {hhmm(entry.end)}</span>}
                      {w && (compact
                        ? <span className="ml-1 font-medium text-red-800" title={warningText(w)}>⚠</span>
                        : <span className="ml-2 rounded bg-red-100 px-1 text-xs text-red-800">{w.type} {w.minutes} min</span>)}
                    </span>
                  </button>
                  {/* The resize strip is a sibling, not a child, of the button (buttons cannot hold interactive content); it follows the block while it is dragged. */}
                  {edit && <span aria-hidden="true" onPointerDown={(e) => onPointerDown(e, b.id, c.day, 'resize', top, b.height)} className="absolute left-1 right-1 z-10 cursor-ns-resize"
                    style={{ top: (top + h) * PX_PER_MIN + dy - HANDLE_PX / 2, height: HANDLE_PX, touchAction: 'none' }} />}
                </Fragment>
              )
            })}
          </div>
        )
      })}
    </div>
  )
}

/** One day (the Day view). */
export function DayView({ day, schedule, stops, selectedId, onSelect, edit }: { day: number; schedule: DaySchedule | null; stops: Stop[]; selectedId: string | null; onSelect: (id: string | null) => void; edit?: GridEdit }) {
  return <TimeGrid columns={[{ day, schedule, stops }]} selectedId={selectedId} onSelect={onSelect} edit={edit} />
}
