import { expect, test } from './fixtures'

/**
 * /join/{code} (companion §8.5), suite 1: the signed-out page only. The signed-in redirect calls the
 * `redeemInvite` callable, so it waits for the Functions emulator (phase 2, seedInvite in support/seed.ts).
 */
test.describe('Join page', () => {
  test('signed out: shows the invitation and asks to sign in', async ({ page }) => {
    await page.goto('/join/abc123') // the dev server rewrites /join/** to the SPA like Hosting does
    await expect(page.getByRole('heading', { name: "You've been invited to a trip" })).toBeVisible()
    await expect(page.getByText('Invite code')).toContainText('abc123')
    await expect(page.getByText('Sign in above to join on the web.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Open in the app' })).toHaveCount(0) // desktop UA: no app link
  })
})
