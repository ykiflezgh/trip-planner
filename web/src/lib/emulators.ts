import { getApp } from 'firebase/app'
import { connectAuthEmulator, getAuth } from 'firebase/auth'
import { connectFirestoreEmulator, getFirestore } from 'firebase/firestore'
import { connectFunctionsEmulator, getFunctions } from 'firebase/functions'

/** Emulator ports from firebase/firebase.json. 127.0.0.1, not localhost: Node 18+ may resolve localhost to ::1. */
export const EMULATOR = { host: '127.0.0.1', auth: 9099, firestore: 8080, functions: 5001 } as const

/**
 * Points the default app at the Emulator Suite (web/e2e, behind VITE_USE_EMULATORS=1). GitLive wraps
 * the same `firebase` package and the same default app, so the Kotlin core's Auth, Firestore and
 * Functions instances are the ones configured here. Must run after `initializeApp` and before any
 * Auth/Firestore/Functions operation (start() is fine: the Koin `createdAtStart` singles only
 * *launch* coroutines, nothing touches the SDK synchronously).
 */
export async function connectEmulators(): Promise<void> {
  const app = getApp()
  connectAuthEmulator(getAuth(app), `http://${EMULATOR.host}:${EMULATOR.auth}`, { disableWarnings: true }) // no red banner: it overlaps the page and trips axe
  connectFirestoreEmulator(getFirestore(app), EMULATOR.host, EMULATOR.firestore)
  connectFunctionsEmulator(getFunctions(app), EMULATOR.host, EMULATOR.functions) // default region us-central1 = GitLive's Firebase.functions
  const { installTestHooks } = await import('./testHooks')
  installTestHooks()
}
