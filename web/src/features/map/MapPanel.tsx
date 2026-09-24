import { useEffect } from 'react'
import { Map, Marker, useMap } from '@vis.gl/react-google-maps'
import type { Stop } from '../../lib/kotlin/tripPlanner'
import { MAPS_KEY as KEY } from './MapsProvider'

/**
 * Markers numbered by order; selecting one selects the stop in the calendar and back (companion §6.6).
 * Without a referrer-restricted web key (design v1.1.1 §11) it degrades to a stop list. Classic
 * markers on purpose: advanced markers need a cloud Map ID, which the spike does not have yet.
 * The Maps loader itself is `MapsProvider`, above the trip page, shared with the Places search.
 */
export function MapPanel({ stops, selectedId, onSelect }: { stops: Stop[]; selectedId: string | null; onSelect: (id: string | null) => void }) {
  const placed = stops.filter((s) => s.lat != null && s.lng != null)
  if (!KEY) {
    return (
      <div className="rounded border border-dashed border-stone-300 p-4 text-sm text-stone-600">
        <p className="mb-2">Map needs <code>VITE_MAPS_API_KEY</code> (a referrer-restricted Maps JavaScript key).</p>
        <ol className="list-decimal pl-5">{placed.map((s) => <li key={s.id} className={s.id === selectedId ? 'font-medium' : ''}>{s.name}</li>)}</ol>
      </div>
    )
  }
  return <LiveMap placed={placed} selectedId={selectedId} onSelect={onSelect} />
}

function LiveMap({ placed, selectedId, onSelect }: { placed: Stop[]; selectedId: string | null; onSelect: (id: string | null) => void }) {
  const sel = placed.find((s) => s.id === selectedId)
  if (placed.length === 0) return <p className="rounded border border-dashed border-stone-300 p-4 text-sm text-stone-500">No stops with a location on this day.</p>
  // Mounted only once stops exist: the Map reads defaultCenter at mount, so an empty first render would leave it at 0,0.
  return (
    <Map className="h-full min-h-[320px] rounded border border-stone-200" defaultZoom={13} defaultCenter={{ lat: placed[0].lat!, lng: placed[0].lng! }}
      center={sel ? { lat: sel.lat!, lng: sel.lng! } : undefined} gestureHandling="greedy" disableDefaultUI>
      <FitToStops stops={placed} />
      {placed.map((s, i) => (
        <Marker key={s.id} position={{ lat: s.lat!, lng: s.lng! }} title={s.name} label={{ text: String(i + 1), color: '#312e81', fontWeight: '600' }}
          opacity={selectedId && selectedId !== s.id ? 0.6 : 1} onClick={() => onSelect(s.id)} />
      ))}
    </Map>
  )
}

/** Frames every stop of the day whenever the set of stops changes (the apps do the same on day change). */
function FitToStops({ stops }: { stops: Stop[] }) {
  const map = useMap()
  const key = stops.map((s) => s.id).join(',')
  useEffect(() => {
    if (!map || stops.length === 0) return
    if (stops.length === 1) { map.setCenter({ lat: stops[0].lat!, lng: stops[0].lng! }); map.setZoom(14); return }
    const b = new google.maps.LatLngBounds()
    for (const s of stops) b.extend({ lat: s.lat!, lng: s.lng! })
    map.fitBounds(b, 48)
  }, [map, key]) // eslint-disable-line react-hooks/exhaustive-deps
  return null
}
