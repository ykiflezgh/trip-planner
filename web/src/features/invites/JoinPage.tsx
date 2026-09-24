import { useEffect, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router'
import { join } from '../../lib/kotlin/tripPlanner'
import { useKotlinState } from '../../hooks/useKotlinState'
import { listFacade } from '../../app/session'

/**
 * /join/{code} (companion §8.5): on phones the OS intercepts the link for the app first; this page
 * is the fallback with "Open in the app". On desktop a signed-in member redeems through the
 * existing `redeemInvite` callable and lands in the trip; a visitor signs in first.
 */
export function JoinPage() {
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const list = useKotlinState(useMemo(() => listFacade(), []))
  const facade = useMemo(() => join(code), [code])
  useEffect(() => () => facade.close(), [facade])
  const state = useKotlinState(facade)
  const signedIn = list?.signedIn ?? false
  useEffect(() => { if (signedIn) facade.join() }, [facade, signedIn])
  useEffect(() => { if (state?.joinedTripId) navigate(`/app/t/${state.joinedTripId}`, { replace: true }) }, [navigate, state?.joinedTripId])

  const ua = navigator.userAgent
  const phone = /Android|iPhone|iPad/i.test(ua)
  const appLink = /Android/i.test(ua) ? `intent://${location.host}/join/${code}#Intent;scheme=https;package=app.tripplanner;end` : `tripplanner://join/${code}`

  return (
    <main className="mx-auto max-w-md p-6">
      <h1 className="mb-2 text-xl font-semibold">You've been invited to a trip</h1>
      <p className="mb-4 text-sm text-stone-600">Invite code <code className="rounded bg-stone-100 px-2 py-1">{code}</code></p>
      {phone && <a className="mb-4 block rounded bg-indigo-600 px-4 py-3 text-center text-white" href={appLink}>Open in the app</a>}
      {!signedIn && <p className="text-sm text-stone-600">Sign in above to join on the web{phone ? ', or open the link in the app' : ''}.</p>}
      {signedIn && state?.joining && <p className="text-sm text-stone-600">Joining…</p>}
      {signedIn && state?.error && <p className="text-sm text-red-700">{state.error} <button className="underline" onClick={() => facade.join()}>try again</button></p>}
      {signedIn && state?.alreadyMember && !state.joinedTripId && <p className="text-sm text-stone-600">You are already a member; opening the trip…</p>}
    </main>
  )
}
