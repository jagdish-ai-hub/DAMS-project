import { CountUp } from '../shell/motion'

/** Flat summary panel shared by the Accountant and Finance Manager overviews. */
export const statPanel = {
  background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 12,
  padding: '16px 18px',
} as const

/**
 * One summary box: what it counts (label), the figure, and a line saying what it means.
 * When `onClick` is given the whole box is a button that opens the detailed table.
 */
export function StatBox({ accent, label, value, note, share, onClick }: {
  accent: string
  label: string
  value: string
  note: string
  /** 0–1 — this box's share of all entries in view; draws a thin meter when given. */
  share?: number
  onClick?: () => void
}) {
  const body = (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: '0.76rem', fontWeight: 600, color: 'var(--muted)' }}>
        <span style={{ width: 7, height: 7, borderRadius: '50%', background: accent, flexShrink: 0 }} />
        {label}
        {onClick && <span style={{ marginLeft: 'auto', color: 'var(--faint)', fontWeight: 500 }}>View →</span>}
      </div>
      <div style={{ fontSize: 'clamp(1.3rem, 2.4vw, 1.65rem)', fontWeight: 700, color: 'var(--ink)', fontVariantNumeric: 'tabular-nums', lineHeight: 1.1, overflowWrap: 'anywhere' }}>
        <CountUp value={value} />
      </div>
      {share != null && (
        <div style={{ height: 4, borderRadius: 999, background: 'var(--bg)', overflow: 'hidden' }}>
          <div className="dams-meter" style={{ height: '100%', width: `${Math.round(share * 100)}%`, background: accent }} />
        </div>
      )}
      <div style={{ fontSize: '0.74rem', color: 'var(--faint)', lineHeight: 1.35 }}>{note}</div>
    </>
  )
  const style = { ...statPanel, display: 'flex', flexDirection: 'column' as const, gap: 6, textAlign: 'left' as const, width: '100%' }
  if (!onClick) return <div style={style}>{body}</div>
  return (
    <button
      type="button"
      onClick={onClick}
      className="dams-statbox"
      style={{ ...style, cursor: 'pointer', font: 'inherit', color: 'inherit' }}
    >
      {body}
    </button>
  )
}
