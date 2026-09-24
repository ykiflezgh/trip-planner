import { useEffect, useState } from 'react'
import type { Subscribable } from '../lib/kotlin/tripPlanner'

/** A Kotlin StateFlow as React state: subscribe on mount, unsubscribe on unmount (companion §6.2). */
export function useKotlinState<S>(facade: Subscribable<S> | null): S | null {
  const [state, setState] = useState<S | null>(null)
  useEffect(() => {
    if (!facade) return
    return facade.subscribe(setState)
  }, [facade])
  return state
}
