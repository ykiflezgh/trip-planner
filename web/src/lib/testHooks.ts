import { getApp } from 'firebase/app'
import { getAuth, signInWithEmailAndPassword, signOut } from 'firebase/auth'

export interface TestHooks {
  /** Resolves with the uid. The Kotlin core sees the sign-in through `Firebase.auth.authStateChanged`. */
  signIn(email: string, password: string): Promise<string>
  signOut(): Promise<void>
  uid(): string | null
}
declare global { interface Window { __tpTest?: TestHooks } }

/** Test-only door for Playwright (web/e2e): email/password against the Auth emulator on the default app's Auth instance. Only ever imported by `connectEmulators`. */
export function installTestHooks(): void {
  const auth = getAuth(getApp())
  window.__tpTest = {
    signIn: async (email, password) => (await signInWithEmailAndPassword(auth, email, password)).user.uid,
    signOut: () => signOut(auth),
    uid: () => auth.currentUser?.uid ?? null,
  }
}
