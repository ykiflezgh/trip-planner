import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Screen-reader announcements for commits (companion §6.6). The region is cleared before each
 * message so the same text twice in a row (two identical nudges) is still announced.
 */
export function useAnnouncer(): [string, (t: string) => void] {
  const [text, setText] = useState('')
  const pending = useRef<number | null>(null)
  const announce = useCallback((t: string) => {
    setText('')
    if (pending.current) cancelAnimationFrame(pending.current)
    pending.current = requestAnimationFrame(() => { pending.current = null; setText(t) })
  }, [])
  useEffect(() => { if (!text) return; const h = setTimeout(() => setText(''), 4000); return () => clearTimeout(h) }, [text])
  return [text, announce]
}
