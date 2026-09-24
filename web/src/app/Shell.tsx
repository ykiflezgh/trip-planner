import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet } from 'react-router'
import { firebaseConfig, listFacade } from './session'
import { useKotlinState } from '../hooks/useKotlinState'
import { enablePush, onForegroundPush, pushConfigured, pushPermission } from '../lib/push/webPush'

export function Shell() {
  const facade = useMemo(() => listFacade(), [])
  const state = useKotlinState(facade)
  const [perm, setPerm] = useState(pushPermission())
  const [toast, setToast] = useState<{ title: string; body: string; tripId: string | null } | null>(null)
  useEffect(() => onForegroundPush((title, body, tripId) => setToast({ title, body, tripId })), [])
  useEffect(() => { if (!toast) return; const t = setTimeout(() => setToast(null), 6000); return () => clearTimeout(t) }, [toast])

  const pushLabel = !pushConfigured ? 'Notifications need setup' : perm === 'unsupported' ? 'Notifications unsupported here' : perm === 'granted' ? 'Notifications on' : perm === 'denied' ? 'Notifications blocked' : 'Turn on notifications'
  const pushTitle = !pushConfigured ? 'Add the web push certificate (VAPID key) to the web config to enable browser notifications.' : perm === 'denied' ? 'Allow notifications for this site in the browser settings.' : undefined

  return (
    <div className="min-h-screen bg-stone-50 text-stone-900">
      <header className="flex items-center gap-4 border-b border-stone-200 bg-white px-4 py-3">
        <Link to="/app" className="text-lg font-semibold">Trip Planner</Link>
        <span className="text-sm text-stone-500">web</span>
        <span className="ml-auto flex items-center gap-2 text-sm">
          {state?.signedIn && (
            <button className="rounded border border-stone-300 px-2 py-1 text-xs disabled:opacity-60" title={pushTitle} disabled={!pushConfigured || perm === 'unsupported' || perm === 'granted' || perm === 'denied'}
              onClick={() => enablePush(firebaseConfig).then((r) => setPerm(r === 'granted' ? 'granted' : r === 'denied' ? 'denied' : 'unsupported')).catch(() => setPerm('unsupported'))}>
              {pushLabel}
            </button>
          )}
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
      {toast && (
        <p role="status" className="mx-4 mt-2 rounded border border-stone-200 bg-white px-3 py-2 text-sm shadow">
          <span className="font-medium">{toast.title}</span> · {toast.body}
          {toast.tripId && <Link className="ml-2 text-indigo-700 underline" to={`/app/t/${toast.tripId}`} onClick={() => setToast(null)}>Open</Link>}
        </p>
      )}
      <Outlet />
    </div>
  )
}
