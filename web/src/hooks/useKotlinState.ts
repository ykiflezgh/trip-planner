import { useEffect, useState } from 'react'
import type { Subscribable } from '../lib/kotlin/tripPlanner'

/**
 * A Kotlin StateFlow as React state: subscribe on mount, unsubscribe on unmount (companion §6.2).
 * Each value is kept with the facade it came from, so after a facade swap (a route param change, or
 * useFacade's facade replacing `null` after the first render) the previous facade's state is not
 * shown against the new one: `null` again until the new facade emits.
 */
export function useKotlinState<S>(facade: Subscribable<S> | null): S | null {
  const [latest, setLatest] = useState<{ from: Subscribable<S>; state: S } | null>(null)
  useEffect(() => {
    if (!facade) return
    return facade.subscribe((state) => setLatest({ from: facade, state }))
  }, [facade])
  return latest && latest.from === facade ? latest.state : null
}
