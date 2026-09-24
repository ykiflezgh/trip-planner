import { expect, test } from './fixtures'

// Suite 1 stand-in for the join redirect (needs the Functions emulator): membership is seeded, the roles render.

test.describe('Viewer', () => {
  test.use({ seed: { memberRole: 'viewer' } })
  test('sees the trip read-only: no editing controls, no owner menu items, no keyboard edits', async ({ page, users, trip, signIn }) => {
    await signIn(users.member)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(page.getByRole('heading', { name: 'Rome weekend' })).toBeVisible()
    await expect(page.locator('main section ol > li')).toHaveCount(3)
    await expect(page.getByRole('button', { name: 'Add stop' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Share' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Stop actions' })).toHaveCount(0)

    await page.getByRole('button', { name: 'More ▾' }).click()
    await expect(page.getByRole('menuitem', { name: 'Activity' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: /^Settings/ })).toHaveCount(0)
    await expect(page.getByRole('menuitem', { name: /^Day hours/ })).toHaveCount(0)
    await expect(page.getByRole('menuitem', { name: /^Suggest an order/ })).toHaveCount(0)
    await page.getByRole('button', { name: 'More ▾' }).click()

    await page.goto(`/app/t/${trip.tripId}?view=day`)
    const colosseum = page.getByRole('gridcell', { name: 'Colosseum, 09:00 to 10:30' })
    await colosseum.focus()
    await page.keyboard.press('ArrowDown')
    await expect(page.locator('p[aria-live="polite"]')).toHaveText('')
    await expect(colosseum).toBeVisible() // unchanged
  })
})

test.describe('Editor', () => {
  test.use({ seed: { memberRole: 'editor' } })
  test('can add stops but not share (owner only)', async ({ page, users, trip, signIn }) => {
    await signIn(users.member)
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(page.getByRole('button', { name: 'Add stop' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Share' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Stop actions' })).toHaveCount(3)
    await page.getByRole('button', { name: 'More ▾' }).click()
    await expect(page.getByRole('menuitem', { name: /^Day hours/ })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: /^Settings/ })).toHaveCount(0)
  })
})

test.describe('Non-member', () => {
  test('is denied by the Firestore rules', async ({ page, users, trip, signIn }) => {
    await signIn(users.member) // not in memberIds
    await page.goto(`/app/t/${trip.tripId}`)
    await expect(page.getByText(/permission/i).first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Rome weekend' })).toHaveCount(0)
  })
})
