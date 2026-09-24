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
import { DayView, useAnnouncer, type GridEdit } from '../calendar/DayView'
import { TripView } from '../calendar/TripView'
import { EntryDialog } from '../calendar/EntryDialog'
import { neighboursForTime } from '../../lib/grid'
import { ActivityPanel } from '../activity/ActivityPanel'
import { ShareDialog } from '../invites/ShareDialog'
import { FeedDialog } from '../feed/FeedDialog'
import { SuggestionBanner } from '../suggest/SuggestionBanner'
import { MapPanel } from '../map/MapPanel'
import { MAPS_KEY, MapsProvider } from '../map/MapsProvider'

type View = 'agenda' | 'day' | 'trip'

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
  const view: View = params.get('view') === 'day' ? 'day' : params.get('view') === 'trip' ? 'trip' : 'agenda'
  const [announcement, announce] = useAnnouncer()
  const [editId, setEditId] = useState<string | null>(null)
  const wide = typeof window !== 'undefined' && window.matchMedia('(min-width: 1024px)').matches
  const [pane, setPane] = useState<'calendar' | 'map'>('calendar')
  const [dialog, setDialog] = useState<'add' | 'hours' | 'settings' | null>(null)
  const [menu, setMenu] = useState(false)
  const schedules = useMemo(() => (state?.schedules ?? []).map(parseSchedule), [state?.schedules])
  const hasMaps = Boolean(MAPS_KEY)

  // URL -> ViewModel (day) and toggle; the ViewModel is the source of truth for the selection.
  const urlDay = Number(params.get('day') ?? '0')
  useEffect(() => { if (Number.isInteger(urlDay) && urlDay >= 0) facade.selectDay(urlDay) }, [facade, urlDay])
  const localTime = params.get('tz') === 'local'
  useEffect(() => facade.setLocalTime(localTime), [facade, localTime])
  // A notification tap (companion §10) carries the stop to select.
  const urlStop = params.get('stop')
  useEffect(() => { if (urlStop) facade.selectStop(urlStop) }, [facade, urlStop])
  const showActivity = params.get('panel') === 'activity'
  // One-shot share and feed links from the ViewModel become dialogs.
  useEffect(() => { if (state?.feedUrl || state?.shareUrl) setMenu(false) }, [state?.feedUrl, state?.shareUrl])

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
  // A previewed suggestion (design §8.7) freezes editing until Apply or Dismiss.
  const editing = state.canEdit && !state.suggestion
  // Grid edits (design §6.6): the drop time and the chronological neighbours go to Kotlin, which computes the key.
  const gridEdit: GridEdit | undefined = editing ? {
    pin: (id, day, time) => {
      const sch = schedules[day]
      const entries = (sch?.entries ?? []).map((e) => ({ id: e.id, start: e.start, order: (state.stopsByDay[day] ?? []).find((s) => s.id === e.id)?.order ?? '' }))
      const n = neighboursForTime(entries, id, `${sch?.date ?? ''}T${time}`)
      facade.pinAt(id, day, time, n.afterOrder, n.beforeOrder, n.keepOrder)
    },
    resize: (id, min) => facade.resizeEntry(id, min),
    moveToDay: (id, day) => { if (day < state.dayCount) facade.moveToDay(id, day) },
    open: (id) => setEditId(id),
    announce,
  } : undefined
  const editStop = editId ? state.stopsByDay.flat().find((s) => s.id === editId) ?? null : null

  return (
    <MapsProvider>
    <main className="mx-auto max-w-7xl p-4">
      <div className="mb-2 flex flex-wrap items-baseline gap-3">
        <Link to="/app" className="text-sm text-indigo-700">← Trips</Link>
        <h1 className="text-xl font-semibold">{trip?.name ?? 'Trip'}</h1>
        {!state.online && <span className="text-sm text-amber-700">offline · changes queue until you are back</span>}
        {state.pendingSync && <span className="text-sm text-stone-500">syncing…</span>}
        <span className="ml-auto flex gap-2 text-sm">
          {state.isOwner && <button className="rounded border border-stone-300 px-3 py-1 disabled:opacity-50" disabled={state.sharing || !state.online} onClick={() => facade.share()}>{state.sharing ? 'Sharing…' : 'Share'}</button>}
          {editing && <button className="rounded bg-indigo-600 px-3 py-1 text-white" onClick={() => setDialog('add')}>Add stop</button>}
          <span className="relative">
            <button className="rounded border border-stone-300 px-3 py-1" aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu(!menu)}>More ▾</button>
            {menu && (
              <span role="menu" className="absolute right-0 z-20 mt-1 flex w-64 flex-col rounded border border-stone-200 bg-white py-1 text-left shadow-lg">
                <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50" onClick={() => { setMenu(false); set({ panel: showActivity ? null : 'activity' }) }}>{showActivity ? 'Hide activity' : 'Activity'}</button>
                <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50" onClick={() => { setMenu(false); facade.setMuted(!state.muted) }}>{state.muted ? 'Unmute notifications' : 'Mute notifications'}</button>
                {editing && <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50" onClick={() => { setMenu(false); setDialog('hours') }}>Day hours…</button>}
                {state.canEdit && state.suggestOrderEnabled && state.stops.length >= 2 && (
                  <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50 disabled:opacity-50" disabled={state.suggesting || !state.online || !!state.suggestion} onClick={() => { setMenu(false); facade.suggestOrder() }}>
                    {state.suggesting ? 'Asking Claude…' : `Suggest an order for Day ${state.selectedDay + 1}`}
                  </button>
                )}
                {state.calendarFeedEnabled && <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50 disabled:opacity-50" disabled={state.calendarBusy || !state.online} onClick={() => { setMenu(false); facade.addToCalendar() }}>{state.calendarBusy ? 'Preparing calendar link…' : 'Add to my calendar…'}</button>}
                {state.calendarFeedEnabled && <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50 disabled:opacity-50" disabled={state.calendarBusy || !state.online} onClick={() => { setMenu(false); facade.revokeCalendarLinks() }}>Remove my calendar links</button>}
                {state.isOwner && trip && <button role="menuitem" className="px-3 py-1.5 text-left hover:bg-stone-50" onClick={() => { setMenu(false); setDialog('settings') }}>Settings…</button>}
              </span>
            )}
          </span>
        </span>
      </div>
      {state.suggestion && <SuggestionBanner suggestion={state.suggestion} onApply={() => facade.applySuggestion()} onDismiss={() => facade.dismissSuggestion()} />}
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
        {state.dayCount > 1 && <Chip active={view === 'trip'} onClick={() => set({ view: 'trip' })}>Trip</Chip>}
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
          {view === 'trip'
            ? <TripView schedules={schedules} stopsByDay={state.stopsByDay} dayLabel={(d) => dayLabel(trip?.startDate ?? '', d)} selectedDay={state.selectedDay} selectedId={state.selectedStopId}
                onSelect={(d, id) => { set({ day: String(d) }); facade.selectStop(id) }} onSelectDay={(d) => set({ day: String(d) })} edit={wide ? gridEdit : undefined} />
            : view === 'day'
            ? <DayView day={state.selectedDay} schedule={schedule} stops={state.stops} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)} edit={gridEdit} />
            : <Agenda schedule={schedule} stops={state.stops} legs={state.legs} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)}
                edit={editing ? {
                  move: (id, after, before) => facade.moveStop(id, state.selectedDay, after, before),
                  moveToDay: (id, day) => facade.moveToDay(id, day),
                  remove: (id) => facade.deleteStop(id),
                  dayCount: state.dayCount, selectedDay: state.selectedDay,
                } : undefined} />}
        </section>
        <aside className={`${pane === 'calendar' ? 'hidden lg:block' : ''} min-h-[320px]`}>
          {showActivity
            ? <ActivityPanel tripId={tripId} onClose={() => set({ panel: null })} onOpenStop={(day, id) => { set({ day: String(day), panel: 'activity' }); facade.selectStop(id) }} />
            : <MapPanel stops={state.stops} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)} />}
        </aside>
      </div>
      <p aria-live="polite" className="sr-only">{announcement}</p>
      {view === 'trip' && !wide && state.canEdit && <p className="mt-2 text-xs text-stone-500">Trip view is read-only at this width; widen the window to drag across days.</p>}
      {editStop && (
        <EntryDialog stop={editStop} entry={schedules[editStop.day]?.entries.find((e) => e.id === editStop.id)}
          onPin={(t) => gridEdit?.pin(editStop.id, editStop.day, t)} onResize={(m) => facade.resizeEntry(editStop.id, m)} onUnpin={() => facade.unpinEntry(editStop.id)} onClose={() => setEditId(null)} />
      )}
      {state.shareUrl && trip && <ShareDialog tripName={trip.name} url={state.shareUrl} onMessage={(t) => facade.showMessage?.(t)} onClose={() => facade.consumeShare()} />}
      {state.feedUrl && <FeedDialog url={state.feedUrl} onMessage={(t) => facade.showMessage?.(t)} onClose={() => facade.consumeFeedUrl()} />}
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
