import { useEffect, useState } from 'react'
import { aiApi,
  type AiBrief, type AnomalyItem, type BenchmarkNarrative, type CashAdvice,
  type ClaimInsight, type CloseChecklistRow, type QueryRoot, type ReceiverDuplicate,
  type RiskScore,
} from '../api/ai'
import type { DashboardPeriod } from '../api/dashboard'
import { useCopy } from '../shared/useCopy'
import { Badge, ErrorBanner, Skeleton, card, cardTitle, ghostBtn, inr } from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

const SEV_TONE: Record<string, 'green' | 'amber' | 'red' | 'blue' | 'gray'> = {
  info: 'blue',
  watch: 'amber',
  urgent: 'red',
}

/**
 * Owner dashboard AI hub (FEAT-10 brief, FEAT-11 watchdog, FEAT-12 claim watch,
 * FEAT-13 query roots, FEAT-14 benchmark, FEAT-15 cash advice, FEAT-16 top risk,
 * FEAT-17 vendor duplicates, FEAT-21 close checklist). Read-only summaries —
 * every action still happens on the underlying screen.
 */
export default function AiInsightsSection({ branchId, period, scopeLabel }: {
  branchId: number | ''
  period: DashboardPeriod
  scopeLabel: string
}) {
  const b = branchId === '' ? undefined : branchId
  // The AI brief only knows today/mtd — a custom dashboard range falls back to mtd.
  const [brief, setBrief] = useState<AiBrief | null>(null)
  const [anomalies, setAnomalies] = useState<AnomalyItem[]>([])
  const [benchmark, setBenchmark] = useState<BenchmarkNarrative | null>(null)
  const [cash, setCash] = useState<CashAdvice[]>([])
  const [claims, setClaims] = useState<ClaimInsight[]>([])
  const [roots, setRoots] = useState<QueryRoot[]>([])
  const [close, setClose] = useState<CloseChecklistRow[]>([])
  const [risk, setRisk] = useState<RiskScore[]>([])
  const [riskQueue, setRiskQueue] = useState<'receipt' | 'expense'>('receipt')
  const [riskLoaded, setRiskLoaded] = useState(false)
  const [dupes, setDupes] = useState<ReceiverDuplicate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [asOf, setAsOf] = useState<Date | null>(null)
  const [expanded, setExpanded] = useState(false)
  const { copiedKey, copyError, copy } = useCopy()

  useEffect(() => {
    let live = true
    setLoading(true)
    Promise.allSettled([
      aiApi.brief(period, b),
      aiApi.anomalies(b),
      aiApi.benchmark(),
      aiApi.cashAdvice(b),
      aiApi.claimInsights(b),
      aiApi.queryRoots(b),
      aiApi.closeChecklist(b),
      aiApi.risk(riskQueue, b),
      aiApi.receiverDuplicates(),
    ]).then((results) => {
      if (!live) return
      const [br, an, be, ca, cl, qr, cc, rk, dp] = results
      if (br.status === 'fulfilled') setBrief(br.value.data)
      if (an.status === 'fulfilled') setAnomalies(an.value.data)
      if (be.status === 'fulfilled') setBenchmark(be.value.data)
      if (ca.status === 'fulfilled') setCash(ca.value.data)
      if (cl.status === 'fulfilled') setClaims(cl.value.data)
      if (qr.status === 'fulfilled') setRoots(qr.value.data)
      if (cc.status === 'fulfilled') setClose(cc.value.data)
      if (rk.status === 'fulfilled') { setRisk(rk.value.data.slice(0, 5)); setRiskLoaded(true) }
      if (dp.status === 'fulfilled') setDupes(dp.value.data.slice(0, 4))
      if (results.every((r) => r.status === 'rejected')) {
        setError(apiError((results[0] as PromiseRejectedResult).reason, 'Could not load AI insights.'))
      }
      setAsOf(new Date())
      setLoading(false)
    })
    return () => { live = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, period, riskQueue])

  if (loading) {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 280px), 1fr))', gap: 14, marginBottom: 18 }}>
        <Skeleton height={120} radius={10} />
        <Skeleton height={120} radius={10} />
        <Skeleton height={120} radius={10} />
      </div>
    )
  }

  // Cards beyond the morning brief + watchdog hide behind an expander when the
  // grid would otherwise dominate the page (more than 3 cards total).
  const extraCount =
    (claims.length > 0 ? 1 : 0) +
    (riskLoaded ? 1 : 0) +
    (cash.length > 0 ? 1 : 0) +
    (benchmark && benchmark.lines.length > 0 ? 1 : 0) +
    (roots.length > 0 ? 1 : 0) +
    (close.length > 0 ? 1 : 0) +
    (dupes.length > 0 ? 1 : 0)
  const gridTotal = (anomalies.length > 0 ? 1 : 0) + extraCount
  const collapsed = !expanded && gridTotal > 3

  return (
    <section aria-label="AI insights" style={{ marginBottom: 18 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
        <h2 style={{ fontSize: '0.95rem', fontWeight: 800, color: 'var(--navy)' }}>✦ AI insights</h2>
        <span style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
          {scopeLabel}{asOf && <> · as of {asOf.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}</>} · suggestions only — you decide
        </span>
      </div>
      <ErrorBanner message={error} />
      <ErrorBanner message={copyError} />
      {brief && (
        <div style={{ ...card, marginBottom: 14, borderLeft: '3px solid var(--navy2)' }}>
          <h3 style={cardTitle}>Morning brief · {brief.scope} · {brief.period === 'today' ? 'Today' : 'Month to date'}</h3>
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: '0.84rem', display: 'flex', flexDirection: 'column', gap: 5 }}>
            {brief.bullets.map((line, i) => <li key={i}>{line}</li>)}
          </ul>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 280px), 1fr))', gap: 14 }}>
        {anomalies.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Watchdog · {anomalies.length} flag{anomalies.length === 1 ? '' : 's'}</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {anomalies.slice(0, 5).map((a, i) => (
                <div key={i} style={{ fontSize: '0.8rem', display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                  <Badge tone={SEV_TONE[a.severity] ?? 'gray'}>{a.severity}</Badge>
                  <span>{a.message}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {gridTotal > 3 && (
        <div style={{ margin: '12px 0' }}>
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            aria-expanded={!collapsed}
            style={{ ...ghostBtn, minHeight: 36, fontWeight: 700 }}
          >
            {collapsed ? `Show all ${extraCount} insights` : 'Show fewer'}
          </button>
        </div>
      )}

      {extraCount > 0 && !collapsed && (
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 280px), 1fr))', gap: 14 }}>

        {claims.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Claim watch · oldest first</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {claims.slice(0, 4).map((c) => (
                <div key={c.documentNo} style={{ fontSize: '0.8rem' }}>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <strong>{c.documentNo}</strong>
                    <Badge tone={c.bucket === '90+' ? 'red' : c.bucket === '61-90' ? 'amber' : 'blue'}>{c.bucket} days</Badge>
                    <span style={{ marginLeft: 'auto', fontWeight: 700 }}>{inr(c.amount)}</span>
                  </div>
                  <div style={{ color: 'var(--faint)', fontSize: '0.74rem', marginTop: 2 }}>
                    {c.customerName} · {c.branchCode} · {c.ageDays} days open
                  </div>
                  <button
                    type="button"
                    onClick={() => void copy(`claim-${c.documentNo}`, c.draftFollowUp)}
                    style={{ background: 'none', border: 'none', color: 'var(--navy2)', cursor: 'pointer', fontSize: '0.74rem', fontWeight: 700, padding: '3px 0 0' }}
                  >
                    {copiedKey === `claim-${c.documentNo}` ? 'Draft copied ✓' : 'Copy OEM follow-up draft'}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {riskLoaded && (
          <div style={card}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
              <h3 style={{ ...cardTitle, marginBottom: 0, flex: 1 }}>Top review risk</h3>
              {(['receipt', 'expense'] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setRiskQueue(t)}
                  style={{
                    border: '1px solid var(--line)', borderRadius: 6, padding: '3px 9px',
                    fontSize: '0.7rem', fontWeight: 700, cursor: 'pointer',
                    background: riskQueue === t ? 'var(--navy)' : 'var(--surface)',
                    color: riskQueue === t ? '#fff' : 'var(--muted)',
                  }}
                >
                  {t === 'receipt' ? 'Receipts' : 'Expenses'}
                </button>
              ))}
            </div>
            {risk.length === 0 ? (
              <p style={{ color: 'var(--faint)', fontSize: '0.82rem' }}>
                No submitted {riskQueue === 'receipt' ? 'receipts' : 'expenses'} right now.
              </p>
            ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {risk.map((r) => (
                <div
                  key={`${r.type}-${r.id}`}
                  title={r.reasons.length > 0 ? r.reasons.join(' · ') : 'No issues found'}
                  style={{ fontSize: '0.8rem', display: 'flex', gap: 8, alignItems: 'center', cursor: 'help' }}
                >
                  <Badge tone={r.score >= 50 ? 'red' : r.score >= 25 ? 'amber' : 'green'}>{r.score}</Badge>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {r.documentNo ?? `#${r.id}`} · {r.reasons.slice(0, 2).join(' · ') || 'looks clean'}
                  </span>
                </div>
              ))}
            </div>
            )}
          </div>
        )}

        {cash.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Cash advice</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {cash.slice(0, 4).map((c, i) => (
                <div key={i} style={{ fontSize: '0.8rem', display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                  <Badge tone={SEV_TONE[c.severity] ?? 'gray'}>{c.branchCode}</Badge>
                  <span>{c.advice}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {benchmark && benchmark.lines.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Branch benchmark</h3>
            <p style={{ fontSize: '0.83rem', fontWeight: 700 }}>{benchmark.headline}</p>
            <p style={{ fontSize: '0.7rem', color: 'var(--faint)', margin: '-2px 0 8px' }}>Across all branches.</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: '0.79rem', color: 'var(--muted)' }}>
              {benchmark.lines.slice(0, 4).map((line, i) => <div key={i}>{line}</div>)}
            </div>
          </div>
        )}

        {roots.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Why entries come back queried</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {roots.slice(0, 3).map((r, i) => (
                <div key={i} style={{ fontSize: '0.8rem' }}>
                  <div><strong>{r.cause}</strong> · {r.count}×</div>
                  <div style={{ color: 'var(--faint)', fontSize: '0.74rem' }}>{r.suggestion}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {close.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Month-end close check</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {close.map((c) => (
                <div key={c.branchCode} style={{ fontSize: '0.8rem', display: 'flex', gap: 8, alignItems: 'center' }}>
                  <Badge tone={c.readyToClose ? 'green' : 'amber'}>{c.branchCode}</Badge>
                  <span style={{ flex: 1 }}>{c.cashNote} · {c.pendingReview} pending · {c.openClaims} open claims</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {dupes.length > 0 && (
          <div style={card}>
            <h3 style={cardTitle}>Possible duplicate vendors</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {dupes.map((d, i) => (
                <div key={i} style={{ fontSize: '0.8rem' }}>
                  <div><strong>{d.firstName}</strong> / {d.secondName}</div>
                  <div style={{ color: 'var(--faint)', fontSize: '0.74rem' }}>{d.reason} — deactivate one, never delete.</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
      )}
    </section>
  )
}
