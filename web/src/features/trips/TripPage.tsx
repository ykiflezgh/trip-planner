import { useEffect, useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { parseSchedule, tripDetail } from '../../lib/kotlin/tripPlanner'
import { useKotlinState } from '../../hooks/useKotlinState'
import { dayLabel } from '../../lib/time'
import { Agenda } from '../calendar/Agenda'
import { AddStopDialog } from '../stops/AddStopDialog'
import { DayHoursDialog } from './DayHoursDialog'
import { TripSettingsDialog } from './TripSettingsDialog'
import { appendKeys } from '../../lib/order'
import { DayView } from '../calendar/DayView'
import { MapPanel } from '../map/MapPanel'
import { MAPS_KEY, MapsProvider } from '../map/MapsProvider'

type View = 'agenda' | 'day'

/**
 * Open trip: one facade (one ViewModel, scoped listeners) for the route's lifetime; day and view
 * live in the URL (companion §8.2). Read-only in W1.
 */
export function TripPage() {
  const { tripId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const facade = useMemo(() => tripDetail(tripId), [tripId])
  useEffect(() => () => facade.close(), [facade])
  const state = useKotlinState(facade)
  const schedule = useMemo(() => parseSchedule(state?.scheduleJson ?? null), [state?.scheduleJson])
  const view: View = params.get('view') === 'day' ? 'day' : 'agenda'
  const [pane, setPane] = useState<'calendar' | 'map'>('calendar')
  const [dialog, setDialog] = useState<'add' | 'hours' | 'settings' | null>(null)
  const hasMaps = Boolean(MAPS_KEY)

  // URL -> ViewModel (day) and toggle; the ViewModel is the source of truth for the selection.
  const urlDay = Number(params.get('day') ?? '0')
  useEffect(() => { if (Number.isInteger(urlDay) && urlDay >= 0) facade.selectDay(urlDay) }, [facade, urlDay])
  const localTime = params.get('tz') === 'local'
  useEffect(() => facade.setLocalTime(localTime), [facade, localTime])

  const set = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) v === null ? next.delete(k) : next.set(k, v)
    setParams(next, { replace: true })
  }

  // One-shot messages from the ViewModel (validation, concurrent deletes) as a dismissible bar.
  const message = state?.message ?? null
  useEffect(() => { if (!message) return; const t = setTimeout(() => facade.consumeMessage(), 6000); return () => clearTimeout(t) }, [facade, message])

  if (!state) return <p className="p-4">Opening trip…</p>
  if (state.error && !state.trip) return <p className="p-4 text-red-700">{state.error}</p>
  const trip = state.trip
  const days = Array.from({ length: state.dayCount }, (_, d) => d)

  return (
    <MapsProvider>
    <main className="mx-auto max-w-7xl p-4">
      <div className="mb-2 flex flex-wrap items-baseline gap-3">
        <Link to="/" className="text-sm text-indigo-700">← Trips</Link>
        <h1 className="text-xl font-semibold">{trip?.name ?? 'Trip'}</h1>
        {!state.online && <span className="text-sm text-amber-700">offline · changes queue until you are back</span>}
        {state.pendingSync && <span className="text-sm text-stone-500">syncing…</span>}
        <span className="ml-auto flex gap-2 text-sm">
          {state.canEdit && <button className="rounded bg-indigo-600 px-3 py-1 text-white" onClick={() => setDialog('add')}>Add stop</button>}
          {state.canEdit && <button className="rounded border border-stone-300 px-3 py-1" onClick={() => setDialog('hours')}>Day hours</button>}
          {state.isOwner && trip && <button className="rounded border border-stone-300 px-3 py-1" onClick={() => setDialog('settings')}>Settings</button>}
        </span>
      </div>
      {message && <p className="mb-2 rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">{message} <button className="underline" onClick={() => facade.consumeMessage()}>dismiss</button></p>}
      <nav className="mb-2 flex gap-1 overflow-x-auto border-b border-stone-200" aria-label="Days">
        {days.map((d) => (
          <button key={d} onClick={() => set({ day: String(d) })} aria-current={d === state.selectedDay ? 'page' : undefined}
            className={`whitespace-nowrap px-3 py-2 text-sm ${d === state.selectedDay ? 'border-b-2 border-indigo-600 font-medium text-indigo-700' : 'text-stone-600'}`}>
            {dayLabel(trip?.startDate ?? '', d)}
          </button>
        ))}
      </nav>
      <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
        <Chip active={view === 'agenda'} onClick={() => set({ view: null })}>Agenda</Chip>
        <Chip active={view === 'day'} onClick={() => set({ view: 'day' })}>Day</Chip>
        {state.zoneDiffers && (
          <Chip active={state.localTime} onClick={() => set({ tz: state.localTime ? null : 'local' })} title={`Trip zone ${state.tripZone}; you are in ${state.deviceZone}`}>
            {state.localTime ? `Local time · ${state.zoneLabel}` : `Trip time · ${state.zoneLabel}`}
          </Chip>
        )}
        {schedule && <span className="text-stone-500">{schedule.hoursStart.slice(11, 16)} – {schedule.hoursEnd.slice(11, 16)}</span>}
        <span className="ml-auto flex gap-1 lg:hidden">
          <Chip active={pane === 'calendar'} onClick={() => setPane('calendar')}>Calendar</Chip>
          <Chip active={pane === 'map'} onClick={() => setPane('map')}>Map</Chip>
        </span>
      </div>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <section className={pane === 'map' ? 'hidden lg:block' : ''}>
          {view === 'day'
            ? <DayView schedule={schedule} stops={state.stops} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)} />
            : <Agenda schedule={schedule} stops={state.stops} legs={state.legs} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)}
                edit={state.canEdit ? {
                  move: (id, after, before) => facade.moveStop(id, state.selectedDay, after, before),
                  moveToDay: (id, day) => facade.moveToDay(id, day),
                  remove: (id) => facade.deleteStop(id),
                  dayCount: state.dayCount, selectedDay: state.selectedDay,
                } : undefined} />}
        </section>
        <aside className={`${pane === 'calendar' ? 'hidden lg:block' : ''} min-h-[320px]`}>
          <MapPanel stops={state.stops} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)} />
        </aside>
      </div>
      {dialog === 'add' && (
        <AddStopDialog hasMaps={hasMaps} onClose={() => setDialog(null)}
          onAddPlace={(p, duration, notes) => { const k = appendKeys(state.stops); facade.addPlaceStop(p.placeId, p.name, p.address, p.lat, p.lng, duration, notes, state.selectedDay, k.afterOrder, k.beforeOrder); setDialog(null) }}
          onAddCustom={(name, duration, pin, notes) => { const k = appendKeys(state.stops); facade.addCustomEntry(name, duration, pin, notes, state.selectedDay, k.afterOrder, k.beforeOrder); setDialog(null) }} />
      )}
      {dialog === 'hours' && (
        <DayHoursDialog label={dayLabel(trip?.startDate ?? '', state.selectedDay)} start={state.dayHoursStart ?? '09:00'} end={state.dayHoursEnd ?? '21:00'}
          onSave={(s, e) => { facade.setDayHours(state.selectedDay, s, e); setDialog(null) }} onClose={() => setDialog(null)} />
      )}
      {dialog === 'settings' && trip && (
        <TripSettingsDialog trip={trip} onClose={() => setDialog(null)}
          onSave={(t) => { facade.updateSettings(t.name, t.startDate, t.endDate, t.timeZone, t.defaultDayStart, t.defaultDayEnd, t.defaultTravelMode); setDialog(null) }} />
      )}
    </main>
    </MapsProvider>
  )
}

function Chip({ active, onClick, title, children }: { active: boolean; onClick: () => void; title?: string; children: React.ReactNode }) {
  return (
    <button onClick={onClick} title={title} aria-pressed={active}
      className={`rounded-full border px-3 py-1 ${active ? 'border-indigo-600 bg-indigo-50 text-indigo-800' : 'border-stone-300 text-stone-700'}`}>
      {children}
    </button>
  )
}
