import { useCallback, useEffect, useMemo, useState } from 'react'
import { receiptsApi } from '../api/receipts'
import { expensesApi } from '../api/expenses'
import { cashApi, type CashDocument } from '../api/cash'
import { reviewApi, type ReviewQueueItem, type ReviewType } from '../api/review'
import { card, ErrorBanner, ghostBtn, primaryBtn, Modal, Badge, Skeleton, SkeletonRows, inr, inputStyle, th, td } from '../shell/ui'
import { RecordCard, CashRecordCard, QueryRejectBox, Tag, apiError, type AnyDoc } from '../review/reviewShared'
import GlobalSearch from '../shared/GlobalSearch'
import { useAuth } from '../auth/useAuth'
import { useRiskMap, RiskDot, } from '../review/AiRiskBadge'
import type { RiskScore } from '../api/ai'
import SortModeControl, { groupItems, type SortMode } from '../review/SortModeControl'

/**
 * Accountant review queue (intial ui prototypes/review-close.html, Accountant view). Two
 * panes: the SUBMITTED queue on the left, and either an overview (nothing selected) or the
 * full record with verify / query / reject / per-line override on the right. Expenses also
 * get an explicit Close; cash movements verify / query / reject (no lines).
 */

type DetailDoc = AnyDoc | CashDocument

const queueFor = (t: ReviewType) =>
  t === 'receipt' ? reviewApi.receiptQueue() : t === 'expense' ? reviewApi.expenseQueue() : reviewApi.cashQueue()
const detailFor = (t: ReviewType, id: number) =>
  t === 'receipt' ? receiptsApi.get(id) : t === 'expense' ? expensesApi.get(id) : cashApi.get(id)

const verifiedQueueFor = (t: ReviewType) => reviewApi.verifiedQueue(t)

