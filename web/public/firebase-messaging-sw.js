/* Background web push (companion §10). Firebase config arrives in the registration URL; the
   notification text mirrors NotificationText.describe in shared. */
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js')
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js')

const config = JSON.parse(new URL(self.location.href).searchParams.get('config') || '{}')
firebase.initializeApp(config)
const messaging = firebase.messaging()

function describe(d) {
  const who = d.actorName || 'Someone'
  const what = d.stopName || 'a stop'
  const day = Number(d.day || 0) + 1
  const from = d.fromDay != null && d.fromDay !== '' ? Number(d.fromDay) + 1 : null
  if (d.kind === 'digest') return who + ' made ' + d.count + ' changes'
  switch (d.type) {
    case 'stop_added': return who + ' added ' + what + ' to Day ' + day
    case 'stop_removed': return who + ' removed ' + what + ' from Day ' + day
    case 'stop_moved': return from != null && from !== day ? who + ' moved ' + what + ' from Day ' + from + ' to Day ' + day : who + ' reordered ' + what + ' on Day ' + day
    case 'stop_edited': return who + ' edited ' + what
    case 'member_joined': return who + ' joined the trip'
    case 'entry_pinned': return who + ' moved ' + what + ' to ' + (d.fixedStart || 'a fixed time') + ' on Day ' + day
    case 'entry_unpinned': return who + ' unpinned ' + what
    case 'entry_resized': return who + ' changed how long ' + what + ' takes'
    default: return who + ' changed the plan'
  }
}

messaging.onBackgroundMessage(async (payload) => {
  const d = payload.data || {}
  // Quiet when a tab already has this trip in focus (companion §10).
  const clientsList = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  if (clientsList.some((c) => c.focused && c.url.includes('/app/t/' + d.tripId))) return
  const url = '/app/t/' + d.tripId + '?day=' + (d.day || 0) + '&view=day' + (d.stopId ? '&stop=' + d.stopId : '')
  await self.registration.showNotification(d.tripName || 'Trip Planner', { body: describe(d), tag: 'trip-' + d.tripId, data: { url }, icon: '/app/favicon.svg' })
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/app/'
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    const existing = list.find((c) => c.url.includes('/app'))
    if (existing) { existing.navigate(url); return existing.focus() }
    return self.clients.openWindow(url)
  }))
})
