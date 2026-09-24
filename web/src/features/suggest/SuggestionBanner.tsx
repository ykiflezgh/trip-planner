import { useEffect, useRef } from 'react'
import type { TripDetailState } from '../../lib/kotlin/tripPlanner'
import { primary, secondary } from '../../components/Modal'

/**
 * Preview strip for a Claude suggestion (design §8.7): the calendar already shows the proposed order.
 * It arrives seconds after the menu closed, while editing is frozen, so it takes focus on mount
 * (WCAG 2.4.3): Apply or Dismiss is the only thing left to do.
 */
export function SuggestionBanner({ suggestion, onApply, onDismiss }: { suggestion: NonNullable<TripDetailState['suggestion']>; onApply: () => void; onDismiss: () => void }) {
  const ref = useRef<HTMLElement>(null)
  useEffect(() => { ref.current?.focus() }, [])
  return (
    <section ref={ref} tabIndex={-1} aria-label="Suggested order"
      className="mb-3 rounded border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500">
      <p className="font-medium">Suggested order for Day {suggestion.day + 1} · preview</p>
      {suggestion.rationale && <p className="text-stone-700">{suggestion.rationale}</p>}
      {suggestion.warnings.map((w, i) => <p key={i} className="text-red-700">{w}</p>)}
      <div className="mt-2 flex gap-2"><button type="button" className={primary} onClick={onApply}>Apply</button><button type="button" className={secondary} onClick={onDismiss}>Dismiss</button></div>
    </section>
  )
}
