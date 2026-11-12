import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { sparePartsApi, type MovementType, type SpareBranch, type StockRow, type TransferStatus } from '../api/spareParts'
import { Badge, inputStyle } from '../shell/ui'

/** Small pieces shared by the Spare Parts screens. */

/** Part IDs and job card numbers are read digit by digit, so they get the monospace face. */
export const mono: CSSProperties = { fontFamily: 'var(--font-mono)', fontSize: '0.8rem', letterSpacing: '0.01em' }

export function useDebounced<T>(value: T, ms = 250): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

const TRANSFER_LABEL: Record<TransferStatus, { text: string; tone: 'amber' | 'blue' | 'green' | 'red' | 'gray' }> = {
  REQUESTED: { text: 'Waiting to be sent', tone: 'amber' },
  SENT: { text: 'On the road', tone: 'blue' },
  RECEIVED: { text: 'Received', tone: 'green' },
  DECLINED: { text: 'Declined', tone: 'red' },
  CANCELLED: { text: 'Cancelled', tone: 'gray' },
}

export function TransferBadge({ status }: { status: TransferStatus }) {
  const s = TRANSFER_LABEL[status]
  return <Badge tone={s.tone}>{s.text}</Badge>
}

export const MOVEMENT_LABEL: Record<MovementType, string> = {
  UPLOAD_ADD: 'Excel · added',
  UPLOAD_SET: 'Excel · set to file',
  MANUAL_ADD: 'Added by hand',
  CORRECTION: 'Corrected',
  BILLED: 'Billed to job card',
  TRANSFER_OUT: 'Sent to a branch',
  TRANSFER_IN: 'Received from a branch',
}

/** +3 in green, −2 in red: the sign carries the meaning, so colour never has to. */
export function SignedQty({ value }: { value: number }) {
  const positive = value > 0
  return (
    <span style={{ fontWeight: 700, color: positive ? 'var(--green)' : 'var(--red)', fontVariantNumeric: 'tabular-nums' }}>
      {positive ? '+' : '−'}{Math.abs(value)}
    </span>
  )
}

/** A line of explanation under a heading: muted, small, never longer than a sentence or two. */
export function Hint({ children }: { children: ReactNode }) {
  return <p style={{ fontSize: '0.8rem', color: 'var(--muted)', lineHeight: 1.5, margin: 0 }}>{children}</p>
}

/** A tinted note: tone "info" (blue) or "warn" (amber). */
export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn'; children: ReactNode }) {
  const warn = tone === 'warn'
  return (
    <div role="note" style={{
      fontSize: '0.8rem', lineHeight: 1.5, borderRadius: 8, padding: '9px 12px',
      background: warn ? 'var(--amber-bg)' : 'var(--navy3)',
      color: warn ? 'var(--amber)' : 'var(--navy)',
      borderLeft: `3px solid ${warn ? 'var(--amber)' : 'var(--navy2)'}`,
    }}>
      {children}
    </div>
  )
}

/** Branch code chip, e.g. OOJ. Name on hover. */
export function BranchChip({ branch }: { branch: Pick<SpareBranch, 'code' | 'name'> }) {
  return (
    <span title={branch.name} style={{
      ...mono, fontWeight: 700, fontSize: '0.72rem', color: 'var(--navy)', background: 'var(--navy3)',
      borderRadius: 5, padding: '1px 6px',
    }}>
      {branch.code}
    </span>
  )
}

/**
 * Type-ahead part picker. Searches the whole organization's stock (everyone may look everywhere) and shows what each
 * branch holds, so the person can see at a glance which branch has the part before choosing.
 */
