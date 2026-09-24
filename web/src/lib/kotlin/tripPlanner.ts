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
  loading: boolean; error: string | null; message: string | null; dayHoursStart: string | null; dayHoursEnd: string | null; online: boolean; selectedDay: number; selectedStopId: string | null; dayCount: number
  canEdit: boolean; isOwner: boolean; pendingSync: boolean; trip: Trip | null; stops: Stop[]; legs: Leg[]; scheduleJson: string | null
  tripZone: string | null; deviceZone: string; zoneDiffers: boolean; localTime: boolean; zoneLabel: string
  /** Every day's computed schedule (JSON or null) and stops, for the Trip view. */
  schedules: (string | null)[]; stopsByDay: Stop[][]
  sharing: boolean; shareUrl: string | null; muted: boolean; memberCount: number
  calendarBusy: boolean; feedUrl: string | null; calendarFeedEnabled: boolean
  suggestOrderEnabled: boolean; suggesting: boolean
  suggestion: { day: number; rationale: string; warnings: string[]; orderedStopIds: string[] } | null
}
export interface ActivityRow { id: string; text: string; mine: boolean; type: string; stopId: string | null; day: number; createdAtMs: number | null }
export interface ActivityState { loading: boolean; error: string | null; rows: ActivityRow[] }
export interface JoinState { joining: boolean; error: string | null; joinedTripId: string | null; joinedTripName: string; alreadyMember: boolean }
export interface ActivityFacade extends Subscribable<ActivityState> { close(): void }
export interface JoinFacade extends Subscribable<JoinState> { join(): void; close(): void }
export interface DaySchedule {
  date: string; end: string; hoursStart: string; hoursEnd: string
  entries: { id: string; start: string; end: string; pinned: boolean; gapBeforeMin: number; travelBefore?: { from: string; to: string; mode: string; seconds: number; pending: boolean; start: string; end: string } }[]
  warnings: { type: string; entryId: string; minutes: number }[]
}

export interface Subscribable<S> { subscribe(onState: (s: S) => void): () => void }
export interface TripListFacade extends Subscribable<TripListState> { signIn(): void; signOut(): void; dismissError(): void }
export interface TripDetailFacade extends Subscribable<TripDetailState> {
  selectDay(day: number): void; selectStop(stopId: string | null): void; setLocalTime(enabled: boolean): void; close(): void
  addPlaceStop(placeId: string, name: string, address: string, lat: number, lng: number, durationMin: number, notes: string, day: number, afterOrder: string | null, beforeOrder: string | null): string | null
  addCustomEntry(name: string, durationMin: number, fixedStart: string | null, notes: string, day: number, afterOrder: string | null, beforeOrder: string | null): string | null
  moveStop(stopId: string, day: number, afterOrder: string | null, beforeOrder: string | null): void
  moveToDay(stopId: string, day: number): void
  deleteStop(stopId: string): void
  setDayHours(day: number, start: string, end: string): void
  pinEntry(stopId: string, hhmm: string, order: string | null): void
  pinAt(stopId: string, day: number, hhmm: string, afterOrder: string | null, beforeOrder: string | null, keepOrder: boolean): void
  unpinEntry(stopId: string): void
  resizeEntry(stopId: string, durationMin: number): void
  updateSettings(name: string, startDate: string, endDate: string, timeZone: string, defaultDayStart: string, defaultDayEnd: string, defaultTravelMode: string): void
  consumeMessage(): void
  showMessage?(text: string): void
  share(): void; consumeShare(): void
  addToCalendar(): void; revokeCalendarLinks(): void; consumeFeedUrl(): void
  suggestOrder(): void; applySuggestion(): void; dismissSuggestion(): void
  setMuted(muted: boolean): void
}

// Kotlin `object` -> a singleton behind getInstance() in the ES-module output.
const Web = TripPlannerWeb.getInstance()

export interface FirebaseWebConfig { appId: string; apiKey: string; projectId: string; authDomain: string; messagingSenderId: string }

export function start(config: FirebaseWebConfig, appLinkHost = '', calendarFeedEnabled = true, suggestOrderEnabled = true): void {
  Web.start(config.appId, config.apiKey, config.projectId, config.authDomain, config.messagingSenderId, appLinkHost, calendarFeedEnabled, suggestOrderEnabled)
}
export const resumeRedirect = (): Promise<boolean> => Web.resumeRedirect()
// Casts: the generated typings say Nullable<T> (undefined included) where the facade guarantees null.
export const tripList = (): TripListFacade => Web.tripList() as unknown as TripListFacade
export const tripDetail = (tripId: string): TripDetailFacade => Web.tripDetail(tripId) as unknown as TripDetailFacade
export const activity = (tripId: string): ActivityFacade => Web.activity(tripId) as unknown as ActivityFacade
export const join = (code: string): JoinFacade => Web.join(code) as unknown as JoinFacade
export const setPushToken = (token: string | null): void => Web.setPushToken(token)
export const computeDay = (dateIso: string, dayStart: string, dayEnd: string, entries: unknown[], travel: unknown[], defaultMode = 'driving'): DaySchedule =>
  JSON.parse(Web.computeDay(dateIso, dayStart, dayEnd, JSON.stringify(entries), JSON.stringify(travel), defaultMode))
export const parseSchedule = (json: string | null): DaySchedule | null => (json ? (JSON.parse(json) as DaySchedule) : null)
