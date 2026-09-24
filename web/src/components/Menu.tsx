import { useEffect, useId, useRef, useState } from 'react'

export interface MenuItem { label: string; onSelect: () => void; disabled?: boolean; danger?: boolean }

/**
 * Menu button with the ARIA menu keyboard model (WCAG 4.1.2, 2.4.3): the trigger reports
 * aria-haspopup/aria-expanded, opening focuses the first item, ArrowUp/Down and Home/End move,
 * Escape (from an item, or from the trigger during the tick before the first item is focused) and outside
 * clicks close, and closing (by choice or Escape) returns focus to the trigger.
 * The menu is named by its trigger (aria-labelledby), whatever shape the label takes: plain text, text
 * with an aria-hidden glyph, or a role="img" span carrying the name.
 */
export function Menu({ label, items, className = '', triggerClassName = '', align = 'right' }: { label: React.ReactNode; items: MenuItem[]; className?: string; triggerClassName?: string; align?: 'left' | 'right' }) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const id = useId()
  const trigger = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLDivElement>(null)
  const enabled = items.map((it, i) => ({ ...it, i })).filter((it) => !it.disabled)

  const close = (returnFocus = true) => { setOpen(false); if (returnFocus) trigger.current?.focus() }
  useEffect(() => {
    if (!open) return
    const first = enabled[0]?.i ?? 0
    setActive(first)
    const t = setTimeout(() => list.current?.querySelector<HTMLElement>(`[data-index="${first}"]`)?.focus(), 0)
    const onDoc = (e: MouseEvent) => { if (!list.current?.contains(e.target as Node) && !trigger.current?.contains(e.target as Node)) close(false) }
    document.addEventListener('mousedown', onDoc)
    return () => { clearTimeout(t); document.removeEventListener('mousedown', onDoc) }
  }, [open]) // eslint-disable-line react-hooks/exhaustive-deps

  const move = (delta: number) => {
    if (!enabled.length) return
    const pos = enabled.findIndex((it) => it.i === active)
    const next = enabled[(pos + delta + enabled.length) % enabled.length].i
    setActive(next); list.current?.querySelector<HTMLElement>(`[data-index="${next}"]`)?.focus()
  }
  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') { e.preventDefault(); close() }
    else if (e.key === 'ArrowDown') { e.preventDefault(); move(1) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); move(-1) }
    else if (e.key === 'Home') { e.preventDefault(); const f = enabled[0]?.i ?? 0; setActive(f); list.current?.querySelector<HTMLElement>(`[data-index="${f}"]`)?.focus() }
    else if (e.key === 'End') { e.preventDefault(); const l = enabled[enabled.length - 1]?.i ?? 0; setActive(l); list.current?.querySelector<HTMLElement>(`[data-index="${l}"]`)?.focus() }
    else if (e.key === 'Tab') close(false)
  }

  return (
    <span className={`relative ${className}`}>
      <button ref={trigger} id={`${id}-trigger`} type="button" aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined} className={triggerClassName}
        onClick={() => setOpen(!open)} onKeyDown={(e) => { if (!open && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) { e.preventDefault(); setOpen(true) } else if (open && e.key === 'Escape') { e.preventDefault(); close() } }}>
        {label}
      </button>
      {open && (
        <div ref={list} id={id} role="menu" aria-labelledby={`${id}-trigger`} onKeyDown={onKey}
          className={`absolute ${align === 'right' ? 'right-0' : 'left-0'} z-20 mt-1 flex w-64 flex-col rounded border border-stone-200 bg-white py-1 text-left text-sm shadow-lg`}>
          {items.map((it, i) => (
            <button key={i} type="button" role="menuitem" data-index={i} tabIndex={i === active ? 0 : -1} disabled={it.disabled} aria-disabled={it.disabled || undefined}
              className={`px-3 py-2 text-left hover:bg-stone-50 focus:bg-stone-100 focus:outline-none disabled:opacity-50 ${it.danger ? 'text-red-700 hover:bg-red-50' : ''}`}
              onClick={() => { close(); it.onSelect() }}>
              {it.label}
            </button>
          ))}
        </div>
      )}
    </span>
  )
}
