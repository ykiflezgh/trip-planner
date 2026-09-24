/** Display helpers for the ISO wall-clock strings the Kotlin engine emits ("2026-09-22T09:00"). */

export const hhmm = (iso: string): string => iso.slice(11, 16)

/** Minutes of `iso` measured from `originIso` (same or a later calendar day). */
export function minutesFrom(originIso: string, iso: string): number {
  const days = (Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10)) - Date.UTC(+originIso.slice(0, 4), +originIso.slice(5, 7) - 1, +originIso.slice(8, 10))) / 86_400_000
  return days * 1440 + (+iso.slice(11, 13) * 60 + +iso.slice(14, 16)) - (+originIso.slice(11, 13) * 60 + +originIso.slice(14, 16))
}

export const minutesLabel = (min: number): string => (min >= 60 ? `${Math.floor(min / 60)} h${min % 60 ? ` ${min % 60} min` : ''}` : `${min} min`)
export const km = (meters: number): string => `${(meters / 1000).toFixed(1)} km`

/** "Day 2 · Sep 23" like the apps' tabs; `startDate` is the trip's ISO date. */
export function dayLabel(startDate: string, day: number): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) return `Day ${day + 1}`
  const d = new Date(Date.UTC(+startDate.slice(0, 4), +startDate.slice(5, 7) - 1, +startDate.slice(8, 10) + day))
  return Number.isNaN(d.getTime()) ? `Day ${day + 1}` : `Day ${day + 1} · ${d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })}`
}
