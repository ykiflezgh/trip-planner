import { useEffect, useRef, useState } from 'react'
import { useMapsLibrary } from '@vis.gl/react-google-maps'
import { Modal, field, primary } from '../../components/Modal'

export interface PlacePick { placeId: string; name: string; address: string; lat: number; lng: number }
interface Suggestion { placeId: string; primary: string; secondary: string; toPlace: () => google.maps.places.Place }

/**
 * Add a stop (companion §8.3): Places autocomplete through the JS Places library with one session
 * token per search (autocomplete + details bill as one session), or a custom entry with no place.
 */
export function AddStopDialog({ hasMaps, onAddPlace, onAddCustom, onClose }: {
  hasMaps: boolean
  onAddPlace: (p: PlacePick, durationMin: number, notes: string) => void
  onAddCustom: (name: string, durationMin: number, fixedStart: string | null, notes: string) => void
  onClose: () => void
}) {
  const [tab, setTab] = useState<'place' | 'custom'>(hasMaps ? 'place' : 'custom')
  return (
    <Modal title="Add stop" onClose={onClose}>
      <div className="mb-3 flex gap-2 text-sm">
        <button className={`rounded-full border px-3 py-1 ${tab === 'place' ? 'border-indigo-600 bg-indigo-50' : 'border-stone-300'}`} onClick={() => setTab('place')} disabled={!hasMaps} title={hasMaps ? undefined : 'Needs the Maps key'}>Place</button>
        <button className={`rounded-full border px-3 py-1 ${tab === 'custom' ? 'border-indigo-600 bg-indigo-50' : 'border-stone-300'}`} onClick={() => setTab('custom')}>Custom entry</button>
      </div>
      {tab === 'place' && hasMaps ? <PlaceForm onAdd={onAddPlace} /> : <CustomForm onAdd={onAddCustom} />}
    </Modal>
  )
}

function PlaceForm({ onAdd }: { onAdd: (p: PlacePick, durationMin: number, notes: string) => void }) {
  const places = useMapsLibrary('places')
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<Suggestion[]>([])
  const [pick, setPick] = useState<PlacePick | null>(null)
  const [duration, setDuration] = useState(60)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef<google.maps.places.AutocompleteSessionToken | null>(null)

  // Suggestions are only shown while a long-enough query is typed and nothing is picked; the effect only fetches.
  const visible = !pick && query.trim().length >= 3 ? items : []
  useEffect(() => {
    if (!places || query.trim().length < 3 || pick) return
    const handle = setTimeout(async () => {
      try {
        token.current ??= new places.AutocompleteSessionToken()
        const { suggestions } = await places.AutocompleteSuggestion.fetchAutocompleteSuggestions({ input: query, sessionToken: token.current })
        setItems(suggestions.flatMap((s) => (s.placePrediction ? [{ placeId: s.placePrediction.placeId, primary: s.placePrediction.mainText?.text ?? s.placePrediction.text.text, secondary: s.placePrediction.secondaryText?.text ?? '', toPlace: () => s.placePrediction!.toPlace() }] : [])))
        setError(null)
      } catch (e) { setError(`Search failed: ${String((e as Error).message ?? e)}`) }
    }, 200)
    return () => clearTimeout(handle)
  }, [places, query, pick])

  const choose = async (s: Suggestion) => {
    setBusy(true)
    try {
      const place = s.toPlace()
      await place.fetchFields({ fields: ['id', 'displayName', 'location', 'formattedAddress'] }) // a Place from toPlace() carries the session; this call ends it
      token.current = null
      const loc = place.location
      if (!loc) throw new Error('Place has no location')
      setPick({ placeId: place.id, name: place.displayName ?? s.primary, address: place.formattedAddress ?? '', lat: loc.lat(), lng: loc.lng() })
      setQuery(place.displayName ?? s.primary); setItems([])
    } catch (e) { setError(`Could not load the place: ${String((e as Error).message ?? e)}`) } finally { setBusy(false) }
  }

  return (
    <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); if (pick) onAdd(pick, duration, notes) }}>
      <label className="block text-sm">Search a place
        <input className={field} value={query} onChange={(e) => { setQuery(e.target.value); setPick(null) }} placeholder="Louvre" autoFocus />
      </label>
      {visible.length > 0 && (
        <ul className="max-h-48 overflow-auto rounded border border-stone-200">
          {visible.map((s) => <li key={s.placeId}><button type="button" className="w-full px-3 py-2 text-left text-sm hover:bg-stone-50" onClick={() => choose(s)}><span className="font-medium">{s.primary}</span> <span className="text-stone-500">{s.secondary}</span></button></li>)}
        </ul>
      )}
      {pick && <p className="text-sm text-stone-600">{pick.address}</p>}
      <DurationNotes duration={duration} setDuration={setDuration} notes={notes} setNotes={setNotes} />
      {error && <p className="text-sm text-red-700">{error}</p>}
      <p className="text-xs text-stone-400">Powered by Google</p>
      <button className={primary} disabled={!pick || busy}>{busy ? 'Loading…' : 'Add stop'}</button>
    </form>
  )
}

function CustomForm({ onAdd }: { onAdd: (name: string, durationMin: number, fixedStart: string | null, notes: string) => void }) {
  const [name, setName] = useState('')
  const [duration, setDuration] = useState(60)
  const [pin, setPin] = useState('')
  const [notes, setNotes] = useState('')
  return (
    <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); if (name.trim()) onAdd(name, duration, pin || null, notes) }}>
      <label className="block text-sm">Title<input className={field} value={name} onChange={(e) => setName(e.target.value)} placeholder="Flight to Rome" maxLength={80} autoFocus /></label>
      <label className="block text-sm">Pinned start (optional)<input className={field} type="time" value={pin} onChange={(e) => setPin(e.target.value)} /></label>
      <DurationNotes duration={duration} setDuration={setDuration} notes={notes} setNotes={setNotes} />
      <button className={primary} disabled={!name.trim()}>Add entry</button>
    </form>
  )
}

function DurationNotes({ duration, setDuration, notes, setNotes }: { duration: number; setDuration: (n: number) => void; notes: string; setNotes: (s: string) => void }) {
  return (
    <>
      <label className="block text-sm">Duration (minutes)<input className={field} type="number" min={5} max={1440} step={5} value={duration} onChange={(e) => setDuration(Number(e.target.value))} /></label>
      <label className="block text-sm">Notes<input className={field} value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
    </>
  )
}

