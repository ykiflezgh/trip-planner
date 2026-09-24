import { randomUUID } from 'node:crypto'
import { getApps, initializeApp } from 'firebase-admin/app'
import { FieldValue, getFirestore } from 'firebase-admin/firestore'
import { PROJECT_ID } from './emulator'

// firebase-admin needs no credentials while FIRESTORE_EMULATOR_HOST is set (emulator.ts sets the default).
const db = () => { if (getApps().length === 0) initializeApp({ projectId: PROJECT_ID }); return getFirestore() }

/** What FractionalIndex.spread(3) produces: between(null,null)='V', then 'k', then 's'; a UI append yields between('s', null)='w'. */
export const ORDER = ['V', 'k', 's'] as const

export interface SeedOptions {
  ownerUid: string
  member?: { uid: string; role: 'editor' | 'viewer' }
  /** Store one DRIVE leg Colosseum -> Pantheon (what the Function would write); default none. */
  legs?: boolean
  days?: 1 | 2
}
export interface SeededTrip { tripId: string; name: string; stops: { colosseum: string; pantheon: string; dinner: string } }

/**
 * One owner, day 0 = two place stops + one pinned custom entry, 2 days by default (enables the Trip view).
 * Field names follow Models.kt exactly; ids are readable so selectors and announcements are stable.
 * Expected schedule with no legs (Schedule.kt): Colosseum 09:00–10:30, Pantheon 10:30–11:30 (a missing leg
 * counts as 0 s, pending), Dinner pinned 19:30–21:00 with "480 min free before", no warnings.
 */
export async function seedTrip(o: SeedOptions): Promise<SeededTrip> {
  const tripId = `e2e-${randomUUID().slice(0, 8)}`
  const ts = FieldValue.serverTimestamp()
  const trip = db().doc(`trips/${tripId}`)
  const batch = db().batch()
  batch.set(trip, {
    name: 'Rome weekend', startDate: '2026-10-05', endDate: o.days === 1 ? '2026-10-05' : '2026-10-06', timeZone: 'Europe/Rome',
    defaultDayStart: '09:00', defaultDayEnd: '21:00', defaultTravelMode: 'driving',
    ownerId: o.ownerUid,
    memberIds: [o.ownerUid, ...(o.member ? [o.member.uid] : [])],
    roles: { [o.ownerUid]: 'owner', ...(o.member ? { [o.member.uid]: o.member.role } : {}) },
    createdAt: ts, updatedAt: ts,
  })
  const stop = (id: string, data: Record<string, unknown>) => batch.set(trip.collection('stops').doc(id), {
    kind: 'place', placeId: '', name: '', lat: null, lng: null, address: '', day: 0, order: '', durationMin: 60,
    fixedStart: null, modeToNext: null, notes: '', addedBy: o.ownerUid, updatedBy: o.ownerUid,
    addedAt: ts, updatedAt: ts, placeFetchedAt: ts, ...data,
  })
  stop('colosseum', { placeId: 'e2e-colosseum', name: 'Colosseum', lat: 41.8902, lng: 12.4922, address: 'Piazza del Colosseo, 1, Roma', order: ORDER[0], durationMin: 90 })
  stop('pantheon', { placeId: 'e2e-pantheon', name: 'Pantheon', lat: 41.8986, lng: 12.4769, address: 'Piazza della Rotonda, Roma', order: ORDER[1], durationMin: 60 })
  stop('dinner', { kind: 'custom', name: 'Dinner in Trastevere', order: ORDER[2], durationMin: 90, fixedStart: '19:30', notes: 'Booked for four', placeFetchedAt: null }) // nothing was fetched from Places for a custom entry: the model default (Models.kt), not the defaults' stamp
  if (o.legs) {
    // trips/{id}/travel/{from}_{to}_{mode}: TravelLeg.mode is the Kotlin enum name (DRIVE|WALK); the client never reads the doc id.
    batch.set(trip.collection('travel').doc('colosseum_pantheon_DRIVE'), { fromStopId: 'colosseum', toStopId: 'pantheon', mode: 'DRIVE', seconds: 600, meters: 1500, error: null, computedAt: Date.now() })
  }
  await batch.commit()
  return { tripId, name: 'Rome weekend', stops: { colosseum: 'colosseum', pantheon: 'pantheon', dinner: 'dinner' } }
}

/** Phase 2 (Functions emulator): an invite `redeemInvite` accepts; `expiresAt` must be a Timestamp (the Function calls `.toDate()`). */
export async function seedInvite(tripId: string, createdBy: string, code = `e2e${randomUUID().slice(0, 6)}`): Promise<string> {
  await db().doc(`invites/${code}`).set({ tripId, role: 'editor', createdBy, createdAt: FieldValue.serverTimestamp(), expiresAt: new Date(Date.now() + 86_400_000), maxUses: 20, uses: 0 })
  return code
}

/** Per-day hours override (design v1.1 §7, DayHoursDoc) for the day-hours spec's "pre-set" case. */
export const seedDayHours = (tripId: string, day: number, start: string, end: string, uid: string) =>
  db().doc(`trips/${tripId}/days/${day}`).set({ day, start, end, title: '', updatedBy: uid, updatedAt: FieldValue.serverTimestamp() })
