import { useState } from 'react'
import type { DaySchedule, Stop } from '../../lib/kotlin/tripPlanner'
import { hhmm } from '../../lib/time'
import { Modal, field, primary, secondary } from '../../components/Modal'

/** The keyboard and screen-reader path to pin, resize and unpin (companion §6.6). */
export function EntryDialog({ stop, entry, onPin, onResize, onUnpin, onClose }: { stop: Stop; entry: DaySchedule['entries'][number] | undefined; onPin: (hhmm: string) => void; onResize: (min: number) => void; onUnpin: () => void; onClose: () => void }) {
  const [time, setTime] = useState(stop.fixedStart ?? (entry ? hhmm(entry.start) : '09:00'))
  const [duration, setDuration] = useState(stop.durationMin)
  return (
    <Modal title={stop.name} onClose={onClose}>
      <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); if (duration !== stop.durationMin) onResize(duration); if (time !== (stop.fixedStart ?? '')) onPin(time); onClose() }}>
        <p className="text-sm text-stone-600">{entry ? `Computed ${hhmm(entry.start)} – ${hhmm(entry.end)}` : ''}{stop.fixedStart && ` · pinned at ${stop.fixedStart}`}</p>
        <label className="block text-sm">Start (pins the entry)<input className={field} type="time" value={time} onChange={(e) => setTime(e.target.value)} /></label>
        <label className="block text-sm">Duration (minutes)<input className={field} type="number" min={5} max={1440} step={5} value={duration} onChange={(e) => setDuration(Number(e.target.value))} /></label>
        <div className="flex gap-2">
          <button className={primary}>Save</button>
          {stop.fixedStart && <button type="button" className={secondary} onClick={() => { onUnpin(); onClose() }}>Unpin</button>}
          <button type="button" className={secondary} onClick={onClose}>Cancel</button>
        </div>
      </form>
    </Modal>
  )
}
