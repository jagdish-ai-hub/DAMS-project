import { useEffect, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { apiMessage, sparePartsApi, type SpareDashboard } from '../api/spareParts'
import { ErrorBanner, Skeleton, card, cardTitle, fmtDateTime, inr, td, th } from '../shell/ui'
import { Hint, MOVEMENT_LABEL, SignedQty, mono } from './spareUi'

/** How many of the latest stock changes the overview lists; the rest are one click away in History. */
const LATEST_SHOWN = 6

/**
 * The Owner's picture of Spare Parts across every branch: what the stock is worth and where it sits, what was billed this
 * month, where requests stand, and the latest changes. Read-only; every number links to the screen that explains it.
 */
export default function OverviewPage() {
  const [data, setData] = useState<SpareDashboard | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    sparePartsApi.dashboard()
      .then(({ data: d }) => { if (!cancelled) setData(d) })
      .catch((e) => { if (!cancelled) setError(apiMessage(e, 'Could not load the overview.')) })
    return () => { cancelled = true }
  }, [])

  if (error) return <ErrorBanner message={error} />
  if (!data) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12 }}>
          {[0, 1, 2, 3].map((i) => <Skeleton key={i} height={92} radius={10} />)}
        </div>
        <Skeleton height={220} radius={10} />
      </div>
    )
  }

  const open = data.requestCounts.REQUESTED + data.requestCounts.SENT
  const maxValue = Math.max(1, ...data.branches.map((b) => b.value))
  const totalBills = data.branches.reduce((n, b) => n + b.billsThisMonth, 0)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12 }}>
        <Tile label="Stock value" value={inr(data.totalValue)} note={`${data.partCount} parts on the books`} accent="var(--navy2)" />
        <Tile label="Units in stock" value={String(data.totalUnits)} note={`across ${data.branches.length} branches`} accent="var(--green)" />
        <Tile label="Requests open" value={String(open)} note={`${data.requestCounts.SENT} on the road`} accent="var(--amber)" to="/app/spare-parts/requests" />
        <Tile label="Billed this month" value={String(data.branches.reduce((n, b) => n + b.unitsBilledThisMonth, 0))}
          note={`${totalBills} ${totalBills === 1 ? 'bill' : 'bills'}`} accent="var(--purple)" to="/app/spare-parts/history" />
      </div>

      <section style={{ ...card, padding: 0, overflow: 'hidden' }}>
        <h2 style={{ ...cardTitle, padding: '16px 18px 0', margin: '0 0 10px' }}>Branch by branch</h2>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem', minWidth: 640 }}>
            <thead>
              <tr style={{ background: 'var(--bg)' }}>
                <th style={th}>Branch</th>
                <th style={{ ...th, textAlign: 'right' }}>Parts held</th>
                <th style={{ ...th, textAlign: 'right' }}>Units</th>
                <th style={{ ...th, minWidth: 200 }}>Value</th>
                <th style={{ ...th, textAlign: 'right' }}>Billed this month</th>
              </tr>
            </thead>
            <tbody>
              {data.branches.map((b) => (
                <tr key={b.branchId}>
                  <td style={td}><span style={{ ...mono, fontWeight: 700, color: 'var(--navy)' }}>{b.code}</span> <span style={{ color: 'var(--muted)' }}>{b.name}</span></td>
                  <td style={{ ...td, textAlign: 'right' }}>{b.partsHeld}</td>
                  <td style={{ ...td, textAlign: 'right' }}>{b.units}</td>
                  <td style={td}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{ flex: 1, height: 8, borderRadius: 999, background: 'var(--gray-bg)', overflow: 'hidden' }} aria-hidden="true">
                        <div style={{ width: `${(b.value / maxValue) * 100}%`, height: '100%', background: 'linear-gradient(90deg, var(--navy2), var(--blue))', borderRadius: 999 }} />
                      </div>
                      <span style={{ fontWeight: 700, minWidth: 84, textAlign: 'right' }}>{inr(b.value)}</span>
                    </div>
                  </td>
                  <td style={{ ...td, textAlign: 'right' }}>{b.unitsBilledThisMonth} <span style={{ color: 'var(--muted)' }}>({b.billsThisMonth} {b.billsThisMonth === 1 ? 'bill' : 'bills'})</span></td>
                </tr>
              ))}
              {data.branches.length === 0 && <tr><td style={{ ...td, padding: 24, color: 'var(--muted)' }} colSpan={5}>No branches yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section style={card}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
          <h2 style={{ ...cardTitle, margin: 0 }}>Branch requests</h2>
          <Link to="/app/spare-parts/requests" style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy2)', textDecoration: 'none' }}>Open the requests →</Link>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 10 }}>
          {([
            ['REQUESTED', 'Waiting to be sent'], ['SENT', 'On the road'], ['RECEIVED', 'Received'],
            ['DECLINED', 'Declined'], ['CANCELLED', 'Cancelled'],
          ] as const).map(([k, label]) => (
            <div key={k} style={{ background: 'var(--bg)', borderRadius: 9, padding: '10px 12px' }}>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--ink)', lineHeight: 1.2 }}>{data.requestCounts[k]}</div>
              <div style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>{label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Full width, two columns when there is room (one on a phone): the list reads left to right instead of leaving
          an empty card beside a long one. A grid, not an inline width, so the responsive rule can still apply. */}
      <section style={{ ...card, minWidth: 0 }}>
        <h2 style={cardTitle}>Latest changes</h2>
        {data.recent.length === 0 ? (
          <Hint>No stock has moved yet.</Hint>
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 380px), 1fr))', columnGap: 28 }}>
            {data.recent.slice(0, LATEST_SHOWN).map((m) => (
              <li key={m.id} style={{ display: 'flex', gap: 10, alignItems: 'baseline', padding: '8px 0', borderTop: '1px solid var(--line)', fontSize: '0.82rem' }}>
                <span style={{ width: 34, textAlign: 'right', flexShrink: 0 }}><SignedQty value={m.qtyChange} /></span>
                <span style={{ minWidth: 0, flex: 1 }}>
                  <span style={{ ...mono, fontWeight: 700 }}>{m.partCode}</span>{' '}
                  <span style={{ color: 'var(--muted)' }}>at {m.branchCode} · {MOVEMENT_LABEL[m.type].toLowerCase()}{m.reference ? ` · ${m.reference}` : ''}</span>
                  <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--faint)' }}>{m.userName} · {fmtDateTime(m.createdAt)}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
        <div style={{ marginTop: 10 }}>
          <Link to="/app/spare-parts/history" style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy2)', textDecoration: 'none' }}>{data.recent.length > LATEST_SHOWN ? 'See all changes in the history →' : 'Open the full history →'}</Link>
        </div>
      </section>
    </div>
  )
}

function Tile(props: { label: string; value: string; note: string; accent: string; to?: string }) {
  const body: CSSProperties = { ...card, padding: '16px 18px', display: 'block', textDecoration: 'none', color: 'inherit', borderTop: `3px solid ${props.accent}` }
  const inner = (
    <>
      <div style={{ fontSize: '0.74rem', fontWeight: 700, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{props.label}</div>
      <div style={{ fontSize: '1.65rem', fontWeight: 700, color: 'var(--ink)', lineHeight: 1.2, marginTop: 4 }}>{props.value}</div>
      <div style={{ fontSize: '0.76rem', color: 'var(--muted)', marginTop: 2 }}>{props.note}</div>
    </>
  )
  return props.to ? <Link to={props.to} style={body}>{inner}</Link> : <div style={body}>{inner}</div>
}
