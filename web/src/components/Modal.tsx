import { useEffect, useRef } from 'react'

/** Native <dialog>: focus trapping, Escape and backdrop for free. */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => { const d = ref.current; if (d && !d.open) d.showModal(); return () => d?.close() }, [])
  return (
    <dialog ref={ref} onClose={onClose} onClick={(e) => { if (e.target === ref.current) onClose() }}
      className="m-auto w-[min(32rem,92vw)] rounded-lg border border-stone-200 bg-white p-0 shadow-xl backdrop:bg-black/30">
      <div className="p-5" onClick={(e) => e.stopPropagation()}>
        <h2 className="mb-3 text-lg font-semibold">{title}</h2>
        {children}
      </div>
    </dialog>
  )
}

export const field = 'w-full rounded border border-stone-300 px-2 py-1.5 text-sm'
export const primary = 'rounded bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-50'
export const secondary = 'rounded border border-stone-300 px-3 py-1.5 text-sm'
