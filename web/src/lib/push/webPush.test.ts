import { describe as suite, expect, it } from 'vitest'
import { describe } from './webPush'

suite('push text', () => {
  it('mirrors the shared wording', () => {
    expect(describe({ type: 'stop_added', actorName: 'Ana', stopName: 'Louvre', day: '1' })).toBe('Ana added Louvre to Day 2')
    expect(describe({ type: 'stop_moved', actorName: 'Ana', stopName: 'Louvre', day: '2', fromDay: '0' })).toBe('Ana moved Louvre from Day 1 to Day 3')
    expect(describe({ type: 'entry_pinned', actorName: '', stopName: 'Belém Tower', day: '0', fixedStart: '16:15' })).toBe('Someone moved Belém Tower to 16:15 on Day 1')
    expect(describe({ kind: 'digest', actorName: 'Ana', count: '4' })).toBe('Ana made 4 changes')
  })
})
