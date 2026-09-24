import { useState } from 'react'
import type { Trip } from '../../lib/kotlin/tripPlanner'
import { Modal, field, primary, secondary } from '../../components/Modal'

const zones = (() => { try { return (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.('timeZone') ?? [] } catch { return [] } })()

/** Owner-only trip settings (design §3.1 item 2): name, dates, zone, default day hours, default travel mode. */
export function TripSettingsDialog({ trip, onSave, onClose }: { trip: Trip; onSave: (t: Omit<Trip, 'id' | 'ownerId' | 'memberIds' | 'pendingSync'>) => void; onClose: () => void }) {
  const [t, setT] = useState({ name: trip.name, startDate: trip.startDate, endDate: trip.endDate, timeZone: trip.timeZone, defaultDayStart: trip.defaultDayStart, defaultDayEnd: trip.defaultDayEnd, defaultTravelMode: trip.defaultTravelMode })
  const set = (k: keyof typeof t) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setT({ ...t, [k]: e.target.value })
  return (
    <Modal title="Trip settings" onClose={onClose}>
      <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); onSave(t) }}>
        <label className="block text-sm">Name<input className={field} value={t.name} onChange={set('name')} required /></label>
        <div className="flex gap-3">
          <label className="block flex-1 text-sm">Start<input className={field} type="date" value={t.startDate} onChange={set('startDate')} required /></label>
          <label className="block flex-1 text-sm">End<input className={field} type="date" value={t.endDate} onChange={set('endDate')} required /></label>
        </div>
        <label className="block text-sm">Time zone<input className={field} list="tz" value={t.timeZone} onChange={set('timeZone')} required /><datalist id="tz">{zones.map((z) => <option key={z} value={z} />)}</datalist></label>
        <div className="flex gap-3">
          <label className="block flex-1 text-sm">Day starts<input className={field} type="time" value={t.defaultDayStart} onChange={set('defaultDayStart')} /></label>
          <label className="block flex-1 text-sm">Day ends<input className={field} type="time" value={t.defaultDayEnd} onChange={set('defaultDayEnd')} /></label>
        </div>
        <label className="block text-sm">Default travel<select className={field} value={t.defaultTravelMode} onChange={set('defaultTravelMode')}><option value="driving">Driving</option><option value="walking">Walking</option></select></label>
        <p className="text-xs text-stone-500">Changing the zone keeps pinned times as wall-clock times (design §7).</p>
        <div className="flex gap-2"><button className={primary}>Save</button><button type="button" className={secondary} onClick={onClose}>Cancel</button></div>
      </form>
    </Modal>
  )
}
