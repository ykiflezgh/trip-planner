import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { parseSchedule, tripDetail } from '../../lib/kotlin/tripPlanner'
import { useKotlinState } from '../../hooks/useKotlinState'
import { useFacade } from '../../hooks/useFacade'
import { dayLabel } from '../../lib/time'
import { Agenda } from '../calendar/Agenda'
import { AddStopDialog } from '../stops/AddStopDialog'
import { DayHoursDialog } from './DayHoursDialog'
import { TripSettingsDialog } from './TripSettingsDialog'
import { appendKeys } from '../../lib/order'
import { DayView, type GridEdit } from '../calendar/DayView'
import { useAnnouncer } from '../../hooks/useAnnouncer'
import { TripView } from '../calendar/TripView'
import { EntryDialog } from '../calendar/EntryDialog'
import { neighboursForTime } from '../../lib/grid'
import { ActivityPanel } from '../activity/ActivityPanel'
import { ShareDialog } from '../invites/ShareDialog'
import { FeedDialog } from '../feed/FeedDialog'
import { SuggestionBanner } from '../suggest/SuggestionBanner'
import { MapPanel } from '../map/MapPanel'
import { MapsProvider } from '../map/MapsProvider'
import { MAPS_KEY } from '../../lib/maps'
import { Menu, type MenuItem } from '../../components/Menu'

type View = 'agenda' | 'day' | 'trip'

// The two-pane breakpoint (Tailwind `lg`), tracked live: the map pane is mounted only while it is visible.
const WIDE = '(min-width: 1024px)'
const subscribeWide = (onChange: () => void) => { const q = window.matchMedia(WIDE); q.addEventListener('change', onChange); return () => q.removeEventListener('change', onChange) }
const isWide = () => window.matchMedia(WIDE).matches

/**
 * Open trip: one facade (one ViewModel, scoped listeners) for the route's lifetime, owned by
 * useFacade; day and view live in the URL (companion §8.2). Read-only in W1.
 */