function fmtGroupDate(key: string): string {
  if (key === 'Unknown date') return key
  const d = new Date(key + 'T00:00:00')
  if (isNaN(d.getTime())) return key
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export default function ReviewQueuePage() {
  const [type, setType] = useState<ReviewType>('receipt')
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null)
  const [verifiedItems, setVerifiedItems] = useState<ReviewQueueItem[] | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [doc, setDoc] = useState<DetailDoc | null>(null)
  const [error, setError] = useState('')
  const [flash, setFlash] = useState('')
  const [tick, setTick] = useState(0)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [bulkBusy, setBulkBusy] = useState(false)
  const [sortMode, setSortMode] = useState<SortMode>('all')
  const [drilldown, setDrilldown] = useState(false)

  const reload = useCallback(() => setTick((n) => n + 1), [])
  const riskMap = useRiskMap(type)

  useEffect(() => {
    let live = true
    setError('')
    Promise.all([queueFor(type), verifiedQueueFor(type)])
      .then(([q, v]) => { if (live) { setItems(q.data); setVerifiedItems(v.data) } })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the review queue.')) })
    return () => { live = false }
  }, [type, tick])

  useEffect(() => {
    if (selectedId == null) { setDoc(null); return }
    let live = true
    detailFor(type, selectedId)
      .then(({ data }) => { if (live) setDoc(data as DetailDoc) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load that document.')) })
    return () => { live = false }
  }, [selectedId, type, tick])

  function pickType(t: ReviewType) {
    setType(t)
    setSelectedId(null)
    setSelectedIds([])
    setDoc(null)
    setFlash('')
    setDrilldown(false)
  }

  async function handleBulkVerify() {
    if (selectedIds.length === 0 || type === 'cash') return
    setBulkBusy(true)
    setError('')
    try {
      const res = await reviewApi.bulkVerify(type, selectedIds)
      const data = res.data
      setFlash(`Bulk verified ${data.verifiedCount} ${type}s successfully.${data.skippedCount > 0 ? ` (${data.skippedCount} skipped due to maker-checker or state)` : ''}`)
      setTimeout(() => setFlash(''), 4000)
      setSelectedIds([])
      reload()
    } catch (e) {
      setError(apiError(e, 'Could not bulk verify items.'))
    } finally {
      setBulkBusy(false)
    }
  }

  function toggleSelect(id: number) {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    )
  }

  function toggleSelectAll() {
    if (!items || items.length === 0) return
    const allSelected = items.every((it) => selectedIds.includes(it.id))
    if (allSelected) {
      setSelectedIds([])
    } else {
      setSelectedIds(items.map((it) => it.id))
    }
  }

  function afterAction(message: string, keepOpen: boolean) {
    setFlash(message)
    setTimeout(() => setFlash(''), 3000)
    if (!keepOpen) setSelectedId(null)
    reload()
  }

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap', marginBottom: 14 }}>
        <div>
          <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Review Queue</h1>
          <div style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
            Verify, query, or adjust each submitted entry. A verified entry moves on to the Finance Manager.
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

      <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr] border border-[var(--line)] rounded-[var(--radius)] overflow-hidden bg-[var(--surface)] min-h-[68vh]">
        <div className={selectedId != null ? 'hidden lg:flex flex-col overflow-y-auto' : 'flex flex-col overflow-y-auto'}>
          <QueuePane
            type={type}
            items={items}
            sortMode={sortMode}
            selectedId={selectedId}
            onType={pickType}
            onSelect={setSelectedId}
            riskMap={riskMap}
            selectedIds={selectedIds}
            onToggleSelect={toggleSelect}
            onToggleSelectAll={toggleSelectAll}
            onClearSelected={() => setSelectedIds([])}
            onBulkVerify={handleBulkVerify}
            bulkBusy={bulkBusy}
          />
        </div>
        <div className={selectedId == null ? 'hidden lg:block border-t lg:border-t-0 lg:border-l border-[var(--line)] p-4 sm:p-6 overflow-y-auto' : 'block border-t lg:border-t-0 lg:border-l border-[var(--line)] p-4 sm:p-6 overflow-y-auto'}>
          {selectedId == null
            ? <Overview
                type={type}
                items={items ?? []}
                verifiedItems={verifiedItems ?? []}
                onSelect={setSelectedId}
                onOpenDrilldown={() => setDrilldown(true)}
              />
            : doc == null
              ? <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div className="lg:hidden mb-2">
                    <button type="button" onClick={() => setSelectedId(null)} style={{ ...ghostBtn, minHeight: 36, fontWeight: 700 }}>← Back to Queue</button>
                  </div>
                  <Skeleton width={200} height={20} />
                  <SkeletonRows rows={5} />
                </div>
              : <RecordDetail type={type} doc={doc} onDone={afterAction} onBack={() => setSelectedId(null)} />}
        </div>
      </div>

      {drilldown && (
        <DrilldownModal
          type={type}
          pending={items ?? []}
          verified={verifiedItems ?? []}
          onSelect={(id) => { setDrilldown(false); setSelectedId(id) }}
          onClose={() => setDrilldown(false)}
        />
      )}
    </div>
  )
}

// ───────────────────────────── Queue pane ─────────────────────────────

