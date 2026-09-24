/** Emulator REST helpers for the suite: the emulators accept any API key and nothing leaves the machine. */

// A demo-* project id is offline by definition (no credentials, no lookups), so a suite can never reach the real tripplanner-dev-fe0a4.
export const PROJECT_ID = 'demo-tripplanner'
// firebase emulators:exec sets both; the defaults cover a suite run against emulators started by hand.
export const FIRESTORE_HOST = (process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080')
export const AUTH_HOST = (process.env.FIREBASE_AUTH_EMULATOR_HOST ??= '127.0.0.1:9099')
// firebase-admin (seed.ts) otherwise probes the GCE metadata server on every worker start: a few seconds and a MetadataLookupWarning.
process.env.METADATA_SERVER_DETECTION ??= 'none'

export interface TestUser { uid: string; email: string; password: string; name: string }

/** Refuses anything that is not a local emulator: the suite wipes the database it points at. */
export function assertLoopback(host: string): void {
  if (!/^(127\.0\.0\.1|localhost|\[::1\]|0\.0\.0\.0):\d+$/.test(host)) throw new Error(`refusing to run e2e against non-loopback host ${host}`)
}

export async function pingEmulators(): Promise<void> {
  for (const [name, host] of [['Firestore', FIRESTORE_HOST], ['Auth', AUTH_HOST]] as const) {
    const ok = await fetch(`http://${host}/`).then((r) => r.ok).catch(() => false)
    if (!ok) throw new Error(`${name} emulator not reachable at ${host}; run: npm run e2e:emulators`)
  }
}

export async function clearEmulators(): Promise<void> {
  // A wipe that silently fails leaves the previous run's users and trips in place, so both responses are checked.
  const wipe = async (what: string, url: string, headers?: Record<string, string>) => {
    const res = await fetch(url, { method: 'DELETE', headers })
    if (!res.ok) throw new Error(`clear ${what}: ${res.status} ${await res.text()}`)
  }
  await wipe('Auth accounts', `http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts`, { Authorization: 'Bearer owner' })
  await wipe('Firestore documents', `http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`)
}

/** Auth emulator REST: any `key` is accepted; returns the new uid as `localId`. */
export async function createUser(email: string, name: string, password = 'e2e-password-1'): Promise<TestUser> {
  const res = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=any`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName: name, returnSecureToken: true }),
  })
  if (!res.ok) throw new Error(`signUp ${email}: ${res.status} ${await res.text()}`)
  const { localId } = (await res.json()) as { localId: string }
  return { uid: localId, email, password, name }
}
