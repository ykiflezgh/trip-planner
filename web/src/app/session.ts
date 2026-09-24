import { tripList, type FirebaseWebConfig } from '../lib/kotlin/tripPlanner'

let cached: ReturnType<typeof tripList> | null = null
/** One trip-list facade for the session, created on first use (after `start()` in main.tsx, never at import time). */
export const listFacade = () => (cached ??= tripList())

/** Firebase web config is public by design (design §11); it lives in .env.local, not in source. */
export const firebaseConfig: FirebaseWebConfig & Record<string, string> = JSON.parse(import.meta.env.VITE_FIREBASE_CONFIG ?? '{}')
