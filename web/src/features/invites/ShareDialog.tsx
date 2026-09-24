import { Modal, primary, secondary } from '../../components/Modal'
import { useAnnouncer } from '../../hooks/useAnnouncer'

/**
 * Owner's invite link (design §8.2): copy, or the Web Share sheet where the browser has one.
 * [onMessage] is accepted for the caller's sake but not used: the page's message strip would show the same
 * confirmation dimmed behind the still-open modal, which is the duplicate the in-dialog status exists to avoid.
 */
export function ShareDialog({ tripName, url, onClose }: { tripName: string; url: string; onMessage?: (t: string) => void; onClose: () => void }) {
  const text = `Join my trip "${tripName}" on Trip Planner: ${url}`
  const canShare = typeof navigator !== 'undefined' && 'share' in navigator
  // The confirmation is shown and announced inside the dialog (WCAG 4.1.3): the page's message strip is inert behind the modal.
  const [status, announce] = useAnnouncer()
  const failed = () => announce('Could not copy the link; select it above and copy it yourself')
  // navigator.clipboard is undefined in insecure contexts (plain http off localhost), where writeText throws
  // synchronously rather than rejecting, so the same fallback covers both paths.
  const copy = () => { try { navigator.clipboard.writeText(url).then(() => announce('Invite link copied'), failed) } catch { failed() } }
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
