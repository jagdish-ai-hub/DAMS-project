import { useEffect, useState } from 'react'
import { dashboardApi, type ClaimsSummary, type ClaimTotals, type DashboardPeriod } from '../api/dashboard'
import { card, inr } from '../shell/ui'

/**
 * Claims at a glance (rev 62) — for the Owner dashboard and the Finance Manager page. Claimed is
 * what was spent / invoiced; Received is what has come back; Rejected is the part a closed claim
 * did not recover; Still open is what is outstanding on claims not yet closed. Expense claims
 * (Transfer to Claim) and warranty / AMC / CG receipt claims are counted together, then split.
 *
 * Pass `period` to follow a parent's period control (Owner dashboard); leave it out and the card
 * shows its own Today / Month toggle (Finance Manager page).
 */
export default function ClaimsSummaryCard(props: { branchId?: number; period?: DashboardPeriod }) {
  const [ownPeriod, setOwnPeriod] = useState<DashboardPeriod>('mtd')
  const period = props.period ?? ownPeriod
  const [data, setData] = useState<ClaimsSummary | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let live = true
    setFailed(false)
    dashboardApi.claims(period, props.branchId)
      .then(({ data }) => { if (live) setData(data) })
      .catch(() => { if (live) { setData(null); setFailed(true) } })
    return () => { live = false }
  }, [period, props.branchId])

  const total = data?.total

  return (
    <section style={{ ...card, marginBottom: 18 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap', marginBottom: 12 }}>
        <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Claims</h3>
        <span style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>
          {period === 'today' ? 'raised today' : 'raised this month'}
          {total ? ` · ${total.count} claim${total.count === 1 ? '' : 's'}, ${total.open} still open` : ''}
        </span>
        <span style={{ flex: 1 }} />
        {props.period == null && (
          <div style={{ display: 'flex', border: '1px solid var(--line)', borderRadius: 8, overflow: 'hidden' }}>
            {([['today', 'Today'], ['mtd', 'This month']] as const).map(([v, label]) => (
              <button key={v} type="button" onClick={() => setOwnPeriod(v)}
                style={{
                  border: 'none', padding: '5px 12px', fontSize: '0.76rem', fontWeight: 700, cursor: 'pointer',
                  background: ownPeriod === v ? 'var(--navy)' : 'var(--surface)',
                  color: ownPeriod === v ? '#fff' : 'var(--muted)',
                }}>
                {label}
              </button>
            ))}
          </div>
        )}
      </div>

      {failed && <div style={{ fontSize: '0.82rem', color: 'var(--faint)' }}>Could not load the claims summary.</div>}
      {!failed && !data && <div style={{ fontSize: '0.82rem', color: 'var(--faint)' }}>Loading…</div>}

      {data && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 150px), 1fr))', gap: 12 }}>
            <Figure label="Total claimed" value={data.total.claimed} tone="var(--navy2)" />
            <Figure label="Total received" value={data.total.received} tone="var(--green)" />
            <Figure label="Claim rejected / not recovered" value={data.total.rejected} tone="var(--red)" />
            <Figure label="Still open" value={data.total.pending} tone="var(--amber)" />
          </div>

          <div style={{ overflowX: 'auto', marginTop: 12 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 460 }}>
              <thead>
                <tr>
                  {['', 'Claims', 'Claimed', 'Received', 'Rejected', 'Still open'].map((h, i) => (
                    <th key={h || 'kind'} style={{
                      fontSize: '0.66rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)',
                      textAlign: i === 0 ? 'left' : 'right', padding: '6px 8px', borderBottom: '1.5px solid var(--line)',
                    }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <Row label="Expense claims" t={data.expenses} />
                <Row label="Warranty / AMC / CG receipts" t={data.receipts} />
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

function Figure({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div style={{ borderLeft: `3px solid ${tone}`, paddingLeft: 10 }}>
      <div style={{ fontSize: '0.68rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--muted)', fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: '1.2rem', fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{inr(value)}</div>
    </div>
  )
}

function Row({ label, t }: { label: string; t: ClaimTotals }) {
  const cell = { padding: '7px 8px', borderTop: '1px solid var(--line)', fontSize: '0.82rem', textAlign: 'right' as const, fontVariantNumeric: 'tabular-nums' as const }
  return (
    <tr>
      <td style={{ ...cell, textAlign: 'left', fontWeight: 600 }}>{label}</td>
      <td style={cell}>{t.count}</td>
      <td style={cell}>{inr(t.claimed)}</td>
      <td style={{ ...cell, color: 'var(--green)' }}>{inr(t.received)}</td>
      <td style={{ ...cell, color: t.rejected > 0 ? 'var(--red)' : undefined }}>{inr(t.rejected)}</td>
      <td style={{ ...cell, color: t.pending > 0 ? 'var(--amber)' : undefined }}>{inr(t.pending)}</td>
    </tr>
  )
}
