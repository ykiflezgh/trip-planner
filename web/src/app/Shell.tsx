import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { Link, Outlet } from 'react-router'
import { firebaseConfig, listFacade } from './session'
import { useKotlinState } from '../hooks/useKotlinState'

/**
 * Read without importing ../lib/push/webPush: the module pulls firebase/messaging, which stays out of
 * the startup graph (companion §6.5) until the user turns notifications on or already has them on.
 */
const pushConfigured = Boolean(import.meta.env.VITE_FCM_VAPID_KEY)
const pushPermission = (): NotificationPermission | 'unsupported' => (typeof Notification === 'undefined' ? 'unsupported' : Notification.permission)

interface Toast { title: string; body: string; tripId: string | null }

export function Shell() {
  const facade = useMemo(() => listFacade(), [])
  const state = useKotlinState(facade)
  const [perm, setPerm] = useState(pushPermission())
  const [toast, setToast] = useState<Toast | null>(null)
  const home = useRef<HTMLAnchorElement>(null)
  const pushHelpId = useId()

  // Foreground pushes (companion §10) need the messaging module only once permission is granted; the
  // `live` flag drops a subscription whose effect was already cleaned up (StrictMode replays the effect).
  useEffect(() => {
    if (!pushConfigured || perm !== 'granted') return
    let live = true
    void import('../lib/push/webPush').then((m) => { if (live) m.onForegroundPush((title, body, tripId) => setToast({ title, body, tripId })) })
    return () => { live = false }
  }, [perm])

  const closeToast = useCallback(() => setToast(null), [])
  /** Dismissing unmounts the control that was activated, so focus moves to the home link first (WCAG 2.4.3). */
  const dismissToast = useCallback(() => { home.current?.focus(); setToast(null) }, [])
  const dismissError = () => { home.current?.focus(); facade.dismissError() }

  const enable = () => import('../lib/push/webPush').then((m) => m.enablePush(firebaseConfig))
    .then((r) => setPerm(r === 'granted' ? 'granted' : r === 'denied' ? 'denied' : 'unsupported')).catch(() => setPerm('unsupported'))
  const pushLabel = !pushConfigured ? 'Notifications need setup' : perm === 'unsupported' ? 'Notifications unsupported here' : perm === 'granted' ? 'Notifications on' : perm === 'denied' ? 'Notifications blocked' : 'Turn on notifications'
  const pushHelp = !pushConfigured ? 'Add the web push certificate (VAPID key) to the web config to enable browser notifications.' : perm === 'denied' ? 'Allow notifications for this site in the browser settings.' : undefined

  return (
    <div className="min-h-screen bg-stone-50 text-stone-900">
      <header className="flex items-center gap-4 border-b border-stone-200 bg-white px-4 py-3">
        <Link ref={home} to="/app" className="text-lg font-semibold">Trip Planner</Link>
        <span className="text-sm text-stone-600">web</span>
        <span className="ml-auto flex items-center gap-2 text-sm">
          {state?.signedIn && (
            <>
              {/* The hint is the button's accessible description, not only a hover title, so it reaches screen readers (WCAG 4.1.2). */}
              <button type="button" className="rounded border border-stone-300 px-2 py-1 text-xs disabled:opacity-60" title={pushHelp} aria-describedby={pushHelp ? pushHelpId : undefined}
                disabled={!pushConfigured || perm !== 'default'} onClick={enable}>
                {pushLabel}
              </button>
              {pushHelp && <span id={pushHelpId} className="sr-only">{pushHelp}</span>}
            </>
          )}
          {state?.signedIn ? (
            <>{state.userName} <button type="button" className="ml-2 rounded border px-2 py-1" onClick={() => facade.signOut()}>Sign out</button></>
          ) : state ? (
            <button type="button" className="rounded bg-indigo-600 px-3 py-1 text-white disabled:opacity-50" disabled={state.signingIn} onClick={() => facade.signIn()}>
              {state.signingIn ? 'Signing in…' : 'Sign in with Google'}
            </button>
          ) : null}
        </span>
      </header>
      {/* Live regions stay mounted and only their content toggles: a region created together with its text is not announced (WCAG 4.1.3). */}
      <div role="alert">
        {state?.error && (
          <p className="bg-red-50 px-4 py-2 text-sm text-red-700">{state.error} <button type="button" className="underline" onClick={dismissError}>Dismiss</button></p>
        )}
      </div>
      <div role="status" aria-live="polite">
        {toast && <PushToast toast={toast} onClose={closeToast} onDismiss={dismissToast} />}
      </div>
      <Outlet />
    </div>
  )
}

/**
 * Foreground push toast (companion §10). One with an "Open" action stays until dismissed; an
 * informational one auto-dismisses after 6 s unless hovered or focused (WCAG 2.2.1). Every toast has
 * a Dismiss button. Hover/focus state lives here so it resets with each toast.
 */
function PushToast({ toast, onClose, onDismiss }: { toast: Toast; onClose: () => void; onDismiss: () => void }) {
  const [held, setHeld] = useState(false)
  useEffect(() => {
    if (toast.tripId || held) return
    const t = setTimeout(onClose, 6000)
    return () => clearTimeout(t)
  }, [toast, held, onClose])
  return (
    <p className="mx-4 mt-2 rounded border border-stone-200 bg-white px-3 py-2 text-sm shadow"
      onMouseEnter={() => setHeld(true)} onMouseLeave={() => setHeld(false)} onFocus={() => setHeld(true)} onBlur={() => setHeld(false)}>
      <span className="font-medium">{toast.title}</span> · {toast.body}
      {toast.tripId && <Link className="ml-2 text-indigo-700 underline" to={`/app/t/${toast.tripId}`} onClick={onClose}>Open</Link>}
      <button type="button" className="ml-2 underline" onClick={onDismiss}>Dismiss</button>
    </p>
  )
}
