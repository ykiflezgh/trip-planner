import { test as base, expect } from '@playwright/test'
import { seedTrip, type SeededTrip, type SeedOptions } from './support/seed'
import type { TestUser } from './support/emulator'

type Users = { owner: TestUser; member: TestUser }
/** Seed options a spec can set with test.use({ seed }); the uids are only known at run time, so membership is a role for `users.member`. */
type Seed = Partial<Omit<SeedOptions, 'ownerUid' | 'member'>> & { memberRole?: 'editor' | 'viewer' }
type Fixtures = {
  users: Users
  /** A fresh trip owned by `users.owner` for every test (a few ms in the emulator). */
  seed: Seed
  trip: SeededTrip
  /** Signs the page in through window.__tpTest and waits for the shell to reflect it. */
  signIn: (user: TestUser) => Promise<void>
}

export const test = base.extend<Fixtures>({
  users: async ({}, provide) => { await provide(JSON.parse(process.env.E2E_USERS ?? '{}') as Users) },
  seed: [{}, { option: true }],
  trip: async ({ users, seed }, provide) => {
    const { memberRole, ...rest } = seed
    await provide(await seedTrip({ ownerUid: users.owner.uid, ...rest, ...(memberRole ? { member: { uid: users.member.uid, role: memberRole } } : {}) }))
  },
  signIn: async ({ page }, provide) => {
    await provide(async (user) => {
      await page.goto('/app/') // Vite serves under base only: /app (no slash) is its "did you mean" page
      await page.waitForFunction(() => Boolean((window as unknown as { __tpTest?: unknown }).__tpTest))
      await page.evaluate(([e, p]) => (window as unknown as { __tpTest: { signIn(e: string, p: string): Promise<string> } }).__tpTest.signIn(e, p), [user.email, user.password])
      await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
    })
  },
})
export { expect }
