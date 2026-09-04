import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { searchApi, type SearchHit } from '../api/search'
import { customersApi, type CustomerHistory } from '../api/customers'
import { card, ErrorBanner, Skeleton, SkeletonRows, inr, initials, fmtDateShort } from '../shell/ui'
import AddPaymentModal from './AddPaymentModal'
import ViewReceiptsModal from './ViewReceiptsModal'

/**
 * Cashier home (intial ui prototypes/cashier-home.html): universal search, results, a
 * "recently looked up" strip, and the customer history card with New Receipt / Add Payment
 * / View Receipts wired to Stage 4.
 */

const RECENT_KEY = 'dams.recentCustomers'
const RECENT_MAX = 5

type RecentCustomer = { id: number; name: string; vehicles: string[] }

function loadRecent(): RecentCustomer[] {
  try {
    const raw = sessionStorage.getItem(RECENT_KEY)
    return raw ? (JSON.parse(raw) as RecentCustomer[]) : []
  } catch {
    return []
  }
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export default function CashierHomePage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [recent, setRecent] = useState<RecentCustomer[]>(loadRecent)
  const [flash, setFlash] = useState('')
  const [params, setParams] = useSearchParams()

  // A newly-created receipt navigates back here with ?flash=…
  useEffect(() => {
    const f = params.get('flash')
    if (f) {
      setFlash(f)
      params.delete('flash')
      setParams(params, { replace: true })
      const t = setTimeout(() => setFlash(''), 3500)
      return () => clearTimeout(t)
    }
  }, [params, setParams])

  const rememberCustomer = useCallback((c: RecentCustomer) => {
    setRecent((prev) => {
      const next = [c, ...prev.filter((x) => x.id !== c.id)].slice(0, RECENT_MAX)
      try {
        sessionStorage.setItem(RECENT_KEY, JSON.stringify(next))
      } catch {
        /* sessionStorage unavailable — the strip just won't persist */
      }
      return next
    })
  }, [])

  return (
    <>
      {flash && (
        <div className="dams-anim-toast" style={{
          position: 'fixed', bottom: 20, right: 20, background: 'var(--ink)', color: '#fff',
          borderRadius: 9, padding: '10px 16px', fontSize: '0.82rem', boxShadow: 'var(--shadow-lift)', zIndex: 60,
        }}>
          {flash}
        </div>
      )}
      {selectedId != null ? (
        <CustomerHistoryView
          customerId={selectedId}
          onBack={() => setSelectedId(null)}
          onLoaded={rememberCustomer}
        />
      ) : (
        <HomeSearch recent={recent} onOpenCustomer={setSelectedId} />
      )}
    </>
  )
}

// ───────────────────────────── Home / search ─────────────────────────────

function HomeSearch(props: {
  recent: RecentCustomer[]
  onOpenCustomer: (id: number) => void
}) {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<SearchHit[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const seq = useRef(0)

  useEffect(() => {
    const query = q.trim()
    if (query.length < 2) {
      setHits(null)
      setError('')
      setLoading(false)
      return
    }
    const mine = ++seq.current
    setLoading(true)
    const t = setTimeout(async () => {
      try {
        const { data } = await searchApi.query(query)
        if (mine === seq.current) {
          setHits(data.hits)
          setError('')
        }
      } catch (e) {
        if (mine === seq.current) setError(apiError(e, 'Search failed. Try again.'))
      } finally {
        if (mine === seq.current) setLoading(false)
      }
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '32px 0 24px' }}>
      <div style={{ textAlign: 'center', marginBottom: 6 }}>
        <h1 style={{ fontSize: '1.5rem', color: 'var(--navy)', marginBottom: 6 }}>Find a customer</h1>
        <div style={{ fontSize: '0.9rem', color: 'var(--muted)' }}>
          Search by name, vehicle number, job card, or invoice — anything works
        </div>
      </div>

      <div style={{ position: 'relative', maxWidth: 640, margin: '22px auto 0' }}>
        <span style={{
          position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
          color: 'var(--faint)', fontSize: '1.1rem',
        }}>⌕</span>
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="e.g. Sumati Sahoo, OD05CA4177, 7731122600388…"
          autoComplete="off"
          style={{
            width: '100%', border: '2px solid var(--line)', borderRadius: 12,
            padding: '15px 18px 15px 46px', fontSize: '1rem', background: 'var(--surface)',
            boxShadow: 'var(--shadow)', outline: 'none',
          }}
        />
      </div>
      <div style={{ textAlign: 'center', fontSize: '0.76rem', color: 'var(--faint)', marginTop: 8 }}>
        {loading ? 'Searching…' : q.trim().length < 2 ? 'Start typing at least 2 characters' : ''}
      </div>

      <ErrorBanner message={error} />

      {hits != null && (
        <div style={{
          maxWidth: 640, margin: '14px auto 0', background: 'var(--surface)',
          border: '1px solid var(--line)', borderRadius: 'var(--radius)',
          boxShadow: 'var(--shadow)', overflow: 'hidden',
        }}>
          {hits.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--faint)', fontSize: '0.85rem' }}>
              No customer, vehicle, or job card matches “{q.trim()}”
            </div>
          ) : (
            hits.map((h) => (
              <ResultRow key={h.customerId} hit={h} onClick={() => props.onOpenCustomer(h.customerId)} />
            ))
          )}
        </div>
      )}

      {hits == null && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 14, maxWidth: 640, margin: '30px auto 0' }}>
            <QuickCard tone="green" mark="＋" title="New Receipt" sub="Start a fresh entry" onClick={() => navigate('/app/new-receipt')} />
            <QuickCard tone="red" mark="−" title="New Expense" sub="Petty cash, fuel, taxi…" onClick={() => navigate('/app/new-expense')} />
            <QuickCard tone="navy" mark="₹" title="Cash" sub="Drawer & day close" onClick={() => navigate('/app/cash')} />
            <QuickCard tone="navy" mark="☰" title="My Entries" sub="Today & recent" onClick={() => navigate('/app/my-entries')} />
          </div>
          <RecentStrip recent={props.recent} onOpenCustomer={props.onOpenCustomer} />
        </>
      )}
    </div>
  )
}

