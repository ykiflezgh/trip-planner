import { expect, test } from './fixtures'

type Page = import('@playwright/test').Page

const openSettings = async (page: Page) => {
  await page.getByRole('button', { name: 'More', exact: true }).click()
  await page.getByRole('menuitem', { name: /^Settings/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('Trip settings')
  return dialog
}

/** The dialog closes on Save either way; the ViewModel's one-shot message shows the rejection (design §3.1 item 2). */
const saveExpecting = async (page: Page, dialog: ReturnType<Page['getByRole']>, message: string) => {
  await dialog.getByRole('button', { name: 'Save' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText(message)).toBeVisible()
  await page.getByRole('button', { name: 'dismiss' }).click()
  await expect(page.getByText(message)).toBeHidden()
}

test.describe('Trip settings (owner)', () => {
  test('validation messages come from the Kotlin ViewModel', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)

    let dialog = await openSettings(page)
    await dialog.getByLabel('End', { exact: true }).fill('2026-10-01')
    await saveExpecting(page, dialog, 'The trip must end on or after it starts')

    dialog = await openSettings(page)
    await dialog.getByLabel('Name').fill('   ') // passes the HTML `required`; Kotlin trims
    await saveExpecting(page, dialog, 'Give the trip a name')

    dialog = await openSettings(page)
    await dialog.getByLabel('Time zone').fill('Mars/Olympus')
    await saveExpecting(page, dialog, 'Unknown time zone: Mars/Olympus')

    dialog = await openSettings(page)
    await dialog.getByLabel('Day ends').fill('08:00')
    await saveExpecting(page, dialog, 'Day hours must be HH:mm and end after they start')

    await expect(page.getByRole('heading', { name: 'Rome weekend' })).toBeVisible() // nothing was written
  })

  test('renames the trip; the heading and the list follow', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)
    const dialog = await openSettings(page)
    await dialog.getByLabel('Name').fill('Roman holiday')
    await dialog.getByRole('button', { name: 'Save' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('heading', { name: 'Roman holiday' })).toBeVisible()

    await page.getByRole('link', { name: '← Trips' }).click()
    await expect(page.locator(`a[href="/app/t/${trip.tripId}"]`)).toContainText('Roman holiday')
  })
})
