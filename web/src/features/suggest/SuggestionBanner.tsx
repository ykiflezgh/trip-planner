import type { TripDetailState } from '../../lib/kotlin/tripPlanner'
import { primary, secondary } from '../../components/Modal'

/** Preview strip for a Claude suggestion (design §8.7): the calendar already shows the proposed order. */
export function SuggestionBanner({ suggestion, onApply, onDismiss }: { suggestion: NonNullable<TripDetailState['suggestion']>; onApply: () => void; onDismiss: () => void }) {
  return (
    <section aria-label="Suggested order" className="mb-3 rounded border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm">
      <p className="font-medium">Suggested order for Day {suggestion.day + 1} · preview</p>
      {suggestion.rationale && <p className="text-stone-700">{suggestion.rationale}</p>}
      {suggestion.warnings.map((w, i) => <p key={i} className="text-red-700">{w}</p>)}
      <div className="mt-2 flex gap-2"><button className={primary} onClick={onApply}>Apply</button><button className={secondary} onClick={onDismiss}>Dismiss</button></div>
    </section>
  )
}
