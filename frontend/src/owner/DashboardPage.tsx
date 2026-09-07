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
import { budgetsApi, monthKeyOf } from '../api/budgets'
import { mastersApi } from '../api/masters'
import { card, ErrorBanner, Skeleton, inr, fmtDate, fmtDateTime, istToday, primaryBtn, ghostBtn, inputStyle } from '../shell/ui'
import GlobalSearch from '../shared/GlobalSearch'
import AskDamsPanel from './AskDamsPanel'
import AiInsightsSection from './AiInsightsSection'
import HelpButton from '../help/HelpButton'
import { Download, AlertTriangle } from 'lucide-react'
import ExportModal from '../shared/ExportModal'

/**
 * Owner dashboard (intial ui prototypes/owner-dashboard.html, dashboard tab). Read-only
 * org aggregates — collections / expenses count APPROVED documents only and never include
 * cash In/Out; cash-in-hand is the live drawer position. Filter by branch and period.
 */

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

const DONUT_COLORS = ['#2E5395', '#1E7F4F', '#B45309', '#6B3FA0', '#0E7490', '#B91C1C', '#5B6470']

const CHECKLIST_KEY = 'dams.ownerChecklist'

/** Default start for a custom range: N days before today (India calendar date). */
function daysAgoIst(n: number): string {
  const t = new Date(`${istToday()}T12:00:00`)
  t.setDate(t.getDate() - n)
  const y = t.getFullYear()
  const m = String(t.getMonth() + 1).padStart(2, '0')
  const d = String(t.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function isNotFound(err: unknown): boolean {
  return (err as { response?: { status?: number } })?.response?.status === 404
}

export default function DashboardPage() {
  const [branchId, setBranchId] = useState<number | ''>('')
  const [period, setPeriod] = useState<DashboardPeriod>('mtd')
  const [customFrom, setCustomFrom] = useState(() => daysAgoIst(29))
  const [customTo, setCustomTo] = useState(() => istToday())
  const [branches, setBranches] = useState<Branch[]>([])
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [outstanding, setOutstanding] = useState<OutstandingItem[]>([])
  const [activity, setActivity] = useState<ActivityItem[]>([])
  const [error, setError] = useState('')
  const [askOpen, setAskOpen] = useState(false)
  const [showExportModal, setShowExportModal] = useState(false)
  const [checklistDismissed, setChecklistDismissed] = useState(
    () => localStorage.getItem(CHECKLIST_KEY) === 'done',
  )
  // Monthly expense budgets, keyed by lower-cased category name (the summary's
  // byCategory rows carry names only, not ids). Null = backend has no budgets
  // endpoint yet (404) — budget bars stay hidden instead of erroring.
  const [budgetByName, setBudgetByName] = useState<Record<string, number> | null>(null)

  useEffect(() => {
    branchesApi.list().then(({ data }) => setBranches(data.filter((b) => b.active))).catch(() => {})
  }, [])

  useEffect(() => {
    let live = true
    setError('')
    const b = branchId === '' ? undefined : branchId
    Promise.all([
      dashboardApi.summary(period, b, period === 'custom' ? customFrom : undefined, period === 'custom' ? customTo : undefined),
      dashboardApi.outstanding(b),
      dashboardApi.activity(b, 100),
    ])
      .then(([s, o, a]) => {
        if (!live) return
        setSummary(s.data)
        setOutstanding(o.data)
        setActivity(a.data)
      })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the dashboard.')) })
    return () => { live = false }
  }, [branchId, period, customFrom, customTo])

  // Budgets for the current month — independent of the period/branch filter.
  useEffect(() => {
    let live = true
    const month = monthKeyOf(istToday())
    if (!month) { setBudgetByName(null); return }
    Promise.all([mastersApi.list('expense-categories'), budgetsApi.list(month)])
      .then(([cats, budgets]) => {
        if (!live) return
        const idToName = new Map(cats.data.map((c) => [c.id, c.name.toLowerCase()]))
        const byName: Record<string, number> = {}
        for (const row of budgets.data) {
          const name = (row.categoryName ?? idToName.get(row.categoryId) ?? '').toLowerCase()
          if (name) byName[name] = row.cap
        }
        setBudgetByName(byName)
      })
      .catch((e) => { if (live && isNotFound(e)) setBudgetByName(null) })
    return () => { live = false }
  }, [])

  const donut = useMemo(() => (summary?.byMode ?? []).filter((m) => m.amount > 0), [summary])
  const donutTotal = donut.reduce((a, m) => a + m.amount, 0)
  const maxCat = Math.max(1, ...(summary?.byCategory ?? []).map((c) => c.amount))
  const scopeLabel = branchId === '' ? 'All branches' : (branches.find((x) => x.id === branchId)?.code ?? 'Branch')

  const cashAlerts = useMemo(() => {
    if (!summary) return []
    const todayStr = istToday()
    const list: { branchCode: string; message: string; severity: 'warning' | 'critical' }[] = []
    for (const b of summary.branchComparison) {
      if (!b.lastClosed) {
        list.push({
          branchCode: b.branchCode,
          message: 'Cash drawer has never been closed',
          severity: 'warning',
        })
      } else if (b.lastClosed < todayStr) {
        const days = Math.max(1, Math.floor((new Date(todayStr).getTime() - new Date(b.lastClosed).getTime()) / (1000 * 60 * 60 * 24)))
        if (days >= 1) {
          list.push({
            branchCode: b.branchCode,
            message: `Cash unclosed for ${days} day${days > 1 ? 's' : ''} (last closed ${fmtDate(b.lastClosed)})`,
            severity: days > 1 ? 'critical' : 'warning',
          })
        }
      }
      if (b.variance != null && Math.abs(Number(b.variance)) > 0.01) {
        list.push({
          branchCode: b.branchCode,
          message: `Discrepancy of ${inr(b.variance)} on last close`,
          severity: 'critical',
        })
      }
    }
    return list
  }, [summary])

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap', marginBottom: 4 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap' }}>
          <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)' }}>Dashboard</h1>
          <HelpButton slug="reading-the-dashboard" />
          <span style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>
            Verified numbers · cash In/Out excluded from collections &amp; expenses
          </span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={() => setShowExportModal(true)}
            style={{
              ...ghostBtn,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              minHeight: 36,
              padding: '6px 12px',
              fontSize: '0.8rem',
              fontWeight: 600,
            }}
          >
            <Download size={15} />
            <span>Export Tally / CSV</span>
          </button>
          <GlobalSearch />
          <button type="button" onClick={() => setAskOpen(true)} style={{ ...primaryBtn(), minHeight: 36, whiteSpace: 'nowrap' }}>
            Ask DAMS
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', margin: '12px 0 18px' }}>
        <Seg
          options={[{ v: '', label: 'All branches' }, ...branches.map((b) => ({ v: b.id, label: b.code }))]}
          value={branchId}
          onChange={(v) => setBranchId(v as number | '')}
        />
        <Seg
          options={[{ v: 'today', label: 'Today' }, { v: 'mtd', label: 'Month to date' }, { v: 'custom', label: 'Custom' }]}
          value={period}
          onChange={(v) => setPeriod(v as DashboardPeriod)}
        />
        {period === 'custom' && (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: '0.8rem', color: 'var(--muted)' }}>
            <input
              type="date"
              aria-label="From date"
              value={customFrom}
              max={customTo}
              onChange={(e) => setCustomFrom(e.target.value)}
              style={{ ...inputStyle, width: 'auto', minHeight: 36, padding: '6px 10px' }}
            />
            <span>→</span>
            <input
              type="date"
              aria-label="To date"
              value={customTo}
              min={customFrom}
              max={istToday()}
              onChange={(e) => setCustomTo(e.target.value)}
              style={{ ...inputStyle, width: 'auto', minHeight: 36, padding: '6px 10px' }}
            />
          </span>
        )}
      </div>

      <ErrorBanner message={error} />

      {summary && (
        <EveningBrief
          collections={summary.kpis.collections}
          expenses={summary.kpis.expenses}
          pendingReview={summary.kpis.pendingReview}
          unclosed={outstanding.length}
          cashAlertCount={cashAlerts.length}
        />
      )}

      {cashAlerts.length > 0 && (
        <div style={{
          background: 'var(--amber-bg, #FEF3C7)',
          border: '1px solid #F59E0B',
          borderRadius: 8,
          padding: '10px 14px',
          marginBottom: 14,
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, fontSize: '0.82rem', color: '#92400E' }}>
            <AlertTriangle size={16} color="#B45309" />
            <span>Cash Drawer Early-Warning Alerts ({cashAlerts.length})</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {cashAlerts.map((a, i) => (
              <span
                key={i}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 5,
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  padding: '3px 8px',
                  borderRadius: 5,
                  background: a.severity === 'critical' ? '#FEE2E2' : '#FFFBEB',
                  color: a.severity === 'critical' ? '#991B1B' : '#92400E',
                  border: `1px solid ${a.severity === 'critical' ? '#FCA5A5' : '#FDE68A'}`,
                }}
              >
                <strong>{a.branchCode}:</strong> {a.message}
              </span>
            ))}
          </div>
        </div>
      )}

      {askOpen && (
        <AskDamsPanel
          branchId={branchId === '' ? undefined : branchId}
          scopeLabel={scopeLabel}
          onClose={() => setAskOpen(false)}
        />
      )}
      {summary == null && !error && (
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 200px), 1fr))', gap: 14, marginBottom: 18 }}>
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} height={92} radius={10} />)}
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-4 mb-4">
            <Skeleton height={264} radius={10} />
            <Skeleton height={264} radius={10} />
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <Skeleton height={220} radius={10} />
            <Skeleton height={220} radius={10} />
          </div>
        </div>
      )}

      {summary && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 200px), 1fr))', gap: 14, marginBottom: 18 }}>
            <Kpi label="Collections" value={inr(summary.kpis.collections)} tone="var(--green)" />
            <Kpi label="Expenses" value={inr(summary.kpis.expenses)} tone="var(--red)" />
            <Kpi label="Net" value={inr(summary.kpis.net)} tone="var(--navy2)" />
            <Kpi label="Cash in hand" value={inr(summary.kpis.cashInHand)} tone="var(--amber)"
              sub={`${summary.kpis.pendingReview} pending review`} />
          </div>

          {!checklistDismissed && (
            <ChecklistCard
              branchCount={branches.length}
              actorCount={new Set(activity.map((a) => a.actor)).size}
              hasNeverClosed={cashAlerts.some((a) => a.message.includes('never been closed'))}
              hasAnyClose={summary.branchComparison.some((b) => b.lastClosed != null)}
              onDismiss={() => {
                localStorage.setItem(CHECKLIST_KEY, 'done')
                setChecklistDismissed(true)
              }}
            />
          )}

          <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-4 mb-4">
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

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
            <div style={{ ...card }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 4 }}>Branch comparison</h3>
              <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginBottom: 8 }}>
                {branchId === '' ? 'Click a row to filter to that branch.' : 'Filtered — click the highlighted row for All branches.'}
              </div>
              <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 500, fontSize: '0.82rem' }}>
                  <thead>
                    <tr>
                      {['Branch', 'Collections', 'Expenses', 'Net', 'Cash', 'Last close'].map((h) => (
                        <th key={h} style={{ textAlign: h === 'Branch' ? 'left' : 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {summary.branchComparison.map((b) => (
                      <tr
                        key={b.branchId}
                        onClick={() => setBranchId((prev) => (prev === b.branchId ? '' : b.branchId))}
                        title={branchId === b.branchId ? 'Show all branches' : `Filter to ${b.branchCode}`}
                        style={{
                          cursor: 'pointer',
                          background: branchId === b.branchId ? 'var(--navy3, #EEF2FA)' : undefined,
                        }}
                      >
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
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 4 }}>Expenses by category</h3>
              {budgetByName != null && (
                <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginBottom: 10 }}>
                  Monthly budget · amber at 80%, red when over
                </div>
              )}
              {summary.byCategory.length === 0 ? (
                <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No approved expenses in this period.</p>
              ) : summary.byCategory.map((c) => (
                <BudgetCategoryRow
                  key={c.name}
                  name={c.name}
                  amount={c.amount}
                  maxCat={maxCat}
                  cap={budgetByName?.[c.name.toLowerCase()]}
                />
              ))}
            </div>
          </div>

          <AiInsightsSection branchId={branchId} period={period} scopeLabel={scopeLabel} />

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
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
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 4 }}>Recent activity</h3>
              {activity.length > 12 && (
                <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginBottom: 6 }}>
                  Latest {Math.min(12, activity.length)} of {activity.length} — full feed powers the staff scorecard below.
                </div>
              )}
              {activity.length === 0 ? (
                <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No recent activity.</p>
              ) : activity.slice(0, 12).map((a, i) => (
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

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4" style={{ marginTop: 16 }}>
            <StaffScorecard activity={activity} />
            <DayRegister trend={summary.trend} />
          </div>
        </>
      )}
      {showExportModal && <ExportModal onClose={() => setShowExportModal(false)} />}
    </div>
  )
}

