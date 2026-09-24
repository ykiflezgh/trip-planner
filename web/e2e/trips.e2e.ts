import { expect, test } from './fixtures'

test.describe('Sign in and trip list', () => {
  test('signs in through the Auth emulator and lists the seeded trip', async ({ page, users, trip, signIn }) => {
    await page.goto('/app/')
    await expect(page.getByText('Sign in to see your trips.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible() // real button, never clicked

    await signIn(users.owner)
    await expect(page.getByRole('heading', { name: 'Your trips' })).toBeVisible()
    // Trips accumulate across the run (one seed per test), so the row is found by its href.
    const row = page.locator(`a[href="/app/t/${trip.tripId}"]`)
    await expect(row).toContainText('Rome weekend')
    await expect(row).toContainText('2026-10-05 → 2026-10-06 · Europe/Rome')

    await row.click()
    await expect(page).toHaveURL(new RegExp(`/app/t/${trip.tripId}$`))
    await expect(page.getByRole('heading', { name: 'Rome weekend' })).toBeVisible()
  })

  test('agenda shows computed times, the pinned dinner and the free time before it', async ({ page, users, trip, signIn }) => {
    await signIn(users.owner)
    await page.goto(`/app/t/${trip.tripId}`)

    const rows = page.locator('main section ol > li') // the Agenda's list; the map panel's <ol> of place stops sits in the <aside>
    await expect(rows).toHaveCount(3)
    await expect(rows.nth(0)).toContainText('Colosseum')
    await expect(rows.nth(0)).toContainText('09:00 – 10:30')
    await expect(rows.nth(1)).toContainText('Pantheon')
    await expect(rows.nth(1)).toContainText('10:30 – 11:30') // no travel leg stored: the engine counts 0 s
    await expect(rows.nth(2)).toContainText('Dinner in Trastevere')
    await expect(rows.nth(2)).toContainText('📌 19:30 – 21:00')
    await expect(rows.nth(2)).toContainText('480 min free before')
    await expect(rows.nth(2)).toContainText('Booked for four')
    await expect(rows.nth(2)).not.toContainText('⚠') // ends exactly at 21:00: no overrun

    await expect(page.getByText('09:00 – 21:00')).toBeVisible() // day hours (trip defaults)
    await expect(page.getByRole('button', { name: 'Day 2 · Oct 6' })).toBeVisible()
    await expect(page.getByRole('button', { name: /^Trip time · / })).toBeVisible() // runner is UTC, trip is Europe/Rome
  })

  test.describe('with a stored travel leg', () => {
    test.use({ seed: { legs: true } })
    test('agenda shows the leg line and shifts the next stop by the drive', async ({ page, users, trip, signIn }) => {
      await signIn(users.owner)
      await page.goto(`/app/t/${trip.tripId}`)
      const rows = page.locator('main section ol > li')
      await expect(rows).toHaveCount(3)
      await expect(rows.nth(1)).toContainText('🚗 10 min · 1.5 km')
      await expect(rows.nth(1)).toContainText('10:40 – 11:40')
      await expect(rows.nth(2)).toContainText('470 min free before')
    })
  })
})
