import { expect, test } from './fixtures'

type Page = import('@playwright/test').Page

/** The time grid: a "Calendar" region of one group per day whose blocks are toggle buttons named "<stop>, <start> to <end>[, pinned][, <leg> before…][, warning: …]". */
const calendar = (page: Page) => page.getByRole('region', { name: 'Calendar' })
const block = (page: Page, name: string) => calendar(page).getByRole('button', { name, exact: true })

test.describe('Day view keyboard editing', () => {
  test('ArrowDown on a focused block pins it 15 minutes later and announces it', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)

    const colosseum = block(page, 'Colosseum, 09:00 to 10:30')
    await expect(colosseum).toBeVisible()
    // Editing blocks cite the keyboard model (WCAG 4.1.2): the sr-only instructions and the key list.
    await expect(colosseum).toHaveAttribute('aria-keyshortcuts', /ArrowDown/)
    await expect(colosseum).toHaveAttribute('aria-describedby', /.+/)
    await colosseum.focus()
    await page.keyboard.press('ArrowDown')

    // Announcement (companion §6.6) goes out synchronously with the write.
    await expect(page.locator('p[aria-live="polite"]')).toHaveText('Colosseum moved to 09:15, Day 1')
    // Latency compensation: the local snapshot already carries the pin; the label re-renders from the schedule.
    await expect(block(page, 'Colosseum, 09:15 to 10:45, pinned')).toBeVisible()
    // Rule 3: the unpinned rest of the day flows after the pinned entry; no leg is stored, so its name says the drive is still computing.
    await expect(block(page, 'Pantheon, 10:45 to 11:45, driving before, computing')).toBeVisible()

    // Persisted in the emulator (rules allow owner writes): the pin survives a reload.
    await page.reload()
    await expect(block(page, 'Colosseum, 09:15 to 10:45, pinned')).toBeVisible()
  })

  test('Shift+ArrowDown resizes by one slot', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)
    const pantheon = block(page, 'Pantheon, 10:30 to 11:30, driving before, computing')
    await pantheon.focus()
    await page.keyboard.press('Shift+ArrowDown')
    await expect(page.locator('p[aria-live="polite"]')).toHaveText('Pantheon now 75 min')
    await expect(block(page, 'Pantheon, 10:30 to 11:45, driving before, computing')).toBeVisible()
  })

  test('Enter on a focused block opens the entry dialog', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)
    const colosseum = block(page, 'Colosseum, 09:00 to 10:30')
    await colosseum.focus()
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('Colosseum')
    await expect(colosseum).toHaveAttribute('aria-pressed', 'true') // Enter selects before it opens the form
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })

  test('a click toggles the selection (aria-pressed)', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}?view=day`)
    const colosseum = block(page, 'Colosseum, 09:00 to 10:30')
    await expect(colosseum).toHaveAttribute('aria-pressed', 'false')
    await colosseum.click()
    await expect(colosseum).toHaveAttribute('aria-pressed', 'true')
    await colosseum.click()
    await expect(colosseum).toHaveAttribute('aria-pressed', 'false')
  })
})
