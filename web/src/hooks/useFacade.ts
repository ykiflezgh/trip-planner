import { useEffect, useState, type DependencyList } from 'react'

/**
 * A Kotlin facade (one ViewModel with scoped listeners, companion §6.2) owned by an effect: created
 * when the effect runs, closed in its cleanup, closed and re-created when `deps` change, closed on
 * unmount. `null` until the effect has run, so callers tolerate null for the first render. Creating
 * it in useMemo and closing it in an effect cleanup was not equivalent: StrictMode's dev-only
 * mount/unmount/mount replay ran that cleanup against the memoized facade and then rendered with it
 * closed (an empty trip screen), and the double-invoked useMemo leaked a second facade. Here the
 * replay closes the first facade and creates a fresh one, so it ends with a live facade and no leak.
 */
export function useFacade<T extends { close(): void }>(create: () => T, deps: DependencyList): T | null {
  const [facade, setFacade] = useState<T | null>(null)
  // eslint-disable-next-line react-hooks/exhaustive-deps -- `deps` is the caller's key list, as for useEffect itself; `create` is only read when they change
  useEffect(() => {
    const f = create()
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the facade is an external system whose lifetime is this effect's
    setFacade(f)
    return () => f.close()
  }, deps) // eslint-disable-line react-hooks/exhaustive-deps
  return facade
}
