import type { DaySchedule } from './kotlin/tripPlanner'
import { minutesFrom } from './time'
import { SLOT_MIN } from './grid'

export const SLOT = SLOT_MIN

/** Grid geometry shared with the tests: rows are minutes from the day's start. */
export function layout(schedule: DaySchedule) {
  const origin = schedule.hoursStart
  const endMin = Math.max(minutesFrom(origin, schedule.hoursEnd), minutesFrom(origin, schedule.end)) + SLOT
  const totalMin = Math.ceil(endMin / 60) * 60
  const blocks = schedule.entries.map((e) => ({ id: e.id, top: minutesFrom(origin, e.start), height: Math.max(SLOT, minutesFrom(e.start, e.end)), pinned: e.pinned }))
  const legs = schedule.entries.flatMap((e) => (e.travelBefore ? [{ id: `${e.travelBefore.from}_${e.id}`, top: minutesFrom(origin, e.travelBefore.start), height: minutesFrom(e.travelBefore.start, e.travelBefore.end), pending: e.travelBefore.pending, mode: e.travelBefore.mode }] : []))
  return { totalMin, blocks, legs }
}

