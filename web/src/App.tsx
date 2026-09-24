import { useMemo, useState } from 'react'
import { computeDay, parseSchedule, tripDetail, tripList, type Trip } from './lib/kotlin/tripPlanner'
import { useKotlinState } from './hooks/useKotlinState'

const time = (iso: string) => iso.slice(11, 16)

function TripDetail({ trip, onBack }: { trip: Trip; onBack: () => void }) {
  const facade = useMemo(() => tripDetail(trip.id), [trip.id])
  const state = useKotlinState(facade)
  const schedule = useMemo(() => parseSchedule(state?.scheduleJson ?? null), [state?.scheduleJson])
  if (!state) return <p>Loading…</p>
  const byId = new Map(state.stops.map((s) => [s.id, s]))
  return (
    <section>
      <button onClick={onBack}>← Trips</button>
      <h2>{state.trip?.name ?? trip.name}</h2>
      <p>
        {Array.from({ length: state.dayCount }, (_, d) => (
          <button key={d} disabled={d === state.selectedDay} onClick={() => facade.selectDay(d)}>Day {d + 1}</button>
        ))}
        {!state.online && ' · offline'}{state.pendingSync && ' · syncing…'}
      </p>
      <ol>
        {(schedule?.entries ?? []).map((e) => (
          <li key={e.id}>
            {time(e.start)}–{time(e.end)} {byId.get(e.id)?.name ?? e.id}{e.pinned ? ' 📌' : ''}
            {e.travelBefore && ` (${Math.round(e.travelBefore.seconds / 60)} min ${e.travelBefore.mode} before)`}
          </li>
        ))}
      </ol>
      {schedule?.warnings.map((w, i) => <p key={i} style={{ color: 'crimson' }}>{w.type}: {byId.get(w.entryId)?.name} by {w.minutes} min</p>)}
    </section>
  )
}

export default function App() {
  const list = useMemo(() => tripList(), [])
  const state = useKotlinState(list)
  const [open, setOpen] = useState<Trip | null>(null)
  // Engine smoke test (W0 spike): the same computeDay the apps and the feed run, in the browser.
  const demo = useMemo(() => computeDay('2026-09-22', '09:00', '21:00',
    [{ id: 'a', name: 'Monastery', kind: 'place', durationMin: 90 }, { id: 'l', name: 'Lunch', kind: 'custom', durationMin: 60, fixedStart: '12:00' }, { id: 'b', name: 'Tower', kind: 'place', durationMin: 60 }],
    [{ from: 'a', to: 'b', mode: 'driving', seconds: 600 }]), [])

  return (
    <main style={{ fontFamily: 'system-ui', maxWidth: 720, margin: '2rem auto', padding: '0 1rem' }}>
      <h1>Trip Planner · web spike</h1>
      <p style={{ color: '#666' }}>Kotlin engine in the browser: {demo.entries.map((e) => `${e.id} ${time(e.start)}`).join(' · ')}</p>
      {!state ? <p>Starting…</p> : !state.signedIn ? (
        <p><button onClick={() => list.signIn()} disabled={state.signingIn}>{state.signingIn ? 'Signing in…' : 'Sign in with Google'}</button></p>
      ) : open ? (
        <TripDetail trip={open} onBack={() => setOpen(null)} />
      ) : (
        <section>
          <p>Signed in as {state.userName} <button onClick={() => list.signOut()}>Sign out</button></p>
          {state.loading ? <p>Loading trips…</p> : (
            <ul>{state.trips.map((t) => <li key={t.id}><button onClick={() => setOpen(t)}>{t.name}</button> {t.startDate} → {t.endDate} ({t.timeZone})</li>)}</ul>
          )}
        </section>
      )}
      {state?.error && <p style={{ color: 'crimson' }}>{state.error} <button onClick={() => list.dismissError()}>dismiss</button></p>}
    </main>
  )
}
