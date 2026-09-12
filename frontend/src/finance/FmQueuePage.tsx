import { useCallback, useEffect, useMemo, useState } from 'react'
import { receiptsApi, type ReceiveDocument } from '../api/receipts'
import { expensesApi } from '../api/expenses'
import { cashApi, type CashDocument } from '../api/cash'
import { reviewApi, type FmQueue, type ReviewQueueItem, type ReviewType } from '../api/review'
import { jobCardsApi } from '../api/jobCards'
import { card, ErrorBanner, ghostBtn, primaryBtn, inputStyle, Modal, Skeleton, SkeletonRows, inr } from '../shell/ui'
import { RecordCard, CashRecordCard, QueryRejectBox, Tag, apiError, type AnyDoc } from '../review/reviewShared'
import GlobalSearch from '../shared/GlobalSearch'
import { useAuth } from '../auth/useAuth'
import AiClaimBanner from './AiClaimBanner'
import { useRiskMap, RiskDot } from '../review/AiRiskBadge'
import SortModeControl, { groupItems, type SortMode } from '../review/SortModeControl'

/**
 * Finance Manager queue (intial ui prototypes/review-close.html, FM view). Approve / query /
 * reject verified entries; close warranty / AMC / CG claims with an optional final override.
 * No Accountant/FM toggle — a logged-in FM sees only this.
 */

const EMPTY: FmQueue = { awaitingApproval: [], openClaims: [], recentlyClosed: [] }

type DetailDoc = AnyDoc | CashDocument

export type AgingBucket = 'all' | '0-30' | '31-60' | '61-90' | '90+'

export function claimAgeDays(submittedAt: string | null): number {
  if (!submittedAt) return 0
  const ms = Date.now() - new Date(submittedAt).getTime()
  return Math.max(0, Math.floor(ms / (1000 * 60 * 60 * 24)))
}

export function getAgingBucket(days: number): '0-30' | '31-60' | '61-90' | '90+' {
  if (days <= 30) return '0-30'
  if (days <= 60) return '31-60'
  if (days <= 90) return '61-90'
  return '90+'
}

export function AgingBadge({ days }: { days: number }) {
  const bucket = getAgingBucket(days)
  const bg = bucket === '0-30' ? 'var(--green-bg, #DCFCE7)' : bucket === '31-60' ? 'var(--amber-bg, #FEF3C7)' : bucket === '61-90' ? '#FFEDD5' : '#FEE2E2'
  const color = bucket === '0-30' ? 'var(--green, #166534)' : bucket === '31-60' ? 'var(--amber, #B45309)' : bucket === '61-90' ? '#C2410C' : '#991B1B'

  return (
    <span style={{
      fontSize: '0.66rem',
      fontWeight: 800,
      padding: '2px 7px',
      borderRadius: 4,
      background: bg,
      color: color,
      display: 'inline-flex',
      alignItems: 'center',
      gap: 3,
    }}>
      ⏱️ {days}d{bucket === '90+' ? ' · Critical' : ''}
    </span>
  )
}