export function TripPage() {
  const { tripId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const facade = useFacade(() => tripDetail(tripId), [tripId])
  const state = useKotlinState(facade)
  const schedule = useMemo(() => parseSchedule(state?.scheduleJson ?? null), [state?.scheduleJson])
  const view: View = params.get('view') === 'day' ? 'day' : params.get('view') === 'trip' ? 'trip' : 'agenda'
  const [announcement, announce] = useAnnouncer()
  const [editId, setEditId] = useState<string | null>(null)
  const wide = useSyncExternalStore(subscribeWide, isWide, () => false)
  const [pane, setPane] = useState<'calendar' | 'map'>('calendar')
  const [dialog, setDialog] = useState<'add' | 'hours' | 'settings' | null>(null)
  const schedules = useMemo(() => (state?.schedules ?? []).map(parseSchedule), [state?.schedules])
  const hasMaps = Boolean(MAPS_KEY)
  // Where focus returns when something the More menu opened goes away (WCAG 2.4.3): the More
  // trigger, or the trip heading if the menu is not on the page.
  const more = useRef<HTMLSpanElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const focusMore = () => (more.current?.querySelector<HTMLButtonElement>('button[aria-haspopup]') ?? heading.current)?.focus()

  // URL -> ViewModel (day) and toggle; the ViewModel is the source of truth for the selection. Each
  // effect depends on the facade: a no-op until useFacade has created it, and run again for the fresh
  // facade after a trip switch, so the URL state reaches whichever facade is live.
  const urlDay = Number(params.get('day') ?? '0')
  useEffect(() => { if (facade && Number.isInteger(urlDay) && urlDay >= 0) facade.selectDay(urlDay) }, [facade, urlDay])
  const localTime = params.get('tz') === 'local'
  useEffect(() => { facade?.setLocalTime(localTime) }, [facade, localTime])
  // A notification tap (companion §10) carries the stop to select.
  const urlStop = params.get('stop')
  useEffect(() => { if (urlStop) facade?.selectStop(urlStop) }, [facade, urlStop])
  const showActivity = params.get('panel') === 'activity'
  // Focus moves into the Activity panel only when the More menu opened it (WCAG 2.4.3), not when a page
  // load or reload mounts it from ?panel=activity. The flag is armed by that menu item and dropped once
  // the panel has gone away again (close button, menu, history), so the next mount from the URL alone
  // does not take focus. The previous `showActivity` is state rather than a ref because it is read during
  // render; the flag cannot simply follow `showActivity`, since the router's navigation is a transition
  // that lands after the flag's own update.
  const [focusActivity, setFocusActivity] = useState(false)
  const [activityShown, setActivityShown] = useState(showActivity)
  if (activityShown !== showActivity) { setActivityShown(showActivity); if (!showActivity) setFocusActivity(false) }
  // The menu has closed by the time Claude is asked (design §8.7), so the wait is announced from here.
  const suggesting = state?.suggesting ?? false
  useEffect(() => { if (suggesting) announce('Asking Claude for an order…') }, [suggesting, announce])

  const set = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) { if (v === null) next.delete(k); else next.set(k, v) }
    setParams(next, { replace: true })
  }

  // One-shot messages from the ViewModel (validation, concurrent deletes) as a dismissible bar. It
  // stays until dismissed (WCAG 2.2.1): no timer that slow readers or magnifier users could miss.
  const message = state?.message ?? null

  if (!state || !facade) return <p className="p-4">Opening trip…</p>
  if (state.error && !state.trip) return <p className="p-4 text-red-700">{state.error}</p>
  const trip = state.trip
  const days = Array.from({ length: state.dayCount }, (_, d) => d)
  // A previewed suggestion (design §8.7) freezes editing until Apply or Dismiss.
  const editing = state.canEdit && !state.suggestion
  // The message is a StateFlow value and equal values de-duplicate, so it is consumed when a dialog
  // opens: the next one, even identical (Day hours rejected twice in a row), is a fresh value and shows.
  const openDialog = (d: NonNullable<typeof dialog>) => { facade.consumeMessage(); setDialog(d) }
  const openEntry = (id: string) => { facade.consumeMessage(); setEditId(id) }
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
    open: openEntry,
    announce,
  } : undefined
  const editStop = editId ? state.stopsByDay.flat().find((s) => s.id === editId) ?? null : null
  // The map pane is hidden below `lg` unless chosen; an unmounted Map does not import the Maps
  // library, while MapsProvider stays around the page for the Places search in AddStopDialog.
  const mapVisible = wide || pane === 'map'

  // The More menu (shared <Menu>: ARIA menu-button keyboard model, focus back on the trigger when it closes).
  const menuItems = ([
    { label: showActivity ? 'Hide activity' : 'Activity', onSelect: () => { if (!showActivity) setFocusActivity(true); set({ panel: showActivity ? null : 'activity' }) } },
    { label: state.muted ? 'Unmute notifications' : 'Mute notifications', onSelect: () => facade.setMuted(!state.muted) },
    editing && { label: 'Day hours…', onSelect: () => openDialog('hours') },
    state.canEdit && state.suggestOrderEnabled && state.stops.length >= 2 && {
      label: state.suggesting ? 'Asking Claude…' : `Suggest an order for Day ${state.selectedDay + 1}`,
      disabled: state.suggesting || !state.online || !!state.suggestion,
      onSelect: () => facade.suggestOrder(),
    },
    state.calendarFeedEnabled && { label: state.calendarBusy ? 'Preparing calendar link…' : 'Add to my calendar…', disabled: state.calendarBusy || !state.online, onSelect: () => facade.addToCalendar() },
    state.calendarFeedEnabled && { label: 'Remove my calendar links', disabled: state.calendarBusy || !state.online, onSelect: () => facade.revokeCalendarLinks() },
    state.isOwner && trip && { label: 'Settings…', onSelect: () => openDialog('settings') },
  ] as (MenuItem | false | null)[]).filter((it): it is MenuItem => Boolean(it))

  return (
    <MapsProvider>
    <main className="mx-auto max-w-7xl p-4">
      <div className="mb-2 flex flex-wrap items-baseline gap-3">
        <Link to="/app" className="text-sm text-indigo-700">← Trips</Link>
        <h1 ref={heading} tabIndex={-1} className="text-xl font-semibold">{trip?.name ?? 'Trip'}</h1>
        {/* Connection state in a region that is always mounted, so going offline or syncing is announced (WCAG 4.1.3). */}
        <span role="status" className="flex flex-wrap gap-3 text-sm">
          {!state.online && <span className="text-amber-700">offline · changes queue until you are back</span>}
          {state.pendingSync && <span className="text-stone-600">syncing…</span>}
        </span>
        <span className="ml-auto flex gap-2 text-sm">
          {state.isOwner && <button className="rounded border border-stone-300 px-3 py-1 disabled:opacity-50" disabled={state.sharing || !state.online} onClick={() => facade.share()}>{state.sharing ? 'Sharing…' : 'Share'}</button>}
          {editing && <button className="rounded bg-indigo-600 px-3 py-1 text-white" onClick={() => openDialog('add')}>Add stop</button>}
          <span ref={more}>
            <Menu label={<>More <span aria-hidden="true">▾</span></>} items={menuItems} triggerClassName="rounded border border-stone-300 px-3 py-1" />
          </span>
        </span>
      </div>
      {/* Not a live region: the banner takes focus on mount (WCAG 2.4.3; it waits for a decision), and a focused section is read
          once; a polite wrapper had screen readers read it a second time. */}
      {state.suggestion && (
        <SuggestionBanner suggestion={state.suggestion} onApply={() => { facade.applySuggestion(); focusMore() }}
          onDismiss={() => { facade.dismissSuggestion(); announce('Suggestion dismissed'); focusMore() }} />
      )}
      <div role="status" aria-live="polite">
        {message && <p className="mb-2 rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">{message} <button type="button" className="underline" onClick={() => { facade.consumeMessage(); focusMore() }}>dismiss</button></p>}
      </div>
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
        {schedule && <span className="text-stone-600">{schedule.hoursStart.slice(11, 16)} – {schedule.hoursEnd.slice(11, 16)}</span>}
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
            ? <ActivityPanel tripId={tripId} autoFocus={focusActivity} onClose={() => { set({ panel: null }); focusMore() }} onOpenStop={(day, id) => { set({ day: String(day), panel: 'activity' }); facade.selectStop(id) }} />
            : mapVisible && <MapPanel stops={state.stops} selectedId={state.selectedStopId} onSelect={(id) => facade.selectStop(id)} />}
        </aside>
      </div>
      <p aria-live="polite" className="sr-only">{announcement}</p>
      {view === 'trip' && !wide && state.canEdit && <p className="mt-2 text-xs text-stone-600">Trip view is read-only at this width; widen the window to drag across days.</p>}
      {editStop && (
        <EntryDialog stop={editStop} entry={schedules[editStop.day]?.entries.find((e) => e.id === editStop.id)}
          onPin={(t) => gridEdit?.pin(editStop.id, editStop.day, t)} onResize={(m) => facade.resizeEntry(editStop.id, m)} onUnpin={() => facade.unpinEntry(editStop.id)} onClose={() => setEditId(null)} />
      )}
      {state.shareUrl && trip && <ShareDialog tripName={trip.name} url={state.shareUrl} onClose={() => facade.consumeShare()} />}
      {state.feedUrl && <FeedDialog url={state.feedUrl} onClose={() => facade.consumeFeedUrl()} />}
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
