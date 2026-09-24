import { minutesFrom } from './time'

export const SLOT_MIN = 15

/** Snaps a minute offset to the grid (design §6.6: 15-minute drops), never below zero. */
export const snap = (minutes: number): number => Math.max(0, Math.round(minutes / SLOT_MIN) * SLOT_MIN)

/** "HH:mm" for a minute offset from an ISO wall-clock origin (may cross midnight: wraps to the clock). */
export function timeAt(originIso: string, offsetMin: number): string {
  const total = (+originIso.slice(11, 13) * 60 + +originIso.slice(14, 16) + offsetMin + 1440 * 7) % 1440
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

export interface ChronoEntry { id: string; start: string; order: string }

/**
 * Where a pinned start lands in the day's order (the Android `chronologicalOrder`): the neighbours
 * by start time, and whether the entry already sits between them so no key is needed.
 */
export function neighboursForTime(entries: ChronoEntry[], stopId: string, startIso: string): { afterOrder: string | null; beforeOrder: string | null; keepOrder: boolean } {
  const others = entries.filter((e) => e.id !== stopId)
  const before = [...others].reverse().find((e) => e.start <= startIso) ?? null
  const after = others.find((e) => e.start > startIso) ?? null
  const current = entries.findIndex((e) => e.id === stopId)
  const beforeIdx = before ? entries.indexOf(before) : -1
  return { afterOrder: before?.order ?? null, beforeOrder: after?.order ?? null, keepOrder: beforeIdx === current - 1 }
}

/** Minutes between a pointer y and the grid origin, given pixels per minute. */
export const minutesAtY = (y: number, top: number, pxPerMin: number): number => (y - top) / pxPerMin

export { minutesFrom }
