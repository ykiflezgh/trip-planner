import { expect, test } from './fixtures'
import { seedDayHours } from './support/seed'

const openDayHours = async (page: import('@playwright/test').Page) => {
  await page.getByRole('button', { name: 'More', exact: true }).click()
  await page.getByRole('menuitem', { name: /^Day hours/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  return dialog
}

test.describe('Day hours', () => {
  test('sets per-day hours and the schedule follows', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(page.getByText('09:00 – 21:00')).toBeVisible()

    const dialog = await openDayHours(page)
    await expect(dialog).toContainText('Day hours · Day 1 · Oct 5')
    await dialog.getByLabel('Start').fill('10:00')
    await dialog.getByLabel('End').fill('18:00')
    await dialog.getByRole('button', { name: 'Save' }).click()
    await expect(dialog).toBeHidden()

    await expect(page.getByText('10:00 – 18:00')).toBeVisible()
    const rows = page.locator('main section ol > li')
    await expect(rows.nth(0)).toContainText('10:00 – 11:30')
    await expect(rows.nth(2)).toContainText("⚠ runs 180 min past the day's end") // dinner is pinned 19:30–21:00, the day now ends 18:00
  })

  test('rejects an end before the start with the ViewModel message', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    const dialog = await openDayHours(page)
    await dialog.getByLabel('Start').fill('10:00')
    await dialog.getByLabel('End').fill('09:00')
    await dialog.getByRole('button', { name: 'Save' }).click() // the dialog only checks the format; Kotlin rejects
    await expect(page.getByText('The day must end after it starts')).toBeVisible()
    await expect(page.getByText('09:00 – 21:00')).toBeVisible() // unchanged
  })

  test('opens with the stored override pre-set', async ({ page, users, trip, signIn }) => {
    await seedDayHours(trip.tripId, 0, '08:00', '20:00', users.owner.uid)
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(page.getByText('08:00 – 20:00')).toBeVisible()
    const dialog = await openDayHours(page)
    await expect(dialog.getByLabel('Start')).toHaveValue('08:00')
    await expect(dialog.getByLabel('End')).toHaveValue('20:00')
  })
})