function QueuePane(props: {
  type: ReviewType
  items: ReviewQueueItem[] | null
  sortMode: SortMode
  selectedId: number | null
  onType: (t: ReviewType) => void
  onSelect: (id: number) => void
  riskMap: Map<number, RiskScore>
  selectedIds: number[]
  onToggleSelect: (id: number) => void
  onToggleSelectAll: () => void
  onClearSelected: () => void
  onBulkVerify: () => void
  bulkBusy: boolean
}) {
  const { items } = props
  const groups = useMemo(() => groupItems(items ?? [], props.sortMode), [items, props.sortMode])
  return (
    <div style={{ display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
      <div style={{ display: 'flex', padding: 12, gap: 4 }}>
        {(['receipt', 'expense', 'cash'] as const).map((t) => (
          <button key={t} type="button" onClick={() => props.onType(t)}
            style={{
              flex: 1, border: '1px solid var(--line)', borderRadius: 7, padding: '7px 0',
              fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer',
              background: props.type === t ? 'var(--navy)' : 'var(--surface)',
              color: props.type === t ? '#fff' : 'var(--muted)',
            }}>
            {t === 'receipt' ? 'Receipts' : t === 'expense' ? 'Expenses' : 'Cash'}
          </button>
        ))}
      </div>
      <div style={{ padding: '4px 14px 8px', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--faint)', fontWeight: 700 }}>
        Awaiting your review
        <span style={{ background: 'var(--amber-bg)', color: 'var(--amber)', borderRadius: 999, fontSize: '0.66rem', padding: '1px 7px', marginLeft: 6 }}>
          {items?.length ?? 0}
        </span>
      </div>

      {props.type !== 'cash' && items && items.length > 0 && (
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '6px 14px', borderBottom: '1px solid var(--line)', background: 'var(--bg)',
          fontSize: '0.76rem',
        }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontWeight: 600 }}>
            <input
              type="checkbox"
              checked={items.length > 0 && items.every((it) => props.selectedIds.includes(it.id))}
              onChange={props.onToggleSelectAll}
            />
            <span>Select All ({items.length})</span>
          </label>
          {props.selectedIds.length > 0 && (
            <span style={{ color: 'var(--navy)', fontWeight: 700 }}>
              {props.selectedIds.length} selected
            </span>
          )}
        </div>
      )}

      {items == null && <div style={{ padding: 14 }}><SkeletonRows rows={5} height={52} /></div>}
      {items != null && items.length === 0 && (
        <p style={{ padding: 14, color: 'var(--faint)', fontSize: '0.82rem' }}>Nothing waiting on you — the queue is clear.</p>
      )}

      {groups.map((g) => (
        <div key={g.heading || 'all'}>
          {g.heading && (
            <div style={{
              padding: '6px 14px', fontSize: '0.7rem', fontWeight: 800, color: 'var(--navy2)',
              background: 'var(--navy3)', textTransform: props.sortMode === 'branch' ? 'uppercase' : 'none',
            }}>
              {props.sortMode === 'date' ? fmtGroupDate(g.heading) : g.heading} · {g.rows.length}
            </div>
          )}
          {g.rows.map((it) => (
            <QueueRow
              key={it.id}
              it={it}
              selected={props.selectedId === it.id}
              onSelect={() => props.onSelect(it.id)}
              risk={props.riskMap.get(it.id)}
              checked={props.type !== 'cash' ? props.selectedIds.includes(it.id) : undefined}
              onToggleCheck={props.type !== 'cash' ? () => props.onToggleSelect(it.id) : undefined}
            />
          ))}
        </div>
      ))}

      {props.type !== 'cash' && props.selectedIds.length > 0 && (
        <div style={{
          position: 'sticky', bottom: 0, zIndex: 10,
          background: 'var(--navy)', color: '#fff', padding: '10px 14px',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
          boxShadow: '0 -2px 10px rgba(0,0,0,0.15)',
        }}>
          <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>
            {props.selectedIds.length} item{props.selectedIds.length === 1 ? '' : 's'} selected
          </span>
          <div style={{ display: 'flex', gap: 6 }}>
            <button
              type="button"
              onClick={props.onClearSelected}
              style={{ ...ghostBtn, color: '#fff', border: '1px solid rgba(255,255,255,0.3)', minHeight: 30, padding: '3px 10px', fontSize: '0.75rem' }}
            >
              Clear
            </button>
            <button
              type="button"
              onClick={props.onBulkVerify}
              disabled={props.bulkBusy}
              style={{
                background: 'var(--green)', color: '#fff', border: 'none', borderRadius: 6,
                padding: '4px 12px', fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer', minHeight: 30,
              }}
            >
              {props.bulkBusy ? 'Verifying…' : `Verify (${props.selectedIds.length})`}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export function QueueRow({
  it,
  selected,
  onSelect,
  risk,
  checked,
  onToggleCheck,
}: {
  it: ReviewQueueItem
  selected: boolean
  onSelect: () => void
  risk?: RiskScore
  checked?: boolean
  onToggleCheck?: () => void
}) {
  return (
    <div
      style={{
        borderBottom: '1px solid var(--line)',
        borderLeft: `3px solid ${selected ? 'var(--navy)' : 'transparent'}`,
        background: selected ? 'var(--navy3)' : 'transparent',
        display: 'flex',
        alignItems: 'stretch',
        minHeight: 44,
      }}
    >
      {onToggleCheck && (
        <div
          onClick={(e) => {
            e.stopPropagation()
            onToggleCheck()
          }}
          style={{
            padding: '12px 0 12px 12px',
            display: 'flex',
            alignItems: 'flex-start',
            cursor: 'pointer',
          }}
        >
          <input
            type="checkbox"
            checked={Boolean(checked)}
            onChange={() => {}}
            style={{ cursor: 'pointer', marginTop: 2 }}
          />
        </div>
      )}
      <button
        type="button"
        onClick={onSelect}
        style={{
          flex: 1,
          textAlign: 'left',
          border: 'none',
          background: 'transparent',
          cursor: 'pointer',
          padding: '12px 14px',
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
          width: '100%',
          minWidth: 0,
        }}
      >
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
        {(it.overLimit || it.hasOverride || (risk && risk.score > 0)) && (
          <div style={{ display: 'flex', gap: 5, marginTop: 2 }}>
            {it.hasOverride && <Tag>Overridden</Tag>}
            {it.overLimit && <Tag>Above limit</Tag>}
            {risk && risk.score > 0 && <RiskDot risk={risk} />}
          </div>
        )}
      </button>
    </div>
  )
}

// ───────────────────────────── Overview panel ─────────────────────────────

function Overview(props: {
  type: ReviewType
  items: ReviewQueueItem[]
  verifiedItems: ReviewQueueItem[]
  onSelect: (id: number) => void
  onOpenDrilldown: () => void
}) {
  const { items, verifiedItems } = props
  const total = items.reduce((a, r) => a + r.amount, 0)
  const verifiedTotal = verifiedItems.reduce((a, r) => a + r.amount, 0)
  const byBranch = useMemo(() => {
    const m = new Map<string, number>()
    items.forEach((r) => m.set(r.branchCode, (m.get(r.branchCode) ?? 0) + 1))
    const max = Math.max(1, ...m.values())
    return [...m.entries()].map(([code, n]) => ({ code, n, pct: Math.round((n / max) * 100) }))
  }, [items])

  const label = props.type === 'receipt' ? 'receipts' : props.type === 'expense' ? 'expenses' : 'cash movements'

  return (
    <div>
      <h2 style={{ fontSize: '1.1rem', color: 'var(--navy)' }}>Accountant overview</h2>
      <div style={{ fontSize: '0.82rem', color: 'var(--muted)', marginBottom: 16 }}>Reviewing {label}</div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 180px), 1fr))', gap: 12, marginBottom: 18 }}>
        <Stat label="Awaiting your review" value={String(items.length)} sub={items.length === 1 ? 'item' : 'items'}
          onClick={props.onOpenDrilldown} />
        <Stat label="Total value pending" value={inr(total)} sub="across your branches" accent
          onClick={props.onOpenDrilldown} />
        <Stat label="Verified" value={String(verifiedItems.length)} sub="moved on to the Finance Manager or closed"
          onClick={props.onOpenDrilldown} />
        <Stat label="Total value verified" value={inr(verifiedTotal)} sub="across your branches" accent
          onClick={props.onOpenDrilldown} />
      </div>

      {items.length === 0 ? (
        <div style={{ ...card, color: 'var(--faint)', fontSize: '0.85rem', textAlign: 'center' }}>
          Nothing waiting on you right now — the {label} queue is clear.
        </div>
      ) : (
        <>
          <div style={{ ...card, marginBottom: 14 }}>
            <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 10 }}>By branch</h3>
            {byBranch.map((b) => (
              <div key={b.code} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '5px 0', fontSize: '0.82rem' }}>
                <span style={{ width: 46, fontWeight: 600, color: 'var(--muted)' }}>{b.code}</span>
                <span style={{ flex: 1, background: 'var(--bg)', borderRadius: 5, height: 10, overflow: 'hidden' }}>
                  <span style={{ display: 'block', height: '100%', width: `${b.pct}%`, background: 'var(--navy2)' }} />
                </span>
                <span style={{ width: 26, textAlign: 'right', fontWeight: 700 }}>{b.n}</span>
              </div>
            ))}
          </div>
          <div style={{ ...card }}>
            <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 4 }}>Needs your attention</h3>
            <div style={{ fontSize: '0.74rem', color: 'var(--faint)', marginBottom: 8 }}>click to open</div>
            {items.slice(0, 6).map((r) => (
              <button key={r.id} type="button" onClick={() => props.onSelect(r.id)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, width: '100%', textAlign: 'left',
                  border: 'none', borderTop: '1px solid var(--line)', background: 'transparent',
                  padding: '10px 0', cursor: 'pointer', fontSize: '0.84rem',
                }}>
                <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.72rem', color: 'var(--navy2)' }}>{r.documentNo ?? 'draft'}</span>
                <span style={{ flex: 1, fontWeight: 700 }}>{r.partyName}</span>
                <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(r.amount)}</span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function Stat({ label, value, sub, accent, onClick }: { label: string; value: string; sub: string; accent?: boolean; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      style={{ ...card, borderTop: `3px solid ${accent ? 'var(--amber)' : 'var(--navy2)'}`, cursor: onClick ? 'pointer' : undefined }}
    >
      <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--muted)', fontWeight: 600 }}>
        {label}{onClick && <span style={{ color: 'var(--navy2)' }}> ⓘ</span>}
      </div>
      <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: 4, fontVariantNumeric: 'tabular-nums' }}>{value}</div>
      <div style={{ fontSize: '0.76rem', color: 'var(--faint)' }}>{sub}</div>
    </div>
  )
}

