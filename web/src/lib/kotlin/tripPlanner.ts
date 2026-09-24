// The only file that touches the Kotlin/JS exports (design v1.1.1 §6.5). Everything React sees is
// a plain object described here.
import { TripPlannerWeb } from '@tripplanner/shared'

export interface Trip {
  id: string; name: string; startDate: string; endDate: string; timeZone: string
  defaultDayStart: string; defaultDayEnd: string; defaultTravelMode: string; ownerId: string; memberIds: string[]; pendingSync: boolean
}
export interface TripListState {
  loading: boolean; signedIn: boolean; userName: string | null; signingIn: boolean; pushEnabled: boolean; error: string | null; trips: Trip[]
}
export interface Stop { id: string; name: string; kind: string; day: number; order: string; durationMin: number; fixedStart: string | null; address: string; lat: number | null; lng: number | null; notes: string }
export interface Leg { from: string; to: string; mode: 'drive' | 'walk'; seconds: number | null; meters: number | null; error: string | null }
export interface TripDetailState {
  loading: boolean; error: string | null; online: boolean; selectedDay: number; selectedStopId: string | null; dayCount: number
  canEdit: boolean; isOwner: boolean; pendingSync: boolean; trip: Trip | null; stops: Stop[]; legs: Leg[]; scheduleJson: string | null
  tripZone: string | null; deviceZone: string; zoneDiffers: boolean; localTime: boolean; zoneLabel: string
}
export interface DaySchedule {
  date: string; end: string; hoursStart: string; hoursEnd: string
  entries: { id: string; start: string; end: string; pinned: boolean; gapBeforeMin: number; travelBefore?: { from: string; to: string; mode: string; seconds: number; pending: boolean; start: string; end: string } }[]
  warnings: { type: string; entryId: string; minutes: number }[]
}

export interface Subscribable<S> { subscribe(onState: (s: S) => void): () => void }
export interface TripListFacade extends Subscribable<TripListState> { signIn(): void; signOut(): void; dismissError(): void }
export interface TripDetailFacade extends Subscribable<TripDetailState> { selectDay(day: number): void; selectStop(stopId: string | null): void; setLocalTime(enabled: boolean): void; close(): void }

// Kotlin `object` -> a singleton behind getInstance() in the ES-module output.
const Web = TripPlannerWeb.getInstance()

export interface FirebaseWebConfig { appId: string; apiKey: string; projectId: string; authDomain: string; messagingSenderId: string }

export function start(config: FirebaseWebConfig, placesApiKey = '', appLinkHost = ''): void {
  Web.start(config.appId, config.apiKey, config.projectId, config.authDomain, config.messagingSenderId, placesApiKey, appLinkHost)
}
export const resumeRedirect = (): Promise<boolean> => Web.resumeRedirect()
export const tripList = (): TripListFacade => Web.tripList()
export const tripDetail = (tripId: string): TripDetailFacade => Web.tripDetail(tripId)
export const computeDay = (dateIso: string, dayStart: string, dayEnd: string, entries: unknown[], travel: unknown[], defaultMode = 'driving'): DaySchedule =>
  JSON.parse(Web.computeDay(dateIso, dayStart, dayEnd, JSON.stringify(entries), JSON.stringify(travel), defaultMode))
export const parseSchedule = (json: string | null): DaySchedule | null => (json ? (JSON.parse(json) as DaySchedule) : null)
