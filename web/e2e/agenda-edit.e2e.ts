import { expect, test } from './fixtures'

const rows = (page: import('@playwright/test').Page) => page.locator('main section ol > li')

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

    // Move up on row 1 is disabled (nothing above it).
    await rows(page).nth(0).getByRole('button', { name: 'Stop actions' }).click()
    const moveUp = rows(page).nth(0).getByRole('button', { name: 'Move up' })
    await expect(moveUp).toBeDisabled()
    await moveUp.hover()
    await page.mouse.move(0, 0) // the menu closes when the pointer leaves it
    await expect(moveUp).toBeHidden()

    // Row 2 (Pantheon) moves above Colosseum; times follow the list order.
    await rows(page).nth(1).getByRole('button', { name: 'Stop actions' }).click()
    await rows(page).nth(1).getByRole('button', { name: 'Move up' }).click()
    await expect(rows(page).nth(0)).toContainText('Pantheon')
    await expect(rows(page).nth(0)).toContainText('09:00 – 10:00')
    await expect(rows(page).nth(1)).toContainText('Colosseum')
    await expect(rows(page).nth(1)).toContainText('10:00 – 11:30')

    // Pantheon to Day 2: two rows stay, Day 2 shows it first thing.
    await rows(page).nth(0).getByRole('button', { name: 'Stop actions' }).click()
    await rows(page).nth(0).getByRole('button', { name: 'Move to Day 2' }).click()
    await expect(rows(page)).toHaveCount(2)
    await expect(rows(page).nth(0)).toContainText('Colosseum')
    await page.getByRole('button', { name: 'Day 2 · Oct 6' }).click()
    await expect(rows(page)).toHaveCount(1)
    await expect(rows(page).nth(0)).toContainText('Pantheon')
    await expect(rows(page).nth(0)).toContainText('09:00 – 10:00')

    // Delete asks with a confirm() and removes the row.
    page.once('dialog', (d) => d.accept())
    await rows(page).nth(0).getByRole('button', { name: 'Stop actions' }).click()
    await rows(page).nth(0).getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByText('No stops on this day yet.')).toBeVisible()
  })
})