export function PartPicker(props: {
  value: StockRow | null
  onChange: (row: StockRow | null) => void
  branches: SpareBranch[]
  autoFocus?: boolean
  /** Only offer parts at least one branch holds (used when asking another branch for a part). */
  inStockOnly?: boolean
}) {
  const [text, setText] = useState('')
  const q = useDebounced(text, 250)
  const [results, setResults] = useState<StockRow[]>([])
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (props.value || !open) return
    let cancelled = false
    setBusy(true)
    sparePartsApi.stock({ q, inStockOnly: props.inStockOnly })
      .then(({ data }) => { if (!cancelled) setResults(data.rows.slice(0, 8)) })
      .catch(() => { if (!cancelled) setResults([]) })
      .finally(() => { if (!cancelled) setBusy(false) })
    return () => { cancelled = true }
  }, [q, open, props.value, props.inStockOnly])

  useEffect(() => {
    function onDown(e: MouseEvent) {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [])

  if (props.value) {
    const p = props.value
    return (
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10, border: '1.5px solid var(--navy2)', borderRadius: 8,
        padding: '8px 11px', background: 'var(--navy3)',
      }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ ...mono, fontWeight: 700, color: 'var(--navy)' }}>{p.partCode}</div>
          <div style={{ fontSize: '0.8rem', color: 'var(--ink)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.description}</div>
        </div>
        <button
          type="button"
          onClick={() => { props.onChange(null); setText('') }}
          style={{ background: 'none', border: 'none', color: 'var(--navy2)', fontWeight: 700, fontSize: '0.78rem', cursor: 'pointer', minHeight: 36 }}
        >
          Change
        </button>
      </div>
    )
  }

  const code = (id: number) => props.branches.find((b) => b.id === id)?.code ?? `#${id}`
  return (
    <div ref={box} style={{ position: 'relative' }}>
      <input
        value={text}
        autoFocus={props.autoFocus}
        placeholder="Search part ID or description"
        onChange={(e) => { setText(e.target.value); setOpen(true) }}
        onFocus={() => setOpen(true)}
        aria-label="Search part"
        style={inputStyle}
      />
      {open && (
        <ul role="listbox" style={{
          position: 'absolute', zIndex: 20, left: 0, right: 0, top: 'calc(100% + 4px)', margin: 0, padding: 4, listStyle: 'none',
          background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 10, boxShadow: 'var(--shadow-lift)',
          maxHeight: 280, overflowY: 'auto',
        }}>
          {busy && results.length === 0 && <li style={{ padding: '10px 12px', fontSize: '0.8rem', color: 'var(--muted)' }}>Searching…</li>}
          {!busy && results.length === 0 && <li style={{ padding: '10px 12px', fontSize: '0.8rem', color: 'var(--muted)' }}>No matching part.</li>}
          {results.map((r) => (
            <li key={r.partId} role="option" aria-selected={false}>
              <button
                type="button"
                onClick={() => { props.onChange(r); setOpen(false) }}
                style={{
                  display: 'block', width: '100%', textAlign: 'left', background: 'transparent', border: 'none',
                  borderRadius: 7, padding: '8px 10px', cursor: 'pointer', minHeight: 44,
                }}
              >
                <span style={{ ...mono, fontWeight: 700, color: 'var(--navy)' }}>{r.partCode}</span>
                <span style={{ fontSize: '0.82rem', color: 'var(--ink)', marginLeft: 8 }}>{r.description}</span>
                <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--muted)', marginTop: 2 }}>
                  {r.cells.filter((c) => c.quantity > 0).map((c) => `${code(c.branchId)} ${c.quantity}`).join(' · ') || 'Nobody holds it right now'}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** A green confirmation that fades itself after a few seconds ("Billed 2 × MF450155 …"). */
export function useFlash(ms = 7000): { message: string; show: (m: string) => void } {
  const [message, setMessage] = useState('')
  const timer = useRef<ReturnType<typeof setTimeout>>()
  useEffect(() => () => clearTimeout(timer.current), [])
  return {
    message,
    show: (m: string) => {
      setMessage(m)
      clearTimeout(timer.current)
      timer.current = setTimeout(() => setMessage(''), ms)
    },
  }
}

export function FlashBanner({ message }: { message: string }) {
  if (!message) return null
  return (
    <div role="status" className="dams-anim-error" style={{
      background: 'var(--green-bg)', border: '1px solid #BFE3CF', borderLeft: '3px solid var(--green)',
      color: 'var(--green)', borderRadius: 8, padding: '10px 12px', fontSize: '0.82rem', fontWeight: 600,
    }}>
      {message}
    </div>
  )
}
