import { useMemo } from 'react'
import { Link, Outlet } from 'react-router'
import { tripList } from '../lib/kotlin/tripPlanner'
import { useKotlinState } from '../hooks/useKotlinState'

let cached: ReturnType<typeof tripList> | null = null
/** One trip-list facade for the session, created on first use (after `start()` in main.tsx, never at import time). */
export const listFacade = () => (cached ??= tripList())

export function Shell() {
  const facade = useMemo(() => listFacade(), [])
  const state = useKotlinState(facade)
  return (
    <div className="min-h-screen bg-stone-50 text-stone-900">
      <header className="flex items-center gap-4 border-b border-stone-200 bg-white px-4 py-3">
        <Link to="/" className="text-lg font-semibold">Trip Planner</Link>
        <span className="text-sm text-stone-500">web · read-only preview</span>
        <span className="ml-auto text-sm">
          {state?.signedIn ? (
            <>{state.userName} <button className="ml-2 rounded border px-2 py-1" onClick={() => facade.signOut()}>Sign out</button></>
          ) : state ? (
            <button className="rounded bg-indigo-600 px-3 py-1 text-white disabled:opacity-50" disabled={state.signingIn} onClick={() => facade.signIn()}>
              {state.signingIn ? 'Signing in…' : 'Sign in with Google'}
            </button>
          ) : null}
        </span>
      </header>
      {state?.error && (
        <p className="bg-red-50 px-4 py-2 text-sm text-red-700">{state.error} <button className="underline" onClick={() => facade.dismissError()}>dismiss</button></p>
      )}
      <Outlet />
    </div>
  )
}
