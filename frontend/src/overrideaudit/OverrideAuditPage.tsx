import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { overrideAuditApi, type OverrideAuditEntry } from '../api/overrideAudit'
import { branchesApi, type Branch } from '../api/branches'
import { usersApi, type TeamUser } from '../api/users'
import { card, ErrorBanner, inputStyle, ghostBtn, Skeleton, inr, fmtDate, fmtDateTime, istToday } from '../shell/ui'

/**
 * Org-wide Override Audit (AGENT.md decision #4) — every amount override in the
 * organisation: the Accountant's provisional line overrides and the Finance Manager's
 * final claim-close overrides, in one feed. Owner and Finance Manager only.
 */

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

const KIND_LABEL: Record<OverrideAuditEntry['kind'], string> = {
  receipt: 'Receipt line',
  expense: 'Expense line',
  claim: 'Claim — final',
}

export default function OverrideAuditPage() {
  const ninetyAgo = useMemo(() => {
    const d = new Date(istToday())
    d.setDate(d.getDate() - 90)
    return d.toISOString().slice(0, 10)
  }, [])

  const [from, setFrom] = useState(ninetyAgo)
  const [to, setTo] = useState(istToday())
  const [branchId, setBranchId] = useState<number | ''>('')
  const [userId, setUserId] = useState<number | ''>('')

  const [rows, setRows] = useState<OverrideAuditEntry[] | null>(null)
  const [branches, setBranches] = useState<Branch[]>([])
  const [users, setUsers] = useState<TeamUser[]>([])
  const [error, setError] = useState('')
  const [tick, setTick] = useState(0)

  useEffect(() => {
    branchesApi.list().then(({ data }) => setBranches(data)).catch(() => {})
    usersApi.list().then(({ data }) => setUsers(data)).catch(() => {})
  }, [])

  useEffect(() => {
    let live = true
    setError('')
    setRows(null)
    overrideAuditApi.list({
      from, to,
      branchId: branchId === '' ? undefined : branchId,
      userId: userId === '' ? undefined : userId,
    })
      .then(({ data }) => { if (live) setRows(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the override audit.')) })
    return () => { live = false }
  }, [from, to, branchId, userId, tick])

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Override Audit</h1>
      <div style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 16 }}>
        Every amount override across the organisation — who, when, from what to what, and why.
      </div>

      <div style={{ ...card, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
        <div style={{ flex: '1 1 140px', minWidth: 130 }}>
          <Field label="From">
            <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} style={{ ...inputStyle, width: '100%', minHeight: 38 }} />
          </Field>
        </div>
        <div style={{ flex: '1 1 140px', minWidth: 130 }}>
          <Field label="To">
            <input type="date" value={to} max={istToday()} onChange={(e) => setTo(e.target.value)} style={{ ...inputStyle, width: '100%', minHeight: 38 }} />
          </Field>
        </div>
        <div style={{ flex: '1 1 160px', minWidth: 150 }}>
          <Field label="Branch">
            <select value={branchId} onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))} style={{ ...inputStyle, width: '100%', minHeight: 38 }}>
              <option value="">All branches</option>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.code} — {b.name}</option>)}
            </select>
          </Field>
        </div>
        <div style={{ flex: '1 1 160px', minWidth: 150 }}>
          <Field label="User">
            <select value={userId} onChange={(e) => setUserId(e.target.value === '' ? '' : Number(e.target.value))} style={{ ...inputStyle, width: '100%', minHeight: 38 }}>
              <option value="">Anyone</option>
              {users.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </Field>
        </div>
        <button type="button" onClick={() => setTick((n) => n + 1)} style={{ ...ghostBtn, minHeight: 38, padding: '0 16px' }}>Refresh</button>
      </div>

      <ErrorBanner message={error} />

      <div style={{ ...card, padding: 0, overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 760 }}>
          <thead>
            <tr>
              {['When', 'Type', 'Document / line', 'Branch', 'By', 'Was', 'Now', 'Reason'].map((h) => (
                <th key={h} style={{
                  fontSize: '0.66rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)',
                  textAlign: 'left', padding: '9px 12px', borderBottom: '1.5px solid var(--line)', background: '#FAFBFC',
                }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows == null && Array.from({ length: 6 }).map((_, i) => (
              <tr key={i}><td colSpan={8} style={{ padding: '10px 12px' }}><Skeleton height={22} /></td></tr>
            ))}
            {rows != null && rows.length === 0 && (
              <tr><td colSpan={8} style={{ padding: 18, color: 'var(--faint)', fontSize: '0.84rem' }}>No overrides in this range.</td></tr>
            )}
            {(rows ?? []).map((r, i) => (
              <tr key={i}>
                <td style={cell} title={fmtDateTime(r.at)}>{fmtDate(r.at)}</td>
                <td style={cell}>
                  <span style={{
                    fontSize: '0.68rem', fontWeight: 700, padding: '2px 7px', borderRadius: 5,
                    background: r.kind === 'claim' ? 'var(--purple-bg, #EFE7FB)' : 'var(--amber-bg)',
                    color: r.kind === 'claim' ? 'var(--purple, #6B3FA0)' : 'var(--amber)',
                  }}>
                    {KIND_LABEL[r.kind]}
                  </span>
                </td>
                <td style={{ ...cell, fontFamily: 'Consolas, monospace', fontSize: '0.76rem' }}>
                  {r.lineId ?? r.documentNo ?? '—'}
                </td>
                <td style={cell}>{r.branchCode ?? '—'}</td>
                <td style={cell}>{r.actorName}</td>
                <td style={{ ...cell, fontVariantNumeric: 'tabular-nums', color: 'var(--faint)', textDecoration: 'line-through' }}>{inr(r.amountBefore)}</td>
                <td style={{ ...cell, fontVariantNumeric: 'tabular-nums', fontWeight: 700, color: 'var(--amber)' }}>{inr(r.amountAfter)}</td>
                <td style={{ ...cell, fontSize: '0.8rem' }}>{r.reason ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

const cell = { padding: '10px 12px', borderTop: '1px solid var(--line)', fontSize: '0.83rem', verticalAlign: 'top' as const }

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: '0.72rem', fontWeight: 600, color: 'var(--muted)' }}>
      {label}
      {children}
    </label>
  )
}
