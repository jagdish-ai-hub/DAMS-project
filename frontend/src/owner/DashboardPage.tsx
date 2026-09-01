import { useEffect, useMemo, useState } from 'react'
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid,
  PieChart, Pie, Cell,
} from 'recharts'
import { branchesApi, type Branch } from '../api/branches'
import {
  dashboardApi,
  type DashboardPeriod, type DashboardSummary, type OutstandingItem, type ActivityItem,
} from '../api/dashboard'
import { card, ErrorBanner, Skeleton, inr, fmtDate, fmtDateTime } from '../shell/ui'

/**
 * Owner dashboard (intial ui prototypes/owner-dashboard.html, dashboard tab). Read-only
 * org aggregates — collections / expenses count APPROVED documents only and never include
 * cash In/Out; cash-in-hand is the live drawer position. Filter by branch and period.
 */

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

const DONUT_COLORS = ['#2E5395', '#1E7F4F', '#B45309', '#6B3FA0', '#0E7490', '#B91C1C', '#5B6470']

export default function DashboardPage() {
  const [branchId, setBranchId] = useState<number | ''>('')
  const [period, setPeriod] = useState<DashboardPeriod>('mtd')
  const [branches, setBranches] = useState<Branch[]>([])
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [outstanding, setOutstanding] = useState<OutstandingItem[]>([])
  const [activity, setActivity] = useState<ActivityItem[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    branchesApi.list().then(({ data }) => setBranches(data.filter((b) => b.active))).catch(() => {})
  }, [])

  useEffect(() => {
    let live = true
    setError('')
    const b = branchId === '' ? undefined : branchId
    Promise.all([
      dashboardApi.summary(period, b),
      dashboardApi.outstanding(b),
      dashboardApi.activity(b, 20),
    ])
      .then(([s, o, a]) => {
        if (!live) return
        setSummary(s.data)
        setOutstanding(o.data)
        setActivity(a.data)
      })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the dashboard.')) })
    return () => { live = false }
  }, [branchId, period])

  const donut = useMemo(() => (summary?.byMode ?? []).filter((m) => m.amount > 0), [summary])
  const donutTotal = donut.reduce((a, m) => a + m.amount, 0)
  const maxCat = Math.max(1, ...(summary?.byCategory ?? []).map((c) => c.amount))

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap', marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)' }}>Dashboard</h1>
        <span style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>
          Verified numbers · cash In/Out excluded from collections &amp; expenses
        </span>
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', margin: '12px 0 18px' }}>
        <Seg
          options={[{ v: '', label: 'All branches' }, ...branches.map((b) => ({ v: b.id, label: b.code }))]}
          value={branchId}
          onChange={(v) => setBranchId(v as number | '')}
        />
        <Seg
          options={[{ v: 'today', label: 'Today' }, { v: 'mtd', label: 'Month to date' }]}
          value={period}
          onChange={(v) => setPeriod(v as DashboardPeriod)}
        />
      </div>

      <ErrorBanner message={error} />
      {summary == null && !error && (
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 18 }}>
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} height={92} radius={10} />)}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 16, marginBottom: 16 }}>
            <Skeleton height={264} radius={10} />
            <Skeleton height={264} radius={10} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Skeleton height={220} radius={10} />
            <Skeleton height={220} radius={10} />
          </div>
        </div>
      )}

      {summary && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 18 }}>
            <Kpi label="Collections" value={inr(summary.kpis.collections)} tone="var(--green)" />
            <Kpi label="Expenses" value={inr(summary.kpis.expenses)} tone="var(--red)" />
            <Kpi label="Net" value={inr(summary.kpis.net)} tone="var(--navy2)" />
            <Kpi label="Cash in hand" value={inr(summary.kpis.cashInHand)} tone="var(--amber)"
              sub={`${summary.kpis.pendingReview} pending review`} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 16, marginBottom: 16 }}>
            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 10 }}>Collections vs Expenses · last 14 days</h3>
              <ResponsiveContainer width="100%" height={220}>
                <AreaChart data={summary.trend.map((t) => ({ ...t, day: t.date.slice(8) }))}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
                  <XAxis dataKey="day" tick={{ fontSize: 11, fill: 'var(--faint)' }} />
                  <YAxis tick={{ fontSize: 11, fill: 'var(--faint)' }} width={54}
                    tickFormatter={(n) => (n >= 1000 ? `${Math.round(n / 1000)}k` : String(n))} />
                  <Tooltip formatter={(v) => inr(Number(v))} labelFormatter={(d) => `Day ${d}`} />
                  <Area type="monotone" dataKey="collections" stroke="var(--green)" fill="var(--green-bg)" strokeWidth={2} />
                  <Area type="monotone" dataKey="expenses" stroke="var(--red)" fill="var(--red-bg)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 10 }}>Collections by mode</h3>
              {donut.length === 0 ? (
                <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No approved collections in this period.</p>
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
                  <ResponsiveContainer width={150} height={150}>
                    <PieChart>
                      <Pie data={donut} dataKey="amount" nameKey="name" innerRadius={40} outerRadius={68} paddingAngle={2}>
                        {donut.map((_, i) => <Cell key={i} fill={DONUT_COLORS[i % DONUT_COLORS.length]} />)}
                      </Pie>
                      <Tooltip formatter={(v) => inr(Number(v))} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div style={{ flex: 1, minWidth: 140, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {donut.map((m, i) => (
                      <div key={m.name} style={{ display: 'flex', alignItems: 'center', gap: 9, fontSize: '0.83rem' }}>
                        <span style={{ width: 10, height: 10, borderRadius: 3, background: DONUT_COLORS[i % DONUT_COLORS.length] }} />
                        <span style={{ flex: 1, color: 'var(--muted)' }}>{m.name}</span>
                        <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(m.amount)}</span>
                        <span style={{ width: 38, textAlign: 'right', color: 'var(--faint)', fontSize: '0.76rem' }}>
                          {Math.round((m.amount / donutTotal) * 100)}%
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 12 }}>Branch comparison</h3>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 460, fontSize: '0.82rem' }}>
                  <thead>
                    <tr>
                      {['Branch', 'Collections', 'Expenses', 'Net', 'Cash', 'Last close'].map((h) => (
                        <th key={h} style={{ textAlign: h === 'Branch' ? 'left' : 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {summary.branchComparison.map((b) => (
                      <tr key={b.branchId}>
                        <td style={bcCell}><strong>{b.branchCode}</strong></td>
                        <td style={{ ...bcCell, textAlign: 'right', color: 'var(--green)' }}>{inr(b.collections)}</td>
                        <td style={{ ...bcCell, textAlign: 'right', color: 'var(--red)' }}>{inr(b.expenses)}</td>
                        <td style={{ ...bcCell, textAlign: 'right', fontWeight: 700 }}>{inr(b.net)}</td>
                        <td style={{ ...bcCell, textAlign: 'right' }}>{inr(b.cashInHand)}</td>
                        <td style={{ ...bcCell, textAlign: 'right', color: 'var(--faint)', fontSize: '0.76rem' }}>
                          {b.lastClosed ? fmtDate(b.lastClosed) : 'never'}
                          {b.variance != null && b.variance !== 0 && (
                            <span style={{ color: 'var(--amber)' }}> · {inr(b.variance)}</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 12 }}>Expenses by category</h3>
              {summary.byCategory.length === 0 ? (
                <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No approved expenses in this period.</p>
              ) : summary.byCategory.map((c) => (
                <div key={c.name} style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10, fontSize: '0.83rem' }}>
                  <span style={{ width: 90, color: 'var(--muted)' }}>{c.name}</span>
                  <span style={{ flex: 1, background: 'var(--bg)', borderRadius: 5, height: 12, overflow: 'hidden' }}>
                    <span style={{ display: 'block', height: '100%', width: `${Math.round((c.amount / maxCat) * 100)}%`, background: 'var(--red)' }} />
                  </span>
                  <span style={{ width: 74, textAlign: 'right', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(c.amount)}</span>
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 6 }}>Outstanding</h3>
              <div style={{ fontSize: '0.74rem', color: 'var(--faint)', marginBottom: 10 }}>money still owed or awaiting settlement</div>
              {outstanding.length === 0 ? (
                <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>Nothing outstanding.</p>
              ) : outstanding.map((o, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderTop: i === 0 ? 'none' : '1px solid var(--line)', fontSize: '0.83rem' }}>
                  <span style={{
                    fontSize: '0.6rem', fontWeight: 800, textTransform: 'uppercase', padding: '2px 6px', borderRadius: 4,
                    background: o.kind === 'claim' ? 'var(--purple-bg, #EFE7FB)' : o.kind === 'b2b' ? 'var(--blue-bg)' : 'var(--amber-bg)',
                    color: o.kind === 'claim' ? 'var(--purple, #6B3FA0)' : o.kind === 'b2b' ? 'var(--blue)' : 'var(--amber)',
                  }}>
                    {o.kind === 'job-card' ? 'JOB' : o.kind === 'b2b' ? 'B2B' : 'CLAIM'}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{o.name}</div>
                    <div style={{ fontSize: '0.72rem', color: 'var(--faint)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{o.sub}</div>
                  </div>
                  <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: 'var(--amber)' }}>{inr(o.amount)}</span>
                </div>
              ))}
            </div>

            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 10 }}>Recent activity</h3>
              {activity.length === 0 ? (
                <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No recent activity.</p>
              ) : activity.map((a, i) => (
                <div key={i} style={{ padding: '8px 0', borderTop: i === 0 ? 'none' : '1px solid var(--line)', fontSize: '0.82rem' }}>
                  <div>
                    <strong>{a.actor}</strong> {a.action.toLowerCase()}{' '}
                    <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.76rem', color: 'var(--navy2)' }}>{a.documentNo ?? a.description}</span>
                    {a.branchCode && <span style={{ color: 'var(--faint)' }}> · {a.branchCode}</span>}
                  </div>
                  <div style={{ fontSize: '0.68rem', color: 'var(--faint)' }}>{fmtDateTime(a.at)}</div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}

const bcCell = { padding: '7px 8px', borderTop: '1px solid var(--line)', verticalAlign: 'middle' as const }

function Kpi({ label, value, tone, sub }: { label: string; value: string; tone: string; sub?: string }) {
  return (
    <div style={{ ...card, borderTop: `3px solid ${tone}` }}>
      <div style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--muted)', fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: 5, fontVariantNumeric: 'tabular-nums' }}>{value}</div>
      <div style={{ fontSize: '0.74rem', color: 'var(--faint)', marginTop: 2 }}>{sub ?? ' '}</div>
    </div>
  )
}

function Seg<T extends string | number>({ options, value, onChange }: {
  options: { v: T; label: string }[]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div style={{ display: 'flex', border: '1px solid var(--line)', borderRadius: 9, overflow: 'hidden' }}>
      {options.map((o) => (
        <button key={String(o.v)} type="button" onClick={() => onChange(o.v)}
          style={{
            border: 'none', padding: '7px 14px', fontSize: '0.8rem', fontWeight: 700, cursor: 'pointer',
            background: value === o.v ? 'var(--navy)' : 'var(--surface)',
            color: value === o.v ? '#fff' : 'var(--muted)',
          }}>
          {o.label}
        </button>
      ))}
    </div>
  )
}
