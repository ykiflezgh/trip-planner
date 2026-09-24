import { expect, test } from './fixtures'

type Page = import('@playwright/test').Page

const rows = (page: Page) => page.locator('main section ol > li')
/**
 * The row's action menu: the trigger is named after its stop; the items are ARIA menuitems in the shared <Menu>.
 * Opening moves focus to the first enabled item a tick after the click (menu-button pattern), and Escape is handled
 * on the menu, not the trigger, so a spec waits for that focus before it sends keys.
 */
const openActions = async (page: Page, row: number, stop: string) => {
  await rows(page).nth(row).getByRole('button', { name: `Actions for ${stop}` }).click()
  await expect(rows(page).nth(row).getByRole('menu').locator('[role=menuitem]:focus')).toHaveCount(1)
}

test.describe('Agenda editing', () => {
  test('Add stop appends a custom entry after the pinned dinner and warns about the overrun', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(rows(page)).toHaveCount(3)

    await page.getByRole('button', { name: 'Add stop' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await dialog.getByLabel('Title', { exact: true }).fill('Gelato')
    await dialog.getByLabel('Duration (minutes)').fill('30')
    await dialog.getByRole('button', { name: 'Add entry' }).click()
    await expect(dialog).toBeHidden()

    await expect(rows(page)).toHaveCount(4)
    await expect(rows(page).nth(3)).toContainText('Gelato')
    await expect(rows(page).nth(3)).toContainText('21:00 – 21:30') // flows from the pinned dinner's end
    await expect(rows(page).nth(3)).toContainText("⚠ runs 30 min past the day's end")
  })

  test('Add stop with a pinned start keeps the list order but shows the pin', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(rows(page)).toHaveCount(3)

    await page.getByRole('button', { name: 'Add stop' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Title', { exact: true }).fill('Lunch')
    await dialog.getByLabel('Pinned start (optional)').fill('12:00')
    await dialog.getByLabel('Duration (minutes)').fill('30')
    await dialog.getByRole('button', { name: 'Add entry' }).click()

    await expect(rows(page)).toHaveCount(4)
    await expect(rows(page).nth(3)).toContainText('Lunch')
    await expect(rows(page).nth(3)).toContainText('📌 12:00 – 12:30') // appended (last key) but pinned at noon
  })

  test('Stop actions: Move up, Move to Day 2 and Delete', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(rows(page)).toHaveCount(3)

    // Move up on row 1 is disabled (nothing above it). Escape closes the menu and hands focus back to its trigger (WCAG 2.4.3).
    await openActions(page, 0, 'Colosseum')
    await expect(rows(page).nth(0).getByRole('menuitem', { name: 'Move up' })).toBeDisabled()
    await page.keyboard.press('Escape')
    await expect(rows(page).nth(0).getByRole('menu')).toBeHidden()
    await expect(rows(page).nth(0).getByRole('button', { name: 'Actions for Colosseum' })).toBeFocused()

    // Row 2 (Pantheon) moves above Colosseum; times follow the list order.
    await openActions(page, 1, 'Pantheon')
    await rows(page).nth(1).getByRole('menuitem', { name: 'Move up' }).click()
    await expect(rows(page).nth(0)).toContainText('Pantheon')
    await expect(rows(page).nth(0)).toContainText('09:00 – 10:00')
    await expect(rows(page).nth(1)).toContainText('Colosseum')
    await expect(rows(page).nth(1)).toContainText('10:00 – 11:30')

    // Pantheon to Day 2: two rows stay, focus lands on the next row (the moved row unmounts: WCAG 2.4.3), Day 2 shows it first thing.
    await openActions(page, 0, 'Pantheon')
    await rows(page).nth(0).getByRole('menuitem', { name: 'Move to Day 2' }).click()
    await expect(rows(page)).toHaveCount(2)
    await expect(rows(page).nth(0)).toContainText('Colosseum')
    await expect(rows(page).nth(0).locator('button[aria-pressed]')).toBeFocused() // the row's main toggle, the only aria-pressed button in it
    await page.getByRole('button', { name: 'Day 2 · Oct 6' }).click()
    await expect(rows(page)).toHaveCount(1)
    await expect(rows(page).nth(0)).toContainText('Pantheon')
    await expect(rows(page).nth(0)).toContainText('09:00 – 10:00')

    // Delete asks with a confirm() and removes the row; with no neighbour left, focus goes to the Agenda wrapper that now reads the empty-day message.
    page.once('dialog', (d) => d.accept())
    await openActions(page, 0, 'Pantheon')
    await rows(page).nth(0).getByRole('menuitem', { name: 'Delete' }).click()
    await expect(page.getByText('No stops on this day yet.')).toBeVisible()
    await expect(page.locator(':focus')).toContainText('No stops on this day yet.')
  })
})
