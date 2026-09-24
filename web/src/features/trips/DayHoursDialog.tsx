import { useId, useState } from 'react'
import { Modal, field, primary, secondary } from '../../components/Modal'

const valid = (t: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(t)

/** Per-day start and end in trip time (design v1.1 §8.4 step 4); the ViewModel rejects end <= start. */
export function DayHoursDialog({ label, start, end, onSave, onClose }: { label: string; start: string; end: string; onSave: (s: string, e: string) => void; onClose: () => void }) {
  const [s, setS] = useState(start)
  const [e, setE] = useState(end)
  const id = useId()
  return (
    <Modal title={`Day hours · ${label}`} onClose={onClose}>
      {/* Save stays enabled so the browser's required-field check can point at the empty field on submit. */}
      <form className="space-y-3" onSubmit={(ev) => { ev.preventDefault(); if (valid(s) && valid(e)) onSave(s, e) }}>
        <div className="flex gap-3">
          <TimeField id={`${id}-start`} label="Start" value={s} onChange={setS} error="Enter a start time." />
          <TimeField id={`${id}-end`} label="End" value={e} onChange={setE} error="Enter an end time." />
        </div>
        <div className="flex gap-2"><button className={primary}>Save</button><button type="button" className={secondary} onClick={onClose}>Cancel</button></div>
      </form>
    </Modal>
  )
}

/**
 * A required time. Clearing it shows an inline error tied to the field and announced (WCAG 3.3.1, 4.1.3).
 * The message follows the live value, and a time input passes through incomplete values on every keystroke,
 * so the region is polite (role="status"): an assertive alert would interrupt each intermediate state.
 */
function TimeField({ id, label, value, onChange, error }: { id: string; label: string; value: string; onChange: (v: string) => void; error: string }) {
  const bad = !valid(value)
  return (
    <div className="flex-1">
      <label className="block text-sm">{label}<input id={id} className={field} type="time" value={value} onChange={(x) => onChange(x.target.value)} required aria-invalid={bad || undefined} aria-describedby={`${id}-error`} /></label>
      <p id={`${id}-error`} role="status" className={bad ? 'mt-1 text-sm text-red-700' : 'sr-only'}>{bad ? error : ''}</p>
    </div>
  )
}
