import { expect, test } from './fixtures'

test.describe('Day view keyboard editing', () => {
  test('ArrowDown on a focused block pins it 15 minutes later and announces it', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)

    const grid = page.getByRole('grid', { name: 'Calendar' })
    const colosseum = grid.getByRole('gridcell', { name: 'Colosseum, 09:00 to 10:30' })
    await expect(colosseum).toBeVisible()
    await colosseum.focus()
    await page.keyboard.press('ArrowDown')

    // Announcement (companion §6.6) goes out synchronously with the write.
    await expect(page.locator('p[aria-live="polite"]')).toHaveText('Colosseum moved to 09:15, Day 1')
    // Latency compensation: the local snapshot already carries the pin; the label re-renders from the schedule.
    await expect(grid.getByRole('gridcell', { name: 'Colosseum, 09:15 to 10:45, pinned' })).toBeVisible()
    // Rule 3: the unpinned rest of the day flows after the pinned entry.
    await expect(grid.getByRole('gridcell', { name: 'Pantheon, 10:45 to 11:45' })).toBeVisible()

    // Persisted in the emulator (rules allow owner writes): the pin survives a reload.
    await page.reload()
    await expect(page.getByRole('gridcell', { name: 'Colosseum, 09:15 to 10:45, pinned' })).toBeVisible()
  })

  test('Shift+ArrowDown resizes by one slot', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)
    const pantheon = page.getByRole('gridcell', { name: 'Pantheon, 10:30 to 11:30' })
    await pantheon.focus()
    await page.keyboard.press('Shift+ArrowDown')
    await expect(page.locator('p[aria-live="polite"]')).toHaveText('Pantheon now 75 min')
    await expect(page.getByRole('gridcell', { name: 'Pantheon, 10:30 to 11:45' })).toBeVisible()
  })

  test('Enter on a focused block opens the entry dialog', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)
    const colosseum = page.getByRole('gridcell', { name: 'Colosseum, 09:00 to 10:30' })
    await colosseum.focus()
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('Colosseum')
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })
})
