import { describe, expect, it } from 'vitest'
import { layout } from './DayView'

describe('Day view layout', () => {
  it('positions blocks and legs in minutes from the day start', () => {
    const { totalMin, blocks, legs } = layout({
      date: '2026-09-22', hoursStart: '2026-09-22T09:00', hoursEnd: '2026-09-22T21:00', end: '2026-09-22T14:10',
      entries: [
        { id: 'a', start: '2026-09-22T09:00', end: '2026-09-22T10:30', pinned: false, gapBeforeMin: 0 },
        { id: 'b', start: '2026-09-22T12:00', end: '2026-09-22T13:00', pinned: true, gapBeforeMin: 80, travelBefore: { from: 'a', to: 'b', mode: 'drive', seconds: 600, pending: false, start: '2026-09-22T10:30', end: '2026-09-22T10:40' } },
      ],
      warnings: [],
    })
    expect(totalMin).toBe(780)
    expect(blocks).toEqual([{ id: 'a', top: 0, height: 90, pinned: false }, { id: 'b', top: 180, height: 60, pinned: true }])
    expect(legs).toEqual([{ id: 'a_b', top: 90, height: 10, pending: false, mode: 'drive' }])
  })
  it('extends the grid when the plan overruns the day', () => {
    const { totalMin } = layout({ date: 'd', hoursStart: '2026-09-22T09:00', hoursEnd: '2026-09-22T12:00', end: '2026-09-22T13:20', entries: [], warnings: [] })
    expect(totalMin).toBe(300)
  })
})