export default function FmQueuePage() {
  const [type, setType] = useState<ReviewType>('receipt')
  const [queue, setQueue] = useState<FmQueue>(EMPTY)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [doc, setDoc] = useState<DetailDoc | null>(null)
  const [error, setError] = useState('')
  const [flash, setFlash] = useState('')
  const [tick, setTick] = useState(0)
  const [claimBucket, setClaimBucket] = useState<AgingBucket>('all')
  const [sortMode, setSortMode] = useState<SortMode>('all')

  const reload = useCallback(() => setTick((n) => n + 1), [])
  const riskMap = useRiskMap(type)

  const filteredOpenClaims = useMemo(() => {
    if (claimBucket === 'all') return queue.openClaims
    return queue.openClaims.filter((c) => getAgingBucket(claimAgeDays(c.submittedAt)) === claimBucket)
  }, [queue.openClaims, claimBucket])

  useEffect(() => {
    let live = true
    setError('')
    reviewApi.fmQueue(type)
      .then(({ data }) => { if (live) setQueue(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the approval queue.')) })
    return () => { live = false }
  }, [type, tick])

  useEffect(() => {
    if (selectedId == null) { setDoc(null); return }
    let live = true
    const load = async () => {
      try {
        const req = type === 'receipt' ? receiptsApi.get(selectedId)
          : type === 'expense' ? expensesApi.get(selectedId)
          : cashApi.get(selectedId)
        const { data } = await req
        if (live) setDoc(data as DetailDoc)
      } catch (e) {
        // No synthetic fallback: queue rows carry real document ids, so a load
        // failure is a real failure — name it instead of fabricating a record.
        if (live) setError(apiError(e, `Could not load document #${selectedId}.`))
      }
    }
    load()
    return () => { live = false }
  }, [selectedId, type, tick])

  function pickType(t: ReviewType) {
    setType(t); setSelectedId(null); setDoc(null); setFlash('')
  }

  function afterAction(message: string, keepOpen: boolean) {
    setFlash(message)
    setTimeout(() => setFlash(''), 3000)
    if (!keepOpen) setSelectedId(null)
    reload()
  }

  const total = queue.awaitingApproval.reduce((a, r) => a + r.amount, 0)

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap', marginBottom: 14 }}>
        <div>
          <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Approvals & Claims</h1>
          <div style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
            Give each verified entry final approval, and close warranty / AMC / CG claims.
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <SortModeControl value={sortMode} onChange={setSortMode} />
          <GlobalSearch />
        </div>
      </div>

      <ErrorBanner message={error} />
      {flash && (
        <div className="dams-anim-notice" style={{ background: 'var(--green-bg)', color: 'var(--green)', borderRadius: 8, padding: '8px 12px', fontSize: '0.82rem', marginBottom: 12 }}>
          {flash}
        </div>
      )}

      {type === 'receipt' && <AiClaimBanner />}

      <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr] border border-[var(--line)] rounded-[var(--radius)] overflow-hidden bg-[var(--surface)] min-h-[68vh]">
        <div className={selectedId != null ? 'hidden lg:flex flex-col overflow-y-auto' : 'flex flex-col overflow-y-auto'}>
          <div style={{ display: 'flex', padding: 12, gap: 4 }}>
            {(['receipt', 'expense', 'cash'] as const).map((t) => (
              <button key={t} type="button" onClick={() => pickType(t)}
                style={{
                  flex: 1, border: '1px solid var(--line)', borderRadius: 7, padding: '7px 0',
                  fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer',
                  background: type === t ? 'var(--navy)' : 'var(--surface)',
                  color: type === t ? '#fff' : 'var(--muted)',
                }}>
                {t === 'receipt' ? 'Receipts' : t === 'expense' ? 'Expenses' : 'Cash'}
              </button>
            ))}
          </div>

          <Section title="Awaiting final approval" items={queue.awaitingApproval} selectedId={selectedId} onSelect={setSelectedId} riskMap={riskMap} sortMode={sortMode} />
          {type === 'receipt' && (
            <>
              <Section
                title="Open warranty / AMC / CG claims"
                items={filteredOpenClaims}
                selectedId={selectedId}
                onSelect={setSelectedId}
                showAging
                claimFilter={claimBucket}
                onClaimFilterChange={setClaimBucket}
                totalCount={queue.openClaims.length}
              />
              <Section title="Recently closed" items={queue.recentlyClosed} selectedId={selectedId} onSelect={setSelectedId} plain />
            </>
          )}
        </div>

        <div className={selectedId == null ? 'hidden lg:block border-t lg:border-t-0 lg:border-l border-[var(--line)] p-4 sm:p-6 overflow-y-auto' : 'block border-t lg:border-t-0 lg:border-l border-[var(--line)] p-4 sm:p-6 overflow-y-auto'}>
          {selectedId == null
            ? <Overview count={queue.awaitingApproval.length} total={total} openClaimsList={queue.openClaims} type={type} />
            : doc == null
              ? <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div className="lg:hidden mb-2">
                    <button type="button" onClick={() => setSelectedId(null)} style={{ ...ghostBtn, minHeight: 36, fontWeight: 700 }}>← Back to Queue</button>
                  </div>
                  {error ? (
                    <div style={{ ...card, color: 'var(--red)', fontSize: '0.85rem' }}>{error}</div>
                  ) : (
                    <>
                      <Skeleton width={200} height={20} />
                      <SkeletonRows rows={5} />
                    </>
                  )}
                </div>
              : <FmDetail type={type} doc={doc} onDone={afterAction} onError={setError} onBack={() => setSelectedId(null)} />}
        </div>
      </div>
    </div>
  )
}

// ───────────────────────────── Left sections ─────────────────────────────

function Section(props: {
  title: string
  items: ReviewQueueItem[]
  selectedId: number | null
  onSelect: (id: number) => void
  plain?: boolean
  riskMap?: Map<number, import('../api/ai').RiskScore>
  showAging?: boolean
  claimFilter?: AgingBucket
  onClaimFilterChange?: (bucket: AgingBucket) => void
  totalCount?: number
  sortMode?: SortMode
}) {
  const groups = useMemo(() => groupItems(props.items, props.sortMode ?? 'all'), [props.items, props.sortMode])
  return (
    <>
      <div style={{ padding: '10px 14px 6px', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--faint)', fontWeight: 700 }}>
        {props.title}
        <span style={{ background: 'var(--gray-bg)', color: 'var(--gray)', borderRadius: 999, fontSize: '0.66rem', padding: '1px 7px', marginLeft: 6 }}>
          {props.totalCount ?? props.items.length}
        </span>
      </div>

      {props.showAging && props.onClaimFilterChange && (
        <div style={{ display: 'flex', gap: 4, padding: '2px 14px 8px', flexWrap: 'wrap' }}>
          {(['all', '0-30', '31-60', '61-90', '90+'] as const).map((b) => (
            <button
              key={b}
              type="button"
              onClick={() => props.onClaimFilterChange!(b)}
              style={{
                border: '1px solid var(--line)',
                borderRadius: 4,
                padding: '2px 6px',
                fontSize: '0.68rem',
                fontWeight: props.claimFilter === b ? 700 : 500,
                background: props.claimFilter === b ? 'var(--navy)' : 'transparent',
                color: props.claimFilter === b ? '#fff' : 'var(--muted)',
                cursor: 'pointer',
              }}
            >
              {b === 'all' ? 'All' : `${b}d`}
            </button>
          ))}
        </div>
      )}

      {groups.map((g) => (
        <div key={g.heading || 'all'}>
          {g.heading && (
            <div style={{
              padding: '6px 14px', fontSize: '0.7rem', fontWeight: 800, color: 'var(--navy2)',
              background: 'var(--navy3)', textTransform: props.sortMode === 'branch' ? 'uppercase' : 'none' as 'uppercase' | 'none',
            }}>
              {props.sortMode === 'date' ? fmtGroupDate(g.heading) : g.heading} · {g.rows.length}
            </div>
          )}
          {g.rows.map((it) => {
            const sel = props.selectedId === it.id
            return (
              <button key={`${props.title}-${it.id}`} type="button" onClick={() => props.onSelect(it.id)}
                style={{
                  textAlign: 'left', border: 'none', borderBottom: '1px solid var(--line)', cursor: 'pointer',
                  borderLeft: `3px solid ${sel ? 'var(--navy)' : 'transparent'}`,
                  background: sel ? 'var(--navy3)' : 'transparent', padding: '12px 14px', minHeight: 44,
                  display: 'flex', flexDirection: 'column', gap: 3, opacity: props.plain ? 0.75 : 1, width: '100%',
                }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                  <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.7rem', fontWeight: 700, color: 'var(--navy2)' }}>
                    {it.documentNo ?? 'draft'}
                  </span>
                  <span style={{ marginLeft: 'auto', fontSize: '0.68rem', color: 'var(--faint)' }}>{it.branchCode}</span>
                </div>
                <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{it.partyName}</div>
                <div style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>
                  {it.categoryName} · <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{inr(it.amount)}</strong>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginTop: 2 }}>
                  {it.hasOverride && <Tag>Overridden</Tag>}
                  {props.showAging && <AgingBadge days={claimAgeDays(it.submittedAt)} />}
                  {props.riskMap?.get(it.id) != null && props.riskMap.get(it.id)!.score > 0 && (
                    <RiskDot risk={props.riskMap.get(it.id)} />
                  )}
                </div>
              </button>
            )
          })}
        </div>
      ))}
    </>
  )
}

function fmtGroupDate(key: string): string {
  if (key === 'Unknown date') return key
  const d = new Date(key + 'T00:00:00')
  if (isNaN(d.getTime())) return key
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

function Overview({
  count,
  total,
  openClaimsList,
  type,
}: {
  count: number
  total: number
  openClaimsList: ReviewQueueItem[]
  type: ReviewType
}) {
  const agingCounts = useMemo(() => {
    let b0_30 = 0
    let b31_60 = 0
    let b61_90 = 0
    let b90_plus = 0
    for (const c of openClaimsList) {
      const days = claimAgeDays(c.submittedAt)
      if (days <= 30) b0_30++
      else if (days <= 60) b31_60++
      else if (days <= 90) b61_90++
      else b90_plus++
    }
    return { b0_30, b31_60, b61_90, b90_plus }
  }, [openClaimsList])

  return (
    <div>
      <h2 style={{ fontSize: '1.1rem', color: 'var(--navy)' }}>Finance Manager overview</h2>
      <div style={{ fontSize: '0.82rem', color: 'var(--muted)', marginBottom: 16 }}>
        Reviewing {type === 'receipt' ? 'receipts' : type === 'expense' ? 'expenses' : 'cash movements'}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 180px), 1fr))', gap: 12 }}>
        <div style={{ ...card, borderTop: '3px solid var(--navy2)' }}>
          <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', color: 'var(--muted)', fontWeight: 600 }}>Awaiting final approval</div>
          <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: 4 }}>{count}</div>
          <div style={{ fontSize: '0.76rem', color: 'var(--faint)' }}>{inr(total)} total</div>
        </div>
        {type === 'receipt' && (
          <div style={{ ...card, borderTop: '3px solid var(--purple, #6B3FA0)' }}>
            <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', color: 'var(--muted)', fontWeight: 600 }}>Open claims</div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: 4 }}>{openClaimsList.length}</div>
            <div style={{ fontSize: '0.76rem', color: 'var(--faint)' }}>awaiting your close</div>
          </div>
        )}
      </div>

      {type === 'receipt' && openClaimsList.length > 0 && (
        <div style={{ ...card, marginTop: 16 }}>
          <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy)', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span>⏱️</span> OEM Claim Aging Buckets
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))', gap: 8 }}>
            <div style={{ padding: '8px 10px', background: 'var(--green-bg, #DCFCE7)', borderRadius: 6, border: '1px solid #BBF7D0' }}>
              <div style={{ fontSize: '0.68rem', fontWeight: 600, color: '#166534' }}>0–30 Days</div>
              <div style={{ fontSize: '1.1rem', fontWeight: 800, color: '#166534', marginTop: 2 }}>{agingCounts.b0_30}</div>
              <div style={{ fontSize: '0.62rem', color: '#15803D' }}>Normal</div>
            </div>
            <div style={{ padding: '8px 10px', background: 'var(--amber-bg, #FEF3C7)', borderRadius: 6, border: '1px solid #FDE68A' }}>
              <div style={{ fontSize: '0.68rem', fontWeight: 600, color: '#B45309' }}>31–60 Days</div>
              <div style={{ fontSize: '1.1rem', fontWeight: 800, color: '#B45309', marginTop: 2 }}>{agingCounts.b31_60}</div>
              <div style={{ fontSize: '0.62rem', color: '#B45309' }}>Follow-up</div>
            </div>
            <div style={{ padding: '8px 10px', background: '#FFEDD5', borderRadius: 6, border: '1px solid #FED7AA' }}>
              <div style={{ fontSize: '0.68rem', fontWeight: 600, color: '#C2410C' }}>61–90 Days</div>
              <div style={{ fontSize: '1.1rem', fontWeight: 800, color: '#C2410C', marginTop: 2 }}>{agingCounts.b61_90}</div>
              <div style={{ fontSize: '0.62rem', color: '#C2410C' }}>Escalate</div>
            </div>
            <div style={{ padding: '8px 10px', background: '#FEE2E2', borderRadius: 6, border: '1px solid #FECACA' }}>
              <div style={{ fontSize: '0.68rem', fontWeight: 600, color: '#991B1B' }}>90+ Days</div>
              <div style={{ fontSize: '1.1rem', fontWeight: 800, color: '#991B1B', marginTop: 2 }}>{agingCounts.b90_plus}</div>
              <div style={{ fontSize: '0.62rem', color: '#B91C1C' }}>Critical</div>
            </div>
          </div>
        </div>
      )}

      <div style={{ textAlign: 'center', fontSize: '0.82rem', color: 'var(--faint)', marginTop: 22, paddingTop: 16, borderTop: '1px dashed var(--line)' }}>
        Select an item from the list to review it.
      </div>
    </div>
  )
}