// ───────────────────────────── Drill-down: pending vs verified ─────────────────────────────

function DrilldownModal(props: {
  type: ReviewType
  pending: ReviewQueueItem[]
  verified: ReviewQueueItem[]
  onSelect: (id: number) => void
  onClose: () => void
}) {
  const [branch, setBranch] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const combined = useMemo(
    () => [
      ...props.pending.map((it) => ({ ...it, bucket: 'pending' as const })),
      ...props.verified.map((it) => ({ ...it, bucket: 'verified' as const })),
    ],
    [props.pending, props.verified],
  )
  const branches = useMemo(() => [...new Set(combined.map((it) => it.branchCode))].sort(), [combined])

  const filtered = useMemo(() => combined.filter((it) => {
    if (branch && it.branchCode !== branch) return false
    const day = it.submittedAt ? it.submittedAt.slice(0, 10) : null
    if ((from || to) && !day) return false
    if (from && day! < from) return false
    if (to && day! > to) return false
    return true
  }), [combined, branch, from, to])

  const total = filtered.reduce((a, r) => a + r.amount, 0)
  const label = props.type === 'receipt' ? 'receipts' : props.type === 'expense' ? 'expenses' : 'cash movements'

  return (
    <Modal title={`Pending vs verified — ${label}`} subtitle="click a row to open it" onClose={props.onClose} maxWidth={780}>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
        <select value={branch} onChange={(e) => setBranch(e.target.value)} style={{ ...inputStyle, width: 'auto' }}>
          <option value="">All branches</option>
          {branches.map((b) => <option key={b} value={b}>{b}</option>)}
        </select>
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} style={{ ...inputStyle, width: 'auto' }} title="Submitted from" />
        <input type="date" value={to} onChange={(e) => setTo(e.target.value)} style={{ ...inputStyle, width: 'auto' }} title="Submitted to" />
        {(branch || from || to) && (
          <button type="button" onClick={() => { setBranch(''); setFrom(''); setTo('') }} style={{ ...ghostBtn, minHeight: 36 }}>
            Clear filters
          </button>
        )}
      </div>

      {filtered.length === 0 ? (
        <div style={{ padding: '18px 4px', fontSize: '0.84rem', color: 'var(--muted)', textAlign: 'center' }}>
          Nothing matches these filters.
        </div>
      ) : (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
              <thead>
                <tr>
                  <th style={th}>Date</th>
                  <th style={th}>Doc</th>
                  <th style={th}>Party</th>
                  <th style={th}>Branch</th>
                  <th style={th}>Status</th>
                  <th style={{ ...th, textAlign: 'right' }}>Amount</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((it) => (
                  <tr
                    key={`${it.type}-${it.id}`}
                    onClick={() => props.onSelect(it.id)}
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--navy3)' }}
                    onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
                  >
                    <td style={td}>{it.submittedAt ? it.submittedAt.slice(0, 10) : '—'}</td>
                    <td style={{ ...td, fontFamily: 'Consolas, monospace', fontSize: '0.76rem' }}>{it.documentNo ?? 'draft'}</td>
                    <td style={td}>{it.partyName}</td>
                    <td style={td}>{it.branchCode}</td>
                    <td style={td}><Badge tone={it.bucket === 'pending' ? 'amber' : 'green'}>{it.workflowStatus}</Badge></td>
                    <td style={{ ...td, textAlign: 'right', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(it.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '12px 4px 2px', marginTop: 8, borderTop: '2px solid var(--line)',
          }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>{filtered.length} entr{filtered.length === 1 ? 'y' : 'ies'}</span>
            <span style={{ fontSize: '0.98rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(total)}</span>
          </div>
        </>
      )}
    </Modal>
  )
}

// ───────────────────────────── Record detail ─────────────────────────────

function RecordDetail(props: {
  type: ReviewType
  doc: DetailDoc
  onDone: (message: string, keepOpen: boolean) => void
  onBack?: () => void
}) {
  const { type, doc } = props
  const cash = type === 'cash'
  const expense = !cash && 'expenseCategoryName' in doc
  const wf = doc.workflowStatus
  const docNo = doc.documentNo ?? 'draft'

  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [box, setBox] = useState<'query' | null>(null)
  const [boxText, setBoxText] = useState('')

  const { user } = useAuth()
  // Maker-checker mirror: the server refuses these actions when you created or last
  // touched the entry — hiding them avoids a dead-end click (server stays authoritative).
  const isMaker = user != null && (user.userId === doc.createdBy
    || (doc.lastModifiedBy != null && user.userId === doc.lastModifiedBy))
  const canReview = wf === 'SUBMITTED' && !isMaker
  const canClose = expense && (wf === 'VERIFIED' || wf === 'APPROVED') && !isMaker
  const overLimit = expense ? (doc as { overLimit: boolean }).overLimit : false

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
        : (
          <RecordCard
            doc={doc as AnyDoc}
            canOverride={canReview}
            busy={busy}
            onError={setError}
            onOverride={(lineNo, amount, reason) =>
              run(() => reviewApi.overrideLine(type, doc.id, lineNo, amount, reason),
                `Line ${lineNo} overridden — shows on this record permanently`, true)}
          />
        )}

      {!canReview && !canClose ? (
        <div style={{ ...card, textAlign: 'center', color: 'var(--faint)', fontSize: '0.84rem' }}>
          {isMaker
            ? 'You created or last edited this entry — maker-checker requires another reviewer.'
            : 'No action needed from you right now.'}
        </div>
      ) : (
        <div style={{ ...card }}>
          {box && (
            <QueryRejectBox
              kind={box}
              text={boxText}
              busy={busy}
              onText={setBoxText}
              onCancel={() => { setBox(null); setBoxText(''); setError('') }}
              onSubmit={submitBox}
            />
          )}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
            {canReview && (
              <>
                <button type="button" onClick={() => { setBox(box === 'query' ? null : 'query'); setBoxText(''); setError('') }}
                  style={{ ...ghostBtn, color: 'var(--amber)', minHeight: 36 }}>Query</button>
                <button type="button" onClick={() => run(() => reviewApi.verify(type, doc.id), `${docNo} verified — moved to Finance Manager`)}
                  disabled={busy} style={{ ...primaryBtn(busy), minHeight: 36 }}>Verify</button>
              </>
            )}
            {canClose && (
              <button type="button" onClick={() => run(() => reviewApi.closeExpense(doc.id), `${docNo} closed`)}
                disabled={busy || (overLimit && wf !== 'APPROVED')}
                style={{ ...primaryBtn(busy || (overLimit && wf !== 'APPROVED')), minHeight: 36 }}
                title={overLimit && wf !== 'APPROVED' ? 'Over-limit expense — needs Finance Manager approval first' : undefined}>
                Close expense
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
