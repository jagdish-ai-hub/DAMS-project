import { useEffect, useState } from 'react'
import { apiMessage, sparePartsApi, type MovementRow, type MovementType } from '../api/spareParts'
import { ErrorBanner, SkeletonRows, card, fmtDateTime, inputStyle, td, th } from '../shell/ui'
import { useSpareAccess } from './SpareAccessContext'
import { Hint, MOVEMENT_LABEL, SignedQty, mono, useDebounced } from './spareUi'

/**
 * The stock ledger: every change to every quantity, newest first, who made it and why. It is append-only, so this is the
 * answer to "where did this part go?". Everyone who can look at stock can look at its history.
 */
export default function HistoryPage() {
  const { access } = useSpareAccess()
  const [branchId, setBranchId] = useState<number | ''>('')
  const [type, setType] = useState<MovementType | ''>('')
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 300)
  const [rows, setRows] = useState<MovementRow[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setRows(null)
    sparePartsApi.movements({ branchId: branchId === '' ? null : branchId, type, q: dq, limit: 200 })
      .then(({ data }) => { if (!cancelled) { setRows(data); setError('') } })
      .catch((e) => { if (!cancelled) setError(apiMessage(e, 'Could not load the history.')) })
    return () => { cancelled = true }
  }, [branchId, type, dq])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <section style={{ ...card, padding: '14px 16px', display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search part ID or description" aria-label="Search history"
          style={{ ...inputStyle, flex: '1 1 220px', minHeight: 40 }} />
        <select aria-label="Branch" value={branchId} onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
          style={{ ...inputStyle, width: 'auto', minHeight: 40 }}>
          <option value="">All branches</option>
          {access.branches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
        </select>
        <select aria-label="What happened" value={type} onChange={(e) => setType(e.target.value as MovementType | '')}
          style={{ ...inputStyle, width: 'auto', minHeight: 40 }}>
          <option value="">Everything</option>
          {(Object.keys(MOVEMENT_LABEL) as MovementType[]).map((t) => <option key={t} value={t}>{MOVEMENT_LABEL[t]}</option>)}
        </select>
      </section>

      <ErrorBanner message={error} />

      <section style={{ ...card, padding: 0, overflow: 'hidden' }}>
        {rows == null ? (
          <div style={{ padding: 16 }}><SkeletonRows rows={6} height={40} /></div>
        ) : (
          <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem', minWidth: 860 }}>
              <thead>
                <tr style={{ background: 'var(--bg)' }}>
                  <th style={th}>When</th><th style={th}>Branch</th><th style={th}>Part</th><th style={th}>What happened</th>
                  <th style={{ ...th, textAlign: 'right' }}>Change</th><th style={{ ...th, textAlign: 'right' }}>Now</th>
                  <th style={th}>Reference</th><th style={th}>By</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((m) => (
                  <tr key={m.id}>
                    <td style={{ ...td, whiteSpace: 'nowrap' }}>{fmtDateTime(m.createdAt)}</td>
                    <td style={{ ...td, ...mono }}>{m.branchCode}</td>
                    <td style={td}>
                      <span style={{ ...mono, fontWeight: 700 }}>{m.partCode}</span>
                      <div style={{ fontSize: '0.74rem', color: 'var(--muted)', maxWidth: 220 }}>{m.description}</div>
                    </td>
                    <td style={td}>
                      {MOVEMENT_LABEL[m.type]}
                      {m.note && <div style={{ fontSize: '0.72rem', color: 'var(--muted)', maxWidth: 260 }}>{m.note}</div>}
                    </td>
                    <td style={{ ...td, textAlign: 'right' }}><SignedQty value={m.qtyChange} /></td>
                    <td style={{ ...td, textAlign: 'right', fontWeight: 700 }}>{m.qtyAfter}</td>
                    <td style={{ ...td, ...mono }}>{m.reference ?? '—'}{m.vehicleNo && <div style={{ color: 'var(--muted)' }}>{m.vehicleNo}</div>}</td>
                    <td style={td}>{m.userName}{m.actingRole && <div style={{ fontSize: '0.7rem', color: 'var(--muted)' }}>as {m.actingRole.replace('_', ' ').toLowerCase()}</div>}</td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr><td style={{ ...td, padding: '28px 16px', color: 'var(--muted)' }} colSpan={8}>Nothing matches.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
        {rows != null && rows.length >= 200 && (
          <div style={{ padding: '10px 16px', borderTop: '1px solid var(--line)' }}><Hint>Showing the latest 200. Narrow the search for older ones.</Hint></div>
        )}
      </section>
    </div>
  )
}
