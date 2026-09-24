import { describe, expect, it } from 'vitest'
import { appendKeys, neighboursAfterMove } from './order'

const list = [{ id: 'a', order: 'a' }, { id: 'b', order: 'b' }, { id: 'c', order: 'c' }]

describe('reorder neighbours', () => {
  it('moves down: lands between the next two', () => {
    expect(neighboursAfterMove(list, 0, 1)).toEqual({ afterOrder: 'b', beforeOrder: 'c' })
  })
  it('moves up to the top', () => {
    expect(neighboursAfterMove(list, 2, 0)).toEqual({ afterOrder: null, beforeOrder: 'a' })
  })
  it('moves to the end', () => {
    expect(neighboursAfterMove(list, 0, 2)).toEqual({ afterOrder: 'c', beforeOrder: null })
    expect(appendKeys(list)).toEqual({ afterOrder: 'c', beforeOrder: null })
    expect(appendKeys([])).toEqual({ afterOrder: null, beforeOrder: null })
  })
})
