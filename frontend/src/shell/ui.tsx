import { type CSSProperties, type ReactNode, useEffect, useRef } from 'react'

/** Small shared building blocks in the same inline-style idiom as the rest of the app. */

/** ₹ with Indian digit grouping, rounded to whole rupees — matches the mockups. */
export function inr(n: number | null | undefined): string {
  return '₹' + Math.round(n || 0).toLocaleString('en-IN')
}

/*
 * Dates in DAMS are always branch-local (India). Every date is shown the same way
 * to every viewer regardless of their machine's timezone or locale:
 *   - calendar dates ('yyyy-mm-dd' from the API) are formatted from their parts,
 *     never through `new Date(str)` — which parses as UTC midnight and would slip
 *     to the previous day for a viewer in the Americas.
 *   - instants (ISO-8601 UTC from the API) are converted to Asia/Kolkata and
 *     labelled IST, so an overseas employee reads the same wall-clock time the
 *     branch did.
 * Storage and the wire format stay ISO; only display goes through here.
 */

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

const IST = 'Asia/Kolkata'

/** '2026-08-07' → '7 August 2026'. Returns '—' for null/empty, echoes anything unparseable. */
export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return iso
  return `${Number(m[3])} ${MONTHS[Number(m[2]) - 1]} ${m[1]}`
}

/** '2026-08-07' → '7 Aug 2026'. */
export function fmtDateShort(iso: string | null | undefined): string {
  if (!iso) return '—'
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return iso
  return `${Number(m[3])} ${MONTHS[Number(m[2]) - 1].slice(0, 3)} ${m[1]}`
}

/** ISO-8601 UTC instant → '7 August 2026, 3:12 pm IST' (India time, for every viewer). */
export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const p = new Intl.DateTimeFormat('en-GB', {
    timeZone: IST, day: 'numeric', month: 'long', year: 'numeric',
    hour: 'numeric', minute: '2-digit', hour12: true,
  }).formatToParts(d).reduce<Record<string, string>>((a, x) => { a[x.type] = x.value; return a }, {})
  return `${p.day} ${p.month} ${p.year}, ${p.hour}:${p.minute} ${(p.dayPeriod || '').toLowerCase()} IST`
}

/** Today's calendar date in India as 'yyyy-mm-dd' — for form defaults and date `max=` guards. */
export function istToday(): string {
  const p = new Intl.DateTimeFormat('en-CA', {
    timeZone: IST, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date()).reduce<Record<string, string>>((a, x) => { a[x.type] = x.value; return a }, {})
  return `${p.year}-${p.month}-${p.day}`
}

/** Up to two uppercase initials from a name, for avatar chips. */
export function initials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase()
}

export const card: CSSProperties = {
  background: 'var(--surface)',
  border: '1px solid var(--line)',
  borderRadius: 'var(--radius)',
  boxShadow: 'var(--shadow)',
  padding: '20px 22px',
}

export const cardTitle: CSSProperties = {
  fontSize: '0.92rem',
  fontWeight: 700,
  color: 'var(--ink)',
  marginBottom: 14,
}

export const th: CSSProperties = { padding: '7px 10px', fontWeight: 600, textAlign: 'left', color: 'var(--muted)' }
export const td: CSSProperties = { padding: '10px', borderTop: '1px solid var(--line)', verticalAlign: 'middle' }

export function primaryBtn(disabled = false): CSSProperties {
  return {
    background: disabled ? 'var(--navy2)' : 'var(--navy)',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    padding: '9px 14px',
    fontWeight: 700,
    fontSize: '0.85rem',
    cursor: disabled ? 'not-allowed' : 'pointer',
  }
}

export const ghostBtn: CSSProperties = {
  background: 'transparent',
  border: '1px solid var(--line)',
  borderRadius: 7,
  padding: '5px 11px',
  fontSize: '0.78rem',
  fontWeight: 600,
  color: 'var(--navy)',
  cursor: 'pointer',
}

export const dangerBtn: CSSProperties = {
  ...ghostBtn,
  color: 'var(--red)',
  borderColor: '#EBC2C2',
}

export const inputStyle: CSSProperties = {
  border: '1.5px solid var(--line)',
  borderRadius: 8,
  padding: '9px 11px',
  fontSize: '0.86rem',
  outline: 'none',
  width: '100%',
}

