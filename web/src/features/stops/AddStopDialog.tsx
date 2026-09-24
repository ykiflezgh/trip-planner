import { useEffect, useId, useRef, useState } from 'react'
import { useMapsLibrary } from '@vis.gl/react-google-maps'
import { Modal, field, primary } from '../../components/Modal'

export interface PlacePick { placeId: string; name: string; address: string; lat: number; lng: number }
interface Suggestion { placeId: string; primary: string; secondary: string; toPlace: () => google.maps.places.Place }
type Tab = 'place' | 'custom'

// The selected tab is bold as well as tinted (WCAG 1.4.1).
const tabClass = (active: boolean) => `rounded-full border px-3 py-1 ${active ? 'border-indigo-600 bg-indigo-50 font-semibold' : 'border-stone-300'}`

/** Arrow keys, Home and End move focus between the tabs; Enter or Space activates one (manual activation). */
const onTabListKey = (e: React.KeyboardEvent<HTMLDivElement>) => {
  const tabs = Array.from(e.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]:not(:disabled)'))
  const i = tabs.indexOf(document.activeElement as HTMLButtonElement)
  if (i < 0) return
  const next = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? (i + 1) % tabs.length
    : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? (i - 1 + tabs.length) % tabs.length
    : e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 : -1
  if (next >= 0) { e.preventDefault(); tabs[next].focus() }
}

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
  const [tab, setTab] = useState<Tab>(hasMaps ? 'place' : 'custom')
  const id = useId()
  const panelId = `${id}-panel`
  const tabId = (t: Tab) => `${id}-tab-${t}`
  // Tabs pattern (WCAG 4.1.2): the selected tab reports aria-selected, controls the one panel and is the tab stop.
  const tabProps = (t: Tab) => ({ type: 'button' as const, role: 'tab', id: tabId(t), 'aria-selected': tab === t, 'aria-controls': tab === t ? panelId : undefined, tabIndex: tab === t ? 0 : -1, className: tabClass(tab === t), onClick: () => setTab(t) })
  return (
    <Modal title="Add stop" onClose={onClose}>
      <div role="tablist" aria-label="Kind of stop" className="mb-3 flex gap-2 text-sm" onKeyDown={onTabListKey}>
        <button {...tabProps('place')} disabled={!hasMaps} title={hasMaps ? undefined : 'Needs the Maps key'}>Place</button>
        <button {...tabProps('custom')}>Custom entry</button>
      </div>
      <div role="tabpanel" id={panelId} aria-labelledby={tabId(tab)}>
        {tab === 'place' && hasMaps ? <PlaceForm onAdd={onAddPlace} /> : <CustomForm onAdd={onAddCustom} />}
      </div>
    </Modal>
  )
}

function PlaceForm({ onAdd }: { onAdd: (p: PlacePick, durationMin: number, notes: string) => void }) {
  const places = useMapsLibrary('places')
  const id = useId()
  const listId = `${id}-options`
  const errorId = `${id}-error`
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<Suggestion[]>([])
  const [active, setActive] = useState(-1)
  const [pick, setPick] = useState<PlacePick | null>(null)
  const [duration, setDuration] = useState(60)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const token = useRef<google.maps.places.AutocompleteSessionToken | null>(null)

  // Suggestions are only shown while a long-enough query is typed and nothing is picked; the effect only fetches.
  const visible = !pick && query.trim().length >= 3 ? items : []
  const activeId = active >= 0 && active < visible.length ? `${listId}-${active}` : undefined
  useEffect(() => {
    if (!places || query.trim().length < 3 || pick) return
    const handle = setTimeout(async () => {
      try {
        token.current ??= new places.AutocompleteSessionToken()
        const { suggestions } = await places.AutocompleteSuggestion.fetchAutocompleteSuggestions({ input: query, sessionToken: token.current })
        setItems(suggestions.flatMap((s) => (s.placePrediction ? [{ placeId: s.placePrediction.placeId, primary: s.placePrediction.mainText?.text ?? s.placePrediction.text.text, secondary: s.placePrediction.secondaryText?.text ?? '', toPlace: () => s.placePrediction!.toPlace() }] : [])))
        setActive(-1)
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
      setQuery(place.displayName ?? s.primary); setItems([]); setActive(-1)
    } catch (e) { setError(`Could not load the place: ${String((e as Error).message ?? e)}`) } finally { setBusy(false) }
  }

  // Combobox keyboard model (WCAG 2.1.1): ArrowDown/Up move the highlight through the suggestions, Enter
  // chooses the highlighted one, Escape hides the list (and, having been handled here, leaves the dialog open).
  const onKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (visible.length === 0) return
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const next = e.key === 'ArrowDown' ? (active + 1) % visible.length : (active <= 0 ? visible.length : active) - 1
      setActive(next)
      document.getElementById(`${listId}-${next}`)?.scrollIntoView({ block: 'nearest' })
    } else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); choose(visible[active]) }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); setItems([]); setActive(-1) }
  }
  const status = busy ? 'Loading the place…' : pick ? `${pick.name} selected` : visible.length > 0 ? `${visible.length} suggestion${visible.length === 1 ? '' : 's'}` : ''

  return (
    <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); if (pick) onAdd(pick, duration, notes) }}>
      <label className="block text-sm">Search a place
        <input className={field} value={query} onChange={(e) => { setQuery(e.target.value); setPick(null); setActive(-1) }} onKeyDown={onKey} placeholder="Louvre" autoFocus autoComplete="off"
          role="combobox" aria-autocomplete="list" aria-expanded={visible.length > 0} aria-controls={visible.length > 0 ? listId : undefined} aria-activedescendant={activeId} aria-describedby={errorId} />
      </label>
      {visible.length > 0 && (
        <ul id={listId} role="listbox" aria-label="Suggestions" className="max-h-48 overflow-auto rounded border border-stone-200">
          {visible.map((s, i) => (
            <li key={s.placeId} role="none">
              {/* mousedown is cancelled so a click keeps focus (and the combobox) in the input; the keyboard path is the arrows above. */}
              <button type="button" role="option" id={`${listId}-${i}`} aria-selected={i === active} tabIndex={-1} onMouseDown={(e) => e.preventDefault()} onClick={() => choose(s)}
                className={`w-full px-3 py-2 text-left text-sm hover:bg-stone-50 ${i === active ? 'bg-indigo-50 ring-2 ring-inset ring-indigo-500' : ''}`}>
                <span className="font-medium">{s.primary}</span> <span className="text-stone-600">{s.secondary}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {/* Persistent live regions (WCAG 4.1.3): the suggestion count and pick for screen readers, the error for everyone. */}
      <p role="status" className="sr-only">{status}</p>
      {pick && <p className="text-sm text-stone-600">{pick.address}</p>}
      <DurationNotes duration={duration} setDuration={setDuration} notes={notes} setNotes={setNotes} />
      <p id={errorId} role="alert" className={error ? 'text-sm text-red-700' : 'sr-only'}>{error ?? ''}</p>
      <p className="text-xs text-stone-600">Powered by Google</p>
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
