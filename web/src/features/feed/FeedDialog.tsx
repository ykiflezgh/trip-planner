import { Modal, primary, secondary } from '../../components/Modal'

/** "Add to my calendar" result (design §8.6): the private feed link, copyable or opened as webcal://. */
export function FeedDialog({ url, onMessage, onClose }: { url: string; onMessage: (t: string) => void; onClose: () => void }) {
  const webcal = 'webcal://' + url.replace(/^https:\/\//, '')
  return (
    <Modal title="Add to my calendar" onClose={onClose}>
      <p className="mb-2 text-sm text-stone-600">Subscribe your calendar app to this link. It is a read-only copy of the itinerary and can lag the app by up to an hour.</p>
      <p className="mb-2 break-all rounded bg-stone-100 px-3 py-2 text-sm">{url}</p>
      <ul className="mb-3 list-disc pl-5 text-xs text-stone-500">
        <li>Google Calendar: Other calendars → From URL, paste the link.</li>
        <li>Apple Calendar: File → New Calendar Subscription, or open the webcal link.</li>
        <li>Outlook: Add calendar → Subscribe from web.</li>
      </ul>
      <p className="mb-3 text-xs text-stone-500">Anyone with the link can read the itinerary. "Remove my calendar links" in the menu revokes it.</p>
      <div className="flex gap-2">
        <button className={primary} onClick={() => navigator.clipboard.writeText(url).then(() => onMessage('Calendar link copied'))}>Copy link</button>
        <a className={secondary} href={webcal}>Open in Apple Calendar</a>
        <button className={secondary} onClick={onClose}>Done</button>
      </div>
    </Modal>
  )
}
