import { assertLoopback, AUTH_HOST, clearEmulators, createUser, FIRESTORE_HOST, pingEmulators } from './support/emulator'

/** Once per run: verify the emulators, wipe them, create the two users. Workers inherit process.env, so the users travel in E2E_USERS. */
export default async function globalSetup(): Promise<void> {
  assertLoopback(FIRESTORE_HOST); assertLoopback(AUTH_HOST)
  await pingEmulators()
  await clearEmulators()
  const owner = await createUser('owner@e2e.test', 'E2E Owner')
  const member = await createUser('member@e2e.test', 'E2E Member')
  process.env.E2E_USERS = JSON.stringify({ owner, member })
}
