import { type CSSProperties, type ReactNode, useEffect } from 'react'

/** Small shared building blocks in the same inline-style idiom as the rest of the app. */

/** ₹ with Indian digit grouping, rounded to whole rupees — matches the mockups. */
export function inr(n: number | null | undefined): string {
  return '₹' + Math.round(n || 0).toLocaleString('en-IN')
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
  if (!message) return null
  return (
    <div style={{
      background: 'var(--red-bg)', border: '1px solid #EBC2C2', color: 'var(--red)',
      borderRadius: 8, padding: '10px 12px', fontSize: '0.82rem',
    }}>
      {message}
    </div>
  )
}

export function Modal(props: { title: string; subtitle?: string; onClose: () => void; children: ReactNode }) {
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
      onMouseDown={props.onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(16,24,40,.45)',
        display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
        padding: '60px 16px', zIndex: 50, overflowY: 'auto',
      }}
    >
      <div
        onMouseDown={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface)', borderRadius: 12, boxShadow: 'var(--shadow)',
          width: '100%', maxWidth: 460,
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