const bcCell = { padding: '7px 8px', borderTop: '1px solid var(--line)', verticalAlign: 'middle' as const }

/** One-line evening brief — reuses already-loaded KPIs, cash alerts and the
 *  outstanding list. No new endpoint. */
function EveningBrief({ collections, expenses, pendingReview, unclosed, cashAlertCount }: {
  collections: number
  expenses: number
  pendingReview: number
  unclosed: number
  cashAlertCount: number
}) {
  return (
    <div style={{
      background: 'var(--navy3, #EEF2FA)',
      border: '1px solid var(--line)',
      borderRadius: 8,
      padding: '9px 14px',
      marginBottom: 14,
      fontSize: '0.83rem',
      color: 'var(--navy)',
    }}>
      <strong>Today so far:</strong> collections {inr(collections)} · expenses {inr(expenses)} ·{' '}
      {pendingReview} pending review · {unclosed} unclosed
      {cashAlertCount > 0 && <> · {cashAlertCount} cash alert{cashAlertCount === 1 ? '' : 's'}</>}
    </div>
  )
}

/** Onboarding checklist for a new organisation — every step is derived from
 *  already-loaded branches / summary data. Dismissal persists in localStorage. */
function ChecklistCard({ branchCount, actorCount, hasNeverClosed, hasAnyClose, onDismiss }: {
  branchCount: number
  actorCount: number
  hasNeverClosed: boolean
  hasAnyClose: boolean
  onDismiss: () => void
}) {
  const steps = [
    { label: 'Add your first branch', done: branchCount >= 1 },
    // Frontend-only heuristic: the activity feed names actors, so more than one
    // distinct actor means the owner has added users who are doing work.
    { label: 'Add users to your team', done: actorCount > 1 },
    { label: 'Set the cash opening for each branch', done: !hasNeverClosed },
    { label: 'Complete the first cash close', done: hasAnyClose },
  ]
  const doneCount = steps.filter((s) => s.done).length
  return (
    <div style={{ ...card, marginBottom: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
        <h3 style={{ fontSize: '0.94rem', fontWeight: 700, flex: 1 }}>
          Getting started · {doneCount} of {steps.length} done
        </h3>
        <button type="button" onClick={onDismiss} style={{ ...ghostBtn, minHeight: 32 }}>Dismiss</button>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
        {steps.map((s) => (
          <div key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 9, fontSize: '0.83rem' }}>
            <span
              aria-hidden="true"
              style={{
                width: 20, height: 20, borderRadius: '50%', display: 'inline-flex',
                alignItems: 'center', justifyContent: 'center', fontSize: '0.72rem', fontWeight: 800,
                background: s.done ? 'var(--green-bg)' : 'var(--bg)',
                color: s.done ? 'var(--green)' : 'var(--faint)',
                border: `1.5px solid ${s.done ? 'var(--green)' : 'var(--line)'}`,
              }}
            >
              {s.done ? '✓' : '·'}
            </span>
            <span style={{ color: s.done ? 'var(--faint)' : 'var(--ink)', textDecoration: s.done ? 'line-through' : 'none' }}>
              {s.label}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * Staff scorecard grouped purely from the loaded activity feed (no new
 * endpoint). Limitation: the feed records who *performed* each action, not who
 * *received* a query — so the "queries" column counts query actions performed
 * by that actor, and is a rough proxy, not queries received.
 */
function StaffScorecard({ activity }: { activity: ActivityItem[] }) {
  const rows = (() => {
    const byActor = new Map<string, { entries: number; queries: number }>()
    for (const a of activity) {
      const row = byActor.get(a.actor) ?? { entries: 0, queries: 0 }
      row.entries += 1
      if (a.action.toLowerCase().includes('quer')) row.queries += 1
      byActor.set(a.actor, row)
    }
    return [...byActor.entries()]
      .map(([actor, r]) => ({ actor, ...r }))
      .sort((x, y) => y.entries - x.entries)
  })()
  return (
    <div style={{ ...card }}>
      <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 4 }}>Staff scorecard</h3>
      <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginBottom: 10 }}>
        From the recent activity feed · queries ≈ query actions performed, not received
      </div>
      {rows.length === 0 ? (
        <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No activity in this scope yet.</p>
      ) : (
        <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 320, fontSize: '0.82rem' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>Staff</th>
                <th style={{ textAlign: 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>Entries</th>
                <th style={{ textAlign: 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>Queries</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.actor}>
                  <td style={bcCell}><strong>{r.actor}</strong></td>
                  <td style={{ ...bcCell, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{r.entries}</td>
                  <td style={{ ...bcCell, textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: r.queries > 0 ? 'var(--amber)' : undefined }}>
                    {r.queries}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

/** Day register — the summary trend (approved documents only) as dated rows
 *  with totals. No new endpoint. */
function DayRegister({ trend }: { trend: { date: string; collections: number; expenses: number }[] }) {
  const rows = trend.slice(-14)
  const totalC = rows.reduce((a, r) => a + r.collections, 0)
  const totalE = rows.reduce((a, r) => a + r.expenses, 0)
  return (
    <div style={{ ...card }}>
      <h3 style={{ fontSize: '0.94rem', fontWeight: 700, marginBottom: 4 }}>Register (last 14 days, approved only)</h3>
      <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginBottom: 10 }}>
        Cash In/Out excluded — approved collections and expenses only
      </div>
      {rows.length === 0 ? (
        <p style={{ color: 'var(--faint)', fontSize: '0.84rem' }}>No approved movement in this period.</p>
      ) : (
        <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 320, fontSize: '0.82rem' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>Day</th>
                <th style={{ textAlign: 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>In</th>
                <th style={{ textAlign: 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>Out</th>
                <th style={{ textAlign: 'right', padding: '6px 8px', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase', borderBottom: '1.5px solid var(--line)' }}>Net</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.date}>
                  <td style={bcCell}>{fmtDate(r.date)}</td>
                  <td style={{ ...bcCell, textAlign: 'right', color: 'var(--green)', fontVariantNumeric: 'tabular-nums' }}>{inr(r.collections)}</td>
                  <td style={{ ...bcCell, textAlign: 'right', color: 'var(--red)', fontVariantNumeric: 'tabular-nums' }}>{inr(r.expenses)}</td>
                  <td style={{ ...bcCell, textAlign: 'right', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(r.collections - r.expenses)}</td>
                </tr>
              ))}
              <tr>
                <td style={{ ...bcCell, fontWeight: 700, borderTop: '1.5px solid var(--line)' }}>Total</td>
                <td style={{ ...bcCell, textAlign: 'right', fontWeight: 700, borderTop: '1.5px solid var(--line)' }}>{inr(totalC)}</td>
                <td style={{ ...bcCell, textAlign: 'right', fontWeight: 700, borderTop: '1.5px solid var(--line)' }}>{inr(totalE)}</td>
                <td style={{ ...bcCell, textAlign: 'right', fontWeight: 800, borderTop: '1.5px solid var(--line)' }}>{inr(totalC - totalE)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

/** Expense category row with a monthly-budget bar. Amber at 80% of cap, red
 *  when over. Renders the plain row when no budget exists for the category. */
function BudgetCategoryRow({ name, amount, maxCat, cap }: {
  name: string
  amount: number
  maxCat: number
  cap: number | undefined
}) {
  return (
    <div style={{ marginBottom: 12, fontSize: '0.83rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{ width: 90, color: 'var(--muted)' }}>{name}</span>
        <span style={{ flex: 1, background: 'var(--bg)', borderRadius: 5, height: 12, overflow: 'hidden' }}>
          <span style={{ display: 'block', height: '100%', width: `${Math.round((amount / Math.max(1, maxCat)) * 100)}%`, background: 'var(--red)' }} />
        </span>
        <span style={{ width: 74, textAlign: 'right', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(amount)}</span>
      </div>
      {cap != null && cap > 0 && <BudgetBar actual={amount} cap={cap} />}
    </div>
  )
}

function BudgetBar({ actual, cap }: { actual: number; cap: number }) {
  const pct = Math.min(100, Math.round((actual / cap) * 100))
  const tone = actual > cap ? 'var(--red)' : actual >= cap * 0.8 ? 'var(--amber)' : 'var(--green)'
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 4 }}>
      <span style={{ width: 90, fontSize: '0.7rem', color: 'var(--faint)' }}>Budget {inr(cap)}</span>
      <span style={{ flex: 1, background: 'var(--bg)', borderRadius: 4, height: 6, overflow: 'hidden' }}>
        <span style={{ display: 'block', height: '100%', width: `${pct}%`, background: tone }} />
      </span>
      <span style={{ width: 74, textAlign: 'right', fontSize: '0.72rem', fontWeight: 700, color: tone, fontVariantNumeric: 'tabular-nums' }}>
        {Math.round((actual / cap) * 100)}%
      </span>
    </div>
  )
}

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
    <div style={{ display: 'flex', border: '1px solid var(--line)', borderRadius: 9, overflowX: 'auto', WebkitOverflowScrolling: 'touch', maxWidth: '100%' }}>
      {options.map((o) => (
        <button key={String(o.v)} type="button" onClick={() => onChange(o.v)}
          style={{
            border: 'none', padding: '7px 14px', fontSize: '0.8rem', fontWeight: 700, cursor: 'pointer',
            background: value === o.v ? 'var(--navy)' : 'var(--surface)',
            color: value === o.v ? '#fff' : 'var(--muted)',
            minHeight: 36, whiteSpace: 'nowrap',
          }}>
          {o.label}
        </button>
      ))}
    </div>
  )
}