// ───────────────────────────── Detail + FM actions ─────────────────────────────

function FmDetail(props: {
  type: ReviewType
  doc: DetailDoc
  onDone: (message: string, keepOpen: boolean) => void
  onError: (msg: string) => void
  onBack?: () => void
}) {
  const { type, doc } = props
  const cash = type === 'cash'
  const receipt = type === 'receipt' ? (doc as ReceiveDocument) : null
  const wf = doc.workflowStatus
  const docNo = doc.documentNo ?? 'draft'

  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [box, setBox] = useState<'query' | null>(null)
  const [boxText, setBoxText] = useState('')
  const [claimModal, setClaimModal] = useState(false)

  const { user } = useAuth()
  // Maker-checker mirror (claim close needs none — an FM can never be a maker by
  // construction, and the service does not check it there either).
  const isMaker = user != null && (user.userId === doc.createdBy
    || (doc.lastModifiedBy != null && user.userId === doc.lastModifiedBy))
  const canApprove = wf === 'VERIFIED' && !isMaker

  const isOpenClaim = !!receipt && receipt.isClaim && wf === 'APPROVED' && !receipt.settledViaClaimClose
  const isClosedClaim = !!receipt && receipt.settledViaClaimClose

  async function run(fn: () => Promise<unknown>, message: string, keepOpen = false) {
    setBusy(true)
    setError('')
    try {
      await fn()
      props.onDone(message, keepOpen)
    } catch (e) {
      setError(apiError(e, 'The action could not be completed.'))
    } finally {
      setBusy(false)
    }
  }

  function submitBox() {
    const text = boxText.trim()
    if (!text) { setError('Type the question for the cashier'); return }
    run(() => reviewApi.query(type, doc.id, text), `${docNo} queried — sent back to the cashier`)
  }

  return (
    <div>
      {props.onBack && (
        <div className="lg:hidden mb-3">
          <button
            type="button"
            onClick={props.onBack}
            style={{ ...ghostBtn, minHeight: 36, display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700 }}
          >
            ← Back to Queue
          </button>
        </div>
      )}
      <ErrorBanner message={error} />
      {cash
        ? <CashRecordCard doc={doc as CashDocument} />
        : <RecordCard doc={doc as AnyDoc} canOverride={false} busy={busy} onError={setError} onOverride={async () => {}} />}

      {isClosedClaim && receipt && (
        <div style={{ ...card, background: 'var(--purple-bg, #EFE7FB)', borderColor: '#D9C7EF', marginBottom: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.86rem' }}>
            <span style={{ fontWeight: 700 }}>Claim closed · Final amount (locked)</span>
            <span style={{ fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{inr(receipt.claimFinalAmount)}</span>
          </div>
          <div style={{ fontSize: '0.76rem', color: 'var(--amber)', marginTop: 4 }}>
            {receipt.claimOverridden
              ? `Overridden · Final — ${receipt.claimOverrideReason ?? ''}`
              : 'Paid in full as invoiced — no override.'}
          </div>
        </div>
      )}

      {!canApprove && !isOpenClaim ? (
        <div style={{ ...card, textAlign: 'center', color: 'var(--faint)', fontSize: '0.84rem' }}>
          {isMaker
            ? 'You created or last edited this entry — maker-checker requires another reviewer.'
            : 'No action needed from you right now.'}
        </div>
      ) : (
        <div style={{ ...card }}>
          {box && (
            <QueryRejectBox
              kind={box} text={boxText} busy={busy}
              onText={setBoxText}
              onCancel={() => { setBox(null); setBoxText(''); setError('') }}
              onSubmit={submitBox}
            />
          )}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
            {canApprove && (
              <>
                <button type="button" onClick={() => { setBox(box === 'query' ? null : 'query'); setBoxText(''); setError('') }}
                  style={{ ...ghostBtn, color: 'var(--amber)', minHeight: 36 }}>Query</button>
                <button type="button" onClick={() => run(() => reviewApi.approve(type, doc.id), `${docNo} approved`)}
                  disabled={busy} style={{ ...primaryBtn(busy), minHeight: 36 }}>Approve</button>
              </>
            )}
            {isOpenClaim && (
              <button type="button" onClick={() => setClaimModal(true)} disabled={busy}
                style={{ ...primaryBtn(busy), background: 'var(--purple, #6B3FA0)', minHeight: 36 }}>
                Close claim
              </button>
            )}
          </div>
        </div>
      )}

      {claimModal && receipt && (
        <ClaimCloseModal
          jobCardId={receipt.jobCardId}
          jobCardReference={receipt.jobCardReference}
          computedTotal={receipt.totalReceived}
          onClose={() => setClaimModal(false)}
          onDone={() => { setClaimModal(false); props.onDone(`${docNo} claim closed — locked`, false) }}
        />
      )}
    </div>
  )
}

function ClaimCloseModal(props: {
  jobCardId: number
  jobCardReference: string
  computedTotal: number
  onClose: () => void
  onDone: () => void
}) {
  const [amount, setAmount] = useState(String(props.computedTotal))
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const finalAmount = Number(amount)
  const overridden = amount !== '' && finalAmount !== props.computedTotal

  async function confirm() {
    if (amount === '' || finalAmount < 0) { setError('Enter the final amount'); return }
    if (overridden && !reason.trim()) { setError('A reason is required when the final amount differs from what was received'); return }
    setBusy(true)
    setError('')
    try {
      await jobCardsApi.closeClaim(props.jobCardId, { finalAmount, reason: reason.trim() || undefined })
      props.onDone()
    } catch (e) {
      setError(apiError(e, 'Could not close the claim.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Close the claim" subtitle={props.jobCardReference} onClose={props.onClose}>
      <ErrorBanner message={error} />
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.84rem', padding: '4px 0' }}>
        <span style={{ color: 'var(--muted)' }}>Received so far</span>
        <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(props.computedTotal)}</span>
      </div>
      <label style={fieldLabel}>Final amount (locked once closed)
        <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} style={{ ...inputStyle, textAlign: 'right' }} />
      </label>
      <label style={fieldLabel}>
        Reason {overridden && <span style={{ color: 'var(--red)' }}>*</span>}
        <input value={reason} onChange={(e) => setReason(e.target.value)} disabled={!overridden}
          placeholder={overridden ? 'e.g. Eicher partial settlement — remainder written off' : 'not needed — matches what was received'}
          style={inputStyle} />
      </label>
      <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
        This is final and immutable. The job card's category and business status lock, and no new receipt can be opened against it.
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, paddingTop: 4 }}>
        <button type="button" onClick={props.onClose} style={{ ...ghostBtn, minHeight: 36 }} disabled={busy}>Cancel</button>
        <button type="button" onClick={confirm} disabled={busy}
          style={{ ...primaryBtn(busy), background: 'var(--purple, #6B3FA0)', minHeight: 36 }}>Confirm &amp; close</button>
      </div>
    </Modal>
  )
}

const fieldLabel = { display: 'flex', flexDirection: 'column' as const, gap: 5, fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)', marginTop: 8 }
