import { describe, expect, it } from 'vitest'
import { neighboursForTime, snap, timeAt } from './grid'

describe('grid helpers', () => {
  it('snaps to 15 minutes and clamps at zero', () => {
    expect(snap(7)).toBe(0); expect(snap(8)).toBe(15); expect(snap(-20)).toBe(0); expect(snap(100)).toBe(105)
  })
  it('formats a time offset from the origin', () => {
    expect(timeAt('2026-09-22T09:00', 90)).toBe('10:30'); expect(timeAt('2026-09-22T23:30', 60)).toBe('00:30')
  })
  it('finds chronological neighbours like the Android helper', () => {
    const entries = [{ id: 'a', start: '2026-09-22T09:00', order: 'a' }, { id: 'b', start: '2026-09-22T11:00', order: 'b' }, { id: 'c', start: '2026-09-22T13:00', order: 'c' }]
    // c pinned to 10:00 lands between a and b
    expect(neighboursForTime(entries, 'c', '2026-09-22T10:00')).toEqual({ afterOrder: 'a', beforeOrder: 'b', keepOrder: false })
    // b pinned to 12:00 is still between a and c: keep its key
    expect(neighboursForTime(entries, 'b', '2026-09-22T12:00')).toEqual({ afterOrder: 'a', beforeOrder: 'c', keepOrder: true })
    // a pinned to 14:00 goes last
    expect(neighboursForTime(entries, 'a', '2026-09-22T14:00')).toEqual({ afterOrder: 'c', beforeOrder: null, keepOrder: false })
  })
})
