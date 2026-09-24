import { useEffect, useState } from 'react'
import { APIProvider } from '@vis.gl/react-google-maps'

import { MAPS_KEY } from '../../lib/maps'

/**
 * One Maps JavaScript API loader per trip page (map pane and Places search share it). Google
 * reports a rejected key through a global callback, not a promise; it is surfaced here.
 */
export function MapsProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<string | null>(null)
  useEffect(() => {
    ;(window as unknown as { gm_authFailure?: () => void }).gm_authFailure = () => setStatus('Google rejected the Maps key (check API enablement and referrer restrictions).')
  }, [])
  if (!MAPS_KEY) return <>{children}</>
  return (
    <APIProvider apiKey={MAPS_KEY} libraries={['places']} onError={(e) => setStatus(`Maps failed to load: ${String(e)}`)}>
      {status && <p className="mx-4 mt-2 rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">{status}</p>}
      {children}
    </APIProvider>
  )
}
