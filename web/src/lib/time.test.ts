import { describe, expect, it } from 'vitest'
import { dayLabel, hhmm, minutesFrom, minutesLabel } from './time'

describe('time helpers', () => {
  it('formats wall-clock strings', () => {
    expect(hhmm('2026-09-22T09:05')).toBe('09:05')
  })
  it('measures minutes across a day boundary', () => {
    expect(minutesFrom('2026-09-22T09:00', '2026-09-22T10:30')).toBe(90)
    expect(minutesFrom('2026-09-22T23:00', '2026-09-23T01:00')).toBe(120)
  })
  it('labels durations and days', () => {
    expect(minutesLabel(45)).toBe('45 min')
    expect(minutesLabel(90)).toBe('1 h 30 min')
    expect(dayLabel('2026-09-22', 1)).toMatch(/^Day 2 · Sep 23$/)
    expect(dayLabel('', 0)).toBe('Day 1')
  })
})
