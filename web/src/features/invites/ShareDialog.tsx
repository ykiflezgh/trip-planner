import { Modal, primary, secondary } from '../../components/Modal'
import { useAnnouncer } from '../../hooks/useAnnouncer'

/** Owner's invite link (design §8.2): copy, or the Web Share sheet where the browser has one. */
export function ShareDialog({ tripName, url, onMessage, onClose }: { tripName: string; url: string; onMessage: (t: string) => void; onClose: () => void }) {
  const text = `Join my trip "${tripName}" on Trip Planner: ${url}`
  const canShare = typeof navigator !== 'undefined' && 'share' in navigator
  // The confirmation is shown and announced inside the dialog (WCAG 4.1.3): the page's message strip is inert behind the modal.
  const [status, announce] = useAnnouncer()
  const copy = () => navigator.clipboard.writeText(url).then(() => { announce('Invite link copied'); onMessage('Invite link copied') }, () => announce('Could not copy the link; select it above and copy it yourself'))
  return (
    <Modal title="Invite to the trip" onClose={onClose}>
      <p className="mb-2 text-sm text-stone-600">Anyone with this link can join. It opens the app on a phone and this site elsewhere.</p>
      <p className="mb-3 break-all rounded bg-stone-100 px-3 py-2 text-sm">{url}</p>
      <div className="flex gap-2">
        <button className={primary} onClick={copy}>Copy link</button>
        {canShare && <button className={secondary} onClick={() => navigator.share({ title: `Invite to ${tripName}`, text, url }).catch(() => undefined)}>Share…</button>}
        <button className={secondary} onClick={onClose}>Done</button>
      </div>
      <p role="status" className={status ? 'mt-2 text-sm text-stone-600' : 'sr-only'}>{status}</p>
    </Modal>
  )
}
