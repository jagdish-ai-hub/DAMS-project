import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { receiptsApi, type ReceiveDocument } from '../api/receipts'
import { expensesApi } from '../api/expenses'
import { cashApi, reopenApi, type CashDocument, type ReopenRequest } from '../api/cash'
import { reviewApi, type FmQueue, type ReviewQueueItem, type ReviewType } from '../api/review'
import { jobCardsApi } from '../api/jobCards'
import { card, ErrorBanner, ghostBtn, primaryBtn, inputStyle, Modal, Skeleton, SkeletonRows, inr } from '../shell/ui'
import { RecordCard, CashRecordCard, QueryRejectBox, Tag, apiError, type AnyDoc } from '../review/reviewShared'
import { useCopy } from '../shared/useCopy'
import GlobalSearch from '../shared/GlobalSearch'
import HelpButton from '../help/HelpButton'
import { useAuth } from '../auth/useAuth'
import AiClaimBanner from './AiClaimBanner'
import { useRiskMap, RiskDot } from '../review/AiRiskBadge'

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
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [bulkBusy, setBulkBusy] = useState(false)

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
    setType(t); setSelectedId(null); setDoc(null); setFlash(''); setSelectedIds([])
  }

  function toggleSelect(id: number) {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    )
  }

  function toggleSelectAll(list: ReviewQueueItem[]) {
    if (list.length === 0) return
    const allSelected = list.every((it) => selectedIds.includes(it.id))
    if (allSelected) setSelectedIds([])
    else setSelectedIds(list.map((it) => it.id))
  }

  async function handleBulkApprove() {
    if (selectedIds.length === 0 || (type !== 'receipt' && type !== 'expense')) return
    setBulkBusy(true)
    setError('')
    try {
      const res = await reviewApi.bulkApprove(type, selectedIds)
      const data = res.data
      const skipped = data.skippedReasons.length
      setFlash(`Approved ${data.verifiedCount} ${type}s.${skipped > 0 ? ` (${skipped} skipped: ${data.skippedReasons.slice(0, 3).join('; ')}${skipped > 3 ? '…' : ''})` : ''}`)
      setTimeout(() => setFlash(''), 4000)
      setSelectedIds([])
      // Drop selected rows that no longer belong; keep the detail open only if untouched.
      if (selectedId != null && data.verifiedIds.includes(selectedId)) setSelectedId(null)
      reload()
    } catch (e) {
      setError(apiError(e, 'Could not bulk approve items.'))
    } finally {
      setBulkBusy(false)
    }
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Approvals & Claims</h1>
            <HelpButton slug="approving-entries" />
          </div>
          <div style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
            Give each verified entry final approval, and close warranty / AMC / CG claims.
          </div>
        </div>
        <GlobalSearch />
      </div>

      <ErrorBanner message={error} />
      {flash && (
        <div className="dams-anim-notice" style={{ background: 'var(--green-bg)', color: 'var(--green)', borderRadius: 8, padding: '8px 12px', fontSize: '0.82rem', marginBottom: 12 }}>
          {flash}
        </div>
      )}

      {type === 'receipt' && <AiClaimBanner />}

      {type === 'cash' && <ReopenRequestsCard onDone={reload} onError={setError} />}

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

          <Section
            title="Awaiting final approval"
            items={queue.awaitingApproval}
            selectedId={selectedId}
            onSelect={setSelectedId}
            riskMap={riskMap}
            showFilters
            selectable={type !== 'cash'}
            selectedIds={selectedIds}
            onToggleCheck={toggleSelect}
            onToggleSelectAll={() => toggleSelectAll(queue.awaitingApproval)}
            bulkBar={
              type !== 'cash' && selectedIds.length > 0 ? (
                <div style={{
                  position: 'sticky', bottom: 0, zIndex: 10,
                  background: 'var(--navy)', color: '#fff', padding: '10px 14px',
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
                }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>
                    {selectedIds.length} selected
                  </span>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      type="button"
                      onClick={() => setSelectedIds([])}
                      style={{ background: 'transparent', color: '#fff', border: '1px solid rgba(255,255,255,0.3)', borderRadius: 6, padding: '4px 10px', fontSize: '0.75rem', cursor: 'pointer' }}
                    >
                      Clear
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleBulkApprove()}
                      disabled={bulkBusy}
                      style={{ background: 'var(--green)', color: '#fff', border: 'none', borderRadius: 6, padding: '4px 12px', fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer' }}
                    >
                      {bulkBusy ? 'Approving…' : `Approve selected (${selectedIds.length})`}
                    </button>
                  </div>
                </div>
              ) : undefined
            }
          />
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
  showFilters?: boolean
  selectable?: boolean
  selectedIds?: number[]
  onToggleCheck?: (id: number) => void
  onToggleSelectAll?: () => void
  bulkBar?: ReactNode
}) {
  const [overLimitOnly, setOverLimitOnly] = useState(false)
  const [overrideOnly, setOverrideOnly] = useState(false)
  const [noBillOnly, setNoBillOnly] = useState(false)
  const hasRisk = (props.riskMap?.size ?? 0) > 0

  const visible = useMemo(() => {
    if (!props.showFilters) return props.items
    return props.items.filter((it) => {
      if (overLimitOnly && !it.overLimit) return false
      if (overrideOnly && !it.hasOverride) return false
      if (noBillOnly) {
        const r = props.riskMap?.get(it.id)
        if (!r || !r.reasons.some((reason) => reason.toLowerCase().includes('bill'))) return false
      }
      return true
    })
  }, [props.items, props.showFilters, overLimitOnly, overrideOnly, noBillOnly, props.riskMap])
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

      {props.showFilters && (
        <div style={{ display: 'flex', gap: 12, padding: '0 14px 8px', fontSize: '0.76rem', flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer' }}>
            <input type="checkbox" checked={overLimitOnly} onChange={(e) => setOverLimitOnly(e.target.checked)} />
            Over-limit
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer' }}>
            <input type="checkbox" checked={overrideOnly} onChange={(e) => setOverrideOnly(e.target.checked)} />
            Has override
          </label>
          {hasRisk && (
            <label style={{ display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer' }}>
              <input type="checkbox" checked={noBillOnly} onChange={(e) => setNoBillOnly(e.target.checked)} />
              No bill
            </label>
          )}
        </div>
      )}

      {props.selectable && visible.length > 0 && (
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '6px 14px', borderBottom: '1px solid var(--line)', background: 'var(--bg)',
          fontSize: '0.76rem',
        }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontWeight: 600 }}>
            <input
              type="checkbox"
              checked={visible.length > 0 && visible.every((it) => props.selectedIds?.includes(it.id))}
              onChange={props.onToggleSelectAll}
            />
            <span>Select all ({visible.length})</span>
          </label>
          {(props.selectedIds?.length ?? 0) > 0 && (
            <span style={{ color: 'var(--navy)', fontWeight: 700 }}>
              {props.selectedIds!.length} selected
            </span>
          )}
        </div>
      )}

      {props.showFilters && visible.length === 0 && props.items.length > 0 && (
        <p style={{ padding: '8px 14px', color: 'var(--faint)', fontSize: '0.8rem' }}>No items match these filters.</p>
      )}

      {visible.map((it) => {
        const sel = props.selectedId === it.id
        const checked = props.selectedIds?.includes(it.id)
        return (
          <div
            key={`${props.title}-${it.id}`}
            style={{
              borderBottom: '1px solid var(--line)',
              borderLeft: `3px solid ${sel ? 'var(--navy)' : 'transparent'}`,
              background: sel ? 'var(--navy3)' : 'transparent',
              display: 'flex', alignItems: 'stretch',
              opacity: props.plain ? 0.75 : 1,
            }}
          >
            {props.selectable && (
              <div
                onClick={(e) => { e.stopPropagation(); props.onToggleCheck?.(it.id) }}
                style={{ padding: '12px 0 12px 12px', display: 'flex', alignItems: 'flex-start', cursor: 'pointer' }}
              >
                <input type="checkbox" checked={Boolean(checked)} onChange={() => {}} style={{ cursor: 'pointer', marginTop: 2 }} />
              </div>
            )}
            <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
              <button type="button" onClick={() => props.onSelect(it.id)}
                style={{
                  textAlign: 'left', border: 'none', cursor: 'pointer', background: 'transparent',
                  padding: '12px 14px', minHeight: 44,
                  display: 'flex', flexDirection: 'column', gap: 3, width: '100%',
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
                  {it.overLimit && <Tag>Above limit</Tag>}
                  {props.showAging && <AgingBadge days={claimAgeDays(it.submittedAt)} />}
                  {props.riskMap?.get(it.id) != null && props.riskMap.get(it.id)!.score > 0 && (
                    <RiskDot risk={props.riskMap.get(it.id)} />
                  )}
                </div>
              </button>
              {props.showAging && (
                <div style={{ padding: '0 14px 10px' }}>
                  <ClaimRowPack item={it} />
                </div>
              )}
            </div>
          </div>
        )
      })}
      {props.bulkBar}
    </>
  )
}

/** Compact claim pack for an open-claim queue row (receipts only). */
function ClaimRowPack({ item }: { item: ReviewQueueItem }) {
  const { copiedKey, copyError, copy } = useCopy()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const key = `claim-row-${item.id}`

  function summary(): string {
    const age = claimAgeDays(item.submittedAt)
    return `Claim ${item.documentNo ?? `#${item.id}`} · ${item.partyName} · ${item.branchCode} · ${inr(item.amount)} · Age ${age}d`
  }

  async function openAll() {
    setBusy(true)
    setError('')
    try {
      const all = []
      const top = await receiptsApi.documentAttachments(item.id)
      for (const a of top.data) all.push(a)
      const detail = await receiptsApi.get(item.id)
      for (const l of detail.data.lines) {
        const r = await receiptsApi.lineAttachments(item.id, l.lineNo)
        for (const a of r.data) all.push(a)
      }
      if (all.length === 0) {
        setError('No bills attached yet.')
        return
      }
      for (const a of all) {
        const { data } = await receiptsApi.signedUrl(a.id)
        window.open(data.url, '_blank', 'noopener')
      }
    } catch {
      setError('Could not open bills.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
      <button
        type="button"
        onClick={() => void copy(key, summary())}
        style={{ background: 'none', border: 'none', color: 'var(--navy2)', cursor: 'pointer', fontSize: '0.72rem', fontWeight: 700, padding: 0 }}
      >
        {copiedKey === key ? 'Copied ✓' : 'Copy claim summary'}
      </button>
      <button
        type="button"
        onClick={() => void openAll()}
        disabled={busy}
        style={{ background: 'none', border: 'none', color: 'var(--navy2)', cursor: 'pointer', fontSize: '0.72rem', fontWeight: 700, padding: 0 }}
      >
        {busy ? 'Opening…' : 'Open all bills'}
      </button>
      {(error || copyError) && <span style={{ fontSize: '0.7rem', color: 'var(--red)' }}>{error || copyError}</span>}
    </div>
  )
}

// ───────────────────────────── Cash-day reopen requests (FM side) ─────────────────────────────
// Cashiers file these from the Cash page when a locked close was miscounted. Approving
// removes the close row so the day can be re-closed; rejecting keeps the lock. Both are audited.

function ReopenRequestsCard({ onDone, onError }: { onDone: () => void; onError: (msg: string) => void }) {
  const [items, setItems] = useState<ReopenRequest[] | null>(null)
  const [rejectId, setRejectId] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(() => {
    reopenApi.list('PENDING').then(({ data }) => setItems(data)).catch(() => setItems([]))
  }, [])

  useEffect(() => { load() }, [load])

  async function act(id: number, kind: 'approve' | 'reject') {
    if (kind === 'reject' && !rejectReason.trim()) { onError('A reason is required to reject a reopen request.'); return }
    setBusyId(id)
    try {
      if (kind === 'approve') await reopenApi.approve(id)
      else await reopenApi.reject(id, rejectReason.trim())
      setRejectId(null)
      setRejectReason('')
      load()
      onDone()
    } catch (e) {
      onError(apiError(e, 'Could not decide that reopen request.'))
    } finally {
      setBusyId(null)
    }
  }

  if (items == null || items.length === 0) return null

  return (
    <div style={{ ...card, marginBottom: 12, borderLeft: '3px solid var(--amber)' }}>
      <h3 style={{ fontSize: '0.88rem', fontWeight: 800, marginBottom: 8 }}>
        Cash-day reopen requests · {items.length} pending
      </h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {items.map((r) => (
          <div key={r.id} style={{ fontSize: '0.8rem', borderTop: '1px solid var(--line)', paddingTop: 8 }}>
            <div><strong>{r.branchCode ?? '—'}</strong> · {r.closeDate} · “{r.reason}”</div>
            {rejectId === r.id ? (
              <div style={{ display: 'flex', gap: 8, marginTop: 6, flexWrap: 'wrap' }}>
                <input value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} maxLength={500}
                  placeholder="Rejection reason" style={{ ...inputStyle, flex: 1, minWidth: 180 }} />
                <button type="button" disabled={busyId === r.id} onClick={() => void act(r.id, 'reject')}
                  style={{ ...primaryBtn(), minHeight: 30, fontSize: '0.76rem' }}>Reject</button>
                <button type="button" onClick={() => { setRejectId(null); setRejectReason('') }}
                  style={{ ...ghostBtn, minHeight: 30, fontSize: '0.76rem' }}>Cancel</button>
              </div>
            ) : (
              <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
                <button type="button" disabled={busyId === r.id} onClick={() => void act(r.id, 'approve')}
                  style={{ ...primaryBtn(), minHeight: 30, fontSize: '0.76rem' }}>
                  {busyId === r.id ? 'Working…' : 'Approve & reopen day'}
                </button>
                <button type="button" onClick={() => setRejectId(r.id)}
                  style={{ ...ghostBtn, minHeight: 30, fontSize: '0.76rem' }}>Reject</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
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