export function Field(props: {
  label: string
  children: ReactNode
  hint?: string
}) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: '0.76rem', fontWeight: 600, color: 'var(--muted)' }}>{props.label}</span>
      {props.children}
      {props.hint && <span style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{props.hint}</span>}
    </label>
  )
}

export function TextInput(props: {
  value: string
  onChange: (v: string) => void
  type?: string
  placeholder?: string
  required?: boolean
  maxLength?: number
  disabled?: boolean
}) {
  return (
    <input
      type={props.type ?? 'text'}
      value={props.value}
      placeholder={props.placeholder}
      required={props.required}
      maxLength={props.maxLength}
      disabled={props.disabled}
      onChange={(e) => props.onChange(e.target.value)}
      style={{ ...inputStyle, background: props.disabled ? 'var(--bg)' : '#fff' }}
    />
  )
}

export function ErrorBanner({ message }: { message: string }) {
  const ref = useRef<HTMLDivElement>(null)
  // A failed action often sets an error far from where the user clicked (the Submit
  // button sits at the bottom of a long form). Pull the message into view so the click
  // never looks like it did nothing.
  useEffect(() => {
    if (message) {
      ref.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }
  }, [message])
  if (!message) return null
  return (
    <div ref={ref} className="dams-anim-notice" style={{
      background: 'var(--red-bg)', border: '1px solid #EBC2C2', color: 'var(--red)',
      borderRadius: 8, padding: '10px 12px', fontSize: '0.82rem',
    }}>
      {message}
    </div>
  )
}

export function Modal(props: { title: string; subtitle?: string; onClose: () => void; children: ReactNode; maxWidth?: number }) {
  const { onClose } = props
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="dams-anim-backdrop"
      onMouseDown={props.onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(16,24,40,.45)',
        display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
        padding: '60px 16px', zIndex: 50, overflowY: 'auto',
      }}
    >
      <div
        className="dams-anim-modal"
        onMouseDown={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface)', borderRadius: 12, boxShadow: 'var(--shadow-lift)',
          width: '100%', maxWidth: props.maxWidth ?? 460,
        }}
      >
        <div style={{
          display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
          padding: '18px 20px', borderBottom: '1px solid var(--line)',
        }}>
          <div>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--navy)' }}>{props.title}</h3>
            {props.subtitle && (
              <div style={{ fontSize: '0.8rem', color: 'var(--muted)', marginTop: 2 }}>{props.subtitle}</div>
            )}
          </div>
          <button onClick={props.onClose} style={{ background: 'none', border: 'none', fontSize: '1.1rem', color: 'var(--muted)', cursor: 'pointer' }}>✕</button>
        </div>
        <div style={{ padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          {props.children}
        </div>
      </div>
    </div>
  )
}

/** Shimmer placeholder block — use while a panel's data is loading. */
export function Skeleton({ width = '100%', height = 14, radius = 6, style }: {
  width?: number | string
  height?: number | string
  radius?: number
  style?: CSSProperties
}) {
  return (
    <span
      className="dams-skeleton"
      style={{ display: 'block', width, height, borderRadius: radius, ...style }}
    />
  )
}

/** A stack of skeleton bars — the default stand-in for a loading list or table. */
export function SkeletonRows({ rows = 4, height = 40, gap = 10 }: { rows?: number; height?: number; gap?: number }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap }}>
      {Array.from({ length: rows }).map((_, i) => <Skeleton key={i} height={height} />)}
    </div>
  )
}

/** Spinning ring that inherits the current text colour — sized in `em`. Drop inside a busy button. */
export function Spinner({ style }: { style?: CSSProperties }) {
  return <span className="dams-spinner" style={style} aria-hidden="true" />
}

export function Badge({ children, tone = 'gray' }: { children: ReactNode; tone?: 'green' | 'amber' | 'gray' | 'red' }) {
  const map = {
    green: { c: 'var(--green)', b: 'var(--green-bg)' },
    amber: { c: 'var(--amber)', b: 'var(--amber-bg)' },
    gray: { c: 'var(--gray)', b: 'var(--gray-bg)' },
    red: { c: 'var(--red)', b: 'var(--red-bg)' },
  }[tone]
  return (
    <span style={{
      fontSize: '0.72rem', fontWeight: 700, color: map.c, background: map.b,
      borderRadius: 999, padding: '2px 9px', whiteSpace: 'nowrap',
    }}>
      {children}
    </span>
  )
}
