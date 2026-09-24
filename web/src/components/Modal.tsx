import { useId, useLayoutEffect, useRef } from 'react'

/**
 * Native <dialog>: focus trapping, Escape and backdrop for free. showModal()/close() run in a layout
 * effect so the cleanup's close() still finds the element in the document and the browser returns
 * focus to the opener (WCAG 2.4.3; a passive cleanup would run after React detached the node). The
 * backdrop closes through the element so the native `close` event drives onClose, and the title
 * names the dialog (4.1.2). The onClose guard drops the stale `close` event StrictMode's dev-only
 * effect replay queues (close → showModal again → event fires while the dialog is open).
 */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useLayoutEffect(() => { const d = ref.current; if (d && !d.open) d.showModal(); return () => d?.close() }, [])
  return (
    <dialog ref={ref} aria-labelledby={titleId} onClose={() => { if (!ref.current?.open) onClose() }} onClick={(e) => { if (e.target === ref.current) ref.current?.close() }}
      className="m-auto w-[min(32rem,92vw)] rounded-lg border border-stone-200 bg-white p-0 shadow-xl backdrop:bg-black/30">
      <div className="p-5" onClick={(e) => e.stopPropagation()}>
        <h2 id={titleId} className="mb-3 text-lg font-semibold">{title}</h2>
        {children}
      </div>
    </dialog>
  )
}

/** Field boundary at stone-500 (~4.8:1 on white): the 1px border is the input's only visual edge (WCAG 1.4.11). */
export const field = 'w-full rounded border border-stone-500 px-2 py-1.5 text-sm'
export const primary = 'rounded bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-50'
export const secondary = 'rounded border border-stone-300 px-3 py-1.5 text-sm'
