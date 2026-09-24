import { useState } from 'react'
import { Modal, field, primary, secondary } from '../../components/Modal'

const valid = (t: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(t)

/** Per-day start and end in trip time (design v1.1 §8.4 step 4); the ViewModel rejects end <= start. */
export function DayHoursDialog({ label, start, end, onSave, onClose }: { label: string; start: string; end: string; onSave: (s: string, e: string) => void; onClose: () => void }) {
  const [s, setS] = useState(start)
  const [e, setE] = useState(end)
  return (
    <Modal title={`Day hours · ${label}`} onClose={onClose}>
      <form className="space-y-3" onSubmit={(ev) => { ev.preventDefault(); if (valid(s) && valid(e)) onSave(s, e) }}>
        <div className="flex gap-3">
          <label className="block flex-1 text-sm">Start<input className={field} type="time" value={s} onChange={(x) => setS(x.target.value)} /></label>
          <label className="block flex-1 text-sm">End<input className={field} type="time" value={e} onChange={(x) => setE(x.target.value)} /></label>
        </div>
        <div className="flex gap-2"><button className={primary} disabled={!valid(s) || !valid(e)}>Save</button><button type="button" className={secondary} onClick={onClose}>Cancel</button></div>
      </form>
    </Modal>
  )
}
