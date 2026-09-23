import { useCallback, useEffect, useRef, useState } from 'react'

/*
 * Motion helpers shared across the app. Everything here is presentation only and becomes a
 * no-op under `prefers-reduced-motion` (and in jsdom, which has no matchMedia).
 */

/** Keep in step with --dur-exit in globals.css (plus a little slack). */
const EXIT_MS = 200

export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Exit animation for a fixed-position overlay (modal, drawer, menu, toast) without changing
 * who unmounts it. Attach the returned callback ref to the overlay's root. When React removes
 * that node — whichever way it was closed (✕, Esc, Cancel, Save, a parent state change) — a
 * short-lived, inert visual copy is left in its place and plays the exit animation defined for
 * `.dams-exit-ghost` in globals.css, then removes itself.
 *
 * Only for roots that are `position: fixed` (the copy is re-hosted under <body>).
 */
export function useExitGhost<T extends HTMLElement>() {
  const nodeRef = useRef<T | null>(null)
  return useCallback((node: T | null) => {
    if (node) {
      nodeRef.current = node
      return
    }
    const prev = nodeRef.current
    nodeRef.current = null
    if (!prev || prefersReducedMotion()) return
    // Scroll offsets vanish once the node is detached — read them now, while it is still laid out.
    const all = [prev, ...Array.from(prev.querySelectorAll<HTMLElement>('*'))]
    const scrolls: [number, number, number][] = []
    all.forEach((el, i) => {
      if (el.scrollTop || el.scrollLeft) scrolls.push([i, el.scrollTop, el.scrollLeft])
    })
    queueMicrotask(() => {
      // Still in the document → it was re-attached (not a real close); nothing to animate.
      if (prev.isConnected) return
      const clone = prev.cloneNode(true) as HTMLElement
      const host = document.createElement('div')
      host.className = 'dams-app dams-exit-ghost'
      host.setAttribute('aria-hidden', 'true')
      host.inert = true
      host.appendChild(clone)
      document.body.appendChild(host)
      const cloned = [clone, ...Array.from(clone.querySelectorAll<HTMLElement>('*'))]
      for (const [i, top, left] of scrolls) {
        if (cloned[i]) { cloned[i].scrollTop = top; cloned[i].scrollLeft = left }
      }
      window.setTimeout(() => host.remove(), EXIT_MS)
    })
  }, [])
}

function easeOutExpo(t: number) {
  return t >= 1 ? 1 : 1 - Math.pow(2, -10 * t)
}

const NUM = /^([^\d]*?)([\d,]+)([^\d]*)$/

/**
 * Figures that settle into place: animates the number inside a formatted string
 * ('₹1,23,456', '42', '-₹500') from its previous value (0 on first show) to the new one.
 * The final rendered text is always exactly `value`. Strings without a single number
 * inside (e.g. '—', '3 / 7') render unchanged.
 */
export function CountUp({ value, duration = 700 }: { value: string; duration?: number }) {
  // Start at 0 when this will animate, so the first paint doesn't flash the final figure.
  const [text, setText] = useState(() => {
    const m = NUM.exec(value)
    return m && !prefersReducedMotion() ? m[1] + '0' + m[3] : value
  })
  // The number currently on screen — the next animation starts from here.
  const shownRef = useRef(0)

  useEffect(() => {
    const m = NUM.exec(value)
    const target = m ? Number(m[2].replace(/,/g, '')) : NaN
    if (!m || !Number.isFinite(target) || prefersReducedMotion() || shownRef.current === target) {
      setText(value)
      if (Number.isFinite(target)) shownRef.current = target
      return
    }
    const [, pre, digits, post] = m
    const grouped = digits.includes(',')
    const from = shownRef.current
    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const n = Math.round(from + (target - from) * easeOutExpo(t))
      shownRef.current = n
      setText(t >= 1 ? value : pre + (grouped ? n.toLocaleString('en-IN') : String(n)) + post)
      if (t < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value, duration])

  return <>{text}</>
}
