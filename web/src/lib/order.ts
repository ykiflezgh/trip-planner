/** Neighbour keys for a reorder inside one day's list (fractional keys come from Kotlin, design §7). */
export function neighboursAfterMove<T extends { id: string; order: string }>(list: T[], fromIndex: number, toIndex: number): { afterOrder: string | null; beforeOrder: string | null } {
  const without = list.filter((_, i) => i !== fromIndex)
  const after = without[toIndex - 1] ?? null
  const before = without[toIndex] ?? null
  return { afterOrder: after?.order ?? null, beforeOrder: before?.order ?? null }
}

/** Keys for inserting at the end of a list (the add-stop default). */
export const appendKeys = <T extends { order: string }>(list: T[]) => ({ afterOrder: list[list.length - 1]?.order ?? null, beforeOrder: null })