function QuickCard(props: {
  tone: 'green' | 'red' | 'navy'
  mark: string
  title: string
  sub: string
  onClick?: () => void
  disabled?: boolean
}) {
  const toneBg = props.tone === 'green' ? 'var(--green-bg)' : props.tone === 'red' ? 'var(--red-bg)' : 'var(--navy3)'
  const toneFg = props.tone === 'green' ? 'var(--green)' : props.tone === 'red' ? 'var(--red)' : 'var(--navy)'
  return (
    <button
      type="button"
      onClick={props.onClick}
      disabled={props.disabled}
      style={{
        background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 'var(--radius)',
        boxShadow: 'var(--shadow)', padding: 16, textAlign: 'left', display: 'flex', alignItems: 'center', gap: 12,
        cursor: props.disabled ? 'not-allowed' : 'pointer', opacity: props.disabled ? 0.55 : 1,
      }}
    >
      <span style={{
        width: 38, height: 38, borderRadius: 10, background: toneBg, color: toneFg,
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '1.1rem', flexShrink: 0,
      }}>
        {props.mark}
      </span>
      <span>
        <span style={{ display: 'block', fontSize: '0.88rem', fontWeight: 700 }}>{props.title}</span>
        <span style={{ display: 'block', fontSize: '0.74rem', color: 'var(--muted)' }}>{props.sub}</span>
      </span>
    </button>
  )
}

function ResultRow({ hit, onClick }: { hit: SearchHit; onClick: () => void }) {
  const out = hit.totalOutstanding
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        display: 'flex', alignItems: 'center', gap: 12, padding: '13px 18px',
        borderBottom: '1px solid var(--line)', textAlign: 'left', width: '100%',
        background: 'transparent', border: 'none', borderBottomWidth: 1, borderBottomStyle: 'solid',
        borderBottomColor: 'var(--line)', cursor: 'pointer',
      }}
    >
      <span style={{
        width: 36, height: 36, borderRadius: 9, background: 'var(--navy3)', color: 'var(--navy)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700,
        fontSize: '0.8rem', flexShrink: 0,
      }}>
        {initials(hit.customerName)}
      </span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ fontSize: '0.92rem', fontWeight: 700 }}>
          {hit.customerName}
          <span style={{
            fontSize: '0.68rem', background: 'var(--amber-bg)', color: 'var(--amber)',
            borderRadius: 5, padding: '1px 6px', fontWeight: 700, marginLeft: 6,
          }}>
            {hit.matchField} match
          </span>
        </span>
        <span style={{ display: 'block', fontSize: '0.78rem', color: 'var(--muted)', marginTop: 1 }}>
          {hit.vehicles.join(', ') || 'No vehicle on file'} · {hit.jobCardCount} job card
          {hit.jobCardCount === 1 ? '' : 's'}
        </span>
      </span>
      <span style={{ textAlign: 'right', fontSize: '0.78rem', flexShrink: 0 }}>
        <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: out > 0 ? 'var(--amber)' : 'var(--green)' }}>
          {out > 0 ? inr(out) : 'Clear'}
        </span>
        <span style={{ display: 'block', color: 'var(--faint)', fontSize: '0.68rem' }}>
          {out > 0 ? 'outstanding' : 'no balance'}
        </span>
      </span>
    </button>
  )
}

