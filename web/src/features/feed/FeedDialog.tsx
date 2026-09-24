import { Modal, primary, secondary } from '../../components/Modal'
import { useAnnouncer } from '../../hooks/useAnnouncer'

/**
 * "Add to my calendar" result (design §8.6): the private feed link, copyable or opened as webcal://.
 * [onMessage] is accepted for the caller's sake but not used: the page's message strip would show the same
 * confirmation dimmed behind the still-open modal, which is the duplicate the in-dialog status exists to avoid.
 */
export function FeedDialog({ url, onClose }: { url: string; onMessage?: (t: string) => void; onClose: () => void }) {
  const webcal = 'webcal://' + url.replace(/^https:\/\//, '')
  // The confirmation is shown and announced inside the dialog (WCAG 4.1.3): the page's message strip is inert behind the modal.
  const [status, announce] = useAnnouncer()
  const failed = () => announce('Could not copy the link; select it above and copy it yourself')
  // navigator.clipboard is undefined in insecure contexts (plain http off localhost), where writeText throws
  // synchronously rather than rejecting, so the same fallback covers both paths.
  const copy = () => { try { navigator.clipboard.writeText(url).then(() => announce('Calendar link copied'), failed) } catch { failed() } }
  return (
    <Modal title="Add to my calendar" onClose={onClose}>
      <p className="mb-2 text-sm text-stone-600">Subscribe your calendar app to this link. It is a read-only copy of the itinerary and can lag the app by up to an hour.</p>
      <p className="mb-2 break-all rounded bg-stone-100 px-3 py-2 text-sm">{url}</p>
      <ul className="mb-3 list-disc pl-5 text-xs text-stone-600">
        <li>Google Calendar: Other calendars → From URL, paste the link.</li>
        <li>Apple Calendar: File → New Calendar Subscription, or open the webcal link.</li>
        <li>Outlook: Add calendar → Subscribe from web.</li>
      </ul>
      <p className="mb-3 text-xs text-stone-600">Anyone with the link can read the itinerary. "Remove my calendar links" in the menu revokes it.</p>
      <div className="flex gap-2">
        <button className={primary} onClick={copy}>Copy link</button>
        <a className={secondary} href={webcal}>Open in Apple Calendar</a>
        <button className={secondary} onClick={onClose}>Done</button>
      </div>
      <p role="status" className={status ? 'mt-2 text-sm text-stone-600' : 'sr-only'}>{status}</p>
    </Modal>
  )
}
