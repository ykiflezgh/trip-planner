import { getApp } from 'firebase/app'
import { getMessaging, getToken, isSupported, onMessage } from 'firebase/messaging'
import { setPushToken } from '../kotlin/tripPlanner'

const VAPID = (import.meta.env.VITE_FCM_VAPID_KEY as string | undefined) || undefined
const SW_URL = '/app/firebase-messaging-sw.js'

export const pushConfigured = Boolean(VAPID)
export const pushPermission = (): NotificationPermission | 'unsupported' => (typeof Notification === 'undefined' ? 'unsupported' : Notification.permission)

/**
 * Web push (companion §10): register the service worker, ask for permission (must follow a user
 * gesture), fetch the FCM token and hand it to the Kotlin core, which stores it in
 * `users/{uid}.fcmTokens` beside the mobile tokens.
 */
export async function enablePush(firebaseConfig: Record<string, string>): Promise<'granted' | 'denied' | 'unsupported'> {
  if (!VAPID || !(await isSupported())) return 'unsupported'
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return 'denied'
  const registration = await navigator.serviceWorker.register(`${SW_URL}?config=${encodeURIComponent(JSON.stringify(firebaseConfig))}`, { scope: '/app/' })
  const token = await getToken(getMessaging(getApp()), { vapidKey: VAPID, serviceWorkerRegistration: registration })
  setPushToken(token || null)
  return 'granted'
}

/** Re-attach silently on later visits when permission was already granted. */
export async function resumePush(firebaseConfig: Record<string, string>): Promise<void> {
  if (!VAPID || pushPermission() !== 'granted' || !(await isSupported())) return
  try { await enablePush(firebaseConfig) } catch { /* offline or blocked: the next visit retries */ }
}

/** Foreground messages: Firestore snapshots already update the page; show a brief toast. */
export function onForegroundPush(handler: (title: string, body: string, tripId: string | null) => void): void {
  if (!VAPID) return
  void isSupported().then((ok) => {
    if (!ok) return
    onMessage(getMessaging(getApp()), (payload) => {
      const d = payload.data ?? {}
      handler(d.tripName || 'Trip Planner', describe(d), d.tripId ?? null)
    })
  })
}

/** Mirrors `NotificationText.describe` in shared for the data keys the Functions send. */
export function describe(d: Record<string, string>): string {
  const who = d.actorName || 'Someone'
  const what = d.stopName || 'a stop'
  const day = Number(d.day ?? 0) + 1
  const from = d.fromDay != null && d.fromDay !== '' ? Number(d.fromDay) + 1 : null
  if (d.kind === 'digest' || (d.count && Number(d.count) > 1 && d.type === 'digest')) return `${who} made ${d.count} changes`
  switch (d.type) {
    case 'stop_added': return `${who} added ${what} to Day ${day}`
    case 'stop_removed': return `${who} removed ${what} from Day ${day}`
    case 'stop_moved': return from != null && from !== day ? `${who} moved ${what} from Day ${from} to Day ${day}` : `${who} reordered ${what} on Day ${day}`
    case 'stop_edited': return `${who} edited ${what}`
    case 'member_joined': return `${who} joined the trip`
    case 'entry_pinned': return `${who} moved ${what} to ${d.fixedStart || 'a fixed time'} on Day ${day}`
    case 'entry_unpinned': return `${who} unpinned ${what}`
    case 'entry_resized': return `${who} changed how long ${what} takes`
    default: return `${who} changed the plan`
  }
}