function RecentStrip(props: { recent: RecentCustomer[]; onOpenCustomer: (id: number) => void }) {
  if (props.recent.length === 0) return null
  return (
    <div style={{ maxWidth: 640, margin: '36px auto 0' }}>
      <h4 style={{
        fontSize: '0.76rem', textTransform: 'uppercase', letterSpacing: '0.05em',
        color: 'var(--faint)', marginBottom: 10,
      }}>
        Recently looked up
      </h4>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 10 }}>
        {props.recent.map((c) => (
          <button
            key={c.id}
            type="button"
            onClick={() => props.onOpenCustomer(c.id)}
            style={{
              display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px',
              border: '1px solid var(--line)', borderRadius: 10, background: 'var(--surface)',
              textAlign: 'left', cursor: 'pointer',
            }}
          >
            <span style={{
              width: 34, height: 34, borderRadius: 9, background: 'var(--navy3)', color: 'var(--navy)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700,
              fontSize: '0.76rem', flexShrink: 0,
            }}>
              {initials(c.name)}
            </span>
            <span style={{ minWidth: 0 }}>
              <span style={{ display: 'block', fontSize: '0.88rem', fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {c.name}
              </span>
              <span style={{ display: 'block', fontSize: '0.76rem', color: 'var(--muted)' }}>
                {c.vehicles.join(', ') || '—'}
              </span>
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}

// ───────────────────────────── History card ─────────────────────────────

type PaymentTarget = {
  receiptId: number
  jobReference: string
  documentNo: string | null
  balanceDue: number
}
type ReceiptsTarget = {
  receiptId: number
  subtitle: string
  frozen: boolean
}

function CustomerHistoryView(props: {
  customerId: number
  onBack: () => void
  onLoaded: (c: RecentCustomer) => void
}) {
  const navigate = useNavigate()
  const [data, setData] = useState<CustomerHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [payTarget, setPayTarget] = useState<PaymentTarget | null>(null)
  const [receiptsTarget, setReceiptsTarget] = useState<ReceiptsTarget | null>(null)
  const [reloadTick, setReloadTick] = useState(0)
  const { onLoaded } = props

  useEffect(() => {
    let live = true
    setLoading(true)
    customersApi
      .history(props.customerId)
      .then(({ data }) => {
        if (!live) return
        setData(data)
        onLoaded({
          id: data.customerId,
          name: data.customerName,
          vehicles: data.vehicles.map((v) => v.vehicleNo),
        })
      })
      .catch((e) => live && setError(apiError(e, 'Could not load this customer.')))
      .finally(() => live && setLoading(false))
    return () => {
      live = false
    }
  }, [props.customerId, onLoaded, reloadTick])

  return (
    <div style={{ maxWidth: 920, margin: '0 auto' }}>
      <button
        type="button"
        onClick={props.onBack}
        style={{ background: 'none', border: 'none', color: 'var(--navy)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', padding: '4px 0', marginBottom: 12 }}
      >
        ← Back to search
      </button>

      <ErrorBanner message={error} />
      {loading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Skeleton height={84} radius={13} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 12 }}>
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} height={64} radius={10} />)}
          </div>
          <SkeletonRows rows={3} height={64} />
        </div>
      )}

      {data && (
        <>
          <div style={{ ...card, display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16, flexWrap: 'wrap' }}>
            <div style={{
              width: 52, height: 52, borderRadius: 13, background: 'var(--navy3)', color: 'var(--navy)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '1.1rem', flexShrink: 0,
            }}>
              {initials(data.customerName)}
            </div>
            <div>
              <div style={{ fontSize: '1.15rem', fontWeight: 700 }}>{data.customerName}</div>
              <div style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 2 }}>
                {data.vehicles.length > 0
                  ? data.vehicles.map((v) => (
                      <span key={v.id} style={{
                        display: 'inline-block', background: 'var(--bg)', border: '1px solid var(--line)',
                        borderRadius: 6, padding: '1px 8px', fontFamily: 'Consolas, monospace',
                        fontSize: '0.74rem', marginRight: 5,
                      }}>
                        {v.vehicleNo}
                      </span>
                    ))
                  : 'No vehicle on file'}
                {data.phone && <span style={{ marginLeft: 4 }}>· {data.phone}</span>}
              </div>
            </div>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={() => navigate(`/app/new-receipt?customerId=${data.customerId}`)}
                style={{ border: '1.5px solid #BBDCC9', background: 'var(--surface)', borderRadius: 8, padding: '9px 14px', fontSize: '0.85rem', fontWeight: 700, color: 'var(--green)', cursor: 'pointer', whiteSpace: 'nowrap', minHeight: 38 }}
              >
                ＋ New Receipt
              </button>
              <button
                type="button"
                onClick={() => navigate(`/app/new-expense?customerId=${data.customerId}`)}
                style={{ border: '1.5px solid #E4C3C3', background: 'var(--surface)', borderRadius: 8, padding: '9px 14px', fontSize: '0.85rem', fontWeight: 700, color: 'var(--red)', cursor: 'pointer', whiteSpace: 'nowrap', minHeight: 38 }}
              >
                ＋ New Expense
              </button>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 12, marginBottom: 16 }}>
            <Stat label="Job Cards" value={String(data.jobCardCount)} />
            <Stat label="Total Invoiced" value={inr(data.totalInvoiced)} />
            <Stat label="Total Received" value={inr(data.totalReceived)} tone="green" />
            <Stat label="Outstanding" value={inr(data.totalOutstanding)} tone={data.totalOutstanding > 0 ? 'amber' : 'muted'} />
          </div>

          <section style={{ ...card, padding: 0, marginBottom: 16 }}>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line)' }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Job Cards</h3>
              <span style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>
                “Add Payment” appends a line to the existing receipt — never a second one
              </span>
            </div>
            <div style={{ padding: '2px 18px 10px' }}>
              {data.jobCards.length === 0 && (
                <div style={{ padding: '16px 0', color: 'var(--faint)', fontSize: '0.85rem' }}>
                  No job cards for this customer yet.
                </div>
              )}
              {data.jobCards.map((j) => (
                <div key={j.id} style={{
                  display: 'flex', alignItems: 'center', gap: 14, padding: '13px 0',
                  borderBottom: '1px solid var(--line)', flexWrap: 'wrap',
                }}>
                  <div style={{ minWidth: 180 }}>
                    <div style={{ fontFamily: 'Consolas, monospace', fontSize: '0.78rem', color: 'var(--navy2)', fontWeight: 700 }}>
                      {j.reference}
                    </div>
                    <div style={{ fontSize: '0.85rem', fontWeight: 600, marginTop: 2 }}>
                      {j.categoryName}
                      {j.isClaim && (
                        <span style={{
                          fontSize: '0.66rem', fontWeight: 700, color: 'var(--amber)', background: 'var(--amber-bg)',
                          borderRadius: 999, padding: '1px 7px', marginLeft: 6,
                        }}>
                          Claim
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
                      {j.businessStatusName}
                      {j.dbmId && ` · JC ${j.dbmId}`}
                      {j.invoiceNo && ` · Inv ${j.invoiceNo}`}
                    </div>
                  </div>
                  <div style={{ marginLeft: 'auto', display: 'flex', gap: 20 }}>
                    <Amt label="Invoice" value={j.invoiceAmount != null ? inr(j.invoiceAmount) : '—'} />
                    <Amt label="Received" value={inr(j.received)} tone="green" />
                    <Amt label="Balance" value={j.invoiceAmount != null ? inr(j.balance) : '—'} tone={j.balance > 0 ? 'amber' : 'muted'} />
                  </div>
                  {j.workflowStatus && (
                    <span style={{
                      fontSize: '0.68rem', fontWeight: 700, padding: '2px 9px', borderRadius: 999,
                      background: 'var(--blue-bg)', color: 'var(--blue)',
                    }}>
                      {j.workflowStatus}
                    </span>
                  )}
                  {j.receiveDocumentId != null && (
                    <button
                      type="button"
                      onClick={() => setReceiptsTarget({
                        receiptId: j.receiveDocumentId!,
                        subtitle: `${j.reference} · whole receipt`,
                        frozen: j.receiveDocumentSettled || j.workflowStatus === 'REJECTED',
                      })}
                      style={{ border: 'none', background: 'none', color: 'var(--navy2)', fontSize: '0.72rem', fontWeight: 700, cursor: 'pointer' }}
                    >
                      View Receipts
                    </button>
                  )}
                  {j.canRecordPayment && j.receiveDocumentId != null ? (
                    <button
                      type="button"
                      onClick={() => setPayTarget({
                        receiptId: j.receiveDocumentId!,
                        jobReference: j.reference,
                        documentNo: null,
                        balanceDue: j.balance,
                      })}
                      style={{ border: '1.5px solid #BBDCC9', background: 'var(--surface)', borderRadius: 8, padding: '6px 11px', fontSize: '0.78rem', fontWeight: 700, color: 'var(--green)', cursor: 'pointer', whiteSpace: 'nowrap' }}
                    >
                      Add Payment
                    </button>
                  ) : j.balance > 0 && j.receiveDocumentId != null ? (
                    <SoonButton label="Add Payment" small title="Only the home-branch cashier can record this payment" />
                  ) : null}
                </div>
              ))}
            </div>
          </section>

          <section style={{ ...card, padding: 0 }}>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line)' }}>
              <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Payment Timeline</h3>
            </div>
            <div style={{ padding: '14px 18px' }}>
              {data.timeline.length === 0 ? (
                <div style={{ color: 'var(--faint)', fontSize: '0.83rem' }}>
                  No payments recorded against this customer yet.
                </div>
              ) : (
                data.timeline.map((t) => (
                  <div key={t.lineId} style={{
                    display: 'flex', alignItems: 'center', gap: 12, padding: '9px 0',
                    borderBottom: '1px dashed var(--line)', fontSize: '0.83rem',
                  }}>
                    <span style={{ width: 96, color: 'var(--faint)', fontSize: '0.76rem', flexShrink: 0 }}>
                      {fmtDateShort(t.date)}
                    </span>
                    <span style={{ flex: 1, color: 'var(--muted)' }}>
                      {t.description}{t.mode ? ` · ${t.mode}` : ''} · {t.lineId}
                    </span>
                    <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: 'var(--green)' }}>
                      {inr(t.amount)}
                    </span>
                  </div>
                ))
              )}
            </div>
          </section>
        </>
      )}

      {payTarget && (
        <AddPaymentModal
          receiptId={payTarget.receiptId}
          customerName={data?.customerName ?? ''}
          jobReference={payTarget.jobReference}
          documentNo={payTarget.documentNo}
          balanceDue={payTarget.balanceDue}
          onClose={() => setPayTarget(null)}
          onDone={() => {
            setPayTarget(null)
            setReloadTick((n) => n + 1)
          }}
        />
      )}
      {receiptsTarget && (
        <ViewReceiptsModal
          receiptId={receiptsTarget.receiptId}
          subtitle={receiptsTarget.subtitle}
          frozen={receiptsTarget.frozen}
          onClose={() => setReceiptsTarget(null)}
          onChanged={() => setReloadTick((n) => n + 1)}
        />
      )}
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'green' | 'amber' | 'muted' }) {
  const color = tone === 'green' ? 'var(--green)' : tone === 'amber' ? 'var(--amber)' : tone === 'muted' ? 'var(--muted)' : 'var(--ink)'
  return (
    <div style={card}>
      <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)' }}>{label}</div>
      <div style={{ fontSize: '1.2rem', fontWeight: 700, marginTop: 3, fontVariantNumeric: 'tabular-nums', color }}>{value}</div>
    </div>
  )
}

function Amt({ label, value, tone }: { label: string; value: string; tone?: 'green' | 'amber' | 'muted' }) {
  const color = tone === 'green' ? 'var(--green)' : tone === 'amber' ? 'var(--amber)' : tone === 'muted' ? 'var(--muted)' : 'var(--ink)'
  return (
    <div style={{ textAlign: 'right' }}>
      <div style={{ fontSize: '0.68rem', color: 'var(--faint)', textTransform: 'uppercase' }}>{label}</div>
      <div style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', fontSize: '0.86rem', color }}>{value}</div>
    </div>
  )
}

/** A control for an action that isn't available (yet, or here) — visible but disabled. */
function SoonButton({ label, small, title }: { label: string; small?: boolean; title?: string }) {
  return (
    <button
      type="button"
      disabled
      title={title ?? 'Arrives in Stage 5'}
      style={{
        border: '1.5px solid var(--line)', background: 'var(--surface)', borderRadius: 8,
        padding: small ? '6px 11px' : '9px 14px', fontSize: small ? '0.78rem' : '0.85rem',
        fontWeight: 700, color: 'var(--faint)', cursor: 'not-allowed', whiteSpace: 'nowrap',
      }}
    >
      {label}
    </button>
  )
}
